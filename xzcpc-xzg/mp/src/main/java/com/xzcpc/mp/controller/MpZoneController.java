package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.AddZoneReq;
import com.xzcpc.mp.dto.ItemSaveReq;
import com.xzcpc.mp.dto.SaveZoneReq;
import com.xzcpc.mp.dto.SortReq;
import com.xzcpc.mp.service.MpZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mp/tasks/{taskId}/zones")
@RequiredArgsConstructor
public class MpZoneController {

    private final MpZoneService zoneService;

    @OpLog(module = "小程序-分区", operation = "查询物料")
    @GetMapping("/{zoneId}/materials")
    public R<Object> getMaterials(@PathVariable Integer taskId, @PathVariable Integer zoneId) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(zoneService.getMaterials(taskId, zoneId, storeId));
    }

    @OpLog(module = "小程序-分区", operation = "批量保存")
    @PostMapping("/{zoneId}/save")
    public R<Void> saveZone(@PathVariable Integer taskId, @PathVariable Integer zoneId,
                            @Valid @RequestBody SaveZoneReq req) {
        String storeId = UserContextHolder.get().getStoreId();
        zoneService.saveZone(taskId, zoneId, req, storeId);
        return R.ok();
    }

    @OpLog(module = "小程序-分区", operation = "单条保存")
    @PostMapping("/{zoneId}/item-save")
    public R<Void> itemSave(@PathVariable Integer taskId, @PathVariable Integer zoneId,
                            @RequestBody ItemSaveReq req) {
        String storeId = UserContextHolder.get().getStoreId();
        zoneService.itemSave(taskId, zoneId, req, storeId);
        return R.ok();
    }

    @OpLog(module = "小程序-分区", operation = "新增分区")
    @PostMapping
    public R<Object> addZone(@PathVariable Integer taskId, @Valid @RequestBody AddZoneReq req) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(zoneService.addZone(taskId, req, storeId));
    }

    @OpLog(module = "小程序-分区", operation = "删除分区")
    @DeleteMapping("/{zoneId}")
    public R<Void> deleteZone(@PathVariable Integer taskId, @PathVariable Integer zoneId) {
        String storeId = UserContextHolder.get().getStoreId();
        zoneService.deleteZone(taskId, zoneId, storeId);
        return R.ok();
    }

    @OpLog(module = "小程序-分区", operation = "排序")
    @PutMapping("/sort")
    public R<Void> sortZones(@PathVariable Integer taskId, @RequestBody SortReq req) {
        String storeId = UserContextHolder.get().getStoreId();
        zoneService.sortZones(taskId, req, storeId);
        return R.ok();
    }

    @OpLog(module = "小程序-分区", operation = "编辑分区名")
    @PutMapping("/{zoneId}/name")
    public R<Void> updateZoneName(@PathVariable Integer taskId, @PathVariable Integer zoneId,
                                   @RequestBody Map<String, String> body) {
        String storeId = UserContextHolder.get().getStoreId();
        zoneService.updateZoneName(taskId, zoneId, body.get("zoneName"), storeId);
        return R.ok();
    }
}
