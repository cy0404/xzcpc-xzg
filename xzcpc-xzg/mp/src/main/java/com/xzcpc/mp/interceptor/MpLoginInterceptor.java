package com.xzcpc.mp.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.config.JwtProperties;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.entity.StoreManagerSession;
import com.xzcpc.mp.mapper.StoreManagerSessionMapper;
import com.xzcpc.mp.util.JwtUtil;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class MpLoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StoreManagerSessionMapper sessionMapper;
    private final EmployeeMapper employeeMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token) || !token.startsWith("Bearer ")) {
            throw new BusinessException(401, "未登录或Token已失效");
        }
        token = token.substring(7);

        try {
            Claims claims = jwtUtil.parse(token);
            String sessionId = claims.getSubject();
            StoreManagerSession session = sessionMapper.selectById(Long.parseLong(sessionId));
            if (session == null || !token.equals(session.getToken())) {
                throw new BusinessException(401, "Token已失效");
            }

            LoginUser user = new LoginUser(
                    session.getId().longValue(),
                    session.getOpenid(),
                    session.getStoreId(),
                    session.getStoreName()
            );
            UserContextHolder.set(user);

            // 已离职员工拒绝访问（/auth/** 除外，/me 需要正常返回 bound=false）
            String uri = request.getRequestURI();
            if (!uri.contains("/auth/") && StringUtils.hasText(session.getStoreId())) {
                Long count = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getStoreId, session.getStoreId())
                        .eq(Employee::getOpenid, session.getOpenid())
                        .eq(Employee::getStatus, "在职"));
                if (count == 0) {
                    throw new BusinessException(401, "账号已失效，请联系管理员");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(401, "Token校验失败");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
