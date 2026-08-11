package com.xzcpc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
public class CacheManageController {

    private final CacheManager cacheManager;

    @PostMapping("/clear-materials")
    public Map<String, Object> clearMaterials() {
        var cache = cacheManager.getCache("materials");
        if (cache != null) cache.clear();
        return Map.of("code", 200, "msg", "ok");
    }
}
