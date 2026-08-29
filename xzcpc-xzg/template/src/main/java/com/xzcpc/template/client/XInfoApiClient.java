package com.xzcpc.template.client;

import com.xzcpc.template.client.dto.XInfoApiResponse;
import com.xzcpc.template.client.dto.XInfoMaterial;
import com.xzcpc.template.client.dto.XInfoSemiFinishedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * xinfo 基础数据管理系统外部 API 客户端（机器码 X-API-Key 认证，只读）。
 * 接口一次返回全部数据（无分页），读超时需留足。
 */
@Slf4j
@Component
public class XInfoApiClient {

    private static final String HEADER_API_KEY = "X-API-Key";

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;

    public XInfoApiClient(
            RestTemplateBuilder builder,
            @Value("${xinfo.api.url}") String apiUrl,
            @Value("${xinfo.api.key}") String apiKey) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    /**
     * 拉取全部原料。HTTP 失败或返回空列表时抛异常（调用方据此中止同步，防误删）。
     */
    public List<XInfoMaterial> fetchMaterials() {
        List<XInfoMaterial> items = doFetch("/api/materials", new ParameterizedTypeReference<XInfoApiResponse<XInfoMaterial>>() {});
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("xinfo 原料接口返回空，中止同步");
        }
        return items;
    }

    /**
     * 拉取全部半成品。HTTP 失败或返回空列表时抛异常（调用方据此中止同步，防误删）。
     */
    public List<XInfoSemiFinishedProduct> fetchSemiFinishedProducts() {
        List<XInfoSemiFinishedProduct> items = doFetch("/api/semi-finished-products",
                new ParameterizedTypeReference<XInfoApiResponse<XInfoSemiFinishedProduct>>() {});
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("xinfo 半成品接口返回空，中止同步");
        }
        return items;
    }

    private <T> List<T> doFetch(String path, ParameterizedTypeReference<XInfoApiResponse<T>> typeRef) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_API_KEY, apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        log.info("xinfo 拉取 {} 开始", path);
        long start = System.currentTimeMillis();
        try {
            XInfoApiResponse<T> body = restTemplate.exchange(
                    apiUrl + path, HttpMethod.GET, entity, typeRef).getBody();
            log.info("xinfo 拉取 {} 完成，{} 条，耗时 {}ms", path,
                    body == null ? 0 : (body.getItems() == null ? 0 : body.getItems().size()),
                    System.currentTimeMillis() - start);
            return body == null ? null : body.getItems();
        } catch (Exception e) {
            throw new IllegalStateException("xinfo 拉取 " + path + " 失败: " + e.getMessage(), e);
        }
    }
}
