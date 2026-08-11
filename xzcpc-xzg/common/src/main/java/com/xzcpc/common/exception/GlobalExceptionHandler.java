package com.xzcpc.common.exception;

import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.feishu.alert.FeishuAlertContext;
import com.xzcpc.common.feishu.alert.FeishuAlertProperties;
import com.xzcpc.common.feishu.alert.FeishuWebhookAlertService;
import com.xzcpc.common.response.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private FeishuWebhookAlertService feishuWebhookAlertService;

    @Autowired
    private FeishuAlertProperties feishuAlertProperties;

    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    // 处理 BusinessException，code>=500 的服务端错误异步推送飞书告警
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        if (e.getCode() >= 500) {
            log.error("业务异常 - {} {} → code={}, message={}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage(), e);
            // 异步推送飞书群告警
            try {
                FeishuAlertContext ctx = captureContext(request);
                feishuWebhookAlertService.notify(ctx, e);
            } catch (Exception ex) {
                log.warn("构建飞书告警上下文失败: {}", ex.getMessage());
            }
        } else {
            log.warn("业务异常 - {} {} → code={}, message={}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        }
        return R.fail(e.getCode(), e.getMessage());
    }

    // 静态资源不存在（如 favicon.ico），静默返回 404
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e, HttpServletResponse response) {
        response.setStatus(404);
        return R.fail(404, "资源不存在");
    }

    // URL 参数类型转换异常（如 "null" 字符串无法转 Integer），返回 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型转换失败: {}", e.getMessage());
        return R.fail(400, "参数格式错误");
    }

    // 请求方法不匹配（如 GET 访问 POST 接口），记录 URL 方便排查
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        log.warn("请求方法不支持: {} {} (支持: {})",
                request.getMethod(), request.getRequestURI(), e.getSupportedHttpMethods());
        response.setStatus(405);
        return R.fail(405, "请求方法不支持");
    }

    // 处理未预期的 Exception，返回通用系统错误提示，并异步推送飞书告警
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        // 客户端断开连接（视频流中断等），降级为 WARN，不推送飞书
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.io.IOException &&
                ("Connection reset by peer".equals(cause.getMessage()) ||
                 "断开的管道".equals(cause.getMessage()) ||
                 "你的主机中的软件中止了一个已建立的连接。".equals(cause.getMessage()))) {
                log.warn("客户端断开连接 - {} {}", request.getMethod(), request.getRequestURI());
                return R.fail("系统错误，请稍后重试");
            }
            cause = cause.getCause();
        }

        log.error("系统异常 - {} {} → {}: {}",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
        log.error("堆栈:", e);

        // 异步推送飞书群告警
        try {
            FeishuAlertContext ctx = captureContext(request);
            feishuWebhookAlertService.notify(ctx, e);
        } catch (Exception ex) {
            log.warn("构建飞书告警上下文失败: {}", ex.getMessage());
        }

        return R.fail("系统错误，请稍后重试");
    }

    /**
     * 从 HTTP 请求和 ThreadLocal 上下文中提取告警所需信息。
     */
    private FeishuAlertContext captureContext(HttpServletRequest request) {
        String userHint = null;
        try {
            AdminUser adminUser = AdminContextHolder.get();
            if (adminUser != null) {
                userHint = adminUser.getName();
                if (adminUser.getOpenId() != null) {
                    userHint += " (" + adminUser.getOpenId() + ")";
                }
            }
        } catch (Exception ignored) {
            // AdminContextHolder 不可用时忽略（如小程序端）
        }

        return FeishuAlertContext.builder()
                .httpMethod(request.getMethod())
                .requestPath(request.getRequestURI())
                .queryString(request.getQueryString())
                .remoteAddr(request.getRemoteAddr())
                .userHint(userHint)
                .appName(feishuAlertProperties.getAppName())
                .environment(activeProfile)
                .build();
    }
}
