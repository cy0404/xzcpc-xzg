package com.xzcpc.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("stores", Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(25, TimeUnit.HOURS)
                .build());
        manager.registerCustomCache("feishuAppToken", Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(110, TimeUnit.MINUTES)
                .build());
        manager.registerCustomCache("templateZoneMaterials", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build());
        return manager;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
