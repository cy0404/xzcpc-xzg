package com.xzcpc.mp.controller;

import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.entity.Issue;
import com.xzcpc.mp.service.IssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * P0 B1: 象目经理状态回传 webhook 脚手架。
 *
 * 路径落在 /api/mp/public/** 下，已在 MpWebMvcConfig 中排除登录拦截（外部回调无 JWT）。
 * 用配置的固定 token 做简单校验；真实 API 接入后按对方签名方案替换校验逻辑。
 *
 * 报文（示例，接入后按对方格式调整）：
 * { "token": "xxx", "bizCode": "ISS...", "status": "processing", "processResult": "已联系维修" }
 */
@Slf4j
@RestController
@RequestMapping("/api/mp/public/xiangmu")
@RequiredArgsConstructor
public class XiangmuCallbackController {

    private final IssueService issueService;

    @Value("${xiangmu.callback-token:}")
    private String callbackToken;

    @PostMapping("/callback")
    public R<Void> callback(@RequestBody Map<String, Object> body) {
        // 简单 token 校验（未配置 token 时拒绝，避免误开放）
        String token = str(body.get("token"));
        if (!StringUtils.hasText(callbackToken) || !callbackToken.equals(token)) {
            throw new BusinessException(403, "回调校验失败");
        }
        Long id = parseId(body.get("id"));
        String bizCode = str(body.get("bizCode"));
        String status = str(body.get("status"));
        String processResult = str(body.get("processResult"));
        String xiangmuId = str(body.get("xiangmuId"));

        Issue issue = resolveIssue(id, bizCode);
        if (issue == null) {
            throw new BusinessException("问题不存在: " + (id != null ? id : bizCode));
        }
        if (StringUtils.hasText(xiangmuId) && !StringUtils.hasText(issue.getXiangmuId())) {
            // 回传象目经理单号（首次）
            issue.setXiangmuId(xiangmuId);
        }
        issueService.applyExternalStatus(issue.getId(), status, processResult);
        log.info("[象目经理-回调] issue={} status={} ", issue.getBizCode(), status);
        return R.ok();
    }

    private Issue resolveIssue(Long id, String bizCode) {
        if (id != null) {
            return issueService.detailForAdmin(id);
        }
        // 仅按 id 精确定位；bizCode 定位可在接入真实 API 时按需扩展
        return null;
    }

    private Long parseId(Object o) {
        if (o == null) return null;
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
