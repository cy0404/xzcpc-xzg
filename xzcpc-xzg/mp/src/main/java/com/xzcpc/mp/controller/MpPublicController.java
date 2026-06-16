package com.xzcpc.mp.controller;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.entity.EmployeeRegistrationApplication;
import com.xzcpc.mp.mapper.EmployeeRegistrationApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mp/public")
@RequiredArgsConstructor
public class MpPublicController {

    private final EmployeeRegistrationApplicationMapper applicationMapper;
    private final WxMaService wxMaService;

    /**
     * 公开接口：根据申请 ID 查询登记申请状态，无需登录。
     */
    @GetMapping("/application/{applicationId}")
    public R<Map<String, Object>> applicationStatus(@PathVariable String applicationId) {
        EmployeeRegistrationApplication app = applicationMapper.selectOne(
                new LambdaQueryWrapper<EmployeeRegistrationApplication>()
                        .eq(EmployeeRegistrationApplication::getApplicationId, applicationId)
                        .last("LIMIT 1"));

        if (app == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", false);
            return R.ok(result);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("applicationId", app.getApplicationId());
        result.put("name", app.getName());
        result.put("mobile", app.getMobile());
        result.put("gender", app.getGender());
        result.put("birthday", app.getBirthday() != null ? app.getBirthday().toString() : "");
        result.put("storeName", app.getStoreName());
        result.put("storeId", app.getStoreId());
        result.put("expectedRole", app.getExpectedRole());
        result.put("employmentType", app.getEmploymentType());
        result.put("entryDate", app.getEntryDate() != null ? app.getEntryDate().toString() : "");
        result.put("emergencyContactName", app.getEmergencyContactName());
        result.put("emergencyContactPhone", app.getEmergencyContactPhone());
        result.put("remark", app.getRemark());
        result.put("status", app.getStatus());
        result.put("rejectReason", app.getRejectReason());
        result.put("createdAt", app.getCreatedAt());
        return R.ok(result);
    }

    /**
     * 公开接口：根据微信 code + storeId 检查该用户是否已有登记申请。
     * 员工点邀请链接进来时自动调用，有申请则直接跳状态页，没有才展示表单。
     */
    @PostMapping("/check-application")
    public R<Map<String, Object>> checkApplication(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String storeId = body.get("storeId");

        if (code == null || code.isBlank() || storeId == null || storeId.isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", false);
            return R.ok(result);
        }

        String openid;
        try {
            WxMaJscode2SessionResult sessionResult = wxMaService.jsCode2SessionInfo(code);
            openid = sessionResult.getOpenid();
        } catch (Exception e) {
            log.warn("checkApplication 换取 openid 失败 code={}", code, e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", false);
            return R.ok(result);
        }

        EmployeeRegistrationApplication app = applicationMapper.selectOne(
                new LambdaQueryWrapper<EmployeeRegistrationApplication>()
                        .eq(EmployeeRegistrationApplication::getOpenid, openid)
                        .eq(EmployeeRegistrationApplication::getStoreId, storeId)
                        .orderByDesc(EmployeeRegistrationApplication::getCreatedAt)
                        .last("LIMIT 1"));

        if (app == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", false);
            return R.ok(result);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("applicationId", app.getApplicationId());
        result.put("name", app.getName());
        result.put("storeName", app.getStoreName());
        result.put("status", app.getStatus());
        result.put("rejectReason", app.getRejectReason());
        result.put("createdAt", app.getCreatedAt());
        return R.ok(result);
    }
}
