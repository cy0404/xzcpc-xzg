package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.BarcodeScanResult;
import com.xzcpc.mp.dto.BarcodeSupplementReq;

/**
 * P0 A1: 盘点扫码服务
 */
public interface MpInventoryService {

    /**
     * 扫码识别物料
     * @param barcode 条码（qm_code 或 qr_code）
     * @param taskId 当前任务 ID（可选，用于检查分区归属和录入状态）
     * @param zoneId 当前分区 ID（可选）
     * @param storeId 门店 ID
     * @return 物料扫描结果
     */
    BarcodeScanResult scan(String barcode, Integer taskId, Integer zoneId, String storeId);

    /**
     * 条码补充申请
     */
    void submitBarcodeSupplement(BarcodeSupplementReq req, String storeId, String openid);
}
