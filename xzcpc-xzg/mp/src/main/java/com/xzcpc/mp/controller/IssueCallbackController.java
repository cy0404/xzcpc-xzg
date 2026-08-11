package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
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
 * P0 B1: 外部系统回调端点（免 JWT，X-Api-Key 鉴权）。
 *
 * 路径 /api/mp/public/** 已在 MpWebMvcConfig 排除登录拦截。
 * 外部 task_platform 表单提交成功后调用此接口，chatId → 门店 → 写 issue 表。
 */
@Slf4j
@RestController
@RequestMapping("/api/mp/public/issue")
@RequiredArgsConstructor
public class IssueCallbackController {

    private final IssueService issueService;

    @Value("${xiangmu.api-key:}")
    private String apiKey;

    @OpLog(module = "小程序-问题处理", operation = "外部回调同步问题")
    @PostMapping("/callback")
    public R<Map<String, Object>> callback(
            @RequestHeader(value = "X-Api-Key", required = false) String reqApiKey,
            @RequestBody Map<String, Object> body) {

        if (!StringUtils.hasText(apiKey) || !apiKey.equals(reqApiKey)) {
            throw new BusinessException(403, "鉴权失败");
        }

        String chatId = str(body.get("chatId"));
        Long externalId = parseLong(body.get("externalId"));
        String issueNo = str(body.get("issueNo"));
        String status = str(body.get("status"));
        String title = str(body.get("title"));
        String storeName = str(body.get("storeName"));
        String issueType = str(body.get("issueType"));
        String severity = str(body.get("impactLevel"));
        String handler = str(body.get("currentOwnerName"));
        String equipment = str(body.get("equipment"));
        String source = str(body.get("source"));

        if (!StringUtils.hasText(chatId)) {
            throw new BusinessException("缺少 chatId");
        }
        if (externalId == null) {
            throw new BusinessException("缺少 externalId");
        }

        log.info("[issue-callback] chatId={} externalId={} issueNo={} status={} title={} source={}", chatId, externalId, issueNo, status, title, source);
        Issue issue = issueService.syncByCallback(chatId, externalId, issueNo, status, title, storeName, issueType, severity, handler, equipment, source);
        if (issue == null) {
            return R.ok(Map.of("id", 0, "bizCode", "NOT_FOUND"));
        }
        return R.ok(Map.of("id", issue.getId(), "bizCode", issue.getBizCode() != null ? issue.getBizCode() : ""));
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    /** 处理记录回调：task_platform 有新记录时推送，存 JSON 到 issue 表 */
    @OpLog(module = "小程序-问题处理", operation = "外部回调处理记录")
    @PostMapping("/callback-records")
    public R<Void> callbackRecords(
            @RequestHeader(value = "X-Api-Key", required = false) String reqApiKey,
            @RequestBody String rawBody) {
        if (!StringUtils.hasText(apiKey) || !apiKey.equals(reqApiKey)) {
            throw new BusinessException(403, "鉴权失败");
        }
        Long externalId = null;
        String recordsJson = rawBody;
        // 1) 先尝试当标准 JSON 解析
        try {
            var node = objectMapper.readTree(rawBody);
            if (node.has("externalId")) {
                externalId = node.get("externalId").asLong();
            }
        } catch (Exception e) {
            // 2) 不是标准 JSON → 尝试把 Java toString 格式 {key=value} 转 JSON
            recordsJson = convertToStringJson(rawBody);
            try {
                var node = objectMapper.readTree(recordsJson);
                if (node.has("externalId")) {
                    externalId = node.get("externalId").asLong();
                }
            } catch (Exception e2) {
                throw new BusinessException("请求体格式错误，无法解析 JSON");
            }
        }
        if (externalId == null) {
            throw new BusinessException("缺少 externalId");
        }
        log.info("[issue-callback-records] externalId={}", externalId);
        issueService.saveRecords(externalId, recordsJson);
        return R.ok();
    }

    /** 把 Java Map toString 格式 {key=value} 转为标准 JSON {"key":"value"} */
    private String convertToStringJson(String raw) {
        // 1) key: 在 { 或 , 后面紧跟的非空白字符序列 → "key"
        // 2) = → :
        // 3) 值保持原样（含嵌套 {} 不处理，让 Jackson 解析）
        String result = raw.trim();
        // key= 改为 "key":
        result = result.replaceAll("([{,]\\s*)([a-zA-Z_][a-zA-Z0-9_]*)=", "$1\"$2\":");
        // 末尾 = 改 :
        result = result.replace("=", ":");
        // 把键名的剩余也加引号（处理中文 key）
        result = result.replaceAll("([{,]\\s*)([^\"\\s,=]+?)=", "$1\"$2\":");
        return result;
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try { return Long.parseLong(String.valueOf(o)); }
        catch (NumberFormatException e) { return null; }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
