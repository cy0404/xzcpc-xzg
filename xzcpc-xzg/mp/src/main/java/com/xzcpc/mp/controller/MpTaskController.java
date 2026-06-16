package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.service.MpTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mp/tasks")
@RequiredArgsConstructor
public class MpTaskController {

    private final MpTaskService taskService;

    @OpLog(module = "小程序-任务", operation = "查询列表")
    @GetMapping
    public R<Object> list() {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(taskService.list(storeId));
    }

    @OpLog(module = "小程序-任务", operation = "查询详情")
    @GetMapping("/{id}")
    public R<Object> detail(@PathVariable Integer id) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(taskService.detail(id, storeId));
    }

    @OpLog(module = "小程序-任务", operation = "提交任务")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Integer id) {
        String storeId = UserContextHolder.get().getStoreId();
        String openid = UserContextHolder.get().getOpenid();
        taskService.submit(id, storeId, openid);
        return R.ok();
    }

    @OpLog(module = "小程序-任务", operation = "查看汇总")
    @GetMapping("/{id}/summary")
    public R<Object> summary(@PathVariable Integer id) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(taskService.summary(id, storeId));
    }

    @OpLog(module = "小程序-任务", operation = "查看结果")
    @GetMapping("/{id}/result")
    public R<Object> result(@PathVariable Integer id) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(taskService.result(id, storeId));
    }
}
