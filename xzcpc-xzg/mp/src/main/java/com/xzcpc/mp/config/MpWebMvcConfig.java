package com.xzcpc.mp.config;

import com.xzcpc.mp.interceptor.MpLoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class MpWebMvcConfig implements WebMvcConfigurer {

    private final MpLoginInterceptor loginInterceptor;

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/mp/**")
                .excludePathPatterns("/api/mp/auth/wx/login",
                                     "/api/mp/auth/owner/query-stores",
                                     "/api/mp/auth/owner/confirm-bind",
                                     "/api/mp/auth/owner/status",
                                     "/api/mp/auth/owner/qrcode",
                                     "/api/mp/auth/owner/qrcode-img",
                                     "/api/mp/auth/owner/my-status",
                                     "/api/mp/public/**",
                                     "/api/mp/staff/registrations");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 /upload/** 到本地 upload 目录，使上传的图片可直接通过 URL 访问
        String absolutePath = Paths.get(uploadPath).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/upload/**")
                .addResourceLocations(absolutePath);
    }
}
