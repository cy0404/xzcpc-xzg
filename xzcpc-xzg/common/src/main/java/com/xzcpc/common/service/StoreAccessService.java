package com.xzcpc.common.service;

import com.xzcpc.common.context.AdminUser;

import java.util.List;
import java.util.Map;

/**
 * 门店访问权限服务。
 * 用于督导角色的门店级数据过滤。
 */
public interface StoreAccessService {

    /**
     * 获取指定用户可访问的门店 ID 列表。
     * 从 supervisor_store_access 表查询。
     */
    List<String> getAccessibleStoreIds(String openId);

    /**
     * 判断用户是否仅具有督导角色（无全量数据访问权限）。
     */
    boolean isSupervisorOnly(AdminUser admin);

    /**
     * 根据督导姓名获取其可访问的门店 ID 列表。
     * 用于总部管理员按督导筛选数据。
     */
    List<String> getAccessibleStoreIdsBySupervisorName(String supervisorName);

    /**
     * 批量获取门店的督导姓名。
     * 返回 storeId → supervisorName 映射。
     */
    Map<String, String> getSupervisorNamesByStoreIds(java.util.Collection<String> storeIds);
}
