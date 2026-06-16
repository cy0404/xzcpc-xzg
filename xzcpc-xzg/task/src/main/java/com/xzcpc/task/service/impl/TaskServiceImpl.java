package com.xzcpc.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.task.dto.TaskCreateRequest;
import com.xzcpc.task.entity.Task;
import com.xzcpc.task.entity.TaskZone;
import com.xzcpc.task.entity.TaskZoneMaterial;
import com.xzcpc.task.mapper.TaskMapper;
import com.xzcpc.task.mapper.TaskZoneMapper;
import com.xzcpc.task.mapper.TaskZoneMaterialMapper;
import com.xzcpc.task.service.StoreService;
import com.xzcpc.task.service.TaskService;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import com.xzcpc.template.entity.Template;
import com.xzcpc.template.entity.TemplateZone;
import com.xzcpc.template.entity.TemplateZoneMaterial;
import com.xzcpc.template.mapper.TemplateMapper;
import com.xzcpc.template.mapper.TemplateZoneMapper;
import com.xzcpc.template.mapper.TemplateZoneMaterialMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService { // 月盘任务服务实现

    private final TaskMapper taskMapper;
    private final TaskZoneMapper taskZoneMapper;
    private final TaskZoneMaterialMapper taskZoneMaterialMapper;
    private final StoreService storeService;
    private final TemplateMapper templateMapper;
    private final TemplateZoneMapper templateZoneMapper;
    private final TemplateZoneMaterialMapper templateZoneMaterialMapper;
    private final MaterialMapper materialMapper;
    private final MaterialInventoryRuleMapper materialInventoryRuleMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Page<Task> page(String storeId, String status, String keyword, String templateName,
                           String taskMonth, int pageNum, int pageSize) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(storeId)) {
            wrapper.eq(Task::getStoreId, storeId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Task::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Task::getTaskName, keyword)
                             .or().like(Task::getStoreName, keyword));
        }
        if (StringUtils.hasText(taskMonth)) {
            wrapper.eq(Task::getTaskMonth, taskMonth);
        }
        if (StringUtils.hasText(templateName)) {
            List<Integer> matchedIds = templateMapper.selectList(
                    new LambdaQueryWrapper<Template>()
                            .like(Template::getTemplateName, templateName))
                    .stream().map(Template::getTemplateId).collect(Collectors.toList());
            if (matchedIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(Task::getTemplateId, matchedIds);
        }
        wrapper.orderByDesc(Task::getCreatedAt);
        Page<Task> result = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<Task> records = result.getRecords();
        if (records.isEmpty()) return result;

        Map<Integer, String> templateNameMap = templateMapper.selectList(null).stream()
                .collect(Collectors.toMap(Template::getTemplateId, Template::getTemplateName));
        Set<Integer> templateIds = records.stream()
                .map(Task::getTemplateId).filter(id -> id != null).collect(Collectors.toSet());
        Set<Integer> taskIds = records.stream()
                .map(Task::getId).collect(Collectors.toSet());
        Map<Integer, Long> zoneCountMap = taskIds.isEmpty() ? Map.of()
                : taskZoneMapper.selectList(
                        new LambdaQueryWrapper<TaskZone>().in(TaskZone::getTaskId, taskIds))
                        .stream().collect(Collectors.groupingBy(TaskZone::getTaskId, Collectors.counting()));
        Map<Integer, Long> materialCountMap = taskIds.isEmpty() ? Map.of()
                : taskZoneMaterialMapper.selectList(
                        new LambdaQueryWrapper<TaskZoneMaterial>().in(TaskZoneMaterial::getTaskId, taskIds))
                        .stream().collect(Collectors.groupingBy(TaskZoneMaterial::getTaskId, Collectors.counting()));

        // 存量数据兼容：如果 storeName 为空，从外部 API 补填
        Map<String, StoreInfo> storeMap = null;
        for (Task task : records) {
            if ((task.getStoreName() == null || task.getXiaochengxuid() == null) && task.getStoreId() != null) {
                if (storeMap == null) {
                    storeMap = storeService.getStoreMap();
                }
                StoreInfo store = storeMap.get(task.getStoreId());
                if (store != null) {
                    if (task.getStoreName() == null) task.setStoreName(store.getMendianmingcheng());
                    if (task.getXiaochengxuid() == null) task.setXiaochengxuid(store.getXiaochengxuid());
                    if (task.getWarehouseCode() == null) task.setWarehouseCode(store.getCangkuid());
                }
            }
            if (task.getTaskId() == null || task.getTaskId() == 0) {
                task.setTaskId(task.getId());
            }
            if (task.getTemplateId() != null) {
                task.setTemplateName(templateNameMap.get(task.getTemplateId()));
            }
            task.setZoneCount(zoneCountMap.getOrDefault(task.getId(), 0L).intValue());
            task.setMaterialCount(materialCountMap.getOrDefault(task.getId(), 0L).intValue());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreate(TaskCreateRequest request) {
        if (request.getStoreIds() == null || request.getStoreIds().isEmpty()) {
            throw new BusinessException("门店列表不能为空");
        }

        Map<String, StoreInfo> storeMap = storeService.getStoreMap();
        int count = 0;
        for (String storeId : request.getStoreIds()) {
            StoreInfo storeInfo = storeMap.get(storeId);
            Task task = new Task();
            task.setTaskName(request.getTaskName());
            task.setTaskMonth(request.getTaskMonth());
            task.setStoreId(storeId);
            task.setStoreName(storeInfo != null ? storeInfo.getMendianmingcheng() : null);
            task.setStoreCode(storeInfo != null ? storeInfo.getBianma() : null);
            task.setXiaochengxuid(storeInfo != null ? storeInfo.getXiaochengxuid() : null);
            task.setWarehouseCode(storeInfo != null ? storeInfo.getCangkuid() : null);
            task.setTemplateId(request.getTemplateId());
            task.setDeadline(request.getDeadline());
            task.setStatus("not_started");
            task.setCreatedBy("admin");
            taskMapper.insert(task);
            task.setTaskId(task.getId());
            task.setBizCode("TASK" + String.format("%08d", task.getId()));
            taskMapper.updateById(task);

            snapshotTemplate(task.getId(), task.getTemplateId());
            count++;
        }
        return count;
    }

    @Override
    public void create(Task task) {
        task.setStatus("not_started");
        task.setCreatedBy("admin");
        taskMapper.insert(task);
        task.setTaskId(task.getId());
        task.setBizCode("TASK" + String.format("%08d", task.getId()));
        taskMapper.updateById(task);
        snapshotTemplate(task.getId(), task.getTemplateId());
    }

    private void snapshotTemplate(Integer taskId, Integer templateId) {
        if (templateId == null) return;

        List<TemplateZone> templateZones = templateZoneMapper.selectList(
                new LambdaQueryWrapper<TemplateZone>()
                        .eq(TemplateZone::getTemplateId, templateId)
                        .orderByAsc(TemplateZone::getSortNo));

        Set<Integer> zoneIds = templateZones.stream()
                .map(TemplateZone::getId).collect(Collectors.toSet());
        List<TemplateZoneMaterial> zoneMaterials = zoneIds.isEmpty() ? List.of()
                : templateZoneMaterialMapper.selectList(
                        new LambdaQueryWrapper<TemplateZoneMaterial>()
                                .in(TemplateZoneMaterial::getZoneId, zoneIds));

        Map<Integer, List<TemplateZoneMaterial>> materialsByZone = zoneMaterials.stream()
                .collect(Collectors.groupingBy(TemplateZoneMaterial::getZoneId));

        for (TemplateZone tz : templateZones) {
            TaskZone taskZone = new TaskZone();
            taskZone.setTaskId(taskId);
            taskZone.setZoneName(tz.getZoneName());
            taskZone.setSortNo(tz.getSortNo());
            taskZone.setSourceType("template");
            taskZoneMapper.insert(taskZone);
            taskZone.setTaskZoneId(taskZone.getId());
            taskZone.setBizCode("TKZ" + String.format("%08d", taskZone.getId()));
            taskZoneMapper.updateById(taskZone);

            List<TemplateZoneMaterial> materials = materialsByZone.getOrDefault(tz.getId(), List.of());
            for (TemplateZoneMaterial tm : materials) {
                TaskZoneMaterial tzm = new TaskZoneMaterial();
                tzm.setTaskId(taskId);
                tzm.setTaskZoneId(taskZone.getId());
                tzm.setMaterialId(tm.getMaterialId());
                tzm.setMaterialName(tm.getMaterialName() != null ? tm.getMaterialName() : "");
                tzm.setSpec(tm.getSpec() != null ? tm.getSpec() : "");
                tzm.setUnit("");
                tzm.setInventoryUnit(tm.getInventoryUnit() != null ? tm.getInventoryUnit() : "");
                tzm.setSortNo(tm.getSortNo());
                tzm.setInputStatus("not_entered");
                taskZoneMaterialMapper.insert(tzm);
                tzm.setTaskZoneMaterialId(tzm.getId());
                tzm.setBizCode("TZM" + String.format("%08d", tzm.getId()));
                taskZoneMaterialMapper.updateById(tzm);
            }
        }
    }

    @Override
    public Task detail(Integer id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (task.getTaskId() == null || task.getTaskId() == 0) {
            task.setTaskId(task.getId());
        }
        if (task.getStoreId() != null
                && (task.getStoreName() == null || task.getXiaochengxuid() == null || task.getWarehouseCode() == null)) {
            StoreInfo store = storeService.getStoreById(task.getStoreId());
            if (store != null) {
                if (task.getStoreName() == null) task.setStoreName(store.getMendianmingcheng());
                if (task.getXiaochengxuid() == null) task.setXiaochengxuid(store.getXiaochengxuid());
                if (task.getWarehouseCode() == null) task.setWarehouseCode(store.getCangkuid());
            }
        }
        if (task.getTemplateId() != null) {
            Template template = templateMapper.selectById(task.getTemplateId());
            if (template != null) task.setTemplateName(template.getTemplateName());
        }
        return task;
    }

    @Override
    public Map<String, Object> getResult(Integer id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }

        List<TaskZone> zones = taskZoneMapper.selectList(
                new LambdaQueryWrapper<TaskZone>()
                        .eq(TaskZone::getTaskId, id)
                        .orderByAsc(TaskZone::getSortNo));

        List<TaskZoneMaterial> allMaterials = zones.isEmpty() ? List.of()
                : taskZoneMaterialMapper.selectList(
                        new LambdaQueryWrapper<TaskZoneMaterial>()
                                .eq(TaskZoneMaterial::getTaskId, id));

        Map<Integer, List<TaskZoneMaterial>> materialsByZone = allMaterials.stream()
                .collect(Collectors.groupingBy(TaskZoneMaterial::getTaskZoneId));

        // 一次性批量加载物料详情和盘点规则（所有分区共享）
        List<String> allMatIds = allMaterials.stream().map(TaskZoneMaterial::getMaterialId).distinct().toList();
        Map<String, Material> materialMap = allMatIds.isEmpty() ? Map.of()
                : materialMapper.selectList(new LambdaQueryWrapper<Material>().in(Material::getMaterialId, allMatIds))
                .stream().collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));
        Map<String, MaterialInventoryRule> ruleMap = allMatIds.isEmpty() ? Map.of()
                : materialInventoryRuleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>().in(MaterialInventoryRule::getMaterialId, allMatIds))
                .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));

        List<Map<String, Object>> zoneList = new java.util.ArrayList<>();
        for (TaskZone zone : zones) {
            Map<String, Object> zoneMap = new java.util.LinkedHashMap<>();
            zoneMap.put("zoneId", zone.getId());
            zoneMap.put("zoneName", zone.getZoneName());
            List<TaskZoneMaterial> zoneMaterials = materialsByZone.getOrDefault(zone.getId(), List.of());

            List<Map<String, Object>> materialList = new java.util.ArrayList<>();
            for (TaskZoneMaterial m : zoneMaterials) {
                Map<String, Object> mMap = new java.util.LinkedHashMap<>();
                mMap.put("materialId", m.getMaterialId());
                mMap.put("materialName", m.getMaterialName());
                mMap.put("spec", m.getSpec());
                mMap.put("unit", snapshotUnit(m));
                // 多单位链：优先取快照，快照为空则从盘点规则兜底
                MaterialInventoryRule rule = ruleMap.get(m.getMaterialId());
                String inventoryUnit = m.getInventoryUnit();
                if ((inventoryUnit == null || inventoryUnit.isEmpty()) && rule != null && rule.getInventoryUnits() != null) {
                    inventoryUnit = rule.getInventoryUnits();
                }
                mMap.put("inventoryUnit", inventoryUnit != null ? inventoryUnit : "");
                mMap.put("quantity", snapshotQty(m));
                mMap.put("baseQty", snapshotQty(m));
                mMap.put("baseUnit", snapshotUnit(m));
                mMap.put("inputMode", m.getInputMode());
                mMap.put("inputOriginalQty", m.getInputOriginalQty());
                mMap.put("inputOriginalUnit", m.getInputOriginalUnit());
                mMap.put("ruleId", m.getRuleIdSnapshot());
                mMap.put("conversionSnapshot", m.getConversionSnapshot());
                mMap.put("unitInputs", m.getUnitInputs() != null ? m.getUnitInputs() : "");
                mMap.put("remark", m.getRemark() != null ? m.getRemark() : "");
                // 物料详情：分类、企迈ID、单价
                Material mat = materialMap.get(m.getMaterialId());
                mMap.put("qmCode", mat != null ? (mat.getQmCode() != null ? mat.getQmCode() : "") : "");
                mMap.put("parentCategory", mat != null ? (mat.getParentCategory() != null ? mat.getParentCategory() : "") : "");
                mMap.put("category", mat != null ? (mat.getCategory() != null ? mat.getCategory() : "") : "");
                mMap.put("unitPrice", rule != null ? rule.getUnitPrice() : null);
                materialList.add(mMap);
            }
            zoneMap.put("materials", materialList);
            zoneList.add(zoneMap);
        }

        Map<String, Map<String, Object>> summaryMap = new java.util.LinkedHashMap<>();
        for (TaskZoneMaterial m : allMaterials) {
            String materialId = m.getMaterialId();
            if (!summaryMap.containsKey(materialId)) {
                Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("materialId", materialId);
                sm.put("materialName", m.getMaterialName());
                sm.put("spec", m.getSpec() != null ? m.getSpec() : "");
                sm.put("zoneCount", 1);
                sm.put("totalQuantity", snapshotQty(m));
                sm.put("unit", snapshotUnit(m));
                // 多单位链：优先快照，快照为空则从盘点规则兜底
                MaterialInventoryRule rule = ruleMap.get(materialId);
                String invUnit = m.getInventoryUnit();
                if ((invUnit == null || invUnit.isEmpty()) && rule != null && rule.getInventoryUnits() != null) {
                    invUnit = rule.getInventoryUnits();
                }
                sm.put("inventoryUnit", invUnit != null ? invUnit : "");
                sm.put("unitInputs", m.getUnitInputs() != null ? m.getUnitInputs() : "");
                sm.put("remark", m.getRemark() != null ? m.getRemark() : "");
                summaryMap.put(materialId, sm);
            } else {
                Map<String, Object> sm = summaryMap.get(materialId);
                sm.put("zoneCount", (int) sm.get("zoneCount") + 1);
                BigDecimal current = (BigDecimal) sm.get("totalQuantity");
                sm.put("totalQuantity", current.add(snapshotQty(m)));
                String merged = mergeUnitInputs((String) sm.get("unitInputs"), m.getUnitInputs());
                sm.put("unitInputs", merged);
            }
        }
        List<Map<String, Object>> summary = new java.util.ArrayList<>(summaryMap.values());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("zones", zoneList);
        result.put("summary", summary);
        return result;
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

    /** 合并两个 unitInputs JSON（{"箱":"1"} + {"箱":"3"} → {"箱":"4"}） */
    private String mergeUnitInputs(String existing, String incoming) {
        if (incoming == null || incoming.isEmpty()) return existing != null ? existing : "";
        if (existing == null || existing.isEmpty()) return incoming;
        try {
            Map<String, String> existingMap = objectMapper.readValue(existing, new TypeReference<Map<String, String>>() {});
            Map<String, String> incomingMap = objectMapper.readValue(incoming, new TypeReference<Map<String, String>>() {});
            for (Map.Entry<String, String> entry : incomingMap.entrySet()) {
                BigDecimal inc = new BigDecimal(entry.getValue());
                BigDecimal exist = existingMap.containsKey(entry.getKey()) ? new BigDecimal(existingMap.get(entry.getKey())) : BigDecimal.ZERO;
                existingMap.put(entry.getKey(), exist.add(inc).stripTrailingZeros().toPlainString());
            }
            return objectMapper.writeValueAsString(existingMap);
        } catch (Exception e) {
            return existing;
        }
    }

    @Override
    public void delete(Integer id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (!"not_started".equals(task.getStatus())) {
            throw new BusinessException("仅未开始状态的任务可删除");
        }
        taskZoneMaterialMapper.delete(new LambdaQueryWrapper<TaskZoneMaterial>()
                .eq(TaskZoneMaterial::getTaskId, id));
        taskZoneMapper.delete(new LambdaQueryWrapper<TaskZone>()
                .eq(TaskZone::getTaskId, id));
        taskMapper.deleteById(id);
    }
}
