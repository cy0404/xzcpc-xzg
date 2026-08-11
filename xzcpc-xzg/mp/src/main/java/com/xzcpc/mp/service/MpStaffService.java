package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.StaffApprovalReq;
import com.xzcpc.mp.dto.StaffRegisterReq;
import com.xzcpc.mp.dto.StaffResignReq;
import com.xzcpc.mp.dto.StaffUpdateReq;

import java.util.List;
import java.util.Map;

public interface MpStaffService {

    Map<String, Object> listStaff(String storeId, String status);

    List<Map<String, Object>> listAllStoresStaff(String openid, String status);

    Map<String, Object> staffDetail(String storeId, String employeeId);

    Map<String, Object> updateStaff(String storeId, String employeeId, StaffUpdateReq req);

    Map<String, Object> submitRegistration(String openid, StaffRegisterReq req);

    List<Map<String, Object>> listApplications(String storeId, String status);

    Map<String, Object> applicationDetail(String storeId, String applicationId);

    Map<String, Object> approve(String storeId, String approverOpenid, String applicationId, StaffApprovalReq req);

    Map<String, Object> currentStaffProfile(String openid, String storeId);

    /**
     * 根据 openid 查找该微信用户关联的所有在职门店。
     * 返回列表，每项包含 storeId / storeName / employeeId / employeeName / role / permissions。
     */
    List<Map<String, Object>> findStoresByOpenid(String openid);

    Map<String, Object> resign(String storeId, String employeeId, StaffResignReq req);

    /** 按门店统计待审批申请数（仅返回 pending>0 的门店） */
    List<Map<String, Object>> overviewByStores(String openid);

    /** 外部平台回调：创建或更新老板员工记录 */
    void createOrUpdateOwner(String storeId, String storeName, String openid, String name, String mobile);

    /** 更新该 openid 下所有在职老板的姓名和手机号 */
    void updateOwnerNameAndMobile(String openid, String name, String mobile);
}
