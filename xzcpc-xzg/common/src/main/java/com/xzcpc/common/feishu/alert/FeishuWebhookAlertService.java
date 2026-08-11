package com.xzcpc.common.feishu.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞书 Webhook 告警服务。
 * <p>
 * 通过自定义机器人 Webhook 将异常信息异步推送到飞书群。
 * 发送失败仅记录 warn 日志，不影响主业务流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuWebhookAlertService {

    private final FeishuAlertProperties alertProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 堆栈最大行数 */
    private static final int MAX_STACK_LINES = 24;
    /** 堆栈最大字符数 */
    private static final int MAX_STACK_CHARS = 1600;
    /** 异常消息最大字符数 */
    private static final int MAX_MESSAGE_CHARS = 200;

    /**
     * 异步推送未处理异常告警到飞书群。
     *
     * @param ctx 告警上下文（请求信息 + 环境）
     * @param e   未处理异常
     */
    @Async("alertTaskExecutor")
    public void notify(FeishuAlertContext ctx, Throwable e) {
        // 开关检查
        if (!alertProperties.isEnabled()) {
            return;
        }
        if (alertProperties.getWebhookUrl() == null || alertProperties.getWebhookUrl().isBlank()) {
            return;
        }

        try {
            String text = buildText(ctx, e);
            String json = buildRequestBody(text);
            sendToFeishu(json);
            log.info("飞书异常告警已发送: {} {} → {}",
                    ctx.getHttpMethod(), ctx.getRequestPath(), e.getClass().getSimpleName());
        } catch (Exception ex) {
            log.warn("飞书异常告警发送失败: {}", ex.getMessage());
        }
    }

    /**
     * 构建告警消息文本。
     */
    String buildText(FeishuAlertContext ctx, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(ctx.getAppName()).append(" 服务端异常】\n");
        sb.append("应用: ").append(ctx.getAppName()).append("\n");
        sb.append("环境: ").append(ctx.getEnvironment()).append("\n");
        sb.append("时间: ").append(LocalDateTime.now().format(TIME_FMT)).append("\n");
        sb.append("请求: ").append(ctx.getHttpMethod()).append(" ").append(ctx.getRequestPath());
        if (ctx.getQueryString() != null && !ctx.getQueryString().isBlank()) {
            sb.append("?").append(ctx.getQueryString());
        }
        sb.append("\n");
        sb.append("来源: ").append(nullToEmpty(ctx.getRemoteAddr())).append("\n");
        if (ctx.getUserHint() != null && !ctx.getUserHint().isBlank()) {
            sb.append("用户: ").append(ctx.getUserHint()).append("\n");
        }
        sb.append("异常: ").append(e.getClass().getName());
        if (e.getMessage() != null) {
            String msg = e.getMessage();
            if (msg.length() > MAX_MESSAGE_CHARS) {
                msg = msg.substring(0, MAX_MESSAGE_CHARS) + "...";
            }
            sb.append(" — ").append(msg);
        }
        sb.append("\n");

        // 堆栈摘要
        sb.append("【堆栈摘要】\n");
        sb.append(formatStackTrace(e));

        return sb.toString();
    }

    /**
     * 格式化堆栈信息，截断过长的堆栈。
     */
    private String formatStackTrace(Throwable e) {
        StackTraceElement[] elements = e.getStackTrace();
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName());
        if (e.getMessage() != null) {
            sb.append(": ").append(e.getMessage());
        }
        sb.append("\n");

        int totalLines = elements.length;
        int printLines = Math.min(totalLines, MAX_STACK_LINES);
        for (int i = 0; i < printLines; i++) {
            sb.append("    at ").append(elements[i].toString()).append("\n");
        }
        if (totalLines > MAX_STACK_LINES) {
            sb.append("    ... (共 ").append(totalLines).append(" 行，已省略 ")
                    .append(totalLines - MAX_STACK_LINES).append(" 行)\n");
        }

        String result = sb.toString();
        if (result.length() > MAX_STACK_CHARS) {
            result = result.substring(0, MAX_STACK_CHARS) + "\n...(stack truncated)";
        }
        return result;
    }

    /**
     * 构造飞书自定义机器人 text 消息 JSON。
     */
    private String buildRequestBody(String text) throws JsonProcessingException {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("text", text);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "text");
        body.put("content", content);
        return objectMapper.writeValueAsString(body);
    }

    /**
     * 发送 HTTP POST 到飞书 Webhook。
     */
    private void sendToFeishu(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        restTemplate.postForEntity(alertProperties.getWebhookUrl(), entity, String.class);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
