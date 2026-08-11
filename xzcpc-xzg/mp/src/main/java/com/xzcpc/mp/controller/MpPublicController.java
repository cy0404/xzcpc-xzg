package com.xzcpc.mp.controller;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.entity.EmployeeRegistrationApplication;
import com.xzcpc.mp.entity.OwnerBindPending;
import com.xzcpc.mp.mapper.EmployeeRegistrationApplicationMapper;
import com.xzcpc.mp.mapper.OwnerBindPendingMapper;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mp/public")
@RequiredArgsConstructor
public class MpPublicController {

    private final EmployeeRegistrationApplicationMapper applicationMapper;
    private final EmployeeMapper employeeMapper;
    private final WxMaService wxMaService;
    private final StoreService storeService;
    private final com.xzcpc.task.mapper.StoreMapper storeMapper;
    private final MpStaffService staffService;
    private final OwnerBindPendingMapper pendingMapper;

    /**
     * 查询登记申请状态。
     * - 无 wxCode → 仅返回 found / status / rejectReason（公开安全信息）
     * - 有 wxCode 且 openid 匹配 → 返回完整信息（重新申请时回填表单用）
     */
    @GetMapping("/application/{applicationId}")
    public R<Map<String, Object>> applicationStatus(@PathVariable String applicationId,
                                                    @RequestParam(required = false) String wxCode) {
        EmployeeRegistrationApplication app = applicationMapper.selectOne(
                new LambdaQueryWrapper<EmployeeRegistrationApplication>()
                        .eq(EmployeeRegistrationApplication::getApplicationId, applicationId)
                        .last("LIMIT 1"));

        if (app == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", false);
            return R.ok(result);
        }

        boolean isOwner = false;
        if (wxCode != null && !wxCode.isBlank()) {
            try {
                WxMaJscode2SessionResult sessionResult = wxMaService.jsCode2SessionInfo(wxCode);
                isOwner = sessionResult.getOpenid().equals(app.getOpenid());
            } catch (Exception e) {
                log.warn("applicationStatus 换取 openid 失败", e);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("status", app.getStatus());
        result.put("rejectReason", app.getRejectReason());

        if (isOwner) {
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
            result.put("createdAt", app.getCreatedAt());
        }
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
            Employee existingEmployee = employeeMapper.selectOne(
                    new LambdaQueryWrapper<Employee>()
                            .eq(Employee::getOpenid, openid)
                            .eq(Employee::getStoreId, storeId)
                            .eq(Employee::getStatus, "在职")
                            .last("LIMIT 1"));
            if (existingEmployee != null
                    && (existingEmployee.getLeaveDate() == null || existingEmployee.getLeaveDate().isAfter(LocalDate.now()))) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("found", true);
                result.put("status", "already_employee");
                result.put("name", existingEmployee.getName());
                result.put("storeName", existingEmployee.getStoreName());
                result.put("role", existingEmployee.getRole());
                return R.ok(result);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", false);
            return R.ok(result);
        }

        if ("approved".equals(app.getStatus())) {
            Employee employee = employeeMapper.selectOne(
                    new LambdaQueryWrapper<Employee>()
                            .eq(Employee::getOpenid, openid)
                            .eq(Employee::getStoreId, storeId)
                            .eq(Employee::getStatus, "在职")
                            .last("LIMIT 1"));
            if (employee == null || (employee.getLeaveDate() != null && !employee.getLeaveDate().isAfter(LocalDate.now()))) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("found", false);
                return R.ok(result);
            }
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

    /**
     * 扫码后存 openid，同时返回回填信息。
     * POST /api/mp/public/save-openid
     * 入参: { storeId, openid }
     * 返回: { openid, alreadyOwner, name, mobile, fromStoreId, fromStoreName }
     * alreadyOwner=true 时会直接创建新门店的员工记录，平台无需回调
     */
    @PostMapping("/save-openid")
    public R<Map<String, Object>> saveOpenid(@RequestBody Map<String, String> body) {
        String storeId = body.get("storeId");
        String openid = body.get("openid");

        if (!StringUtils.hasText(storeId) || !StringUtils.hasText(openid)) {
            throw new RuntimeException("storeId 和 openid 不能为空");
        }

        // 查该 openid 是否已是在职老板
        Employee existingOwner = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getOpenid, openid)
                        .eq(Employee::getRole, "老板")
                        .eq(Employee::getStatus, "在职")
                        .last("LIMIT 1"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openid", openid);

        // 写入待处理表
        OwnerBindPending pending = pendingMapper.selectOne(
                new LambdaQueryWrapper<OwnerBindPending>()
                        .eq(OwnerBindPending::getStoreId, storeId));
        if (pending != null) {
            pending.setOpenid(openid);
            pending.setUpdatedAt(LocalDateTime.now());
            pendingMapper.updateById(pending);
        } else {
            pending = new OwnerBindPending();
            pending.setStoreId(storeId);
            pending.setOpenid(openid);
            pending.setStatus("pending");
            pendingMapper.insert(pending);
        }

        if (existingOwner != null) {
            // 已是老板：取最早的门店 ID 告知平台
            Employee firstOwner = employeeMapper.selectList(
                    new LambdaQueryWrapper<Employee>()
                            .eq(Employee::getOpenid, openid)
                            .eq(Employee::getRole, "老板")
                            .eq(Employee::getStatus, "在职")
                            .orderByAsc(Employee::getId))
                    .stream().findFirst().orElse(existingOwner);

            result.put("alreadyOwner", true);
            result.put("fromStoreId", firstOwner.getStoreId());
        } else {
            // 新老板：查历史信息回填
            OwnerBindPending prev = pendingMapper.selectOne(
                    new LambdaQueryWrapper<OwnerBindPending>()
                            .eq(OwnerBindPending::getOpenid, openid)
                            .ne(OwnerBindPending::getStoreId, storeId)
                            .orderByDesc(OwnerBindPending::getUpdatedAt)
                            .last("LIMIT 1"));

            result.put("alreadyOwner", false);
            if (prev != null) {
                result.put("name", prev.getName() != null ? prev.getName() : "");
                result.put("mobile", prev.getMobile() != null ? prev.getMobile() : "");
            }
        }
        return R.ok(result);
    }

    /**
     * 公开接口：外部平台审核通过后回调，从 pending 表取 openid 创建老板员工。
     * POST /api/mp/public/owner-callback
     * 入参: { storeId, storeName, name, mobile }
     */
    @PostMapping("/owner-callback")
    public R<Map<String, Object>> ownerCallback(@RequestBody Map<String, String> body) {
        String storeId = body.get("storeId");
        String storeName = body.get("storeName");
        String name = body.get("name");
        String mobile = body.get("mobile");

        if (!StringUtils.hasText(storeId)) {
            throw new RuntimeException("storeId 不能为空");
        }

        // 从 pending 表取 openid
        OwnerBindPending pending = pendingMapper.selectOne(
                new LambdaQueryWrapper<OwnerBindPending>()
                        .eq(OwnerBindPending::getStoreId, storeId));
        if (pending == null || !StringUtils.hasText(pending.getOpenid())) {
            throw new RuntimeException("该门店尚未扫描二维码，请确认老板已扫码");
        }
        String openid = pending.getOpenid();

        // 更新 pending 表的 name/mobile
        pending.setName(StringUtils.hasText(name) ? name : null);
        pending.setMobile(StringUtils.hasText(mobile) ? mobile : null);
        pending.setStatus("completed");
        pending.setUpdatedAt(LocalDateTime.now());
        pendingMapper.updateById(pending);

        // 查或建门店
        Store store = storeMapper.selectOne(
                new LambdaQueryWrapper<Store>()
                        .eq(Store::getStoreId, storeId));
        if (store == null) {
            store = new Store();
            store.setStoreId(storeId);
            store.setStoreName(StringUtils.hasText(storeName) ? storeName : "");
            storeMapper.insert(store);
        }

        // 更新该 openid 下所有在职老板记录的姓名手机号
        staffService.updateOwnerNameAndMobile(openid, name, mobile);
        // 若当前门店还没有员工记录，新建
        staffService.createOrUpdateOwner(storeId,
                StringUtils.hasText(storeName) ? storeName : store.getStoreName(),
                openid, name, mobile);

        // 更新门店老板信息
        storeService.updateOwnerInfo(storeId, openid, name, mobile);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storeId", storeId);
        result.put("storeName", store.getStoreName());
        result.put("message", "老板信息已同步");
        return R.ok(result);
    }

}
