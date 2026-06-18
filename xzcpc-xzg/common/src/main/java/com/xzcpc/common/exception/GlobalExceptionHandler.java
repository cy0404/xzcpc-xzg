package com.xzcpc.common.exception;

import com.xzcpc.common.response.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 全局异常处理器，捕获业务异常和未预期异常并返回统一响应

    // 处理 BusinessException，返回业务错误码和提示信息
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        if (e.getCode() >= 500) {
            log.error("业务异常 - {} {} → code={}, message={}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage(), e);
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

    // 处理未预期的 Exception，返回通用系统错误提示
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 - {} {} → {}: {}",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
        log.error("堆栈:", e);
        return R.fail("系统错误，请稍后重试");
    }
}
