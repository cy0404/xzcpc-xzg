package com.xzcpc.expense.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.expense.dto.ExpenseDashboardResp;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.mapper.ExpenseRecordMapper;
import com.xzcpc.expense.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRecordMapper expenseRecordMapper;
    private final StoreAccessService storeAccessService;

    @Override
    public Page<ExpenseRecord> page(String storeId, String supervisorName, String typeId, String startDate, String endDate,
                                    String handlerName, int pageNum, int pageSize) {
        LambdaQueryWrapper<ExpenseRecord> wrapper = new LambdaQueryWrapper<>();

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(ExpenseRecord::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(ExpenseRecord::getStoreId, supervisorStoreIds);
        }

        if (StringUtils.hasText(storeId)) {
            String[] ids = storeId.split(",");
            if (ids.length == 1) {
                wrapper.eq(ExpenseRecord::getStoreId, ids[0].trim());
            } else {
                wrapper.in(ExpenseRecord::getStoreId, java.util.Arrays.asList(ids));
            }
        }
        if (StringUtils.hasText(typeId)) {
            wrapper.eq(ExpenseRecord::getTypeId, typeId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(ExpenseRecord::getOccurredDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(ExpenseRecord::getOccurredDate, LocalDate.parse(endDate));
        }
        if (StringUtils.hasText(handlerName)) {
            wrapper.like(ExpenseRecord::getHandlerName, handlerName.trim());
        }
        wrapper.orderByDesc(ExpenseRecord::getOccurredDate).orderByDesc(ExpenseRecord::getId);
        Page<ExpenseRecord> result = expenseRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<ExpenseRecord> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<String> storeIds = records.stream().map(ExpenseRecord::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
            if (!storeIds.isEmpty()) {
                Map<String, String> supervisorMap = storeAccessService.getSupervisorNamesByStoreIds(storeIds);
                records.forEach(r -> r.setSupervisorName(supervisorMap.getOrDefault(r.getStoreId(), "")));
            }
        }
        return result;
    }

    @Override
    public ExpenseDashboardResp dashboard(String range, String startDate, String endDate, String supervisorName) {
        LocalDate end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDate start;
        if (StringUtils.hasText(startDate)) {
            start = LocalDate.parse(startDate);
        } else {
            start = getRangeStart(range, end);
        }

        var qw = new LambdaQueryWrapper<ExpenseRecord>()
                .ge(ExpenseRecord::getOccurredDate, start)
                .le(ExpenseRecord::getOccurredDate, end);

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return emptyDashboard();
            }
            qw.in(ExpenseRecord::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return emptyDashboard();
            }
            qw.in(ExpenseRecord::getStoreId, supervisorStoreIds);
        }

        List<ExpenseRecord> records = expenseRecordMapper.selectList(qw);

        // 批量填充督导姓名
        if (!records.isEmpty()) {
            Set<String> storeIds = records.stream().map(ExpenseRecord::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
            if (!storeIds.isEmpty()) {
                Map<String, String> supMap = storeAccessService.getSupervisorNamesByStoreIds(storeIds);
                records.forEach(r -> r.setSupervisorName(supMap.getOrDefault(r.getStoreId(), "")));
            }
        }

        ExpenseDashboardResp resp = new ExpenseDashboardResp();
        BigDecimal totalAmount = records.stream()
                .map(ExpenseRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long storeCount = records.stream().map(ExpenseRecord::getStoreId).filter(Objects::nonNull).distinct().count();
        BigDecimal avgStoreAmount = storeCount == 0
                ? BigDecimal.ZERO
                : totalAmount.divide(BigDecimal.valueOf(storeCount), 2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> storeAmountMap = records.stream()
                .collect(Collectors.groupingBy(
                        item -> StringUtils.hasText(item.getStoreName()) ? item.getStoreName() : item.getStoreId(),
                        Collectors.mapping(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        Map.Entry<String, BigDecimal> topStore = storeAmountMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(Map.entry("-", BigDecimal.ZERO));

        long voucherCount = records.stream().filter(item -> StringUtils.hasText(item.getVoucherUrl())).count();
        int voucherRate = records.isEmpty()
                ? 0
                : BigDecimal.valueOf(voucherCount * 100)
                        .divide(BigDecimal.valueOf(records.size()), 0, RoundingMode.HALF_UP)
                        .intValue();

        resp.setSummary(new ExpenseDashboardResp.Summary(
                totalAmount,
                "较上期持平",
                avgStoreAmount,
                storeCount,
                topStore.getKey(),
                topStore.getValue(),
                voucherRate,
                "较上期持平"
        ));
        Map<String, Integer> storeCountMap = records.stream()
                .collect(Collectors.groupingBy(
                        item -> StringUtils.hasText(item.getStoreName()) ? item.getStoreName() : item.getStoreId(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        // storeName → supervisorName 映射
        Map<String, String> storeNameToSupervisor = new LinkedHashMap<>();
        Set<String> seenStores = new HashSet<>();
        for (ExpenseRecord r : records) {
            if (r.getStoreId() != null && r.getStoreName() != null && seenStores.add(r.getStoreId())) {
                storeNameToSupervisor.put(r.getStoreName(), r.getSupervisorName() != null ? r.getSupervisorName() : "");
            }
        }
        resp.setStoreRanking(buildStoreRanking(storeAmountMap, storeCountMap, storeNameToSupervisor));
        resp.setTypeDistribution(buildTypeDistribution(records));
        resp.setMonthlyTrend(buildMonthlyTrend(range, start, end));
        return resp;
    }

    private ExpenseDashboardResp emptyDashboard() {
        ExpenseDashboardResp resp = new ExpenseDashboardResp();
        resp.setSummary(new ExpenseDashboardResp.Summary(
                BigDecimal.ZERO, "0%", BigDecimal.ZERO, 0L, "--", BigDecimal.ZERO, 0, ""));
        resp.setStoreRanking(List.of());
        resp.setTypeDistribution(List.of());
        resp.setMonthlyTrend(List.of());
        return resp;
    }

    private LocalDate getRangeStart(String range, LocalDate end) {
        YearMonth currentMonth = YearMonth.from(end);
        if ("quarter".equals(range)) {
            return currentMonth.minusMonths(2).atDay(1);
        }
        if ("halfYear".equals(range)) {
            return currentMonth.minusMonths(5).atDay(1);
        }
        return currentMonth.atDay(1);
    }

    private List<ExpenseDashboardResp.StoreRanking> buildStoreRanking(
            Map<String, BigDecimal> storeAmountMap, Map<String, Integer> storeCountMap,
            Map<String, String> supervisorMap) {
        BigDecimal maxAmt = storeAmountMap.values().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        int maxCnt = storeCountMap.values().stream().max(Comparator.naturalOrder()).orElse(1);
        return storeAmountMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> {
                    String name = entry.getKey();
                    int count = storeCountMap.getOrDefault(name, 0);
                    ExpenseDashboardResp.StoreRanking ranking = new ExpenseDashboardResp.StoreRanking(
                            name,
                            supervisorMap.getOrDefault(name, ""),
                            entry.getValue(),
                            count,
                            0,
                            maxAmt.compareTo(BigDecimal.ZERO) == 0 ? 0
                                    : entry.getValue().multiply(BigDecimal.valueOf(100))
                                            .divide(maxAmt, 0, RoundingMode.HALF_UP).intValue(),
                            maxCnt == 0 ? 0 : count * 100 / maxCnt);
                    return ranking;
                })
                .collect(Collectors.toList());
    }

    private List<ExpenseDashboardResp.TypeDistribution> buildTypeDistribution(List<ExpenseRecord> records) {
        BigDecimal total = records.stream()
                .map(ExpenseRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> typeAmountMap = records.stream()
                .collect(Collectors.groupingBy(
                        item -> StringUtils.hasText(item.getTypeName()) ? item.getTypeName() : item.getTypeId(),
                        Collectors.mapping(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        return typeAmountMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> new ExpenseDashboardResp.TypeDistribution(
                        entry.getKey(),
                        total.compareTo(BigDecimal.ZERO) == 0
                                ? 0
                                : entry.getValue().multiply(BigDecimal.valueOf(100))
                                        .divide(total, 0, RoundingMode.HALF_UP)
                                        .intValue()))
                .collect(Collectors.toList());
    }

    private List<ExpenseDashboardResp.MonthlyTrend> buildMonthlyTrend(String range, LocalDate start, LocalDate end) {
        List<ExpenseDashboardResp.MonthlyTrend> trend = new ArrayList<>();
        YearMonth current = YearMonth.from(start);
        YearMonth endMonth = YearMonth.from(end);
        while (!current.isAfter(endMonth)) {
            trend.add(new ExpenseDashboardResp.MonthlyTrend(
                    current.getMonthValue() + "月",
                    BigDecimal.ZERO));
            current = current.plusMonths(1);
        }
        List<ExpenseRecord> records = expenseRecordMapper.selectList(
                new LambdaQueryWrapper<ExpenseRecord>()
                        .ge(ExpenseRecord::getOccurredDate, start)
                        .le(ExpenseRecord::getOccurredDate, end));
        Map<String, BigDecimal> monthlyAmount = records.stream()
                .filter(r -> r.getOccurredDate() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getOccurredDate().getMonthValue() + "月",
                        Collectors.mapping(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        for (ExpenseDashboardResp.MonthlyTrend t : trend) {
            BigDecimal amount = monthlyAmount.get(t.getMonth());
            if (amount != null) t.setAmount(amount);
        }
        return trend;
    }
}
