package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.StaffApprovalReq;
import com.xzcpc.mp.dto.StaffRegisterReq;
import com.xzcpc.mp.dto.StaffResignReq;
import com.xzcpc.mp.dto.StaffUpdateReq;
import com.xzcpc.mp.service.MpStaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mp/staff")
@RequiredArgsConstructor
public class MpStaffController {

    private final MpStaffService staffService;

    @OpLog(module = "小程序-人员", operation = "查询当前用户员工详情")
    @GetMapping("/me")
    public R<Map<String, Object>> myProfile() {
        LoginUser user = UserContextHolder.get();
        Map<String, Object> profile = staffService.currentStaffProfile(user.getOpenid(), user.getStoreId());
        String employeeId = (String) profile.getOrDefault("employeeId", "");
        if (employeeId.isEmpty()) {
            throw new BusinessException(404, "未找到您的员工信息，请联系店长完善入职资料");
        }
        return R.ok(staffService.staffDetail(user.getStoreId(), employeeId));
    }

    @OpLog(module = "小程序-人员", operation = "查询员工列表")
    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "") String status) {
        LoginUser user = UserContextHolder.get();
        return R.ok(staffService.listStaff(user.getStoreId(), status));
    }

    @OpLog(module = "小程序-人员", operation = "查询员工详情")
    @GetMapping("/{employeeId}")
    public R<Map<String, Object>> detail(@PathVariable String employeeId) {
        LoginUser user = UserContextHolder.get();
        return R.ok(staffService.staffDetail(user.getStoreId(), employeeId));
    }

    @OpLog(module = "小程序-人员", operation = "编辑员工信息")
    @PutMapping("/{employeeId}")
    public R<Map<String, Object>> update(@PathVariable String employeeId, @Valid @RequestBody StaffUpdateReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(staffService.updateStaff(user.getStoreId(), employeeId, req));
    }

    @OpLog(module = "小程序-人员", operation = "提交员工登记")
    @PostMapping("/registrations")
    public R<Map<String, Object>> register(@Valid @RequestBody StaffRegisterReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(staffService.submitRegistration(user.getOpenid(), req));
    }

    @OpLog(module = "小程序-人员", operation = "查询登记申请")
    @GetMapping("/applications")
    public R<List<Map<String, Object>>> listApplications(@RequestParam(defaultValue = "pending") String status) {
        LoginUser user = UserContextHolder.get();
        return R.ok(staffService.listApplications(user.getStoreId(), status));
    }

    @OpLog(module = "小程序-人员", operation = "查询登记详情")
    @GetMapping("/applications/{applicationId}")
    public R<Map<String, Object>> applicationDetail(@PathVariable String applicationId) {
        LoginUser user = UserContextHolder.get();
        return R.ok(staffService.applicationDetail(user.getStoreId(), applicationId));
    }

    @OpLog(module = "小程序-人员", operation = "审批登记申请")
    @PostMapping("/applications/{applicationId}/approval")
    public R<Map<String, Object>> approve(@PathVariable String applicationId, @RequestBody StaffApprovalReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(staffService.approve(user.getStoreId(), user.getOpenid(), applicationId, req));
    }

    @OpLog(module = "小程序-人员", operation = "办理离职")
    @PutMapping("/{employeeId}/resign")
    public R<Map<String, Object>> resign(@PathVariable String employeeId, @RequestBody StaffResignReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(staffService.resign(user.getStoreId(), employeeId, req));
    }
}
