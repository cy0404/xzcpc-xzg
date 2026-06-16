package com.xzcpc.mp.controller;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mp/staff")
@RequiredArgsConstructor
public class MpStaffController {

    private final MpStaffService staffService;
    private final WxMaService wxMaService;

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
        String openid;
        if (user != null && StringUtils.hasText(user.getOpenid())) {
            // 已登录用户直接使用 openid（重新申请场景）
            openid = user.getOpenid();
        } else {
            // 未登录新用户：通过 wxCode 换取 openid
            if (!StringUtils.hasText(req.getWxCode())) {
                throw new BusinessException("缺少微信授权码，请重试");
            }
            try {
                WxMaJscode2SessionResult session = wxMaService.jsCode2SessionInfo(req.getWxCode());
                openid = session.getOpenid();
            } catch (Exception e) {
                log.error("wxCode 换取 openid 失败", e);
                throw new BusinessException("微信授权失败，请重试");
            }
        }
        return R.ok(staffService.submitRegistration(openid, req));
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
