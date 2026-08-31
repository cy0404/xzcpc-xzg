package com.xzcpc.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.template.client.XInfoApiClient;
import com.xzcpc.template.client.dto.XInfoFormulaItem;
import com.xzcpc.template.client.dto.XInfoFormulaVersion;
import com.xzcpc.template.client.dto.XInfoSemiFinishedProduct;
import com.xzcpc.template.entity.SemiFormulaItem;
import com.xzcpc.template.entity.SemiFormulaVersion;
import com.xzcpc.template.entity.SemiProduct;
import com.xzcpc.template.mapper.SemiFormulaItemMapper;
import com.xzcpc.template.mapper.SemiFormulaVersionMapper;
import com.xzcpc.template.mapper.SemiProductMapper;
import com.xzcpc.template.service.SemiFormulaSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 半成品成本卡同步实现。
 *
 * 同步语义（防旧数据误用）：
 * - semi_product：按 semi_id upsert（接口存在 → 全量覆盖 + del_flag 归 0 复活）；
 *   接口消失的半成品不删（status 从接口同步，DISABLED 半成品无 ACTIVE 配方自然不爆炸）
 * - semi_formula_version：先按 semi_id 全量逻辑删，再对接口仍存在的 version_id 恢复 +
 *   upsert——接口配方被删/清空后，旧 ACTIVE 版本立即失效，杜绝爆炸误用残留配方
 * - semi_formula_item：按版本物理删除后重插（版本内容以接口为准，全量替换）
 *
 * 保护措施（与物料同步一致）：
 * - 拉取失败或返回空 → 抛异常中止整轮，任何写入不执行（防误删全量）
 * - GET_LOCK 串行化，server / mp-server 双端定时互斥
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemiFormulaSyncServiceImpl implements SemiFormulaSyncService {

    private static final String LOCK_NAME = "semi_formula_xinfo_sync";
    private static final String STATUS_DISABLED = "DISABLED";

    private final XInfoApiClient xinfoApiClient;
    private final SemiProductMapper semiProductMapper;
    private final SemiFormulaVersionMapper versionMapper;
    private final SemiFormulaItemMapper itemMapper;
    private final DataSource dataSource;

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
            throw new IllegalStateException("半成品配方同步获取数据库连接失败: " + e.getMessage(), e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int doSync() {
        long start = System.currentTimeMillis();
        // 空响应/异常由客户端抛出，整轮中止（防误删）
        List<XInfoSemiFinishedProduct> semis = xinfoApiClient.fetchSemiFinishedProducts();

        SyncStats stats = new SyncStats();
        LocalDateTime now = LocalDateTime.now();

        for (XInfoSemiFinishedProduct sp : semis) {
            if (sp.getId() == null || sp.getCode() == null) {
                stats.skipped++;
                continue;
            }
            // 1. 半成品主表 upsert（del_flag 归 0；先填全字段再入库，code 等 NOT NULL 列无默认值）
            SemiProduct semi = semiProductMapper.selectAnyBySemiId(sp.getId());
            boolean isNew = (semi == null);
            if (isNew) {
                semi = new SemiProduct();
                semi.setSemiId(sp.getId());
            } else {
                semiProductMapper.restoreBySemiId(sp.getId());
            }
            semi.setCode(sp.getCode());
            semi.setName(sp.getName());
            semi.setSpecification(sp.getSpecification());
            semi.setUnit(sp.getUnit());
            semi.setNetOutputQuantity(sp.getNetOutputQuantity());
            semi.setNetOutputUnit(sp.getNetOutputUnit());
            semi.setYieldRate(sp.getYieldRate());
            semi.setCost(sp.getCost());
            semi.setStatus(sp.getStatus());
            semi.setSyncedAt(now);
            if (isNew) {
                semiProductMapper.insert(semi);
                stats.inserted++;
            } else {
                semiProductMapper.updateById(semi);
                stats.updated++;
            }

            // 2. 版本：先全量逻辑删，再恢复 + upsert 接口仍存在的版本
            int versionCount = 0;
            int itemCount = 0;
            versionMapper.logicDeleteBySemiId(sp.getId());
            List<XInfoFormulaVersion> versions = sp.getFormulaVersions();
            if (versions != null && !versions.isEmpty()) {
                for (XInfoFormulaVersion v : versions) {
                    if (v.getId() == null) {
                        continue;
                    }
                    versionMapper.restoreByVersionId(v.getId());
                    SemiFormulaVersion ver = versionMapper.selectOne(new LambdaQueryWrapper<SemiFormulaVersion>()
                            .eq(SemiFormulaVersion::getVersionId, v.getId()));
                    if (ver == null) {
                        ver = new SemiFormulaVersion();
                        ver.setVersionId(v.getId());
                        ver.setSemiId(sp.getId());
                        versionMapper.insert(ver);
                    }
                    ver.setVersionName(v.getVersionName());
                    ver.setStatus(v.getStatus());
                    ver.setTotalCost(v.getTotalCost());
                    ver.setSyncedAt(now);
                    versionMapper.updateById(ver);
                    versionCount++;

                    // 3. 配方行：物理删后全量重插
                    itemCount += itemMapper.deleteByVersionId(v.getId());
                    if (v.getItems() != null) {
                        int sort = 1;
                        for (XInfoFormulaItem it : v.getItems()) {
                            SemiFormulaItem item = new SemiFormulaItem();
                            item.setVersionId(v.getId());
                            item.setSemiId(sp.getId());
                            item.setItemId(it.getId());
                            item.setItemType(it.getItemType());
                            item.setMaterialId(it.getMaterialId());
                            item.setSemiFinishedProductId(it.getSemiFinishedProductId());
                            item.setItemName(it.getItemName());
                            item.setQuantity(it.getQuantity());
                            item.setUnit(it.getUnit());
                            item.setLossRate(it.getLossRate());
                            item.setSortOrder(it.getSortOrder() != null ? it.getSortOrder() : sort);
                            item.setQimaiProductCode(it.getQimaiProductCode());
                            item.setSyncedAt(now);
                            itemMapper.insert(item);
                            sort++;
                        }
                    }
                }
            }
            stats.versionCount += versionCount;
            stats.itemCount += itemCount;
            if (versionCount == 0) {
                stats.noFormulaCount++;
                log.warn("半成品 {}[{}] 无配方版本，差异计算将保留自身行（fallback）",
                        sp.getCode(), sp.getName());
            }
            if (versionCount > 1) {
                // 多版本需人工确认 ACTIVE 唯一性（爆炸只取 ACTIVE）
                log.warn("半成品 {}[{}] 有 {} 个配方版本，爆炸仅取 ACTIVE",
                        sp.getCode(), sp.getName(), versionCount);
            }
        }

        // 接口消失的存量半成品：保留主表，但确保其版本全部失效（无 ACTIVE 则无爆炸）
        List<SemiProduct> stale = semiProductMapper.selectList(new LambdaQueryWrapper<SemiProduct>());
        for (SemiProduct s : stale) {
            if (s.getSemiId() == null) continue;
            boolean stillExists = false;
            for (XInfoSemiFinishedProduct sp : semis) {
                if (sp.getId() != null && sp.getId().equals(s.getSemiId())) {
                    stillExists = true;
                    break;
                }
            }
            if (!stillExists) {
                versionMapper.logicDeleteBySemiId(s.getSemiId());
                log.warn("半成品 {}[{}] 已不在 xinfo 接口，主表保留、配方版本全部失效",
                        s.getCode(), s.getName());
            }
        }

        log.info("半成品成本卡同步完成：半成品 {}（新增 {} 复活/更新 {} 跳过 {}），版本 {}，配方行 {}，"
                        + "无配方 {} 个，耗时 {}ms",
                semis.size(), stats.inserted, stats.updated, stats.skipped,
                stats.versionCount, stats.itemCount, stats.noFormulaCount,
                System.currentTimeMillis() - start);
        return semis.size();
    }

    // ==================== GET_LOCK（与物料同步一致） ====================

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
        int inserted;        // 新插入的半成品
        int updated;         // 恢复/更新的存量半成品
        int skipped;         // 缺 id/code 跳过
        int versionCount;    // 处理配方版本数
        int itemCount;       // 配方行总数（含删除）
        int noFormulaCount;  // 无配方的半成品数
    }
}
