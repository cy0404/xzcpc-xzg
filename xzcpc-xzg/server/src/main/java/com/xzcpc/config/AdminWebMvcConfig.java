package com.xzcpc.config;

import com.xzcpc.interceptor.AdminLoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 总部端 Web MVC 配置，注册管理员登录拦截器。
 * 排除登录接口和静态资源，不影响 /api/mp/**（由 mp-server 独立处理）。
 */
@Configuration
@RequiredArgsConstructor
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminLoginInterceptor adminLoginInterceptor;

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absPath = new java.io.File(uploadPath).getAbsolutePath() + "/";
        String location = "file:" + absPath;
        registry.addResourceHandler("/upload/**").addResourceLocations(location);
        registry.addResourceHandler("/api/upload/**").addResourceLocations(location);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminLoginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/feishu/login",
                        "/api/auth/dev/**",
                        "/api/materials/migrate-inventory-units",
                        "/api/materials/sync",
                        "/api/materials/*/qrcode-img",
                        "/api/upload/**",
                        "/api/mp/**",
                        "/api/reports/**",
                        "/api/logs/operation",
                        "/api/logs/login"
                );
    }
}
