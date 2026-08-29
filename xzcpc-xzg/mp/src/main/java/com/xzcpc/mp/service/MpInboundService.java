package com.xzcpc.mp.service;

import com.xzcpc.mp.entity.InboundOrder;
import com.xzcpc.mp.entity.InboundOrderItem;

import java.util.List;
import java.util.Map;

public interface MpInboundService {

    /**
     * 从企迈 OpenAPI 同步入库单到本地（9.2.2 批量查询入库单）。
     * 仅同步 1仓配入库 / 3采购入库，通过 bizNo 匹配报货单归属当前门店。
     * 已存在的单按 inbound_no upsert，只更新企迈侧字段，本地收货字段不动。
     *
     * @param qmaiStoreId 企迈门店ID（用于拉报货单做归属匹配）
     * @param days        同步时间窗口（近 N 天）：定时任务 7，下拉刷新 2
     * @return 本次同步新增的入库单数量
     */
    int syncFromQmai(String storeId, Long qmaiStoreId, int days);

    /**
     * 入库单列表（分页），支持 keyword 搜索和 status 筛选。
     */
    Map<String, Object> list(String storeId, String keyword, String status, int pageNum, int pageSize);

    /**
     * 入库单详情（含商品明细）。
     */
    Map<String, Object> detail(Long id, String storeId);

    /**
     * 一键入库：整单所有商品标记为已收货。
     */
    InboundOrder quickInbound(Long id, String storeId, String receivedBy);

    /**
     * 单件商品收货确认。
     *
     * @param qty      实收数量，为 null 时默认按报货数量全额收货
     * @param operator 操作人（同步企迈 9.2.3 用）
     */
    InboundOrderItem receiveItem(Long orderId, Long itemId, String storeId,
                                 java.math.BigDecimal qty, String operator);

    /**
     * 批量收货：勾选多件物料，全部按报货数量全额收货（单事务）。
     * 已收货或不属于该单的物料自动跳过。
     *
     * @return 本次实际收货的物料数量
     */
    int batchReceive(Long orderId, List<Long> itemIds, String storeId, String receivedBy);

    /**
     * 获取入库单的商品明细列表。
     */
    List<InboundOrderItem> getItems(Long orderId);
}
