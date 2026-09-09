package com.xzcpc.mp.client;

import com.xzcpc.mp.util.QmaiSignUtil;
import com.xzcpc.mp.util.QmaiSignUtil.QmaiAuth;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

/**
 * 企迈 OpenAPI v3 客户端。
 * 用于查询门店报货单列表和详情，供到货验收报损 H5 页面选择。
 */
@Slf4j
@Component
public class QmaiClient {

    private final RestTemplate restTemplate;

    @Value("${qmai.open-id}")
    private String openId;

    @Value("${qmai.grant-code}")
    private String grantCode;

    @Value("${qmai.open-key}")
    private String openKey;

    @Value("${qmai.base-url:https://openapi.qmai.cn}")
    private String baseUrl;

    @Value("${qmai.console.base-url:https://inapi.qmai.cn}")
    private String consoleBaseUrl;

    @Value("${qmai.console.cookie:}")
    private String consoleCookie;

    @Value("${qmai.console.seller-id:}")
    private String consoleSellerId;

    public QmaiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 查询报货单列表（9.1.17）。
     * 列表接口不返回 declareProductList（始终为 null），仅返回订单摘要。
     * 获取商品明细需调用 {@link #getDeclareOrderDetail(String)}。
     *
     * @param qmaiStoreId    企迈门店 ID
     * @param createdStartAt 创建时间起（含），如 "2026-08-01 00:00:00"
     * @param createdEndAt   创建时间止（含），如 "2026-08-11 23:59:59"
     * @param pageNo         页码
     * @param pageSize       每页条数
     */
    public DeclareOrderListResult getDeclareOrderList(long qmaiStoreId,
                                                      String createdStartAt,
                                                      String createdEndAt,
                                                      int pageNo,
                                                      int pageSize) {
        return fetchDeclareOrderList(qmaiStoreId, createdStartAt, createdEndAt, pageNo, pageSize, true);
    }

    /**
     * 查询报货单列表（全部状态，不按 orderStatus/payStatus 过滤）。
     * 用于智能订货支付状态实时刷新：待支付(0)的单子必须能查到。
     */
    public DeclareOrderListResult getDeclareOrderListAll(long qmaiStoreId,
                                                         String createdStartAt,
                                                         String createdEndAt,
                                                         int pageNo,
                                                         int pageSize) {
        return fetchDeclareOrderList(qmaiStoreId, createdStartAt, createdEndAt, pageNo, pageSize, false);
    }

    @SuppressWarnings("unchecked")
    private DeclareOrderListResult fetchDeclareOrderList(long qmaiStoreId,
                                                         String createdStartAt,
                                                         String createdEndAt,
                                                         int pageNo,
                                                         int pageSize,
                                                         boolean activeOnly) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("storeIdList", Collections.singletonList(qmaiStoreId));
        bizParams.put("createdStartAt", createdStartAt);
        bizParams.put("createdEndAt", createdEndAt);
        bizParams.put("pageNo", pageNo);
        bizParams.put("pageSize", pageSize);
        if (activeOnly) {
            // 只查履约中(3)+已完成(4)，且已付款(1)+已审核(2)
            bizParams.put("statusList", List.of(3, 4));
            bizParams.put("payStatusList", List.of(1, 2));
        }

        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/newPattern/scmApiserver/post/declare/order/list";

        Map<String, Object> respBody = doPost(url, body);
        Map<String, Object> data = (Map<String, Object>) respBody.get("data");
        if (data == null) {
            return new DeclareOrderListResult();
        }

        DeclareOrderListResult result = new DeclareOrderListResult();
        result.setTotal(((Number) data.getOrDefault("total", 0)).intValue());

        // 注意：列表接口响应在 data.data 下（不是 data.records）
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("data");
        if (records != null) {
            List<DeclareOrderSummary> list = new ArrayList<>();
            for (Map<String, Object> r : records) {
                DeclareOrderSummary item = new DeclareOrderSummary();
                item.setDeclareNo((String) r.get("declareNo"));
                item.setRequireNo((String) r.get("requireNo"));
                item.setRequireNoList((List<String>) r.get("requireNoList"));
                item.setPurchaseApplyNoList((List<String>) r.get("purchaseApplyNoList"));
                item.setPurchaseNoList((List<String>) r.get("purchaseNoList"));
                item.setBizNoList((List<String>) r.get("bizNoList"));
                item.setBizNo((String) r.get("bizNo"));
                item.setStoreWarehouseNo((String) r.get("storeWarehouseNo"));
                item.setStoreName((String) r.get("storeName"));
                item.setCreatedAt((String) r.get("createdAt"));
                // 金额字段 API 返回单位为元（float）
                item.setAmount(toDouble(r.get("amount")));
                item.setFreight(toDouble(r.get("freight")));
                item.setOnlinePay(toInt(r.get("onlinePay")));
                // 状态字段名是 orderStatus，不是 status
                item.setOrderStatus(toInt(r.get("orderStatus")));
                item.setPayStatus(toInt(r.get("payStatus")));
                item.setSource(toInt(r.get("source")));
                item.setProductNum(toDouble(r.get("productNum")));
                item.setProductCateNum(toInt(r.get("productCateNum")));
                item.setUpdatedAt((String) r.get("updatedAt"));
                // 列表接口 declareProductList 始终为 null，忽略
                list.add(item);
            }
            result.setRecords(list);
        }
        return result;
    }

    /**
     * 按报货单号查询其拆单关联（订货单 requireNoList / 采购申请单 / 采购单）。
     * 报货单按配送中心拆单，同一报货单可对应多个订货单（不同仓单号不同）。
     *
     * @param declareNo 报货单号，如 "BH20260818000079"
     * @return 单号关联摘要；查无记录时返回空对象
     */
    @SuppressWarnings("unchecked")
    public DeclareOrderSummary getDeclareOrderByNo(String declareNo) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("declareNo", declareNo);
        bizParams.put("pageNo", 1);
        bizParams.put("pageSize", 5);

        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/newPattern/scmApiserver/post/declare/order/list";

        Map<String, Object> respBody = doPost(url, body);
        Map<String, Object> data = (Map<String, Object>) respBody.get("data");
        if (data == null) {
            return new DeclareOrderSummary();
        }
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("data");
        if (records == null || records.isEmpty()) {
            return new DeclareOrderSummary();
        }
        Map<String, Object> r = records.get(0);
        DeclareOrderSummary item = new DeclareOrderSummary();
        item.setDeclareNo((String) r.get("declareNo"));
        item.setRequireNo((String) r.get("requireNo"));
        item.setRequireNoList((List<String>) r.get("requireNoList"));
        item.setPurchaseApplyNoList((List<String>) r.get("purchaseApplyNoList"));
        item.setPurchaseNoList((List<String>) r.get("purchaseNoList"));
        item.setBizNoList((List<String>) r.get("bizNoList"));
        return item;
    }

    /**
     * 查询订货单（9.？ require/order/list），含状态/配送中心/金额。
     * 报货单拆单后每个配送中心一个订货单，状态各自独立：
     * 0待审核 1待发货 2已部分发货 3已发货 4已驳回 5已取消 6已完成。
     *
     * @param requireNo 订货单号，如 "DH20260819000136"
     * @return 订货单摘要；查无记录时返回空对象
     */
    @SuppressWarnings("unchecked")
    public RequireOrderSummary getRequireOrderByNo(String requireNo) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("requireNo", requireNo);
        bizParams.put("pageNo", 1);
        bizParams.put("pageSize", 5);

        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/newPattern/scmApiserver/post/require/order/list";

        Map<String, Object> respBody = doPost(url, body);
        Map<String, Object> data = (Map<String, Object>) respBody.get("data");
        if (data == null) {
            return new RequireOrderSummary();
        }
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("data");
        if (records == null || records.isEmpty()) {
            return new RequireOrderSummary();
        }
        Map<String, Object> r = records.get(0);
        RequireOrderSummary item = new RequireOrderSummary();
        item.setRequireNo((String) r.get("requireNo"));
        item.setDeclareNo((String) r.get("declareNo"));
        item.setWarehouseNo((String) r.get("warehouseNo"));
        item.setWarehouseName((String) r.get("warehouseName"));
        item.setStoreWarehouseNo((String) r.get("storeWarehouseNo"));
        item.setOrderStatus(toInt(r.get("orderStatus")));
        item.setAmount(toDouble(r.get("amount")));
        item.setProductNum(toDouble(r.get("productNum")));
        item.setOrderAt((String) r.get("orderAt"));
        return item;
    }

    /**
     * 查询报货单详情（9.1.2），包含完整的商品明细 declareProductList。
     *
     * @param declareNo 报货单号，如 "BH20260810000033"
     */
    @SuppressWarnings("unchecked")
    public DeclareOrderDetail getDeclareOrderDetail(String declareNo) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("declareNo", declareNo);

        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/scm/order/declare/order/detail";

        Map<String, Object> respBody = doPost(url, body);
        Map<String, Object> data = (Map<String, Object>) respBody.get("data");
        if (data == null) {
            throw new RuntimeException("Qmai detail API returned empty data for " + declareNo);
        }

        DeclareOrderDetail detail = new DeclareOrderDetail();
        detail.setDeclareNo((String) data.get("declareNo"));
        detail.setRequireNo((String) data.get("requireNo"));
        detail.setRequireNoList((List<String>) data.get("requireNoList"));
        detail.setPurchaseApplyNoList((List<String>) data.get("purchaseApplyNoList"));
        detail.setBizNoList((List<String>) data.get("bizNoList"));
        detail.setBizNo((String) data.get("bizNo"));
        detail.setStoreName((String) data.get("storeName"));
        detail.setCreatedAt((String) data.get("createdAt"));
        detail.setAmount(toDouble(data.get("amount")));
        detail.setActualAmount(toDouble(data.get("actualAmount")));
        detail.setAuditAmount(toDouble(data.get("auditAmount")));
        detail.setDiscountAmount(toDouble(data.get("discountAmount")));
        detail.setFreight(toDouble(data.get("freight")));
        detail.setFreightType(toInt(data.get("freightType")));
        detail.setDeliveryType(toInt(data.get("deliveryType")));
        detail.setOnlinePay(toInt(data.get("onlinePay")));
        detail.setOrderStatus(toInt(data.get("orderStatus")));
        detail.setPayStatus(toInt(data.get("payStatus")));
        detail.setPayType(toInt(data.get("payType")));
        detail.setProductNum(toDouble(data.get("productNum")));
        detail.setProductCateNum(toInt(data.get("productCateNum")));
        detail.setRejectionReason((String) data.get("rejectionReason"));
        detail.setRemark((String) data.get("remark"));
        detail.setSource(toInt(data.get("source")));
        detail.setExpArrivalDate((String) data.get("expArrivalDate"));

        // 解析商品明细
        List<Map<String, Object>> products = (List<Map<String, Object>>) data.get("declareProductList");
        if (products != null) {
            List<DeclareProduct> prodList = new ArrayList<>();
            for (Map<String, Object> p : products) {
                DeclareProduct dp = new DeclareProduct();
                dp.setProductCode((String) p.get("productCode"));
                dp.setProductId(toLong(p.get("productId")));
                dp.setProductName((String) p.get("productName"));
                dp.setProductNum(toDouble(p.get("productNum")));
                dp.setProductSpec((String) p.get("productSpec"));
                dp.setProductUnit((String) p.get("productUnit"));
                dp.setPrice(toDouble(p.get("price")));
                dp.setAmount(toDouble(p.get("amount")));
                dp.setAuditAmount(toDouble(p.get("auditAmount")));
                dp.setExamineNum(toDouble(p.get("examineNum")));
                dp.setDiscountAmount(toDouble(p.get("discountAmount")));
                dp.setDiscountPrice(toDouble(p.get("discountPrice")));
                dp.setRequireAmount(toDouble(p.get("requireAmount")));
                dp.setIsGift(toInt(p.get("isGift")));
                dp.setImgUrl((String) p.get("imgUrl"));
                dp.setTagName((String) p.get("tagName"));
                dp.setPerformanceCode((String) p.get("performanceCode"));
                dp.setPerformanceName((String) p.get("performanceName"));
                prodList.add(dp);
            }
            detail.setProducts(prodList);
        }
        return detail;
    }

    /**
     * 创建报货单（智能订货同步企迈）。
     *
     * @param warehouseNo    门店仓库编码（必填，创建报货单使用）
     * @param onlinePay      支付方式：0 线下 1 线上
     * @param orderAttribute 订单属性：1（实测该商户单据属性枚举无 0，传 0 报 160098）
     * @param creator        创建人名称，可为 null
     * @param products       商品清单（productCode=企迈编码，productNum=数量，price=单价）
     */
    @SuppressWarnings("unchecked")
    public DeclareCreateResult createDeclareOrder(String warehouseNo, int onlinePay, int orderAttribute,
                                                  String creator, List<DeclareCreateProduct> products) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("warehouseNo", warehouseNo);
        bizParams.put("onlinePay", onlinePay);
        bizParams.put("orderAttribute", orderAttribute);
        if (creator != null && !creator.isBlank()) {
            bizParams.put("creator", creator);
        }
        bizParams.put("productList", products.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productCode", p.productCode);
            m.put("productNum", p.productNum);
            if (p.price != null) m.put("price", p.price);
            return m;
        }).toList());

        log.info("QMAI_CREATE_DECLARE warehouseNo={} onlinePay={} orderAttribute={} creator={} products={}",
                warehouseNo, onlinePay, orderAttribute, creator, products);

        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/newPattern/scmApiserver/post/declare/order/create";

        Map<String, Object> respBody = doPost(url, body);
        Map<String, Object> data = (Map<String, Object>) respBody.get("data");
        DeclareCreateResult r = new DeclareCreateResult();
        if (data != null) {
            r.declareNoList = data.get("declareNoList") instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList() : List.of();
            r.errorList = data.get("errorList") instanceof List<?> e
                    ? e.stream().map(String::valueOf).toList() : List.of();
        }
        return r;
    }

    /**
     * 9.2.13 查询实时库存列表（总仓/指定仓库）。
     * 智能订货下单前比对用：订单数量不得超过仓库可用库存（availableQuantity）。
     *
     * @param warehouseNoList 仓库编码，最多 5 个；为空查全部仓库
     * @param productCodeList 品项编码，最多 100 个；为空查全部品项
     * @return 库存行列表；接口失败抛 RuntimeException，由调用方降级
     */
    @SuppressWarnings("unchecked")
    public List<WarehouseProductStock> getWarehouseProductStock(List<String> warehouseNoList,
                                                                List<String> productCodeList) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("pageNo", 1);
        bizParams.put("pageSize", 100); // 最大 100，与 productCodeList 上限一致，一次拉全
        bizParams.put("isEmpty", 1);    // 包含 0 库存，缺货品项也要返回
        if (warehouseNoList != null && !warehouseNoList.isEmpty()) {
            bizParams.put("warehouseNoList", warehouseNoList.stream().limit(5).toList());
        }
        if (productCodeList != null && !productCodeList.isEmpty()) {
            bizParams.put("productCodeList", productCodeList.stream().limit(100).toList());
        }

        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/newPattern/scmApiserver/post/warehouse-product/list";

        Map<String, Object> respBody = doPost(url, body);
        Map<String, Object> data = (Map<String, Object>) respBody.get("data");
        List<WarehouseProductStock> stocks = new ArrayList<>();
        if (data == null) return stocks;

        // 兼容 data.list / data.data / data.records 三种列表结构
        Object listObj = data.get("list") != null ? data.get("list")
                : (data.get("data") != null ? data.get("data") : data.get("records"));
        if (listObj instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> r = (Map<String, Object>) o;
                WarehouseProductStock s = new WarehouseProductStock();
                s.setProductCode((String) r.get("productCode"));
                s.setProductName((String) r.get("productName"));
                s.setProductSpec((String) r.get("productSpec"));
                s.setQuantity(toDouble(r.get("quantity")));
                s.setAvailableQuantity(toDouble(r.get("availableQuantity")));
                s.setOccupyQuantity(toDouble(r.get("occupyQuantity")));
                s.setCostPrice(toDouble(r.get("costPrice")));
                s.setStockUnit((String) r.get("stockUnit"));
                s.setWarehouseNo((String) r.get("warehouseNo"));
                s.setWarehouseName((String) r.get("warehouseName"));
                s.setOrgName((String) r.get("orgName"));
                stocks.add(s);
            }
        }
        return stocks;
    }

    /** 测试用：返回详情接口原始 data map */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDeclareOrderDetailRaw(String declareNo) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);
        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("declareNo", declareNo);
        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/scm/order/declare/order/detail";
        Map<String, Object> respBody = doPost(url, body);
        return (Map<String, Object>) respBody.get("data");
    }

    /**
     * 控制台入库单查询（方案 B：交叉验证签收时间）。
     * 调用 inapi.qmai.cn 控制台接口，通过 Cookie 认证，按 warehouseId + 时间范围查询。
     * 成功返回 bizNo → inboundAt 映射 + bizNo → 商品明细映射。
     * 控制台接口调不通时返回空 Map（调用方 fallback 到 updatedAt + 6 天）。
     *
     * @param createdStartAt 创建时间起
     * @param createdEndAt   创建时间止
     * @param warehouseId    仓库 ID（控制台）
     */
    @SuppressWarnings("unchecked")
    public ConsoleInboundResult getConsoleInboundOrders(String createdStartAt, String createdEndAt,
                                                         String warehouseId) {
        ConsoleInboundResult result = new ConsoleInboundResult();
        String url = consoleBaseUrl + "/gw/scm/console/inbound/order/list";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("createdStartAt", createdStartAt);
        body.put("createdEndAt", createdEndAt);
        body.put("pageNo", 1);
        body.put("pageSize", 50);
        body.put("warehouseId", warehouseId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", "qm_seller_token=" + consoleCookie
                    + "; ALL_DATA_SELLERID=" + consoleSellerId);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.debug("Qmai console API request: url={}", url);
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            Map<String, Object> respBody = resp.getBody();
            if (respBody == null) {
                log.warn("Qmai console API returned empty response");
                return result;
            }

            int code = respBody.get("code") instanceof Number
                    ? ((Number) respBody.get("code")).intValue() : -1;
            if (code != 0) {
                log.warn("Qmai console API error: code={}, message={}",
                        code, respBody.getOrDefault("message", "unknown"));
                return result;
            }

            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            if (data == null) return result;

            List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("data");
            if (records == null || records.isEmpty()) return result;

            for (Map<String, Object> r : records) {
                String bizNo = (String) r.get("bizNo");
                if (bizNo == null || bizNo.isBlank()) continue;
                // 只取采购入库（inboundType=3）
                int inboundType = toInt(r.get("inboundType"));
                if (inboundType != 3) continue;
                String inboundAt = (String) r.get("inboundAt");
                if (inboundAt != null && !inboundAt.isBlank()) {
                    result.getInboundTimeMap().put(bizNo, inboundAt);
                }
                // 解析商品明细
                List<Map<String, Object>> prodList = (List<Map<String, Object>>) r.get("inboundProductList");
                if (prodList != null && !prodList.isEmpty()) {
                    List<Map<String, Object>> prods = new ArrayList<>();
                    for (Map<String, Object> p : prodList) {
                        Map<String, Object> pm = new LinkedHashMap<>();
                        pm.put("productName", p.getOrDefault("productName", ""));
                        pm.put("productSpec", p.getOrDefault("productSpec", ""));
                        pm.put("productUnit", p.getOrDefault("productUnit", ""));
                        pm.put("productNum", toDouble(p.get("productNum")));
                        pm.put("examineNum", toDouble(p.get("examineNum")));
                        pm.put("price", toDouble(p.get("price")));
                        pm.put("amount", toDouble(p.get("amount")));
                        pm.put("isGift", toInt(p.get("isGift")));
                        prods.add(pm);
                    }
                    result.getProductMap().put(bizNo, prods);
                }
            }
            log.info("QMAI_CONSOLE_INBOUND warehouseId={} records={} timeMap={} prodMap={}",
                    warehouseId, records.size(), result.getInboundTimeMap().size(),
                    result.getProductMap().size());
        } catch (Exception e) {
            log.warn("Qmai console inbound API failed (will fallback to updatedAt+6d): {}",
                    e.getMessage());
        }
        return result;
    }

    /**
     * 批量查询入库单（9.2.2），响应自带商品明细 inboundProductList。
     * 无门店过滤参数，返回整个商户下的入库单，调用方按 bizNo 自行过滤门店归属。
     * 响应无 total 字段，翻页以"本页不足 pageSize 即结束"为准。
     *
     * @param createdStartAt 创建时间起（含），如 "2026-08-01 00:00:00"
     * @param createdEndAt   创建时间止（含），如 "2026-08-15 23:59:59"
     * @param pageNo         页码（从 1 开始）
     * @param pageSize       每页条数（最大 1000）
     */
    @SuppressWarnings("unchecked")
    public InboundOrderListResult getInboundOrderList(String createdStartAt, String createdEndAt,
                                                      int pageNo, int pageSize) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("createdStartAt", createdStartAt);
        bizParams.put("createdEndAt", createdEndAt);
        bizParams.put("pageNo", pageNo);
        bizParams.put("pageSize", pageSize);

        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/scm/order/inbound/order/list";

        Map<String, Object> respBody = doPost(url, body);
        Object data = respBody.get("data");

        InboundOrderListResult result = new InboundOrderListResult();
        List<?> rawList = data instanceof List<?> l ? l : null;
        if (rawList == null && data instanceof Map<?, ?> m) {
            // 兼容 data.data / data.records 包装
            Object inner = m.get("data");
            if (inner instanceof List<?> l) rawList = l;
            else if (m.get("records") instanceof List<?> l) rawList = l;
        }
        if (rawList == null) {
            return result;
        }

        List<InboundOrderSummary> records = new ArrayList<>();
        for (Object o : rawList) {
            if (!(o instanceof Map<?, ?>)) continue;
            Map<String, Object> r = (Map<String, Object>) o;
            InboundOrderSummary s = new InboundOrderSummary();
            s.setId(toLong(r.get("id")));
            s.setInboundNo((String) r.get("inboundNo"));
            s.setBizNo((String) r.get("bizNo"));
            s.setCreatedAt((String) r.get("createdAt"));
            s.setCreator((String) r.get("creator"));
            s.setInboundAt((String) r.get("inboundAt"));
            s.setInboundPerson((String) r.get("inboundPerson"));
            s.setInboundType(toInt(r.get("inboundType")));
            s.setStatus(toInt(r.get("status")));
            s.setAmount(toDouble(r.get("amount")));
            s.setProductAllNum(toDouble(r.get("productAllNum")));
            s.setProductTypeNum(toInt(r.get("productTypeNum")));
            s.setWarehouseCode((String) r.get("warehouseCode"));
            s.setWarehouseId(r.get("warehouseId") != null ? String.valueOf(r.get("warehouseId")) : null);
            s.setWarehouseName((String) r.get("warehouseName"));
            s.setInOrgName((String) r.get("inOrgName"));
            s.setProviderCode((String) r.get("providerCode"));
            s.setProviderName((String) r.get("providerName"));
            s.setSupplierCode((String) r.get("supplierCode"));
            s.setSupplierName((String) r.get("supplierName"));
            s.setRemark((String) r.get("remark"));

            List<Map<String, Object>> prods = (List<Map<String, Object>>) r.get("inboundProductList");
            if (prods != null) {
                List<InboundProduct> prodList = new ArrayList<>();
                for (Map<String, Object> p : prods) {
                    InboundProduct ip = new InboundProduct();
                    ip.setCostPrice(toDouble(p.get("costPrice")));
                    ip.setInboundAmount(toDouble(p.get("inboundAmount")));
                    ip.setInboundId(toLong(p.get("inboundId")));
                    ip.setInboundNum(toDouble(p.get("inboundNum")));
                    ip.setInboundPrice(toDouble(p.get("inboundPrice")));
                    ip.setNum(toDouble(p.get("num")));
                    ip.setOrderStep(toDouble(p.get("orderStep")));
                    ip.setPartNum(toDouble(p.get("partNum")));
                    ip.setProductCode((String) p.get("productCode"));
                    ip.setProductId(toLong(p.get("productId")));
                    ip.setProductName((String) p.get("productName"));
                    ip.setProductSpec((String) p.get("productSpec"));
                    ip.setProductUnit((String) p.get("productUnit"));
                    ip.setQuantity(toDouble(p.get("quantity")));
                    ip.setStockUnit((String) p.get("stockUnit"));
                    prodList.add(ip);
                }
                s.setProducts(prodList);
            }
            records.add(s);
        }
        result.setRecords(records);
        return result;
    }

    /**
     * 入库单物品数量更新（9.2.3）——官方"确认收货"接口。
     * 失败时抛异常（code != 0 或网络异常），由调用方回滚本地事务。
     *
     * @param inboundNo     入库单号
     * @param inboundType   入库类型（OpenAPI 语义：1仓配 3采购 等）
     * @param warehouseNo   仓库编码（优先，store_info.cangkuid）
     * @param warehouseMark 仓库标识（兜底，控制台 warehouseId）
     * @param operator      操作人，可为 null
     * @param products      物品清单（num=实收数量，price=单价字符串，productCode，productName）
     */
    public void updateInboundOrder(String inboundNo, int inboundType, String warehouseNo,
                                   String warehouseMark, String operator,
                                   List<InboundUpdateProduct> products) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);

        Map<String, Object> bizParams = new LinkedHashMap<>();
        bizParams.put("matchType", 1); // 物品编码匹配
        bizParams.put("inboundNo", inboundNo);
        bizParams.put("inboundType", inboundType);
        if (operator != null && !operator.isBlank()) {
            bizParams.put("operator", operator);
        }
        if (warehouseNo != null && !warehouseNo.isBlank()) {
            bizParams.put("warehouseNo", warehouseNo);
        } else if (warehouseMark != null && !warehouseMark.isBlank()) {
            bizParams.put("warehouseMark", warehouseMark);
        }
        bizParams.put("productList", products.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("num", p.num);
            m.put("price", p.price);
            m.put("productCode", p.productCode);
            m.put("productName", p.productName);
            return m;
        }).toList());

        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/scm/order/inbound/order/update";

        doPost(url, body); // code != 0 抛异常
        log.info("QMAI_INBOUND_UPDATE inboundNo={} type={} warehouseNo={} items={}",
                inboundNo, inboundType, warehouseNo, products.size());
    }

    /**
     * 9.2.4 创建出库单（补发出库联动：outboundType=29 其他出库，autoOutbound=1 立即扣库存）。
     *
     * @param bizParams 业务参数：outboundType/autoOutbound/warehouseNo/externalNo/bizId/remark/productList
     *                  productList 项：productCode/productName/num(库存单位数量)/price(单价必传)
     * @return 出库单号 outboundNo（响应 data）
     * @throws RuntimeException 企迈返回非 0 或响应异常（由调用方决定是否阻断）
     */
    public long createOutboundOrder(Map<String, Object> bizParams) {
        QmaiAuth auth = QmaiSignUtil.makeAuth(openId, grantCode, openKey);
        Map<String, Object> body = buildBody(auth, bizParams);
        String url = baseUrl + "/v3/scm/order/outbound/order/create";
        log.info("QMAI_OUTBOUND_CREATE url={} warehouseNo={} externalNo={} products={}",
                url, bizParams.get("warehouseNo"), bizParams.get("externalNo"),
                bizParams.get("productList"));
        Map<String, Object> resp = doPost(url, body);
        Object data = resp.get("data");
        if (data == null) throw new RuntimeException("Qmai create outbound returned no data: " + resp);
        long outboundNo = data instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(data));
        log.info("QMAI_OUTBOUND_CREATE_OK externalNo={} outboundNo={}", bizParams.get("externalNo"), outboundNo);
        return outboundNo;
    }

    // ==================== 内部工具方法 ====================

    private Map<String, Object> buildBody(QmaiAuth auth, Map<String, Object> bizParams) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openId", openId);
        body.put("grantCode", grantCode);
        body.put("nonce", auth.nonce());
        body.put("timestamp", auth.timestamp());
        body.put("token", auth.token());
        body.put("params", bizParams);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doPost(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.debug("Qmai API request: url={}", url);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class);

        Map<String, Object> respBody = resp.getBody();
        if (respBody == null) {
            throw new RuntimeException("Qmai API returned empty response");
        }

        int code = respBody.get("code") instanceof Number ? ((Number) respBody.get("code")).intValue() : -1;
        if (code != 0) {
            String message = (String) respBody.getOrDefault("message", "unknown error");
            log.warn("Qmai API error: code={} message={} respBody={}", code, message, respBody);
            throw new RuntimeException("Qmai API error: code=" + code + ", message=" + message);
        }
        return respBody;
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return 0.0;
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (Exception ignored) {}
        }
        return 0L;
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
    public static class DeclareOrderListResult {
        private int total;
        private List<DeclareOrderSummary> records = List.of();
    }

    /** 列表摘要（不含商品明细） */
    @Data
    public static class DeclareOrderSummary {
        private String declareNo;
        private String requireNo;       // 订货单号
        private List<String> requireNoList; // 订货单号列表（如 DH/CGSQ 开头）
        private List<String> purchaseApplyNoList; // 采购申请单号列表（备选）
        private List<String> purchaseNoList; // 采购单号列表（CG*，入库单 bizNo 匹配用）
        private List<String> bizNoList; // 业务单号列表
        private String bizNo;           // 业务单号（单数字符串，如 DH20260422000014）
        private String storeWarehouseNo;
        private String storeName;
        private String createdAt;
        private double amount;          // 报货金额，单位：元
        private double freight;         // 运费，单位：元
        private int onlinePay;
        /** 报货单状态：0=待支付 1=待接单 2=已接单 3=履约中 4=已完成 5=已取消 6=已驳回 */
        private int orderStatus;
        private int payStatus;
        /** 来源：1=门店/店长在企迈端手动下单；2=系统 API（智能订货自动提交） */
        private int source;
        private double productNum;      // 报货物品种类数
        private int productCateNum;     // 报货物品种类数
        private String updatedAt;       // 更新时间（用于 48h 过滤已完成订单）
    }

    /** 报货单详情（含商品明细） */
    @Data
    public static class DeclareOrderDetail {
        private String declareNo;
        private String requireNo;       // 订货单号
        private List<String> requireNoList; // 订货单号列表
        private List<String> purchaseApplyNoList; // 采购申请单号列表
        private List<String> bizNoList; // 业务单号列表
        private String bizNo;           // 业务单号（单数字符串）
        private String storeName;
        private String createdAt;
        private double amount;
        private double actualAmount;
        private double auditAmount;
        private double discountAmount;
        private double freight;
        private int freightType;
        private int deliveryType;
        private int onlinePay;
        private int orderStatus;
        private int payStatus;
        private int payType;
        private double productNum;
        private int productCateNum;
        private String rejectionReason;
        private String remark;
        private int source;
        private String expArrivalDate;
        private List<DeclareProduct> products = List.of();
    }

    /** 订货单摘要（require/order/list，报货单按配送中心拆单后的订货单） */
    @Data
    public static class RequireOrderSummary {
        private String requireNo;       // 订货单号（DH*，不同配送中心单号不同）
        private String declareNo;       // 来源报货单号（BH*）
        private String warehouseNo;     // 配送中心仓库编码
        private String warehouseName;   // 配送中心仓库名称
        private String storeWarehouseNo;// 门店仓库编码
        /** 订货单状态：0待审核 1待发货 2已部分发货 3已发货 4已驳回 5已取消 6已完成 */
        private int orderStatus;
        private double amount;          // 订货金额，单位：元
        private double productNum;      // 订货品种数
        private String orderAt;         // 订货时间
    }

    @Data
    public static class DeclareProduct {
        private String productCode;
        private long productId;
        private String productName;
        private double productNum;      // 报货数量
        private String productSpec;     // 物品规格
        private String productUnit;     // 订货单位
        private double price;           // 单价，单位：元
        private double amount;          // 金额，单位：元
        private double auditAmount;     // 审核金额
        private double examineNum;      // 审核数量
        private double discountAmount;  // 优惠金额
        private double discountPrice;   // 优惠后单价
        private double requireAmount;   // 订货金额
        private int isGift;            // 是否赠品 1=是 0=否
        private String performanceCode; // 业绩归属编码（配送中心 PSCK* / 供应商 GYS*，与订货单 warehouseNo 关联）
        private String imgUrl;
        private String tagName;
        private String performanceName; // 履约方名称
    }

    // ==================== 控制台入库单（方案 B）DTO ====================

    @Data
    public static class ConsoleInboundResult {
        /** bizNo → inboundAt 映射 */
        private Map<String, String> inboundTimeMap = new LinkedHashMap<>();
        /** bizNo → 商品明细映射 */
        private Map<String, List<Map<String, Object>>> productMap = new LinkedHashMap<>();
    }

    // ==================== 入库单（9.2.2 / 9.2.3）DTO ====================

    /** 9.2.2 批量查询入库单结果 */
    @Data
    public static class InboundOrderListResult {
        private List<InboundOrderSummary> records = List.of();
    }

    /** 9.2.2 入库单摘要（含商品明细） */
    @Data
    public static class InboundOrderSummary {
        private long id;
        private String inboundNo;       // 入库单号
        private String bizNo;           // 业务单据（采购单号/订货单号等，用于匹配门店归属）
        private String createdAt;
        private String creator;
        private String inboundAt;       // 入库时间（待入库时为空）
        private String inboundPerson;
        /** OpenAPI 语义：1仓配 2盘盈 3采购 4返配 5销退 6调拨 */
        private int inboundType;
        /** 1待入库 2已入库 3已作废 */
        private int status;
        private double amount;
        private double productAllNum;
        private int productTypeNum;
        private String warehouseCode;   // 开放平台仓库编码（warehouseNo，确认收货回传用）
        private String warehouseId;     // 控制台仓库ID（warehouseMark 兜底）
        private String warehouseName;   // 入库仓库
        private String inOrgName;       // 入库机构
        private String providerCode;
        private String providerName;
        private String supplierCode;
        private String supplierName;
        private String remark;
        private List<InboundProduct> products = List.of();
    }

    /** 9.2.2 入库物品明细 */
    @Data
    public static class InboundProduct {
        private double costPrice;       // 成本单价
        private double inboundAmount;   // 入库金额
        private long inboundId;
        private double inboundNum;      // 入库数量
        private double inboundPrice;    // 入库单价
        private double num;             // 件数
        private double orderStep;       // 订货步长
        private double partNum;         // 零数
        private String productCode;     // 物品编码
        private long productId;
        private String productName;
        private String productSpec;
        private String productUnit;     // 订货单位
        private double quantity;        // 当前库存
        private String stockUnit;       // 库存单位
    }

    /** 9.2.3 物品数量更新清单项 */
    @Data
    public static class InboundUpdateProduct {
        private double num;             // 实收数量
        private String price;           // 单价（字符串，必填）
        private String productCode;     // 物品编码
        private String productName;     // 物品名称（必填）
    }

    // ==================== 实时库存 DTO（9.2.13） ====================

    /** 实时库存行：按 productCode 关联，下单校验用 availableQuantity，下单价格用 costPrice */
    @Data
    public static class WarehouseProductStock {
        private String productCode;
        private String productName;
        private String productSpec;
        private double quantity;          // 库存量
        private double availableQuantity; // 可用库存量（最常用）
        private double occupyQuantity;    // 占用量
        private double costPrice;         // 成本单价（元，报货单单价口径，实测一致）
        private String stockUnit;         // 库存单位
        private String warehouseNo;
        private String warehouseName;
        private String orgName;
    }

    // ==================== 创建报货单 DTO ====================

    /** 创建报货单商品 */
    @Data
    public static class DeclareCreateProduct {
        private String productCode;
        private double productNum;
        private BigDecimal price;
    }

    /** 创建报货单结果：declareNoList 为空/errorList 非空均视为失败 */
    @Data
    public static class DeclareCreateResult {
        private List<String> declareNoList = List.of();
        private List<String> errorList = List.of();
    }
}
