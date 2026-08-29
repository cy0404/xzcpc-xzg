package com.xzcpc.mp.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.client.QmaiClient;
import com.xzcpc.mp.client.QmaiClient.DeclareOrderListResult;
import com.xzcpc.mp.client.QmaiClient.DeclareOrderDetail;
import com.xzcpc.mp.client.QmaiClient.DeclareOrderSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 临时测试接口：直调 Qmai API 查看响应字段。
 * 用法: ?qmaiStoreId=196065  或  ?declareNo=BH20260811000149
 */
@Slf4j
@RestController
@RequestMapping("/api/mp/public/qmai-test")
@RequiredArgsConstructor
public class QmaiTestController {

    private final QmaiClient qmaiClient;

    @GetMapping
    public R<?> test(@RequestParam(required = false) Long qmaiStoreId,
                     @RequestParam(required = false) String declareNo) {
        try {
            if (declareNo != null && !declareNo.isBlank()) {
                DeclareOrderDetail detail = qmaiClient.getDeclareOrderDetail(declareNo);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("declareNo", detail.getDeclareNo());
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("declareNo", detail.getDeclareNo());
                f.put("requireNo", detail.getRequireNo());
                f.put("requireNoList", detail.getRequireNoList());
                f.put("purchaseApplyNoList", detail.getPurchaseApplyNoList());
                f.put("bizNoList", detail.getBizNoList());
                f.put("bizNo", detail.getBizNo());
                f.put("storeName", detail.getStoreName());
                f.put("createdAt", detail.getCreatedAt());
                f.put("amount", detail.getAmount());
                f.put("orderStatus", detail.getOrderStatus());
                List<Map<String, Object>> fields = new ArrayList<>();
                for (Map.Entry<String, Object> e : f.entrySet()) {
                    Object v = e.getValue();
                    fields.add(Map.of("field", e.getKey(), "value", v != null ? v.toString() : "NULL", "type", v != null ? v.getClass().getSimpleName() : "null"));
                }
                response.put("detailFields", fields);
                // 直接 dump 原始 API 返回看看
                try {
                    var raw = qmaiClient.getDeclareOrderDetailRaw(declareNo);
                    response.put("rawDataKeys", new ArrayList<>(raw.keySet()));
                } catch (Exception ignored) {}
                return R.ok(response);
            }

            if (qmaiStoreId == null) {
                return R.fail(400, "请传 qmaiStoreId 或 declareNo");
            }

            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(30);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startStr = start.atStartOfDay().format(dtf);
            String endStr = end.atTime(23, 59, 59).format(dtf);

            DeclareOrderListResult result = qmaiClient.getDeclareOrderList(qmaiStoreId, startStr, endStr, 1, 3);

            if (result.getRecords() == null || result.getRecords().isEmpty()) {
                return R.fail(500, "No orders for qmaiStoreId=" + qmaiStoreId);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("qmaiStoreId", qmaiStoreId);
            response.put("total", result.getTotal());

            DeclareOrderSummary first = result.getRecords().get(0);
            Map<String, Object> allFields = new LinkedHashMap<>();
            allFields.put("declareNo", first.getDeclareNo());
            allFields.put("requireNo", first.getRequireNo());
            allFields.put("requireNoList", first.getRequireNoList());
            allFields.put("purchaseApplyNoList", first.getPurchaseApplyNoList());
            allFields.put("bizNoList", first.getBizNoList());
            allFields.put("bizNo", first.getBizNo());
            allFields.put("storeName", first.getStoreName());
            allFields.put("createdAt", first.getCreatedAt());
            allFields.put("amount", first.getAmount());
            allFields.put("orderStatus", first.getOrderStatus());

            List<Map<String, Object>> fields = new ArrayList<>();
            for (Map.Entry<String, Object> e : allFields.entrySet()) {
                Object v = e.getValue();
                fields.add(Map.of("field", e.getKey(), "value", v != null ? v.toString() : "NULL", "type", v != null ? v.getClass().getSimpleName() : "null"));
            }
            response.put("firstOrderFields", fields);

            return R.ok(response);
        } catch (Exception e) {
            log.error("Qmai test failed", e);
            return R.fail(500, "Qmai test failed: " + e.getMessage());
        }
    }
}
