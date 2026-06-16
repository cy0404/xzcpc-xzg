package com.xzcpc.common.aspect;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.entity.OperationLog;
import com.xzcpc.common.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 操作日志切面：拦截 @OpLog 注解的方法，异步写入日志表。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    @AfterReturning("@annotation(com.xzcpc.common.annotation.OpLog)")
    public void afterReturning(JoinPoint joinPoint) {
        saveLog(joinPoint, null);
    }

    @AfterThrowing(pointcut = "@annotation(com.xzcpc.common.annotation.OpLog)", throwing = "e")
    public void afterThrowing(JoinPoint joinPoint, Exception e) {
        saveLog(joinPoint, e);
    }

    private void saveLog(JoinPoint joinPoint, Exception e) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            OpLog annotation = method.getAnnotation(OpLog.class);

            OperationLog entity = new OperationLog();
            entity.setModule(annotation.module());
            entity.setOperation(annotation.operation());
            entity.setDescription(annotation.desc());
            entity.setStatus(e == null ? 1 : 0);
            if (e != null) {
                String errMsg = e.getMessage();
                if (errMsg != null && errMsg.length() > 1000) {
                    errMsg = errMsg.substring(0, 1000);
                }
                entity.setErrorMsg(errMsg);
            }

            // 获取请求信息
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entity.setRequestIp(getClientIp(request));
            }

            // 获取当前用户
            entity.setUsername(getCurrentUsername());

            operationLogService.save(entity);
        } catch (Exception ex) {
            log.error("保存操作日志失败", ex);
        }
    }

    // 缓存类查找结果，避免每次请求都 Class.forName 抛异常（仅检查一次）
    private volatile Boolean adminCtxAvailable;
    private volatile Boolean userCtxAvailable;

    /**
     * 获取当前登录用户标识。
     * 优先级：总部端 AdminContextHolder > 小程序端 UserContextHolder > "anonymous"
     */
    private String getCurrentUsername() {
        // 1. 总部端飞书管理员
        if (adminCtxAvailable == null) {
            try {
                Class.forName("com.xzcpc.common.context.AdminContextHolder");
                adminCtxAvailable = true;
            } catch (ClassNotFoundException e) {
                adminCtxAvailable = false;
            }
        }
        if (adminCtxAvailable) {
            try {
                Class<?> adminCtx = Class.forName("com.xzcpc.common.context.AdminContextHolder");
                Object adminUser = adminCtx.getMethod("get").invoke(null);
                if (adminUser != null) {
                    Object name = adminUser.getClass().getMethod("getName").invoke(adminUser);
                    if (name != null && !name.toString().isEmpty()) {
                        return name.toString();
                    }
                }
            } catch (Exception ignored) {
                // reflection failed
            }
        }

        // 2. 小程序端店长
        if (userCtxAvailable == null) {
            try {
                Class.forName("com.xzcpc.mp.context.UserContextHolder");
                userCtxAvailable = true;
            } catch (ClassNotFoundException e) {
                userCtxAvailable = false;
            }
        }
        if (userCtxAvailable) {
            try {
                Class<?> ctxClass = Class.forName("com.xzcpc.mp.context.UserContextHolder");
                Object user = ctxClass.getMethod("get").invoke(null);
                if (user != null) {
                    Object storeId = user.getClass().getMethod("getStoreId").invoke(user);
                    if (storeId != null && !storeId.toString().isEmpty()) {
                        return storeId.toString();
                    }
                    Object openid = user.getClass().getMethod("getOpenid").invoke(user);
                    if (openid != null && !openid.toString().isEmpty()) {
                        return openid.toString();
                    }
                }
            } catch (Exception ignored) {
                // reflection failed
            }
        }
        return "anonymous";
    }

    /**
     * 获取客户端真实 IP。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
