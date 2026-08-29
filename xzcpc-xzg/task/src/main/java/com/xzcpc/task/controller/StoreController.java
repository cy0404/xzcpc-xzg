package com.xzcpc.task.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.common.response.R;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @OpLog(module = "门店", operation = "查询")
    @GetMapping
    public R<List<StoreInfo>> list() {
        return R.ok(storeService.getAllStores());
    }

    @OpLog(module = "门店", operation = "查询详情")
    @GetMapping("/{id}")
    public R<StoreInfo> detail(@PathVariable String id) {
        StoreInfo store = storeService.getStoreById(id);
        if (store == null) {
            return R.fail(404, "门店不存在");
        }
        return R.ok(store);
    }

    @OpLog(module = "门店", operation = "更新二维码")
    @PutMapping("/{id}/qr-code")
    public R<Void> updateQrCode(@PathVariable String id, @RequestBody Map<String, String> body) {
        storeService.updateQrCode(id, body.getOrDefault("qrCode", ""));
        return R.ok();
    }

    @OpLog(module = "门店", operation = "更新订货周期配置")
    @PutMapping("/order-cycle")
    public R<Void> updateOrderCycle(@RequestBody Map<String, Map<String, Object>> body) {
        // body: {storeId: {orderDays: "1,4"|null, paused: 0|1}}，
        // orderDays 为空表示清空（不参与周盘），非空则 upsert 并保存暂停开关
        storeService.updateOrderCycle(body);
        return R.ok();
    }
}
