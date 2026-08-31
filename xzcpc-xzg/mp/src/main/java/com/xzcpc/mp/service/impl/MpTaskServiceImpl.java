package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.mp.entity.StoreManagerSession;
import com.xzcpc.mp.mapper.StoreManagerSessionMapper;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.mp.service.MpTaskService;
import com.xzcpc.mp.service.SmartOrderService;
import com.xzcpc.task.entity.*;
import com.xzcpc.task.mapper.*;
import com.xzcpc.task.service.StoreService;
import com.xzcpc.task.service.TaskService;
import com.xzcpc.template.dto.MaterialRuleResp;
import com.xzcpc.template.service.MaterialRuleService;
import com.xzcpc.template.entity.Template;
import com.xzcpc.template.mapper.TemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpTaskServiceImpl implements MpTaskService {

    private final TaskMapper taskMapper;
    private final TaskZoneMapper taskZoneMapper;
    private final TaskZoneMaterialMapper taskZoneMaterialMapper;
    private final TaskMaterialSummaryMapper taskMaterialSummaryMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TaskService taskService;
    private final TemplateMapper templateMapper;
    private final StoreService storeService;
    private final StoreManagerSessionMapper sessionMapper;
    private final MaterialRuleService materialRuleService;
    private final MpStaffService staffService;
    private final SmartOrderService smartOrderService;

    @Override
    public Map<String, Object> list(String storeId) {
        return list(storeId, false, null);
    }

    @Override
    public Map<String, Object> list(String storeId, boolean all, String openid) {
        requireStore(storeId);
        List<String> storeIds;
        if (all && StringUtils.hasText(openid)) {
            // 查询名下所有门店
            storeIds = staffService.findStoresByOpenid(openid).stream()
                    .map(m -> (String) m.get("storeId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (storeIds.isEmpty()) {
                storeIds = List.of(storeId);
            }
        } else {
            storeIds = List.of(storeId);
        }

        List<Task> allTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .in(Task::getStoreId, storeIds)
                        .orderByDesc(Task::getDeadline));

        Map<String, Template> templateMap = templateMapper.selectList(null).stream()
                .collect(Collectors.toMap(t -> t.getId().toString(), t -> t));

        // 预加载所有任务的分区与物料统计，避免 N+1
        List<Integer> taskIds = allTasks.stream().map(Task::getId).collect(Collectors.toList());
        Map<Integer, List<TaskZone>> zonesByTask = taskIds.isEmpty() ? new HashMap<>()
                : taskZoneMapper.selectList(
                        new LambdaQueryWrapper<TaskZone>().in(TaskZone::getTaskId, taskIds))
                        .stream()
                        .collect(Collectors.groupingBy(TaskZone::getTaskId));
        Map<Integer, List<TaskZoneMaterial>> materialsByTask = taskIds.isEmpty() ? new HashMap<>()
                : taskZoneMaterialMapper.selectList(
                        new LambdaQueryWrapper<TaskZoneMaterial>().in(TaskZoneMaterial::getTaskId, taskIds))
                        .stream()
                        .collect(Collectors.groupingBy(TaskZoneMaterial::getTaskId));

        List<Map<String, Object>> current = new ArrayList<>();
        List<Map<String, Object>> history = new ArrayList<>();

        for (Task task : allTasks) {
            Map<String, Object> taskMap = buildTaskMap(task, templateMap);
            List<TaskZone> zoneList = zonesByTask.getOrDefault(task.getId(), List.of());
            List<TaskZoneMaterial> materialList = materialsByTask.getOrDefault(task.getId(), List.of());
            int totalMaterials = materialList.size();
            int enteredMaterials = (int) materialList.stream()
                    .filter(m -> m.getInputStatus() != null && !"not_entered".equals(m.getInputStatus()))
                    .count();
            taskMap.put("zoneCount", zoneList.size());
            taskMap.put("totalZones", zoneList.size());
            taskMap.put("totalMaterials", totalMaterials);
            taskMap.put("enteredMaterials", enteredMaterials);
            // P0: submitted → history; expired → history (提示"任务已过期"); others → current
            boolean expired = task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now());
            if ("submitted".equals(task.getStatus())) {
                history.add(taskMap);
            } else if (expired) {
                if (!"overdue".equals(task.getStatus())) {
                    taskMap.put("status", "overdue");
                }
                history.add(taskMap);
            } else {
                current.add(taskMap);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", current);
        result.put("history", history);
        return result;
    }

    @Override
    public Map<String, Object> detail(Integer taskId, String storeId) {
        Task task = getTaskById(taskId, storeId);
        fillStoreInfo(task);

        List<TaskZone> zones = taskZoneMapper.selectList(
                new LambdaQueryWrapper<TaskZone>()
                        .eq(TaskZone::getTaskId, taskId)
                        .orderByAsc(TaskZone::getSortNo));

        List<TaskZoneMaterial> allMaterials = zones.isEmpty() ? List.of()
                : taskZoneMaterialMapper.selectList(
                        new LambdaQueryWrapper<TaskZoneMaterial>()
                                .eq(TaskZoneMaterial::getTaskId, taskId));

        Map<Integer, List<TaskZoneMaterial>> materialsByZone = allMaterials.stream()
                .collect(Collectors.groupingBy(TaskZoneMaterial::getTaskZoneId));

        int totalZones = zones.size();
        int completedZones = 0;
        List<Map<String, Object>> zoneList = new ArrayList<>();

        for (TaskZone zone : zones) {
            List<TaskZoneMaterial> zoneMaterials = materialsByZone.getOrDefault(zone.getId(), List.of());
            long entered = zoneMaterials.stream()
                    .filter(m -> m.getInputStatus() != null && !"not_entered".equals(m.getInputStatus()))
                    .count();
            int total = zoneMaterials.size();
            boolean isComplete = total > 0 && entered >= total;
            if (isComplete) {
                completedZones++;
            }
            Map<String, Object> zm = new LinkedHashMap<>();
            zm.put("taskZoneId", zone.getId());
            zm.put("zoneName", zone.getZoneName());
            zm.put("zoneSaved", zone.getZoneSaved() != null && zone.getZoneSaved() == 1);
            zm.put("isComplete", isComplete);
            zm.put("entered", (int) entered);
            zm.put("total", total);
            zoneList.add(zm);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("taskName", task.getTaskName());
        result.put("taskMonth", task.getTaskMonth());
        result.put("storeId", task.getStoreId());
        result.put("storeName", task.getStoreName());
        result.put("storeCode", task.getStoreCode());
        result.put("xiaochengxuid", task.getXiaochengxuid());
        result.put("warehouseCode", task.getWarehouseCode());
        result.put("status", task.getStatus());
        result.put("deadline", task.getDeadline() != null ? task.getDeadline().toString() : null);
        result.put("isExpired", task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now()));
        result.put("templateId", task.getTemplateId());
        result.put("templateName", task.getTemplateName());
        result.put("totalZones", totalZones);
        result.put("savedZones", completedZones);
        result.put("completedZones", completedZones);
        result.put("zones", zoneList);
        return result;
    }

    @Override
    @Transactional
    public void submit(Integer taskId, String storeId, String openid) {
        Task task = getTaskById(taskId, storeId);

        if ("submitted".equals(task.getStatus())) {
            throw new BusinessException(4032, "任务已提交，不可重复提交");
        }

        // P0: overdue 状态不允许提交
        if ("overdue".equals(task.getStatus())) {
            throw new BusinessException(4033, "任务已逾期，请联系总部延长时间");
        }

        // P0: 只有 pending_submit 或 in_progress 可提交
        if (!"pending_submit".equals(task.getStatus()) && !"in_progress".equals(task.getStatus())) {
            throw new BusinessException(4032, "当前任务状态不可提交");
        }

        if (task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now())) {
            // P0: 过期自动标记为 overdue
            task.setStatus("overdue");
            taskMapper.updateById(task);
            throw new BusinessException(4033, "任务已过截止时间");
        }

        // 将未录入物料批量设为 0（单条 SQL，避免 N 次 updateById）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TaskZoneMaterial> zeroUpdate =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        zeroUpdate.eq(TaskZoneMaterial::getTaskId, taskId)
                .eq(TaskZoneMaterial::getInputStatus, "not_entered")
                .set(TaskZoneMaterial::getInputQty, BigDecimal.ZERO)
                .set(TaskZoneMaterial::getInputStatus, "zero_entered");
        taskZoneMaterialMapper.update(null, zeroUpdate);

        task.setStatus("submitted");
        task.setSubmittedAt(LocalDateTime.now());
        task.setSubmittedBy(openid);
        taskMapper.updateById(task);

        // 更新汇总表（物理删除后重建，含多单位明细，持久化供历史查看）
        jdbcTemplate.update("DELETE FROM task_material_summary WHERE task_id = ?", taskId);

        List<TaskZoneMaterial> allMaterials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>().eq(TaskZoneMaterial::getTaskId, taskId));

        // 按物料聚合并收集多单位明细
        Map<String, TaskMaterialSummary> summaryMap = new LinkedHashMap<>();
        for (TaskZoneMaterial m : allMaterials) {
            BigDecimal qty = snapshotQty(m);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            String materialId = m.getMaterialId();
            String entryUnit = StringUtils.hasText(m.getInputOriginalUnit())
                    ? m.getInputOriginalUnit() : (m.getUnit() != null ? m.getUnit() : "");
            BigDecimal entryQty = m.getInputQty() != null ? m.getInputQty() : BigDecimal.ZERO;
            if (summaryMap.containsKey(materialId)) {
                TaskMaterialSummary sm = summaryMap.get(materialId);
                sm.setTotalQty(sm.getTotalQty().add(qty));
                sm.setOriginalQty(sm.getOriginalQty().add(qty));
                sm.setAdjustedQty(sm.getAdjustedQty().add(qty));
                sm.setZoneCount(sm.getZoneCount() + 1);
                // 合并 unitBreakdown JSON
                sm.setUnitBreakdown(mergeBreakdown(sm.getUnitBreakdown(), entryUnit, entryQty, m));
            } else {
                TaskMaterialSummary sm = new TaskMaterialSummary();
                sm.setTaskId(taskId);
                sm.setMaterialId(materialId);
                sm.setMaterialName(m.getMaterialName());
                sm.setSpec(m.getSpec() != null ? m.getSpec() : "");
                sm.setImageUrl(m.getImageUrl() != null ? m.getImageUrl() : "");
                sm.setBaseUnit(snapshotUnit(m));
                sm.setTotalQty(qty);
                sm.setOriginalQty(qty);
                sm.setAdjustedQty(qty);
                sm.setZoneCount(1);
                sm.setUnitBreakdown(makeBreakdown(entryUnit, entryQty, m));
                summaryMap.put(materialId, sm);
            }
        }

        // 真正的批量 INSERT——单条 SQL 多 VALUES
        List<TaskMaterialSummary> summaryList = new ArrayList<>(summaryMap.values());
        if (!summaryList.isEmpty()) {
            taskMaterialSummaryMapper.insertBatch(summaryList);
        }

        // 计算盘点金额 = sum(baseQty × 单价)
        BigDecimal totalAmount = calcTotalAmount(allMaterials);
        task.setTotalAmount(totalAmount);
        taskMapper.updateById(task);

        // P2B: 周盘提交 → 事务提交后触发生成智能订货单（先盘后订；生成失败不影响盘点提交）
        final Task submittedTask = task;
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if ("weekly".equals(submittedTask.getTaskType())) {
                        try {
                            smartOrderService.generateByWeeklyTask(submittedTask.getId());
                        } catch (Exception e) {
                            log.warn("SMART_ORDER_GEN 周盘提交触发生成失败 taskId={}: {}", submittedTask.getId(), e.getMessage());
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 注册周盘提交回调失败 taskId={}: {}", taskId, e.getMessage());
        }
    }

    /** 计算盘点金额：遍历物料，baseQty × unitPriceSnapshot，单价缺失则从规则兜底 */
    private BigDecimal calcTotalAmount(List<TaskZoneMaterial> allMaterials) {
        BigDecimal total = BigDecimal.ZERO;
        if (allMaterials.isEmpty()) return total;
        Set<String> materialIds = allMaterials.stream().map(TaskZoneMaterial::getMaterialId).collect(Collectors.toSet());
        Map<String, MaterialRuleResp> ruleMap = materialIds.isEmpty() ? Map.of()
                : materialRuleService.batchDetail(new ArrayList<>(materialIds));
        for (TaskZoneMaterial m : allMaterials) {
            BigDecimal qty = snapshotQty(m);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal price = m.getUnitPriceSnapshot();
            if (price == null) {
                MaterialRuleResp rule = ruleMap.get(m.getMaterialId());
                price = rule != null ? rule.getUnitPrice() : null;
            }
            if (price != null) {
                total = total.add(qty.multiply(price));
            }
        }
        return total;
    }

    private BigDecimal calcSummaryTotalAmount(Task task, List<TaskZoneMaterial> allMaterials) {
        // 已提交且已有存储金额则直接返回
        if ("submitted".equals(task.getStatus()) && task.getTotalAmount() != null) {
            return task.getTotalAmount();
        }
        return calcTotalAmount(allMaterials);
    }

    @Override
    public Map<String, Object> summary(Integer taskId, String storeId) {
        Task task = getTaskById(taskId, storeId);
        fillStoreInfo(task);

        List<TaskZone> zones = taskZoneMapper.selectList(
                new LambdaQueryWrapper<TaskZone>().eq(TaskZone::getTaskId, taskId));

        List<TaskZoneMaterial> allMaterials = zones.isEmpty() ? List.of()
                : taskZoneMaterialMapper.selectList(
                        new LambdaQueryWrapper<TaskZoneMaterial>().eq(TaskZoneMaterial::getTaskId, taskId));

        int totalZones = zones.size();
        Map<Integer, List<TaskZoneMaterial>> summaryMaterialsByZone = allMaterials.stream()
                .collect(Collectors.groupingBy(TaskZoneMaterial::getTaskZoneId));
        int completedZones = 0;
        for (TaskZone zone : zones) {
            List<TaskZoneMaterial> zoneMaterials = summaryMaterialsByZone.getOrDefault(zone.getId(), List.of());
            long entered = zoneMaterials.stream()
                    .filter(m -> m.getInputStatus() != null && !"not_entered".equals(m.getInputStatus()))
                    .count();
            if (zoneMaterials.size() > 0 && entered >= zoneMaterials.size()) {
                completedZones++;
            }
        }
        int totalMaterials = allMaterials.size();
        long enteredMaterials = allMaterials.stream()
                .filter(m -> m.getInputStatus() != null && !"not_entered".equals(m.getInputStatus()))
                .count();

        // 已提交任务从持久化汇总表读；未提交实时计算
        List<Map<String, Object>> materialSummary;
        if ("submitted".equals(task.getStatus())) {
            List<TaskMaterialSummary> rows = taskMaterialSummaryMapper.selectList(
                    new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, taskId));
            List<String> mids = rows.stream().map(TaskMaterialSummary::getMaterialId).distinct().collect(Collectors.toList());
            Map<String, MaterialRuleResp> ruleMap = mids.isEmpty() ? Map.of()
                    : materialRuleService.batchDetail(mids);
            materialSummary = rows.stream().map(sm -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("materialId", sm.getMaterialId());
                m.put("materialName", sm.getMaterialName());
                m.put("spec", sm.getSpec());
                m.put("imageUrl", sm.getImageUrl() != null ? sm.getImageUrl() : "");
                m.put("baseUnit", sm.getBaseUnit());
                m.put("totalQty", sm.getTotalQty());
                m.put("zoneCount", sm.getZoneCount());
                m.put("unitBreakdown", parseBreakdown(sm.getUnitBreakdown()));
                MaterialRuleResp rule = ruleMap.get(sm.getMaterialId());
                boolean hasMulti = (sm.getUnitBreakdown() != null && sm.getUnitBreakdown().contains("},{"))
                        || (rule != null && !rule.getConversions().isEmpty());
                m.put("isMultiUnit", hasMulti);
                m.put("category", rule != null && rule.getCategory() != null ? rule.getCategory() : "");
                return m;
            }).collect(Collectors.toList());
        } else {
            materialSummary = computeMaterialSummary(allMaterials);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("taskName", task.getTaskName());
        result.put("storeId", task.getStoreId());
        result.put("storeName", task.getStoreName());
        result.put("storeCode", task.getStoreCode());
        result.put("xiaochengxuid", task.getXiaochengxuid());
        result.put("warehouseCode", task.getWarehouseCode());
        result.put("deadline", task.getDeadline() != null ? task.getDeadline().toString() : null);
        result.put("totalZones", totalZones);
        result.put("savedZones", completedZones);
        result.put("completedZones", completedZones);
        result.put("totalMaterials", totalMaterials);
        result.put("enteredMaterials", (int) enteredMaterials);
        result.put("materialSummary", materialSummary);
        result.put("totalAmount", calcSummaryTotalAmount(task, allMaterials));
        result.put("status", task.getStatus());
        return result;
    }

    @Override
    public Map<String, Object> result(Integer taskId, String storeId) {
        Task task = getTaskById(taskId, storeId);
        fillStoreInfo(task);
        if (!"submitted".equals(task.getStatus())) {
            throw new BusinessException("任务尚未提交，无法查看结果");
        }
        Map<String, Object> raw = taskService.getResult(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("taskName", task.getTaskName());
        result.put("taskMonth", task.getTaskMonth());
        result.put("storeId", task.getStoreId());
        result.put("storeName", task.getStoreName());
        result.put("storeCode", task.getStoreCode());
        result.put("xiaochengxuid", task.getXiaochengxuid());
        result.put("warehouseCode", task.getWarehouseCode());
        result.put("status", task.getStatus());
        result.put("submittedAt", task.getSubmittedAt() != null ? task.getSubmittedAt().toString() : null);
        result.put("submittedBy", resolveSubmitterName(task.getSubmittedBy(), task.getStoreId()));
        result.put("submittedByOpenid", task.getSubmittedBy() != null ? task.getSubmittedBy() : "");
        result.put("zones", raw.get("zones"));
        result.put("summary", raw.get("summary"));
        // 盘点金额：优先用已存储值，旧任务未存储则实时计算
        BigDecimal totalAmount = task.getTotalAmount();
        if (totalAmount == null) {
            List<TaskZoneMaterial> allMaterials = taskZoneMaterialMapper.selectList(
                    new LambdaQueryWrapper<TaskZoneMaterial>().eq(TaskZoneMaterial::getTaskId, taskId));
            totalAmount = calcTotalAmount(allMaterials);
        }
        result.put("totalAmount", totalAmount);
        return result;
    }

    private String resolveSubmitterName(String openid, String storeId) {
        if (!StringUtils.hasText(openid)) {
            return "";
        }
        // 优先：Employee 表中的真实姓名
        if (StringUtils.hasText(storeId)) {
            try {
                Map<String, Object> profile = staffService.currentStaffProfile(openid, storeId);
                if (profile != null && StringUtils.hasText((String) profile.get("employeeName"))) {
                    return (String) profile.get("employeeName");
                }
            } catch (Exception ignored) { /* fall through */ }
        }
        // 其次：微信昵称
        StoreManagerSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<StoreManagerSession>().eq(StoreManagerSession::getOpenid, openid));
        if (session != null && StringUtils.hasText(session.getWxNickname())) {
            return session.getWxNickname();
        }
        return openid;
    }

    private Task getTaskById(Integer taskId, String storeId) {
        requireStore(storeId);
        Task task = taskMapper.selectById(taskId);
        if (task == null || !storeId.equals(task.getStoreId())) {
            throw new BusinessException(4040, "任务不存在");
        }
        return task;
    }

    private void requireStore(String storeId) {
        if (!StringUtils.hasText(storeId)) {
            throw new BusinessException(4031, "请先选择门店");
        }
    }

    /**
     * 存量数据兼容：旧任务没有快照 storeName/xiaochengxuid，从外部 API 补填
     */
    private void fillStoreInfo(Task task) {
        if (task != null && task.getStoreId() != null
                && (task.getStoreName() == null || task.getXiaochengxuid() == null || task.getWarehouseCode() == null)) {
            StoreInfo info = storeService.getStoreById(task.getStoreId());
            if (info != null) {
                if (task.getStoreName() == null) task.setStoreName(info.getMendianmingcheng());
                if (task.getXiaochengxuid() == null) task.setXiaochengxuid(info.getXiaochengxuid());
                if (task.getWarehouseCode() == null) task.setWarehouseCode(info.getCangkuid());
            }
        }
    }

    private BigDecimal snapshotQty(TaskZoneMaterial material) {
        if (material.getBaseQty() != null) {
            return material.getBaseQty();
        }
        return material.getInputQty() != null ? material.getInputQty() : BigDecimal.ZERO;
    }

    private String snapshotUnit(TaskZoneMaterial material) {
        if (StringUtils.hasText(material.getBaseUnitSnapshot())) {
            return material.getBaseUnitSnapshot();
        }
        return material.getInventoryUnit() != null ? material.getInventoryUnit() : "";
    }

    /** 从 task_zone_material 实时计算物料汇总 */
    private List<Map<String, Object>> computeMaterialSummary(List<TaskZoneMaterial> allMaterials) {
        Map<String, Map<String, Object>> msMap = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Object>>> breakdownMap = new LinkedHashMap<>();
        Set<String> materialIds = new LinkedHashSet<>();
        for (TaskZoneMaterial m : allMaterials) {
            String materialId = m.getMaterialId();
            materialIds.add(materialId);
            BigDecimal addQty = snapshotQty(m);
            Map<String, BigDecimal> unitQtys = parseUnitInputs(m);
            if (!unitQtys.isEmpty()) {
                for (Map.Entry<String, BigDecimal> e : unitQtys.entrySet()) {
                    mergeBreakdownEntry(breakdownMap, materialId, e.getKey(), e.getValue(), false);
                }
            } else {
                String entryUnit = StringUtils.hasText(m.getInputOriginalUnit())
                        ? m.getInputOriginalUnit() : (m.getUnit() != null ? m.getUnit() : "");
                BigDecimal entryInputQty = m.getInputQty() != null ? m.getInputQty() : BigDecimal.ZERO;
                boolean isWeight = m.getBaseQty() != null
                        && m.getBaseQty().compareTo(BigDecimal.ZERO) > 0
                        && entryInputQty.compareTo(BigDecimal.ZERO) > 0
                        && m.getBaseQty().compareTo(entryInputQty) != 0;
                mergeBreakdownEntry(breakdownMap, materialId, entryUnit, entryInputQty, isWeight);
            }
            if (msMap.containsKey(materialId)) {
                Map<String, Object> sm = msMap.get(materialId);
                sm.put("totalQty", ((BigDecimal) sm.get("totalQty")).add(addQty));
                sm.put("zoneCount", (int) sm.get("zoneCount") + 1);
            } else {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("materialId", materialId);
                sm.put("materialName", m.getMaterialName());
                sm.put("spec", m.getSpec() != null ? m.getSpec() : "");
                sm.put("imageUrl", m.getImageUrl() != null ? m.getImageUrl() : "");
                sm.put("totalQty", addQty);
                sm.put("zoneCount", 1);
                msMap.put(materialId, sm);
            }
        }
        // 批量加载所有物料规则（1 次查询代替 N 次）
        Map<String, MaterialRuleResp> ruleMap = materialIds.isEmpty() ? Map.of()
                : materialRuleService.batchDetail(new ArrayList<>(materialIds));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : msMap.entrySet()) {
            String materialId = entry.getKey();
            Map<String, Object> sm = entry.getValue();
            Map<String, Map<String, Object>> unitMap = breakdownMap.getOrDefault(materialId, Map.of());
            List<Map<String, Object>> breakdown = unitMap.values().stream()
                    .filter(bd -> ((BigDecimal) bd.getOrDefault("qty", BigDecimal.ZERO)).compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());
            sm.put("unitBreakdown", breakdown);
            MaterialRuleResp rule = ruleMap.get(materialId);
            boolean hasMulti = breakdown.size() > 1 || (rule != null && !rule.getConversions().isEmpty());
            sm.put("isMultiUnit", hasMulti);
            sm.put("category", rule != null && rule.getCategory() != null ? rule.getCategory() : "");
            sm.put("baseUnit", rule != null && StringUtils.hasText(rule.getBaseUnit()) ? rule.getBaseUnit() : "");
            result.add(sm);
        }
        return result;
    }

    /** 从 JSON 字符串解析 unitBreakdown */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseBreakdown(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            // 简单 JSON 数组解析，避免引入 Jackson
            List<Map<String, Object>> result = new ArrayList<>();
            String content = json.trim();
            if (!content.startsWith("[") || !content.endsWith("]")) return List.of();
            content = content.substring(1, content.length() - 1);
            if (content.isEmpty()) return List.of();
            // 按 "},{" 分割各对象
            String[] parts = content.split("\\},\\{");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i > 0) part = "{" + part;
                if (i < parts.length - 1) part = part + "}";
                Map<String, Object> item = new LinkedHashMap<>();
                for (String kv : part.replaceAll("[{}\"]", "").split(",")) {
                    String[] kvPair = kv.split(":", 2);
                    if (kvPair.length == 2) {
                        String key = kvPair[0].trim();
                        String val = kvPair[1].trim();
                        if ("unit".equals(key)) item.put(key, val);
                        else if ("isWeight".equals(key)) item.put(key, "true".equals(val));
                        else item.put(key, new BigDecimal(val));
                    }
                }
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 加载物料的盘点规则（含换算关系） */
    private MaterialRuleResp loadRule(String materialId) {
        try {
            return materialRuleService.detail(materialId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 构建换算链条文本，如 "1件=1包=40支" */
    private String buildChainText(MaterialRuleResp rule) {
        if (rule == null || rule.getConversions().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MaterialRuleResp.UnitConversionItem c : rule.getConversions()) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(c.getFromQuantity().stripTrailingZeros().toPlainString())
              .append(c.getFromUnit())
              .append("=")
              .append(c.getToQuantity().stripTrailingZeros().toPlainString())
              .append(c.getToUnit());
        }
        return sb.toString();
    }

    /** 解析 unitInputs JSON，如 {"支":"40","包":"1"} */
    private Map<String, BigDecimal> parseUnitInputs(TaskZoneMaterial m) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        String json = m.getUnitInputs();
        if (json == null || json.isEmpty()) return result;
        try {
            String content = json.trim();
            if (content.startsWith("{")) content = content.substring(1);
            if (content.endsWith("}")) content = content.substring(0, content.length() - 1);
            for (String part : content.split(",")) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("\"", "");
                    String val = kv[1].trim().replaceAll("\"", "");
                    BigDecimal qty = new BigDecimal(val);
                    if (qty.compareTo(BigDecimal.ZERO) > 0) {
                        result.merge(key, qty, BigDecimal::add);
                    }
                }
            }
        } catch (Exception ignored) { }
        return result;
    }

    private void mergeBreakdownEntry(Map<String, Map<String, Map<String, Object>>> breakdownMap,
                                      String materialId, String unit, BigDecimal qty, boolean isWeight) {
        breakdownMap.computeIfAbsent(materialId, k -> new LinkedHashMap<>());
        Map<String, Map<String, Object>> unitMap = breakdownMap.get(materialId);
        if (unitMap.containsKey(unit)) {
            Map<String, Object> bd = unitMap.get(unit);
            bd.put("qty", ((BigDecimal) bd.get("qty")).add(qty));
        } else {
            Map<String, Object> bd = new LinkedHashMap<>();
            bd.put("unit", unit);
            bd.put("qty", qty);
            bd.put("isWeight", isWeight);
            unitMap.put(unit, bd);
        }
    }

    private String getBaseUnit(String materialId) {
        TaskZoneMaterial first = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getMaterialId, materialId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        return first != null ? snapshotUnit(first) : "";
    }

    /** 生成 unitBreakdown JSON：[{"unit":"包","qty":1,"isWeight":false}] */
    private String makeBreakdown(String unit, BigDecimal qty, TaskZoneMaterial m) {
        boolean isWeight = m.getBaseQty() != null
                && m.getBaseQty().compareTo(BigDecimal.ZERO) > 0
                && qty.compareTo(BigDecimal.ZERO) > 0
                && m.getBaseQty().compareTo(qty) != 0;
        return "[{\"unit\":\"" + escapeJson(unit) + "\",\"qty\":" + qty.stripTrailingZeros().toPlainString()
                + ",\"isWeight\":" + isWeight + "}]";
    }

    /** 合并 unitBreakdown JSON，追加或累加单位数量 */
    private String mergeBreakdown(String existing, String unit, BigDecimal addQty, TaskZoneMaterial m) {
        if (existing == null || existing.isEmpty()) return makeBreakdown(unit, addQty, m);
        // 简单追加新单位条目
        boolean isWeight = m.getBaseQty() != null
                && m.getBaseQty().compareTo(BigDecimal.ZERO) > 0
                && addQty.compareTo(BigDecimal.ZERO) > 0
                && m.getBaseQty().compareTo(addQty) != 0;
        String newEntry = "{\"unit\":\"" + escapeJson(unit) + "\",\"qty\":" + addQty.stripTrailingZeros().toPlainString()
                + ",\"isWeight\":" + isWeight + "}";
        // 去掉结尾 ]，追加
        return existing.substring(0, existing.length() - 1) + "," + newEntry + "]";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public List<Map<String, Object>> getUnenteredMaterials(Integer taskId, String storeId) {
        getTaskById(taskId, storeId); // 校验任务归属
        List<TaskZoneMaterial> allMaterials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getInputStatus, "not_entered"));
        List<String> materialIds = allMaterials.stream().map(TaskZoneMaterial::getMaterialId).distinct().collect(Collectors.toList());
        Map<String, MaterialRuleResp> ruleMap = materialRuleService.batchDetail(materialIds);
        return allMaterials.stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskZoneMaterialId", m.getTaskZoneMaterialId());
            item.put("taskZoneId", m.getTaskZoneId());
            item.put("materialId", m.getMaterialId());
            item.put("materialName", m.getMaterialName());
            item.put("spec", m.getSpec() != null ? m.getSpec() : "");
            item.put("imageUrl", m.getImageUrl() != null ? m.getImageUrl() : "");
            item.put("unit", m.getUnit() != null ? m.getUnit() : "");
            item.put("sortNo", m.getSortNo());
            MaterialRuleResp rule = ruleMap.get(m.getMaterialId());
            item.put("category", rule != null && rule.getCategory() != null ? rule.getCategory() : "");
            return item;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> buildTaskMap(Task task, Map<String, Template> templateMap) {
        fillStoreInfo(task);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", task.getId());
        m.put("taskName", task.getTaskName());
        m.put("taskMonth", task.getTaskMonth());
        m.put("taskType", task.getTaskType());
        m.put("taskWeek", task.getTaskWeek());
        m.put("storeId", task.getStoreId());
        m.put("storeName", task.getStoreName());
        m.put("storeCode", task.getStoreCode());
        m.put("xiaochengxuid", task.getXiaochengxuid());
        m.put("warehouseCode", task.getWarehouseCode());
        m.put("status", task.getStatus());
        m.put("deadline", task.getDeadline() != null ? task.getDeadline().toString() : null);
        m.put("isExpired", task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now()));
        m.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
        m.put("submittedAt", task.getSubmittedAt() != null ? task.getSubmittedAt().toString() : null);

        Template template = templateMap.get(task.getTemplateId() != null ? task.getTemplateId().toString() : "");
        m.put("templateName", template != null ? template.getTemplateName() : "");
        return m;
    }
}
