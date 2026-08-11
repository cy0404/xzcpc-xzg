package com.xzcpc.interceptor;

import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.mapper.AdminPermissionMapper;
import com.xzcpc.entity.AdminPermission;
import com.xzcpc.util.AdminJwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * JWT 只验证身份，角色每次从 DB 实时查询，权限变更即时生效。
 */
@Component
@RequiredArgsConstructor
public class AdminLoginInterceptor implements HandlerInterceptor {

    private final AdminJwtUtil adminJwtUtil;
    private final AdminPermissionMapper adminPermissionMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

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

            // 角色从 DB 实时查，不依赖 JWT 中缓存的角色
            String role = "";
            try {
                AdminPermission perm = adminPermissionMapper.selectOne(
                        new LambdaQueryWrapper<AdminPermission>().eq(AdminPermission::getOpenId, openId));
                if (perm != null) role = perm.getRole() != null ? perm.getRole() : "";
                if (name == null && perm != null) name = perm.getName();
            } catch (Exception ignored) {}

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
