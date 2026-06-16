package com.xzcpc.config;

import com.xzcpc.interceptor.AdminLoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 总部端 Web MVC 配置，注册管理员登录拦截器。
 * 排除登录接口和静态资源，不影响 /api/mp/**（由 mp-server 独立处理）。
 */
@Configuration
@RequiredArgsConstructor
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminLoginInterceptor adminLoginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminLoginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/feishu/login",    // 飞书登录
                        "/api/auth/dev/**",          // 本地开发 Mock 登录
                        "/api/materials/migrate-inventory-units", // 一次性迁移
                        "/api/materials/sync",       // 物料同步
                        "/api/mp/**",                // 小程序端（走 mp-server 独立部署）
                        "/api/reports/**",           // 报表接口（自带固定 token 鉴权）
                        "/api/logs/operation",       // 日志查询暂时放行（开发阶段）
                        "/api/logs/login"            // 日志查询暂时放行（开发阶段）
                );
    }
}
