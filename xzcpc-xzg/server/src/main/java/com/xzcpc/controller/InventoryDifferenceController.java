package com.xzcpc.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.task.service.DifferenceCalcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 盘点差异处理（总部端）
 * 差异列表按任务（盘点单）维度展示，支持差异计算明细和数量修改
 */
@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class InventoryDifferenceController {

    private final DifferenceCalcService diffCalcService;

    // ==================== 差异任务列表 ====================

    /** 盘点差异任务列表（按任务分组） */
    @GetMapping("/diff-tasks")
    public R<Map<String, Object>> listDiffTasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String storeIds,
            @RequestParam(required = false) String supervisorName) {
        return R.ok(diffCalcService.listDiffTasks(pageNum, pageSize, storeIds, supervisorName));
    }

    /** 某任务的差异明细（含任务信息和所有差异项） */
    @GetMapping("/diff-tasks/{taskId}")
    public R<Map<String, Object>> getDiffDetail(@PathVariable Integer taskId) {
        return R.ok(diffCalcService.getDiffDetail(taskId));
    }

    /** 触发差异计算 */
    @PostMapping("/diff-tasks/{taskId}/calculate")
    public R<Map<String, Object>> calculate(@PathVariable Integer taskId) {
        int count = diffCalcService.calculateAndSaveDifferences(taskId);
        return R.ok(Map.of("count", count, "msg", "差异计算完成，共 " + count + " 条差异项"));
    }

    /** 批量计算所有未计算的差异 */
    @PostMapping("/diff-tasks/batch-calculate")
    public R<Map<String, Object>> batchCalculate() {
        int count = diffCalcService.batchCalculateUncounted();
        return R.ok(Map.of("count", count, "msg", "批量计算完成，共计算 " + count + " 个任务"));
    }

    /** 按物料维度聚合差异（跨任务/跨门店），返回材料汇总+可用月份 */
    @GetMapping("/diff-materials")
    public R<Map<String, Object>> listDiffMaterials(
            @RequestParam(required = false) String taskMonth,
            @RequestParam(required = false) String storeIds,
            @RequestParam(required = false) String supervisorName) {
        return R.ok(diffCalcService.listDiffMaterials(taskMonth, storeIds, supervisorName));
    }

    // ==================== 差异处理 ====================

    /** 修改 adjusted_qty 并重算差异 */
    @PutMapping("/differences/{id}/adjust")
    public R<Void> adjustQty(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal newQty = new BigDecimal(body.get("adjustedQty").toString());
        String operator = body.getOrDefault("operator", "admin").toString();
        diffCalcService.modifyAdjustedQty(id, newQty, operator);
        return R.ok();
    }

    // ==================== 阈值配置 ====================

    /** 获取差异阈值 */
    @GetMapping("/diff-config")
    public R<Map<String, Object>> getConfig() {
        double rate = diffCalcService.getThresholdRate();
        return R.ok(Map.of("thresholdRate", rate, "thresholdPercent", Math.round(rate * 100) + "%"));
    }

    /** 更新差异阈值，同时重算所有差异的 is_large 标记 */
    @PutMapping("/diff-config")
    public R<Void> updateConfig(@RequestBody Map<String, Object> body) {
        double rate = Double.parseDouble(body.get("thresholdRate").toString());
        diffCalcService.updateThresholdRate(rate);
        diffCalcService.recalcIsLarge(rate);
        return R.ok();
    }
}
