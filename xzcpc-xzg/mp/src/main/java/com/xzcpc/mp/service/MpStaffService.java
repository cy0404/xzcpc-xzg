package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.StaffApprovalReq;
import com.xzcpc.mp.dto.StaffRegisterReq;
import com.xzcpc.mp.dto.StaffResignReq;
import com.xzcpc.mp.dto.StaffUpdateReq;

import java.util.List;
import java.util.Map;

public interface MpStaffService {

    Map<String, Object> listStaff(String storeId, String status);

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
}
