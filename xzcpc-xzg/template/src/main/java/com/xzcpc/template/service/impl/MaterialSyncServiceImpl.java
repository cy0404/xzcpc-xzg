package com.xzcpc.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.template.client.XInfoApiClient;
import com.xzcpc.template.client.dto.XInfoMaterial;
import com.xzcpc.template.client.dto.XInfoSemiFinishedProduct;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialConversionRule;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialConversionRuleMapper;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import com.xzcpc.template.service.MaterialSyncService;
import com.xzcpc.template.util.ConversionTextParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 xinfo API 同步物料主数据（原料全量 + 半成品补采购字段）。
 *
 * 存量优先模式（接口数据校准前默认）：按 qm_code 匹配存量物料（存量 material_id 是旧 id 体系如 WP0917，
 * qm_code 与接口 code 同编码），命中 → 只补规则表采购单价/采购单位 + 一级分类成本映射，material 表其他字段
 * 不动（存量维持不变更）；接口有、库里没有的默认不插入（insert-new=true 才插）、存量默认不删
 * （delete-absent=true 才做全量镜像）。半成品独立 86 条（id 前缀 cmq28/cmpdo），id 并入源集合
 * （deleteAbsent 不清理半成品）：存量按 qm_code 补规则行采购/订货字段，insert-new=true 时接口有
 * 库里没有的也插入并建基础规则（无换算行；order_unit=unit、order_price=cost，已与业务确认
 * cost 即半成品订货价格），字段为 null 时跳过不覆盖。
 *
 * 设计要点：
 * - 拉取失败或返回空 → 抛异常中止整轮，任何删除不执行（防误删全量）
 * - material upsert 含"复活"（del_flag=1 → ENABLED 的 del_flag=0 恢复，经自定义 SQL 绕过逻辑删除过滤）
 * - 换算规则先逻辑删除再全量重插（与 MaterialRuleServiceImpl.clearRuleItems 语义一致）
 * - 不发布 MaterialChangedEvent：一轮数百条会触发事件风暴，快照正确性由迁移 SQL 按名称重映射保证
 * - GET_LOCK 串行化，server / mp-server 双端定时互斥
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialSyncServiceImpl implements MaterialSyncService {

    private static final String LOCK_NAME = "material_xinfo_sync";
    private static final String TYPE_UNIT = "unit";
    private static final String TYPE_WEIGHT = "weight";
    private static final String STATUS_DISABLED = "DISABLED";

    /** 一级分类映射：接口分类 → 库内成本科目名（成本类；淘汰品类/周年&周边/研发等保持原样） */
    private static final Map<String, String> PARENT_CATEGORY_MAP = Map.of(
            "食材物料", "食材成本",
            "耗材物料", "耗材成本",
            "辅材物料", "辅材成本",
            "包材物料", "包材成本",
            "自购食材物料", "自购食材成本"
    );

    /** 半成品统一父级分类：xinfo 半成品接口无分类字段，业务口径全部归入「食材成本」（已与用户确认） */
    private static final String SEMI_PARENT_CATEGORY = "食材成本";

    /** 半成品统一二级分类：接口无分类字段，业务口径二级分类即「半成品」（已与用户确认） */
    private static final String SEMI_CATEGORY = "半成品";

    private final XInfoApiClient xinfoApiClient;
    private final MaterialMapper materialMapper;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialConversionRuleMapper conversionRuleMapper;
    private final DataSource dataSource;

    /** 是否覆盖已存在的盘点规则（false 时仅补齐缺失规则，保护人工维护） */
    @Value("${app.sync.overwrite-rules:true}")
    private boolean overwriteRules;

    /**
     * 是否逻辑删除不在 xinfo 源中的物料（跳过手工创建 M 开头）。
     * 默认 false：存量物料不删（接口数据校准前不搞全量镜像）；校准后需要全量对齐时开启。
     */
    @Value("${app.sync.delete-absent:false}")
    private boolean deleteAbsent;

    /**
     * 是否插入接口有、库里没有的新物料（走接口 id 体系插入）。
     * 默认 false：接口数据校准前只补存量采购字段，不引入新物料。
     */
    @Value("${app.sync.insert-new:false}")
    private boolean insertNew;

    /**
     * 是否同步半成品（拉取接口 + 插新/规则/分类刷新）。
     * 默认 true；暂时关闭（false）时不拉半成品接口，但库中已标记半成品
     * （category='半成品'）的 code/material_id 仍纳入跳过与源集合，
     * 防止原料循环按 qm_code 刷新其规则/换算、deleteAbsent 误删。
     */
    @Value("${app.sync.semi-enabled:true}")
    private boolean semiEnabled;

    // ==================== 入口 ====================

    @Override
    public int sync() {
        try (Connection connection = dataSource.getConnection()) {
            if (!acquireLock(connection)) {
                log.info("未获取到同步锁 {}（另一实例正在同步），本轮跳过", LOCK_NAME);
                return 0;
            }
            try {
                return doSync();
            } finally {
                releaseLock(connection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("物料同步获取数据库连接失败: " + e.getMessage(), e);
        }
    }

    // ==================== 同步主流程 ====================

    private int doSync() {
        long start = System.currentTimeMillis();
        SyncStats stats = new SyncStats();

        // 1. 拉取原料（空响应/异常由客户端抛出，整轮中止，防误删）
        //    半成品 id 并入源集合（deleteAbsent 不清理），存量/插新处理见步骤 2.5——
        //    半成品接口当前无 purchaseUnit/purchasePrice 字段（2026-08-27 实测 0/86），
        //    字段为 null 时跳过不覆盖，将来接口补充后同步自动补录
        List<XInfoMaterial> materials = xinfoApiClient.fetchMaterials();
        Set<String> sourceIds = new HashSet<>(materials.size());
        // 仅收集"有真实名称"的物料进源集合：名称为空或名称=编码占位（如 name='WP0005'）的
        // 一律不拉取、不进源集合，deleteAbsent 会把库中对应物料逻辑删除（企迈补名后自动复活）
        materials.forEach(m -> {
            if (hasRealName(m.getId(), m.getName(), m.getCode())) sourceIds.add(m.getId());
        });
        // 半成品 id 也并入源集合：原料接口不含半成品（半成品独立 86 条，id 前缀 cmq28/cmpdo），
        // 不加进来 deleteAbsent 会把已入库的半成品物料全部误删。2.5 步骤复用同一列表，只拉一次。
        // semi-enabled=false 时不拉半成品接口（暂时不同步半成品）
        List<XInfoSemiFinishedProduct> semiProducts = Collections.emptyList();
        if (semiEnabled) {
            semiProducts = xinfoApiClient.fetchSemiFinishedProducts();
        } else {
            log.info("半成品同步已关闭（app.sync.semi-enabled=false），跳过半成品接口拉取");
        }
        Set<String> semiCodes = new HashSet<>();
        for (XInfoSemiFinishedProduct sp : semiProducts) {
            if (hasRealName(sp.getId(), sp.getName(), sp.getCode())) {
                sourceIds.add(sp.getId());
                semiCodes.add(sp.getCode());
            }
        }
        // 半成品关闭时：库中已标记半成品（category='半成品'）的 code 纳入跳过集合、
        // material_id 纳入源集合——原料循环不会按 qm_code 命中刷新其规则/换算，
        // deleteAbsent 也不会误删（原料 secondaryCategory 无"半成品"值，可唯一识别）
        if (!semiEnabled) {
            List<Material> semiRows = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                    .eq(Material::getCategory, SEMI_CATEGORY));
            for (Material s : semiRows) {
                if (StringUtils.hasText(s.getQmCode())) {
                    semiCodes.add(s.getQmCode());
                }
                if (StringUtils.hasText(s.getMaterialId())) {
                    sourceIds.add(s.getMaterialId());
                }
            }
        }

        // 2. 原料 upsert + 规则（名称为空/编码占位的不拉取）
        for (XInfoMaterial m : materials) {
            if (!hasRealName(m.getId(), m.getName(), m.getCode())) {
                stats.emptyNameSkipped++;
                continue;
            }
            // 半成品 code 跳过：原料/半成品接口有 19 条 code 重叠（同一物料两边都有），原料循环
            // 会按 unitConversion 建换算行（1kg=1000g），与半成品"无换算"语义冲突，统一由 2.5 处理
            if (semiCodes.contains(m.getCode())) {
                continue;
            }
            // 全部同步：按 qm_code 匹配任意状态存量（del_flag=0/1/2 都命中，无存量/淘汰区分），
            // 统一全量覆盖 material 字段 + del_flag 归位（接口 ENABLED→0 复活、DISABLED→1）
            // + 规则全量刷新。保留存量 material_id（模板/任务快照引用不破坏）。
            Material existing = materialMapper.selectAnyByQmCodeAny(m.getCode());
            if (existing != null) {
                upsertMaterial(existing.getMaterialId(), m.getCode(), m.getName(),
                        mapParentCategory(m.getPrimaryCategory()), m.getSecondaryCategory(),
                        m.getSpecification(), STATUS_DISABLED.equals(m.getStatus()), stats);
                upsertRules(RuleSource.of(m), stats);
                continue;
            }
            // 接口有、库里没有：默认不插入（数据校准前避免引入不准数据），insert-new=true 时按 id 走现状插入
            if (!insertNew) {
                stats.newSkipped++;
                continue;
            }
            boolean disabled = STATUS_DISABLED.equals(m.getStatus());
            UpsertOutcome outcome = upsertMaterial(m.getId(), m.getCode(), m.getName(),
                    mapParentCategory(m.getPrimaryCategory()), m.getSecondaryCategory(),
                    m.getSpecification(), disabled, stats);
            if (outcome.needRules()) {
                upsertRules(RuleSource.of(m), stats);
            }
        }

        // 2.5 半成品：存量（qm_code 命中 del_flag=0）补采购/订货字段、父级分类为空时补「食材成本」、
        //     二级分类为空时补「半成品」；insert-new=true 时接口有库里没有的也插入
        //     （material_id=接口 id，父级分类=食材成本、二级分类=半成品，接口无分类字段），
        //     并建基础规则（半成品无换算，仅 base_unit/inventory_units/
        //     order_unit/order_price，unit 为空的不建，后台人工补）。
        //     半成品无独立订货单位（unit 即基础单位），order_unit 取 unit；
        //     order_price 取 cost（接口字段名，业务口径即「实际成本价 = netOutputQuantity×每克价」，
        //     已与业务确认 = 半成品订货价格；qimaiPrice/qimaiStockPrice 均非订货价）；
        //     stock_unit 接口无独立库存单位字段，暂不覆盖（传 null）。
        //     接口字段为 null（采购字段当前全 null；cost 6 条 null、unit 16 条空）时由
        //     upsertSemiRule 内部跳过（unit 空/禁用不建规则）。
        for (XInfoSemiFinishedProduct sp : semiProducts) {
            if (!hasRealName(sp.getId(), sp.getName(), sp.getCode())) {
                stats.emptyNameSkipped++;
                continue;
            }
            Material semi = materialMapper.selectAnyByQmCode(sp.getCode());
            if (semi == null) {
                if (!insertNew) {
                    stats.newSkipped++;
                    continue;
                }
                boolean disabled = STATUS_DISABLED.equals(sp.getStatus());
                upsertMaterial(sp.getId(), sp.getCode(), sp.getName(),
                        SEMI_PARENT_CATEGORY, SEMI_CATEGORY, sp.getSpecification(), disabled, stats);
                upsertSemiRule(sp, sp.getId(), disabled, stats);
                continue;
            }
            // 存量半成品：父级分类为空时补「食材成本」、二级分类为空时补「半成品」
            // （接口无分类字段，旧数据/迁移数据可能为空；已有值不覆盖）
            if (!StringUtils.hasText(semi.getParentCategory())) {
                updateParentCategoryFields(semi.getMaterialId(), SEMI_PARENT_CATEGORY);
            }
            if (!StringUtils.hasText(semi.getCategory())) {
                updateCategoryFields(semi.getMaterialId(), SEMI_CATEGORY);
            }
            // 存量规格为空时补齐接口原值（规格按接口同步、不清空，仅在缺失时补）
            if (!StringUtils.hasText(semi.getSpec()) && StringUtils.hasText(sp.getSpecification())) {
                updateSpecFields(semi.getMaterialId(), sp.getSpecification());
            }
            // 存量也全量刷新规则（base_unit/inventory_units/unit_price/order_price，以接口为准；
            // 规则归属存量行 material_id，与接口 id 可能不一致；unit 为空/禁用时 upsertSemiRule 内部跳过）
            upsertSemiRule(sp, semi.getMaterialId(), STATUS_DISABLED.equals(sp.getStatus()), stats);
        }

        // 3. 清理不在源中的物料（源集合 = 原料接口 + 半成品接口；手工创建 M 开头保留）
        if (deleteAbsent) {
            stats.deleted = deleteAbsentMaterials(sourceIds);
            // 残留 del_flag=2（接口已无此 code 的淘汰物料）统一归 1——
            // 接口仍存在的 del_flag=2 已在循环中复活为 0，到这里的只剩接口没有的
            int marked = materialMapper.markEliminatedDeleted();
            if (marked > 0) {
                log.info("del_flag=2 淘汰物料统一归 1：{} 条", marked);
            }
        }

        log.info("物料同步完成：新增 {}，复活 {}，删除 {}，未匹配跳过 {}，空名跳过 {}，"
                        + "规则创建 {}，规则更新 {}，换算行 {}，解析失败 {} 段，耗时 {}ms",
                stats.inserted, stats.updated, stats.deleted, stats.newSkipped,
                stats.emptyNameSkipped, stats.rulesCreated, stats.rulesUpdated, stats.conversionRows,
                stats.parseFailures, System.currentTimeMillis() - start);
        return materials.size();
    }

    // ==================== material upsert ====================

    /** 是否拉取该物料：id 非空、名称非空、且名称不是编码占位（name 与 code 相同视为未维护） */
    private boolean hasRealName(String id, String name, String code) {
        return StringUtils.hasText(id) && StringUtils.hasText(name) && !name.trim().equals(code);
    }

    /** 一级分类映射：接口分类 → 库内成本科目名（未映射的保持原样） */
    private String mapParentCategory(String primaryCategory) {
        if (primaryCategory == null) return null;
        return PARENT_CATEGORY_MAP.getOrDefault(primaryCategory, primaryCategory);
    }

    /** 存量物料：刷新一级分类（成本科目名），其他字段不动 */
    private void updateParentCategoryFields(String materialId, String mappedParentCategory) {
        Material m = new Material();
        m.setMaterialId(materialId);
        m.setParentCategory(mappedParentCategory);
        materialMapper.updateParentCategoryFields(m);
    }

    /** 存量物料：刷新二级分类，其他字段不动 */
    private void updateCategoryFields(String materialId, String category) {
        Material m = new Material();
        m.setMaterialId(materialId);
        m.setCategory(category);
        materialMapper.updateCategoryFields(m);
    }

    /** 存量物料：规格为空时补齐接口原值，其他字段不动 */
    private void updateSpecFields(String materialId, String spec) {
        Material m = new Material();
        m.setMaterialId(materialId);
        m.setSpec(spec);
        materialMapper.updateSpecFields(m);
    }

    /**
     * upsert 物料。全部同步模式下无存量/淘汰区分：
     * - 库中已存在（del_flag=0/1/2 任意）：按接口全量覆盖字段并归位 del_flag
     *   （接口 ENABLED→0 即复活，DISABLED→1），保留原 material_id（快照表引用不破坏）
     * - 库中不存在：插入
     * 采购单价/采购单位维护在 material_inventory_rule 表。
     */
    private UpsertOutcome upsertMaterial(String materialId, String code, String name,
                                         String parentCategory, String category, String spec,
                                         boolean disabled, SyncStats stats) {
        Material material = materialMapper.selectAnyByMaterialId(materialId);
        if (material == null) {
            material = new Material();
            material.setMaterialId(materialId);
            material.setQmCode(code);
            material.setParentCategory(parentCategory);
            material.setCategory(category);
            material.setMaterialName(name);
            material.setSpec(spec);
            // 不覆盖人工配置的 loss_visible
            material.setDelFlag(disabled ? 1 : 0);
            materialMapper.insert(material);
            stats.inserted++;
            return new UpsertOutcome(!disabled, false);
        }
        // 已存在（含 del_flag=1/2）：全量覆盖字段，del_flag 按接口重写（ENABLED→0 复活）
        material.setQmCode(code);
        material.setParentCategory(parentCategory);
        material.setCategory(category);
        material.setMaterialName(name);
        material.setSpec(spec);
        material.setDelFlag(disabled ? 1 : 0);
        materialMapper.upsertSyncFields(material);
        stats.updated++;
        return new UpsertOutcome(!disabled, false);
    }

    /** upsert 分支结果：needRules=需生成/刷新规则（禁用物料不建） */
    private record UpsertOutcome(boolean needRules, boolean ignored) {}

    private int deleteAbsentMaterials(Set<String> sourceIds) {
        List<Material> all = materialMapper.selectList(new LambdaQueryWrapper<>());
        int deleted = 0;
        for (Material m : all) {
            if (sourceIds.contains(m.getMaterialId())) continue;
            if (m.getMaterialId() != null && m.getMaterialId().startsWith("M")) continue; // 手工物料保留
            materialMapper.deleteById(m.getId());
            deleted++;
        }
        return deleted;
    }

    // ==================== 规则生成 ====================

    /** 规则数据源：原料与半成品统一抽象 */
    private record RuleSource(String materialId, String name, String baseUnit, String usageUnit,
                              String unitConversion, String weighingConversion, BigDecimal inventoryPrice,
                              BigDecimal purchasePrice, String purchaseUnit,
                              BigDecimal orderPrice, String orderUnit, String stockUnit) {
        static RuleSource of(XInfoMaterial m) {
            return new RuleSource(m.getId(), m.getName(), m.getBaseUnit(), m.getUsageUnit(),
                    m.getUnitConversion(), m.getWeighingConversion(), m.getInventoryPrice(),
                    m.getPurchasePrice(), m.getPurchaseUnit(),
                    m.getStandardCostPrice(), m.getUsageUnit(), m.getStockUnit());
        }
    }

    private void upsertRules(RuleSource src, SyncStats stats) {
        String baseUnit = StringUtils.hasText(src.baseUnit()) ? src.baseUnit().trim() : null;
        String usageUnit = StringUtils.hasText(src.usageUnit()) ? src.usageUnit().trim() : null;
        if (baseUnit == null) {
            baseUnit = usageUnit;
        }
        if (baseUnit == null) {
            log.warn("物料 {}[{}] 无基础单位与使用单位，跳过规则生成", src.materialId(), src.name());
            return;
        }

        // 解析换算文本
        List<ConversionTextParser.ConversionEntry> unitEntries = ConversionTextParser.parse(
                src.materialId(), src.name(), src.unitConversion());
        List<ConversionTextParser.ConversionEntry> weightEntries = ConversionTextParser.parse(
                src.materialId(), src.name(), src.weighingConversion());
        stats.parseFailures +=
                countSegments(src.unitConversion()) + countSegments(src.weighingConversion())
                        - unitEntries.size() - weightEntries.size();

        // 已存在规则且不覆盖 → 跳过刷新（仅补齐缺失）
        MaterialInventoryRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<MaterialInventoryRule>()
                .eq(MaterialInventoryRule::getMaterialId, src.materialId()));
        if (rule != null && !overwriteRules) {
            return;
        }

        boolean ruleNew = rule == null;
        if (rule == null) {
            rule = new MaterialInventoryRule();
            rule.setRuleId("TMP");
            rule.setMaterialId(src.materialId());
            rule.setBaseUnit(baseUnit);
            rule.setInventoryUnits(buildInventoryUnits(baseUnit, unitEntries, weightEntries));
            rule.setUnitPrice(resolveUnitPrice(src, baseUnit));
            rule.setPurchasePrice(src.purchasePrice());
            rule.setPurchaseUnit(src.purchaseUnit());
            rule.setOrderPrice(src.orderPrice());
            rule.setOrderUnit(src.orderUnit());
            rule.setStockUnit(src.stockUnit());
            ruleMapper.insert(rule);
            // 正式编码 MR + 8 位自增 id（与 MaterialRuleServiceImpl 一致）
            rule.setRuleId("MR" + String.format("%08d", rule.getId()));
            ruleMapper.updateById(rule);
            stats.rulesCreated++;
        } else {
            rule.setBaseUnit(baseUnit);
            rule.setInventoryUnits(buildInventoryUnits(baseUnit, unitEntries, weightEntries));
            rule.setUnitPrice(resolveUnitPrice(src, baseUnit));
            rule.setPurchasePrice(src.purchasePrice());
            rule.setPurchaseUnit(src.purchaseUnit());
            rule.setOrderPrice(src.orderPrice());
            rule.setOrderUnit(src.orderUnit());
            rule.setStockUnit(src.stockUnit());
            ruleMapper.updateById(rule);
            stats.rulesUpdated++;
        }

        // 换算行：先逻辑删除再全量重插（sort_no unit 类从 1、weight 类从 1）
        conversionRuleMapper.delete(new LambdaQueryWrapper<MaterialConversionRule>()
                .eq(MaterialConversionRule::getRuleId, rule.getRuleId()));
        int sort = 1;
        for (ConversionTextParser.ConversionEntry e : unitEntries) {
            conversionRuleMapper.insert(buildConversion(rule.getRuleId(), TYPE_UNIT, e, sort++));
        }
        sort = 1;
        for (ConversionTextParser.ConversionEntry e : weightEntries) {
            conversionRuleMapper.insert(buildConversion(rule.getRuleId(), TYPE_WEIGHT, e, sort++));
        }
        stats.conversionRows += unitEntries.size() + weightEntries.size();
        if (ruleNew) {
            log.debug("物料 {}[{}] 规则已生成：base={}，unit换算 {} 条，weight换算 {} 条",
                    src.materialId(), src.name(), baseUnit, unitEntries.size(), weightEntries.size());
        }
    }

    /**
     * 半成品基础规则：半成品接口无换算字段，换算行从规格自动解析（1000g/kg → 1000g=1kg）。
     * unit_price 取 qimaiPrice（元/unit 口径，null 兜底 0）。
     * materialId 为规则归属的 material_id：插新=接口 id，存量=存量行 id（qm_code 命中的旧行，
     * 与接口 id 不一致，用接口 id 查会建出孤儿规则、旧规则残留换算行不被清理）。
     * unit 为空不建（后台人工补）；禁用物料不建；已存在且不覆盖时跳过（overwriteRules 门控）。
     */
    private void upsertSemiRule(XInfoSemiFinishedProduct sp, String materialId, boolean disabled, SyncStats stats) {
        if (disabled) {
            log.debug("半成品 {}[{}] 为禁用状态，跳过规则生成", sp.getId(), sp.getName());
            return;
        }
        String unit = sp.getUnit() != null ? sp.getUnit().trim() : null;
        if (!StringUtils.hasText(unit)) {
            log.warn("半成品 {}[{}] 无单位，跳过规则生成（后台人工补）", sp.getId(), sp.getName());
            return;
        }
        MaterialInventoryRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<MaterialInventoryRule>()
                .eq(MaterialInventoryRule::getMaterialId, materialId));
        if (rule != null && !overwriteRules) {
            return;
        }
        // 规格自动解析换算：1000g/kg → 1000g=1kg；50g/包 → 50g=1包；纯单位规格（kg/kg 等）无换算
        List<ConversionTextParser.ConversionEntry> specEntries =
                ConversionTextParser.parseSpec(sp.getId(), sp.getName(), sp.getSpecification());
        if (rule == null) {
            rule = new MaterialInventoryRule();
            rule.setRuleId("TMP");
            rule.setMaterialId(materialId);
            rule.setBaseUnit(unit);
            rule.setInventoryUnits(buildInventoryUnits(unit, specEntries, Collections.emptyList()));
            rule.setUnitPrice(sp.getQimaiPrice() == null ? BigDecimal.ZERO : sp.getQimaiPrice());
            rule.setOrderPrice(sp.getCost());
            rule.setOrderUnit(unit);
            ruleMapper.insert(rule);
            // 正式编码 MR + 8 位自增 id（与 upsertRules 一致）
            rule.setRuleId("MR" + String.format("%08d", rule.getId()));
            ruleMapper.updateById(rule);
            stats.rulesCreated++;
        } else {
            rule.setBaseUnit(unit);
            rule.setInventoryUnits(buildInventoryUnits(unit, specEntries, Collections.emptyList()));
            rule.setUnitPrice(sp.getQimaiPrice() == null ? BigDecimal.ZERO : sp.getQimaiPrice());
            rule.setOrderPrice(sp.getCost());
            rule.setOrderUnit(unit);
            ruleMapper.updateById(rule);
            stats.rulesUpdated++;
        }
        // 换算行：先清残留（19 条 code 与原料接口重叠，原料循环历史双重处理留下的），
        // 再按规格自动生成（sort_no 从 1，unit 类）
        conversionRuleMapper.delete(new LambdaQueryWrapper<MaterialConversionRule>()
                .eq(MaterialConversionRule::getRuleId, rule.getRuleId()));
        int sort = 1;
        for (ConversionTextParser.ConversionEntry e : specEntries) {
            conversionRuleMapper.insert(buildConversion(rule.getRuleId(), TYPE_UNIT, e, sort++));
        }
        stats.conversionRows += specEntries.size();
    }

    private MaterialConversionRule buildConversion(String ruleId, String type,
                                                   ConversionTextParser.ConversionEntry e, int sortNo) {
        MaterialConversionRule c = new MaterialConversionRule();
        c.setRuleId(ruleId);
        c.setConversionType(type);
        c.setFromQuantity(e.fromQuantity());
        c.setFromUnit(e.fromUnit());
        c.setToQuantity(e.toQuantity());
        c.setToUnit(e.toUnit());
        c.setSortNo(sortNo);
        return c;
    }

    private String buildInventoryUnits(String baseUnit,
                                       List<ConversionTextParser.ConversionEntry> unitEntries,
                                       List<ConversionTextParser.ConversionEntry> weightEntries) {
        Set<String> units = new LinkedHashSet<>();
        units.add(baseUnit);
        unitEntries.forEach(e -> {
            units.add(e.fromUnit());
            units.add(e.toUnit());
        });
        weightEntries.forEach(e -> {
            units.add(e.fromUnit());
            units.add(e.toUnit());
        });
        return String.join(",", units);
    }

    /**
     * 价格口径：unit_price = 基础单位单价。
     * xinfo inventoryPrice 即基础单位价（已与 standardCostPrice 交叉验证：
     * 南姜 inventoryPrice=0.014 元/g = 14 元/kg，试饮杯 0.1 元/个 × 50 = 5 元/捆），直接落库；
     * null → 0。
     */
    private BigDecimal resolveUnitPrice(RuleSource src, String baseUnit) {
        BigDecimal price = src.inventoryPrice();
        return price == null ? BigDecimal.ZERO : price;
    }

    private int countSegments(String text) {
        if (!StringUtils.hasText(text)) return 0;
        int count = 0;
        for (String s : text.split("[,，;；]")) {
            if (StringUtils.hasText(s)) count++;
        }
        return count;
    }

    // ==================== GET_LOCK ====================

    private boolean acquireLock(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT GET_LOCK('" + LOCK_NAME + "', 10)")) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("获取同步锁失败: " + e.getMessage(), e);
        }
    }

    private void releaseLock(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT RELEASE_LOCK('" + LOCK_NAME + "')");
        } catch (SQLException e) {
            log.warn("释放同步锁失败: {}", e.getMessage());
        }
    }

    // ==================== 统计 ====================

    private static class SyncStats {
        int inserted;          // 新插入（insert-new=true 时）
        int updated;           // 复活（del_flag=1 → 按接口全量覆盖）
        int deleted;
        int newSkipped;        // 接口有、库里没有且 insert-new=false，跳过未插入
        int emptyNameSkipped;  // 接口名称空，不拉取
        int rulesCreated;
        int rulesUpdated;
        int conversionRows;
        int parseFailures;
    }
}
