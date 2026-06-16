package com.xzcpc.mp.service;

import java.util.List;
import java.util.Map;

public interface MpStoreService {
    /**
     * 列出所有门店供小程序选择，支持按名称/编码模糊搜索。
     */
    List<Map<String, Object>> listStores(String keyword);

    /**
     * 手动刷新门店缓存（从外部 API 重新拉取）。
     */
    void refreshStoreCache();
}
