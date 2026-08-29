package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.feishu.alert.FeishuAlertContext;
import com.xzcpc.common.feishu.alert.FeishuAlertProperties;
import com.xzcpc.common.feishu.alert.FeishuWebhookAlertService;
import com.xzcpc.mp.client.QmaiClient;
import com.xzcpc.mp.client.QmaiClient.DeclareOrderSummary;
import com.xzcpc.mp.client.QmaiClient.DeclareProduct;
import com.xzcpc.mp.client.QmaiClient.InboundOrderSummary;
import com.xzcpc.mp.client.QmaiClient.InboundProduct;
import com.xzcpc.mp.client.QmaiClient.InboundUpdateProduct;
import com.xzcpc.mp.entity.InboundOrder;
import com.xzcpc.mp.entity.InboundOrderItem;
import com.xzcpc.mp.mapper.InboundOrderItemMapper;
import com.xzcpc.mp.mapper.InboundOrderMapper;
import com.xzcpc.mp.service.MpInboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 入库管理。
 * <p>
 * 同步：OpenAPI 9.2.2 批量查询入库单（仅 1仓配入库/3采购入库），
 * bizNo 匹配报货单归属门店，按 inbound_no upsert，本地收货字段不被覆盖。
 * 收货：OpenAPI 9.2.3 物品数量更新 —— 本地更新与企迈调用同事务（严格一致），
 * 企迈失败抛异常回滚本地并飞书告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpInboundServiceImpl implements MpInboundService {

    private final InboundOrderMapper orderMapper;
    private final InboundOrderItemMapper itemMapper;
    private final QmaiClient qmaiClient;
    private final JdbcTemplate jdbcTemplate;
    private final FeishuWebhookAlertService feishuAlert;
    private final FeishuAlertProperties feishuAlertProperties;

    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 同步类型白名单：1仓配入库 3采购入库（OpenAPI 语义） */
    private static final Set<Integer> SYNC_INBOUND_TYPES = Set.of(1, 3);

    @Override
    @Transactional
    public int syncFromQmai(String storeId, Long qmaiStoreId, int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        String startStr = start.format(DTF);
        String endStr = end.format(DTF);

        // 0. 本店仓库编码 + 门店名（二级/三级归属匹配用）
        String cangkuid = getStoreCangkuid(storeId);
        String storeName = getStoreName(storeId);

        // 1. 该门店近 N 天报货单（按 qmai_store_id 拉）→ 归属匹配键 + 本店仓库编码集
        //    匹配窗口放宽到至少 30 天：入库单 bizNo 可能指向更早的采购申请单（挂账老单）
        LocalDateTime declareEnd = end;
        LocalDateTime declareStart = end.minusDays(Math.max(days, 30));
        String declareStartStr = declareStart.format(DTF);
        String declareEndStr = declareEnd.format(DTF);
        Set<String> bizKeys = new HashSet<>();
        Set<String> storeWarehouseNos = new HashSet<>();
        if (qmaiStoreId != null && qmaiStoreId > 0) {
            try {
                var declareResult = qmaiClient.getDeclareOrderList(qmaiStoreId, declareStartStr, declareEndStr, 1, 50);
                if (declareResult.getRecords() != null) {
                    for (DeclareOrderSummary d : declareResult.getRecords()) {
                        addIfText(bizKeys, d.getDeclareNo());
                        addIfText(bizKeys, d.getRequireNo());
                        addIfText(bizKeys, d.getBizNo());
                        addAllIfNotNull(bizKeys, d.getRequireNoList());
                        addAllIfNotNull(bizKeys, d.getPurchaseApplyNoList());
                        addAllIfNotNull(bizKeys, d.getPurchaseNoList());
                        addAllIfNotNull(bizKeys, d.getBizNoList());
                        addIfText(storeWarehouseNos, d.getStoreWarehouseNo());
                    }
                }
            } catch (Exception e) {
                log.warn("INBOUND_SYNC 拉取报货单失败，跳过 bizNo/仓库归属匹配 storeId={}: {}", storeId, e.getMessage());
            }
        }
        if (cangkuid != null) storeWarehouseNos.add(cangkuid);

        // 2. OpenAPI 9.2.2 分页拉全量入库单，按类型白名单 + 三级归属过滤：
        //    ① bizNo ∈ 本店报货单键集（报货/订货/采购申请/采购单号，报货单按 qmai_store_id 拉）
        //    ② warehouseCode ∈ 本店仓库编码集（storeWarehouseNo ∪ cangkuid；总仓直采 CG 单无报货单可关联）
        //    ③ 归一化仓库名匹配本店店名（兜底）
        int newCount = 0;
        int pageNo = 1;
        while (pageNo <= 50) { // 安全上限：50 页 × 1000 = 5 万条
            var result = qmaiClient.getInboundOrderList(startStr, endStr, pageNo, 1000);
            List<InboundOrderSummary> records = result.getRecords();
            if (records.isEmpty()) break;
            for (InboundOrderSummary rec : records) {
                if (!SYNC_INBOUND_TYPES.contains(rec.getInboundType())) continue;
                if (!belongsToStore(rec, bizKeys, storeWarehouseNos, storeName)) continue;
                if (upsertInboundOrder(storeId, qmaiStoreId, rec)) newCount++;
            }
            if (records.size() < 1000) break;
            pageNo++;
        }
        log.info("INBOUND_SYNC storeId={} days={} new={}", storeId, days, newCount);
        return newCount;
    }

    /**
     * 入库单归属判定（三级）：
     * ① bizNo ∈ 报货单键集（报货单按 qmai_store_id 拉取）；
     * ② warehouseCode ∈ 本店仓库编码集（报货单 storeWarehouseNo ∪ store_info.cangkuid）；
     * ③ 归一化仓库名 ≈ 本店店名。
     * 企迈两套编号互不相通（报货单带 CGSQ 采购申请号，入库单带 CG 采购单号），
     * 且一店可有多个仓库，故总仓直采采购单靠仓库编码/名称兜底。
     */
    private boolean belongsToStore(InboundOrderSummary rec, Set<String> bizKeys,
                                   Set<String> storeWarehouseNos, String storeName) {
        if (rec.getBizNo() != null && bizKeys.contains(rec.getBizNo())) return true;
        if (rec.getWarehouseCode() != null && storeWarehouseNos.contains(rec.getWarehouseCode())) return true;
        if (storeName == null) return false;
        String wh = normalizeName(rec.getWarehouseName());
        if (wh.isEmpty()) return false;
        if (wh.equals(normalizeName(storeName))) return true;
        return fuzzyNameMatch(wh, normalizeName(storeName));
    }

    /** 归一化：去括号（全角/半角）、空白（含全角空格），用于企迈仓库名 ↔ 本地门店名比对 */
    private static String normalizeName(String s) {
        if (s == null) return "";
        return s.replaceAll("[()（）\\s　]", "");
    }

    /**
     * 模糊比对：归一化后一方以另一方开头/结尾（如 毕节店↔毕节招商花园店、彝人古镇店↔楚雄彝人古镇店）。
     * 较短一方长度 ≥2 才算命中，避免"店"字单字误配。
     */
    private static boolean fuzzyNameMatch(String a, String b) {
        if (a.equals(b)) return true;
        String longer = a.length() >= b.length() ? a : b;
        String shorter = a.length() < b.length() ? a : b;
        if (shorter.length() < 2) return false;
        return longer.startsWith(shorter) || longer.endsWith(shorter);
    }

    /**
     * 按 inbound_no upsert；新单返回 true。
     * 已存在的单只更新企迈侧字段，本地收货字段（localStatus/receivedAt/receivedBy/明细 received）不动；
     * 仅当本地还是 pending 时随企迈状态联动（已入库/已作废）。
     */
    private boolean upsertInboundOrder(String storeId, Long qmaiStoreId, InboundOrderSummary rec) {
        if (rec.getInboundNo() == null || rec.getInboundNo().isBlank()) return false;
        InboundOrder exist = orderMapper.selectOne(new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getInboundNo, rec.getInboundNo()));

        if (exist == null) {
            InboundOrder order = new InboundOrder();
            order.setStoreId(storeId);
            order.setQmaiStoreId(qmaiStoreId);
            order.setWarehouseId(rec.getWarehouseId());
            order.setWarehouseCode(rec.getWarehouseCode());
            order.setWarehouseName(rec.getWarehouseName());
            order.setInboundNo(rec.getInboundNo());
            order.setBizNo(rec.getBizNo());
            order.setSourceRequireNo(rec.getBizNo());
            order.setInboundAt(parseDateTime(rec.getInboundAt()));
            order.setInboundType(rec.getInboundType());
            order.setStatus(rec.getStatus());
            // 9.2.2 金额单位为分 → 统一存元（与 H5 显示、控制台历史数据一致）
            order.setAmount(fenToYuan(rec.getAmount()));
            order.setProductAllNum(BigDecimal.valueOf(rec.getProductAllNum()));
            order.setProductTypeNum(rec.getProductTypeNum());
            order.setCreator(rec.getCreator());
            order.setInboundPerson(rec.getInboundPerson());
            order.setRemark(rec.getRemark());
            order.setCreatedAtQmai(parseDateTime(rec.getCreatedAt()));
            order.setLocalStatus(mapQmaiStatus(rec.getStatus()));
            orderMapper.insert(order);
            insertItems(order.getId(), rec.getProducts());
            return true;
        }

        exist.setQmaiStoreId(qmaiStoreId);
        exist.setWarehouseId(rec.getWarehouseId());
        exist.setWarehouseCode(rec.getWarehouseCode());
        exist.setWarehouseName(rec.getWarehouseName());
        exist.setInboundAt(parseDateTime(rec.getInboundAt()));
        exist.setInboundType(rec.getInboundType());
        exist.setStatus(rec.getStatus());
        exist.setAmount(fenToYuan(rec.getAmount()));
        exist.setProductAllNum(BigDecimal.valueOf(rec.getProductAllNum()));
        exist.setProductTypeNum(rec.getProductTypeNum());
        exist.setCreator(rec.getCreator());
        exist.setInboundPerson(rec.getInboundPerson());
        exist.setRemark(rec.getRemark());
        if ("pending".equals(exist.getLocalStatus())) {
            exist.setLocalStatus(mapQmaiStatus(rec.getStatus()));
        }
        orderMapper.updateById(exist);

        // 明细回填：本地还没有明细时补（收货字段在明细上，已有明细不覆盖）
        Long itemCount = itemMapper.selectCount(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundOrderId, exist.getId()));
        if (itemCount == null || itemCount == 0) {
            insertItems(exist.getId(), rec.getProducts());
        }
        return false;
    }

    /** 9.2.2 物品明细 → 本地明细（入库数量为应收数量，入库单价为单价） */
    private void insertItems(Long orderId, List<InboundProduct> products) {
        if (products == null) return;
        for (InboundProduct p : products) {
            if (p.getProductCode() == null || p.getProductCode().isBlank()) continue;
            InboundOrderItem item = new InboundOrderItem();
            item.setInboundOrderId(orderId);
            item.setProductCode(p.getProductCode());
            item.setProductId(p.getProductId() > 0 ? p.getProductId() : null);
            item.setProductName(p.getProductName());
            item.setProductSpec(p.getProductSpec());
            item.setProductUnit(p.getProductUnit());
            item.setProductNum(BigDecimal.valueOf(p.getInboundNum() > 0 ? p.getInboundNum() : p.getNum()));
            // 9.2.2 单价/成本价单位为分 → 统一存元
            BigDecimal price = fenToYuan(p.getInboundPrice() > 0 ? p.getInboundPrice() : p.getCostPrice());
            item.setPrice(price);
            item.setAmount(item.getProductNum().multiply(price));
            item.setReceivedQty(BigDecimal.ZERO);
            item.setReceived(0);
            itemMapper.insert(item);
        }
    }

    /** OpenAPI status：1待入库→pending 2已入库→done 3已作废→cancelled */
    private String mapQmaiStatus(int status) {
        return switch (status) {
            case 1 -> "pending";
            case 2 -> "done";
            default -> "cancelled";
        };
    }

    @Override
    public Map<String, Object> list(String storeId, String keyword, String status,
                                     int pageNum, int pageSize) {
        LambdaQueryWrapper<InboundOrder> qw = new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getStoreId, storeId);

        if (StringUtils.hasText(status) && !"all".equals(status)) {
            qw.eq(InboundOrder::getLocalStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(InboundOrder::getInboundNo, keyword)
                    .or().like(InboundOrder::getBizNo, keyword)
                    .or().like(InboundOrder::getSourceDeclareNo, keyword)
                    .or().like(InboundOrder::getSourceRequireNo, keyword));
        }
        qw.orderByDesc(InboundOrder::getCreatedAtQmai);

        Page<InboundOrder> page = orderMapper.selectPage(
                new Page<>(pageNum, pageSize), qw);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        return result;
    }

    @Override
    public Map<String, Object> detail(Long id, String storeId) {
        InboundOrder order = orderMapper.selectById(id);
        if (order == null || !order.getStoreId().equals(storeId)) {
            throw new BusinessException("入库单不存在");
        }

        // 懒加载商品明细：优先本地，为空时通过 OpenAPI 报货单详情补（老数据兜底）
        List<InboundOrderItem> items = getItems(id);
        if (items.isEmpty() && order.getSourceDeclareNo() != null) {
            try {
                var detail = qmaiClient.getDeclareOrderDetail(order.getSourceDeclareNo());
                if (detail.getProducts() != null) {
                    for (DeclareProduct p : detail.getProducts()) {
                        InboundOrderItem item = new InboundOrderItem();
                        item.setInboundOrderId(order.getId());
                        item.setProductCode(p.getProductCode());
                        item.setProductId(p.getProductId());
                        item.setProductName(p.getProductName());
                        item.setProductSpec(p.getProductSpec());
                        item.setProductUnit(p.getProductUnit());
                        item.setProductNum(BigDecimal.valueOf(p.getProductNum()));
                        item.setPrice(BigDecimal.valueOf(p.getPrice()));
                        item.setAmount(BigDecimal.valueOf(p.getAmount()));
                        item.setIsGift(p.getIsGift());
                        item.setImgUrl(p.getImgUrl());
                        item.setPerformanceName(p.getPerformanceName());
                        item.setReceivedQty(BigDecimal.ZERO);
                        item.setReceived(0);
                        itemMapper.insert(item);
                    }
                }
                items = getItems(id);
            } catch (Exception e) {
                log.warn("Failed to sync items for inboundNo={}", order.getInboundNo(), e);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("items", items);
        return result;
    }

    @Override
    @Transactional
    public InboundOrder quickInbound(Long id, String storeId, String receivedBy) {
        InboundOrder order = orderMapper.selectById(id);
        if (order == null || !order.getStoreId().equals(storeId)) {
            throw new BusinessException("入库单不存在");
        }
        if (!order.canReceive()) {
            throw new BusinessException("当前状态不可入库");
        }

        List<InboundOrderItem> items = getItems(id);
        for (InboundOrderItem item : items) {
            item.setReceivedQty(item.getProductNum());
            item.setReceived(1);
            itemMapper.updateById(item);
        }

        order.setLocalStatus("done");
        order.setReceivedAt(LocalDateTime.now());
        order.setReceivedBy(receivedBy);
        orderMapper.updateById(order);

        // 严格一致：同事务内同步企迈 9.2.3，失败抛异常回滚本地
        pushQimaiReceive(order, items, receivedBy);

        return order;
    }

    @Override
    @Transactional
    public InboundOrderItem receiveItem(Long orderId, Long itemId, String storeId,
                                         BigDecimal qty, String operator) {
        InboundOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getStoreId().equals(storeId)) {
            throw new BusinessException("入库单不存在");
        }
        if (!order.canReceive()) {
            throw new BusinessException("当前状态不可收货");
        }

        InboundOrderItem item = itemMapper.selectById(itemId);
        if (item == null || !item.getInboundOrderId().equals(orderId)) {
            throw new BusinessException("商品不存在");
        }
        if (item.getReceived() != null && item.getReceived() == 1) {
            throw new BusinessException("该商品已收货");
        }

        item.setReceivedQty(qty != null ? qty : item.getProductNum());
        item.setReceived(1);
        itemMapper.updateById(item);

        List<InboundOrderItem> allItems = getItems(orderId);
        boolean allReceived = allItems.stream()
                .allMatch(i -> i.getReceived() != null && i.getReceived() == 1);
        if (allReceived) {
            order.setLocalStatus("done");
            order.setReceivedAt(LocalDateTime.now());
            if (order.getReceivedBy() == null || order.getReceivedBy().isBlank()) {
                order.setReceivedBy(operator);
            }
        }
        orderMapper.updateById(order);

        // 严格一致：同事务内同步企迈 9.2.3，失败抛异常回滚本地
        pushQimaiReceive(order, List.of(item), operator);

        return item;
    }

    @Override
    @Transactional
    public int batchReceive(Long orderId, List<Long> itemIds, String storeId, String receivedBy) {
        InboundOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getStoreId().equals(storeId)) {
            throw new BusinessException("入库单不存在");
        }
        if (!order.canReceive()) {
            throw new BusinessException("当前状态不可收货");
        }
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException("请先勾选物料");
        }

        List<InboundOrderItem> affected = new ArrayList<>();
        for (Long itemId : itemIds) {
            InboundOrderItem item = itemMapper.selectById(itemId);
            if (item == null || !item.getInboundOrderId().equals(orderId)) {
                continue; // 不属于该单的物料，忽略
            }
            if (item.getReceived() != null && item.getReceived() == 1) {
                continue;
            }
            item.setReceivedQty(item.getProductNum());
            item.setReceived(1);
            itemMapper.updateById(item);
            affected.add(item);
        }

        List<InboundOrderItem> allItems = getItems(orderId);
        boolean allReceived = allItems.stream()
                .allMatch(i -> i.getReceived() != null && i.getReceived() == 1);
        if (allReceived) {
            order.setLocalStatus("done");
            order.setReceivedAt(LocalDateTime.now());
            order.setReceivedBy(receivedBy);
        }
        orderMapper.updateById(order);

        // 严格一致：同事务内同步企迈 9.2.3，失败抛异常回滚本地
        pushQimaiReceive(order, affected, receivedBy);

        return affected.size();
    }

    @Override
    public List<InboundOrderItem> getItems(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundOrderId, orderId));
    }

    // ==================== 企迈收货同步（9.2.3） ====================

    /**
     * 收货同步企迈：本地更新后同事务内调用，企迈失败 → 飞书告警 + 抛业务异常回滚本地。
     *
     * @param items 本次新收货的物料（含实收数量）
     */
    private void pushQimaiReceive(InboundOrder order, List<InboundOrderItem> items, String operator) {
        if (items == null || items.isEmpty()) return;
        if (order.getInboundNo() == null || order.getInboundNo().isBlank()) {
            throw new BusinessException("入库单缺少企迈单号，无法同步企迈");
        }
        if (order.getInboundType() == null) {
            throw new BusinessException("入库单缺少入库类型，无法同步企迈");
        }

        List<InboundUpdateProduct> products = new ArrayList<>(items.size());
        for (InboundOrderItem item : items) {
            String name = item.getProductName() != null ? item.getProductName() : "未知物料";
            if (item.getProductCode() == null || item.getProductCode().isBlank()) {
                throw new BusinessException("物料「" + name + "」缺少企迈物品编码，请先重新同步入库单明细");
            }
            InboundUpdateProduct p = new InboundUpdateProduct();
            BigDecimal qty = item.getReceivedQty() != null ? item.getReceivedQty()
                    : (item.getProductNum() != null ? item.getProductNum() : BigDecimal.ZERO);
            p.setNum(qty.doubleValue());
            p.setPrice(item.getPrice() != null ? item.getPrice().toPlainString() : "0");
            p.setProductCode(item.getProductCode());
            p.setProductName(name);
            products.add(p);
        }

        try {
            // warehouseNo 优先用入库单自身的仓库编码（收货仓库即订单仓库），
            // 老数据无 warehouseCode 时退回 store_info.cangkuid，再退回 warehouseMark（控制台仓库ID）
            String warehouseNo = (order.getWarehouseCode() != null && !order.getWarehouseCode().isBlank())
                    ? order.getWarehouseCode() : getStoreCangkuid(order.getStoreId());
            qmaiClient.updateInboundOrder(order.getInboundNo(), order.getInboundType(),
                    warehouseNo, order.getWarehouseId(), operator, products);
        } catch (Exception e) {
            alertQimaiFailure(order, e);
            throw new BusinessException("同步企迈收货失败，请稍后重试");
        }
    }

    /** 门店企迈仓库编码（OpenAPI warehouseNo）；查询失败返回 null，调用方兜底 warehouseMark */
    private String getStoreCangkuid(String storeId) {
        try {
            String wn = jdbcTemplate.queryForObject(
                    "SELECT cangkuid FROM store_info WHERE store_id=? AND del_flag=0", String.class, storeId);
            return (wn != null && !wn.isBlank()) ? wn : null;
        } catch (Exception e) {
            log.warn("查询门店仓库编码失败 storeId={}: {}", storeId, e.getMessage());
            return null;
        }
    }

    /** 门店名称（归属匹配三级用）；查询失败返回 null */
    private String getStoreName(String storeId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT store_name FROM store_info WHERE store_id=? AND del_flag=0", String.class, storeId);
        } catch (Exception e) {
            log.warn("查询门店名称失败 storeId={}: {}", storeId, e.getMessage());
            return null;
        }
    }

    /** 企迈收货同步失败 → 飞书群告警（异步，不阻塞回滚流程） */
    private void alertQimaiFailure(InboundOrder order, Throwable e) {
        try {
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("POST")
                    .requestPath("/api/mp/inbound/orders/" + order.getId() + "/receive")
                    .userHint("门店=" + order.getStoreId() + " 入库单=" + order.getInboundNo())
                    .appName(feishuAlertProperties.getAppName())
                    .environment(activeProfile)
                    .build();
            feishuAlert.notify(ctx, e);
        } catch (Exception ex) {
            log.warn("企迈失败告警发送异常: {}", ex.getMessage());
        }
    }

    // ==================== 内部工具方法 ====================

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s, DTF);
        } catch (Exception e) {
            return null;
        }
    }

    /** 9.2.2 金额/单价单位为分 → 统一存元（保留 2 位），与 H5 显示及控制台历史数据一致 */
    private BigDecimal fenToYuan(double fen) {
        if (fen <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static void addIfText(Set<String> set, String v) {
        if (v != null && !v.isBlank()) set.add(v.trim());
    }

    private static void addAllIfNotNull(Set<String> set, List<String> list) {
        if (list != null) {
            for (String v : list) addIfText(set, v);
        }
    }
}
