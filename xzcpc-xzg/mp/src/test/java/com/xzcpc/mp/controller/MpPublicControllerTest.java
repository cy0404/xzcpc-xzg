package com.xzcpc.mp.controller;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.entity.EmployeeRegistrationApplication;
import com.xzcpc.mp.mapper.EmployeeRegistrationApplicationMapper;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("登记前检查 check-application")
class MpPublicControllerTest {

    @Mock private EmployeeRegistrationApplicationMapper applicationMapper;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private WxMaService wxMaService;
    @InjectMocks private MpPublicController controller;

    private static final String OPENID = "oTest456";
    private static final String STORE_ID = "STORE001";

    private Map<String, String> body(String code, String storeId) {
        Map<String, String> b = new LinkedHashMap<>();
        b.put("code", code);
        b.put("storeId", storeId);
        return b;
    }

    private void mockWxCode(String code, String openid) {
        try {
            WxMaJscode2SessionResult r = new WxMaJscode2SessionResult();
            r.setOpenid(openid);
            when(wxMaService.jsCode2SessionInfo(code)).thenReturn(r);
        } catch (Exception ignored) {}
    }

    private Employee newEmployee(String openid, String storeId, String status, LocalDate leaveDate) {
        Employee e = new Employee();
        e.setOpenid(openid);
        e.setStoreId(storeId);
        e.setStatus(status);
        e.setLeaveDate(leaveDate);
        e.setName("王五");
        e.setRole("店员");
        e.setStoreName("测试门店");
        return e;
    }

    private EmployeeRegistrationApplication newApp(String applicationId, String openid, String storeId, String status) {
        EmployeeRegistrationApplication app = new EmployeeRegistrationApplication();
        app.setApplicationId(applicationId);
        app.setOpenid(openid);
        app.setStoreId(storeId);
        app.setStoreName("测试门店");
        app.setName("王五");
        app.setStatus(status);
        return app;
    }

    // ==================== 参数校验 ====================

    @Test
    @DisplayName("code 为空 → found: false")
    void missingCodeShouldReturnNotFound() {
        R<Map<String, Object>> r = controller.checkApplication(body("", STORE_ID));
        assertEquals(200, r.getCode());
        assertEquals(false, r.getData().get("found"));
    }

    @Test
    @DisplayName("storeId 为空 → found: false")
    void missingStoreIdShouldReturnNotFound() {
        R<Map<String, Object>> r = controller.checkApplication(body("valid-code", ""));
        assertEquals(200, r.getCode());
        assertEquals(false, r.getData().get("found"));
    }

    @Test
    @DisplayName("wxCode 换取 openid 失败 → found: false")
    void wxCodeExchangeFailureShouldReturnNotFound() throws Exception {
        when(wxMaService.jsCode2SessionInfo("bad-code"))
                .thenThrow(new RuntimeException("微信服务异常"));

        R<Map<String, Object>> r = controller.checkApplication(body("bad-code", STORE_ID));
        assertEquals(false, r.getData().get("found"));
    }

    // ==================== 无申请记录 ====================

    @Nested
    @DisplayName("无申请记录时")
    class NoApplication {

        @Test
        @DisplayName("无在职员工 → found: false（展示登记表单）")
        void noApplicationNoEmployee() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(false, r.getData().get("found"));
        }

        @Test
        @DisplayName("有在职员工（无 leaveDate）→ status: already_employee")
        void activeEmployeeWithoutLeaveDate() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newEmployee(OPENID, STORE_ID, "在职", null));

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(true, r.getData().get("found"));
            assertEquals("already_employee", r.getData().get("status"));
            assertEquals("王五", r.getData().get("name"));
            assertEquals("店员", r.getData().get("role"));
        }

        @Test
        @DisplayName("有在职员工（leaveDate 未到期）→ status: already_employee")
        void activeEmployeeWithFutureLeaveDate() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newEmployee(OPENID, STORE_ID, "在职", LocalDate.now().plusDays(10)));

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(true, r.getData().get("found"));
            assertEquals("already_employee", r.getData().get("status"));
        }

        @Test
        @DisplayName("在职员工但 leaveDate 已到期 → found: false（允许重新申请）")
        void employeeWithPastLeaveDateShouldAllow() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newEmployee(OPENID, STORE_ID, "在职", LocalDate.now().minusDays(1)));

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(false, r.getData().get("found"));
        }

        @Test
        @DisplayName("员工状态为离职 → found: false（允许重新申请）")
        void resignedEmployeeShouldAllow() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            // 查询 status="在职" → 查不到（状态是"离职"）
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(false, r.getData().get("found"));
        }
    }

    // ==================== 有申请记录 ====================

    @Nested
    @DisplayName("有申请记录时")
    class WithApplication {

        @Test
        @DisplayName("pending 申请 → 返回申请状态")
        void pendingApplication() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newApp("STA00000001", OPENID, STORE_ID, "pending"));

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(true, r.getData().get("found"));
            assertEquals("pending", r.getData().get("status"));
            assertEquals("STA00000001", r.getData().get("applicationId"));
        }

        @Test
        @DisplayName("rejected 申请 → 返回申请状态（前端显示重新申请按钮）")
        void rejectedApplication() {
            mockWxCode("ok", OPENID);
            EmployeeRegistrationApplication app = newApp("STA00000001", OPENID, STORE_ID, "rejected");
            app.setRejectReason("信息不全");
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(true, r.getData().get("found"));
            assertEquals("rejected", r.getData().get("status"));
            assertEquals("信息不全", r.getData().get("rejectReason"));
        }

        @Test
        @DisplayName("approved + 员工在职（无 leaveDate）→ 返回 approved")
        void approvedWithActiveEmployee() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newApp("STA00000001", OPENID, STORE_ID, "approved"));
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newEmployee(OPENID, STORE_ID, "在职", null));

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(true, r.getData().get("found"));
            assertEquals("approved", r.getData().get("status"));
        }

        @Test
        @DisplayName("approved 但员工 leaveDate 已到期 → found: false（离职员工允许重新申请）")
        void approvedButLeaveDatePassed() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newApp("STA00000001", OPENID, STORE_ID, "approved"));
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newEmployee(OPENID, STORE_ID, "在职", LocalDate.now().minusDays(1)));

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(false, r.getData().get("found"));
        }

        @Test
        @DisplayName("approved 但员工已离职（无在职记录）→ found: false")
        void approvedButEmployeeResigned() {
            mockWxCode("ok", OPENID);
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(newApp("STA00000001", OPENID, STORE_ID, "approved"));
            // 查在职员工 → null（已离职）
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            R<Map<String, Object>> r = controller.checkApplication(body("ok", STORE_ID));

            assertEquals(false, r.getData().get("found"));
        }
    }
}
