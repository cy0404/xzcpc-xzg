package com.xzcpc.mp.service;

import java.util.List;
import java.util.Map;

public interface OwnerRegisterService {
    /** 获取所有门店列表（供扫码页选择） */
    List<Map<String, Object>> listStores();
    /** 提交管理员登记 */
    List<Map<String, Object>> submit(String code, String name, String phone, String role, String bindCode, List<String> storeIds);
}
