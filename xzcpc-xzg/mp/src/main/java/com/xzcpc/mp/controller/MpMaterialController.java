package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.AddMaterialReq;
import com.xzcpc.mp.dto.SortReq;
import com.xzcpc.mp.service.MpMaterialService;
import com.xzcpc.template.dto.MaterialRuleResp;
import com.xzcpc.template.service.MaterialRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mp/tasks/{taskId}/zones/{zoneId}")
@RequiredArgsConstructor
public class MpMaterialController {

    private final MpMaterialService materialService;
    private final MaterialRuleService materialRuleService;

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

    @OpLog(module = "小程序-物料", operation = "扫码查询")
    @GetMapping("/materials/scan")
    public R<Object> scanMaterial(@PathVariable Integer taskId, @PathVariable Integer zoneId,
                                  @RequestParam String qmCode) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(materialService.lookupByQmCode(taskId, zoneId, qmCode.trim(), storeId));
    }

    @GetMapping("/materials/scan-rule")
    public R<MaterialRuleResp> scanRule(@RequestParam String materialId) {
        return R.ok(materialRuleService.detail(materialId));
    }
}

@RestController
@RequestMapping("/api/mp/tasks/{taskId}")
@RequiredArgsConstructor
class MpTaskMaterialController {

    private final MpMaterialService materialService;

    @OpLog(module = "小程序-任务", operation = "跨分区搜索物料")
    @GetMapping("/materials/search")
    public R<Object> searchMaterial(@PathVariable Integer taskId, @RequestParam String keyword) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(materialService.searchAcrossZones(taskId, keyword, storeId));
    }
}
