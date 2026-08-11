package com.xzcpc.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.entity.Issue;
import com.xzcpc.mp.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * P0 B1: 过渡期手动推进问题状态/处理结果。
 *
 * 仅在未接入象目经理外部系统时启用（xiangmu.enabled=false，默认），
 * 供联调门店验收流程。真实 API 接入后（xiangmu.enabled=true）该端点自动失效，
 * 状态由象目经理 webhook 回调驱动。
 */
@RestController
@RequestMapping("/api/admin/issue")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "xiangmu.enabled", havingValue = "false", matchIfMissing = true)
public class IssueInterimSyncController {

    private final IssueService issueService;

    @PostMapping("/{id}/sync-status")
    public R<Issue> syncStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String processResult = body.get("processResult");
        return R.ok(issueService.applyExternalStatus(id, status, processResult));
    }
}
