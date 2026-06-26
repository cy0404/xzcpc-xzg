package com.xzcpc.task.service;

import com.xzcpc.common.model.StoreInfo;
import java.util.List;
import java.util.Map;

public interface StoreService {

    List<StoreInfo> getAllStores();

    StoreInfo getStoreById(String id);

    Map<String, StoreInfo> getStoreMap();

    /**
     * 手动刷新门店数据，从外部 API 重新拉取全量数据并写入 store_info 表。
     */
    void refreshCache();

    /**
     * 更新门店二维码（本地维护字段，不随外部同步覆盖）。
     */
    void updateQrCode(String storeId, String qrCode);

    /**
     * 更新门店老板绑定信息（openid/姓名/手机号）。
     */
    void updateOwnerInfo(String storeId, String openid, String name, String phone);

    /**
     * 更新门店老板绑定信息（复用已加载的 Store 对象，避免重复查询）。
     */
    void updateOwnerInfo(com.xzcpc.task.entity.Store store, String openid, String name, String phone);
}
