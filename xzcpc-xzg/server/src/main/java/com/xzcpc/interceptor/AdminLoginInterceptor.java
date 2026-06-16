package com.xzcpc.interceptor;

import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.util.AdminJwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 总部端管理员登录拦截器。
 * 从 Authorization header 取 Bearer token，校验 JWT，写入 AdminContextHolder。
 */
@Component
@RequiredArgsConstructor
public class AdminLoginInterceptor implements HandlerInterceptor {

    private final AdminJwtUtil adminJwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OPTIONS 预检放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = adminJwtUtil.validate(token);
            String openId = claims.getSubject();
            String name = claims.get("name", String.class);
            String role = claims.get("role", String.class);

            AdminUser user = new AdminUser(openId, name, null, null, null, role);
            AdminContextHolder.set(user);
            return true;
        } catch (JwtException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AdminContextHolder.clear();
    }
}
