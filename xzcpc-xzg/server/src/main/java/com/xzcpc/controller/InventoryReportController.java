package com.xzcpc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.response.R;
import com.xzcpc.expense.entity.SelfPurchaseMaterial;
import com.xzcpc.expense.entity.SelfPurchaseMaterialItem;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialMapper;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialItemMapper;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.entity.LossReportItem;
import com.xzcpc.mp.entity.LossReportLog;
import com.xzcpc.mp.entity.StoreWorkHours;
import com.xzcpc.mp.entity.TransferOrder;
import com.xzcpc.mp.entity.TransferOrderItem;
import com.xzcpc.mp.entity.TransferReturnRecord;
import com.xzcpc.mp.mapper.LossReportItemMapper;
import com.xzcpc.mp.mapper.LossReportLogMapper;
import com.xzcpc.mp.mapper.LossReportMapper;
import com.xzcpc.mp.mapper.StoreWorkHoursMapper;
import com.xzcpc.mp.mapper.TransferOrderItemMapper;
import com.xzcpc.mp.mapper.TransferOrderMapper;
import com.xzcpc.mp.mapper.TransferReturnRecordMapper;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialConversionRule;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialConversionRuleMapper;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
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
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialConversionRuleMapper conversionMapper;
    private final StoreWorkHoursMapper workHoursMapper;
    private final StoreMapper storeMapper;
    private final SelfPurchaseMaterialMapper selfPurchaseMaterialMapper;
    private final SelfPurchaseMaterialItemMapper selfPurchaseMaterialItemMapper;
    private final LossReportMapper lossReportMapper;
    private final LossReportLogMapper lossReportLogMapper;
    private final LossReportItemMapper lossReportItemMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final TransferOrderItemMapper transferOrderItemMapper;
    private final TransferReturnRecordMapper transferReturnRecordMapper;
    private final JdbcTemplate jdbcTemplate;

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

        // 1. 先查不重复的（门店+月份）组合总数、并分页
        // 用 JdbcTemplate 构建 SQL
        StringBuilder sqlBase = new StringBuilder(" FROM task WHERE status='submitted' AND del_flag=0 AND task_type='monthly'");
        List<Object> params = new ArrayList<>();
        if (!monthList.isEmpty()) {
            sqlBase.append(" AND task_month IN (");
            sqlBase.append(monthList.stream().map(m -> "?").collect(Collectors.joining(",")));
            sqlBase.append(")");
            params.addAll(monthList);
        }
        if (!storeIdList.isEmpty()) {
            sqlBase.append(" AND store_id IN (");
            sqlBase.append(storeIdList.stream().map(s -> "?").collect(Collectors.joining(",")));
            sqlBase.append(")");
            params.addAll(storeIdList);
        }
        int total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT store_id, task_month)" + sqlBase, Integer.class, params.toArray());
        if (total == 0) return R.ok(pageResult(List.of(), 0, pageNum, pageSize));

        // 2. 分页：查当前页的（门店+月份）组合
        String limit = " LIMIT " + ((pageNum - 1) * pageSize) + "," + pageSize;
        List<Map<String, Object>> storeKeys = jdbcTemplate.queryForList(
                "SELECT DISTINCT store_id, task_month" + sqlBase + " ORDER BY task_month DESC, store_id ASC" + limit,
                params.toArray());

        if (storeKeys.isEmpty()) return R.ok(pageResult(List.of(), total, pageNum, pageSize));

        // 3. 查这些门店+月份组合的所有 task。
        //    pageSize=100 时原实现会拼出 100 组 OR 链 (store_id=? AND task_month=?)，优化器易退化，
        //    改为 store_id IN + task_month IN 一次查回，Java 侧按组合过滤（语义等价）。
        Set<String> pageKeys = storeKeys.stream()
                .map(k -> (String) k.get("store_id") + "|" + (String) k.get("task_month"))
                .collect(Collectors.toSet());
        List<String> sidList = storeKeys.stream().map(k -> (String) k.get("store_id")).distinct().toList();
        List<String> tmList = storeKeys.stream().map(k -> (String) k.get("task_month")).distinct().toList();
        LambdaQueryWrapper<Task> taskW = new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "submitted")
                .eq(Task::getTaskType, "monthly")
                .in(Task::getStoreId, sidList)
                .in(Task::getTaskMonth, tmList);
        List<Task> tasks = taskMapper.selectList(taskW).stream()
                .filter(t -> pageKeys.contains(t.getStoreId() + "|" + t.getTaskMonth()))
                .toList();

        Map<Integer, Task> taskMap = new HashMap<>();
        for (Task t : tasks) taskMap.put(t.getId(), t);

        // 4. 查汇总数据（只取分组需要的 3 列，避免整行拉取 unit_breakdown 等字段）
        List<Integer> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskMaterialSummary> summaries = taskMaterialSummaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().in(TaskMaterialSummary::getTaskId, taskIds)
                        .select(TaskMaterialSummary::getTaskId, TaskMaterialSummary::getMaterialId,
                                TaskMaterialSummary::getAdjustedQty));

        // 5. 从任务快照取盘点单位和单价（只取需要的 3 列，unit_inputs 等大字段不拉取）
        Map<String, String> snapUnit = new HashMap<>();
        Map<String, BigDecimal> snapPrice = new HashMap<>();
        if (!taskIds.isEmpty()) {
            List<TaskZoneMaterial> zms = taskZoneMaterialMapper.selectList(
                    new LambdaQueryWrapper<TaskZoneMaterial>().in(TaskZoneMaterial::getTaskId, taskIds)
                            .select(TaskZoneMaterial::getMaterialId, TaskZoneMaterial::getBaseUnitSnapshot,
                                    TaskZoneMaterial::getUnitPriceSnapshot));
            for (TaskZoneMaterial zm : zms) {
                if (StringUtils.hasText(zm.getBaseUnitSnapshot())) snapUnit.putIfAbsent(zm.getMaterialId(), zm.getBaseUnitSnapshot());
                if (zm.getUnitPriceSnapshot() != null) snapPrice.putIfAbsent(zm.getMaterialId(), zm.getUnitPriceSnapshot());
            }
        }

        // 6. 物料详情
        List<String> matIds = summaries.stream().map(TaskMaterialSummary::getMaterialId).distinct().toList();
        Map<String, Material> matMap = loadMaterialsIncludingDeleted(matIds);

        // 7. 按门店+月份分组
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
            m.put("totalQuantity", sm.getAdjustedQty());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> materials = (List<Map<String, Object>>) store.get("materials");
            materials.add(m);
        }

        return R.ok(pageResult(new ArrayList<>(storeMap.values()), total, pageNum, pageSize));
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
        LambdaQueryWrapper<Task> taskW = new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, "submitted")
                .eq(Task::getTaskType, "monthly");
        if (!monthList.isEmpty()) taskW.in(Task::getTaskMonth, monthList);
        if (!storeIdList.isEmpty()) taskW.in(Task::getStoreId, storeIdList);
        List<Task> tasks = taskMapper.selectList(taskW);
        if (tasks.isEmpty()) return R.ok(pageResult(List.of(), 0, pageNum, pageSize));

        Map<Integer, Task> taskMap = new HashMap<>();
        for (Task t : tasks) taskMap.put(t.getId(), t);

        // 2. 查汇总（只取用到的 3 列）
        List<Integer> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskMaterialSummary> summaries = taskMaterialSummaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().in(TaskMaterialSummary::getTaskId, taskIds)
                        .select(TaskMaterialSummary::getTaskId, TaskMaterialSummary::getMaterialId,
                                TaskMaterialSummary::getTotalQty));

        // 3. 查物料
        List<String> matIds = summaries.stream().map(TaskMaterialSummary::getMaterialId).distinct().toList();
        Map<String, Material> matMap = loadMaterialsIncludingDeleted(matIds);

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
                s.put("warehouseCode", nvl(task.getWarehouseCode()));
                s.put("materials", new ArrayList<Map<String, Object>>());
                return s;
            });

            Material mat = matMap.get(sm.getMaterialId());
            MaterialInventoryRule rule = ruleMap.get(sm.getMaterialId());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialId", sm.getMaterialId());
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null ? nvl(mat.getSpec()) : "");
            m.put("qmCode", mat != null ? nvl(mat.getQmCode()) : "");
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

        // 原生 SQL 绕过 @TableLogic：软删记录也下发（delFlag 由调用方自行判断，兑现接口文档承诺）；
        // itemName 取当前有效明细的首条（明细软删重建后主表 item_name 已不维护，存量记录兜底主表值）
        StringBuilder sql = new StringBuilder(
                "SELECT e.store_id, e.store_miniapp_no, e.store_name, e.expense_id, e.first_type_name,"
                        + " e.type_name, e.item_name, e.amount, e.occurred_date, e.created_at, e.del_flag,"
                        + " (SELECT eri.item_name FROM expense_record_item eri"
                        + "   WHERE eri.expense_id = e.expense_id AND eri.del_flag = 0"
                        + "   ORDER BY eri.sort_no ASC, eri.id ASC LIMIT 1) AS first_item_name"
                        + " FROM expense_record e"
                        + " WHERE e.first_type_name != '自购成本'");
        List<Object> params = new ArrayList<>();
        if (!monthList.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < monthList.size(); i++) {
                if (i > 0) sql.append(" OR ");
                LocalDate s = LocalDate.parse(monthList.get(i) + "-01");
                sql.append("(e.occurred_date BETWEEN ? AND ?)");
                params.add(s);
                params.add(s.plusMonths(1).minusDays(1));
            }
            sql.append(")");
        }
        if (!storeIdList.isEmpty()) {
            sql.append(" AND e.store_id IN (");
            sql.append(storeIdList.stream().map(s -> "?").collect(Collectors.joining(",")));
            sql.append(")");
            params.addAll(storeIdList);
        }
        sql.append(" ORDER BY e.occurred_date DESC, e.id DESC");

        List<Map<String, Object>> all = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Map<String, Map<String, Object>> storeMap = new LinkedHashMap<>();
        for (Map<String, Object> r : all) {
            String storeId = nvl((String) r.get("store_id"));
            Map<String, Object> store = storeMap.computeIfAbsent(storeId, k -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("storeId", storeId);
                s.put("storeXiaochengxuId", nvl((String) r.get("store_miniapp_no")));
                s.put("storeName", nvl((String) r.get("store_name")));
                s.put("expenses", new ArrayList<Map<String, Object>>());
                return s;
            });
            Map<String, Object> item = new LinkedHashMap<>();
            String occurred = r.get("occurred_date") != null ? r.get("occurred_date").toString() : "";
            item.put("expenseMonth", occurred.length() >= 7 ? occurred.substring(0, 7) : "");
            item.put("firstTypeName", nvl((String) r.get("first_type_name")));
            item.put("typeName", nvl((String) r.get("type_name")));
            String firstItem = r.get("first_item_name") != null ? r.get("first_item_name").toString() : "";
            item.put("itemName", StringUtils.hasText(firstItem) ? firstItem : nvl((String) r.get("item_name")));
            item.put("expenseId", nvl((String) r.get("expense_id")));
            item.put("amount", r.get("amount"));
            item.put("occurredDate", occurred);
            String createdAt = r.get("created_at") != null ? r.get("created_at").toString() : "";
            item.put("createdAt", createdAt.length() > 19 ? createdAt.substring(0, 19) : createdAt);
            item.put("delFlag", r.get("del_flag") != null ? r.get("del_flag") : 0);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> expenses = (List<Map<String, Object>>) store.get("expenses");
            expenses.add(item);
        }
        // 分页：按门店分组切片（与 self-purchase-cost 一致）
        int total = storeMap.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return R.ok(pageResult(new ArrayList<>(storeMap.values()).subList(from, to), total, pageNum, pageSize));
    }

    // ---------- 自购成本 ----------

    @GetMapping("/self-purchase-cost")
    public R<Map<String, Object>> selfPurchaseCost(
            @RequestParam(required = false) String months,
            @RequestParam(required = false) String storeIds,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {

        if (!checkToken(request)) return R.fail(401, "未授权访问");

        List<String> monthList = parseList(months);
        List<String> storeIdList = parseList(storeIds);

        LambdaQueryWrapper<SelfPurchaseMaterial> w = new LambdaQueryWrapper<>();
        if (!monthList.isEmpty()) {
            w.in(SelfPurchaseMaterial::getPurchaseMonth, monthList);
        }
        if (!storeIdList.isEmpty()) w.in(SelfPurchaseMaterial::getStoreId, storeIdList);
        w.orderByDesc(SelfPurchaseMaterial::getPurchaseDate)
         .orderByDesc(SelfPurchaseMaterial::getId);

        List<SelfPurchaseMaterial> all = selfPurchaseMaterialMapper.selectList(w);
        List<Map<String, Object>> records = new ArrayList<>();

        // 多物料：主表铺平到明细，每物料一行
        if (!all.isEmpty()) {
            List<String> bizCodes = all.stream().map(SelfPurchaseMaterial::getBizCode)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            Map<String, List<SelfPurchaseMaterialItem>> itemsByBiz = Collections.emptyMap();
            if (!bizCodes.isEmpty()) {
                itemsByBiz = selfPurchaseMaterialItemMapper.selectList(
                                new LambdaQueryWrapper<SelfPurchaseMaterialItem>()
                                        .in(SelfPurchaseMaterialItem::getBizCode, bizCodes)
                                        .orderByAsc(SelfPurchaseMaterialItem::getSortNo))
                        .stream().collect(Collectors.groupingBy(SelfPurchaseMaterialItem::getBizCode));
            }
            for (SelfPurchaseMaterial r : all) {
                List<SelfPurchaseMaterialItem> items = itemsByBiz.getOrDefault(r.getBizCode(), Collections.emptyList());
                if (items.isEmpty()) {
                    // 迁移前无明细的兜底：直接返回主表行（业务ID = 支出业务ID + "-" + 主表自增ID，保证唯一）
                    records.add(buildSpmRow(r, r.getBizCode() + "-" + r.getId(), r.getMaterialName(),
                            r.getParentCategory(), r.getCategory(),
                            r.getMaterialId(), r.getUnit(), r.getPurchaseQty(), r.getUnitPrice(), r.getTotalAmount()));
                    continue;
                }
                for (SelfPurchaseMaterialItem it : items) {
                    // 多明细铺平一行一条：业务ID = 支出业务ID + "-" + 明细行自增ID，每行唯一（编辑后明细软删重建，行 id 随之更新）
                    records.add(buildSpmRow(r, r.getBizCode() + "-" + it.getId(), it.getMaterialName(),
                            it.getParentCategory(), it.getCategory(),
                            it.getMaterialId(), it.getUnit(), it.getPurchaseQty(), it.getUnitPrice(), it.getTotalAmount()));
                }
            }
        }

        int total = records.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return R.ok(pageResult(records.subList(from, to), total, pageNum, pageSize));
    }

    /** 自购成本报表行：主表头信息 + 物料行字段 */
    private Map<String, Object> buildSpmRow(SelfPurchaseMaterial r, String bizCode, String materialName,
                                            String parentCategory, String category, String materialId, String unit,
                                            BigDecimal purchaseQty, BigDecimal unitPrice, BigDecimal totalAmount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("storeId", nvl(r.getStoreId()));
        item.put("storeXiaochengxuId", nvl(r.getStoreMiniappNo()));
        item.put("storeName", nvl(r.getStoreName()));
        item.put("bizCode", nvl(bizCode));
        item.put("parentCategory", nvl(parentCategory));
        item.put("category", nvl(category));
        item.put("materialId", nvl(materialId));
        item.put("materialName", nvl(materialName));
        item.put("unit", nvl(unit));
        item.put("purchaseMonth", nvl(r.getPurchaseMonth()));
        item.put("purchaseDate", r.getPurchaseDate() != null ? r.getPurchaseDate().toString() : "");
        item.put("purchaseQty", purchaseQty);
        item.put("unitPrice", unitPrice);
        item.put("totalAmount", totalAmount);
        item.put("handlerName", nvl(r.getHandlerName()));
        item.put("remark", nvl(r.getRemark()));
        // 凭证为多张逗号拼接，报表下发首张（保持单张URL契约）
        item.put("voucherUrl", r.getVoucherUrl() != null && !r.getVoucherUrl().isEmpty()
                ? r.getVoucherUrl().split(",")[0].trim() : "");
        item.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString().replace("T", " ") : "");
        return item;
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
                            "recordId", nvl(r.getRecordId()),
                            "recordTime", nvl(r.getRecordTime()),
                            "hours", r.getHours() != null ? r.getHours() : BigDecimal.ZERO,
                            "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "",
                            "delFlag", r.getDelFlag() != null ? r.getDelFlag() : 0));
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
        // 分页：按门店分组切片
        int total = all.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return R.ok(pageResult(all.subList(from, to), total, pageNum, pageSize));
    }

    // ---------- 到货报损单 ----------

    /**
     * 到货报损单：查询厂家审批通过的到货报损，供外部系统拉取。
     * 入参 months / storeIds / pageNum / pageSize，鉴权同其他 reports 接口。
     */
    @GetMapping("/loss-report")
    public R<Map<String, Object>> lossReport(
            @RequestParam(required = false) String months,
            @RequestParam(required = false) String storeIds,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {

        if (!checkToken(request)) return R.fail(401, "未授权访问");

        List<String> monthList = parseList(months);
        List<String> storeIdList = parseList(storeIds);

        // 只查到货报损 + 厂家审批通过及之后的状态
        LambdaQueryWrapper<LossReport> w = new LambdaQueryWrapper<LossReport>()
                .eq(LossReport::getLossType, "arrival")
                .in(LossReport::getStatus, "registered", "confirmed_resend", "received", "not_received");
        if (!monthList.isEmpty()) {
            w.and(wp -> {
                for (int i = 0; i < monthList.size(); i++) {
                    LocalDate start = LocalDate.parse(monthList.get(i) + "-01");
                    LocalDate end = start.plusMonths(1).minusDays(1);
                    if (i == 0) wp.between(LossReport::getOccurredDate, start, end);
                    else wp.or().between(LossReport::getOccurredDate, start, end);
                }
            });
        }
        if (!storeIdList.isEmpty()) w.in(LossReport::getStoreId, storeIdList);
        w.orderByDesc(LossReport::getOccurredDate).orderByDesc(LossReport::getId);

        List<LossReport> reports = lossReportMapper.selectList(w);

        // 批量加载关联数据
        Set<String> sids = reports.stream().map(LossReport::getStoreId).filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, Store> storeMap = sids.isEmpty() ? Map.of()
                : storeMapper.selectList(new LambdaQueryWrapper<Store>().in(Store::getStoreId, sids))
                .stream().collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));

        Set<String> matIds = reports.stream().map(LossReport::getMaterialId).filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, Material> matMap = loadMaterialsIncludingDeleted(matIds);

        Map<String, MaterialInventoryRule> ruleMap = matIds.isEmpty() ? Map.of()
                : ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>().in(MaterialInventoryRule::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));

        // 批量查补发日期（loss_report_log action='receive'）
        List<Long> reportIds = reports.stream().map(LossReport::getId).toList();
        Map<Long, String> resendDateMap = new HashMap<>();
        if (!reportIds.isEmpty()) {
            lossReportLogMapper.selectList(
                    new LambdaQueryWrapper<LossReportLog>()
                            .in(LossReportLog::getReportId, reportIds)
                            .eq(LossReportLog::getAction, "receive"))
                    .forEach(log -> resendDateMap.put(log.getReportId(),
                            log.getCreatedAt() != null ? log.getCreatedAt().toString().replace("T", " ") : ""));
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (LossReport r : reports) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bizCode", nvl(r.getBizCode()));

            Store store = storeMap.get(r.getStoreId());
            item.put("cangkuid", store != null ? nvl(store.getCangkuid()) : "");
            item.put("xiaochengxuid", store != null ? nvl(store.getXiaochengxuid()) : "");
            item.put("storeName", nvl(r.getStoreName()));

            Material mat = matMap.get(r.getMaterialId());
            item.put("parentCategory", mat != null ? nvl(mat.getParentCategory()) : "");
            item.put("category", mat != null ? nvl(mat.getCategory()) : "");
            item.put("qmCode", mat != null ? nvl(mat.getQmCode()) : "");
            item.put("materialName", nvl(r.getMaterialName()));

            item.put("baseQty", r.getBaseQty());
            item.put("baseUnit", nvl(r.getBaseUnit()));

            // 总金额 = input_qty × unit_price
            MaterialInventoryRule rule = ruleMap.get(r.getMaterialId());
            BigDecimal unitPrice = (rule != null && rule.getUnitPrice() != null) ? rule.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal inputQty = r.getInputQty() != null ? r.getInputQty() : BigDecimal.ZERO;
            item.put("totalAmount", inputQty.multiply(unitPrice));

            // 报损状态: 1=厂家审批通过（查询已过滤，全部为1）
            item.put("status", 1);
            item.put("occurredDate", r.getOccurredDate() != null ? r.getOccurredDate().toString() : "");

            // 补发状态: 0=待补发, 1=已补发
            boolean isReceived = "received".equals(r.getStatus());
            item.put("resendStatus", isReceived ? 1 : 0);
            item.put("resendDate", resendDateMap.getOrDefault(r.getId(), ""));

            records.add(item);
        }

        return R.ok(pageResult(records, records.size(), pageNum, pageSize));
    }

    // ---------- 日常报损单 ----------

    /**
     * 日常报损单：查询日常报损（多物料），供外部系统拉取。
     * 主体信息在 loss_report，物料明细在 loss_report_item。
     * 入参 months / storeIds / pageNum / pageSize，鉴权同其他 reports 接口。
     */
    @GetMapping("/daily-loss")
    public R<Map<String, Object>> dailyLoss(
            @RequestParam(required = false) String months,
            @RequestParam(required = false) String dates,
            @RequestParam(required = false) String storeIds,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {

        if (!checkToken(request)) return R.fail(401, "未授权访问");

        List<String> monthList = parseList(months);
        List<String> dateList = parseList(dates);
        List<String> storeIdList = parseList(storeIds);

        LambdaQueryWrapper<LossReport> w = new LambdaQueryWrapper<LossReport>()
                .eq(LossReport::getLossType, "daily")
                .eq(LossReport::getStatus, "completed");
        if (!dateList.isEmpty()) {
            List<LocalDate> days = dateList.stream().map(LocalDate::parse).toList();
            w.in(LossReport::getOccurredDate, days);
        } else if (!monthList.isEmpty()) {
            w.and(wp -> {
                for (int i = 0; i < monthList.size(); i++) {
                    LocalDate start = LocalDate.parse(monthList.get(i) + "-01");
                    LocalDate end = start.plusMonths(1).minusDays(1);
                    if (i == 0) wp.between(LossReport::getOccurredDate, start, end);
                    else wp.or().between(LossReport::getOccurredDate, start, end);
                }
            });
        }
        if (!storeIdList.isEmpty()) w.in(LossReport::getStoreId, storeIdList);
        w.orderByDesc(LossReport::getOccurredDate).orderByDesc(LossReport::getId);

        List<LossReport> reports = lossReportMapper.selectList(w);
        if (reports.isEmpty()) return R.ok(pageResult(List.of(), 0, pageNum, pageSize));

        Map<Long, LossReport> reportMap = reports.stream()
                .collect(Collectors.toMap(LossReport::getId, r -> r, (a, b) -> a));

        // 批量加载 loss_report_item
        List<Long> reportIds = reports.stream().map(LossReport::getId).toList();
        List<LossReportItem> items = lossReportItemMapper.selectList(
                new LambdaQueryWrapper<LossReportItem>().in(LossReportItem::getReportId, reportIds)
                        .orderByDesc(LossReportItem::getReportId)
                        .orderByAsc(LossReportItem::getSortNo)
                        .orderByAsc(LossReportItem::getId));

        // 批量加载 stores
        Set<String> sids = reports.stream().map(LossReport::getStoreId).filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, Store> storeMap = sids.isEmpty() ? Map.of()
                : storeMapper.selectList(new LambdaQueryWrapper<Store>().in(Store::getStoreId, sids))
                .stream().collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));

        // 批量加载 materials
        Set<String> matIds = items.stream().map(LossReportItem::getMaterialId).filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, Material> matMap = loadMaterialsIncludingDeleted(matIds);

        List<Map<String, Object>> records = new ArrayList<>();
        for (LossReportItem item : items) {
            LossReport r = reportMap.get(item.getReportId());
            if (r == null) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bizCode", nvl(r.getBizCode()));

            Store store = storeMap.get(r.getStoreId());
            row.put("cangkuid", store != null ? nvl(store.getCangkuid()) : "");
            row.put("xiaochengxuid", store != null ? nvl(store.getXiaochengxuid()) : "");
            row.put("storeName", nvl(r.getStoreName()));
            row.put("lossType", "日常报损");

            Material mat = matMap.get(item.getMaterialId());
            row.put("parentCategory", mat != null ? nvl(mat.getParentCategory()) : "");
            row.put("category", mat != null ? nvl(mat.getCategory()) : "");
            row.put("qmCode", mat != null ? nvl(mat.getQmCode()) : "");
            row.put("materialName", nvl(item.getMaterialName()));

            row.put("inputQty", item.getInputQty());
            row.put("inputUnit", nvl(item.getInputUnit()));
            row.put("unitPrice", scale4(item.getUnitPrice()));
            row.put("totalAmount", scale4(item.getTotalAmount()));
            row.put("status", toStatusCn(r.getStatus()));
            row.put("reason", nvl(r.getReason()));
            row.put("occurredDate", r.getOccurredDate() != null ? r.getOccurredDate().toString() : "");
            row.put("handlerName", nvl(r.getHandlerName()));
            row.put("createdAt", item.getCreatedAt() != null ? item.getCreatedAt().toString().replace("T", " ") : "");
            row.put("updatedAt", item.getUpdatedAt() != null ? item.getUpdatedAt().toString().replace("T", " ") : "");

            records.add(row);
        }

        // 分页：按物料明细行切片
        int total = records.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return R.ok(pageResult(records.subList(from, to), total, pageNum, pageSize));
    }

    private static String toStatusCn(String status) {
        if (status == null) return "";
        return switch (status) {
            case "pending_approval"   -> "待店长审批";
            case "completed"          -> "已录入";
            default                   -> status;
        };
    }

    // ---------- 调货单 ----------

    /**
     * 调货单：查询已完成的调货单，按物料、调出/调入门店拆分为单条记录。
     * 每个 transfer_order_item 拆成 2 行（调出_0 / 调入_1）。
     * 入参 months / pageNum / pageSize，鉴权同其他 reports 接口。
     */
    @GetMapping("/transfer-order")
    public R<Map<String, Object>> transferOrder(
            @RequestParam(required = false) String months,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {

        if (!checkToken(request)) return R.fail(401, "未授权访问");

        List<String> monthList = parseList(months);

        LambdaQueryWrapper<TransferOrder> w = new LambdaQueryWrapper<TransferOrder>()
                .eq(TransferOrder::getStatus, "completed");
        if (!monthList.isEmpty()) {
            w.and(wp -> {
                for (int i = 0; i < monthList.size(); i++) {
                    if (i == 0) wp.apply("DATE_FORMAT(completed_at,'%Y-%m') = {0}", monthList.get(i));
                    else wp.or().apply("DATE_FORMAT(completed_at,'%Y-%m') = {0}", monthList.get(i));
                }
            });
        }
        w.orderByDesc(TransferOrder::getCompletedAt);
        List<TransferOrder> orders = transferOrderMapper.selectList(w);

        if (orders.isEmpty()) return R.ok(pageResult(List.of(), 0, pageNum, pageSize));

        // 批量加载 items
        List<Long> transferIds = orders.stream().map(TransferOrder::getId).toList();
        Map<Long, TransferOrder> orderMap = orders.stream().collect(Collectors.toMap(TransferOrder::getId, o -> o, (a, b) -> a));
        List<TransferOrderItem> allItems = transferOrderItemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>().in(TransferOrderItem::getTransferId, transferIds));

        // 批量加载 stores
        Set<String> allStoreIds = new HashSet<>();
        for (TransferOrder o : orders) {
            if (StringUtils.hasText(o.getFromStoreId())) allStoreIds.add(o.getFromStoreId());
            if (StringUtils.hasText(o.getToStoreId())) allStoreIds.add(o.getToStoreId());
        }
        Map<String, Store> storeMap = allStoreIds.isEmpty() ? Map.of()
                : storeMapper.selectList(new LambdaQueryWrapper<Store>().in(Store::getStoreId, allStoreIds))
                .stream().collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));

        // 批量加载 materials
        Set<String> matIds = allItems.stream().map(TransferOrderItem::getMaterialId).filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, Material> matMap = loadMaterialsIncludingDeleted(matIds);

        // 批量加载归还记录（按 transferId，取每个 item 最晚的 created_at）
        List<Long> itemIds = allItems.stream().map(TransferOrderItem::getId).toList();
        Map<Long, TransferReturnRecord> returnMap = new HashMap<>(); // itemId → latest return record
        if (!itemIds.isEmpty()) {
            List<TransferReturnRecord> returns = transferReturnRecordMapper.selectList(
                    new LambdaQueryWrapper<TransferReturnRecord>().in(TransferReturnRecord::getItemId, itemIds));
            for (TransferReturnRecord rr : returns) {
                TransferReturnRecord existing = returnMap.get(rr.getItemId());
                if (existing == null || rr.getCreatedAt().isAfter(existing.getCreatedAt())) {
                    returnMap.put(rr.getItemId(), rr);
                }
            }
        }

        // 拆分：每个 item → 2 行
        List<Map<String, Object>> records = new ArrayList<>();
        for (TransferOrderItem item : allItems) {
            TransferOrder order = orderMap.get(item.getTransferId());
            if (order == null) continue;

            // ---- 调出行 _0 ----
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("transferItemNo", nvl(order.getBizCode()) + "_" + item.getId() + "_0");
            out.put("transferStatus", 0);
            Store fromStore = storeMap.get(order.getFromStoreId());
            out.put("cangkuid", fromStore != null ? nvl(fromStore.getCangkuid()) : "");
            out.put("storeName", nvl(order.getFromStoreName()));
            fillTransferItemFields(out, item, matMap);
            out.put("completedAt", order.getCompletedAt() != null ? order.getCompletedAt().toString().replace("T", " ") : "");
            fillReturnInfo(out, item, returnMap);
            records.add(out);

            // ---- 调入行 _1 ----
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("transferItemNo", nvl(order.getBizCode()) + "_" + item.getId() + "_1");
            in.put("transferStatus", 1);
            Store toStore = storeMap.get(order.getToStoreId());
            in.put("cangkuid", toStore != null ? nvl(toStore.getCangkuid()) : "");
            in.put("storeName", nvl(order.getToStoreName()));
            fillTransferItemFields(in, item, matMap);
            in.put("completedAt", order.getCompletedAt() != null ? order.getCompletedAt().toString().replace("T", " ") : "");
            fillReturnInfo(in, item, returnMap);
            records.add(in);
        }

        return R.ok(pageResult(records, records.size(), pageNum, pageSize));
    }

    private void fillTransferItemFields(Map<String, Object> row, TransferOrderItem item, Map<String, Material> matMap) {
        Material mat = matMap.get(item.getMaterialId());
        row.put("parentCategory", mat != null ? nvl(mat.getParentCategory()) : "");
        row.put("category", mat != null ? nvl(mat.getCategory()) : "");
        row.put("qmCode", mat != null ? nvl(mat.getQmCode()) : "");
        row.put("materialName", nvl(item.getMaterialName()));
        BigDecimal qty = item.getInputQty() != null ? item.getInputQty() : BigDecimal.ZERO;
        BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        row.put("totalAmount", qty.multiply(price));
    }

    private void fillReturnInfo(Map<String, Object> row, TransferOrderItem item, Map<Long, TransferReturnRecord> returnMap) {
        TransferReturnRecord rr = returnMap.get(item.getId());
        row.put("isReturned", rr != null ? 1 : 0);
        row.put("returnDate", rr != null && rr.getCreatedAt() != null
                ? rr.getCreatedAt().toString().replace("T", " ") : "");
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

    private BigDecimal scale4(BigDecimal val) {
        return val != null ? val.setScale(4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
    }

    /**
     * 按 material_id 批量加载物料，包含 del_flag=2（彻底删除）的行。
     * 报表接口要展示历史单据的物料分类/企迈编码，物料被删除后仍可能被历史单据引用，
     * 走 jdbcTemplate 绕过 MyBatis-Plus 逻辑删除过滤。
     */
    private Map<String, Material> loadMaterialsIncludingDeleted(Collection<String> matIds) {
        Set<String> ids = matIds.stream().filter(StringUtils::hasText).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        String sql = "SELECT * FROM material WHERE material_id IN ("
                + ids.stream().map(m -> "?").collect(Collectors.joining(",")) + ")";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Material.class), ids.toArray())
                .stream().collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));
    }

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
