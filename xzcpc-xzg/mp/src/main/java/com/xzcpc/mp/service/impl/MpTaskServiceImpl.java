package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.mp.entity.StoreManagerSession;
import com.xzcpc.mp.mapper.StoreManagerSessionMapper;
import com.xzcpc.mp.service.MpTaskService;
import com.xzcpc.task.entity.*;
import com.xzcpc.task.mapper.*;
import com.xzcpc.task.service.StoreService;
import com.xzcpc.task.service.TaskService;
import com.xzcpc.template.dto.MaterialRuleResp;
import com.xzcpc.template.service.MaterialRuleService;
import com.xzcpc.template.entity.Template;
import com.xzcpc.template.mapper.TemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MpTaskServiceImpl implements MpTaskService {

    private final TaskMapper taskMapper;
    private final TaskZoneMapper taskZoneMapper;
    private final TaskZoneMaterialMapper taskZoneMaterialMapper;
    private final TaskMaterialSummaryMapper taskMaterialSummaryMapper;
    private final TaskService taskService;
    private final TemplateMapper templateMapper;
    private final StoreService storeService;
    private final StoreManagerSessionMapper sessionMapper;
    private final MaterialRuleService materialRuleService;

    @Override
    public Map<String, Object> list(String storeId) {
        requireStore(storeId);
        List<Task> allTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getStoreId, storeId)
                        .orderByDesc(Task::getDeadline));

        Map<String, Template> templateMap = templateMapper.selectList(null).stream()
                .collect(Collectors.toMap(t -> t.getTemplateId().toString(), t -> t));

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
            boolean expired = task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now());
            if ("submitted".equals(task.getStatus()) || expired) {
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

        if (task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException(4033, "任务已过截止时间");
        }

        List<TaskZone> zones = taskZoneMapper.selectList(
                new LambdaQueryWrapper<TaskZone>().eq(TaskZone::getTaskId, taskId));
        List<TaskZoneMaterial> allSubmitMaterials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>().eq(TaskZoneMaterial::getTaskId, taskId));
        Map<Integer, List<TaskZoneMaterial>> submitMaterialsByZone = allSubmitMaterials.stream()
                .collect(Collectors.groupingBy(TaskZoneMaterial::getTaskZoneId));

        for (TaskZone zone : zones) {
            List<TaskZoneMaterial> zoneMaterials = submitMaterialsByZone.getOrDefault(zone.getId(), List.of());
            long entered = zoneMaterials.stream()
                    .filter(m -> m.getInputStatus() != null && !"not_entered".equals(m.getInputStatus()))
                    .count();
            if (zoneMaterials.isEmpty() || entered < zoneMaterials.size()) {
                throw new BusinessException("分区「" + zone.getZoneName() + "」尚未完成盘点，请录入所有物料数量");
            }
        }

        task.setStatus("submitted");
        task.setSubmittedAt(LocalDateTime.now());
        task.setSubmittedBy(openid);
        taskMapper.updateById(task);

        // 更新汇总表（含多单位明细，持久化供历史查看）
        taskMaterialSummaryMapper.delete(
                new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, taskId));

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
                sm.setZoneCount(sm.getZoneCount() + 1);
                // 合并 unitBreakdown JSON
                sm.setUnitBreakdown(mergeBreakdown(sm.getUnitBreakdown(), entryUnit, entryQty, m));
            } else {
                TaskMaterialSummary sm = new TaskMaterialSummary();
                sm.setTaskId(taskId);
                sm.setMaterialId(materialId);
                sm.setMaterialName(m.getMaterialName());
                sm.setSpec(m.getSpec() != null ? m.getSpec() : "");
                sm.setBaseUnit(snapshotUnit(m));
                sm.setTotalQty(qty);
                sm.setZoneCount(1);
                sm.setUnitBreakdown(makeBreakdown(entryUnit, entryQty, m));
                summaryMap.put(materialId, sm);
            }
        }

        for (TaskMaterialSummary sm : summaryMap.values()) {
            taskMaterialSummaryMapper.insert(sm);
        }
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
                m.put("baseUnit", sm.getBaseUnit());
                m.put("totalQty", sm.getTotalQty());
                m.put("zoneCount", sm.getZoneCount());
                m.put("unitBreakdown", parseBreakdown(sm.getUnitBreakdown()));
                MaterialRuleResp rule = ruleMap.get(sm.getMaterialId());
                boolean hasMulti = (sm.getUnitBreakdown() != null && sm.getUnitBreakdown().contains("},{"))
                        || (rule != null && !rule.getConversions().isEmpty());
                m.put("isMultiUnit", hasMulti);
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
        result.put("submittedBy", resolveSubmitterName(task.getSubmittedBy()));
        result.put("submittedByOpenid", task.getSubmittedBy() != null ? task.getSubmittedBy() : "");
        result.put("zones", raw.get("zones"));
        result.put("summary", raw.get("summary"));
        return result;
    }

    private String resolveSubmitterName(String openid) {
        if (!StringUtils.hasText(openid)) {
            return "";
        }
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

    private Map<String, Object> buildTaskMap(Task task, Map<String, Template> templateMap) {
        fillStoreInfo(task);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", task.getId());
        m.put("taskName", task.getTaskName());
        m.put("taskMonth", task.getTaskMonth());
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
