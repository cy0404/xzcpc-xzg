package com.xzcpc.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.event.MaterialChangedEvent;
import com.xzcpc.common.event.TemplateChangedEvent;
import com.xzcpc.task.entity.Task;
import com.xzcpc.task.entity.TaskZone;
import com.xzcpc.task.entity.TaskZoneMaterial;
import com.xzcpc.task.mapper.TaskMapper;
import com.xzcpc.task.mapper.TaskZoneMapper;
import com.xzcpc.task.mapper.TaskZoneMaterialMapper;
import com.xzcpc.template.entity.TemplateZone;
import com.xzcpc.template.entity.TemplateZoneMaterial;
import com.xzcpc.template.mapper.TemplateZoneMapper;
import com.xzcpc.template.mapper.TemplateZoneMaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateChangeListener {

    /** 防抖：2 秒内同一模板只触发一次 */
    private final ConcurrentHashMap<Integer, Long> lastSyncTime = new ConcurrentHashMap<>();

    private final TaskMapper taskMapper;
    private final TaskZoneMapper taskZoneMapper;
    private final TaskZoneMaterialMapper taskZoneMaterialMapper;
    private final TemplateZoneMapper templateZoneMapper;
    private final TemplateZoneMaterialMapper templateZoneMaterialMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTemplateChanged(TemplateChangedEvent event) {
        Integer templateId = event.getTemplateId();

        // 防抖：2 秒内同一模板的重复事件跳过
        long now = System.currentTimeMillis();
        Long last = lastSyncTime.put(templateId, now);
        if (last != null && now - last < 2000) {
            log.info("模板 {} 2 秒内已有同步任务，跳过", templateId);
            return;
        }

        log.info("模板 {} 变更，异步同步未开始任务", templateId);

        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getTemplateId, templateId)
                        .eq(Task::getStatus, "not_started"));

        if (tasks.isEmpty()) return;

        for (Task task : tasks) {
            resyncTask(task);
        }
        log.info("已同步 {} 个未开始任务", tasks.size());
    }

    /**
     * 物料基本信息变更时，找到所有引用该物料的模板，同步其未开始任务。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaterialChanged(MaterialChangedEvent event) {
        String materialId = event.getMaterialId();
        log.info("物料 {} 基本信息变更，查找引用该物料的模板...", materialId);

        // 找到所有引用了该物料的模板分区(zone_id)
        List<TemplateZoneMaterial> zoneMaterials = templateZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TemplateZoneMaterial>()
                        .eq(TemplateZoneMaterial::getMaterialId, materialId));
        if (zoneMaterials.isEmpty()) return;

        // 提取 zoneId → 查找对应的 templateId
        Set<Integer> zoneIds = zoneMaterials.stream()
                .map(TemplateZoneMaterial::getZoneId).collect(Collectors.toSet());
        List<TemplateZone> zones = templateZoneMapper.selectBatchIds(zoneIds);
        Set<Integer> templateIds = zones.stream()
                .map(TemplateZone::getTemplateId).collect(Collectors.toSet());

        if (templateIds.isEmpty()) return;

        for (Integer templateId : templateIds) {
            long now = System.currentTimeMillis();
            Long last = lastSyncTime.put(templateId, now);
            if (last != null && now - last < 2000) {
                log.info("模板 {} 2 秒内已有同步任务，跳过", templateId);
                continue;
            }

            List<Task> tasks = taskMapper.selectList(
                    new LambdaQueryWrapper<Task>()
                            .eq(Task::getTemplateId, templateId)
                            .eq(Task::getStatus, "not_started"));
            for (Task task : tasks) {
                resyncTask(task);
            }
            log.info("模板 {} 已同步 {} 个未开始任务", templateId, tasks.size());
        }
    }

    private void resyncTask(Task task) {
        Integer taskId = task.getId();

        // 删除旧快照
        taskZoneMaterialMapper.delete(
                new LambdaQueryWrapper<TaskZoneMaterial>().eq(TaskZoneMaterial::getTaskId, taskId));
        taskZoneMapper.delete(
                new LambdaQueryWrapper<TaskZone>().eq(TaskZone::getTaskId, taskId));

        // 从当前模板重建（Java 排序，sort_no 可空，空值排最后，与模板模块 zones() 排序一致）
        List<TemplateZone> templateZones = templateZoneMapper.selectList(
                new LambdaQueryWrapper<TemplateZone>()
                        .eq(TemplateZone::getTemplateId, task.getTemplateId()));
        templateZones.sort((a, b) -> {
            if (a.getSortNo() != null && b.getSortNo() != null) return a.getSortNo() - b.getSortNo();
            if (a.getSortNo() != null) return -1;
            if (b.getSortNo() != null) return 1;
            return 0;
        });

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
}
