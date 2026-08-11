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
                                     "/api/mp/auth/owner/status",
                                     "/api/mp/auth/owner/qrcode",
                                     "/api/mp/auth/owner/qrcode-img",
                                     "/api/mp/public/**",
                                     "/api/mp/staff/registrations",
                                     "/api/mp/stores-all",
                                     "/api/mp/stores-all/refresh",
                                     "/api/mp/upload/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadPath).toAbsolutePath().toUri().toString();
        String oldPath = Paths.get("./upload").toAbsolutePath().toUri().toString();
        // 映射 /upload/** 到新路径，旧路径兜底
        registry.addResourceHandler("/upload/**")
                .addResourceLocations(absolutePath, oldPath);
        // 容器图片优先读 upload/containers/（可随时加图），找不到再读 JAR 内置
        registry.addResourceHandler("/containers/**")
                .addResourceLocations(absolutePath + "/containers/",
                                      "classpath:/static/containers/");
    }
}
