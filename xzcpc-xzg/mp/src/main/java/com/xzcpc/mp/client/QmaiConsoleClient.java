package com.xzcpc.mp.client;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 企迈控制台 API 客户端（inapi.qmai.cn）。
 * 鉴权：Cookie（qm_seller_token + ALL_DATA_SELLERID）。
 *
 * Cookie 来源：优先读 sys_config 表 qmai_console_cookie（改库即生效，无需重启），
 * 表中为空时回退到环境变量 qmai.console.cookie。
 */
@Slf4j
@Component
public class QmaiConsoleClient {

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Value("${qmai.console.base-url:https://inapi.qmai.cn}")
    private String baseUrl;

    /** 环境变量兜底 Cookie */
    @Value("${qmai.console.cookie:}")
    private String envCookie;

    @Value("${qmai.console.seller-id:}")
    private String sellerId;

    /** Cookie 短缓存：登录态会过期，允许改库后 1 分钟内生效 */
    private volatile String cachedCookie;
    private volatile long cookieLoadedAt;
    private static final long COOKIE_CACHE_MS = 60_000;

    public QmaiConsoleClient(RestTemplate restTemplate, JdbcTemplate jdbcTemplate) {
        this.restTemplate = restTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询入库单列表。
     *
     * @param warehouseId   仓库 ID（非空时仅查该仓）
     * @param createdStartAt 创建时间起
     * @param createdEndAt   创建时间止
     * @param statusList     状态筛选（空=全部）：1待入库 2已入库 3已关闭
     * @param inboundTypeList 入库类型筛选（空=全部）：1期初 2盘盈 3采购 4调拨 5退货 6其他 10加工
     * @param pageNo         页码
     * @param pageSize       每页条数
     */
    @SuppressWarnings("unchecked")
    public InboundOrderListResult getInboundOrderList(String warehouseId,
                                                       String createdStartAt,
                                                       String createdEndAt,
                                                       List<Integer> statusList,
                                                       List<String> inboundTypeList,
                                                       int pageNo,
                                                       int pageSize) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseId", warehouseId);
        body.put("createdStartAt", createdStartAt);
        body.put("createdEndAt", createdEndAt);
        body.put("statusList", statusList != null ? statusList : List.of());
        body.put("inboundTypeList", inboundTypeList != null ? inboundTypeList : List.of());
        body.put("pageNo", pageNo);
        body.put("pageSize", pageSize);

        String url = baseUrl + "/gw/scm/console/inbound/order/list";
        Map<String, Object> respBody = doPost(url, body);

        Map<String, Object> data = (Map<String, Object>) respBody.get("data");
        if (data == null) {
            return new InboundOrderListResult();
        }

        InboundOrderListResult result = new InboundOrderListResult();
        result.setTotal(((Number) data.getOrDefault("total", 0)).intValue());

        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("data");
        if (records != null) {
            List<ConsoleInboundOrder> list = new ArrayList<>();
            for (Map<String, Object> r : records) {
                ConsoleInboundOrder item = new ConsoleInboundOrder();
                item.setId((String) r.get("id"));
                item.setInboundNo((String) r.get("inboundNo"));
                item.setInboundAt((String) r.get("inboundAt"));
                item.setDocumentDate((String) r.get("documentDate"));
                item.setInboundType(toInt(r.get("inboundType")));
                item.setBizNo((String) r.get("bizNo"));
                item.setWarehouseId((String) r.get("warehouseId"));
                item.setWarehouseName((String) r.get("warehouseName"));
                item.setStatus(toInt(r.get("status")));
                item.setAmount(toDouble(r.get("amount")));
                item.setProductAllNum(toDouble(r.get("productAllNum")));
                item.setProductTypeNum(toInt(r.get("productTypeNum")));
                item.setCreator((String) r.get("creator"));
                item.setInboundPerson((String) r.get("inboundPerson"));
                item.setRemark((String) r.get("remark"));
                item.setCreatedAt((String) r.get("createdAt"));
                list.add(item);
            }
            result.setRecords(list);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doPost(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Cookie 鉴权
        String token = resolveCookie();
        if (token != null && !token.isBlank()) {
            headers.set("Cookie", "qm_seller_token=" + token + "; ALL_DATA_SELLERID=" + sellerId);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.debug("Qmai Console API request: url={}", url);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class);

        Map<String, Object> respBody = resp.getBody();
        if (respBody == null) {
            throw new RuntimeException("Qmai Console API returned empty response");
        }

        int code = respBody.get("code") instanceof Number ? ((Number) respBody.get("code")).intValue() : -1;
        if (code != 0) {
            String message = (String) respBody.getOrDefault("message", "unknown error");
            throw new RuntimeException("Qmai Console API error: code=" + code + ", message=" + message);
        }
        return respBody;
    }

    /**
     * 获取控制台登录 Cookie：优先 sys_config 表，其次环境变量。
     * 带 60 秒缓存，避免每次调用都查库。
     */
    private String resolveCookie() {
        long now = System.currentTimeMillis();
        if (cachedCookie == null || now - cookieLoadedAt > COOKIE_CACHE_MS) {
            String dbVal = "";
            try {
                dbVal = jdbcTemplate.queryForObject(
                        "SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                        String.class, "qmai_console_cookie");
            } catch (Exception e) {
                log.warn("读取 sys_config.qmai_console_cookie 失败，回退环境变量: {}", e.getMessage());
            }
            cachedCookie = (dbVal != null && !dbVal.isBlank()) ? dbVal.trim()
                    : (envCookie != null ? envCookie.trim() : "");
            cookieLoadedAt = now;
        }
        return cachedCookie;
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return 0.0;
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return 0;
    }

    // ==================== DTO ====================

    @Data
    public static class InboundOrderListResult {
        private int total;
        private List<ConsoleInboundOrder> records = List.of();
    }

    /** 控制台入库单 */
    @Data
    public static class ConsoleInboundOrder {
        private String id;
        private String inboundNo;
        private String inboundAt;
        private String documentDate;
        /** 1期初 2盘盈 3采购 4调拨 5退货 6其他 10加工 */
        private int inboundType;
        /** 关联单号：采购型 → CG*；退货型 → R* */
        private String bizNo;
        private String warehouseId;
        private String warehouseName;
        /** 1待入库 2已入库 3已关闭 */
        private int status;
        private double amount;
        private double productAllNum;
        private int productTypeNum;
        private String creator;
        private String inboundPerson;
        private String remark;
        private String createdAt;
    }
}
