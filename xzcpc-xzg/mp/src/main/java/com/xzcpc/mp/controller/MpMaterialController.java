package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.AddMaterialReq;
import com.xzcpc.mp.dto.SortReq;
import com.xzcpc.mp.service.MpMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mp/tasks/{taskId}/zones/{zoneId}")
@RequiredArgsConstructor
public class MpMaterialController {

    private final MpMaterialService materialService;

    @OpLog(module = "小程序-物料", operation = "搜索候选")
    @GetMapping("/candidates")
    public R<Object> getCandidates(@PathVariable Integer taskId, @PathVariable Integer zoneId,
                                   @RequestParam(defaultValue = "") String keyword) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(materialService.getCandidates(taskId, zoneId, keyword, storeId));
    }

    @OpLog(module = "小程序-物料", operation = "添加物料")
    @PostMapping("/materials")
    public R<Void> addMaterial(@PathVariable Integer taskId, @PathVariable Integer zoneId,
                               @RequestBody AddMaterialReq req) {
        String storeId = UserContextHolder.get().getStoreId();
        materialService.addMaterial(taskId, zoneId, req, storeId);
        return R.ok();
    }

    @OpLog(module = "小程序-物料", operation = "移除物料")
    @DeleteMapping("/materials/{materialId}")
    public R<Void> removeMaterial(@PathVariable Integer taskId, @PathVariable Integer zoneId,
                                  @PathVariable String materialId) {
        String storeId = UserContextHolder.get().getStoreId();
        materialService.removeMaterial(taskId, zoneId, materialId, storeId);
        return R.ok();
    }

    @OpLog(module = "小程序-物料", operation = "排序")
    @PutMapping("/materials/sort")
    public R<Void> sortMaterials(@PathVariable Integer taskId, @PathVariable Integer zoneId,
                                 @RequestBody SortReq req) {
        String storeId = UserContextHolder.get().getStoreId();
        materialService.sortMaterials(taskId, zoneId, req, storeId);
        return R.ok();
    }
}
