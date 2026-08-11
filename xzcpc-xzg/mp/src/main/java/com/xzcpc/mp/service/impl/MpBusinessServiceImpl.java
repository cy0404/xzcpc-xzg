package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.entity.SelfPurchaseMaterial;
import com.xzcpc.expense.mapper.ExpenseRecordMapper;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialMapper;
import com.xzcpc.mp.dto.BusinessOverviewResp;
import com.xzcpc.mp.dto.BusinessOverviewResp.KpiItem;
import com.xzcpc.mp.dto.BusinessTrendResp;
import com.xzcpc.mp.dto.BusinessTrendResp.DailyPoint;
import com.xzcpc.mp.dto.BusinessReportSummaryResp;
import com.xzcpc.mp.dto.BusinessReportDetailResp;
import com.xzcpc.mp.dto.BusinessReportDetailResp.Indicators;
import com.xzcpc.mp.dto.BusinessReportDetailResp.DetailItem;
import com.xzcpc.mp.dto.BusinessReportDetailResp.CostItem;
import com.xzcpc.mp.dto.BusinessReportDetailResp.DailyItem;
import com.xzcpc.mp.dto.BusinessReportDetailResp.ChannelItem;
import com.xzcpc.mp.dto.HomeOverviewResp;
import com.xzcpc.mp.entity.ReportVisibilityConfig;
import com.xzcpc.mp.mapper.ReportVisibilityConfigMapper;
import com.xzcpc.mp.service.MpBusinessService;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.mp.service.OpenApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MpBusinessServiceImpl implements MpBusinessService {

    private final JdbcTemplate pgJdbcTemplate;
    private final JdbcTemplate mysqlJdbcTemplate;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final SelfPurchaseMaterialMapper spmMapper;
    private final MpStaffService staffService;
    private final ReportVisibilityConfigMapper visibilityMapper;
    private final OpenApiClient openApiClient;

    /** 成本类别 → OpenAPI group 参数映射 */
    private static final Map<String, String> COST_GROUP_MAP = Map.of(
        "食材成本", "food",
        "自购食材成本", "food",
        "包材成本", "food",
        "营运成本", "operation",
        "人工成本", "labor",
        "能耗成本", "energy",
        "租金成本", "rent"
    );

    public MpBusinessServiceImpl(
            @Qualifier("pgJdbcTemplate") JdbcTemplate pgJdbcTemplate,
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate mysqlJdbcTemplate,
            ExpenseRecordMapper expenseRecordMapper,
            SelfPurchaseMaterialMapper spmMapper,
            MpStaffService staffService,
            ReportVisibilityConfigMapper visibilityMapper,
            OpenApiClient openApiClient) {
        this.pgJdbcTemplate = pgJdbcTemplate;
        this.mysqlJdbcTemplate = mysqlJdbcTemplate;
        this.expenseRecordMapper = expenseRecordMapper;
        this.spmMapper = spmMapper;
        this.staffService = staffService;
        this.visibilityMapper = visibilityMapper;
        this.openApiClient = openApiClient;
    }

    // ---------- overview ----------

    @Override
    public BusinessOverviewResp getOverview(List<String> storeIds, String period) {
        PeriodRange cur = resolvePeriod(period, 0);
        PeriodRange prev = resolvePeriod(period, 1);

        log.info("getOverview: storeIds={}, period={}, cur={}~{}, prev={}~{}",
                storeIds, period, cur.start, cur.end, prev.start, prev.end);

        BusinessOverviewResp resp = new BusinessOverviewResp();
        BigDecimal salesCur = queryPgSum("ads.store_day_metrics", "original_amount", storeIds, "period_start", cur);
        BigDecimal salesPrev = queryPgSum("ads.store_day_metrics", "original_amount", storeIds, "period_start", prev);
        log.info("sales: cur={}, prev={}", salesCur, salesPrev);
        resp.setSales(buildKpi(salesCur, salesPrev));

        BigDecimal expenseCur = queryExpenseSum(storeIds, cur);
        BigDecimal expensePrev = queryExpenseSum(storeIds, prev);
        log.info("expense: cur={}, prev={}", expenseCur, expensePrev);
        resp.setExpense(buildKpi(expenseCur, expensePrev));

        BigDecimal lossCur = queryDailyLossSum(storeIds, cur);
        BigDecimal lossPrev = queryDailyLossSum(storeIds, prev);
        log.info("loss: cur={}, prev={}", lossCur, lossPrev);
        resp.setLoss(buildKpi(lossCur, lossPrev));
        return resp;
    }

    // ---------- trend ----------

    @Override
    public BusinessTrendResp getTrend(List<String> storeIds, String period, String metric) {
        PeriodRange cur = resolvePeriod(period, 0);
        List<DailyPoint> points = new ArrayList<>();

        // 批量查询每日数据，避免 N+1
        Map<LocalDate, BigDecimal> dayMap = queryDailyBatch(metric, storeIds, cur);

        LocalDate d = cur.start;
        while (!d.isAfter(cur.end)) {
            DailyPoint point = new DailyPoint();
            point.setDate(String.format("%d/%d", d.getMonthValue(), d.getDayOfMonth()));
            point.setValue(dayMap.getOrDefault(d, BigDecimal.ZERO));
            points.add(point);
            d = d.plusDays(1);
        }

        BusinessTrendResp resp = new BusinessTrendResp();
        resp.setMetric(metric);
        resp.setPoints(points);
        resp.setCaption(buildCaption(metric, period));
        return resp;
    }

    // ---------- reports ----------

    @Override
    public List<BusinessReportSummaryResp> getReports(List<String> storeIds) {
        if (CollectionUtils.isEmpty(storeIds)) return Collections.emptyList();

        String inClause = storeIds.stream().map(s -> "'" + s.replace("'", "''") + "'")
                .collect(Collectors.joining(","));
        String sql = "SELECT to_char(sm.stat_month, 'YYYY-MM') AS year_month, "
                   + "ds.record_id AS store_id, sm.store_name "
                   + "FROM ads.store_operations_summary sm "
                   + "JOIN dim.store ds ON ds.store_uuid::text = sm.store_id "
                   + "WHERE ds.record_id IN (" + inClause + ") "
                   + "ORDER BY sm.stat_month DESC, ds.record_id";
        try {
            List<Map<String, Object>> rows = pgJdbcTemplate.queryForList(sql);
            // 过滤配置表中存在的财报（有记录=隐藏）
            Set<String> hidden = visibilityMapper.selectList(null).stream()
                .map(c -> c.getStoreId() + "|" + c.getStatMonth())
                .collect(Collectors.toSet());
            return rows.stream()
                .filter(row -> !hidden.contains(row.get("store_id") + "|" + row.get("year_month")))
                .map(row -> {
                    BusinessReportSummaryResp r = new BusinessReportSummaryResp();
                    r.setYearMonth((String) row.get("year_month"));
                    r.setStoreId((String) row.get("store_id"));
                    r.setStoreName((String) row.get("store_name"));
                    return r;
                }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询财报列表失败: {}", e.toString());
            return Collections.emptyList();
        }
    }

    // ---------- report detail ----------

    @Override
    public BusinessReportDetailResp getReportDetail(String storeId, String yearMonth) {
        // 1. 尝试通过 OpenAPI 获取完整数据
        String[] uuidName = resolveStoreUuid(storeId);
        if (uuidName != null) {
            BusinessReportDetailResp openApiResult = buildFromOpenApi(storeId, uuidName[0], uuidName[1], yearMonth);
            if (openApiResult != null) {
                return openApiResult;
            }
        }
        // 2. Fallback：使用现有 PG 查询
        log.info("OpenAPI 未返回数据，fallback 到 PG 查询: storeId={}, yearMonth={}", storeId, yearMonth);
        return buildFromPg(storeId, yearMonth);
    }

    /**
     * 从 OpenAPI 组装财报详情
     */
    private BusinessReportDetailResp buildFromOpenApi(String storeId, String uuid, String storeName, String yearMonth) {
        String statMonth = yearMonth + "-01";
        java.time.LocalDate ym = java.time.LocalDate.parse(yearMonth + "-01");
        String startDate = statMonth;
        String endDate = ym.withDayOfMonth(ym.lengthOfMonth()).toString();

        // 并行调用 3 个 OpenAPI 接口
        java.util.concurrent.CompletableFuture<Map<String, Object>> detailFuture =
            java.util.concurrent.CompletableFuture.supplyAsync(() ->
                openApiClient.getStoreDetail(uuid, statMonth, storeName));
        java.util.concurrent.CompletableFuture<Map<String, Object>> costFuture =
            java.util.concurrent.CompletableFuture.supplyAsync(() ->
                openApiClient.getStoreCost(uuid, statMonth));
        java.util.concurrent.CompletableFuture<List<Map<String, Object>>> dailyFuture =
            java.util.concurrent.CompletableFuture.supplyAsync(() ->
                openApiClient.getDaily(uuid, startDate, endDate));

        Map<String, Object> detail = detailFuture.join();
        if (detail == null) return null;

        BusinessReportDetailResp resp = new BusinessReportDetailResp();
        resp.setYearMonth(yearMonth);
        resp.setStoreName((String) detail.getOrDefault("storeName", ""));
        resp.setStoreId(storeId);

        // --- 提取成本明细数据 ---
        BigDecimal foodCost = BigDecimal.ZERO;       // 食材成本
        BigDecimal selfPurchaseCost = BigDecimal.ZERO; // 自购食材成本
        BigDecimal packingCost = BigDecimal.ZERO;    // 包材成本
        BigDecimal rentCost = BigDecimal.ZERO;       // 租金成本

        Map<String, Object> costData = costFuture.join();
        if (costData != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) costData.get("items");
            if (items != null) {
                for (Map<String, Object> item : items) {
                    String category = (String) item.get("category");
                    BigDecimal amount = toDecimal(item.get("amount"));
                    switch (category) {
                        case "食材成本": foodCost = amount; break;
                        case "自购食材成本": selfPurchaseCost = amount; break;
                        case "包材成本": packingCost = amount; break;
                        case "租金成本": rentCost = amount; break;
                    }
                }
            }
        }

        // --- 月度经营指标 ---
        Indicators ind = new Indicators();
        BigDecimal sales = toDecimal(detail.get("gmv"));
        BigDecimal actualRevenue = toDecimal(detail.get("actualRevenue"));
        BigDecimal theoryMaterialCost = toDecimal(detail.get("theoryMaterialCost"));
        BigDecimal productGrossProfitRate = toDecimal(detail.get("productGrossProfitRate"));
        BigDecimal lossRate = toDecimal(detail.get("lossRate"));

        // 实际物料成本 = 食材 + 自购食材 + 包材（从 store-cost 明细求和）
        BigDecimal actualMaterialCost = foodCost.add(selfPurchaseCost).add(packingCost);
        // 产品毛利 = 营业收入 − 实际物料成本
        BigDecimal grossProfit = actualRevenue.subtract(actualMaterialCost);
        // 毛利率
        BigDecimal grossProfitRate = BigDecimal.ZERO;
        if (actualRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossProfitRate = grossProfit.divide(actualRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        }
        // 现金流 = 现金流率 × 营业收入（API 直接取值）
        BigDecimal cashFlowRate = toDecimal(detail.get("cashFlowRate"));
        BigDecimal cashFlow = cashFlowRate.multiply(actualRevenue);
        // 净利润 = 现金流 − 租金（API 无直接字段，需计算）
        BigDecimal netProfit = cashFlow.subtract(rentCost);
        // 净利率
        BigDecimal netProfitRate = BigDecimal.ZERO;
        if (actualRevenue.compareTo(BigDecimal.ZERO) > 0) {
            netProfitRate = netProfit.divide(actualRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        }

        ind.setSales(sales);
        ind.setActualRevenue(actualRevenue);
        ind.setGrossProfit(grossProfit);
        ind.setGrossProfitRate(grossProfitRate);
        ind.setTheoryMaterialCost(theoryMaterialCost);
        ind.setActualMaterialCost(actualMaterialCost);
        ind.setBookingRate(toDecimal(detail.get("bookingRate")));
        ind.setProductGrossProfitRate(productGrossProfitRate);
        ind.setCashFlowRate(toDecimal(detail.get("cashFlowRate")));
        ind.setLossRate(lossRate);
        ind.setMaterialCostRatio(toDecimal(detail.get("materialCostRatio")));
        ind.setRentRatio(toDecimal(detail.get("rentRatio")));
        ind.setLaborRatio(toDecimal(detail.get("laborRatio")));
        ind.setCashFlow(cashFlow);
        ind.setNetProfit(netProfit);
        ind.setNetProfitRate(netProfitRate);
        resp.setIndicators(ind);

        // --- 财报明细行（含计算公式） ---
        List<DetailItem> details = new ArrayList<>();
        details.add(makeDetail("营业额", ind.getSales(), "default", "门店原价销售额(GMV)"));
        details.add(makeDetail("营业收入", ind.getActualRevenue(), "default", "门店折后实收金额"));
        details.add(makeDetail("产品毛利", ind.getGrossProfit(), "primary", "营业收入 − 实际物料成本"));
        details.add(makeDetail("产品毛利率", ind.getProductGrossProfitRate(), "primary", "产品毛利 ÷ 营业收入"));
        details.add(makeDetail("理论物料成本", ind.getTheoryMaterialCost(), "default", "按出品配方推算的标准物料成本"));
        details.add(makeDetail("实际物料成本", ind.getActualMaterialCost(), "default", "食材成本 + 自购食材成本 + 包材成本\n（期初+采购−期末+支出）"));
        details.add(makeDetail("现金流", ind.getCashFlow(), "default", "营业收入 − 物料成本 − 人工成本 − 营运成本 − 能耗成本\n（不含租金）"));
        details.add(makeDetail("损耗率", ind.getLossRate(), "danger", "(实际物料成本 − 理论物料成本) ÷ 营业额"));
        details.add(makeDetail("净利润", ind.getNetProfit(), ind.getNetProfit().compareTo(BigDecimal.ZERO) >= 0 ? "primary" : "danger", "现金流 − 租金成本"));
        details.add(makeDetail("净利率", ind.getNetProfitRate(), ind.getNetProfitRate().compareTo(BigDecimal.ZERO) >= 0 ? "primary" : "danger", "净利润 ÷ 营业收入"));
        resp.setDetails(details);

        // --- 日营收 ---
        List<Map<String, Object>> dailyList = dailyFuture.join();
        if (dailyList != null) {
            List<DailyItem> dailyItems = new ArrayList<>();
            for (Map<String, Object> d : dailyList) {
                DailyItem di = new DailyItem();
                di.setStatDate((String) d.get("statDate"));
                di.setGmv(toDecimal(d.get("gmv")));
                di.setActualRevenue(toDecimal(d.get("actualRevenue")));
                di.setOrderCount(toInteger(d.get("orderCount")));
                di.setDiscountAmount(toDecimal(d.get("discountAmount")));
                di.setRefundAmount(toDecimal(d.get("refundAmount")));
                di.setAvgOrderValue(toDecimal(d.get("avgOrderValue")));
                di.setBookingRate(toDecimal(d.get("bookingRate")));
                // 渠道拆分
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> channels = (List<Map<String, Object>>) d.get("channels");
                if (channels != null) {
                    List<ChannelItem> channelItems = new ArrayList<>();
                    for (Map<String, Object> ch : channels) {
                        ChannelItem ci = new ChannelItem();
                        ci.setChannel((String) ch.get("channel"));
                        ci.setGmv(toDecimal(ch.get("gmv")));
                        ci.setActualRevenue(toDecimal(ch.get("actualRevenue")));
                        ci.setOrderCount(toInteger(ch.get("orderCount")));
                        channelItems.add(ci);
                    }
                    di.setChannels(channelItems);
                }
                dailyItems.add(di);
            }
            resp.setDailyItems(dailyItems);
        }

        // --- 成本结构（复用上方已获取的 costData）---
        if (costData != null) {
            List<CostItem> costs = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) costData.get("items");
            if (items != null) {
                for (Map<String, Object> item : items) {
                    CostItem ci = new CostItem();
                    String category = (String) item.get("category");
                    ci.setName(category);
                    ci.setAmount(toDecimal(item.get("amount")));
                    ci.setRatio(toDecimal(item.get("ratio")).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
                    ci.setGroup(COST_GROUP_MAP.getOrDefault(category, "operation"));
                    costs.add(ci);
                }
            }
            resp.setCostStructure(costs);
        }

        return resp;
    }

    /**
     * Fallback：从 PG 组装财报详情（保留原有逻辑）
     */
    private BusinessReportDetailResp buildFromPg(String storeId, String yearMonth) {
        String sql = "SELECT store_name, gmv, actual_revenue, "
                   + "product_gross_profit, product_gross_profit_rate, "
                   + "food_cost, packing_cost, labor_cost, "
                   + "operation_cost, energy_cost, rent_cost "
                   + "FROM ads.store_operations_summary "
                   + "WHERE store_id IN (SELECT store_uuid::text FROM dim.store WHERE record_id = ?) "
                   + "AND to_char(stat_month, 'YYYY-MM') = ?";
        try {
            List<Map<String, Object>> rows = pgJdbcTemplate.queryForList(sql, storeId, yearMonth);
            if (rows.isEmpty()) return null;

            Map<String, Object> row = rows.get(0);
            BusinessReportDetailResp resp = new BusinessReportDetailResp();
            resp.setYearMonth(yearMonth);
            resp.setStoreName((String) row.get("store_name"));
            resp.setStoreId(storeId);

            Indicators ind = new Indicators();
            ind.setSales(toDecimal(row.get("gmv")));
            ind.setActualRevenue(toDecimal(row.get("actual_revenue")));
            ind.setGrossProfit(toDecimal(row.get("product_gross_profit")));
            ind.setGrossProfitRate(toDecimal(row.get("product_gross_profit_rate")));
            resp.setIndicators(ind);

            BigDecimal gmv = ind.getSales() != null ? ind.getSales() : BigDecimal.ZERO;

            List<DetailItem> details = new ArrayList<>();
            details.add(makeDetail("销售额", ind.getSales(), "default", "门店原价销售额(GMV)"));
            details.add(makeDetail("营业实收", ind.getActualRevenue(), "default", "门店折后实收金额"));
            details.add(makeDetail("毛利", ind.getGrossProfit(), "primary", ""));
            details.add(makeDetail("毛利率", ind.getGrossProfitRate(), "primary", ""));
            resp.setDetails(details);

            List<CostItem> costs = new ArrayList<>();
            addCostPg(costs, "食材成本", row.get("food_cost"), gmv, "food");
            addCostPg(costs, "包材成本", row.get("packing_cost"), gmv, "food");
            addCostPg(costs, "人工成本", row.get("labor_cost"), gmv, "labor");
            addCostPg(costs, "营运成本", row.get("operation_cost"), gmv, "operation");
            addCostPg(costs, "能耗成本", row.get("energy_cost"), gmv, "energy");
            addCostPg(costs, "租金成本", row.get("rent_cost"), gmv, "rent");
            resp.setCostStructure(costs);

            return resp;
        } catch (Exception e) {
            log.warn("查询财报详情失败: storeId={}, yearMonth={}, error={}", storeId, yearMonth, e.toString());
            return null;
        }
    }

    @Override
    public Map<String, Object> getCostBreakdown(String storeId, String yearMonth, String group, String category) {
        String[] uuidName = resolveStoreUuid(storeId);
        if (uuidName == null) {
            log.warn("成本下钻：无法解析 UUID: storeId={}", storeId);
            return null;
        }
        return openApiClient.getCostBreakdown(uuidName[0], yearMonth + "-01", group, category);
    }

    @Override
    public HomeOverviewResp getYesterday(List<String> storeIds) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        PeriodRange r = new PeriodRange(yesterday, yesterday);
        HomeOverviewResp resp = new HomeOverviewResp();
        resp.setYesterdaySales(queryPgSum("ads.store_day_metrics", "original_amount", storeIds, "period_start", r));
        resp.setYesterdayExpense(queryExpenseSum(storeIds, r));
        return resp;
    }

    // ==================== helper methods ====================

    /**
     * 根据 openid 获取所有关联门店ID列表（与其他模块一致，查 employee 表）
     */
    public List<String> getOwnerStoreIds(String openid) {
        return staffService.findStoresByOpenid(openid).stream()
                .map(m -> (String) m.get("storeId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 通过 store_info.store_id（= dim.store.record_id）查询 PG dim.store 的 UUID 及门店名
     * @return [uuid, storeName] 或 null
     */
    private String[] resolveStoreUuid(String storeId) {
        try {
            Map<String, Object> row = pgJdbcTemplate.queryForMap(
                "SELECT store_uuid::text, store_name FROM dim.store WHERE record_id = ? LIMIT 1", storeId);
            return new String[] { (String) row.get("store_uuid"), (String) row.get("store_name") };
        } catch (Exception e) {
            log.warn("解析 store UUID 失败: storeId={}, error={}", storeId, e.toString());
            return null;
        }
    }

    private KpiItem buildKpi(BigDecimal current, BigDecimal previous) {
        KpiItem item = new KpiItem();
        item.setValue(current != null ? current : BigDecimal.ZERO);
        item.setChange(BigDecimal.ZERO);
        if (current != null && previous != null && previous.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = current.subtract(previous);
            item.setChange(diff);
            BigDecimal rate = diff.divide(previous, 3, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            boolean negative = rate.compareTo(BigDecimal.ZERO) < 0;
            item.setChangeRate((negative ? "" : "+") + rate.setScale(1, RoundingMode.HALF_UP) + "%");
            item.setTrend(rate.compareTo(BigDecimal.ZERO) >= 0 ? "up" : "down");
        } else {
            item.setChangeRate("--");
            item.setTrend("flat");
        }
        return item;
    }

    private BigDecimal queryPgSum(String table, String amountColumn, List<String> storeIds,
                                   String dateColumn, PeriodRange range) {
        if (CollectionUtils.isEmpty(storeIds)) return BigDecimal.ZERO;
        String inClause = storeIds.stream().map(s -> "'" + s.replace("'", "''") + "'")
                .collect(Collectors.joining(","));
        // store_info.store_id → dim.store.record_id → dim.store.store_uuid → fact.store_id
        String sql = "SELECT COALESCE(SUM(" + amountColumn + "), 0) AS total "
                   + "FROM " + table + " "
                   + "WHERE store_id IN (SELECT store_uuid::text FROM dim.store WHERE record_id IN (" + inClause + ")) "
                   + "AND " + dateColumn + " BETWEEN ? AND ?";
        log.debug("PG query: {} | params: {} ~ {}", sql, range.start, range.end);
        try {
            Map<String, Object> row = pgJdbcTemplate.queryForMap(sql,
                    Date.valueOf(range.start), Date.valueOf(range.end));
            Object total = row.get("total");
            log.debug("PG result: {}", total);
            return toDecimal(total);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            log.warn("PG 查询失败 {}: {}", table, root.toString());
            return BigDecimal.ZERO;
        }
    }

    /** 日常报损金额：loss_type='daily' AND status='completed' */
    private BigDecimal queryDailyLossSum(List<String> storeIds, PeriodRange range) {
        if (CollectionUtils.isEmpty(storeIds)) return BigDecimal.ZERO;
        String inClause = storeIds.stream().map(s -> "'" + s.replace("'", "''") + "'")
                .collect(Collectors.joining(","));
        String sql = "SELECT COALESCE(SUM(total_amount), 0) AS total "
                   + "FROM loss_report "
                   + "WHERE store_id IN (" + inClause + ") "
                   + "AND loss_type = 'daily' AND status = 'completed' AND del_flag = 0 "
                   + "AND occurred_date BETWEEN ? AND ?";
        try {
            Map<String, Object> row = mysqlJdbcTemplate.queryForMap(sql,
                    Date.valueOf(range.start), Date.valueOf(range.end));
            return toDecimal(row.get("total"));
        } catch (Exception e) {
            Throwable r2 = e; while (r2.getCause() != null) r2 = r2.getCause();
            log.warn("查询日常报损失败: {}", r2.toString());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal queryExpenseSum(List<String> storeIds, PeriodRange range) {
        if (CollectionUtils.isEmpty(storeIds)) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        // expense_record
        try {
            List<ExpenseRecord> records = expenseRecordMapper.selectList(
                    new LambdaQueryWrapper<ExpenseRecord>()
                            .in(ExpenseRecord::getStoreId, storeIds)
                            .ge(ExpenseRecord::getOccurredDate, range.start)
                            .le(ExpenseRecord::getOccurredDate, range.end)
                            .eq(ExpenseRecord::getDelFlag, 0));
            total = total.add(records.stream()
                    .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        } catch (Exception e) {
            log.warn("查询 expense_record 失败: {}", e.getMessage());
        }

        // self_purchase_material
        try {
            List<SelfPurchaseMaterial> mats = spmMapper.selectList(
                    new LambdaQueryWrapper<SelfPurchaseMaterial>()
                            .in(SelfPurchaseMaterial::getStoreId, storeIds)
                            .ge(SelfPurchaseMaterial::getPurchaseDate, range.start)
                            .le(SelfPurchaseMaterial::getPurchaseDate, range.end)
                            .eq(SelfPurchaseMaterial::getDelFlag, 0));
            total = total.add(mats.stream()
                    .map(m -> m.getTotalAmount() != null ? m.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        } catch (Exception e) {
            log.warn("查询 self_purchase_material 失败: {}", e.getMessage());
        }

        return total;
    }

    private Map<LocalDate, BigDecimal> queryDailyBatch(String metric, List<String> storeIds, PeriodRange range) {
        if (CollectionUtils.isEmpty(storeIds)) return Map.of();
        String inClause = storeIds.stream().map(s -> "'" + s.replace("'", "''") + "'")
                .collect(Collectors.joining(","));
        if ("sales".equals(metric)) {
            String sql = "SELECT t.period_start AS d, COALESCE(SUM(t.original_amount), 0) AS v "
                       + "FROM ads.store_day_metrics t "
                       + "JOIN dim.store ds ON ds.store_uuid::text = t.store_id "
                       + "WHERE ds.record_id IN (" + inClause + ") "
                       + "AND t.period_start BETWEEN ? AND ? GROUP BY t.period_start";
            try {
                return pgJdbcTemplate.queryForList(sql, Date.valueOf(range.start), Date.valueOf(range.end))
                        .stream().collect(Collectors.toMap(
                            row -> ((Date) row.get("d")).toLocalDate(),
                            row -> toDecimal(row.get("v"))));
            } catch (Exception e) {
                log.warn("PG 批量查询 sales 失败: {}", e.toString());
                return Map.of();
            }
        }
        if ("loss".equals(metric)) {
            String sql = "SELECT occurred_date AS d, COALESCE(SUM(total_amount), 0) AS v "
                       + "FROM loss_report "
                       + "WHERE store_id IN (" + inClause + ") "
                       + "AND loss_type = 'daily' AND status = 'completed' AND del_flag = 0 "
                       + "AND occurred_date BETWEEN ? AND ? GROUP BY occurred_date";
            try {
                return mysqlJdbcTemplate.queryForList(sql, Date.valueOf(range.start), Date.valueOf(range.end))
                        .stream().collect(Collectors.toMap(
                            row -> ((Date) row.get("d")).toLocalDate(),
                            row -> toDecimal(row.get("v"))));
            } catch (Exception e) {
                log.warn("MySQL 批量查询日常报损失败: {}", e.toString());
                return Map.of();
            }
        }
        // expense 不支持批量，逐日查
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        LocalDate d = range.start;
        while (!d.isAfter(range.end)) {
            map.put(d, queryExpenseSum(storeIds, new PeriodRange(d, d)));
            d = d.plusDays(1);
        }
        return map;
    }

    private String buildCaption(String metric, String period) {
        String periodLabel = "7".equals(period) ? "近 7 天" : "30".equals(period) ? "近 30 天" : "本月";
        String metricLabel = "sales".equals(metric) ? "销售额" : "expense".equals(metric) ? "支出" : "日常报损";
        return metricLabel + periodLabel + "走势。";
    }

    private DetailItem makeDetail(String label, BigDecimal value, String color, String formula) {
        DetailItem item = new DetailItem();
        item.setLabel(label);
        item.setValue(value);
        item.setColor(color);
        item.setFormula(formula);
        return item;
    }

    private void addCostPg(List<CostItem> list, String name, Object amountObj, BigDecimal gmv, String group) {
        BigDecimal amount = toDecimal(amountObj);
        if (amount == null) amount = BigDecimal.ZERO;
        CostItem item = new CostItem();
        item.setName(name);
        item.setAmount(amount);
        item.setGroup(group);
        if (gmv.compareTo(BigDecimal.ZERO) > 0) {
            item.setRatio(amount.divide(gmv, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP));
        } else {
            item.setRatio(BigDecimal.ZERO);
        }
        list.add(item);
    }

    private BigDecimal toDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer toInteger(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== period resolution ====================

    private PeriodRange resolvePeriod(String period, int offset) {
        LocalDate today = LocalDate.now();
        if ("7".equals(period)) {
            // trend chart shows last 7 days up to yesterday
            LocalDate end = today.minusDays(1 + offset * 7L);
            return new PeriodRange(end.minusDays(6), end);
        } else if ("30".equals(period)) {
            LocalDate end = today.minusDays(offset * 30L);
            return new PeriodRange(end.minusDays(29), end);
        } else {
            // month — current = 1st～today, previous = last month
            if (offset == 0) {
                return new PeriodRange(today.withDayOfMonth(1), today);
            } else {
                LocalDate lastMonth = today.minusMonths(1);
                LocalDate start = lastMonth.withDayOfMonth(1);
                LocalDate end = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
                return new PeriodRange(start, end);
            }
        }
    }

    private static class PeriodRange {
        final LocalDate start;
        final LocalDate end;
        PeriodRange(LocalDate start, LocalDate end) { this.start = start; this.end = end; }
    }
}
