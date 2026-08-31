package com.xzcpc.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.entity.SemiFormulaItem;
import com.xzcpc.template.entity.SemiFormulaVersion;
import com.xzcpc.template.entity.SemiProduct;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import com.xzcpc.template.mapper.SemiFormulaItemMapper;
import com.xzcpc.template.mapper.SemiFormulaVersionMapper;
import com.xzcpc.template.mapper.SemiProductMapper;
import com.xzcpc.template.service.SemiFormulaExplodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 半成品 BOM 爆炸引擎实现。
 *
 * 设计要点（2026-08-29~31 数据实测校准）：
 * - 半成品判定/关联统一走 qm_code ↔ semi_product.code（material.material_id 与 semi_id 不对应）
 * - 配方行目标匹配优先 qimaiProductCode → qm_code → material.material_id（存量物料 material_id
 *   可能为旧 id 体系，接口 materialId 直配会漏）；materialId 直配兜底
 * - 净出量在半成品主表（netOutputQuantity/netOutputUnit），版本层无此字段
 * - 质量换算表 kg/g/斤/两/mg；未覆盖单位 → 整体爆炸失败（调用方 fallback，防算错）
 * - 递归 SEMI_FINISHED + 深度/环保护（栈内重复即环）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemiFormulaExplodeServiceImpl implements SemiFormulaExplodeService {

    private static final String TYPE_SEMI_FINISHED = "SEMI_FINISHED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int MAX_DEPTH = 10;
    private static final int SCALE = 4;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 质量单位 → 克（支持净出/用量/基础单位间互转） */
    private static final Map<String, BigDecimal> GRAMS = Map.of(
            "kg", BigDecimal.valueOf(1000),
            "g", BigDecimal.ONE,
            "斤", BigDecimal.valueOf(500),
            "两", BigDecimal.valueOf(50),
            "mg", new BigDecimal("0.001"));

    private final SemiProductMapper semiProductMapper;
    private final SemiFormulaVersionMapper versionMapper;
    private final SemiFormulaItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 生效配方内存快照（prepare() 构建，每任务计算前刷新） */
    private volatile FormulaSnapshot snapshot;

    /** 内存快照：半成品 + ACTIVE 配方行 + 原料/规则映射 */
    private record FormulaSnapshot(
            Map<String, SemiProduct> semiById,
            Map<String, SemiProduct> semiByCode,
            Map<String, List<SemiFormulaItem>> itemsBySemi,
            Map<String, String> midByQmCode,
            Map<String, String> codeByMid,
            Set<String> materialIds,
            Map<String, String> baseUnitByMid) {}

    @Override
    public boolean isEnabled() {
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key='semi_bom_explode_enabled' LIMIT 1",
                    String.class);
            return "1".equals(val);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isBlacklisted(String qmCode) {
        if (!StringUtils.hasText(qmCode)) return false;
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key='semi_no_explode_codes' LIMIT 1",
                    String.class);
            if (!StringUtils.hasText(val)) return false;
            return Arrays.stream(val.split("[,\\s]+")).anyMatch(qmCode::equals);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void prepare() {
        // 1. 半成品主表（del_flag=0）
        Map<String, SemiProduct> semiById = new HashMap<>();
        Map<String, SemiProduct> semiByCode = new HashMap<>();
        for (SemiProduct sp : semiProductMapper.selectList(new LambdaQueryWrapper<>())) {
            if (sp.getSemiId() != null) {
                semiById.put(sp.getSemiId(), sp);
            }
            if (StringUtils.hasText(sp.getCode())) {
                semiByCode.put(sp.getCode(), sp);
            }
        }

        // 2. ACTIVE 配方版本（多个 ACTIVE 取最后遇到的 + 告警，爆炸前人工确认唯一性）
        Map<String, SemiFormulaVersion> versionBySemi = new HashMap<>();
        for (SemiFormulaVersion v : versionMapper.selectList(new LambdaQueryWrapper<SemiFormulaVersion>()
                .eq(SemiFormulaVersion::getStatus, STATUS_ACTIVE))) {
            SemiFormulaVersion prev = versionBySemi.put(v.getSemiId(), v);
            if (prev != null) {
                log.warn("半成品 {} 存在多个 ACTIVE 配方版本（{} 与 {}），爆炸使用 {}",
                        v.getSemiId(), prev.getVersionId(), v.getVersionId(), v.getVersionId());
            }
        }

        // 3. 配方行按半成品分组
        Map<String, List<SemiFormulaItem>> itemsBySemi = new HashMap<>();
        if (!versionBySemi.isEmpty()) {
            List<Long> versionIds = versionBySemi.values().stream()
                    .map(SemiFormulaVersion::getVersionId).collect(Collectors.toList());
            for (SemiFormulaItem item : itemMapper.selectList(new LambdaQueryWrapper<SemiFormulaItem>()
                    .in(SemiFormulaItem::getVersionId, versionIds))) {
                itemsBySemi.computeIfAbsent(item.getSemiId(), k -> new ArrayList<>()).add(item);
            }
        }

        // 4. 原料映射（qm_code → material_id / material_id → qm_code / 存在集合）
        Map<String, String> midByQmCode = new HashMap<>();
        Map<String, String> codeByMid = new HashMap<>();
        Set<String> materialIds = new HashSet<>();
        for (Material m : materialMapper.selectList(new LambdaQueryWrapper<>())) {
            if (m.getMaterialId() != null) {
                materialIds.add(m.getMaterialId());
                codeByMid.putIfAbsent(m.getMaterialId(), m.getQmCode());
            }
            if (StringUtils.hasText(m.getQmCode())) {
                midByQmCode.putIfAbsent(m.getQmCode(), m.getMaterialId());
            }
        }

        // 5. 盘点基础单位（爆炸目标输出单位）
        Map<String, String> baseUnitByMid = new HashMap<>();
        for (MaterialInventoryRule r : ruleMapper.selectList(new LambdaQueryWrapper<>())) {
            if (r.getMaterialId() != null && r.getBaseUnit() != null) {
                baseUnitByMid.putIfAbsent(r.getMaterialId(), r.getBaseUnit());
            }
        }

        snapshot = new FormulaSnapshot(semiById, semiByCode, itemsBySemi, midByQmCode,
                codeByMid, materialIds, baseUnitByMid);
        log.info("半成品爆炸引擎就绪：半成品 {} 个，有 ACTIVE 配方 {} 个，原料映射 {} 个，规则 {} 个",
                semiById.size(), itemsBySemi.size(), midByQmCode.size(), baseUnitByMid.size());
    }

    @Override
    public Map<String, BigDecimal> explode(String semiMid, BigDecimal qty, String unit) {
        FormulaSnapshot snap = snapshot;
        if (snap == null) {
            prepare();
            snap = snapshot;
        }
        if (qty == null || qty.signum() == 0) {
            return Map.of();
        }
        SemiProduct semi = snap.semiById.get(semiMid);
        if (semi == null) {
            // 双身份兜底：调用方传的是盘点主键 material_id（与 semi_id 不同值），
            // 经 qm_code 通道转半成品
            String code = snap.codeByMid.get(semiMid);
            if (code != null) {
                semi = snap.semiByCode.get(code);
            }
        }
        if (semi == null) {
            log.warn("半成品爆炸：material_id {} 未匹配到 semi_product，保留自身行", semiMid);
            return null;
        }
        return explodeInternal(semi, qty, unit);
    }

    @Override
    public Map<String, BigDecimal> explodeByCode(String qmCode, BigDecimal qty, String unit) {
        FormulaSnapshot snap = snapshot;
        if (snap == null) {
            prepare();
            snap = snapshot;
        }
        if (qty == null || qty.signum() == 0) {
            return Map.of();
        }
        SemiProduct semi = snap.semiByCode.get(qmCode);
        if (semi == null) {
            log.warn("半成品爆炸：qm_code {} 未匹配到 semi_product，保留自身行", qmCode);
            return null;
        }
        return explodeInternal(semi, qty, unit);
    }

    /** 爆炸核心（semi 已匹配）：黑名单/无配方/净出量异常/无基础单位 → null（调用方 fallback） */
    private Map<String, BigDecimal> explodeInternal(SemiProduct semi, BigDecimal qty, String unit) {
        if (isBlacklisted(semi.getCode())) {
            log.warn("半成品爆炸：{}[{}] 在黑名单中，保留自身行", semi.getCode(), semi.getName());
            return null;
        }
        FormulaSnapshot snap = snapshot;
        List<SemiFormulaItem> items = snap.itemsBySemi.get(semi.getSemiId());
        if (items == null || items.isEmpty()) {
            log.warn("半成品爆炸：{}[{}] 无 ACTIVE 配方，保留自身行", semi.getCode(), semi.getName());
            return null;
        }
        BigDecimal netOut = semi.getNetOutputQuantity();
        if (netOut == null || netOut.signum() <= 0) {
            log.warn("半成品爆炸：{}[{}] 净出量异常（{}），保留自身行", semi.getCode(), semi.getName(), netOut);
            return null;
        }
        // 未指定单位：半成品在 material 表有盘点规则则取基础单位兜底，否则爆炸失败
        if (!StringUtils.hasText(unit)) {
            String mid = snap.midByQmCode.get(semi.getCode());
            if (mid != null) {
                unit = snap.baseUnitByMid.get(mid);
            }
        }
        if (!StringUtils.hasText(unit)) {
            log.warn("半成品爆炸：{}[{}] 无盘点基础单位，保留自身行", semi.getCode(), semi.getName());
            return null;
        }
        Map<String, BigDecimal> result = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        if (!explodeLevel(semi, qty, unit, snap, result, stack, 0)) {
            log.warn("半成品爆炸：{}[{}] 爆炸失败（单位无法换算/目标未匹配/递归环），保留自身行",
                    semi.getCode(), semi.getName());
            return null;
        }
        return result;
    }

    @Override
    public boolean isExplodable(String mid) {
        FormulaSnapshot snap = snapshot;
        if (snap == null) {
            prepare();
            snap = snapshot;
        }
        if (mid == null) return false;
        String code = snap.codeByMid.get(mid);
        if (code == null) return false;
        if (isBlacklisted(code)) return false;
        SemiProduct semi = snap.semiByCode.get(code);
        if (semi == null) return false;
        List<SemiFormulaItem> items = snap.itemsBySemi.get(semi.getSemiId());
        return items != null && !items.isEmpty();
    }

    @Override
    public boolean isExplodableCode(String qmCode) {
        FormulaSnapshot snap = snapshot;
        if (snap == null) {
            prepare();
            snap = snapshot;
        }
        if (qmCode == null) return false;
        if (isBlacklisted(qmCode)) return false;
        SemiProduct semi = snap.semiByCode.get(qmCode);
        if (semi == null) return false;
        List<SemiFormulaItem> items = snap.itemsBySemi.get(semi.getSemiId());
        return items != null && !items.isEmpty();
    }

    @Override
    public Set<String> explodableQmCodes() {
        FormulaSnapshot snap = snapshot;
        if (snap == null) {
            prepare();
            snap = snapshot;
        }
        Set<String> codes = new HashSet<>();
        for (Map.Entry<String, SemiProduct> e : snap.semiByCode.entrySet()) {
            SemiProduct semi = e.getValue();
            List<SemiFormulaItem> items = snap.itemsBySemi.get(semi.getSemiId());
            if (items != null && !items.isEmpty() && !isBlacklisted(e.getKey())) {
                codes.add(e.getKey());
            }
        }
        return codes;
    }

    // ==================== 递归爆炸 ====================

    /**
     * 单层爆炸：rawQty = 半成品数量(净出单位折算) / 净出量 × 配方行用量 × (1+损耗率)。
     * 返回 false = 本层爆炸失败（调用方整体 fallback）。
     */
    private boolean explodeLevel(SemiProduct semi, BigDecimal qty, String unit, FormulaSnapshot snap,
                                 Map<String, BigDecimal> result, Deque<String> stack, int depth) {
        if (depth > MAX_DEPTH) {
            log.warn("半成品爆炸：{}[{}] 超过最大深度 {}，终止", semi.getCode(), semi.getName(), MAX_DEPTH);
            return false;
        }
        if (stack.contains(semi.getSemiId())) {
            log.warn("半成品爆炸：{}[{}] 存在递归环 {}，终止", semi.getCode(), semi.getName(), stack);
            return false;
        }
        stack.addLast(semi.getSemiId());
        try {
            BigDecimal netOut = semi.getNetOutputQuantity();
            if (netOut == null || netOut.signum() <= 0) return false;
            BigDecimal qtyNet = convert(qty, unit, semi.getNetOutputUnit());
            if (qtyNet == null) {
                log.warn("半成品爆炸：{}[{}] 数量单位 {} → 净出单位 {} 无法换算",
                        semi.getCode(), semi.getName(), unit, semi.getNetOutputUnit());
                return false;
            }
            BigDecimal factor = qtyNet.divide(netOut, 8, RoundingMode.HALF_UP);
            for (SemiFormulaItem item : snap.itemsBySemi.getOrDefault(semi.getSemiId(), List.of())) {
                if (item.getQuantity() == null) {
                    log.warn("半成品爆炸：{}[{}] 配方行 {} 用量为空，跳过该行",
                            semi.getCode(), semi.getName(), item.getItemName());
                    continue;
                }
                BigDecimal lossRate = item.getLossRate() == null ? ZERO : item.getLossRate();
                BigDecimal raw = factor.multiply(item.getQuantity()).multiply(ONE.add(lossRate));

                if (TYPE_SEMI_FINISHED.equals(item.getItemType())) {
                    // 递归：子半成品（raw 在 item.unit 单位 → 折算到子半成品基础单位）
                    SemiProduct child = snap.semiById.get(item.getSemiFinishedProductId());
                    if (child == null) {
                        log.warn("半成品爆炸：{}[{}] 子半成品 {} 未匹配到 semi_product",
                                semi.getCode(), semi.getName(), item.getSemiFinishedProductId());
                        return false;
                    }
                    BigDecimal childQty = convert(raw, item.getUnit(), child.getUnit());
                    if (childQty == null) {
                        log.warn("半成品爆炸：{}[{}] 子半成品 {} 用量单位 {} → {} 无法换算",
                                semi.getCode(), semi.getName(), child.getCode(), item.getUnit(), child.getUnit());
                        return false;
                    }
                    if (!explodeLevel(child, childQty, child.getUnit(), snap, result, stack, depth + 1)) {
                        return false;
                    }
                } else {
                    // MATERIAL：目标原料（优先 qm_code 通道，materialId 直配兜底）
                    String targetMid = resolveMaterialMid(item, snap);
                    if (targetMid == null) {
                        log.warn("半成品爆炸：{}[{}] 配方行 {}（materialId={}, qmCode={}）未匹配到原料",
                                semi.getCode(), semi.getName(), item.getItemName(),
                                item.getMaterialId(), item.getQimaiProductCode());
                        return false;
                    }
                    String targetUnit = snap.baseUnitByMid.get(targetMid);
                    if (targetUnit == null) {
                        log.warn("半成品爆炸：{}[{}] 原料 {} 无盘点基础单位，保留自身行",
                                semi.getCode(), semi.getName(), targetMid);
                        return false;
                    }
                    BigDecimal targetQty = convert(raw, item.getUnit(), targetUnit);
                    if (targetQty == null) {
                        log.warn("半成品爆炸：{}[{}] 配方行 {} 用量单位 {} → 基础单位 {} 无法换算",
                                semi.getCode(), semi.getName(), item.getItemName(), item.getUnit(), targetUnit);
                        return false;
                    }
                    result.merge(targetMid, targetQty.setScale(SCALE, RoundingMode.HALF_UP), BigDecimal::add);
                }
            }
            return true;
        } finally {
            stack.removeLast();
        }
    }

    /** 配方行原料 → material.material_id：qimaiProductCode（qm_code 通道）优先，materialId 直配兜底 */
    private String resolveMaterialMid(SemiFormulaItem item, FormulaSnapshot snap) {
        if (StringUtils.hasText(item.getQimaiProductCode())) {
            String mid = snap.midByQmCode.get(item.getQimaiProductCode());
            if (mid != null) {
                return mid;
            }
        }
        if (item.getMaterialId() != null && snap.materialIds.contains(item.getMaterialId())) {
            return item.getMaterialId();
        }
        return null;
    }

    /** 质量单位换算：qty[from] → qty[to]；同单位原值返回；非质量单位/未知单位返回 null */
    private BigDecimal convert(BigDecimal qty, String from, String to) {
        if (qty == null) return null;
        if (from == null || to == null) return null;
        String f = from.trim();
        String t = to.trim();
        if (f.equalsIgnoreCase(t)) return qty;
        BigDecimal fGrams = GRAMS.get(f);
        BigDecimal tGrams = GRAMS.get(t);
        if (fGrams == null || tGrams == null) return null;
        return qty.multiply(fGrams).divide(tGrams, 8, RoundingMode.HALF_UP);
    }
}
