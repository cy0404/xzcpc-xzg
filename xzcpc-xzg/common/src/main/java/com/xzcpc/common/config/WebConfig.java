package com.xzcpc.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // CORS 跨域配置，允许前端开发服务器访问后端 API

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // "null" 字符串兼容处理：前端可能传递字面量 "null"
        registry.addConverter(new Converter<String, Integer>() {
            @Override
            public Integer convert(String source) {
                if ("null".equals(source)) return 0;
                return Integer.valueOf(source);
            }
        });
    }
}
