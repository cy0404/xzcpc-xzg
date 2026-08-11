package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.BarcodeScanResult;
import com.xzcpc.mp.dto.BarcodeSupplementReq;
import com.xzcpc.mp.service.MpInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * P0 A1: 盘点扫码控制器
 */
@RestController
@RequestMapping("/api/mp/inventory")
@RequiredArgsConstructor
public class MpInventoryController {

    private final MpInventoryService inventoryService;

    @OpLog(module = "小程序-盘点", operation = "扫码识别")
    @GetMapping("/scan/{barcode}")
    public R<BarcodeScanResult> scan(@PathVariable String barcode,
                                      @RequestParam(required = false) Integer taskId,
                                      @RequestParam(required = false) Integer zoneId) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(inventoryService.scan(barcode, taskId, zoneId, storeId));
    }

    @OpLog(module = "小程序-盘点", operation = "条码补充申请")
    @PostMapping("/barcode/supplement")
    public R<Void> supplementBarcode(@RequestBody BarcodeSupplementReq req) {
        var user = UserContextHolder.get();
        inventoryService.submitBarcodeSupplement(req, user.getStoreId(), user.getOpenid());
        return R.ok();
    }
}
