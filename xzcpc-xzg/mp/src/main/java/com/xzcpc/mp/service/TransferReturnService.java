package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.TransferReturnReq;

public interface TransferReturnService {
    /**
     * 执行还货/还钱操作
     */
    void doReturn(Long transferId, String storeId, String employeeName, TransferReturnReq req);

    /**
     * 调出门店确认还货完成
     */
    void confirmReturn(Long transferId, String storeId);
}
