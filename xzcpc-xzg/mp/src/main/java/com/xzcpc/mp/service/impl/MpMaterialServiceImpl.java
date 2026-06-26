package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.MaterialInfo;
import com.xzcpc.common.util.BizCodeUtil;
import com.xzcpc.mp.dto.AddMaterialReq;
import com.xzcpc.mp.dto.SortReq;
import com.xzcpc.mp.service.MpMaterialService;
import com.xzcpc.task.entity.*;
import com.xzcpc.task.mapper.*;
import com.xzcpc.template.service.MaterialService;
import com.xzcpc.template.service.MaterialRuleService;
import com.xzcpc.template.dto.MaterialRuleResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MpMaterialServiceImpl implements MpMaterialService {

    private final TaskMapper taskMapper;
    private final TaskZoneMapper taskZoneMapper;
    private final TaskZoneMaterialMapper taskZoneMaterialMapper;
    private final MaterialService materialService;
    private final MaterialRuleService materialRuleService;

    @Override
    public List<Map<String, Object>> getCandidates(Integer taskId, Integer zoneId, String keyword, String storeId) {
        validateTaskOwnership(taskId, storeId);

        List<TaskZoneMaterial> currentMaterials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId));

        Set<String> currentMaterialIds = currentMaterials.stream()
                .map(TaskZoneMaterial::getMaterialId)
                .collect(Collectors.toSet());

        List<MaterialInfo> allMaterials = materialService.searchMaterials(keyword);

        List<Map<String, Object>> result = new ArrayList<>();
        for (MaterialInfo m : allMaterials) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialId", m.getId());
            item.put("materialName", m.getYuancailiaomingcheng());
            item.put("qmCode", m.getPinxiangbianma() != null ? m.getPinxiangbianma() : "");
            item.put("parentCategory", m.getLeibie() != null ? m.getLeibie() : "");
            item.put("category", m.getLeibie2() != null ? m.getLeibie2() : "");
            item.put("spec", m.getGuige() != null ? m.getGuige() : "");
            item.put("inventoryUnit", m.getPandiandanwei() != null ? m.getPandiandanwei() : "");
            item.put("unit", m.getPandiandanwei() != null ? m.getPandiandanwei() : "");
            item.put("inCurrentZone", currentMaterialIds.contains(m.getId()));
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> lookupByQmCode(Integer taskId, Integer zoneId, String qmCode, String storeId) {
        validateTaskOwnership(taskId, storeId);
        MaterialInfo material = materialService.getByQmCode(qmCode);
        if (material == null) {
            throw new BusinessException(404, "未找到该编码对应的物料");
        }
        Set<String> currentMaterialIds = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId))
                .stream().map(TaskZoneMaterial::getMaterialId).collect(Collectors.toSet());

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("materialId", material.getId());
        item.put("materialName", material.getYuancailiaomingcheng() != null ? material.getYuancailiaomingcheng() : "");
        item.put("qmCode", material.getPinxiangbianma() != null ? material.getPinxiangbianma() : "");
        item.put("parentCategory", material.getLeibie() != null ? material.getLeibie() : "");
        item.put("category", material.getLeibie2() != null ? material.getLeibie2() : "");
        item.put("spec", material.getGuige() != null ? material.getGuige() : "");
        item.put("inventoryUnit", material.getPandiandanwei() != null ? material.getPandiandanwei() : "");
        item.put("unit", material.getPandiandanwei() != null ? material.getPandiandanwei() : "");
        item.put("inCurrentZone", currentMaterialIds.contains(material.getId()));
        return item;
    }

    @Override
    @Transactional
    public void addMaterial(Integer taskId, Integer zoneId, AddMaterialReq req, String storeId) {
        validateTaskOwnership(taskId, storeId);
        validateTaskNotSubmitted(taskId);

        // 1. 检查有效记录（del_flag=0，走 @TableLogic 自动过滤）
        List<TaskZoneMaterial> existing = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId)
                        .eq(TaskZoneMaterial::getMaterialId, req.getMaterialId()));
        if (!existing.isEmpty()) {
            throw new BusinessException("该物料已在当前分区中");
        }

        MaterialInfo material = materialService.getMaterialInfoById(req.getMaterialId());
        if (material == null) {
            throw new BusinessException("物料不存在");
        }

        String materialName = material.getYuancailiaomingcheng() != null ? material.getYuancailiaomingcheng() : "";
        String spec = material.getGuige() != null ? material.getGuige() : "";
        String unit = material.getPandiandanwei() != null ? material.getPandiandanwei() : "";

        int maxSort = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId))
                .stream()
                .mapToInt(m -> m.getSortNo() != null ? m.getSortNo() : 0)
                .max()
                .orElse(0);

        // 2. 检查是否存在已逻辑删除的旧记录（绕过 @TableLogic）
        TaskZoneMaterial deleted = taskZoneMaterialMapper.selectAnyByKey(taskId, zoneId, req.getMaterialId());
        if (deleted != null) {
            // 复活旧记录：将 del_flag 改为 0，同时刷新物料快照字段
            taskZoneMaterialMapper.reactivateByKey(
                    deleted.getId(), materialName, spec, unit, unit, maxSort + 1);
        } else {
            // 3. 全新的物料 → 正常新增
            TaskZoneMaterial tzm = new TaskZoneMaterial();
            tzm.setTaskId(taskId);
            tzm.setTaskZoneId(zoneId);
            tzm.setMaterialId(req.getMaterialId());
            tzm.setMaterialName(materialName);
            tzm.setSpec(spec);
            tzm.setUnit(unit);
            tzm.setInventoryUnit(unit);
            tzm.setSortNo(maxSort + 1);
            tzm.setInputStatus("not_entered");
            tzm.setBizCode(BizCodeUtil.of("TZM"));
            taskZoneMaterialMapper.insert(tzm);
        }
    }

    @Override
    @Transactional
    public void removeMaterial(Integer taskId, Integer zoneId, String materialId, String storeId) {
        validateTaskOwnership(taskId, storeId);
        validateTaskNotSubmitted(taskId);

        // 逻辑删除，数据可恢复，防止误删攻击
        taskZoneMaterialMapper.delete(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getTaskZoneId, zoneId)
                        .eq(TaskZoneMaterial::getMaterialId, materialId));
    }

    @Override
    @Transactional
    public void sortMaterials(Integer taskId, Integer zoneId, SortReq req, String storeId) {
        validateTaskOwnership(taskId, storeId);
        validateTaskNotSubmitted(taskId);

        for (int i = 0; i < req.getIds().size(); i++) {
            TaskZoneMaterial material = taskZoneMaterialMapper.selectById(req.getIds().get(i));
            if (material != null && material.getTaskZoneId().equals(zoneId)) {
                material.setSortNo(i + 1);
                taskZoneMaterialMapper.updateById(material);
            }
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

    @Override
    public List<Map<String, Object>> searchAcrossZones(Integer taskId, String keyword, String storeId) {
        validateTaskOwnership(taskId, storeId);

        String kw = keyword != null ? keyword.trim() : "";
        if (kw.isEmpty()) return List.of();

        List<TaskZoneMaterial> matches = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .like(TaskZoneMaterial::getMaterialName, kw)
                        .orderByAsc(TaskZoneMaterial::getTaskZoneId)
                        .last("LIMIT 30"));

        if (matches.isEmpty()) return List.of();

        Set<Integer> zoneIds = matches.stream()
                .map(TaskZoneMaterial::getTaskZoneId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Integer, TaskZone> zoneMap = taskZoneMapper.selectBatchIds(zoneIds).stream()
                .collect(Collectors.toMap(TaskZone::getId, z -> z));

        // 加载物料规则以获取分类
        List<String> materialIds = matches.stream().map(TaskZoneMaterial::getMaterialId).distinct().collect(Collectors.toList());
        Map<String, MaterialRuleResp> ruleMap = materialRuleService.batchDetail(materialIds);

        List<Map<String, Object>> results = new ArrayList<>();
        for (TaskZoneMaterial m : matches) {
            TaskZone zone = zoneMap.get(m.getTaskZoneId());
            MaterialRuleResp rule = ruleMap.get(m.getMaterialId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialId", m.getMaterialId());
            item.put("materialName", m.getMaterialName());
            item.put("zoneId", m.getTaskZoneId());
            item.put("zoneName", zone != null ? zone.getZoneName() : "");
            item.put("taskZoneId", m.getTaskZoneId());
            item.put("category", rule != null && rule.getCategory() != null ? rule.getCategory() : "");
            results.add(item);
        }
        return results;
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
}
