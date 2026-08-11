package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.mp.dto.TransferCreateReq;
import com.xzcpc.mp.entity.TransferOrder;
import com.xzcpc.mp.entity.TransferOrderItem;

import java.util.List;
import java.util.Map;

public interface TransferOrderService {
    TransferOrder create(TransferCreateReq req);
    TransferOrder confirm(Long id);
    TransferOrder ship(Long id);
    TransferOrder receive(Long id);
    TransferOrder cancel(Long id);
    TransferOrder reject(Long id);
    Page<TransferOrder> pageByStore(String storeId, String status, int pageNum, int pageSize);
    Page<TransferOrder> pageByStores(String openid, String status, int pageNum, int pageSize);
    TransferOrder detail(Long id, String storeId);
    List<TransferOrderItem> getItems(Long transferId);
    Map<Long, List<TransferOrderItem>> getItemsBatch(List<Long> transferIds);
    Map<String, Long> overview(String storeId);
    long overviewActionRequired(String storeId);
    List<Map<String, Object>> overviewByStores(String openid);
    Map<String, Long> overviewTotal(String openid);
    Page<TransferOrder> pageAll(String fromStoreId, String toStoreId, String supervisorName, String status, String handoff,
                                String keyword, String startDate, String endDate,
                                int pageNum, int pageSize);
}
