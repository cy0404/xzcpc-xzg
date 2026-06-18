package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.dto.AddZoneReq;
import com.xzcpc.mp.dto.ItemSaveReq;
import com.xzcpc.mp.dto.SaveZoneReq;
import com.xzcpc.mp.dto.SortReq;
import com.xzcpc.mp.dto.ZoneMaterialItem;
import com.xzcpc.mp.service.MpZoneService;
import com.xzcpc.task.entity.*;
import com.xzcpc.task.mapper.*;
import com.xzcpc.template.dto.MaterialRuleResp;
import com.xzcpc.template.service.MaterialRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MpZoneServiceImpl implements MpZoneService {

    private final TaskMapper taskMapper;
    private final TaskZoneMapper taskZoneMapper;
    private final TaskZoneMaterialMapper taskZoneMaterialMapper;
    private final MaterialRuleService materialRuleService;
    private final ObjectMapper objectMapper;

    @Override
    public List<Map<String, Object>> getMaterials(Integer taskId, Integer zoneId, String storeId) {
        validateTaskOwnership(taskId, storeId);
        List<TaskZoneMaterial> materials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId)
                        .orderByAsc(TaskZoneMaterial::getSortNo));

        // 批量加载盘点规则，避免 N+1
        List<String> materialIds = materials.stream().map(TaskZoneMaterial::getMaterialId).distinct().toList();
        Map<String, com.xzcpc.template.dto.MaterialRuleResp> ruleMap = materialRuleService.batchDetail(materialIds);

        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskZoneMaterial m : materials) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskZoneMaterialId", m.getTaskZoneMaterialId());
            item.put("materialId", m.getMaterialId());
            item.put("materialName", m.getMaterialName());
            item.put("spec", m.getSpec() != null ? m.getSpec() : "");
            item.put("unit", m.getInventoryUnit() != null ? m.getInventoryUnit() : "");
            item.put("inputQty", m.getInputQty());
            item.put("remark", m.getRemark() != null ? m.getRemark() : "");
            item.put("inputStatus", m.getInputStatus() != null ? m.getInputStatus() : "not_entered");
            item.put("sortNo", m.getSortNo());
            item.put("inputMode", m.getInputMode());
            item.put("inputOriginalQty", m.getInputOriginalQty());
            item.put("inputOriginalUnit", m.getInputOriginalUnit());
            item.put("baseUnit", m.getBaseUnitSnapshot());
            item.put("baseQty", m.getBaseQty() != null ? m.getBaseQty() : m.getInputQty());
            item.put("conversionSnapshot", m.getConversionSnapshot());
            com.xzcpc.template.dto.MaterialRuleResp rule = ruleMap.get(m.getMaterialId());
            item.put("inventoryRule", rule != null ? rule : pendingFallback(m.getMaterialId()));
            result.add(item);
        }
        return result;
    }

    private Object pendingFallback(String materialId) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("materialId", materialId);
        fallback.put("ruleStatus", "pending");
        fallback.put("units", List.of());
        fallback.put("conversions", List.of());
        fallback.put("weightConversions", List.of());
        return fallback;
    }

    @Override
    @Transactional
    public void saveZone(Integer taskId, Integer zoneId, SaveZoneReq req, String storeId) {
        validateTaskOwnership(taskId, storeId);
        validateTaskNotSubmitted(taskId);
        TaskZone zone = taskZoneMapper.selectById(zoneId);
        if (zone == null || !zone.getTaskId().equals(taskId)) {
            throw new BusinessException("分区不存在");
        }

        List<TaskZoneMaterial> allMaterials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId));

        Map<String, TaskZoneMaterial> materialMap = new HashMap<>();
        for (TaskZoneMaterial m : allMaterials) {
            materialMap.put(m.getMaterialId(), m);
        }

        // 批量加载盘点规则，避免 N+1
        Set<String> batchIds = req.getItems().stream().map(ZoneMaterialItem::getMaterialId).collect(Collectors.toSet());
        Map<String, MaterialRuleResp> ruleMap = materialRuleService.batchDetail(new ArrayList<>(batchIds));

        Map<String, SnapshotResult> snapshotMap = new HashMap<>();
        for (var item : req.getItems()) {
            if (!materialMap.containsKey(item.getMaterialId())) {
                throw new BusinessException("物料 " + item.getMaterialId() + " 不属于当前分区");
            }
            SnapshotResult snapshot = buildSnapshot(materialMap.get(item.getMaterialId()), SnapshotInput.from(item), ruleMap);
            if (snapshot.baseQty() != null && snapshot.baseQty().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("物料「" + item.getMaterialId() + "」数量不能为负数");
            }
            snapshotMap.put(item.getMaterialId(), snapshot);
        }

        for (var item : req.getItems()) {
            TaskZoneMaterial material = materialMap.get(item.getMaterialId());
            SnapshotResult snapshot = snapshotMap.get(item.getMaterialId());
            if (snapshot.baseQty() != null) {
                applySnapshot(material, snapshot);
                material.setInputStatus("entered");
            }
            if (item.getRemark() != null) {
                material.setRemark(item.getRemark());
            }
            taskZoneMaterialMapper.updateById(material);
        }

        zone.setZoneSaved(1);
        taskZoneMapper.updateById(zone);

        Task task = taskMapper.selectById(taskId);
        if ("not_started".equals(task.getStatus())) {
            task.setStatus("in_progress");
            taskMapper.updateById(task);
        }
    }

    @Override
    @Transactional
    public void itemSave(Integer taskId, Integer zoneId, ItemSaveReq req, String storeId) {
        validateTaskOwnership(taskId, storeId);
        validateTaskNotSubmitted(taskId);

        List<TaskZoneMaterial> materials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId)
                        .eq(TaskZoneMaterial::getMaterialId, req.getMaterialId()));

        if (materials.isEmpty()) {
            throw new BusinessException("物料不属于当前分区");
        }

        TaskZoneMaterial material = materials.get(0);
        SnapshotResult snapshot = buildSnapshot(material, SnapshotInput.from(req));
        applySnapshot(material, snapshot);
        material.setRemark(req.getRemark());
        material.setUnitInputs(req.getUnitInputs());
        if (snapshot.baseQty() != null) {
            material.setInputStatus("entered");
        }
        taskZoneMaterialMapper.updateById(material);

        // 任务状态从未开始变为进行中
        Task task = taskMapper.selectById(taskId);
        if ("not_started".equals(task.getStatus())) {
            task.setStatus("in_progress");
            taskMapper.updateById(task);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> addZone(Integer taskId, AddZoneReq req, String storeId) {
        validateTaskOwnership(taskId, storeId);
        validateTaskNotSubmitted(taskId);

        int maxSort = taskZoneMapper.selectList(
                new LambdaQueryWrapper<TaskZone>()
                        .eq(TaskZone::getTaskId, taskId))
                .stream()
                .mapToInt(z -> z.getSortNo() != null ? z.getSortNo() : 0)
                .max()
                .orElse(0);

        TaskZone zone = new TaskZone();
        zone.setTaskId(taskId);
        zone.setZoneName(req.getZoneName());
        zone.setSortNo(maxSort + 1);
        zone.setSourceType("manual");
        zone.setZoneSaved(0);
        taskZoneMapper.insert(zone);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskZoneId", zone.getId());
        result.put("zoneName", zone.getZoneName());
        return result;
    }

    @Override
    @Transactional
    public void deleteZone(Integer taskId, Integer zoneId, String storeId) {
        validateTaskOwnership(taskId, storeId);
        validateTaskNotSubmitted(taskId);

        TaskZone zone = taskZoneMapper.selectById(zoneId);
        if (zone == null || !zone.getTaskId().equals(taskId)) {
            throw new BusinessException("分区不存在");
        }

        taskZoneMaterialMapper.delete(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId));
        taskZoneMapper.deleteById(zoneId);
    }

    @Override
    @Transactional
    public void sortZones(Integer taskId, SortReq req, String storeId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || (storeId != null && !storeId.isEmpty() && !storeId.equals(task.getStoreId()))) {
            throw new BusinessException(4040, "任务不存在");
        }
        if ("submitted".equals(task.getStatus())) {
            throw new BusinessException(4032, "任务已提交，不可修改");
        }
        if (task.getDeadline() != null && task.getDeadline().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException(4033, "任务已过截止时间");
        }
        for (int i = 0; i < req.getIds().size(); i++) {
            TaskZone zone = new TaskZone();
            zone.setId(req.getIds().get(i));
            zone.setSortNo(i + 1);
            taskZoneMapper.updateById(zone);
        }
    }
    private void validateTaskOwnership(Integer taskId, String storeId) {
        if (storeId == null || storeId.isEmpty()) {
            throw new BusinessException(4031, "请先选择门店");
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null || !storeId.equals(task.getStoreId())) {
            throw new BusinessException(4040, "任务不存在");
        }
    }

    private void validateTaskNotSubmitted(Integer taskId) {
        Task task = taskMapper.selectById(taskId);
        if ("submitted".equals(task.getStatus())) {
            throw new BusinessException(4032, "任务已提交，不可修改");
        }
        if (task.getDeadline() != null && task.getDeadline().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException(4033, "任务已过截止时间");
        }
    }

    private SnapshotResult buildSnapshot(TaskZoneMaterial material, SnapshotInput input) {
        MaterialRuleResp rule = loadMaintainedRule(material.getMaterialId());
        Map<String, MaterialRuleResp> ruleMap = rule != null ? Map.of(material.getMaterialId(), rule) : Map.of();
        return buildSnapshot(material, input, ruleMap);
    }

    private SnapshotResult buildSnapshot(TaskZoneMaterial material, SnapshotInput input, Map<String, MaterialRuleResp> ruleMap) {
        BigDecimal originalQty = input.originalQty() != null ? input.originalQty() : input.qty();
        if (originalQty == null) {
            return new SnapshotResult(null, null, null, null, null, null, null, null);
        }
        if (originalQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("物料「" + material.getMaterialName() + "」数量不能为负数");
        }

        String inputMode = StringUtils.hasText(input.inputMode()) ? input.inputMode() : "unit";
        String originalUnit = StringUtils.hasText(input.originalUnit())
                ? input.originalUnit()
                : (StringUtils.hasText(material.getInventoryUnit()) ? material.getInventoryUnit() : material.getUnit());

        MaterialRuleResp rule = ruleMap.get(material.getMaterialId());
        if (rule == null || !"maintained".equals(rule.getRuleStatus()) || !StringUtils.hasText(rule.getBaseUnit())) {
            rule = null;
        }
        if (rule == null) {
            String fallbackUnit = StringUtils.hasText(originalUnit) ? originalUnit : material.getInventoryUnit();
            return new SnapshotResult(inputMode, originalQty, fallbackUnit, fallbackUnit, null,
                    originalQty, null, null);
        }

        BigDecimal baseQty;
        String conversionSnapshot;
        if ("weight".equals(inputMode)) {
            MaterialRuleResp.WeightConversionItem weightRule = findWeightRule(rule, input);
            if (weightRule == null) {
                throw new BusinessException("物料「" + material.getMaterialName() + "」未找到匹配的称重换算规则");
            }
            BigDecimal ratio = ratioToBase(rule, weightRule.getCountUnit(), new HashSet<>());
            baseQty = originalQty
                    .divide(weightRule.getWeightQuantity(), 10, RoundingMode.HALF_UP)
                    .multiply(weightRule.getCountQuantity())
                    .multiply(ratio);
            originalUnit = weightRule.getWeightUnit();
            conversionSnapshot = toJson(Map.of(
                    "type", "weight",
                    "weightQuantity", weightRule.getWeightQuantity(),
                    "weightUnit", weightRule.getWeightUnit(),
                    "countQuantity", weightRule.getCountQuantity(),
                    "countUnit", weightRule.getCountUnit()
            ));
        } else {
            if (!StringUtils.hasText(originalUnit)) {
                originalUnit = rule.getBaseUnit();
            }
            BigDecimal ratio = ratioToBase(rule, originalUnit, new HashSet<>());
            baseQty = originalQty.multiply(ratio);
            conversionSnapshot = toJson(Map.of(
                    "type", "unit",
                    "fromUnit", originalUnit,
                    "toUnit", rule.getBaseUnit(),
                    "ratio", ratio
            ));
        }

        BigDecimal normalizedBaseQty = baseQty.setScale(4, RoundingMode.HALF_UP);
        return new SnapshotResult(inputMode, originalQty, originalUnit, rule.getBaseUnit(), rule.getRuleId(),
                normalizedBaseQty, conversionSnapshot, rule.getUnitPrice());
    }

    private MaterialRuleResp loadMaintainedRule(String materialId) {
        try {
            MaterialRuleResp rule = materialRuleService.detail(materialId);
            if (rule != null && "maintained".equals(rule.getRuleStatus()) && StringUtils.hasText(rule.getBaseUnit())) {
                return rule;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private MaterialRuleResp.WeightConversionItem findWeightRule(MaterialRuleResp rule, SnapshotInput input) {
        if (rule.getWeightConversions() == null || rule.getWeightConversions().isEmpty()) {
            return null;
        }
        return rule.getWeightConversions().stream()
                .filter(item -> sameText(item.getWeightUnit(), input.weightUnit())
                        || sameText(item.getWeightUnit(), input.originalUnit()))
                .filter(item -> input.weightQuantity() == null
                        || sameNumber(item.getWeightQuantity(), input.weightQuantity()))
                .filter(item -> input.countQuantity() == null
                        || sameNumber(item.getCountQuantity(), input.countQuantity()))
                .filter(item -> !StringUtils.hasText(input.countUnit())
                        || sameText(item.getCountUnit(), input.countUnit()))
                .findFirst()
                .orElse(rule.getWeightConversions().size() == 1 ? rule.getWeightConversions().get(0) : null);
    }

    private BigDecimal ratioToBase(MaterialRuleResp rule, String unit, Set<String> visiting) {
        if (!StringUtils.hasText(unit)) {
            throw new BusinessException("录入单位不能为空");
        }
        if (unit.equals(rule.getBaseUnit())) {
            return BigDecimal.ONE;
        }
        if (!visiting.add(unit)) {
            throw new BusinessException("物料「" + rule.getMaterialName() + "」换算规则存在循环");
        }
        if (rule.getConversions() != null) {
            for (MaterialRuleResp.UnitConversionItem item : rule.getConversions()) {
                if (item.getFromQuantity() == null || item.getToQuantity() == null
                        || item.getFromQuantity().compareTo(BigDecimal.ZERO) <= 0
                        || item.getToQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (unit.equals(item.getFromUnit())) {
                    return item.getToQuantity()
                            .divide(item.getFromQuantity(), 10, RoundingMode.HALF_UP)
                            .multiply(ratioToBase(rule, item.getToUnit(), visiting));
                }
                if (unit.equals(item.getToUnit())) {
                    return item.getFromQuantity()
                            .divide(item.getToQuantity(), 10, RoundingMode.HALF_UP)
                            .multiply(ratioToBase(rule, item.getFromUnit(), visiting));
                }
            }
        }
        throw new BusinessException("物料「" + rule.getMaterialName() + "」缺少「" + unit + "」到基础单位的换算规则");
    }

    private void applySnapshot(TaskZoneMaterial material, SnapshotResult snapshot) {
        material.setInputQty(snapshot.baseQty());
        material.setInputMode(snapshot.inputMode());
        material.setInputOriginalQty(snapshot.originalQty());
        material.setInputOriginalUnit(snapshot.originalUnit());
        material.setBaseUnitSnapshot(snapshot.baseUnit());
        material.setRuleIdSnapshot(snapshot.ruleId());
        material.setBaseQty(snapshot.baseQty());
        material.setConversionSnapshot(snapshot.conversionSnapshot());
        material.setUnitPriceSnapshot(snapshot.unitPrice());
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private boolean sameText(String a, String b) {
        return StringUtils.hasText(a) && StringUtils.hasText(b) && a.trim().equals(b.trim());
    }

    private boolean sameNumber(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private record SnapshotInput(String materialId, BigDecimal qty, String inputMode, BigDecimal originalQty,
                                 String originalUnit, BigDecimal weightQuantity, String weightUnit,
                                 BigDecimal countQuantity, String countUnit) {
        static SnapshotInput from(ItemSaveReq req) {
            return new SnapshotInput(req.getMaterialId(), req.getQty(), req.getInputMode(), req.getOriginalQty(),
                    req.getOriginalUnit(), req.getWeightQuantity(), req.getWeightUnit(), req.getCountQuantity(),
                    req.getCountUnit());
        }

        static SnapshotInput from(com.xzcpc.mp.dto.ZoneMaterialItem item) {
            return new SnapshotInput(item.getMaterialId(), item.getQty(), item.getInputMode(), item.getOriginalQty(),
                    item.getOriginalUnit(), item.getWeightQuantity(), item.getWeightUnit(), item.getCountQuantity(),
                    item.getCountUnit());
        }
    }

    private record SnapshotResult(String inputMode, BigDecimal originalQty, String originalUnit, String baseUnit,
                                  String ruleId, BigDecimal baseQty, String conversionSnapshot, BigDecimal unitPrice) {
    }
}
