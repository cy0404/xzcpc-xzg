package com.xzcpc.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.entity.StoreOrderCycle;
import com.xzcpc.task.mapper.StoreMapper;
import com.xzcpc.task.mapper.StoreOrderCycleMapper;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 门店信息服务实现——数据库存储版。
 * 数据从外部 API 同步到 store_info 表，qr_code 字段仅本地维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final RestTemplate restTemplate;
    private final StoreMapper storeMapper;
    private final StoreOrderCycleMapper storeOrderCycleMapper;

    @Value("${store.api.url}")
    private String apiUrl;

    @Value("${store.api.key}")
    private String apiKey;

    // ==================== 查询（走数据库） ====================

    @Override
    public List<StoreInfo> getAllStores() {
        List<Store> stores = storeMapper.selectList(
                new LambdaQueryWrapper<Store>().orderByDesc(Store::getUpdatedAt));
        if (stores.isEmpty() && syncEnabled()) {
            syncFromApi();
            stores = storeMapper.selectList(
                    new LambdaQueryWrapper<Store>().orderByDesc(Store::getUpdatedAt));
        }
        // 批量附带订货周期配置（store_order_cycle），避免逐门店查询
        Map<String, StoreOrderCycle> cycleByStore = storeOrderCycleMapper.selectList(
                        new LambdaQueryWrapper<StoreOrderCycle>())
                .stream().collect(Collectors.toMap(StoreOrderCycle::getStoreId, Function.identity()));
        return stores.stream().map(s -> {
            StoreInfo info = toStoreInfo(s);
            attachOrderCycle(info, cycleByStore.get(info.getId()));
            return info;
        }).collect(Collectors.toList());
    }

    @Override
    public StoreInfo getStoreById(String id) {
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getStoreId, id));
        if (store != null) {
            StoreInfo info = toStoreInfo(store);
            attachOrderCycle(info, storeOrderCycleMapper.selectOne(
                    new LambdaQueryWrapper<StoreOrderCycle>()
                            .eq(StoreOrderCycle::getStoreId, id)));
            return info;
        }
        // 缓存未命中时尝试验证 API 是否存在该门店
        List<StoreInfo> all = getAllStores();
        return all.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public Map<String, StoreInfo> getStoreMap() {
        return getAllStores().stream()
                .collect(Collectors.toMap(StoreInfo::getId, Function.identity()));
    }

    @Override
    public String getChatId(String storeId) {
        if (!StringUtils.hasText(storeId)) return null;
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getStoreId, storeId));
        return store != null ? store.getChatId() : null;
    }

    @Override
    public Store getStoreByChatId(String chatId) {
        if (!StringUtils.hasText(chatId)) return null;
        return storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getChatId, chatId));
    }

    // ==================== 外部 API 同步到数据库 ====================

    /**
     * 每天凌晨 1 点全量同步：从外部 API 拉取门店数据，upsert 到 store_info。
     * 已存在的门店只更新名称/编码/小程序号/仓库ID，不覆盖 qr_code。
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void scheduledRefresh() {
        if (!syncEnabled()) return;
        refreshCache();
    }

    @Override
    public void refreshCache() {
        log.info("开始从外部 API 同步门店信息到数据库...");
        try {
            syncFromApi();
        } catch (Exception e) {
            log.error("同步门店信息失败", e);
        }
    }

    private void syncFromApi() {
        List<StoreInfo> apiStores = fetchAllFromApi();
        upsertStores(apiStores);
        log.info("门店信息同步完成，共 {} 条记录", apiStores.size());
    }

    @Transactional(rollbackFor = Exception.class)
    private void upsertStores(List<StoreInfo> items) {
        for (StoreInfo item : items) {
            if (!StringUtils.hasText(item.getId())) {
                continue;
            }
            Store existing = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                    .eq(Store::getStoreId, item.getId()));
            if (existing == null) {
                existing = new Store();
                existing.setStoreId(item.getId());
            }
            existing.setStoreName(item.getMendianmingcheng());
            existing.setStoreCode(item.getBianma());
            existing.setXiaochengxuid(item.getXiaochengxuid());
            existing.setCangkuid(item.getCangkuid());
            // 本地维护字段不随外部同步覆盖：qr_code, owner_name, owner_phone, owner_openid
            if (existing.getId() == null) {
                storeMapper.insert(existing);
            } else {
                storeMapper.updateById(existing);
            }
        }
    }

    // ==================== 二维码维护 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQrCode(String storeId, String qrCode) {
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getStoreId, storeId));
        if (store == null) {
            throw new BusinessException(404, "门店不存在");
        }
        store.setQrCode(StringUtils.hasText(qrCode) ? qrCode.trim() : null);
        storeMapper.updateById(store);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderCycle(Map<String, Map<String, Object>> storeIdToConfig) {
        if (storeIdToConfig == null || storeIdToConfig.isEmpty()) return;
        for (Map.Entry<String, Map<String, Object>> e : storeIdToConfig.entrySet()) {
            Map<String, Object> cfg = e.getValue();
            if (cfg == null) continue;
            String storeId = e.getKey();
            String orderDays = normalizeOrderDays(cfg.get("orderDays"));
            Integer paused = cfg.get("paused") == null ? null : ((Number) cfg.get("paused")).intValue();
            if (paused != null && paused != 0 && paused != 1) {
                throw new BusinessException("周盘暂停取值需为 0或1，门店=" + storeId);
            }
            Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                    .eq(Store::getStoreId, storeId));
            if (store == null) {
                throw new BusinessException("门店不存在: " + storeId);
            }
            // 周盘仅直营店参与：非直营店配置订货日拒绝（清空配置不受限）
            if (orderDays != null && !"direct".equals(store.getStoreType())) {
                throw new BusinessException("门店[" + store.getStoreName() + "]为加盟店，周盘仅直营店可配置订货周期");
            }
            StoreOrderCycle existing = storeOrderCycleMapper.selectOne(
                    new LambdaQueryWrapper<StoreOrderCycle>()
                            .eq(StoreOrderCycle::getStoreId, storeId));
            if (orderDays == null) {
                // orderDays 为空 → 清空配置（不参与周盘），删除行
                if (existing != null) {
                    storeOrderCycleMapper.deleteById(existing.getId());
                }
                continue;
            }
            if (existing != null) {
                existing.setOrderDays(orderDays);
                if (paused != null) existing.setPaused(paused);
                storeOrderCycleMapper.updateById(existing);
            } else {
                StoreOrderCycle cycle = new StoreOrderCycle();
                cycle.setStoreId(storeId);
                cycle.setOrderDays(orderDays);
                cycle.setPaused(paused != null ? paused : 0);
                storeOrderCycleMapper.insert(cycle);
            }
        }
    }

    /** 规范化订货日："1,4" → "1,4"（校验 1-7、去重、升序）；null/空 → null；全非法 → 报错 */
    private String normalizeOrderDays(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        java.util.Set<Integer> set = new java.util.TreeSet<>();
        for (String part : s.split(",")) {
            try {
                int d = Integer.parseInt(part.trim());
                if (d >= 1 && d <= 7) set.add(d);
            } catch (NumberFormatException ignored) {
            }
        }
        if (set.isEmpty()) {
            throw new BusinessException("订货日取值需在1(周一)-7(周日)之间，逗号分隔");
        }
        return set.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOwnerInfo(String storeId, String openid, String name, String phone) {
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getStoreId, storeId));
        if (store == null) return;
        updateOwnerInfo(store, openid, name, phone);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOwnerInfo(Store store, String openid, String name, String phone) {
        store.setOwnerOpenid(openid);
        store.setOwnerName(name);
        store.setOwnerPhone(phone);
        store.setUpdatedAt(java.time.LocalDateTime.now());
        storeMapper.updateById(store);
    }

    // ==================== 外部 API 翻页拉取 ====================

    private List<StoreInfo> fetchAllFromApi() {
        List<StoreInfo> allStores = new ArrayList<>();
        int page = 1;
        int pageSize = 100;
        boolean hasMore = true;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Publish-Api-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        while (hasMore) {
            String url = apiUrl + "?page=" + page + "&pageSize=" + pageSize;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = response.getBody();
            if (body == null) break;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) body.get("records");
            if (records == null || records.isEmpty()) break;

            for (Map<String, Object> record : records) {
                StoreInfo store = new StoreInfo();
                store.setId(toString(record.get("id")));
                store.setMendianmingcheng((String) record.get("mendianmingcheng"));
                store.setBianma((String) record.get("bianma"));
                store.setXiaochengxuid(toString(record.get("xiaochengxuid")));
                store.setCangkuid(toString(record.get("cangkuid")));
                allStores.add(store);
            }

            Object totalObj = body.get("total");
            int total = totalObj instanceof Number ? ((Number) totalObj).intValue() : 0;
            hasMore = page * pageSize < total;
            page++;
        }

        log.info("从外部 API 获取门店信息完成，共 {} 条记录，请求 {} 页", allStores.size(), page - 1);
        return allStores;
    }

    // ==================== 工具方法 ====================

    private StoreInfo toStoreInfo(Store s) {
        StoreInfo info = new StoreInfo();
        info.setId(s.getStoreId());
        info.setMendianmingcheng(s.getStoreName());
        info.setBianma(s.getStoreCode());
        info.setStoreType(s.getStoreType());
        info.setXinfoStoreName(s.getXinfoStoreName());
        info.setProvince(s.getProvince());
        info.setCity(s.getCity());
        info.setDistrict(s.getDistrict());
        info.setAddress(s.getAddress());
        info.setXiaochengxuid(s.getXiaochengxuid());
        info.setCangkuid(s.getCangkuid());
        info.setQrCode(s.getQrCode());
        info.setOwnerName(s.getOwnerName());
        info.setOwnerPhone(s.getOwnerPhone());
        info.setOwnerOpenid(s.getOwnerOpenid());
        info.setSupervisorName(s.getSupervisorName());
        info.setQmaiStoreId(s.getQmaiStoreId());
        info.setWeeklyInventoryDay(s.getWeeklyInventoryDay());
        return info;
    }

    /** 组装订货周期配置到门店信息（无配置则为 null） */
    private void attachOrderCycle(StoreInfo info, StoreOrderCycle cycle) {
        if (cycle == null) return;
        info.setOrderDays(cycle.getOrderDays());
        info.setWeeklyPaused(cycle.getPaused());
    }

    private static String toString(Object value) {
        return value == null ? null : value.toString();
    }

    @org.springframework.beans.factory.annotation.Value("${app.sync.enabled:true}")
    private boolean syncEnabled;

    private boolean syncEnabled() { return syncEnabled; }

    @Override
    public void save(com.xzcpc.task.entity.Store store) {
        storeMapper.insert(store);
    }
}
