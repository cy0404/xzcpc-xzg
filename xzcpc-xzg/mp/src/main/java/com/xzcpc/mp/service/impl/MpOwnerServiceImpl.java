package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.mapper.ExpenseRecordMapper;
import com.xzcpc.mp.dto.OwnerDashboardResp;
import com.xzcpc.mp.service.MpOwnerService;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpOwnerServiceImpl implements MpOwnerService {

    private final MpStaffService staffService;
    private final EmployeeMapper employeeMapper;
    private final ExpenseRecordMapper expenseRecordMapper;

    @Override
    public OwnerDashboardResp dashboard(String openid) {
        // 1. 获取该 openid 关联的所有门店
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);

        OwnerDashboardResp resp = new OwnerDashboardResp();
        resp.setStoreCount(stores.size());

        if (stores.isEmpty()) {
            resp.setOwnerName("老板");
            resp.setTotalExpense("¥0");
            resp.setTotalStaff(0);
            resp.setStores(List.of());
            return resp;
        }

        List<String> storeIds = stores.stream()
                .map(s -> (String) s.get("storeId"))
                .filter(StringUtils::hasText)
                .toList();

        // 2. 老板姓名（从第一个门店取 employeeName）
        resp.setOwnerName((String) stores.get(0).getOrDefault("employeeName", "老板"));

        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        // 4. 批量查所有门店的在职员工（一次查询代替 N 次）
        List<Employee> allEmployees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getStoreId, storeIds)
                .eq(Employee::getStatus, "在职"));
        // 员工数（排除老板）
        Map<String, Long> staffCountByStore = allEmployees.stream()
                .filter(e -> !"老板".equals(e.getRole()))
                .collect(Collectors.groupingBy(Employee::getStoreId, Collectors.counting()));
        int totalStaff = (int) staffCountByStore.values().stream().mapToLong(Long::longValue).sum();
        // 店长
        Map<String, String> managerByStore = allEmployees.stream()
                .filter(e -> "店长".equals(e.getRole()))
                .collect(Collectors.toMap(Employee::getStoreId, Employee::getName, (a, b) -> a));

        // 5. 批量查所有门店的当月支出（一次查询代替 N 次）
        List<ExpenseRecord> allExpenses = expenseRecordMapper.selectList(new LambdaQueryWrapper<ExpenseRecord>()
                .in(ExpenseRecord::getStoreId, storeIds)
                .between(ExpenseRecord::getOccurredDate, firstOfMonth, today));
        Map<String, BigDecimal> expenseByStore = allExpenses.stream()
                .collect(Collectors.groupingBy(ExpenseRecord::getStoreId,
                        Collectors.reducing(BigDecimal.ZERO, e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add)));

        resp.setTotalStaff(totalStaff);

        // 6. 组装门店列表
        List<OwnerDashboardResp.StoreSummary> storeList = new ArrayList<>();
        for (Map<String, Object> s : stores) {
            String sid = (String) s.get("storeId");
            OwnerDashboardResp.StoreSummary summary = new OwnerDashboardResp.StoreSummary();
            summary.setStoreId(sid);
            summary.setStoreName((String) s.get("storeName"));
            summary.setManager(managerByStore.getOrDefault(sid, "--"));
            summary.setStaffCount(staffCountByStore.getOrDefault(sid, 0L).intValue());
            summary.setExpense(formatMoney(expenseByStore.getOrDefault(sid, BigDecimal.ZERO)));
            storeList.add(summary);
        }
        resp.setStores(storeList);

        // 本月总支出
        BigDecimal totalExpense = expenseByStore.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        resp.setTotalExpense(formatMoney(totalExpense));

        return resp;
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return "¥0";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(0);
        return "¥" + nf.format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
