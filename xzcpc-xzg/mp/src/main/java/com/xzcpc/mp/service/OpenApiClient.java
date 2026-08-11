package com.xzcpc.mp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 小象数据 OpenAPI 客户端
 * 调用 http://119.45.162.160:8000/openapi/v1/finance/* 接口
 * 鉴权方式：IP 白名单，无需 Token
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenApiClient {

    private final RestTemplate restTemplate;

    @Value("${openapi.finance.base-url:http://119.45.162.160:8000}")
    private String baseUrl;

    /**
     * 门店月度经营指标（优先用关键词精确匹配）
     * GET /openapi/v1/finance/store-detail?stat_month=YYYY-MM-DD&keyword=
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStoreDetail(String storeUuid, String statMonth, String storeName) {
        try {
            // 先用门店名关键词精确搜索（通常 1-2 条结果，远快于遍历全量）
            String keywordUrl = baseUrl + "/openapi/v1/finance/store-detail"
                    + "?stat_month=" + statMonth
                    + "&page_size=5"
                    + "&keyword=" + (storeName != null ? storeName : "");
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    keywordUrl, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (body != null && Integer.valueOf(0).equals(body.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data != null && data.get("stores") instanceof List) {
                    List<Map<String, Object>> stores = (List<Map<String, Object>>) data.get("stores");
                    for (Map<String, Object> store : stores) {
                        if (storeUuid.equals(store.get("storeId"))) {
                            log.info("OpenAPI store-detail 关键词命中: uuid={}", storeUuid);
                            return store;
                        }
                    }
                }
            }
            // 关键词未命中 → fallback 分页遍历
            log.info("OpenAPI store-detail 关键词未命中，回退分页遍历: uuid={}", storeUuid);
            int page = 1; int pageSize = 100; int total = Integer.MAX_VALUE;
            Map<String, Object> data;
            while ((page - 1) * pageSize < total) {
                String url = baseUrl + "/openapi/v1/finance/store-detail"
                        + "?stat_month=" + statMonth
                        + "&page=" + page + "&page_size=" + pageSize;
                resp = restTemplate.exchange(url, HttpMethod.GET, null,
                        new ParameterizedTypeReference<Map<String, Object>>() {});
                body = resp.getBody();
                if (body != null && Integer.valueOf(0).equals(body.get("code"))) {
                    data = (Map<String, Object>) body.get("data");
                    if (data != null) {
                        total = ((Number) data.getOrDefault("total", 0)).intValue();
                        if (data.get("stores") instanceof List) {
                            List<Map<String, Object>> stores = (List<Map<String, Object>>) data.get("stores");
                            for (Map<String, Object> store : stores) {
                                if (storeUuid.equals(store.get("storeId"))) {
                                    log.info("OpenAPI store-detail 分页命中: uuid={}, page={}", storeUuid, page);
                                    return store;
                                }
                            }
                        }
                    }
                }
                if (page * pageSize >= total) break;
                page++;
            }
            log.warn("OpenAPI store-detail 未找到匹配门店: uuid={}, total={}", storeUuid, total);
            return null;
        } catch (RestClientException e) {
            log.warn("OpenAPI store-detail 调用失败: {}", e.toString());
            return null;
        }
    }

    /**
     * 日营收 + 渠道拆分
     * GET /openapi/v1/finance/daily?store_id=UUID&start_date=YYYY-MM-DD&end_date=YYYY-MM-DD
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDaily(String storeUuid, String startDate, String endDate) {
        String url = baseUrl + "/openapi/v1/finance/daily"
                + "?store_id=" + storeUuid
                + "&start_date=" + startDate
                + "&end_date=" + endDate;
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (body != null && Integer.valueOf(0).equals(body.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data != null && data.get("daily") instanceof List) {
                    return (List<Map<String, Object>>) data.get("daily");
                }
            }
            return null;
        } catch (RestClientException e) {
            log.warn("OpenAPI daily 调用失败: {}", e.toString());
            return null;
        }
    }

    /**
     * 成本构成
     * GET /openapi/v1/finance/store-cost?store_id=UUID&cost_month=YYYY-MM-DD
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStoreCost(String storeUuid, String costMonth) {
        String url = baseUrl + "/openapi/v1/finance/store-cost"
                + "?store_id=" + storeUuid
                + "&cost_month=" + costMonth;
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (body != null && Integer.valueOf(0).equals(body.get("code"))) {
                return (Map<String, Object>) body.get("data");
            }
            return null;
        } catch (RestClientException e) {
            log.warn("OpenAPI store-cost 调用失败: {}", e.toString());
            return null;
        }
    }

    /**
     * 成本下钻（按分组 + 可选类别过滤）
     * GET /openapi/v1/finance/store-cost/breakdown?store_id=UUID&cost_month=YYYY-MM-DD&group=&category=
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCostBreakdown(String storeUuid, String costMonth, String group, String category) {
        StringBuilder url = new StringBuilder(baseUrl + "/openapi/v1/finance/store-cost/breakdown"
                + "?store_id=" + storeUuid
                + "&cost_month=" + costMonth
                + "&group=" + group);
        if (category != null && !category.isEmpty()) {
            url.append("&category=").append(category);
        }
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url.toString(), HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (body != null && Integer.valueOf(0).equals(body.get("code"))) {
                return (Map<String, Object>) body.get("data");
            }
            return null;
        } catch (RestClientException e) {
            log.warn("OpenAPI cost-breakdown 调用失败: {}", e.toString());
            return null;
        }
    }
}
