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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

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

    // multipart 解析失败（超限 / 客户端中断 / 请求体损坏）：统一 400，客户端可重试，不推飞书
    // MaxUploadSizeExceededException 是 MultipartException 的子类，Spring 会优先命中本 handler
    @ExceptionHandler(MultipartException.class)
    public R<Void> handleMultipartException(MultipartException e, HttpServletRequest request) {
        if (isSizeExceeded(e)) {
            log.warn("上传文件超过大小限制 - {} {}", request.getMethod(), request.getRequestURI());
            return R.fail(400, "文件大小超过限制");
        }
        if (isClientDisconnect(e)) {
            log.warn("客户端断开连接(上传中断) - {} {}", request.getMethod(), request.getRequestURI());
            return R.fail(400, "上传数据不完整，请重试");
        }
        log.warn("multipart 解析失败 - {} {} → {}: {}",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
        return R.fail(400, "上传数据不完整，请重试");
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

    // 请求体参数校验失败（@NotBlank/@NotNull/@Valid 等），客户端问题：返回 400，不推飞书
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 - {} {} → {}", request.getMethod(), request.getRequestURI(), msg);
        return R.fail(400, org.springframework.util.StringUtils.hasText(msg) ? msg : "参数校验失败");
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
        if (isClientDisconnect(e)) {
            log.warn("客户端断开连接 - {} {}", request.getMethod(), request.getRequestURI());
            return R.fail("系统错误，请稍后重试");
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
     * 异常链是否为上传大小超限。
     * 覆盖 Spring 的 MaxUploadSizeExceededException 与 Tomcat maxPostSize 超限
     * 抛出的 IllegalStateException("...maxPostSize exceeded...")。
     */
    private static boolean isSizeExceeded(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof MaxUploadSizeExceededException) return true;
            if (t instanceof IllegalStateException && t.getMessage() != null
                    && t.getMessage().toLowerCase().contains("maxpostsize")) return true;
            t = t.getCause();
        }
        return false;
    }

    /**
     * 异常链是否为客户端断开连接（移动网络波动/用户取消/上传超时等不可控 IO 中断）。
     * 用类名字符串判断 ClientAbortException，避免 common 模块编译期硬依赖 tomcat 类。
     */
    private static boolean isClientDisconnect(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String cls = t.getClass().getSimpleName();
            if ("ClientAbortException".equals(cls) || "EOFException".equals(cls)) return true;
            if (t instanceof java.io.IOException && t.getMessage() != null) {
                String m = t.getMessage().toLowerCase();
                if (m.contains("connection reset") || m.contains("broken pipe")
                        || m.contains("unexpected eof") || m.contains("eof read")
                        || m.contains("forcibly closed") || m.contains("closed by remote")
                        || m.contains("stream closed") || m.contains("aborted by the software")
                        || m.contains("断开的管道") || m.contains("中止了一个已建立的连接")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
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
