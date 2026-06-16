package com.xzcpc.mp.service.impl;

import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.mp.service.MpStoreService;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpStoreServiceImpl implements MpStoreService {

    private final StoreService storeService;

    @Override
    public void refreshStoreCache() {
        log.info("手动刷新门店缓存...");
        storeService.refreshCache();
    }

    @Override
    public List<Map<String, Object>> listStores(String keyword) {
        List<StoreInfo> all = storeService.getAllStores();
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (StoreInfo s : all) {
            if (kw != null) {
                String name = s.getMendianmingcheng() == null ? "" : s.getMendianmingcheng().toLowerCase();
                String code = s.getBianma() == null ? "" : s.getBianma().toLowerCase();
                if (!name.contains(kw) && !code.contains(kw)) {
                    continue;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("storeId", s.getId());
            item.put("storeName", s.getMendianmingcheng());
            item.put("storeCode", s.getBianma());
            item.put("xiaochengxuid", s.getXiaochengxuid());
            item.put("warehouseCode", s.getCangkuid());
            item.put("qrCode", s.getQrCode());
            result.add(item);
        }
        return result;
    }
}
