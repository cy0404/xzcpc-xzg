package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.mp.dto.StaffApprovalReq;
import com.xzcpc.mp.dto.StaffRegisterReq;
import com.xzcpc.mp.dto.StaffResignReq;
import com.xzcpc.mp.dto.StaffUpdateReq;
import com.xzcpc.mp.entity.EmployeeRegistrationApplication;
import com.xzcpc.mp.mapper.EmployeeRegistrationApplicationMapper;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MpStaffServiceImpl implements MpStaffService {

    private static final String STATUS_ACTIVE = "在职";
    private static final String APP_PENDING = "pending";
    private static final String APP_APPROVED = "approved";
    private static final String APP_REJECTED = "rejected";

    private final EmployeeMapper employeeMapper;
    private final EmployeeRegistrationApplicationMapper applicationMapper;
    private final StoreService storeService;

    @Override
    public Map<String, Object> listStaff(String storeId, String status) {
        requireStore(storeId);
        // 筛选列表（排除老板）
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, storeId)
                .ne(Employee::getRole, "老板")
                .eq(StringUtils.hasText(status), Employee::getStatus, status)
                .orderByAsc(Employee::getRole)
                .orderByDesc(Employee::getEntryDate)
                .orderByDesc(Employee::getId));
        // 概况始终统计全部门店员工（不受 status 筛选影响，排除老板）
        List<Employee> allEmployees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, storeId)
                .ne(Employee::getRole, "老板"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", buildOverview(allEmployees));
        result.put("records", employees.stream().map(this::toStaffMap).toList());
        return result;
    }

    @Override
    public Map<String, Object> staffDetail(String storeId, String employeeId) {
        Employee employee = getEmployee(storeId, employeeId);
        return toStaffMap(employee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateStaff(String storeId, String employeeId, StaffUpdateReq req) {
        Employee employee = getEmployee(storeId, employeeId);
        employee.setName(req.getName());
        employee.setMobile(req.getMobile());
        employee.setGender(req.getGender());
        employee.setBirthday(req.getBirthday());
        employee.setRole(req.getRole());
        employee.setEmploymentType(req.getEmploymentType());
        employee.setEntryDate(req.getEntryDate());
        employee.setEmergencyContactName(req.getEmergencyContactName());
        employee.setEmergencyContactPhone(req.getEmergencyContactPhone());
        employee.setRemark(req.getRemark());
        employeeMapper.updateById(employee);
        return toStaffMap(employee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitRegistration(String openid, StaffRegisterReq req) {
        StoreInfo store = storeService.getStoreById(req.getStoreId());
        if (store == null) {
            throw new BusinessException("门店不存在");
        }

        // 重新申请：覆盖旧申请，重置为 pending
        if (StringUtils.hasText(req.getApplicationId())) {
            EmployeeRegistrationApplication application = applicationMapper.selectOne(
                    new LambdaQueryWrapper<EmployeeRegistrationApplication>()
                            .eq(EmployeeRegistrationApplication::getApplicationId, req.getApplicationId())
                            .eq(EmployeeRegistrationApplication::getOpenid, openid)
                            .last("LIMIT 1"));
            if (application == null) {
                throw new BusinessException("申请记录不存在");
            }
            if (!APP_REJECTED.equals(application.getStatus())) {
                throw new BusinessException("仅可重新提交被驳回的申请");
            }
            application.setName(req.getName());
            application.setMobile(req.getMobile());
            application.setGender(req.getGender());
            application.setBirthday(req.getBirthday());
            application.setExpectedRole(req.getExpectedRole());
            application.setEmploymentType(req.getEmploymentType());
            application.setEntryDate(req.getEntryDate());
            application.setEmergencyContactName(req.getEmergencyContactName());
            application.setEmergencyContactPhone(req.getEmergencyContactPhone());
            application.setRemark(req.getRemark());
            application.setStatus(APP_PENDING);
            application.setRejectReason(null);
            applicationMapper.updateById(application);
            return toApplicationMap(application);
        }

        // 新申请
        EmployeeRegistrationApplication application = new EmployeeRegistrationApplication();
        application.setApplicationId("TMP");
        application.setOpenid(openid);
        application.setStoreId(store.getId());
        application.setStoreName(store.getMendianmingcheng());
        application.setName(req.getName());
        application.setMobile(req.getMobile());
        application.setGender(req.getGender());
        application.setBirthday(req.getBirthday());
        application.setExpectedRole(req.getExpectedRole());
        application.setEmploymentType(req.getEmploymentType());
        application.setEntryDate(req.getEntryDate());
        application.setEmergencyContactName(req.getEmergencyContactName());
        application.setEmergencyContactPhone(req.getEmergencyContactPhone());
        application.setRemark(req.getRemark());
        application.setStatus(APP_PENDING);
        applicationMapper.insert(application);
        application.setApplicationId("STA" + String.format("%08d", application.getId()));
        applicationMapper.updateById(application);
        return toApplicationMap(application);
    }

    @Override
    public List<Map<String, Object>> listApplications(String storeId, String status) {
        requireStore(storeId);
        return applicationMapper.selectList(new LambdaQueryWrapper<EmployeeRegistrationApplication>()
                        .eq(EmployeeRegistrationApplication::getStoreId, storeId)
                        .eq(StringUtils.hasText(status), EmployeeRegistrationApplication::getStatus, status)
                        .orderByDesc(EmployeeRegistrationApplication::getCreatedAt)
                        .orderByDesc(EmployeeRegistrationApplication::getId))
                .stream()
                .map(this::toApplicationMap)
                .toList();
    }

    @Override
    public Map<String, Object> applicationDetail(String storeId, String applicationId) {
        return toApplicationMap(getApplication(storeId, applicationId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> approve(String storeId, String approverOpenid, String applicationId, StaffApprovalReq req) {
        EmployeeRegistrationApplication application = getApplication(storeId, applicationId);
        if (!APP_PENDING.equals(application.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        String action = req.getAction();
        if ("reject".equals(action)) {
            application.setStatus(APP_REJECTED);
            application.setRejectReason(req.getRejectReason());
            application.setApproverOpenid(approverOpenid);
            application.setApprovedAt(LocalDateTime.now());
            applicationMapper.updateById(application);
            return toApplicationMap(application);
        }
        if (!"approve".equals(action)) {
            throw new BusinessException("审批动作不正确");
        }

        Employee employee = new Employee();
        employee.setEmployeeId("TMP");
        employee.setStoreId(application.getStoreId());
        employee.setStoreName(application.getStoreName());
        employee.setStatus(STATUS_ACTIVE);
        employee.setOpenid(application.getOpenid());
        employee.setName(application.getName());
        employee.setMobile(application.getMobile());
        employee.setGender(application.getGender());
        employee.setBirthday(application.getBirthday());
        employee.setRole(StringUtils.hasText(req.getRole()) ? req.getRole() : application.getExpectedRole());
        employee.setEmploymentType(StringUtils.hasText(req.getEmploymentType()) ? req.getEmploymentType() : application.getEmploymentType());
        employee.setEntryDate(application.getEntryDate());
        employee.setEmergencyContactName(application.getEmergencyContactName());
        employee.setEmergencyContactPhone(application.getEmergencyContactPhone());
        employee.setRemark(application.getRemark());
        employeeMapper.insert(employee);
        employee.setEmployeeId("EMP" + String.format("%08d", employee.getId()));
        employeeMapper.updateById(employee);

        application.setStatus(APP_APPROVED);
        application.setApproverOpenid(approverOpenid);
        application.setApprovedAt(LocalDateTime.now());
        application.setEmployeeId(employee.getEmployeeId());
        applicationMapper.updateById(application);
        return toApplicationMap(application);
    }

    @Override
    public List<Map<String, Object>> findStoresByOpenid(String openid) {
        if (!StringUtils.hasText(openid)) {
            return List.of();
        }
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getOpenid, openid)
                .eq(Employee::getStatus, STATUS_ACTIVE)
                .orderByAsc(Employee::getId));
        return employees.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("storeId", e.getStoreId());
            map.put("storeName", e.getStoreName());
            map.put("employeeId", e.getEmployeeId());
            map.put("employeeName", e.getName());
            map.put("role", e.getRole());
            map.put("permissions", permissionsForRole(e.getRole()));
            return map;
        }).toList();
    }

    @Override
    public Map<String, Object> currentStaffProfile(String openid, String storeId) {
        if (!StringUtils.hasText(openid) || !StringUtils.hasText(storeId)) {
            return Map.of("role", "", "employeeId", "", "employeeName", "", "staffBound", false);
        }
        Employee employee = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, storeId)
                .eq(Employee::getOpenid, openid)
                .last("LIMIT 1"));
        if (employee == null) {
            employee = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getStoreId, storeId)
                    .eq(Employee::getStatus, STATUS_ACTIVE)
                    .last("LIMIT 1"));
        }
        if (employee == null) {
            return Map.of("role", "", "employeeId", "", "employeeName", "", "staffBound", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", employee.getEmployeeId());
        result.put("employeeName", employee.getName());
        result.put("role", employee.getRole());
        result.put("staffBound", true);
        result.put("permissions", permissionsForRole(employee.getRole()));
        return result;
    }

    private Employee getEmployee(String storeId, String employeeId) {
        requireStore(storeId);
        Employee employee = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, storeId)
                .eq(Employee::getEmployeeId, employeeId)
                .last("LIMIT 1"));
        if (employee == null) {
            throw new BusinessException("员工不存在");
        }
        return employee;
    }

    private EmployeeRegistrationApplication getApplication(String storeId, String applicationId) {
        requireStore(storeId);
        EmployeeRegistrationApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<EmployeeRegistrationApplication>()
                        .eq(EmployeeRegistrationApplication::getStoreId, storeId)
                        .eq(EmployeeRegistrationApplication::getApplicationId, applicationId)
                        .last("LIMIT 1"));
        if (application == null) {
            throw new BusinessException("登记申请不存在");
        }
        return application;
    }

    private void requireStore(String storeId) {
        if (!StringUtils.hasText(storeId)) {
            throw new BusinessException(4031, "请先选择门店");
        }
    }

    private Map<String, Object> buildOverview(List<Employee> employees) {
        // 排除老板，只统计店长/店员/兼职
        List<Employee> staffOnly = employees.stream()
                .filter(e -> !"老板".equals(e.getRole())).toList();
        long active = staffOnly.stream().filter(e -> STATUS_ACTIVE.equals(e.getStatus())).count();
        List<Employee> activeStaff = staffOnly.stream()
                .filter(e -> STATUS_ACTIVE.equals(e.getStatus())).toList();
        long manager = activeStaff.stream().filter(e -> "店长".equals(e.getRole())).count();
        long staff = activeStaff.stream().filter(e -> "店员".equals(e.getRole())).count();
        long partTime = activeStaff.stream().filter(e -> "兼职".equals(e.getEmploymentType()) || "兼职".equals(e.getRole())).count();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("activeCount", active);
        map.put("managerCount", manager);
        map.put("staffCount", staff);
        map.put("partTimeCount", partTime);
        map.put("totalCount", staffOnly.size());
        return map;
    }

    private Map<String, Object> toStaffMap(Employee employee) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("employeeId", employee.getEmployeeId());
        map.put("name", employee.getName());
        map.put("mobile", employee.getMobile());
        map.put("gender", employee.getGender());
        map.put("birthday", employee.getBirthday());
        map.put("storeId", employee.getStoreId());
        map.put("storeName", employee.getStoreName());
        map.put("role", employee.getRole());
        map.put("employmentType", employee.getEmploymentType());
        map.put("entryDate", employee.getEntryDate());
        map.put("leaveDate", employee.getLeaveDate());
        map.put("status", employee.getStatus());
        map.put("emergencyContactName", employee.getEmergencyContactName());
        map.put("emergencyContactPhone", employee.getEmergencyContactPhone());
        map.put("remark", employee.getRemark());
        map.put("staffBound", StringUtils.hasText(employee.getOpenid()));
        map.put("permissions", permissionsForRole(employee.getRole()));
        return map;
    }

    private Map<String, Object> toApplicationMap(EmployeeRegistrationApplication application) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("applicationId", application.getApplicationId());
        map.put("storeId", application.getStoreId());
        map.put("storeName", application.getStoreName());
        map.put("name", application.getName());
        map.put("mobile", application.getMobile());
        map.put("gender", application.getGender());
        map.put("birthday", application.getBirthday());
        map.put("expectedRole", application.getExpectedRole());
        map.put("employmentType", application.getEmploymentType());
        map.put("entryDate", application.getEntryDate());
        map.put("emergencyContactName", application.getEmergencyContactName());
        map.put("emergencyContactPhone", application.getEmergencyContactPhone());
        map.put("remark", application.getRemark());
        map.put("status", application.getStatus());
        map.put("rejectReason", application.getRejectReason());
        map.put("employeeId", application.getEmployeeId());
        map.put("createdAt", application.getCreatedAt());
        map.put("approvedAt", application.getApprovedAt());
        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resign(String storeId, String employeeId, StaffResignReq req) {
        Employee employee = getEmployee(storeId, employeeId);
        employee.setStatus("离职");
        employee.setLeaveDate(req.getResignDate() != null ? java.time.LocalDate.parse(req.getResignDate()) : null);
        employeeMapper.updateById(employee);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", employee.getEmployeeId());
        result.put("name", employee.getName());
        result.put("status", employee.getStatus());
        result.put("leaveDate", employee.getLeaveDate());
        return result;
    }

    private List<String> permissionsForRole(String role) {
        List<String> permissions = new ArrayList<>();
        permissions.add("task:view");
        if ("老板".equals(role) || "店长".equals(role)) {
            permissions.add("task:manage");
            permissions.add("expense:manage");
            permissions.add("staff:manage");
        } else {
            permissions.add("task:entry");
            permissions.add("expense:create");
        }
        return permissions;
    }
}
