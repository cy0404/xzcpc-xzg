package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.mp.dto.StaffRegisterReq;
import com.xzcpc.mp.entity.EmployeeRegistrationApplication;
import com.xzcpc.mp.mapper.EmployeeRegistrationApplicationMapper;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import com.xzcpc.task.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("员工登记 submitRegistration")
class MpStaffServiceImplTest {

    @Mock private EmployeeMapper employeeMapper;
    @Mock private EmployeeRegistrationApplicationMapper applicationMapper;
    @Mock private StoreService storeService;
    @InjectMocks private MpStaffServiceImpl staffService;

    private static final String OPENID = "oTest123";
    private static final String STORE_ID = "STORE001";

    private StaffRegisterReq newReq() {
        StaffRegisterReq req = new StaffRegisterReq();
        req.setStoreId(STORE_ID);
        req.setName("张三");
        req.setMobile("13800138000");
        req.setExpectedRole("店员");
        req.setEmploymentType("全职");
        req.setEntryDate(LocalDate.now());
        return req;
    }

    private StoreInfo newStore() {
        StoreInfo s = new StoreInfo();
        s.setId(STORE_ID);
        s.setMendianmingcheng("测试门店");
        return s;
    }

    @BeforeEach
    void setUp() {
        lenient().when(storeService.getStoreById(STORE_ID)).thenReturn(newStore());
    }

    // ==================== 门店校验 ====================

    @Test
    @DisplayName("门店不存在 → 抛异常")
    void storeNotFoundShouldThrow() {
        when(storeService.getStoreById(STORE_ID)).thenReturn(null);

        StaffRegisterReq req = newReq();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> staffService.submitRegistration(OPENID, req));
        assertEquals("门店不存在", ex.getMessage());
    }

    // ==================== 重新申请路径 ====================

    @Nested
    @DisplayName("重新申请（带 applicationId）")
    class Reapply {

        @Test
        @DisplayName("申请记录不存在 → 抛异常")
        void applicationNotFoundShouldThrow() {
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            StaffRegisterReq req = newReq();
            req.setApplicationId("STA00000001");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> staffService.submitRegistration(OPENID, req));
            assertEquals("申请记录不存在", ex.getMessage());
        }

        @Test
        @DisplayName("申请状态为 approved → 不可重新提交")
        void approvedApplicationShouldThrow() {
            EmployeeRegistrationApplication app = new EmployeeRegistrationApplication();
            app.setApplicationId("STA00000001");
            app.setOpenid(OPENID);
            app.setStatus("approved");
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

            StaffRegisterReq req = newReq();
            req.setApplicationId("STA00000001");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> staffService.submitRegistration(OPENID, req));
            assertEquals("仅可重新提交被驳回的申请", ex.getMessage());
        }

        @Test
        @DisplayName("申请状态为 pending → 不可重新提交")
        void pendingApplicationShouldThrow() {
            EmployeeRegistrationApplication app = new EmployeeRegistrationApplication();
            app.setApplicationId("STA00000001");
            app.setOpenid(OPENID);
            app.setStatus("pending");
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

            StaffRegisterReq req = newReq();
            req.setApplicationId("STA00000001");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> staffService.submitRegistration(OPENID, req));
            assertEquals("仅可重新提交被驳回的申请", ex.getMessage());
        }

        @Test
        @DisplayName("申请状态为 rejected → 成功更新并重置为 pending")
        void rejectedApplicationShouldUpdateSuccessfully() {
            EmployeeRegistrationApplication app = new EmployeeRegistrationApplication();
            app.setId(100L);
            app.setApplicationId("STA00000100");
            app.setOpenid(OPENID);
            app.setStoreId(STORE_ID);
            app.setStoreName("测试门店");
            app.setStatus("rejected");
            app.setRejectReason("信息不完整");
            when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

            StaffRegisterReq req = newReq();
            req.setApplicationId("STA00000100");

            Map<String, Object> result = staffService.submitRegistration(OPENID, req);

            // 验证更新操作
            verify(applicationMapper).updateById(app);
            assertEquals("pending", app.getStatus());
            assertNull(app.getRejectReason());
            assertEquals("STA00000100", result.get("applicationId"));
            assertEquals("pending", result.get("status"));
        }
    }

    // ==================== 新申请路径 ====================

    @Nested
    @DisplayName("新申请（无 applicationId）")
    class NewApplication {

        @Test
        @DisplayName("已是该门店在职员工（无 leaveDate）→ 抛异常")
        void existingActiveEmployeeShouldThrow() {
            Employee emp = new Employee();
            emp.setId(1L);
            emp.setOpenid(OPENID);
            emp.setStoreId(STORE_ID);
            emp.setStatus("在职");
            emp.setLeaveDate(null);
            emp.setName("李四");
            emp.setRole("店长");
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(emp);

            StaffRegisterReq req = newReq();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> staffService.submitRegistration(OPENID, req));
            assertEquals("您已是该门店在职员工，无需重复登记", ex.getMessage());
        }

        @Test
        @DisplayName("已是该门店在职员工（leaveDate 未到期）→ 抛异常")
        void existingEmployeeWithFutureLeaveDateShouldThrow() {
            Employee emp = new Employee();
            emp.setId(1L);
            emp.setOpenid(OPENID);
            emp.setStoreId(STORE_ID);
            emp.setStatus("在职");
            emp.setLeaveDate(LocalDate.now().plusDays(30));
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(emp);

            StaffRegisterReq req = newReq();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> staffService.submitRegistration(OPENID, req));
            assertEquals("您已是该门店在职员工，无需重复登记", ex.getMessage());
        }

        @Test
        @DisplayName("在职员工 leaveDate 已到期 → 视为已离职，允许重新申请")
        void employeeWithPastLeaveDateShouldAllow() {
            // employeeMapper 查"在职" → 找到（状态仍为在职但 leaveDate 已过）
            Employee emp = new Employee();
            emp.setId(1L);
            emp.setOpenid(OPENID);
            emp.setStoreId(STORE_ID);
            emp.setStatus("在职");
            emp.setLeaveDate(LocalDate.now().minusDays(1)); // 昨天到期
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(emp);
            // insert 模拟自增 ID
            doAnswer(inv -> {
                EmployeeRegistrationApplication a = inv.getArgument(0);
                a.setId(200L);
                return 1;
            }).when(applicationMapper).insert(any(EmployeeRegistrationApplication.class));

            StaffRegisterReq req = newReq();

            Map<String, Object> result = staffService.submitRegistration(OPENID, req);

            assertNotNull(result.get("applicationId"));
            assertEquals("pending", result.get("status"));
            verify(applicationMapper).insert(any(EmployeeRegistrationApplication.class));
        }

        @Test
        @DisplayName("无在职记录（离职员工）→ 允许重新申请")
        void noActiveEmployeeShouldAllow() {
            // employeeMapper 查不到在职记录
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            doAnswer(inv -> {
                EmployeeRegistrationApplication a = inv.getArgument(0);
                a.setId(200L);
                return 1;
            }).when(applicationMapper).insert(any(EmployeeRegistrationApplication.class));

            StaffRegisterReq req = newReq();

            Map<String, Object> result = staffService.submitRegistration(OPENID, req);

            assertEquals("STA00000200", result.get("applicationId"));
            assertEquals("pending", result.get("status"));
            assertEquals("张三", result.get("name"));
            verify(applicationMapper).insert(any(EmployeeRegistrationApplication.class));
        }

        @Test
        @DisplayName("首次登记（无申请、无员工）→ 成功创建")
        void firstTimeRegistrationShouldSucceed() {
            // employeeMapper 查不到任何记录
            when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            doAnswer(inv -> {
                EmployeeRegistrationApplication a = inv.getArgument(0);
                a.setId(1L);
                return 1;
            }).when(applicationMapper).insert(any(EmployeeRegistrationApplication.class));

            StaffRegisterReq req = newReq();

            Map<String, Object> result = staffService.submitRegistration(OPENID, req);

            assertEquals("STA00000001", result.get("applicationId"));
            assertEquals("pending", result.get("status"));
            assertEquals("张三", result.get("name"));
            assertEquals("测试门店", result.get("storeName"));
            verify(applicationMapper).insert(any(EmployeeRegistrationApplication.class));
            verify(applicationMapper).updateById(any(EmployeeRegistrationApplication.class));
        }
    }
}
