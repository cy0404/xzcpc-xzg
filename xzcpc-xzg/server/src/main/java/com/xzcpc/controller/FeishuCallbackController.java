package com.xzcpc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.xzcpc.mp.entity.Issue;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.service.IssueService;
import com.xzcpc.mp.service.LossReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 飞书卡片回调：处理到货报损的确认/拒绝按钮 + 未验收问题的已解决按钮
 */
@Slf4j
@RestController
@RequestMapping("/api/feishu")
@RequiredArgsConstructor
public class FeishuCallbackController {

    private final LossReportService lossReportService;
    private final IssueService issueService;

    /** 卡片回调：URL 验证 + 事件处理 */
    @PostMapping("/loss-callback")
    public Object lossCallback(@RequestBody JsonNode body) {
        log.info("飞书回调：{}", body.toPrettyString());

        String type = body.has("type") ? body.get("type").asText() : "";
        // 飞书 URL 验证
        if ("url_verification".equals(type)) {
            String challenge = body.get("challenge").asText();
            return Map.of("challenge", challenge);
        }

        // 卡片交互事件
        try {
            JsonNode event = body.path("event");
            JsonNode action = event.path("action");

            // value 两种格式：新按钮 JSON 对象 {"action":"xxx","issue_id":1}；旧报损按钮字符串 "register:123"
            String act = "";
            long targetId = 0;
            JsonNode vNode = action.path("value");
            if (vNode != null && vNode.isObject()) {
                act = vNode.path("action").asText("");
                targetId = vNode.path("issue_id").asLong(0);
            } else {
                String rawValue = vNode.asText("");
                if (!rawValue.isEmpty()) {
                    String[] parts = rawValue.split(":", 2);
                    act = parts[0];
                    targetId = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
                }
            }
            if (act.isEmpty()) return Map.of("code", 0);

            // ===== 未验收问题：已解决 =====
            if ("issue_resolve".equals(act)) {
                Issue i = issueService.acceptFromFeishu(targetId);
                return Map.of("toast", Map.of("type", "success", "content", "已验收，问题已关闭"));
            }

            // ===== 到货报损：确认/拒绝 =====
            long reportId = targetId;
            if (reportId == 0) return Map.of("code", 0);

            LossReport r = lossReportService.getById(reportId);
            if (r == null) return Map.of("code", 0, "msg", "报损记录不存在");

            if ("register".equals(act)) {
                r.setStatus("registered");
                r.setConfirmedAt(LocalDateTime.now());
                r.setUpdatedAt(LocalDateTime.now());
                lossReportService.updateById(r);
                log.info("报损确认登记：id={}", reportId);
                return Map.of("toast", Map.of("type", "success", "content", "已确认报损登记"));
            }

            if ("confirm".equals(act)) {
                r.setStatus("confirmed_resend");
                r.setConfirmedAt(LocalDateTime.now());
                r.setUpdatedAt(LocalDateTime.now());
                lossReportService.updateById(r);
                log.info("报损确认补发：id={}", reportId);
                return Map.of("toast", Map.of("type", "success", "content", "已确认补发"));
            }

            if ("reject".equals(act)) {
                r.setStatus("rejected");
                r.setRejectReason("");
                r.setUpdatedAt(LocalDateTime.now());
                lossReportService.updateById(r);
                log.info("报损已拒绝：id={}", reportId);
                return Map.of("toast", Map.of("type", "success", "content", "已拒绝"));
            }
        } catch (Exception e) {
            log.error("飞书回调处理失败", e);
            return Map.of("toast", Map.of("type", "error", "content",
                    e.getMessage() != null ? e.getMessage() : "操作失败"));
        }
        return Map.of("code", 0);
    }
}
