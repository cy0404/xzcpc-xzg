package com.xzcpc.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.task.dto.MaterialUpdateReq;
import com.xzcpc.task.dto.TaskCreateRequest;
import com.xzcpc.task.dto.TaskUpdateRequest;
import com.xzcpc.task.entity.Task;
import com.xzcpc.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController { // 月盘任务控制器

    private final TaskService taskService;

    @GetMapping("/latest-month")
    public R<String> latestMonth() {
        return R.ok(taskService.getLatestMonth());
    }

    @OpLog(module = "任务", operation = "查询列表")
    @GetMapping
    public R<Page<Task>> list(@RequestParam(defaultValue = "") String storeIds,
                               @RequestParam(defaultValue = "") String storeId,
                               @RequestParam(defaultValue = "") String supervisorName,
                               @RequestParam(defaultValue = "") String status,
                               @RequestParam(defaultValue = "") String keyword,
                               @RequestParam(defaultValue = "") String templateName,
                               @RequestParam(defaultValue = "") String taskMonth,
                               @RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "10") int pageSize) {
        String ids = StringUtils.hasText(storeIds) ? storeIds : storeId;
        return R.ok(taskService.page(ids, supervisorName, status, keyword, templateName, taskMonth, pageNum, pageSize));
    }

    @OpLog(module = "任务", operation = "创建")
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody TaskCreateRequest request) { // 创建月盘任务（支持多门店）
        int count = taskService.batchCreate(request);
        Map<String, Object> result = Map.of("count", count);
        return R.ok(result);
    }

    @OpLog(module = "任务", operation = "查询详情")
    @GetMapping("/{id}")
    public R<Task> detail(@PathVariable Integer id) { // 根据 ID 获取任务详情
        return R.ok(taskService.detail(id));
    }

    @OpLog(module = "任务", operation = "查看结果")
    @GetMapping("/{id}/result")
    public R<Map<String, Object>> result(@PathVariable Integer id) { // 获取任务盘点结果
        return R.ok(taskService.getResult(id));
    }

    @OpLog(module = "任务", operation = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Integer id) { // 删除任务（仅未开始状态）
        taskService.delete(id);
        return R.ok();
    }

    @OpLog(module = "任务", operation = "编辑基本信息")
    @PutMapping("/{id}")
    public R<Task> update(@PathVariable Integer id, @RequestBody TaskUpdateRequest req) {
        taskService.update(id, req);
        return R.ok(taskService.detail(id));
    }

    @OpLog(module = "任务", operation = "编辑物料数量",
           desc = "修改任务#{#id}的#{#materials.size()}个物料数量")
    @PutMapping("/{id}/materials")
    public R<Void> updateMaterials(@PathVariable Integer id, @RequestBody List<MaterialUpdateReq> materials) {
        taskService.updateMaterials(id, materials);
        return R.ok();
    }

    @OpLog(module = "任务", operation = "删除物料",
           desc = "删除任务#{#id}的物料#{#materialId}")
    @DeleteMapping("/{id}/materials/{materialId}")
    public R<Void> deleteMaterial(@PathVariable Integer id, @PathVariable Integer materialId) {
        taskService.deleteMaterial(id, materialId);
        return R.ok();
    }

    @OpLog(module = "任务", operation = "汇总编辑",
           desc = "修改任务#{#id}物料#{#materialId}总量为#{#body['totalQty']}")
    @PutMapping("/{id}/materials/{materialId}/total")
    public R<Void> setMaterialTotal(@PathVariable Integer id, @PathVariable String materialId,
                                    @RequestBody Map<String, Object> body) {
        BigDecimal totalQty = body.get("totalQty") != null
                ? new BigDecimal(body.get("totalQty").toString()) : BigDecimal.ZERO;
        taskService.setMaterialTotal(id, materialId, totalQty);
        return R.ok();
    }
}
