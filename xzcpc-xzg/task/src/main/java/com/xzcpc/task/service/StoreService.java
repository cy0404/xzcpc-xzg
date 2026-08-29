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
     * 批量更新门店订货周期配置（store_order_cycle 表）：
     * body = {storeId: {orderDays: "1,4"|null, paused: 0|1}}。
     * orderDays 为 null/空表示清空配置（不参与周盘）；orderDays 非空则 upsert 并保存 paused。
     */
    void updateOrderCycle(Map<String, Map<String, Object>> storeIdToConfig);

    /**
     * 更新门店老板绑定信息（openid/姓名/手机号）。
     */
    void updateOwnerInfo(String storeId, String openid, String name, String phone);

    /**
     * 更新门店老板绑定信息（复用已加载的 Store 对象，避免重复查询）。
     */
    void updateOwnerInfo(com.xzcpc.task.entity.Store store, String openid, String name, String phone);

    /**
     * 获取门店的外部问题表单系统标识（chat_id），无则返回 null。
     */
    String getChatId(String storeId);

    /**
     * 根据 chat_id 反查门店信息，无则返回 null。
     */
    com.xzcpc.task.entity.Store getStoreByChatId(String chatId);

    /** 新增门店（手动插入，非外部同步） */
    void save(com.xzcpc.task.entity.Store store);
}
