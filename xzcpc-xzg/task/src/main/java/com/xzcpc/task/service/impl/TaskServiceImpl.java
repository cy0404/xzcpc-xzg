package com.xzcpc.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.common.util.BizCodeUtil;
import com.xzcpc.task.dto.MaterialUpdateReq;
import com.xzcpc.task.dto.TaskCreateRequest;
import com.xzcpc.task.dto.TaskUpdateRequest;
import com.xzcpc.task.entity.Task;
import com.xzcpc.task.entity.TaskZone;
import com.xzcpc.task.entity.TaskZoneMaterial;
import com.xzcpc.task.entity.StoreOrderCycle;
import com.xzcpc.task.mapper.StoreOrderCycleMapper;
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
import com.xzcpc.task.mapper.StoreMapper;
import com.xzcpc.task.mapper.TaskMaterialSummaryMapper;
import com.xzcpc.task.entity.TaskMaterialSummary;
import com.xzcpc.task.entity.Store;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
    private final TaskMaterialSummaryMapper taskMaterialSummaryMapper;
    private final ObjectMapper objectMapper;
    private final StoreAccessService storeAccessService;
    private final StoreMapper storeMapper;
    private final StoreOrderCycleMapper storeOrderCycleMapper;

    /** 星期名称（1周一-7周日），用于周盘任务名区分同周多次 */
    private static final String[] WEEK_DAY_NAMES = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @Override
    public String getLatestMonth() {
        Task task = taskMapper.selectOne(
                new LambdaQueryWrapper<Task>().orderByDesc(Task::getTaskMonth).last("LIMIT 1"));
        return task != null ? task.getTaskMonth() : "";
    }

    @Override
    public Page<Task> page(String storeId, String supervisorName, String status, String keyword, String templateName,
                           String taskMonth, String taskType, int pageNum, int pageSize) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(Task::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤（管理员选择督导后只看该督导管辖的门店）
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(Task::getStoreId, supervisorStoreIds);
        }

        if (StringUtils.hasText(storeId)) {
            String[] ids = storeId.split(",");
            if (ids.length == 1) {
                wrapper.eq(Task::getStoreId, ids[0].trim());
            } else {
                wrapper.in(Task::getStoreId, java.util.Arrays.asList(ids));
            }
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
        if (StringUtils.hasText(taskType)) {
            wrapper.eq(Task::getTaskType, taskType);
        }
        if (StringUtils.hasText(templateName)) {
            List<Integer> matchedIds = templateMapper.selectList(
                    new LambdaQueryWrapper<Template>()
                            .like(Template::getTemplateName, templateName))
                    .stream().map(Template::getId).collect(Collectors.toList());
            if (matchedIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(Task::getTemplateId, matchedIds);
        }
        wrapper.orderByDesc(Task::getCreatedAt);
        Page<Task> result = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<Task> records = result.getRecords();
        if (records.isEmpty()) return result;

        Set<Integer> taskIds = records.stream().map(Task::getId).collect(Collectors.toSet());
        Set<Integer> tplIds = records.stream().map(Task::getTemplateId).filter(id -> id != null).collect(Collectors.toSet());

        // 只查当前页涉及的模板名（不拉全表）
        Map<Integer, String> templateNameMap = tplIds.isEmpty() ? Map.of()
                : templateMapper.selectList(
                        new LambdaQueryWrapper<Template>().in(Template::getId, tplIds))
                        .stream().collect(Collectors.toMap(Template::getId, Template::getTemplateName));

        // GROUP BY 聚合，只查两列（task_id + count），不拉全表
        Map<Integer, Long> zoneCountMap = taskIds.isEmpty() ? Map.of()
                : taskZoneMapper.selectMaps(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TaskZone>()
                                .select("task_id, COUNT(*) AS cnt")
                                .in("task_id", taskIds)
                                .groupBy("task_id"))
                        .stream().collect(Collectors.toMap(
                                m -> (Integer) m.get("task_id"),
                                m -> ((Number) m.get("cnt")).longValue()));
        Map<Integer, Long> materialCountMap = taskIds.isEmpty() ? Map.of()
                : taskZoneMaterialMapper.selectMaps(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TaskZoneMaterial>()
                                .select("task_id, COUNT(*) AS cnt")
                                .in("task_id", taskIds)
                                .groupBy("task_id"))
                        .stream().collect(Collectors.toMap(
                                m -> (Integer) m.get("task_id"),
                                m -> ((Number) m.get("cnt")).longValue()));

        // 批量查当前页涉及的所有 store（一次查询替代 N+1）
        Set<String> pageStoreIds = records.stream().map(Task::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
        Map<String, Store> storeEntityMap = pageStoreIds.isEmpty() ? Map.of()
                : storeMapper.selectList(new LambdaQueryWrapper<Store>().in(Store::getStoreId, pageStoreIds))
                        .stream().collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));
        Map<String, String> supervisorMap = pageStoreIds.isEmpty() ? Map.of()
                : storeAccessService.getSupervisorNamesByStoreIds(pageStoreIds);

        for (Task task : records) {
            Store storeEntity = storeEntityMap.get(task.getStoreId());
            if (storeEntity != null) {
                if (task.getStoreName() == null) task.setStoreName(storeEntity.getStoreName());
                if (task.getXiaochengxuid() == null) task.setXiaochengxuid(storeEntity.getXiaochengxuid());
                if (task.getWarehouseCode() == null) task.setWarehouseCode(storeEntity.getCangkuid());
            }
            task.setSupervisorName(supervisorMap.getOrDefault(task.getStoreId(), ""));
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
        String taskType = StringUtils.hasText(request.getTaskType()) ? request.getTaskType() : "monthly";
        if (!"monthly".equals(taskType) && !"weekly".equals(taskType)) {
            throw new BusinessException("任务类型非法: " + taskType);
        }

        // 周盘：校验周标签并推导周起始日（周一），周盘任务不传统一 deadline，逐门店按配置的周盘点日自动计算
        LocalDateTime weekStart = null;
        if ("weekly".equals(taskType)) {
            if (!StringUtils.hasText(request.getTaskWeek())) {
                throw new BusinessException("周盘任务必须选择盘点周");
            }
            weekStart = parseWeekStart(request.getTaskWeek());
        } else if (!StringUtils.hasText(request.getTaskMonth())) {
            throw new BusinessException("月盘任务必须选择盘点月份");
        }

        Map<String, StoreInfo> storeMap = storeService.getStoreMap();
        List<String> noDayStores = new java.util.ArrayList<>();
        int count = 0;
        for (String storeId : request.getStoreIds()) {
            StoreInfo storeInfo = storeMap.get(storeId);
            LocalDateTime deadline = request.getDeadline();
            if ("weekly".equals(taskType)) {
                // 门店未配置订货周期 → 收集并报错，不创建
                List<Integer> orderDays = parseOrderDays(storeOrderCycleMapper.selectOne(
                        new LambdaQueryWrapper<StoreOrderCycle>()
                                .eq(StoreOrderCycle::getStoreId, storeId)));
                if (orderDays.isEmpty()) {
                    noDayStores.add(storeInfo != null ? storeInfo.getMendianmingcheng() : storeId);
                    continue;
                }
                // 订货日：请求指定（自动生成场景每订货日各一次）优先，缺省取配置第一个
                int orderDay = request.getOrderDay() != null ? request.getOrderDay() : orderDays.get(0);
                if (orderDay < 1 || orderDay > 7) {
                    throw new BusinessException("订货日非法: " + orderDay);
                }
                // 盘点日 = 订货日前一天（周一订货 → 上周日盘点）；截止时间 = 盘点日 23:59:59
                int inventoryDay = inventoryDayOfOrderDay(orderDay);
                deadline = weekStart.plusDays(inventoryDay - 1L).toLocalDate().atTime(23, 59, 59);
            }
            // 存在未配置订货周期的门店：跳过创建，统一在最后报错（事务回滚保证原子性）

            // 周盘防重：同门店同周同截止时间已有未提交（not_started/in_progress/overdue）的周盘任务则拒绝
            // （同周多次周盘按 deadline 区分）
            if ("weekly".equals(taskType)) {
                Long dup = taskMapper.selectCount(
                        new LambdaQueryWrapper<Task>()
                                .eq(Task::getStoreId, storeId)
                                .eq(Task::getTaskType, "weekly")
                                .eq(Task::getTaskWeek, request.getTaskWeek())
                                .eq(Task::getDeadline, deadline)
                                .in(Task::getStatus, "not_started", "in_progress", "overdue"));
                if (dup > 0) {
                    throw new BusinessException("门店[" + (storeInfo != null ? storeInfo.getMendianmingcheng() : storeId)
                            + "]本周该订货日已存在周盘任务，请勿重复创建");
                }
            }

            Task task = new Task();
            task.setTaskName(request.getTaskName());
            task.setTaskMonth(request.getTaskMonth());
            task.setTaskType(taskType);
            task.setTaskWeek("weekly".equals(taskType) ? request.getTaskWeek() : null);
            task.setStoreId(storeId);
            task.setStoreName(storeInfo != null ? storeInfo.getMendianmingcheng() : null);
            task.setStoreCode(storeInfo != null ? storeInfo.getBianma() : null);
            task.setXiaochengxuid(storeInfo != null ? storeInfo.getXiaochengxuid() : null);
            task.setWarehouseCode(storeInfo != null ? storeInfo.getCangkuid() : null);
            task.setTemplateId(request.getTemplateId());
            task.setDeadline(deadline);
            task.setStatus("not_started");
            task.setCreatedBy("admin");
            task.setId(null);
            task.setBizCode(BizCodeUtil.of("TASK"));
            taskMapper.insert(task);

            snapshotTemplate(task.getId(), task.getTemplateId());
            count++;
        }
        if (!noDayStores.isEmpty()) {
            throw new BusinessException("以下门店未配置订货周期，请先到门店订货周期配置设置："
                    + String.join("、", noDayStores));
        }
        return count;
    }

    /** ISO 周标签格式 YYYY-Www（Locale.ROOT 保证 ISO 周制） */
    private static final DateTimeFormatter ISO_WEEK_FMT =
            java.time.format.DateTimeFormatter.ofPattern("YYYY-'W'ww", java.util.Locale.ROOT);

    @Override
    public Map<String, Object> autoGenerateWeekly() {
        // 1. 启用的 weekly 模板（全局一份，取最新启用的）
        Template template = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getTemplateType, "weekly")
                .eq(Template::getStatus, 1)
                .orderByDesc(Template::getUpdatedAt)
                .last("LIMIT 1"));
        if (template == null) {
            log.warn("WEEKLY_GEN 无启用的周盘模板，未生成任务");
            return Map.of("generated", 0, "skipped", 0, "failed", List.of(), "taskWeek", "", "warning", "无启用的周盘模板");
        }

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        String taskWeek = ISO_WEEK_FMT.format(weekStart);
        int weekNo = Integer.parseInt(taskWeek.substring(taskWeek.indexOf('W') + 1));
        String taskName = weekStart.getYear() + "年第" + weekNo + "周周盘";

        int generated = 0, skipped = 0;
        List<String> failed = new ArrayList<>();
        Map<String, StoreInfo> storeMap = storeService.getStoreMap();
        // 按门店订货周期表：每个订货日 → 盘点日=订货日-1，盘点日在今天或明天则生成
        List<StoreOrderCycle> cycles = storeOrderCycleMapper.selectList(
                new LambdaQueryWrapper<StoreOrderCycle>().orderByAsc(StoreOrderCycle::getId));
        for (StoreOrderCycle cycle : cycles) {
            // 暂停的门店不参与周盘
            if (cycle.getPaused() != null && cycle.getPaused() == 1) { skipped++; continue; }
            List<Integer> orderDays = parseOrderDays(cycle);
            if (orderDays.isEmpty()) { skipped++; continue; }
            StoreInfo store = storeMap.get(cycle.getStoreId());
            if (store == null) { skipped++; continue; }
            for (int orderDay : orderDays) {
                // 盘点日 = 订货日前一天（周一订货 → 上周日盘点）；窗口：今天/明天
                LocalDate inventoryDate = weekStart.plusDays(inventoryDayOfOrderDay(orderDay) - 1L);
                if (inventoryDate.isBefore(today) || inventoryDate.isAfter(today.plusDays(1))) { skipped++; continue; }
                // 幂等：同店同周同截止时间（同周多次周盘按 deadline 区分）
                LocalDateTime deadline = inventoryDate.atTime(23, 59, 59);
                Long dup = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                        .eq(Task::getStoreId, store.getId())
                        .eq(Task::getTaskType, "weekly")
                        .eq(Task::getTaskWeek, taskWeek)
                        .eq(Task::getDeadline, deadline)
                        .in(Task::getStatus, "not_started", "in_progress", "overdue"));
                if (dup != null && dup > 0) { skipped++; continue; }
                try {
                    TaskCreateRequest req = new TaskCreateRequest();
                    // 任务名带盘点日，同周多次可辨认
                    req.setTaskName(taskName + "·" + WEEK_DAY_NAMES[inventoryDayOfOrderDay(orderDay)]);
                    req.setTaskType("weekly");
                    req.setTaskWeek(taskWeek);
                    req.setOrderDay(orderDay);
                    req.setStoreIds(List.of(store.getId()));
                    req.setTemplateId(template.getId());
                    batchCreate(req);
                    generated++;
                } catch (Exception e) {
                    log.warn("WEEKLY_GEN 门店生成失败 storeId={} orderDay={}: {}", store.getId(), orderDay, e.getMessage());
                    failed.add(store.getId());
                }
            }
        }
        log.info("WEEKLY_GEN done: taskWeek={} generated={} skipped={} failed={}", taskWeek, generated, skipped, failed);
        return Map.of("generated", generated, "skipped", skipped, "failed", failed, "taskWeek", taskWeek);
    }

    /** 订货日 → 盘点日（订货日前一天）：周一(1)订货 → 上周日(7)盘点 */
    private static int inventoryDayOfOrderDay(int orderDay) {
        return orderDay == 1 ? 7 : orderDay - 1;
    }

    /** 解析订货日配置 "1,4" → [1,4]（去重、过滤非法值） */
    private static List<Integer> parseOrderDays(StoreOrderCycle cycle) {
        List<Integer> list = new ArrayList<>();
        if (cycle == null || !StringUtils.hasText(cycle.getOrderDays())) return list;
        for (String s : cycle.getOrderDays().split(",")) {
            try {
                int d = Integer.parseInt(s.trim());
                if (d >= 1 && d <= 7 && !list.contains(d)) list.add(d);
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    /** 解析周标签 YYYY-Www → 该周周一 00:00:00 */
    private LocalDateTime parseWeekStart(String taskWeek) {
        if (!taskWeek.matches("\\d{4}-W\\d{2}")) {
            throw new BusinessException("盘点周格式非法: " + taskWeek + "（应为 YYYY-Www，如 2026-W34）");
        }
        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                .appendPattern("YYYY-'W'ww")
                .parseDefaulting(ChronoField.DAY_OF_WEEK, 1) // ISO 周内补周一
                .toFormatter();
        try {
            LocalDate weekStart = LocalDate.parse(taskWeek, fmt);
            return weekStart.atStartOfDay();
        } catch (Exception e) {
            throw new BusinessException("盘点周无效: " + taskWeek);
        }
    }

    @Override
    public void create(Task task) {
        task.setId(null);
        task.setStatus("not_started");
        task.setCreatedBy("admin");
        task.setBizCode(BizCodeUtil.of("TASK"));
        taskMapper.insert(task);
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

        List<TaskZoneMaterial> batchMaterials = new java.util.ArrayList<>();
        for (TemplateZone tz : templateZones) {
            TaskZone taskZone = new TaskZone();
            taskZone.setTaskId(taskId);
            taskZone.setZoneName(tz.getZoneName());
            taskZone.setSortNo(tz.getSortNo());
            taskZone.setSourceType("template");
            taskZone.setBizCode(BizCodeUtil.of("TKZ"));
            taskZoneMapper.insert(taskZone);  // 单个插入，保证拿到 ID

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
                tzm.setBizCode(BizCodeUtil.of("TZM"));
                batchMaterials.add(tzm);
            }
        }
        // 批量插入所有物料（一条 SQL）
        if (!batchMaterials.isEmpty()) {
            taskZoneMaterialMapper.insertBatch(batchMaterials);
        }
    }

    @Override
    public Task detail(Integer id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
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
                mMap.put("id", m.getId());
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
            MaterialInventoryRule rule = ruleMap.get(materialId);
            BigDecimal unitPrice = rule != null ? rule.getUnitPrice() : null;
            BigDecimal addQty = snapshotQty(m);
            if (!summaryMap.containsKey(materialId)) {
                Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("materialId", materialId);
                sm.put("materialName", m.getMaterialName());
                sm.put("spec", m.getSpec() != null ? m.getSpec() : "");
                sm.put("zoneCount", 1);
                sm.put("totalQuantity", addQty);
                sm.put("unit", snapshotUnit(m));
                // 多单位链：优先快照，快照为空则从盘点规则兜底
                String invUnit = m.getInventoryUnit();
                if ((invUnit == null || invUnit.isEmpty()) && rule != null && rule.getInventoryUnits() != null) {
                    invUnit = rule.getInventoryUnits();
                }
                sm.put("inventoryUnit", invUnit != null ? invUnit : "");
                sm.put("unitInputs", m.getUnitInputs() != null ? m.getUnitInputs() : "");
                sm.put("remark", m.getRemark() != null ? m.getRemark() : "");
                sm.put("unitPrice", unitPrice);
                sm.put("amount", unitPrice != null ? addQty.multiply(unitPrice) : null);
                summaryMap.put(materialId, sm);
            } else {
                Map<String, Object> sm = summaryMap.get(materialId);
                sm.put("zoneCount", (int) sm.get("zoneCount") + 1);
                BigDecimal current = (BigDecimal) sm.get("totalQuantity");
                BigDecimal newTotal = current.add(addQty);
                sm.put("totalQuantity", newTotal);
                // 合并多条记录的 amount
                BigDecimal curAmount = (BigDecimal) sm.getOrDefault("amount", BigDecimal.ZERO);
                if (curAmount == null) curAmount = BigDecimal.ZERO;
                if (unitPrice != null) {
                    sm.put("amount", curAmount.add(addQty.multiply(unitPrice)));
                }
                String merged = mergeUnitInputs((String) sm.get("unitInputs"), m.getUnitInputs());
                sm.put("unitInputs", merged);
            }
        }
        List<Map<String, Object>> summary = new java.util.ArrayList<>(summaryMap.values());

        // 计算总金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map<String, Object> sm : summary) {
            BigDecimal amt = (BigDecimal) sm.getOrDefault("amount", BigDecimal.ZERO);
            if (amt != null) totalAmount = totalAmount.add(amt);
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("zones", zoneList);
        result.put("summary", summary);
        result.put("totalAmount", totalAmount);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, TaskUpdateRequest req) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        // P0: submitted/pending_submit 不可修改基本信息；overdue 仅允许延长时间
        if ("submitted".equals(task.getStatus()) || "pending_submit".equals(task.getStatus())) {
            throw new BusinessException("已提交/待提交的任务不可修改基本信息");
        }
        if (req.getTaskName() != null) task.setTaskName(req.getTaskName());
        if (req.getTaskMonth() != null) task.setTaskMonth(req.getTaskMonth());
        if (req.getDeadline() != null) {
            task.setDeadline(req.getDeadline());
            // P0: 延长截止时间 → 逾期任务恢复为进行中
            if ("overdue".equals(task.getStatus())) {
                task.setStatus("in_progress");
            }
        }
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMaterials(Integer taskId, List<MaterialUpdateReq> materials) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (!"submitted".equals(task.getStatus())) {
            throw new BusinessException("仅已提交的任务可修改物料数量");
        }
        for (MaterialUpdateReq m : materials) {
            TaskZoneMaterial tzm = taskZoneMaterialMapper.selectById(m.getId());
            if (tzm == null || !tzm.getTaskId().equals(taskId)) {
                continue;
            }
            if (m.getInputQty() != null) tzm.setInputQty(m.getInputQty());
            if (m.getBaseQty() != null) tzm.setBaseQty(m.getBaseQty());
            if (m.getUnitInputs() != null) tzm.setUnitInputs(m.getUnitInputs());
            tzm.setInputStatus(m.getInputQty() != null && m.getInputQty().compareTo(BigDecimal.ZERO) > 0
                    ? "entered" : "zero_entered");
            taskZoneMaterialMapper.updateById(tzm);
        }
        rebuildSummary(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Integer taskId, Integer materialId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (!"submitted".equals(task.getStatus())) {
            throw new BusinessException("仅已提交的任务可删除物料");
        }
        TaskZoneMaterial tzm = taskZoneMaterialMapper.selectById(materialId);
        if (tzm == null || !tzm.getTaskId().equals(taskId)) {
            throw new BusinessException("物料不存在");
        }
        taskZoneMaterialMapper.deleteById(materialId);
        rebuildSummary(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setMaterialTotal(Integer taskId, String materialId, BigDecimal totalQty) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if (!"submitted".equals(task.getStatus())) throw new BusinessException("仅已提交的任务可编辑");

        List<TaskZoneMaterial> records = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getMaterialId, materialId));
        if (records.isEmpty()) throw new BusinessException("物料不在该任务中");

        boolean first = true;
        for (TaskZoneMaterial tzm : records) {
            if (first) {
                tzm.setInputQty(totalQty);
                tzm.setBaseQty(totalQty);
                tzm.setUnitInputs("");
                tzm.setInputStatus(totalQty.compareTo(BigDecimal.ZERO) > 0 ? "entered" : "zero_entered");
                first = false;
            } else {
                tzm.setInputQty(BigDecimal.ZERO);
                tzm.setBaseQty(BigDecimal.ZERO);
                tzm.setUnitInputs("");
                tzm.setInputStatus("zero_entered");
            }
            taskZoneMaterialMapper.updateById(tzm);
        }
        rebuildSummary(taskId);
    }

    private void rebuildSummary(Integer taskId) {
        // 保留已有物料的 original_qty，以防覆盖任务提交时的快照
        List<TaskMaterialSummary> existingSummaries = taskMaterialSummaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, taskId));
        Map<String, BigDecimal> origQtyMap = new LinkedHashMap<>();
        for (TaskMaterialSummary es : existingSummaries) {
            if (es.getOriginalQty() != null) {
                origQtyMap.put(es.getMaterialId(), es.getOriginalQty());
            }
        }

        // 硬删除旧的汇总记录
        taskMaterialSummaryMapper.physicalDeleteByTaskId(taskId);

        List<TaskZoneMaterial> allMaterials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>().eq(TaskZoneMaterial::getTaskId, taskId));
        Map<String, TaskMaterialSummary> summaryMap = new LinkedHashMap<>();
        for (TaskZoneMaterial m : allMaterials) {
            BigDecimal qty = snapshotQty(m);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            String materialId = m.getMaterialId();
            summaryMap.compute(materialId, (k, v) -> {
                if (v == null) {
                    v = new TaskMaterialSummary();
                    v.setTaskId(taskId);
                    v.setMaterialId(materialId);
                    v.setMaterialName(m.getMaterialName());
                    v.setSpec(m.getSpec());
                    v.setBaseUnit(snapshotUnit(m));
                    v.setTotalQty(qty);
                    // originalQty: 已有则保留原值，新物料则用当前 qty
                    BigDecimal orig = origQtyMap.getOrDefault(materialId, qty);
                    v.setOriginalQty(orig);
                    v.setAdjustedQty(qty);
                    v.setZoneCount(1);
                    v.setUnitBreakdown(m.getUnitInputs());
                } else {
                    v.setTotalQty(v.getTotalQty().add(qty));
                    v.setAdjustedQty(v.getAdjustedQty().add(qty));
                    v.setOriginalQty(v.getOriginalQty().add(qty));
                    v.setZoneCount(v.getZoneCount() + 1);
                    v.setUnitBreakdown(mergeUnitInputs(v.getUnitBreakdown(), m.getUnitInputs()));
                }
                return v;
            });
        }
        if (!summaryMap.isEmpty()) {
            taskMaterialSummaryMapper.insertBatch(new ArrayList<>(summaryMap.values()));
        }
    }
}