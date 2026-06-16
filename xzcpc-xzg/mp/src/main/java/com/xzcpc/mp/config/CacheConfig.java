package com.xzcpc.mp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xzcpc.expense.entity.ExpenseItem;
import com.xzcpc.expense.entity.ExpenseType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration("mpCacheConfig")
public class CacheConfig {

    @Bean
    public Cache<String, List<ExpenseType>> typeCache() {
        return Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    @Bean
    public Cache<String, List<ExpenseItem>> itemCache() {
        return Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }
}
