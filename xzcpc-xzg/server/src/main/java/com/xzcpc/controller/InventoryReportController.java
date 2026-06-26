package com.xzcpc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.response.R;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.mapper.ExpenseRecordMapper;
import com.xzcpc.mp.entity.StoreWorkHours;
import com.xzcpc.mp.mapper.StoreWorkHoursMapper;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialConversionRule;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialConversionRuleMapper;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.entity.Task;
import com.xzcpc.task.entity.TaskMaterialSummary;
import com.xzcpc.task.entity.TaskZoneMaterial;
import com.xzcpc.task.mapper.StoreMapper;
import com.xzcpc.task.mapper.TaskMapper;
import com.xzcpc.task.mapper.TaskMaterialSummaryMapper;
import com.xzcpc.task.mapper.TaskZoneMaterialMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class InventoryReportController {

    private final TaskMapper taskMapper;
    private final TaskMaterialSummaryMapper taskMaterialSummaryMapper;
    private final TaskZoneMaterialMapper taskZoneMaterialMapper;
    private final MaterialMapper materialMapper;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialConversionRuleMapper conversionMapper;
    private final StoreWorkHoursMapper workHoursMapper;
    private final StoreMapper storeMapper;

    @Value("${report.api.token:U09MTyBHZW5lcmF0ZWQgMjAyNg==}")
    private String reportToken;

    @GetMapping("/inventory-result")
    public R<Map<String, Object>> inventoryResult(
            @RequestParam(required = false) String months,
            @RequestParam(required = false) String storeIds,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {

        if (!checkToken(request)) return R.fail(401, "未授权访问");
        List<String> monthList = parseList(months);
        List<String> storeIdList = parseList(storeIds);
        LambdaQueryWrapper<Task> taskW = new LambdaQueryWrapper<Task>().eq(Task::getStatus, "submitted");
        if (!monthList.isEmpty()) taskW.in(Task::getTaskMonth, monthList);
        if (!storeIdList.isEmpty()) taskW.in(Task::getStoreId, storeIdList);
        List<Task> tasks = taskMapper.selectList(taskW);
        if (tasks.isEmpty()) return R.ok(pageResult(List.of(), 0, pageNum, pageSize));

        Map<Integer, Task> taskMap = new HashMap<>();
        for (Task t : tasks) taskMap.put(t.getId(), t);

        // 2. 查汇总数据
        List<Integer> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskMaterialSummary> summaries = taskMaterialSummaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().in(TaskMaterialSummary::getTaskId, taskIds));

        // 3. 从任务快照取盘点单位和单价（任务提交时的值，不受后续规则变更影响）
        Map<String, String> snapUnit = new HashMap<>();
        Map<String, BigDecimal> snapPrice = new HashMap<>();
        if (!taskIds.isEmpty()) {
            List<TaskZoneMaterial> zms = taskZoneMaterialMapper.selectList(
                    new LambdaQueryWrapper<TaskZoneMaterial>().in(TaskZoneMaterial::getTaskId, taskIds));
            for (TaskZoneMaterial zm : zms) {
                if (StringUtils.hasText(zm.getBaseUnitSnapshot())) snapUnit.putIfAbsent(zm.getMaterialId(), zm.getBaseUnitSnapshot());
                if (zm.getUnitPriceSnapshot() != null) snapPrice.putIfAbsent(zm.getMaterialId(), zm.getUnitPriceSnapshot());
            }
        }

        // 5. 物料详情
        List<String> matIds = summaries.stream().map(TaskMaterialSummary::getMaterialId).distinct().toList();
        Map<String, Material> matMap = matIds.isEmpty() ? Map.of()
                : materialMapper.selectList(new LambdaQueryWrapper<Material>().in(Material::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));

        // 5. 按门店+月份分组
        Map<String, Map<String, Object>> storeMap = new LinkedHashMap<>();
        for (TaskMaterialSummary sm : summaries) {
            Task task = taskMap.get(sm.getTaskId());
            if (task == null) continue;
            String key = task.getStoreId() + "|" + task.getTaskMonth();

            Map<String, Object> store = storeMap.computeIfAbsent(key, k -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("taskMonth", task.getTaskMonth());
                s.put("storeId", task.getStoreId());
                s.put("storeXiaochengxuId", nvl(task.getXiaochengxuid()));
                s.put("storeName", nvl(task.getStoreName()));
                s.put("materials", new ArrayList<Map<String, Object>>());
                return s;
            });

            Material mat = matMap.get(sm.getMaterialId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("parentCategory", mat != null ? nvl(mat.getParentCategory()) : "");
            m.put("category", mat != null ? nvl(mat.getCategory()) : "");
            m.put("materialId", sm.getMaterialId());
            m.put("materialQmCode", mat != null ? nvl(mat.getQmCode()) : "");
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null ? nvl(mat.getSpec()) : "");
            m.put("inventoryUnit", snapUnit.getOrDefault(sm.getMaterialId(), ""));
            m.put("unitPrice", snapPrice.getOrDefault(sm.getMaterialId(), null));
            m.put("totalQuantity", sm.getTotalQty());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> materials = (List<Map<String, Object>>) store.get("materials");
            materials.add(m);
        }

        List<Map<String, Object>> all = new ArrayList<>(storeMap.values());
        int total = all.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return R.ok(pageResult(all.subList(from, to), total, pageNum, pageSize));
    }

    // ---------- 企迈盘点单 ----------

    @GetMapping("/qimai-inventory")
    public R<Map<String, Object>> qimaiInventory(
            @RequestParam(required = false) String months,
            @RequestParam(required = false) String storeIds,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {

        if (!checkToken(request)) return R.fail(401, "未授权访问");

        List<String> monthList = parseList(months);
        List<String> storeIdList = parseList(storeIds);

        // 1. 查任务
        LambdaQueryWrapper<Task> taskW = new LambdaQueryWrapper<Task>().eq(Task::getStatus, "submitted");
        if (!monthList.isEmpty()) taskW.in(Task::getTaskMonth, monthList);
        if (!storeIdList.isEmpty()) taskW.in(Task::getStoreId, storeIdList);
        List<Task> tasks = taskMapper.selectList(taskW);
        if (tasks.isEmpty()) return R.ok(pageResult(List.of(), 0, pageNum, pageSize));

        Map<Integer, Task> taskMap = new HashMap<>();
        for (Task t : tasks) taskMap.put(t.getId(), t);

        // 2. 查汇总
        List<Integer> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskMaterialSummary> summaries = taskMaterialSummaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().in(TaskMaterialSummary::getTaskId, taskIds));

        // 3. 查物料
        List<String> matIds = summaries.stream().map(TaskMaterialSummary::getMaterialId).distinct().toList();
        Map<String, Material> matMap = matIds.isEmpty() ? Map.of()
                : materialMapper.selectList(new LambdaQueryWrapper<Material>().in(Material::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));

        // 4. 查盘点规则（baseUnit + stockUnit）
        Map<String, MaterialInventoryRule> ruleMap = matIds.isEmpty() ? Map.of()
                : ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                        .in(MaterialInventoryRule::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));

        // 5. 查换算关系
        List<String> ruleIds = ruleMap.values().stream().map(MaterialInventoryRule::getRuleId).distinct().toList();
        Map<String, List<MaterialConversionRule>> convMap = ruleIds.isEmpty() ? Map.of()
                : conversionMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .in(MaterialConversionRule::getRuleId, ruleIds))
                .stream().collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));

        // 6. 分组输出
        Map<String, Map<String, Object>> storeMap = new LinkedHashMap<>();
        for (TaskMaterialSummary sm : summaries) {
            Task task = taskMap.get(sm.getTaskId());
            if (task == null) continue;
            String key = task.getStoreId() + "|" + task.getTaskMonth();

            Map<String, Object> store = storeMap.computeIfAbsent(key, k -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("taskMonth", task.getTaskMonth());
                s.put("storeId", task.getStoreId());
                s.put("storeName", nvl(task.getStoreName()));
                s.put("materials", new ArrayList<Map<String, Object>>());
                return s;
            });

            Material mat = matMap.get(sm.getMaterialId());
            MaterialInventoryRule rule = ruleMap.get(sm.getMaterialId());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialId", sm.getMaterialId());
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null ? nvl(mat.getSpec()) : "");
            m.put("baseUnit", rule != null ? nvl(rule.getBaseUnit()) : "");
            m.put("baseQuantity", sm.getTotalQty());

            // 换算到库存单位
            String stockUnit = "";
            BigDecimal stockQty = sm.getTotalQty();
            if (rule != null && StringUtils.hasText(rule.getStockUnit())) {
                stockUnit = rule.getStockUnit();
                if (!rule.getStockUnit().equals(rule.getBaseUnit())) {
                    List<MaterialConversionRule> conversions = convMap.getOrDefault(rule.getRuleId(), List.of());
                    stockQty = convertToStock(sm.getTotalQty(), rule.getBaseUnit(), stockUnit, conversions);
                }
            } else if (rule != null && StringUtils.hasText(rule.getBaseUnit())) {
                stockUnit = rule.getBaseUnit();
            }
            m.put("stockUnit", stockUnit);
            m.put("stockQuantity", stockQty);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> materials = (List<Map<String, Object>>) store.get("materials");
            materials.add(m);
        }

        List<Map<String, Object>> all = new ArrayList<>(storeMap.values());
        return R.ok(pageResult(all, all.size(), pageNum, pageSize));
    }

    private BigDecimal convertToStock(BigDecimal baseQty, String baseUnit, String stockUnit,
                                       List<MaterialConversionRule> conversions) {
        for (MaterialConversionRule c : conversions) {
            if (baseUnit.equals(c.getToUnit()) && stockUnit.equals(c.getFromUnit())) {
                BigDecimal ratio = c.getToQuantity().divide(c.getFromQuantity(), 10, java.math.RoundingMode.HALF_UP);
                return baseQty.divide(ratio, 3, java.math.RoundingMode.HALF_UP);
            }
        }
        return baseQty;
    }

    // ---------- 支出报表 ----------

    @GetMapping("/expense")
    public R<Map<String, Object>> expenseReport(
            @RequestParam(required = false) String months,
            @RequestParam(required = false) String storeIds,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {

        if (!checkToken(request)) return R.fail(401, "未授权访问");

        List<String> monthList = parseList(months);
        List<String> storeIdList = parseList(storeIds);

        LambdaQueryWrapper<ExpenseRecord> w = new LambdaQueryWrapper<ExpenseRecord>();
        if (!monthList.isEmpty()) {
            for (String m : monthList) {
                LocalDate s = LocalDate.parse(m + "-01");
                w.and(wp -> wp.between(ExpenseRecord::getOccurredDate, s, s.plusMonths(1).minusDays(1)));
            }
        }
        if (!storeIdList.isEmpty()) w.in(ExpenseRecord::getStoreId, storeIdList);
        w.orderByDesc(ExpenseRecord::getOccurredDate).orderByDesc(ExpenseRecord::getId);

        List<ExpenseRecord> all = expenseRecordMapper.selectList(w);
        Map<String, Map<String, Object>> storeMap = new LinkedHashMap<>();
        for (ExpenseRecord r : all) {
            Map<String, Object> store = storeMap.computeIfAbsent(nvl(r.getStoreId()), k -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("storeId", r.getStoreId());
                s.put("storeXiaochengxuId", nvl(r.getStoreMiniappNo()));
                s.put("storeName", nvl(r.getStoreName()));
                s.put("expenses", new ArrayList<Map<String, Object>>());
                return s;
            });
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("expenseMonth", r.getOccurredDate() != null
                    ? r.getOccurredDate().getYear() + "-" + String.format("%02d", r.getOccurredDate().getMonthValue()) : "");
            item.put("firstTypeName", nvl(r.getFirstTypeName()));
            item.put("typeName", nvl(r.getTypeName()));
            item.put("itemName", nvl(r.getItemName()));
            item.put("amount", r.getAmount());
            item.put("occurredDate", r.getOccurredDate() != null ? r.getOccurredDate().toString() : "");
            item.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString().replace("T", " ") : "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> expenses = (List<Map<String, Object>>) store.get("expenses");
            expenses.add(item);
        }
        return R.ok(pageResult(new ArrayList<>(storeMap.values()), storeMap.size(), pageNum, pageSize));
    }

    // ---------- 店铺总工资 ----------

    @GetMapping("/work-hours")
    public R<Map<String, Object>> workHours(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String months,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        if (!checkToken(request)) return R.fail(401, "未授权访问");

        List<String> monthList = parseList(months);

        // 查询门店
        LambdaQueryWrapper<Store> sq = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(storeId)) sq.eq(Store::getStoreId, storeId);
        Map<String, Store> storeMap = new LinkedHashMap<>();
        for (Store s : storeMapper.selectList(sq)) storeMap.put(s.getStoreId(), s);

        // 查询工时
        LambdaQueryWrapper<StoreWorkHours> hq = new LambdaQueryWrapper<StoreWorkHours>()
                .orderByDesc(StoreWorkHours::getRecordTime).orderByDesc(StoreWorkHours::getId);
        if (StringUtils.hasText(storeId)) hq.eq(StoreWorkHours::getStoreId, storeId);
        if (!monthList.isEmpty()) hq.in(StoreWorkHours::getRecordTime, monthList);

        // 按门店分组
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (StoreWorkHours r : workHoursMapper.selectList(hq)) {
            grouped.computeIfAbsent(nvl(r.getStoreId()), k -> new ArrayList<>())
                    .add(Map.of(
                            "recordTime", nvl(r.getRecordTime()),
                            "hours", r.getHours() != null ? r.getHours() : BigDecimal.ZERO,
                            "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""));
        }

        // 组装
        List<Map<String, Object>> all = new ArrayList<>();
        for (var e : grouped.entrySet()) {
            Store s = storeMap.get(e.getKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("storeId", e.getKey());
            item.put("storeXiaochengxuId", s != null ? nvl(s.getXiaochengxuid()) : "");
            item.put("storeName", s != null ? nvl(s.getStoreName()) : "");
            item.put("details", e.getValue());
            all.add(item);
        }
        return R.ok(pageResult(all, all.size(), pageNum, pageSize));
    }

    // ---------- helpers ----------

    private boolean checkToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        String token = StringUtils.hasText(auth) ? auth.replace("Bearer ", "").trim() : "";
        return reportToken.equals(token);
    }

    private List<String> parseList(String str) {
        if (!StringUtils.hasText(str)) return List.of();
        return Arrays.stream(str.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    private String nvl(String v) { return v != null ? v : ""; }

    private Map<String, Object> pageResult(List<Map<String, Object>> records, int total, int pageNum, int pageSize) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("records", records);
        r.put("total", total);
        r.put("pageNum", pageNum);
        r.put("pageSize", pageSize);
        r.put("pages", total == 0 ? 0 : (int) Math.ceil((double) total / pageSize));
        return r;
    }
}
