package com.xzcpc.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.task.dto.TaskCreateRequest;
import com.xzcpc.task.entity.Task;
import com.xzcpc.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController { // 月盘任务控制器

    private final TaskService taskService;

    @OpLog(module = "任务", operation = "查询列表")
    @GetMapping
    public R<Page<Task>> list(@RequestParam(defaultValue = "") String storeId,
                               @RequestParam(defaultValue = "") String status,
                               @RequestParam(defaultValue = "") String keyword,
                               @RequestParam(defaultValue = "") String templateName,
                               @RequestParam(defaultValue = "") String taskMonth,
                               @RequestParam(defaultValue = "1") int pageNum,
                               @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(taskService.page(storeId, status, keyword, templateName, taskMonth, pageNum, pageSize));
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
}
