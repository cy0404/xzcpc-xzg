package com.xzcpc.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.service.SupervisorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 督导-门店关系同步触发（新门店接口 xinfo）。
 * 首次使用流程：先不传 apply（dry-run 出对账报告）→ 人工确认报告 → apply=true 执行写库。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/supervisor-sync")
@RequiredArgsConstructor
public class SupervisorSyncController {

    private final SupervisorSyncService supervisorSyncService;

    /** 手动触发一次同步；默认 dry-run 只出报告，?apply=true 才写库 */
    @OpLog(module = "督导同步", operation = "手动触发督导门店关系同步")
    @PostMapping("/trigger")
    public R<Map<String, Object>> trigger(@RequestParam(defaultValue = "false") boolean apply) {
        Map<String, Object> report = supervisorSyncService.sync(apply);
        return R.ok(report);
    }
}
