package com.xzcpc.mp.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.service.MpStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mp/stores")
@RequiredArgsConstructor
public class MpStoreController {

    private final MpStoreService storeService;

    /**
     * 获取门店列表，支持按名称/编码搜索。
     * 登录后但未绑定门店时调用此接口让用户选择门店。
     */
    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(storeService.listStores(keyword));
    }

    /**
     * 手动刷新门店缓存。外部 API 新增门店数据后调用此接口立即生效，无需等到凌晨 1 点定时刷新。
     */
    @PostMapping("/refresh")
    public R<Void> refresh() {
        storeService.refreshStoreCache();
        return R.ok();
    }
}
