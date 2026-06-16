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
}
