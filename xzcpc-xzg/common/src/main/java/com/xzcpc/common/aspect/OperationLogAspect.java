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
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
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

    private static final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();
    private static final ExpressionParser spelParser = new SpelExpressionParser();

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
            entity.setDescription(buildDescription(annotation.desc(), method, joinPoint.getArgs()));
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
            fillUserInfo(entity);

            operationLogService.save(entity);
        } catch (Exception ex) {
            log.error("保存操作日志失败", ex);
        }
    }

    /**
     * 构建描述：如果 desc 包含 SpEL 表达式（#{...}），则解析并替换变量。
     * 方法参数可通过 #paramName 引用（需编译时保留参数名，或用 #a0/#a1 按位置引用）。
     * 纯文本则直接返回。
     */
    private String buildDescription(String desc, Method method, Object[] args) {
        if (desc == null || desc.isEmpty()) return "";
        if (!desc.contains("#")) return desc; // 不含 SpEL → 直接返回

        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            // 按参数名注册
            String[] names = paramDiscoverer.getParameterNames(method);
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    ctx.setVariable(names[i], args[i]);
                }
            }
            // 按位置注册，兼容没有参数名的情况
            for (int i = 0; i < args.length; i++) {
                ctx.setVariable("a" + i, args[i]);
                ctx.setVariable("p" + i, args[i]);
            }

            // 使用 SpEL 模板解析：先找 #{...} 片段并替换
            StringBuilder result = new StringBuilder();
            int idx = 0;
            while (idx < desc.length()) {
                int start = desc.indexOf("#{", idx);
                if (start == -1) { result.append(desc, idx, desc.length()); break; }
                result.append(desc, idx, start);
                int end = desc.indexOf("}", start + 2);
                if (end == -1) { result.append(desc, start, desc.length()); break; }
                String expr = desc.substring(start + 2, end);
                try {
                    Expression exp = spelParser.parseExpression(expr);
                    Object val = exp.getValue(ctx);
                    result.append(val != null ? val.toString() : "");
                } catch (Exception ex) {
                    result.append("?").append(expr).append("?");
                }
                idx = end + 1;
            }
            return result.toString();
        } catch (Exception e) {
            log.warn("解析操作日志描述失败: {}", desc, e);
            return desc;
        }
    }

    // 缓存类查找结果，避免每次请求都 Class.forName 抛异常（仅检查一次）
    private volatile Boolean adminCtxAvailable;
    private volatile Boolean userCtxAvailable;

    /**
     * 填充 user_id 和 username。
     * 总部端：userId = 飞书 open_id，username = 飞书姓名
     * 小程序端：userId = 微信 openid，username = 员工姓名
     */
    private void fillUserInfo(OperationLog entity) {
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
                    Object openId = adminUser.getClass().getMethod("getOpenId").invoke(adminUser);
                    Object name = adminUser.getClass().getMethod("getName").invoke(adminUser);
                    entity.setUserId(openId != null ? openId.toString() : "");
                    entity.setSource("admin");
                    entity.setUsername(name != null ? name.toString() : "");
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // 2. 小程序端
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
                    Object openid = user.getClass().getMethod("getOpenid").invoke(user);
                    Object empName = user.getClass().getMethod("getEmployeeName").invoke(user);
                    entity.setUserId(openid != null ? openid.toString() : "");
                    entity.setSource("mp");
                    entity.setUsername(empName != null ? empName.toString() : "");
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        entity.setUserId("");
        entity.setSource("");
        entity.setUsername("anonymous");
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
