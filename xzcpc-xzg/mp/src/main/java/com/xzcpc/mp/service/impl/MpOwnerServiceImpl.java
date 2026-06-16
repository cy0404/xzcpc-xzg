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

        // 3. 本月支出（所有门店汇总）
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        resp.setTotalExpense(formatMoney(sumExpenses(storeIds, firstOfMonth, today)));

        // 4. 在职员工总数
        int totalStaff = 0;
        Map<String, Integer> staffCountByStore = new HashMap<>();
        Map<String, String> managerByStore = new HashMap<>();
        Map<String, String> expenseByStore = new HashMap<>();

        for (String storeId : storeIds) {
            // 员工数（排除老板）
            Long count = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getStoreId, storeId)
                    .eq(Employee::getStatus, "在职")
                    .ne(Employee::getRole, "老板"));
            int sc = count != null ? count.intValue() : 0;
            staffCountByStore.put(storeId, sc);
            totalStaff += sc;

            // 店长
            Employee manager = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getStoreId, storeId)
                    .eq(Employee::getRole, "店长")
                    .eq(Employee::getStatus, "在职")
                    .last("LIMIT 1"));
            managerByStore.put(storeId, manager != null ? manager.getName() : "--");

            // 本月支出
            expenseByStore.put(storeId, formatMoney(sumExpenses(List.of(storeId), firstOfMonth, today)));
        }
        resp.setTotalStaff(totalStaff);

        // 5. 组装门店列表
        List<OwnerDashboardResp.StoreSummary> storeList = new ArrayList<>();
        for (Map<String, Object> s : stores) {
            String sid = (String) s.get("storeId");
            OwnerDashboardResp.StoreSummary summary = new OwnerDashboardResp.StoreSummary();
            summary.setStoreId(sid);
            summary.setStoreName((String) s.get("storeName"));
            summary.setManager(managerByStore.getOrDefault(sid, "--"));
            summary.setStaffCount(staffCountByStore.getOrDefault(sid, 0));
            summary.setExpense(expenseByStore.getOrDefault(sid, "¥0"));
            storeList.add(summary);
        }
        resp.setStores(storeList);

        return resp;
    }

    private BigDecimal sumExpenses(List<String> storeIds, LocalDate start, LocalDate end) {
        if (storeIds.isEmpty()) return BigDecimal.ZERO;
        List<ExpenseRecord> records = expenseRecordMapper.selectList(new LambdaQueryWrapper<ExpenseRecord>()
                .in(ExpenseRecord::getStoreId, storeIds)
                .between(ExpenseRecord::getOccurredDate, start, end));
        return records.stream()
                .map(ExpenseRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return "¥0";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(0);
        return "¥" + nf.format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
