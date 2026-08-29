package com.xzcpc.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.task.entity.*;
import com.xzcpc.task.mapper.*;
import com.xzcpc.task.service.DifferenceCalcService;
import com.xzcpc.template.dto.MaterialRuleResp;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.mapper.MaterialMapper;
import com.xzcpc.template.service.MaterialRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifferenceCalcServiceImpl implements DifferenceCalcService {

    private static final double DEFAULT_THRESHOLD = 0.5;

    @Autowired
    private JdbcTemplate mysqlJdbc;
    @Resource(name = "pgJdbcTemplate")
    private JdbcTemplate pgJdbc;
    private final MaterialMapper materialMapper;
    private final MaterialRuleService materialRuleService;
    private final TaskMapper taskMapper;
    private final TaskMaterialSummaryMapper summaryMapper;
    private final InventoryDifferenceMapper diffMapper;
    private final DifferenceProcessLogMapper logMapper;
    private final DifferenceModifyLogMapper modifyLogMapper;
    private final StoreAccessService storeAccessService;

    @Lazy
    @Resource
    private DifferenceCalcServiceImpl self;  // 自身代理，确保 @Async 生效

    private final Object calcLock = new Object();  // 全局锁，自动计算和手动计算串行
    private volatile boolean autoCalcRunning = false;

    // ==================== 核心计算 ====================

    @Override
    public int calculateAndSaveDifferences(Integer taskId) {
        synchronized (calcLock) {
            return doCalculate(taskId);
        }
    }

    private int doCalculate(Integer taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在: " + taskId);
        if (!"submitted".equals(task.getStatus())) throw new RuntimeException("仅已提交的任务可计算差异");

        // 先删子表再删父表
        mysqlJdbc.update("DELETE FROM difference_modify_log WHERE task_id = ?", taskId);
        diffMapper.physicalDeleteByTaskId(taskId);

        String storeId = task.getStoreId();
        String warehouseCode = task.getWarehouseCode();
        LocalDateTime endTime = task.getSubmittedAt();
        if (endTime == null) endTime = LocalDateTime.now();

        // 时间窗口：上次提交时间 → 本次提交时间
        LocalDateTime startTime = findPrevSubmitTime(storeId, endTime);
        String startDate = startTime.toLocalDate().toString();
        String endDate = endTime.toLocalDate().plusDays(1).toString(); // exclusive
        log.warn("时间窗口: start={} end={}", startDate, endDate);

        // 上次盘点剩余：上一个任务对应物料的 adjusted_qty
        Integer prevTaskId = findPrevTaskId(storeId, endTime);
        Map<String, BigDecimal> prevAdjustedMap = prevTaskId != null ? loadPrevAdjustedQty(prevTaskId) : Map.of();

        // 获取任务所有物料（含汇总中的数据 + 未录入/0录入的物料）
        List<TaskMaterialSummary> summaries = summaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, taskId));
        // 补零盘点物料：task_zone_material 中 not_entered / zero_entered 的
        Map<String, String> zeroMatMap = new LinkedHashMap<>(); // materialId → materialName
        List<Map<String, Object>> zoneMats = mysqlJdbc.queryForList(
                "SELECT DISTINCT material_id, material_name FROM task_zone_material WHERE task_id=? AND del_flag=0 AND input_status IN ('not_entered','zero_entered')", taskId);
        for (Map<String, Object> zm : zoneMats) {
            String mid = (String) zm.get("material_id");
            if (mid != null && summaries.stream().noneMatch(s -> mid.equals(s.getMaterialId()))) {
                zeroMatMap.put(mid, (String) zm.get("material_name"));
            }
        }
        // 汇总物料ID + 零盘点物料ID
        Set<String> allMatIds = new LinkedHashSet<>();
        summaries.forEach(s -> allMatIds.add(s.getMaterialId()));
        allMatIds.addAll(zeroMatMap.keySet());
        List<String> matIdList = new ArrayList<>(allMatIds);

        // 预加载物料
        Map<String, Material> materialMap = matIdList.isEmpty() ? Map.of() :
                materialMapper.selectList(new LambdaQueryWrapper<Material>().in(Material::getMaterialId, matIdList))
                .stream().collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));
        // 预加载规则
        Map<String, MaterialRuleResp> ruleMap;
        try { ruleMap = matIdList.isEmpty() ? Map.of() : materialRuleService.batchDetail(matIdList); }
        catch (Exception e) { log.warn("加载规则失败", e); ruleMap = Map.of(); }
        Map<String, String> categoryGroupMap = loadCategoryGroupMap();
        double threshold = getThresholdRate();

        // qmCode 映射
        Map<String, String> qmToMid = new HashMap<>();
        for (Material m : materialMap.values())
            if (m.getQmCode() != null && !m.getQmCode().isEmpty()) qmToMid.put(m.getQmCode(), m.getMaterialId());

        // ---- 查询各数据源（时间窗口：startDate → endDate）----
        Set<String> storeIds = Set.of(storeId);
        Set<String> whCodes = warehouseCode != null ? Set.of(warehouseCode) : Set.of();

        Map<String, BigDecimal> pgPurchaseMap = batchQueryPgByDate("dwd.purchase", whCodes, startDate, endDate, qmToMid, ruleMap, "purchase_quantity");
        Map<String, BigDecimal> pgOrderMap = batchQueryPgByDate("dwd.purchase_order", whCodes, startDate, endDate, qmToMid, ruleMap, "order_quantity");
        Map<String, BigDecimal> pgConsumptionMap = batchQueryPgConsumptionByDate(whCodes, startDate, endDate, qmToMid, ruleMap);
        Map<String, BigDecimal> transferNetMap = batchQueryTransferByDate(storeIds, startDate, endDate, qmToMid, ruleMap);
        Map<String, BigDecimal> returnMap = batchQueryReturnByDate(storeIds, startDate, endDate, ruleMap);
        Map<String, BigDecimal> lossMap = batchQueryLossByDate(storeIds, startDate, endDate, categoryGroupMap);
        Map<String, BigDecimal> selfPurchaseMap = batchQuerySelfPurchaseByDate(storeIds, startDate, endDate, ruleMap);

        List<InventoryDifference> diffList = new ArrayList<>();
        // 处理有盘点数量的物料（从 summary）
        for (TaskMaterialSummary sm : summaries) {
            String mid = sm.getMaterialId();
            computeDiff(diffList, mid, sm.getMaterialName(), sm.getSpec(), sm.getBaseUnit(), sm.getAdjustedQty(), taskId, storeId, startTime, endTime,
                    pgPurchaseMap, pgOrderMap, pgConsumptionMap, transferNetMap, returnMap, lossMap, selfPurchaseMap, prevAdjustedMap, threshold);
        }
        // 处理零盘点物料（not_entered/zero_entered）
        for (Map.Entry<String, String> e : zeroMatMap.entrySet()) {
            String mid = e.getKey();
            Material m = materialMap.get(mid);
            String spec = m != null ? m.getSpec() : "";
            String unit = "";
            try { MaterialRuleResp r = ruleMap.get(mid); if (r != null && r.getBaseUnit() != null) unit = r.getBaseUnit(); } catch (Exception ignored) {}
            computeDiff(diffList, mid, e.getValue(), spec, unit, BigDecimal.ZERO, taskId, storeId, startTime, endTime,
                    pgPurchaseMap, pgOrderMap, pgConsumptionMap, transferNetMap, returnMap, lossMap, selfPurchaseMap, prevAdjustedMap, threshold);
        }
        log.warn("单任务 storeId={} 调货:{} 还货:{} 报损:{} 自购:{} PG采购:{} 订货:{} 消耗:{} 上月:{}",
                storeId, transferNetMap.size(), returnMap.size(), lossMap.size(), selfPurchaseMap.size(),
                pgPurchaseMap.size(), pgOrderMap.size(), pgConsumptionMap.size(), prevAdjustedMap.size());
        if (!diffList.isEmpty()) diffMapper.insertBatch(diffList);
        log.warn("任务 {} 差异计算完成，共 {} 条", taskId, diffList.size());
        return diffList.size();
    }

    private void computeDiff(List<InventoryDifference> diffList, String mid, String matName, String spec, String unit,
                              BigDecimal actual, Integer taskId, String storeId, LocalDateTime startTime, LocalDateTime endTime,
                              Map<String, BigDecimal> pgPurchaseMap, Map<String, BigDecimal> pgOrderMap, Map<String, BigDecimal> pgConsumptionMap,
                              Map<String, BigDecimal> transferNetMap, Map<String, BigDecimal> returnMap, Map<String, BigDecimal> lossMap,
                              Map<String, BigDecimal> selfPurchaseMap, Map<String, BigDecimal> prevAdjustedMap, double threshold) {
        if (actual == null) actual = BigDecimal.ZERO;
        BigDecimal lastMonth = prevAdjustedMap.getOrDefault(mid, BigDecimal.ZERO);
        BigDecimal purchase = pgPurchaseMap.getOrDefault(mid, BigDecimal.ZERO);
        BigDecimal order = pgOrderMap.getOrDefault(mid, BigDecimal.ZERO);
        BigDecimal consumption = pgConsumptionMap.getOrDefault(mid, BigDecimal.ZERO);
        BigDecimal transferNet = transferNetMap.getOrDefault(mid + "|" + storeId, BigDecimal.ZERO);
        BigDecimal returnNet = returnMap.getOrDefault(mid + "|" + storeId, BigDecimal.ZERO);
        BigDecimal loss = lossMap.getOrDefault(mid + "|" + storeId, BigDecimal.ZERO);
        BigDecimal selfPurchase = selfPurchaseMap.getOrDefault(mid + "|" + storeId, BigDecimal.ZERO);

        // 消耗为0的不统计
        if (consumption.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal theoretical = lastMonth.add(purchase).add(order).add(transferNet).add(returnNet)
                .subtract(loss).add(selfPurchase).subtract(consumption);
        BigDecimal diff = actual.subtract(theoretical);
        BigDecimal diffRate = BigDecimal.ZERO;
        if (theoretical.compareTo(BigDecimal.ZERO) != 0)
            diffRate = diff.abs().divide(theoretical.abs(), 4, RoundingMode.HALF_UP);
        boolean isLarge = diffRate.compareTo(BigDecimal.valueOf(threshold)) > 0;

        InventoryDifference d = new InventoryDifference();
        d.setTaskId(taskId); d.setMaterialId(mid); d.setMaterialName(matName != null ? matName : "");
        d.setSpec(spec); d.setUnit(unit);
        d.setLastMonthQty(lastMonth); d.setTransferNetQty(transferNet); d.setReturnQty(returnNet);
        d.setLossQty(loss); d.setSelfPurchaseQty(selfPurchase);
        d.setPurchaseQty(purchase); d.setOrderQty(order); d.setConsumptionQty(consumption);
        d.setTheoreticalQty(theoretical); d.setActualQty(actual);
        d.setDiffQty(diff); d.setDiffRate(diffRate); d.setIsLarge(isLarge ? 1 : 0);
        d.setOriginalIsLarge(isLarge ? 1 : 0);
        d.setStatus("pending");
        d.setCreatedAt(LocalDateTime.now()); d.setUpdatedAt(LocalDateTime.now());
        diffList.add(d);
    }

    @Override
    public double getThresholdRate() {
        try {
            String val = mysqlJdbc.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key='diff_threshold_rate' LIMIT 1", String.class);
            if (val != null && !val.isEmpty()) {
                return Double.parseDouble(val);
            }
        } catch (Exception e) {
            log.warn("读取差异阈值失败，使用默认值 {}", DEFAULT_THRESHOLD, e);
        }
        return DEFAULT_THRESHOLD;
    }

    @Override
    public int batchCalculateUncounted() {
        List<Integer> taskIds = mysqlJdbc.queryForList(
                "SELECT id FROM task WHERE status = 'submitted' AND del_flag = 0 AND task_type = 'monthly' " +
                "AND NOT EXISTS (SELECT 1 FROM inventory_difference d WHERE d.task_id = task.id AND d.del_flag = 0) " +
                "ORDER BY id", Integer.class);
        log.info("批量计算 {} 个未计算任务", taskIds.size());
        int done = 0;
        synchronized (calcLock) {
            for (Integer id : taskIds) {
                try { doCalculate(id); done++; }
                catch (Exception e) { log.error("任务 {} 计算失败: {}", id, e.getMessage()); }
            }
        }
        return done;
    }

    @Override
    public void updateThresholdRate(double rate) {
        mysqlJdbc.update(
                "INSERT INTO sys_config (config_key, config_value, description) VALUES ('diff_threshold_rate', ?, '盘点差异阈值') ON DUPLICATE KEY UPDATE config_value = ?",
                String.valueOf(rate), String.valueOf(rate));
    }

    @Override
    public int recalcIsLarge(double rate) {
        List<InventoryDifference> all = diffMapper.selectList(null);
        int changed = 0;
        BigDecimal threshold = BigDecimal.valueOf(rate);
        for (InventoryDifference d : all) {
            if (d.getDiffRate() == null) continue;
            int newVal = d.getDiffRate().compareTo(threshold) > 0 ? 1 : 0;
            if (d.getIsLarge() == null || d.getIsLarge() != newVal) {
                d.setIsLarge(newVal);
                diffMapper.updateById(d);
                changed++;
            }
        }
        log.info("阈值更新为 {}，{} 条差异 is_large 变更", rate, changed);
        return changed;
    }

    // ==================== 列表查询 ====================

    @Override
    public Map<String, Object> listDiffTasks(int pageNum, int pageSize, String storeIds, String supervisorName) {
        // 督导角色：只查询自己管理的门店
        String storeFilter = "";
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleIds.isEmpty()) return Map.of("records", List.of(), "total", 0, "current", pageNum, "pages", 0);
            storeFilter = "AND t.store_id IN (" + accessibleIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")) + ")";
        }
        // 前端筛选：门店多选
        if (storeIds != null && !storeIds.isEmpty()) {
            String[] arr = storeIds.split(",");
            storeFilter += " AND t.store_id IN (" + Arrays.stream(arr).map(s -> "'" + s.trim() + "'").collect(Collectors.joining(",")) + ")";
        }
        // 前端筛选：督导
        if (supervisorName != null && !supervisorName.isEmpty()) {
            List<String> svStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (svStoreIds.isEmpty()) return Map.of("records", List.of(), "total", 0, "current", pageNum, "pages", 0);
            storeFilter += " AND t.store_id IN (" + svStoreIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")) + ")";
        }
        String countSql = "SELECT COUNT(*) FROM task t WHERE t.status = 'submitted' AND t.submitted_at >= '2026-07-20' AND t.del_flag = 0 AND t.task_type = 'monthly' " + storeFilter;
        int total = mysqlJdbc.queryForObject(countSql, Integer.class);

        int offset = (pageNum - 1) * pageSize;
        String dataSql = "SELECT t.id AS taskId, t.store_name AS storeName, t.store_id AS storeId, " +
                "t.submitted_at AS submittedAt, t.task_month AS taskMonth, " +
                "(SELECT COUNT(*) FROM task_material_summary s WHERE s.task_id = t.id AND s.del_flag = 0) AS materialCount, " +
                "COALESCE((SELECT COUNT(*) FROM inventory_difference d2 WHERE d2.task_id = t.id AND d2.is_large = 1 AND d2.del_flag = 0), 0) AS largeDiffCount, " +
                "COALESCE((SELECT COUNT(*) FROM inventory_difference d2 WHERE d2.task_id = t.id AND d2.del_flag = 0), 0) AS totalDiffCount, " +
                "COALESCE((SELECT COUNT(*) FROM inventory_difference d2 WHERE d2.task_id = t.id AND d2.original_is_large = 1 AND d2.del_flag = 0), 0) AS originalLargeDiffCount, " +
                "COALESCE((SELECT SUM(ABS(d2.diff_qty)) FROM inventory_difference d2 WHERE d2.task_id = t.id AND d2.del_flag = 0), 0) AS totalDiffQty, " +
                "(SELECT ssa.admin_name FROM supervisor_store_access ssa WHERE ssa.store_id = t.store_id AND ssa.del_flag = 0 LIMIT 1) AS supervisorName, " +
                "EXISTS(SELECT 1 FROM inventory_difference d3 WHERE d3.task_id = t.id AND d3.del_flag = 0) AS hasCalculated " +
                "FROM task t " +
                "WHERE t.status = 'submitted' AND t.submitted_at >= '2026-07-20' AND t.del_flag = 0 AND t.task_type = 'monthly' " + storeFilter +
                "ORDER BY largeDiffCount DESC, totalDiffQty DESC " +
                "LIMIT " + pageSize + " OFFSET " + offset;
        List<Map<String, Object>> list = mysqlJdbc.queryForList(dataSql);

        return Map.of("records", list, "total", total, "current", pageNum, "pages",
                (int) Math.ceil((double) total / pageSize));
    }

    @Override
    public Map<String, Object> getDiffDetail(Integer taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        // 任务信息
        Map<String, Object> taskInfo = new LinkedHashMap<>();
        taskInfo.put("taskId", task.getId());
        taskInfo.put("taskName", task.getTaskName());
        taskInfo.put("storeName", task.getStoreName());
        taskInfo.put("taskMonth", task.getTaskMonth());
        taskInfo.put("submittedAt", task.getSubmittedAt());
        // 督导
        try {
            String supervisor = mysqlJdbc.queryForObject(
                    "SELECT admin_name FROM supervisor_store_access WHERE store_id = ? AND del_flag = 0 LIMIT 1",
                    String.class, task.getStoreId());
            taskInfo.put("supervisorName", supervisor != null ? supervisor : "");
        } catch (Exception e) {
            taskInfo.put("supervisorName", "");
        }

        // 加载 task_zone_material 的多单位录入明细（unit_inputs 列存 JSON）
        Map<String, String> unitInputsMap = new HashMap<>();
        try {
            List<Map<String, Object>> zoneMats = mysqlJdbc.queryForList(
                    "SELECT material_id, unit_inputs FROM task_zone_material " +
                    "WHERE task_id = ? AND del_flag = 0 AND unit_inputs IS NOT NULL AND unit_inputs != ''", taskId);
            for (Map<String, Object> row : zoneMats) {
                String mid = (String) row.get("material_id");
                String unitInputs = (String) row.get("unit_inputs");
                if (mid != null && unitInputs != null) {
                    unitInputsMap.put(mid, unitInputs);
                }
            }
        } catch (Exception e) { log.warn("加载录入明细失败: {}", e.getMessage()); }

        // 差异明细，按大差异优先、差异率绝对值降序
        List<InventoryDifference> diffs = diffMapper.selectList(
                new LambdaQueryWrapper<InventoryDifference>()
                        .eq(InventoryDifference::getTaskId, taskId)
                        .orderByDesc(InventoryDifference::getIsLarge)
                        .orderByDesc(InventoryDifference::getDiffRate));

        for (InventoryDifference d : diffs) {
            d.setUnitBreakdown(unitInputsMap.get(d.getMaterialId()));
        }

        return Map.of("taskInfo", taskInfo, "differences", diffs,
                "total", diffs.size(),
                "largeCount", diffs.stream().filter(d -> d.getIsLarge() != null && d.getIsLarge() == 1).count());
    }

    @Override
    @Transactional
    public void modifyAdjustedQty(Long diffId, java.math.BigDecimal newAdjustedQty, String operator) {
        InventoryDifference diff = diffMapper.selectById(diffId);
        if (diff == null) {
            throw new RuntimeException("差异项不存在");
        }
        // 记录修改前的值（允许反复修改，每次记录日志）
        BigDecimal oldQty = diff.getActualQty();

        // 更新 task_material_summary 的 adjusted_qty
        summaryMapper.updateAdjustedQty(diff.getTaskId(), diff.getMaterialId(), newAdjustedQty);

        // 重算差异
        BigDecimal theoretical = diff.getTheoreticalQty();
        BigDecimal diffQty = newAdjustedQty.subtract(theoretical);
        BigDecimal diffRate = BigDecimal.ZERO;
        if (theoretical.compareTo(BigDecimal.ZERO) != 0) {
            diffRate = diffQty.abs().divide(theoretical.abs(), 4, RoundingMode.HALF_UP);
        }
        double threshold = getThresholdRate();
        boolean isLarge = diffRate.compareTo(BigDecimal.valueOf(threshold)) > 0;

        diff.setActualQty(newAdjustedQty);
        diff.setDiffQty(diffQty);
        diff.setDiffRate(diffRate);
        diff.setIsLarge(isLarge ? 1 : 0);
        diff.setStatus("adjusted");
        diff.setHandler(operator);
        diff.setHandledAt(LocalDateTime.now());
        diffMapper.updateById(diff);

        // 查询门店名和初始值
        String storeId = "";
        String storeName = "";
        BigDecimal initialQty = oldQty;
        try {
            Task t = taskMapper.selectById(diff.getTaskId());
            if (t != null) { storeId = t.getStoreId() != null ? t.getStoreId() : ""; storeName = t.getStoreName() != null ? t.getStoreName() : ""; }
            TaskMaterialSummary sm = summaryMapper.selectOne(
                    new LambdaQueryWrapper<TaskMaterialSummary>()
                            .eq(TaskMaterialSummary::getTaskId, diff.getTaskId())
                            .eq(TaskMaterialSummary::getMaterialId, diff.getMaterialId()));
            if (sm != null && sm.getOriginalQty() != null) initialQty = sm.getOriginalQty();
        } catch (Exception ignored) {}

        // 修改日志
        DifferenceModifyLog mlog = new DifferenceModifyLog();
        mlog.setDiffId(diffId);
        mlog.setTaskId(diff.getTaskId());
        mlog.setStoreId(storeId);
        mlog.setStoreName(storeName);
        mlog.setMaterialId(diff.getMaterialId());
        mlog.setMaterialName(diff.getMaterialName());
        mlog.setInitialQty(initialQty);
        mlog.setOldQty(oldQty);
        mlog.setNewQty(newAdjustedQty);
        mlog.setOperator(operator);
        mlog.setCreatedAt(LocalDateTime.now());
        modifyLogMapper.insert(mlog);

        // 操作日志
        DifferenceProcessLog log = new DifferenceProcessLog();
        log.setDiffId(diffId);
        log.setAction("adjust");
        log.setOperator(operator);
        log.setRemark("修改 adjusted_qty 为 " + newAdjustedQty);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    // ==================== 物料维度聚合 ====================

    @Override
    public Map<String, Object> listDiffMaterials(String taskMonth, String storeIds, String supervisorName) {
        // 督导权限过滤
        String storeFilter = "";
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleIds.isEmpty()) return Map.of("materials", List.of(), "months", List.of());
            storeFilter = "AND t.store_id IN (" + accessibleIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")) + ")";
        }
        // 前端筛选：门店多选
        if (storeIds != null && !storeIds.isEmpty()) {
            String[] arr = storeIds.split(",");
            storeFilter += " AND t.store_id IN (" + Arrays.stream(arr).map(s -> "'" + s.trim() + "'").collect(Collectors.joining(",")) + ")";
        }
        // 前端筛选：督导
        if (supervisorName != null && !supervisorName.isEmpty()) {
            List<String> svStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (svStoreIds.isEmpty()) return Map.of("materials", List.of(), "months", List.of());
            storeFilter += " AND t.store_id IN (" + svStoreIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")) + ")";
        }

        // 查询可用月份
        String monthsSql = "SELECT DISTINCT t.task_month FROM task t WHERE t.status = 'submitted' AND t.submitted_at >= '2026-07-20' AND t.del_flag = 0 AND t.task_type = 'monthly' " + storeFilter + " ORDER BY t.task_month DESC";
        List<String> months = mysqlJdbc.queryForList(monthsSql, String.class);

        // 未传月份时只返回月份列表
        if (taskMonth == null || taskMonth.isEmpty()) {
            return Map.of("materials", List.of(), "months", months);
        }

        // 按物料聚合差异（仅大差异）
        String dataSql = "SELECT d.material_id, d.material_name, d.spec, d.unit, " +
                "COUNT(DISTINCT t.store_id) AS store_count, " +
                "SUM(d.diff_qty) AS total_diff_qty, " +
                "SUM(ABS(d.diff_qty)) AS total_abs_diff_qty, " +
                "COUNT(*) AS large_diff_count, " +
                "AVG(d.diff_rate) AS avg_diff_rate " +
                "FROM inventory_difference d " +
                "JOIN task t ON d.task_id = t.id AND t.del_flag = 0 " +
                "WHERE t.task_month = ? AND t.task_type = 'monthly' AND d.del_flag = 0 AND d.is_large = 1 " + storeFilter + " " +
                "GROUP BY d.material_id, d.material_name, d.spec, d.unit " +
                "ORDER BY store_count DESC, total_abs_diff_qty DESC";
        List<Map<String, Object>> materials = mysqlJdbc.queryForList(dataSql, taskMonth);

        // 为每个物料查询门店级明细
        for (Map<String, Object> mat : materials) {
            String mid = (String) mat.get("material_id");
            String detailSql = "SELECT t.store_id, t.store_name, d.diff_qty, d.diff_rate, d.is_large, " +
                    "d.theoretical_qty, d.actual_qty, d.task_id " +
                    "FROM inventory_difference d " +
                    "JOIN task t ON d.task_id = t.id AND t.del_flag = 0 " +
                    "WHERE d.material_id = ? AND t.task_month = ? AND d.del_flag = 0 AND d.is_large = 1 " + storeFilter + " " +
                    "ORDER BY ABS(d.diff_qty) DESC";
            List<Map<String, Object>> storeDetails = mysqlJdbc.queryForList(detailSql, mid, taskMonth);
            mat.put("storeDetails", storeDetails);
        }

        return Map.of("materials", materials, "months", months);
    }

    // ==================== 时间窗口工具 ====================

    /** 找同一门店上一次提交任务的时间 */
    private LocalDateTime findPrevSubmitTime(String storeId, LocalDateTime before) {
        try {
            Task t = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                    .eq(Task::getStoreId, storeId).eq(Task::getStatus, "submitted")
                    .eq(Task::getTaskType, "monthly")
                    .lt(Task::getSubmittedAt, before)
                    .orderByDesc(Task::getSubmittedAt).last("LIMIT 1"));
            if (t != null && t.getSubmittedAt() != null) return t.getSubmittedAt();
        } catch (Exception ignored) {}
        // 无历史提交，用当前时间减 2 个月兜底
        return before.minusMonths(2);
    }

    /** 找同一门店上一次提交任务的 ID */
    private Integer findPrevTaskId(String storeId, LocalDateTime before) {
        try {
            Task t = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                    .eq(Task::getStoreId, storeId).eq(Task::getStatus, "submitted")
                    .eq(Task::getTaskType, "monthly")
                    .lt(Task::getSubmittedAt, before)
                    .orderByDesc(Task::getSubmittedAt).last("LIMIT 1"));
            return t != null ? t.getId() : null;
        } catch (Exception ignored) { return null; }
    }

    /** 加载上一个任务的 adjusted_qty */
    private Map<String, BigDecimal> loadPrevAdjustedQty(Integer prevTaskId) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (prevTaskId == null) return map;
        List<TaskMaterialSummary> list = summaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, prevTaskId));
        for (TaskMaterialSummary sm : list) {
            BigDecimal v = sm.getAdjustedQty() != null ? sm.getAdjustedQty() : sm.getTotalQty();
            map.put(sm.getMaterialId(), v);
        }
        return map;
    }

    // ==================== 数据源查询（按时间窗口） ====================

    private Map<String, BigDecimal> batchQueryTransferByDate(Set<String> storeIds, String startDate, String endDate,
                                                               Map<String, String> qmToMid, Map<String, MaterialRuleResp> ruleMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
            String sql = "SELECT oi.material_id, oi.material_name, oi.transfer_qty, oi.unit, o.from_store_id, o.to_store_id " +
                    "FROM transfer_order_item oi JOIN transfer_order o ON oi.transfer_id = o.id " +
                    "WHERE (o.from_store_id IN (" + inStores + ") OR o.to_store_id IN (" + inStores + ")) " +
                    "AND o.status IN ('completed','returned') AND o.received_at >= ? AND o.received_at < ? " +
                    "AND oi.del_flag = 0 AND o.del_flag = 0";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql, startDate, endDate);
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String matName = (String) row.get("material_name");
                if (mid == null && matName != null) mid = qmToMid.get(matName);
                if (mid == null) continue;
                String fromSid = (String) row.get("from_store_id");
                String toSid = (String) row.get("to_store_id");
                BigDecimal rawQty = toBigDecimal(row.get("transfer_qty"));
                if (rawQty.compareTo(BigDecimal.ZERO) == 0) continue;
                BigDecimal qty = convertToBaseUnit(mid, (String) row.get("unit"), rawQty, ruleMap);
                if (storeIds.contains(fromSid)) map.merge(mid + "|" + fromSid, qty.negate(), BigDecimal::add);
                if (storeIds.contains(toSid)) map.merge(mid + "|" + toSid, qty, BigDecimal::add);
            }
        } catch (Exception e) { log.warn("调货查询失败: {}", e.getMessage()); }
        return map;
    }

    private Map<String, BigDecimal> batchQueryReturnByDate(Set<String> storeIds, String startDate, String endDate,
                                                             Map<String, MaterialRuleResp> ruleMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
            String sql = "SELECT oi.material_id, oi.material_name, oi.unit, o.from_store_id, o.to_store_id, rr.return_qty " +
                    "FROM transfer_return_record rr " +
                    "JOIN transfer_order_item oi ON rr.item_id = oi.id AND oi.del_flag = 0 " +
                    "JOIN transfer_order o ON rr.transfer_id = o.id AND o.del_flag = 0 " +
                    "WHERE (o.from_store_id IN (" + inStores + ") OR o.to_store_id IN (" + inStores + ")) " +
                    "AND rr.return_type='goods' AND rr.created_at >= ? AND rr.created_at < ?";
            log.warn("还货SQL: WHERE store IN ({}) date=[{} , {}]", inStores, startDate, endDate);
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql, startDate, endDate);
            log.warn("batchQueryReturn 查询到 {} 行", rows.size());
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                boolean useName = false;
                if (mid == null) { mid = (String) row.get("material_name"); useName = true; }
                if (mid == null) continue;
                String fromSid = (String) row.get("from_store_id");
                String toSid = (String) row.get("to_store_id");
                BigDecimal rawQty = toBigDecimal(row.get("return_qty"));
                if (rawQty.compareTo(BigDecimal.ZERO) == 0) continue;
                // material_id 有值时换算最小单位，按名称匹配的用原值
                BigDecimal qty = useName ? rawQty : convertToBaseUnit(mid, (String) row.get("unit"), rawQty, ruleMap);
                if (storeIds.contains(fromSid)) map.merge(mid + "|" + fromSid, qty, BigDecimal::add);
                if (storeIds.contains(toSid)) map.merge(mid + "|" + toSid, qty.negate(), BigDecimal::add);
            }
        } catch (Exception e) { log.warn("还货查询失败: {}", e.getMessage()); }
        return map;
    }

    private Map<String, BigDecimal> batchQueryLossByDate(Set<String> storeIds, String startDate, String endDate,
                                                           Map<String, String> categoryGroupMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
            String sql = "SELECT lr.material_id, lr.store_id, lr.status, lr.loss_type, lr.occurred_date, lr.completed_at, " +
                    "CASE WHEN lr.base_qty IS NOT NULL AND lr.base_qty > 0 THEN lr.base_qty ELSE lr.input_qty END AS base_qty, " +
                    "m.category FROM loss_report lr LEFT JOIN material m ON lr.material_id = m.material_id " +
                    "WHERE lr.store_id IN (" + inStores + ") AND lr.del_flag = 0 " +
                    "AND ((lr.occurred_date >= ? AND lr.occurred_date < ?) OR (lr.completed_at >= ? AND lr.completed_at < ?))";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql, startDate, endDate, startDate, endDate);
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String status = (String) row.get("status");
                String lossType = (String) row.get("loss_type");
                String category = (String) row.get("category");
                String sid = (String) row.get("store_id");
                BigDecimal qty = toBigDecimal(row.get("base_qty"));
                if (mid == null || qty.compareTo(BigDecimal.ZERO) == 0) continue;
                if ("daily".equals(lossType)) {
                    if ("completed".equals(status)) map.merge(mid + "|" + sid, qty, BigDecimal::add);
                    continue;
                }
                boolean isFruitVeg = isFruitVegMaterial(category, categoryGroupMap);
                if (isFruitVeg && "registered".equals(status)) map.merge(mid + "|" + sid, qty, BigDecimal::add);
                else if (!isFruitVeg) {
                    if ("confirmed_resend".equals(status) || "received".equals(status) || "completed".equals(status))
                        map.merge(mid + "|" + sid, qty, BigDecimal::add);
                    if ("completed".equals(status) && row.get("completed_at") != null) {
                        map.merge(mid + "|" + sid, qty.negate(), BigDecimal::add);
                    }
                }
            }
        } catch (Exception e) { log.warn("报损查询失败: {}", e.getMessage()); }
        return map;
    }

    private Map<String, BigDecimal> batchQuerySelfPurchaseByDate(Set<String> storeIds, String startDate, String endDate,
                                                                   Map<String, MaterialRuleResp> ruleMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
            String sql = "SELECT material_id, store_id, unit, COALESCE(SUM(purchase_qty),0) AS qty FROM self_purchase_material " +
                    "WHERE store_id IN (" + inStores + ") AND purchase_date >= ? AND purchase_date < ? AND del_flag = 0 " +
                    "GROUP BY material_id, store_id, unit";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql, startDate, endDate);
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String sid = (String) row.get("store_id");
                if (mid != null) {
                    BigDecimal rawQty = toBigDecimal(row.get("qty"));
                    BigDecimal qty = convertToBaseUnit(mid, (String) row.get("unit"), rawQty, ruleMap);
                    map.merge(mid + "|" + sid, qty, BigDecimal::add);
                }
            }
        } catch (Exception e) { log.warn("自购查询失败: {}", e.getMessage()); }
        return map;
    }

    private Map<String, BigDecimal> batchQueryPgByDate(String table, Set<String> whCodes, String startDate, String endDate,
                                                          Map<String, String> qmToMid, Map<String, MaterialRuleResp> ruleMap, String qtyField) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (whCodes.isEmpty() || qmToMid.isEmpty()) return map;
        try {
            String inWh = whCodes.stream().map(w -> "'" + w + "'").collect(Collectors.joining(","));
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            String sql = "SELECT item_code, COALESCE(SUM(" + qtyField + "),0) AS total_qty, " +
                    "COALESCE(unit, MAX(unit) OVER (PARTITION BY item_code)) AS unit FROM " + table +
                    " WHERE warehouse_code IN (" + inWh + ") AND stat_date >= ?::date AND stat_date < ?::date " +
                    "AND item_code IN (" + inItems + ") GROUP BY item_code, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, startDate, endDate);
            for (Map<String, Object> row : rows) {
                String mid = qmToMid.get((String) row.get("item_code"));
                if (mid != null) {
                    BigDecimal qty = convertToBaseUnit(mid, (String) row.get("unit"), toBigDecimal(row.get("total_qty")), ruleMap);
                    map.merge(mid, qty, BigDecimal::add);
                }
            }
        } catch (Exception e) { log.warn("PG {} 查询失败: {}", table, e.getMessage()); }
        return map;
    }

    private Map<String, BigDecimal> batchQueryPgConsumptionByDate(Set<String> whCodes, String startDate, String endDate,
                                                                    Map<String, String> qmToMid, Map<String, MaterialRuleResp> ruleMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (whCodes.isEmpty() || qmToMid.isEmpty()) return map;
        try {
            String inWh = whCodes.stream().map(w -> "'" + w + "'").collect(Collectors.joining(","));
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            String sql = "SELECT item_code, COALESCE(SUM(sales_quantity - COALESCE(return_quantity,0)),0) AS total_qty, " +
                    "COALESCE(unit, MAX(unit) OVER (PARTITION BY item_code)) AS unit " +
                    "FROM dwd.store_item_sales WHERE warehouse_code IN (" + inWh + ") " +
                    "AND stat_date >= ?::date AND stat_date < ?::date AND item_code IN (" + inItems + ") GROUP BY item_code, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, startDate, endDate);
            for (Map<String, Object> row : rows) {
                String itemCode = (String) row.get("item_code");
                String pgUnit = (String) row.get("unit");
                BigDecimal rawQty = toBigDecimal(row.get("total_qty"));
                String mid = qmToMid.get(itemCode);
                if (mid != null) {
                    BigDecimal qty = convertToBaseUnit(mid, pgUnit, rawQty, ruleMap);
                    log.warn("消耗: itemCode={} pgUnit={} rawQty={} → converted={}", itemCode, pgUnit, rawQty, qty);
                    map.merge(mid, qty, BigDecimal::add);
                }
            }
        } catch (Exception e) { log.warn("PG消耗查询失败: {}", e.getMessage()); }
        return map;
    }

    // ==================== 原有数据源查询 ====================

    /** 上月盘点剩余：查上个月同门店同物料的 task_material_summary.adjusted_qty */
    private Map<String, BigDecimal> queryLastMonthRemaining(String storeId, String prevMonth,
                                                             List<String> materialIds, Map<String, Material> materialMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (materialIds.isEmpty()) return map;
        try {
            // 找上个月同门店已提交任务
            String sql = "SELECT sm.material_id, sm.adjusted_qty FROM task_material_summary sm " +
                    "JOIN task t ON sm.task_id = t.id AND t.del_flag = 0 " +
                    "WHERE t.store_id = ? AND t.task_month = ? AND t.status = 'submitted' AND t.task_type = 'monthly' AND sm.del_flag = 0";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql, storeId, prevMonth);
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                Object qty = row.get("adjusted_qty");
                if (mid != null && qty != null) {
                    map.put(mid, toBigDecimal(qty));
                }
            }
        } catch (Exception e) {
            log.warn("查询上月盘点剩余失败: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, BigDecimal> queryPgSum(String tableName, String warehouseCode, String taskMonth,
                                                Map<String, Material> materialMap, Map<String, MaterialRuleResp> ruleMap) {
        return queryPgSum(tableName, warehouseCode, taskMonth, materialMap, ruleMap, "purchase_quantity");
    }

    /** 查询 PG SUM(qtyField)，按 item_code→material.qmCode 映射 */
    private Map<String, BigDecimal> queryPgSum(String tableName, String warehouseCode, String taskMonth,
                                                Map<String, Material> materialMap, Map<String, MaterialRuleResp> ruleMap,
                                                String qtyField) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            // qmCode → materialId 映射
            Map<String, String> qmToMid = new HashMap<>();
            for (Material m : materialMap.values()) {
                if (m.getQmCode() != null && !m.getQmCode().isEmpty()) {
                    qmToMid.put(m.getQmCode(), m.getMaterialId());
                }
            }
            if (qmToMid.isEmpty()) return map;

            List<String> qmCodes = new ArrayList<>(qmToMid.keySet());
            String inClause = qmCodes.stream().map(c -> "'" + c.replace("'", "''") + "'")
                    .collect(Collectors.joining(","));

            String sql = "SELECT item_code, COALESCE(SUM(" + qtyField + "), 0) AS total_qty, unit " +
                    "FROM " + tableName + " WHERE warehouse_code = ? AND stat_month = ?::date " +
                    "AND item_code IN (" + inClause + ") GROUP BY item_code, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, warehouseCode, taskMonth + "-01");

            for (Map<String, Object> row : rows) {
                String itemCode = (String) row.get("item_code");
                String pgUnit = (String) row.get("unit");
                BigDecimal qty = toBigDecimal(row.get("total_qty"));
                String mid = qmToMid.get(itemCode);
                if (mid != null) {
                    BigDecimal converted = convertToBaseUnit(mid, pgUnit, qty, ruleMap);
                    map.merge(mid, converted, BigDecimal::add);
                }
            }
        } catch (Exception e) {
            log.warn("查询 PG 表 {} 失败: {}", tableName, e.getMessage());
        }
        return map;
    }

    /** 查询 PG 消耗：SUM(sales_quantity - return_quantity) */
    private Map<String, BigDecimal> queryPgConsumption(String warehouseCode, String taskMonth,
                                                        Map<String, Material> materialMap, Map<String, MaterialRuleResp> ruleMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            Map<String, String> qmToMid = new HashMap<>();
            for (Material m : materialMap.values()) {
                if (m.getQmCode() != null && !m.getQmCode().isEmpty()) {
                    qmToMid.put(m.getQmCode(), m.getMaterialId());
                }
            }
            if (qmToMid.isEmpty()) return map;

            List<String> qmCodes = new ArrayList<>(qmToMid.keySet());
            String inClause = qmCodes.stream().map(c -> "'" + c.replace("'", "''") + "'")
                    .collect(Collectors.joining(","));

            String sql = "SELECT item_code, COALESCE(SUM(sales_quantity - COALESCE(return_quantity, 0)), 0) AS total_qty, unit " +
                    "FROM dwd.store_item_sales WHERE warehouse_code = ? AND stat_month = ?::date " +
                    "AND item_code IN (" + inClause + ") GROUP BY item_code, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, warehouseCode, taskMonth + "-01");

            for (Map<String, Object> row : rows) {
                String itemCode = (String) row.get("item_code");
                String pgUnit = (String) row.get("unit");
                BigDecimal qty = toBigDecimal(row.get("total_qty"));
                String mid = qmToMid.get(itemCode);
                if (mid != null) {
                    BigDecimal converted = convertToBaseUnit(mid, pgUnit, qty, ruleMap);
                    map.merge(mid, converted, BigDecimal::add);
                }
            }
        } catch (Exception e) {
            log.warn("查询 PG 消耗失败: {}", e.getMessage());
        }
        return map;
    }

    /** 调货净值（调入-调出），按物料名匹配 */
    private Map<String, BigDecimal> queryTransferNet(String storeId, String taskMonth) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            YearMonth ym = YearMonth.parse(taskMonth);
            String start = ym.atDay(1).toString();
            String end = ym.plusMonths(1).atDay(1).toString();

            // 调出：本店是调出方，按收货时间(received_at)过滤
            String outSql = "SELECT oi.material_name, COALESCE(SUM(oi.base_qty), 0) AS qty " +
                    "FROM transfer_order_item oi JOIN transfer_order o ON oi.transfer_id = o.id " +
                    "WHERE o.from_store_id = ? AND o.status IN ('completed','returned') " +
                    "AND o.received_at >= ? AND o.received_at < ? AND oi.del_flag = 0 AND o.del_flag = 0 " +
                    "GROUP BY oi.material_name";
            List<Map<String, Object>> outRows = mysqlJdbc.queryForList(outSql, storeId, start, end);
            for (Map<String, Object> row : outRows) {
                String name = (String) row.get("material_name");
                map.merge(name, toBigDecimal(row.get("qty")).negate(), BigDecimal::add);
            }

            // 调入：本店是调入方，按收货时间(received_at)过滤
            String inSql = "SELECT oi.material_name, COALESCE(SUM(oi.base_qty), 0) AS qty " +
                    "FROM transfer_order_item oi JOIN transfer_order o ON oi.transfer_id = o.id " +
                    "WHERE o.to_store_id = ? AND o.status IN ('completed','returned') " +
                    "AND o.received_at >= ? AND o.received_at < ? AND oi.del_flag = 0 AND o.del_flag = 0 " +
                    "GROUP BY oi.material_name";
            List<Map<String, Object>> inRows = mysqlJdbc.queryForList(inSql, storeId, start, end);
            for (Map<String, Object> row : inRows) {
                String name = (String) row.get("material_name");
                map.merge(name, toBigDecimal(row.get("qty")), BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("查询调货净值失败: {}", e.getMessage());
        }
        return map;
    }

    /** 还货净值：return_qty 按物料名匹配 */
    private Map<String, BigDecimal> queryReturnNet(String storeId, String taskMonth) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            YearMonth ym = YearMonth.parse(taskMonth);
            String start = ym.atDay(1).toString();
            String end = ym.plusMonths(1).atDay(1).toString();

            // 还入：本店是调出方，物料被还回来（+），按还货记录创建时间(rr.created_at)过滤
            String inSql = "SELECT oi.material_name, COALESCE(SUM(rr.return_qty), 0) AS qty " +
                    "FROM transfer_return_record rr " +
                    "JOIN transfer_order_item oi ON rr.item_id = oi.id AND oi.del_flag = 0 " +
                    "JOIN transfer_order o ON rr.transfer_id = o.id AND o.del_flag = 0 " +
                    "WHERE o.from_store_id = ? AND rr.return_type = 'goods' " +
                    "AND rr.created_at >= ? AND rr.created_at < ? " +
                    "GROUP BY oi.material_name";
            List<Map<String, Object>> inRows = mysqlJdbc.queryForList(inSql, storeId, start, end);
            for (Map<String, Object> row : inRows) {
                String name = (String) row.get("material_name");
                map.merge(name, toBigDecimal(row.get("qty")), BigDecimal::add);
            }

            // 还出：本店是调入方，需要还物料回去（-），按还货记录创建时间(rr.created_at)过滤
            String outSql = "SELECT oi.material_name, COALESCE(SUM(rr.return_qty), 0) AS qty " +
                    "FROM transfer_return_record rr " +
                    "JOIN transfer_order_item oi ON rr.item_id = oi.id AND oi.del_flag = 0 " +
                    "JOIN transfer_order o ON rr.transfer_id = o.id AND o.del_flag = 0 " +
                    "WHERE o.to_store_id = ? AND rr.return_type = 'goods' " +
                    "AND rr.created_at >= ? AND rr.created_at < ? " +
                    "GROUP BY oi.material_name";
            List<Map<String, Object>> outRows = mysqlJdbc.queryForList(outSql, storeId, start, end);
            for (Map<String, Object> row : outRows) {
                String name = (String) row.get("material_name");
                map.merge(name, toBigDecimal(row.get("qty")).negate(), BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("查询还货净值失败: {}", e.getMessage());
        }
        return map;
    }

    /**
     * 报损数量
     *  到货报损(arrival)：先减后加，净额对冲
     *  日常报损(daily)：只减，completed + occurred_date 本月
     */
    private Map<String, BigDecimal> queryLoss(String storeId, String taskMonth,
                                               Map<String, String> categoryGroupMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            YearMonth ym = YearMonth.parse(taskMonth);
            String start = ym.atDay(1).toString();
            String end = ym.plusMonths(1).atDay(1).toString();

            String sql = "SELECT lr.material_id, CASE WHEN lr.base_qty IS NOT NULL AND lr.base_qty > 0 THEN lr.base_qty ELSE lr.input_qty END AS base_qty, " +
                    "lr.status, lr.loss_type, lr.occurred_date, lr.completed_at, m.category " +
                    "FROM loss_report lr LEFT JOIN material m ON lr.material_id = m.material_id " +
                    "WHERE lr.store_id = ? AND lr.del_flag = 0 " +
                    "AND (lr.occurred_date >= ? AND lr.occurred_date < ? " +
                    "     OR (lr.completed_at >= ? AND lr.completed_at < ?))";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql,
                    storeId, start, end, start, end);

            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String status = (String) row.get("status");
                String lossType = (String) row.get("loss_type");
                String category = (String) row.get("category");
                BigDecimal qty = toBigDecimal(row.get("base_qty"));
                Object occurredDate = row.get("occurred_date");
                Object completedAt = row.get("completed_at");

                if (mid == null || qty.compareTo(BigDecimal.ZERO) == 0) continue;

                // 日常报损：completed + occurred_date 本月 → 只减
                if ("daily".equals(lossType)) {
                    if ("completed".equals(status) && isInMonth(occurredDate, start, end)) {
                        map.merge(mid, qty, BigDecimal::add);
                    }
                    continue;
                }

                // 到货报损
                boolean isFruitVeg = isFruitVegMaterial(category, categoryGroupMap);
                if (isFruitVeg) {
                    if ("registered".equals(status) && isInMonth(occurredDate, start, end)) {
                        map.merge(mid, qty, BigDecimal::add);
                    }
                } else {
                    if (("confirmed_resend".equals(status) || "received".equals(status) || "completed".equals(status))
                            && isInMonth(occurredDate, start, end)) {
                        map.merge(mid, qty, BigDecimal::add);
                    }
                    if ("completed".equals(status) && isInMonth(completedAt, start, end)) {
                        map.merge(mid, qty.negate(), BigDecimal::add);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询报损失败: {}", e.getMessage());
        }
        return map;
    }

    /** 判断日期是否在 [start, end) 区间内 */
    private boolean isInMonth(Object dateObj, String start, String end) {
        if (dateObj == null) return false;
        try {
            String dateStr;
            if (dateObj instanceof java.sql.Date) {
                dateStr = dateObj.toString();
            } else if (dateObj instanceof java.time.LocalDate) {
                dateStr = dateObj.toString();
            } else {
                dateStr = dateObj.toString().substring(0, 10);
            }
            return dateStr.compareTo(start) >= 0 && dateStr.compareTo(end) < 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 自购食材：SUM(purchase_qty) WHERE purchase_month */
    private Map<String, BigDecimal> querySelfPurchase(String storeId, String taskMonth) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            String sql = "SELECT material_id, COALESCE(SUM(purchase_qty), 0) AS qty FROM self_purchase_material " +
                    "WHERE store_id = ? AND purchase_month = ? AND del_flag = 0 " +
                    "GROUP BY material_id";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql, storeId, taskMonth);
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                if (mid != null) {
                    map.put(mid, toBigDecimal(row.get("qty")));
                }
            }
        } catch (Exception e) {
            log.warn("查询自购食材失败: {}", e.getMessage());
        }
        return map;
    }

    // ==================== 单位换算 ====================

    /**
     * 将 PG 数据的入库数量换算为基础单位数量
     */
    private BigDecimal convertToBaseUnit(String materialId, String pgUnit, BigDecimal qty,
                                          Map<String, MaterialRuleResp> ruleMap) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        if (pgUnit == null || pgUnit.isEmpty()) return qty;

        MaterialRuleResp rule = ruleMap.get(materialId);
        if (rule == null || rule.getBaseUnit() == null) return qty;
        String baseUnit = rule.getBaseUnit();
        if (pgUnit.equalsIgnoreCase(baseUnit)) return qty;

        // 查找换算规则：pgUnit → baseUnit
        // 先在单位换算中找
        if (rule.getConversions() != null) {
            for (MaterialRuleResp.UnitConversionItem conv : rule.getConversions()) {
                if (pgUnit.equalsIgnoreCase(conv.getFromUnit()) && baseUnit.equalsIgnoreCase(conv.getToUnit())) {
                    // qty * (toQuantity / fromQuantity)  -- 例如 3箱 * (12瓶/1箱) = 36瓶
                    return qty.multiply(conv.getToQuantity()).divide(conv.getFromQuantity(), 4, RoundingMode.HALF_UP);
                }
                if (pgUnit.equalsIgnoreCase(conv.getToUnit()) && baseUnit.equalsIgnoreCase(conv.getFromUnit())) {
                    return qty.multiply(conv.getFromQuantity()).divide(conv.getToQuantity(), 4, RoundingMode.HALF_UP);
                }
            }
        }
        // 在称重换算中找
        if (rule.getWeightConversions() != null) {
            for (MaterialRuleResp.WeightConversionItem wc : rule.getWeightConversions()) {
                if (pgUnit.equalsIgnoreCase(wc.getWeightUnit()) && baseUnit.equalsIgnoreCase(wc.getCountUnit())) {
                    return qty.multiply(wc.getCountQuantity()).divide(wc.getWeightQuantity(), 4, RoundingMode.HALF_UP);
                }
            }
        }

        // 没找到换算规则，直接返回原值
        log.warn("物料 {} 未找到换算: pgUnit={} baseUnit={} convSize={} weightSize={}，使用原值qty={}",
                materialId, pgUnit, baseUnit,
                rule.getConversions() != null ? rule.getConversions().size() : 0,
                rule.getWeightConversions() != null ? rule.getWeightConversions().size() : 0,
                qty);
        return qty;
    }

    // ==================== 水果蔬菜分类 ====================

    /**
     * 从 loss_notify_card_config 加载分类→分组映射
     */
    private Map<String, String> loadCategoryGroupMap() {
        Map<String, String> map = new HashMap<>();
        try {
            List<Map<String, Object>> configs = mysqlJdbc.queryForList(
                    "SELECT DISTINCT category FROM loss_notify_card_config WHERE status=1 AND category != '其他类'");
            for (Map<String, Object> cfg : configs) {
                String cats = (String) cfg.get("category");
                if (cats != null && !cats.isEmpty()) {
                    for (String c : cats.split(",")) {
                        String tc = c.trim();
                        if (!tc.isEmpty()) map.put(tc, cats);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载分类分组映射失败: {}", e.getMessage());
        }
        return map;
    }

    /**
     * 判断物料是否属于水果蔬菜类
     * 使用与 LossReportH5Controller 相同的分组逻辑
     */
    private boolean isFruitVegMaterial(String materialCategory, Map<String, String> categoryGroupMap) {
        if (materialCategory == null || materialCategory.isEmpty()) return false;
        if (categoryGroupMap.isEmpty()) return false;
        String groupKey = categoryGroupMap.getOrDefault(materialCategory.trim(), "其他类");
        // 水果蔬菜类报损只减不增（发券方式补偿）
        return "水果蔬菜".equals(groupKey);
    }

    // ==================== 异步批量自动计算 ====================

    @Async
    public void autoCalcUncounted() {
        log.warn("autoCalcUncounted 被调用，running={}", autoCalcRunning);
        if (autoCalcRunning) { log.warn("自动计算已在运行中，跳过"); return; }
        autoCalcRunning = true;
        try {
            doAutoCalc();
        } finally {
            autoCalcRunning = false;
        }
    }

    private void doAutoCalc() {
        synchronized (calcLock) {
        List<Map<String, Object>> tasks = mysqlJdbc.queryForList(
                "SELECT t.id, t.store_id, t.warehouse_code, t.task_month, t.submitted_at " +
                "FROM task t WHERE t.status = 'submitted' AND t.del_flag = 0 AND t.task_type = 'monthly' " +
                "AND NOT EXISTS (SELECT 1 FROM inventory_difference d WHERE d.task_id = t.id AND d.del_flag = 0)");

        log.warn("doAutoCalc: 查询到 {} 个未计算任务", tasks.size());
        if (tasks.isEmpty()) return;

        // 收集所有 taskId、materialId、warehouseCode、taskMonth
        List<Integer> taskIds = new ArrayList<>();
        Set<String> allStoreIds = new HashSet<>();
        Set<String> allWhCodes = new HashSet<>();
        Set<String> allMonths = new HashSet<>();
        for (Map<String, Object> t : tasks) {
            taskIds.add((Integer) t.get("id"));
            if (t.get("store_id") != null) allStoreIds.add((String) t.get("store_id"));
            if (t.get("warehouse_code") != null) allWhCodes.add((String) t.get("warehouse_code"));
            if (t.get("task_month") != null) allMonths.add((String) t.get("task_month"));
        }

        // 批量加载所有任务的物料汇总
        List<TaskMaterialSummary> allSummaries = summaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().in(TaskMaterialSummary::getTaskId, taskIds));
        Map<Integer, List<TaskMaterialSummary>> taskSummaryMap = allSummaries.stream()
                .collect(Collectors.groupingBy(TaskMaterialSummary::getTaskId));

        // 收集所有 materialId
        Set<String> allMatIds = allSummaries.stream().map(TaskMaterialSummary::getMaterialId).collect(Collectors.toSet());
        List<String> matIdList = new ArrayList<>(allMatIds);

        // 批量加载物料和规则（按 material_id 字段查，不是自增 id）
        Map<String, Material> materialMap = materialMapper.selectList(
                new LambdaQueryWrapper<Material>().in(Material::getMaterialId, matIdList))
                .stream().collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));
        Map<String, MaterialRuleResp> ruleMap;
        try { ruleMap = matIdList.isEmpty() ? Map.of() : materialRuleService.batchDetail(matIdList); }
        catch (Exception e) { log.warn("加载盘点规则失败: {}", e.getMessage()); ruleMap = Map.of(); }
        Map<String, String> categoryGroupMap = loadCategoryGroupMap();
        double threshold = getThresholdRate();

        // ---- 批量查询 PG（每个表一次）----
        // qmCode → materialId
        Map<String, String> qmToMid = new HashMap<>();
        log.warn("PG查询参数: whCodes={}, months={}, qmCount={}", allWhCodes, allMonths, matIdList.size());
        for (Material m : materialMap.values()) {
            if (m.getQmCode() != null && !m.getQmCode().isEmpty()) qmToMid.put(m.getQmCode(), m.getMaterialId());
        }

        try { log.warn("PG连接验证: url={}", pgJdbc.getDataSource().getConnection().getMetaData().getURL()); }
        catch (Exception ex) { log.warn("PG连接验证失败: {}", ex.getMessage()); }
        Map<String, BigDecimal> purchaseMap = batchQueryPg("dwd.purchase", allWhCodes, allMonths, qmToMid, ruleMap, "purchase_quantity");
        Map<String, BigDecimal> orderMap = batchQueryPg("dwd.purchase_order", allWhCodes, allMonths, qmToMid, ruleMap, "order_quantity");
        Map<String, BigDecimal> consumptionMap = batchQueryPgConsumption(allWhCodes, allMonths, qmToMid, ruleMap);

        // ---- 批量查询 MySQL ----å
        Map<String, BigDecimal> transferMap = batchQueryTransfer(allStoreIds, allMonths, qmToMid, ruleMap);
        Map<String, BigDecimal> returnMap = batchQueryReturn(allStoreIds, allMonths);
        Map<String, BigDecimal> lossMap = batchQueryLoss(allStoreIds, allMonths, categoryGroupMap);
        Map<String, BigDecimal> selfPurchaseMap = batchQuerySelfPurchase(allStoreIds, allMonths);

        // ---- 逐个任务计算并批量写入 ----
        List<InventoryDifference> allDiffs = new ArrayList<>();
        for (Map<String, Object> t : tasks) {
            int taskId = (Integer) t.get("id");
            String storeId = (String) t.get("store_id");
            String taskMonth = (String) t.get("task_month");
            String prevMonth = previousMonth(taskMonth);
            List<TaskMaterialSummary> summaries = taskSummaryMap.getOrDefault(taskId, List.of());

            // 上月剩余：批量查一次所有 stores + months 的组合
            Set<String> prevMonths = previousMonths(allMonths);
            log.warn("上月剩余查询: storeIds={}, prevMonths={}, matCount={}", allStoreIds, prevMonths, matIdList.size());
            Map<String, BigDecimal> lastMonthMap = batchQueryLastMonth(allStoreIds, prevMonths, matIdList);
            log.warn("上月剩余结果: {} 条", lastMonthMap.size());

            for (TaskMaterialSummary sm : summaries) {
                String mid = sm.getMaterialId();
                String key = mid + "|" + storeId + "|" + taskMonth;

                BigDecimal lastMonth = lastMonthMap.getOrDefault(mid + "|" + storeId + "|" + prevMonth, BigDecimal.ZERO);
                // PG 按 material_id 关联
                BigDecimal purchase = purchaseMap.getOrDefault(mid + "|" + taskMonth, BigDecimal.ZERO);
                BigDecimal order = orderMap.getOrDefault(mid + "|" + taskMonth, BigDecimal.ZERO);
                BigDecimal consumption = consumptionMap.getOrDefault(mid + "|" + taskMonth, BigDecimal.ZERO);
                // 调货/还货按 material_id 关联
                BigDecimal transferNet = transferMap.getOrDefault(mid + "|" + storeId + "|" + taskMonth, BigDecimal.ZERO);
                BigDecimal returnNet = returnMap.getOrDefault(mid + "|" + storeId + "|" + taskMonth, BigDecimal.ZERO);
                // 报损/自购按 material_id 关联
                BigDecimal loss = lossMap.getOrDefault(mid + "|" + storeId + "|" + taskMonth, BigDecimal.ZERO);
                BigDecimal selfPurchase = selfPurchaseMap.getOrDefault(mid + "|" + storeId + "|" + taskMonth, BigDecimal.ZERO);

                BigDecimal theoretical = lastMonth.add(purchase).add(order).add(transferNet).add(returnNet)
                        .subtract(loss).add(selfPurchase).subtract(consumption);
                BigDecimal actual = sm.getAdjustedQty() != null ? sm.getAdjustedQty() : sm.getTotalQty();
                BigDecimal diff = actual.subtract(theoretical);
                BigDecimal diffRate = BigDecimal.ZERO;
                if (theoretical.compareTo(BigDecimal.ZERO) != 0)
                    diffRate = diff.abs().divide(theoretical.abs(), 4, RoundingMode.HALF_UP);
                boolean isLarge = diffRate.compareTo(BigDecimal.valueOf(threshold)) > 0;

                InventoryDifference d = new InventoryDifference();
                d.setTaskId(taskId);
                d.setMaterialId(mid);
                d.setMaterialName(sm.getMaterialName());
                d.setSpec(sm.getSpec());
                d.setUnit(sm.getBaseUnit());
                d.setLastMonthQty(lastMonth);
                d.setPurchaseQty(purchase);
                d.setOrderQty(order);
                d.setConsumptionQty(consumption);
                d.setTransferNetQty(transferNet);
                d.setReturnQty(returnNet);
                d.setLossQty(loss);
                d.setSelfPurchaseQty(selfPurchase);
                d.setTheoreticalQty(theoretical);
                d.setActualQty(actual);
                d.setDiffQty(diff);
                d.setDiffRate(diffRate);
                d.setIsLarge(isLarge ? 1 : 0);
                d.setOriginalIsLarge(isLarge ? 1 : 0);
                d.setStatus("pending");
                d.setCreatedAt(LocalDateTime.now());
                d.setUpdatedAt(LocalDateTime.now());
                allDiffs.add(d);
            }
        }

        // 批量写入
        if (!allDiffs.isEmpty()) {
            diffMapper.insertBatch(allDiffs);
        }
        log.warn("批量计算完成，共写入 {} 条差异项。PG采购:{} 订货:{} 消耗:{} 调货:{} 还货:{} 报损:{} 自购:{}",
                allDiffs.size(),
                purchaseMap.size(), orderMap.size(), consumptionMap.size(),
                transferMap.size(), returnMap.size(), lossMap.size(), selfPurchaseMap.size());
        }
    }

    // ---- 批量 MySQL 查询 ----

    private Map<String, BigDecimal> batchQueryTransfer(Set<String> storeIds, Set<String> months,
                                                         Map<String, String> qmToMid, Map<String, MaterialRuleResp> ruleMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty() || months.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));

            // 调出+调入合并查询，按 material_id 聚合
            String sql = "SELECT oi.material_id, oi.transfer_qty, oi.unit, o.from_store_id, o.to_store_id, " +
                    "DATE_FORMAT(o.received_at,'%Y-%m') AS mth " +
                    "FROM transfer_order_item oi JOIN transfer_order o ON oi.transfer_id = o.id " +
                    "WHERE (o.from_store_id IN (" + inStores + ") OR o.to_store_id IN (" + inStores + ")) " +
                    "AND o.status IN ('completed','returned') " +
                    "AND o.received_at >= ? AND o.received_at < ? " +
                    "AND oi.del_flag = 0 AND o.del_flag = 0";
            String firstMonth = months.stream().sorted().findFirst().orElse("1970-01") + "-01";
            String lastMonth = months.stream().sorted().reduce((a, b) -> b).orElse("2099-12");
            String endDate = YearMonth.parse(lastMonth).plusMonths(1).atDay(1).toString();
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql, firstMonth, endDate);
            log.warn("batchQueryTransfer 查询到 {} 行", rows.size());

            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String matName = (String) row.get("material_name");
                String fromSid = (String) row.get("from_store_id");
                String toSid = (String) row.get("to_store_id");
                String mth = (String) row.get("mth");
                String unit = (String) row.get("unit");
                BigDecimal rawQty = toBigDecimal(row.get("transfer_qty"));
                // material_id 为 NULL 时用 material_name 兜底
                if (mid == null && matName != null) mid = qmToMid.get(matName);
                if (mid == null) continue;
                if (rawQty.compareTo(BigDecimal.ZERO) == 0) continue;
                if (!months.contains(mth)) { log.warn("transfer skip: mth={} not in months", mth); continue; }

                // 换算到最小单位
                BigDecimal qty = convertToBaseUnit(mid, unit, rawQty, ruleMap);

                if (storeIds.contains(fromSid)) {
                    map.merge(mid + "|" + fromSid + "|" + mth, qty.negate(), BigDecimal::add);
                }
                if (storeIds.contains(toSid)) {
                    map.merge(mid + "|" + toSid + "|" + mth, qty, BigDecimal::add);
                }
            }
        } catch (Exception e) {
            log.warn("批量查询调货失败: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, BigDecimal> batchQueryReturn(Set<String> storeIds, Set<String> months) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty() || months.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));

            // 还入+还出合并查询，按 material_id 聚合
            String sql = "SELECT oi.material_id, o.from_store_id, o.to_store_id, " +
                    "rr.return_qty, DATE_FORMAT(rr.created_at,'%Y-%m') AS mth " +
                    "FROM transfer_return_record rr " +
                    "JOIN transfer_order_item oi ON rr.item_id = oi.id AND oi.del_flag = 0 " +
                    "JOIN transfer_order o ON rr.transfer_id = o.id AND o.del_flag = 0 " +
                    "WHERE (o.from_store_id IN (" + inStores + ") OR o.to_store_id IN (" + inStores + ")) " +
                    "AND rr.return_type = 'goods' " +
                    "AND rr.created_at >= ? AND rr.created_at < ?";
            String firstMonth = months.stream().sorted().findFirst().orElse("1970-01") + "-01";
            String lastMonth = months.stream().sorted().reduce((a, b) -> b).orElse("2099-12");
            String endDate = YearMonth.parse(lastMonth).plusMonths(1).atDay(1).toString();
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql, firstMonth, endDate);

            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String fromSid = (String) row.get("from_store_id");
                String toSid = (String) row.get("to_store_id");
                String mth = (String) row.get("mth");
                BigDecimal retQty = toBigDecimal(row.get("return_qty"));
                if (mid == null || retQty.compareTo(BigDecimal.ZERO) == 0) continue;
                if (!months.contains(mth)) continue;

                // 还入（from_store → 物料被还回，+）
                if (storeIds.contains(fromSid)) {
                    map.merge(mid + "|" + fromSid + "|" + mth, retQty, BigDecimal::add);
                }
                // 还出（to_store → 还物料回去，-）
                if (storeIds.contains(toSid)) {
                    map.merge(mid + "|" + toSid + "|" + mth, retQty.negate(), BigDecimal::add);
                }
            }
        } catch (Exception e) {
            log.warn("批量查询还货失败: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, BigDecimal> batchQueryLoss(Set<String> storeIds, Set<String> months,
                                                     Map<String, String> categoryGroupMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty() || months.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
            String inMonths = months.stream().map(m -> "'" + m + "-01'").collect(Collectors.joining(","));

            String sql = "SELECT lr.material_id, CASE WHEN lr.base_qty IS NOT NULL AND lr.base_qty > 0 THEN lr.base_qty ELSE lr.input_qty END AS base_qty, " +
                    "lr.status, lr.loss_type, lr.occurred_date, lr.completed_at, " +
                    "m.category, lr.store_id, DATE_FORMAT(lr.occurred_date,'%Y-%m') AS mth " +
                    "FROM loss_report lr LEFT JOIN material m ON lr.material_id = m.material_id " +
                    "WHERE lr.store_id IN (" + inStores + ") AND lr.del_flag = 0 " +
                    "AND (DATE_FORMAT(lr.occurred_date,'%Y-%m') IN (" + months.stream().map(m -> "'" + m + "'").collect(Collectors.joining(",")) + ") " +
                    "     OR DATE_FORMAT(lr.completed_at,'%Y-%m') IN (" + months.stream().map(m -> "'" + m + "'").collect(Collectors.joining(",")) + "))";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql);
            log.warn("batchQueryLoss 查询到 {} 行, months={}", rows.size(), months);
            int dailyMatch = 0, arrivalMatch = 0, skipped = 0;
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String status = (String) row.get("status");
                String lossType = (String) row.get("loss_type");
                String category = (String) row.get("category");
                String sid = (String) row.get("store_id");
                BigDecimal qty = toBigDecimal(row.get("base_qty"));
                String occMth = fmtMonth(row.get("occurred_date"));
                String compMth = fmtMonth(row.get("completed_at"));
                if (mid == null) { log.warn("loss skip: material_id is null, lossType={} status={}", lossType, status); skipped++; continue; }
                if (qty.compareTo(BigDecimal.ZERO) == 0) { log.warn("loss skip: qty=0 for mid={} lossType={} status={}", mid, lossType, status); skipped++; continue; }

                // 日常报损：completed + occurred_date 本月 → 只减
                if ("daily".equals(lossType)) {
                    if ("completed".equals(status) && months.contains(occMth)) {
                        map.merge(mid + "|" + sid + "|" + occMth, qty, BigDecimal::add);
                        dailyMatch++;
                    }
                    continue;
                }

                // 到货报损
                boolean isFruitVeg = isFruitVegMaterial(category, categoryGroupMap);
                if (isFruitVeg && "registered".equals(status) && months.contains(occMth)) {
                    map.merge(mid + "|" + sid + "|" + occMth, qty, BigDecimal::add);
                } else if (!isFruitVeg) {
                    if (("confirmed_resend".equals(status) || "received".equals(status) || "completed".equals(status))
                            && months.contains(occMth)) {
                        map.merge(mid + "|" + sid + "|" + occMth, qty, BigDecimal::add);
                    }
                    if ("completed".equals(status) && months.contains(compMth)) {
                        map.merge(mid + "|" + sid + "|" + compMth, qty.negate(), BigDecimal::add);
                    }
                }
            }
            log.warn("batchQueryLoss: dailyMatch={} arrivalMatch={} skipped={} mapSize={}", dailyMatch, arrivalMatch, skipped, map.size());
        } catch (Exception e) {
            log.warn("批量查询报损失败: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, BigDecimal> batchQuerySelfPurchase(Set<String> storeIds, Set<String> months) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty() || months.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
            String inMonths = months.stream().map(m -> "'" + m + "'").collect(Collectors.joining(","));

            String sql = "SELECT material_id, store_id, purchase_month, COALESCE(SUM(purchase_qty), 0) AS qty " +
                    "FROM self_purchase_material WHERE store_id IN (" + inStores + ") " +
                    "AND purchase_month IN (" + inMonths + ") AND del_flag = 0 " +
                    "GROUP BY material_id, store_id, purchase_month";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String sid = (String) row.get("store_id");
                String mth = (String) row.get("purchase_month");
                if (mid != null) map.merge(mid + "|" + sid + "|" + mth, toBigDecimal(row.get("qty")), BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("批量查询自购失败: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, BigDecimal> batchQueryLastMonth(Set<String> storeIds, Set<String> prevMonths, List<String> matIds) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (storeIds.isEmpty() || prevMonths.isEmpty() || matIds.isEmpty()) return map;
        try {
            String inStores = storeIds.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","));
            String inMonths = prevMonths.stream().map(m -> "'" + m + "'").collect(Collectors.joining(","));
            String inMats = matIds.stream().map(m -> "'" + m + "'").collect(Collectors.joining(","));

            String sql = "SELECT sm.material_id, t.store_id, t.task_month, COALESCE(SUM(sm.adjusted_qty), 0) AS qty " +
                    "FROM task_material_summary sm JOIN task t ON sm.task_id = t.id " +
                    "WHERE t.store_id IN (" + inStores + ") AND t.task_month IN (" + inMonths + ") " +
                    "AND sm.material_id IN (" + inMats + ") AND t.status = 'submitted' AND t.task_type = 'monthly' AND sm.del_flag = 0 AND t.del_flag = 0 " +
                    "GROUP BY sm.material_id, t.store_id, t.task_month";
            List<Map<String, Object>> rows = mysqlJdbc.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                String sid = (String) row.get("store_id");
                String mth = (String) row.get("task_month");
                map.merge(mid + "|" + sid + "|" + mth, toBigDecimal(row.get("qty")), BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("批量查询上月剩余失败: {}", e.getMessage());
        }
        return map;
    }

    // ---- 批量 PG 查询 ----

    private Map<String, BigDecimal> batchQueryPg(String table, Set<String> whCodes, Set<String> months,
                                                   Map<String, String> qmToMid, Map<String, MaterialRuleResp> ruleMap,
                                                   String qtyField) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (whCodes.isEmpty() || months.isEmpty() || qmToMid.isEmpty()) {
            log.warn("PG {} 跳过: whEmpty={} monthEmpty={} qmEmpty={}", table, whCodes.isEmpty(), months.isEmpty(), qmToMid.isEmpty());
            return map;
        }
        try {
            String inWh = whCodes.stream().map(w -> "'" + w + "'").collect(Collectors.joining(","));
            String inMonths = months.stream().map(m -> "'" + m + "-01'::date").collect(Collectors.joining(","));
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));

            log.warn("PG查询 {}: wh={} month={} itemCount={}", table, inWh, inMonths, qmToMid.size());

            String sql = "SELECT item_code, warehouse_code, TO_CHAR(stat_month,'YYYY-MM') AS mth, " +
                    "COALESCE(SUM(" + qtyField + "), 0) AS total_qty, unit FROM " + table +
                    " WHERE warehouse_code IN (" + inWh + ") AND stat_month IN (" + inMonths + ") " +
                    "AND item_code IN (" + inItems + ") GROUP BY item_code, warehouse_code, mth, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql);
            log.warn("PG {} 返回 {} 行，qmToMid 有 {} 个映射", table, rows.size(), qmToMid.size());
            int matched = 0;
            for (Map<String, Object> row : rows) {
                String itemCode = (String) row.get("item_code");
                String pgUnit = (String) row.get("unit");
                String mth = (String) row.get("mth");
                String mid = qmToMid.get(itemCode);
                if (mid != null) {
                    BigDecimal qty = toBigDecimal(row.get("total_qty"));
                    qty = convertToBaseUnit(mid, pgUnit, qty, ruleMap);
                    map.merge(mid + "|" + mth, qty, BigDecimal::add);
                    matched++;
                }
            }
            log.warn("PG {} 匹配成功 {}/{}", table, matched, rows.size());
        } catch (Exception e) {
            log.warn("批量查询 PG {} 失败: {} SQL: {}", table, e.getMessage(),
                    "SELECT ... FROM " + table + " WHERE wh IN (" + (whCodes.isEmpty() ? "" : whCodes.stream().map(w -> "'" + w + "'").collect(Collectors.joining(","))) + ")");
        }
        return map;
    }

    private Map<String, BigDecimal> batchQueryPgConsumption(Set<String> whCodes, Set<String> months,
                                                              Map<String, String> qmToMid, Map<String, MaterialRuleResp> ruleMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (whCodes.isEmpty() || months.isEmpty() || qmToMid.isEmpty()) return map;
        try {
            String inWh = whCodes.stream().map(w -> "'" + w + "'").collect(Collectors.joining(","));
            String inMonths = months.stream().map(m -> "'" + m + "-01'::date").collect(Collectors.joining(","));
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));

            log.warn("PG查询消耗: wh={} month={} itemCount={}", inWh, inMonths, qmToMid.size());

            String sql = "SELECT item_code, warehouse_code, TO_CHAR(stat_month,'YYYY-MM') AS mth, " +
                    "COALESCE(SUM(sales_quantity - COALESCE(return_quantity, 0)), 0) AS total_qty, unit " +
                    "FROM dwd.store_item_sales WHERE warehouse_code IN (" + inWh + ") " +
                    "AND stat_month IN (" + inMonths + ") AND item_code IN (" + inItems + ") " +
                    "GROUP BY item_code, warehouse_code, mth, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql);

            for (Map<String, Object> row : rows) {
                String itemCode = (String) row.get("item_code");
                String pgUnit = (String) row.get("unit");
                String mth = (String) row.get("mth");
                String mid = qmToMid.get(itemCode);
                if (mid != null) {
                    BigDecimal qty = toBigDecimal(row.get("total_qty"));
                    qty = convertToBaseUnit(mid, pgUnit, qty, ruleMap);
                    map.merge(mid + "|" + mth, qty, BigDecimal::add);
                }
            }
        } catch (Exception e) {
            log.warn("批量查询 PG 消耗失败: {}", e.getMessage());
        }
        return map;
    }

    private String fmtMonth(Object d) {
        if (d == null) return null;
        try { return d.toString().substring(0, 7); } catch (Exception e) { return null; }
    }

    private Set<String> previousMonths(Set<String> months) {
        return months.stream().map(this::previousMonth).collect(Collectors.toSet());
    }

    // ==================== 工具方法 ====================

    private String previousMonth(String taskMonth) {
        YearMonth ym = YearMonth.parse(taskMonth);
        return ym.minusMonths(1).toString();
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
