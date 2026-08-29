package com.xzcpc.mp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.service.MpInboundService;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 入库管理 — 从企迈 OpenAPI 同步入库单并追踪收货入库。
 */
@Slf4j
@RestController
@RequestMapping("/api/mp/inbound")
@RequiredArgsConstructor
public class MpInboundController {

    private final MpInboundService inboundService;
    private final StoreMapper storeMapper;

    /**
     * 入库单列表（分页 + 搜索 + 筛选）。
     */
    @GetMapping("/orders")
    public R<?> list(@RequestParam(defaultValue = "") String keyword,
                     @RequestParam(defaultValue = "all") String status,
                     @RequestParam(defaultValue = "1") int pageNum,
                     @RequestParam(defaultValue = "20") int pageSize) {
        String storeId = UserContextHolder.get().getStoreId();
        Map<String, Object> result = inboundService.list(storeId, keyword, status, pageNum, pageSize);
        return R.ok(result);
    }

    /**
     * 入库单详情（含商品明细）。
     */
    @GetMapping("/orders/{id}")
    public R<?> detail(@PathVariable Long id) {
        String storeId = UserContextHolder.get().getStoreId();
        Map<String, Object> detail = inboundService.detail(id, storeId);
        return R.ok(detail);
    }

    /**
     * 从企迈 OpenAPI 同步入库单（9.2.2，近2天）。
     * 仅同步 1仓配入库/3采购入库，通过 bizNo 匹配报货单归属当前门店。
     * 定时任务（每日凌晨3点）另走 InboundSyncJob，此接口用于小程序下拉刷新。
     */
    @PostMapping("/orders/sync")
    @OpLog(module = "入库管理", operation = "同步企迈入库单")
    public R<?> sync() {
        String storeId = UserContextHolder.get().getStoreId();
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getStoreId, storeId));
        if (store == null) {
            throw new BusinessException("门店不存在");
        }
        if (store.getQmaiStoreId() == null || store.getQmaiStoreId() <= 0) {
            throw new BusinessException("当前门店未配置企迈门店ID，请联系管理员");
        }
        int count = inboundService.syncFromQmai(storeId, store.getQmaiStoreId(), 2);
        return R.ok(Map.of("synced", count, "message", "成功同步 " + count + " 条入库单"));
    }

    /**
     * 一键入库。
     */
    @PostMapping("/orders/{id}/quick")
    @OpLog(module = "入库管理", operation = "一键入库")
    public R<?> quickInbound(@PathVariable Long id) {
        String storeId = UserContextHolder.get().getStoreId();
        inboundService.quickInbound(id, storeId, currentOperator());
        return R.ok(Map.of("message", "入库完成"));
    }

    /**
     * 单件商品收货确认。
     */
    @PostMapping("/orders/{orderId}/receive-item/{itemId}")
    @OpLog(module = "入库管理", operation = "商品收货确认")
    public R<?> receiveItem(@PathVariable Long orderId,
                            @PathVariable Long itemId,
                            @RequestParam(required = false) Double qtyParam,
                            @RequestBody(required = false) Map<String, Object> body) {
        // H5 前端以 JSON body 传 {qty}，@RequestParam 读不到 → 必须兼容 body；
        // 保留 query 传参兼容旧调用。
        Double qty = qtyParam;
        if (qty == null && body != null && body.get("qty") != null) {
            qty = Double.parseDouble(body.get("qty").toString());
        }
        String storeId = UserContextHolder.get().getStoreId();
        BigDecimal receiveQty = qty != null ? BigDecimal.valueOf(qty) : null;
        inboundService.receiveItem(orderId, itemId, storeId, receiveQty, currentOperator());
        return R.ok(Map.of("message", "收货确认成功"));
    }

    /**
     * 批量收货：勾选多件物料，全部全额收货（单事务）。
     */
    @PostMapping("/orders/{orderId}/receive-items")
    @OpLog(module = "入库管理", operation = "批量收货确认")
    public R<?> receiveItems(@PathVariable Long orderId,
                             @RequestBody List<Long> itemIds) {
        String storeId = UserContextHolder.get().getStoreId();
        int count = inboundService.batchReceive(orderId, itemIds, storeId, currentOperator());
        return R.ok(Map.of("message", "已收货 " + count + " 件物料", "count", count));
    }

    /** 操作人：优先店名（同步企迈 operator 字段），空则退回 openid */
    private String currentOperator() {
        LoginUser user = UserContextHolder.get();
        return (user.getStoreName() != null && !user.getStoreName().isBlank())
                ? user.getStoreName() : user.getOpenid();
    }
}
