package com.xzcpc.expense.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRecordMapper expenseRecordMapper;

    @Override
    public Page<ExpenseRecord> page(String storeId, String typeId, String startDate, String endDate,
                                    String handlerName, int pageNum, int pageSize) {
        LambdaQueryWrapper<ExpenseRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(storeId)) {
            wrapper.eq(ExpenseRecord::getStoreId, storeId);
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
        return expenseRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public ExpenseDashboardResp dashboard(String range) {
        LocalDate end = LocalDate.now();
        LocalDate start = getRangeStart(range, end);
        List<ExpenseRecord> records = expenseRecordMapper.selectList(
                new LambdaQueryWrapper<ExpenseRecord>()
                        .ge(ExpenseRecord::getOccurredDate, start)
                        .le(ExpenseRecord::getOccurredDate, end));

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
        resp.setStoreRanking(buildStoreRanking(storeAmountMap));
        resp.setTypeDistribution(buildTypeDistribution(records));
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

    private List<ExpenseDashboardResp.StoreRanking> buildStoreRanking(Map<String, BigDecimal> storeAmountMap) {
        BigDecimal max = storeAmountMap.values().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        return storeAmountMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new ExpenseDashboardResp.StoreRanking(
                        entry.getKey(),
                        entry.getValue(),
                        max.compareTo(BigDecimal.ZERO) == 0
                                ? 0
                                : entry.getValue().multiply(BigDecimal.valueOf(100))
                                        .divide(max, 0, RoundingMode.HALF_UP)
                                        .intValue()))
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
}
