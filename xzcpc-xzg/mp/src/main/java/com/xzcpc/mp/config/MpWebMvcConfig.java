package com.xzcpc.mp.config;

import com.xzcpc.mp.interceptor.MpLoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
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
        // H5 页面不缓存：web-view 无 Cache-Control 时微信启发式缓存会让门店停留在旧页面（最长数天），
        // 改 H5 文件后门店端不生效。/upload/h5/** 更具体，命中时优先于此前的 /upload/** 映射
        registry.addResourceHandler("/upload/h5/**")
                .addResourceLocations(absolutePath + "/h5/", oldPath + "/h5/")
                .setCacheControl(CacheControl.noCache());
        // 容器图片优先读 upload/containers/（可随时加图），找不到再读 JAR 内置
        registry.addResourceHandler("/containers/**")
                .addResourceLocations(absolutePath + "/containers/",
                                      "classpath:/static/containers/");
    }
}
