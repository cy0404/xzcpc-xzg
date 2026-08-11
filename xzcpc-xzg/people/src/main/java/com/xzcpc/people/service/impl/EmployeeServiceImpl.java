package com.xzcpc.people.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.people.dto.PeopleDashboardResp;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import com.xzcpc.people.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private static final String STATUS_ACTIVE = "在职";

    /** 不计入在职员工统计的角色 */
    private static final java.util.Set<String> STAT_EXCLUDE_ROLES =
            java.util.Set.of("老板");

    private final EmployeeMapper employeeMapper;
    private final StoreAccessService storeAccessService;

    @Override
    public Page<Employee> page(String storeId, String supervisorName, String role, String status, String name, int pageNum, int pageSize) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(Employee::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(Employee::getStoreId, supervisorStoreIds);
        }

        if (StringUtils.hasText(storeId)) {
            wrapper.eq(Employee::getStoreId, storeId);
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(Employee::getRole, role);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Employee::getStatus, status);
        }
        if (StringUtils.hasText(name)) {
            wrapper.like(Employee::getName, name.trim());
        }
        wrapper.orderByDesc(Employee::getEntryDate).orderByDesc(Employee::getId);
        Page<Employee> result = employeeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Employee> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<String> storeIds = records.stream().map(Employee::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
            if (!storeIds.isEmpty()) {
                Map<String, String> supervisorMap = storeAccessService.getSupervisorNamesByStoreIds(storeIds);
                records.forEach(r -> r.setSupervisorName(supervisorMap.getOrDefault(r.getStoreId(), "")));
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> detail(String employeeId) {
        Employee employee = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>().eq(Employee::getEmployeeId, employeeId));
        if (employee == null) {
            throw new BusinessException(404, "员工不存在");
        }

        // 督导角色校验：只能查看自己管辖门店的员工
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty() || !accessibleStoreIds.contains(employee.getStoreId())) {
                throw new BusinessException(404, "员工不存在");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", employee.getId());
        result.put("employeeId", employee.getEmployeeId());
        result.put("name", employee.getName());
        result.put("mobile", employee.getMobile());
        result.put("gender", employee.getGender());
        result.put("birthday", employee.getBirthday());
        result.put("storeId", employee.getStoreId());
        result.put("storeMiniappNo", employee.getStoreMiniappNo());
        result.put("storeName", employee.getStoreName());
        result.put("role", employee.getRole());
        result.put("employmentType", employee.getEmploymentType());
        result.put("entryDate", employee.getEntryDate());
        result.put("status", employee.getStatus());
        result.put("permissions", buildPermissions(employee));
        result.put("timeline", buildTimeline(employee));
        result.put("histories", buildHistories(employee));
        return result;
    }

    @Override
    public PeopleDashboardResp dashboard(String range, String supervisorName) {
        LocalDate end = LocalDate.now();
        LocalDate start = getRangeStart(range, end);

        LambdaQueryWrapper<Employee> qw = new LambdaQueryWrapper<>();

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return emptyPeopleDashboard();
            }
            qw.in(Employee::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return emptyPeopleDashboard();
            }
            qw.in(Employee::getStoreId, supervisorStoreIds);
        }

        List<Employee> allEmployees = employeeMapper.selectList(qw);

        // 批量填充督导姓名
        if (!allEmployees.isEmpty()) {
            Set<String> storeIdSet = allEmployees.stream().map(Employee::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
            if (!storeIdSet.isEmpty()) {
                Map<String, String> supMap = storeAccessService.getSupervisorNamesByStoreIds(storeIdSet);
                allEmployees.forEach(e -> e.setSupervisorName(supMap.getOrDefault(e.getStoreId(), "")));
            }
        }

        List<Employee> activeEmployees = allEmployees.stream()
                .filter(item -> STATUS_ACTIVE.equals(item.getStatus()))
                .filter(item -> !STAT_EXCLUDE_ROLES.contains(item.getRole()))
                .collect(Collectors.toList());
        long newHireCount = allEmployees.stream()
                .filter(item -> item.getEntryDate() != null
                        && !item.getEntryDate().isBefore(start)
                        && !item.getEntryDate().isAfter(end))
                .count();
        long leaveCount = allEmployees.stream()
                .filter(item -> item.getLeaveDate() != null
                        && !item.getLeaveDate().isBefore(start)
                        && !item.getLeaveDate().isAfter(end))
                .count();
        long storeCount = activeEmployees.stream()
                .map(Employee::getStoreId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        double turnoverRate = activeEmployees.isEmpty()
                ? 0
                : BigDecimal.valueOf(leaveCount * 100.0)
                        .divide(BigDecimal.valueOf(activeEmployees.size()), 1, RoundingMode.HALF_UP)
                        .doubleValue();

        PeopleDashboardResp resp = new PeopleDashboardResp();
        resp.setSummary(new PeopleDashboardResp.Summary(
                activeEmployees.size(),
                storeCount,
                newHireCount,
                "较上期持平",
                leaveCount,
                "按当前时间范围统计",
                turnoverRate,
                "按当前时间范围统计"
        ));
        resp.setStoreDistribution(buildStoreDistribution(activeEmployees));
        resp.setRoleDistribution(buildRoleDistribution(activeEmployees));
        resp.setTrend(buildTrend(allEmployees, start, end));
        return resp;
    }

    private PeopleDashboardResp emptyPeopleDashboard() {
        PeopleDashboardResp resp = new PeopleDashboardResp();
        resp.setSummary(new PeopleDashboardResp.Summary(0L, 0L, 0L, "", 0L, "", 0.0, ""));
        resp.setStoreDistribution(List.of());
        resp.setRoleDistribution(List.of());
        resp.setTrend(List.of());
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

    private List<PeopleDashboardResp.StoreDistribution> buildStoreDistribution(List<Employee> activeEmployees) {
        Map<String, Long> storeCountMap = activeEmployees.stream()
                .collect(Collectors.groupingBy(
                        item -> StringUtils.hasText(item.getStoreName()) ? item.getStoreName() : item.getStoreId(),
                        Collectors.counting()));
        // storeName → supervisorName
        Map<String, String> supMap = new LinkedHashMap<>();
        for (Employee e : activeEmployees) {
            String name = StringUtils.hasText(e.getStoreName()) ? e.getStoreName() : e.getStoreId();
            if (!supMap.containsKey(name) && e.getSupervisorName() != null) {
                supMap.put(name, e.getSupervisorName());
            }
        }
        long max = storeCountMap.values().stream().max(Comparator.naturalOrder()).orElse(0L);
        return storeCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new PeopleDashboardResp.StoreDistribution(
                        entry.getKey(),
                        supMap.getOrDefault(entry.getKey(), ""),
                        entry.getValue(),
                        max == 0 ? 0 : BigDecimal.valueOf(entry.getValue() * 100)
                                .divide(BigDecimal.valueOf(max), 0, RoundingMode.HALF_UP)
                                .intValue()))
                .collect(Collectors.toList());
    }

    private List<PeopleDashboardResp.RoleDistribution> buildRoleDistribution(List<Employee> activeEmployees) {
        Map<String, Long> roleCountMap = activeEmployees.stream()
                .collect(Collectors.groupingBy(
                        item -> StringUtils.hasText(item.getRole()) ? item.getRole() : "未设置",
                        Collectors.counting()));
        long total = activeEmployees.size();
        return roleCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new PeopleDashboardResp.RoleDistribution(
                        entry.getKey(),
                        total == 0 ? 0 : BigDecimal.valueOf(entry.getValue() * 100)
                                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                                .intValue()))
                .collect(Collectors.toList());
    }

    private List<PeopleDashboardResp.TrendPoint> buildTrend(List<Employee> employees, LocalDate start, LocalDate end) {
        List<PeopleDashboardResp.TrendPoint> result = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth endMonth = YearMonth.from(end);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M月");
        while (!cursor.isAfter(endMonth)) {
            YearMonth month = cursor;
            long hire = employees.stream()
                    .filter(item -> item.getEntryDate() != null && YearMonth.from(item.getEntryDate()).equals(month))
                    .count();
            long leave = employees.stream()
                    .filter(item -> item.getLeaveDate() != null && YearMonth.from(item.getLeaveDate()).equals(month))
                    .count();
            result.add(new PeopleDashboardResp.TrendPoint(cursor.atDay(1).format(formatter), hire, leave));
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    private List<Map<String, Object>> buildPermissions(Employee employee) {
        boolean active = STATUS_ACTIVE.equals(employee.getStatus());
        return List.of(
                Map.of("name", "门店盘点", "enabled", active),
                Map.of("name", "支出登记", "enabled", active),
                Map.of("name", "员工管理", "enabled", active),
                Map.of("name", "总部后台", "enabled", false)
        );
    }

    private List<Map<String, String>> buildTimeline(Employee employee) {
        List<Map<String, String>> timeline = new ArrayList<>();
        LocalDate entryDate = employee.getEntryDate();
        timeline.add(Map.of(
                "date", entryDate == null ? "-" : entryDate.minusDays(2).toString(),
                "title", "提交员工登记",
                "desc", "由门店端完成信息登记"
        ));
        timeline.add(Map.of(
                "date", entryDate == null ? "-" : entryDate.minusDays(1).toString(),
                "title", "审批通过",
                "desc", employee.getRole() + "权限生效"
        ));
        timeline.add(Map.of(
                "date", entryDate == null ? "-" : entryDate.toString(),
                "title", "正式入职",
                "desc", "绑定" + employee.getStoreName()
        ));
        if (employee.getLeaveDate() != null) {
            timeline.add(Map.of(
                    "date", employee.getLeaveDate().toString(),
                    "title", "离职",
                    "desc", "员工状态变更为离职"
            ));
        }
        return timeline;
    }

    private List<Map<String, String>> buildHistories(Employee employee) {
        return List.of(
                Map.of("label", "盘点记录", "value", "0 次", "desc", "暂无真实盘点汇总"),
                Map.of("label", "支出记录", "value", "0 笔", "desc", "暂无真实支出汇总")
        );
    }
}
