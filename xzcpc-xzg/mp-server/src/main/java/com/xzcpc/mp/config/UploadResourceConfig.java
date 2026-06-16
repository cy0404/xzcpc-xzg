package com.xzcpc.mp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadPath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/upload/**")
                .addResourceLocations(absolutePath);
    }
}
