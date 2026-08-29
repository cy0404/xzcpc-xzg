package com.xzcpc.mp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.client.QmaiClient;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.BatchApproveReq;
import com.xzcpc.mp.dto.DailyLossCreateReq;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.service.LossReportService;
import com.xzcpc.mp.service.LossStandardService;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.mapper.StoreMapper;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialConversionRule;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialConversionRuleMapper;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/mp/loss-report")
@RequiredArgsConstructor
public class MpLossReportController {

    private final LossReportService lossReportService;
    private final LossStandardService lossStandardService;
    private final MaterialMapper materialMapper;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialConversionRuleMapper conversionRuleMapper;
    private final QmaiClient qmaiClient;
    private final StoreMapper storeMapper;

    @org.springframework.beans.factory.annotation.Value("${app.public-url:}")
    private String publicUrl;

    @OpLog(module = "小程序-报损", operation = "新建")
    @PostMapping
    public R<LossReport> create(@RequestBody LossReport report) {
        var user = UserContextHolder.get();
        return R.ok(lossReportService.create(report, user.getOpenid()));
    }

    /** 日常多物料报损 */
    @OpLog(module = "小程序-报损", operation = "日常多物料报损")
    @PostMapping("/daily")
    public R<LossReport> createDaily(@Valid @RequestBody DailyLossCreateReq req) {
        return R.ok(lossReportService.createDaily(req));
    }

    @GetMapping("/overview")
    public R<List<Map<String, Object>>> overview(@RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            String openid = UserContextHolder.get().getOpenid();
            return R.ok(lossReportService.overviewByStores(openid));
        }
        return R.ok(List.of());
    }

    @GetMapping("/list")
    public R<Page<LossReport>> list(
            @RequestParam(required = false) String lossType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(lossReportService.pageByStore(storeId, lossType, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<LossReport> detail(@PathVariable Long id) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(lossReportService.detail(id, storeId));
    }

    /** 待审批列表 */
    @GetMapping("/approval-list")
    public R<Page<LossReport>> approvalList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(lossReportService.pageApprovalByStore(storeId, pageNum, pageSize));
    }

    /** 审批通过 */
    @PostMapping("/{id}/approve")
    public R<LossReport> approve(@PathVariable Long id) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(lossReportService.approve(id, storeId));
    }

    /** 审批拒绝 */
    @PostMapping("/{id}/reject-approval")
    public R<Void> rejectApproval(@PathVariable Long id) {
        String storeId = UserContextHolder.get().getStoreId();
        lossReportService.rejectApproval(id, storeId);
        return R.ok();
    }

    /** 批量审批（通过/拒绝），已处理或非本店记录自动跳过 */
    @PostMapping("/batch-approve")
    public R<Map<String, Object>> batchApprove(@RequestBody BatchApproveReq req) {
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(lossReportService.batchApprove(req.getIds(), req.getAction(), storeId));
    }

    /** 修改日常报损 */
    @OpLog(module = "小程序-报损", operation = "修改日常报损")
    @PutMapping("/{id}")
    public R<LossReport> updateDaily(@PathVariable Long id, @Valid @RequestBody DailyLossCreateReq req) {
        return R.ok(lossReportService.updateDaily(id, req));
    }

    /** 删除日常报损 */
    @OpLog(module = "小程序-报损", operation = "删除日常报损")
    @DeleteMapping("/{id}")
    public R<Void> deleteDaily(@PathVariable Long id) {
        var user = UserContextHolder.get();
        lossReportService.deleteDaily(id, user.getStoreId(), user.getEmployeeName());
        return R.ok();
    }

    @GetMapping("/containers")
    public R<Object> containers() {
        return R.ok(lossReportService.listContainers(UserContextHolder.get().getStoreId()));
    }

    /** 搜索物料（报损用，搜全表，返回可选单位+换算提示） */
    @GetMapping("/materials/search")
    public R<Object> searchMaterials(@RequestParam(defaultValue = "") String keyword,
                                     @RequestParam(defaultValue = "") String lossObject) {
        var qw = new LambdaQueryWrapper<Material>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Material::getMaterialName, keyword).or().like(Material::getQmCode, keyword));
        } else {
            qw.eq(Material::getLossVisible, 1); // 默认只展示白名单物料
        }
        // 到货报损剔除半成品
        if ("finished".equals(lossObject)) {
            qw.notLike(Material::getCategory, "半成品");
        }
        qw.orderByAsc(Material::getMaterialName).last("LIMIT 30");
        List<Material> materials = materialMapper.selectList(qw);
        List<String> matIds = materials.stream().map(Material::getMaterialId).filter(Objects::nonNull).toList();
        Map<String, MaterialInventoryRule> ruleMap = matIds.isEmpty() ? Map.of()
                : ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>().in(MaterialInventoryRule::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));
        // 批量取换算规则
        List<String> ruleIds = ruleMap.values().stream().map(MaterialInventoryRule::getRuleId).filter(Objects::nonNull).toList();
        Map<String, List<MaterialConversionRule>> convMap = ruleIds.isEmpty() ? Map.of()
                : conversionRuleMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>().in(MaterialConversionRule::getRuleId, ruleIds))
                .stream().collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));
        List<Map<String, Object>> list = materials.stream().map(m -> {
            MaterialInventoryRule rule = ruleMap.get(m.getMaterialId());
            String baseUnit = rule != null && rule.getBaseUnit() != null ? rule.getBaseUnit() : "";
            String stockUnit = rule != null && rule.getStockUnit() != null ? rule.getStockUnit() : "";
            // 收集 unit 类型换算中的所有单位
            Set<String> unitSet = new LinkedHashSet<>();
            if (!baseUnit.isEmpty()) unitSet.add(baseUnit);
            List<MaterialConversionRule> convs = convMap.getOrDefault(rule != null ? rule.getRuleId() : "", List.of());
            for (MaterialConversionRule cr : convs) {
                if ("unit".equals(cr.getConversionType())) {
                    if (cr.getFromUnit() != null) unitSet.add(cr.getFromUnit());
                    if (cr.getToUnit() != null) unitSet.add(cr.getToUnit());
                }
            }
            List<String> units = new ArrayList<>(unitSet);
            // 换算提示
            List<Map<String, String>> unitInfos = new ArrayList<>();
            for (String u : units) {
                Map<String, String> info = new LinkedHashMap<>();
                info.put("unit", u);
                if (u.equals(baseUnit)) {
                    info.put("hint", "1" + baseUnit);
                } else {
                    BigDecimal factor = computeConversionFactor(u, baseUnit, convs);
                    if (factor != null) {
                        info.put("hint", "1" + u + "=" + fmtFactor(factor) + baseUnit);
                    } else {
                        info.put("hint", "1" + u);
                    }
                }
                unitInfos.add(info);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialId", m.getMaterialId());
            item.put("materialName", m.getMaterialName());
            item.put("spec", m.getSpec() != null ? m.getSpec() : "");
            item.put("category", m.getCategory() != null ? m.getCategory() : "");
            item.put("baseUnit", baseUnit);
            item.put("stockUnit", stockUnit);
            item.put("units", units);
            item.put("unitInfos", unitInfos);
            item.put("unitPrice", rule != null ? rule.getUnitPrice() : null);
            return item;
        }).toList();
        return R.ok(Map.of("list", list));
    }

    /** BFS 换算链 */
    private BigDecimal computeConversionFactor(String inputUnit, String baseUnit, List<MaterialConversionRule> rules) {
        if (rules.isEmpty()) return null;
        Map<String, List<ConversionEdge>> graph = new HashMap<>();
        for (MaterialConversionRule r : rules) {
            if (!"unit".equals(r.getConversionType())) continue;
            BigDecimal f = r.getToQuantity().divide(r.getFromQuantity(), 10, RoundingMode.HALF_UP);
            BigDecimal b = r.getFromQuantity().divide(r.getToQuantity(), 10, RoundingMode.HALF_UP);
            graph.computeIfAbsent(r.getFromUnit(), k -> new ArrayList<>()).add(new ConversionEdge(r.getToUnit(), f));
            graph.computeIfAbsent(r.getToUnit(), k -> new ArrayList<>()).add(new ConversionEdge(r.getFromUnit(), b));
        }
        if (!graph.containsKey(inputUnit)) return null;
        Map<String, BigDecimal> visited = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.put(inputUnit, BigDecimal.ONE);
        queue.add(inputUnit);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            BigDecimal curFactor = visited.get(cur);
            if (cur.equals(baseUnit)) return curFactor;
            for (ConversionEdge e : graph.getOrDefault(cur, List.of())) {
                if (visited.containsKey(e.toUnit)) continue;
                visited.put(e.toUnit, curFactor.multiply(e.ratio));
                queue.add(e.toUnit);
            }
        }
        return null;
    }

    private String fmtFactor(BigDecimal f) {
        if (f == null) return "?";
        return f.stripTrailingZeros().toPlainString();
    }

    private record ConversionEdge(String toUnit, BigDecimal ratio) {}

    /** 操作日志 */
    @GetMapping("/{id}/logs")
    public R<List<Map<String, Object>>> logs(@PathVariable Long id) {
        return R.ok(lossReportService.getLogs(id));
    }

    /** 已收货 */
    @PostMapping("/{id}/receive")
    public R<Void> receive(@PathVariable Long id, @RequestBody Map<String, String> body) {
        var user = UserContextHolder.get();
        lossReportService.receive(id, user.getStoreId(), user.getEmployeeName(), body.getOrDefault("remark", ""));
        return R.ok();
    }

    /** 未收到货 */
    @PostMapping("/{id}/not-receive")
    public R<Void> notReceive(@PathVariable Long id, @RequestBody Map<String, String> body) {
        var user = UserContextHolder.get();
        lossReportService.notReceive(id, user.getStoreId(), user.getEmployeeName(), body.getOrDefault("remark", ""));
        return R.ok();
    }

    /** 追加凭证URL（后台上传完成后调用） */
    @SuppressWarnings("unchecked")
    @PostMapping("/{id}/append-voucher")
    public R<Void> appendVoucher(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LossReport r = lossReportService.getById(id);
        if (r == null || !r.getStoreId().equals(UserContextHolder.get().getStoreId())) {
            return R.fail(403, "报损记录不存在");
        }
        Object urlObj = body.get("url");
        if (urlObj == null) return R.ok();
        String newUrl;
        if (urlObj instanceof Map) {
            newUrl = (String) ((Map<String, Object>) urlObj).get("url");
        } else {
            newUrl = urlObj.toString();
        }
        if (!StringUtils.hasText(newUrl)) return R.ok();
        String existing = r.getVoucherUrl() != null ? r.getVoucherUrl() : "";
        r.setVoucherUrl(existing.isEmpty() ? newUrl : existing + "," + newUrl);
        lossReportService.updateById(r);
        return R.ok();
    }

    /** 移除凭证URL */
    @PostMapping("/{id}/remove-voucher")
    public R<Void> removeVoucher(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LossReport r = lossReportService.getById(id);
        if (r == null || !r.getStoreId().equals(UserContextHolder.get().getStoreId())) {
            return R.fail(403, "报损记录不存在");
        }
        String removeUrl = body.get("url");
        if (removeUrl == null || removeUrl.isEmpty()) return R.ok();
        String existing = r.getVoucherUrl() != null ? r.getVoucherUrl() : "";
        List<String> urls = new java.util.ArrayList<>(java.util.Arrays.asList(existing.split(",")));
        urls.removeIf(u -> u.equals(removeUrl));
        r.setVoucherUrl(String.join(",", urls));
        lossReportService.updateById(r);
        return R.ok();
    }

    /** 厂家拒绝后重新提交：覆盖更新原记录，状态重置为 pending */
    @PostMapping("/{id}/resubmit")
    public R<LossReport> resubmit(@PathVariable Long id, @RequestBody LossReport body) {
        var user = UserContextHolder.get();
        LossReport r = lossReportService.getById(id);
        if (r == null || !r.getStoreId().equals(user.getStoreId())) {
            return R.fail(403, "报损记录不存在");
        }
        if (!"rejected".equals(r.getStatus())) {
            return R.fail(400, "仅已拒绝的报损可重新提交");
        }
        // 覆盖更新字段
        r.setMaterialId(body.getMaterialId());
        r.setMaterialName(body.getMaterialName());
        r.setSpec(body.getSpec());
        r.setInputUnit(body.getInputUnit());
        r.setInputQty(body.getInputQty());
        r.setUnitPrice(body.getUnitPrice());
        r.setTotalAmount(body.getTotalAmount());
        r.setQimaiOrderNo(body.getQimaiOrderNo());
        r.setReason(body.getReason());
        r.setRemark(body.getRemark());
        r.setVoucherUrl(body.getVoucherUrl());
        r.setUrgent(body.getUrgent());
        r.setLossObject(body.getLossObject() != null ? body.getLossObject() : "finished");
        // 重置状态
        r.setStatus("pending");
        r.setRejectReason(null);
        r.setUpdatedAt(java.time.LocalDateTime.now());
        lossReportService.updateById(r);
        return R.ok(r);
    }

    /** 关闭已拒绝的报损 */
    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        var user = UserContextHolder.get();
        LossReport r = lossReportService.getById(id);
        if (r == null || !r.getStoreId().equals(user.getStoreId())) {
            return R.fail(403, "报损记录不存在");
        }
        if (!"rejected".equals(r.getStatus())) {
            return R.fail(400, "仅已拒绝的报损可关闭");
        }
        r.setStatus("closed");
        r.setUpdatedAt(java.time.LocalDateTime.now());
        lossReportService.updateById(r);
        return R.ok();
    }

    /**
     * 查询当前门店的企迈报货单，供到货验收报损 H5 选择订单号。
     * - 不传 declareNo：返回报货单列表（近 30 天摘要，不含商品明细）
     * - 传 declareNo：返回该报货单详情（含完整商品明细）
     */
    @GetMapping("/qmai-declare-orders")
    public R<?> qmaiDeclareOrders(@RequestParam(required = false) String declareNo) {
        String storeId = UserContextHolder.get().getStoreId();

        // 查本地 store_info 获取企迈门店 ID
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getStoreId, storeId));
        if (store == null || store.getQmaiStoreId() == null) {
            return R.fail(400, "当前门店未绑定企迈门店，请联系管理员");
        }

        try {
            if (declareNo != null && !declareNo.isBlank()) {
                // 查询详情（含商品明细）
                return R.ok(buildDetailResult(qmaiClient.getDeclareOrderDetail(declareNo)));
            } else {
                // 查询列表（含入库单商品明细，按 bizNo 分组）
                return R.ok(buildListResult(store.getQmaiStoreId()));
            }
        } catch (Exception e) {
            log.error("Failed to query Qmai declare orders for storeId={}, qmaiStoreId={}",
                    storeId, store.getQmaiStoreId(), e);
            return R.fail(500, "查询企迈报货单失败：" + e.getMessage());
        }
    }

    private Map<String, Object> buildListResult(long qmaiStoreId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String startStr = start.atStartOfDay().format(dtf);
        String endStr = end.atTime(23, 59, 59).format(dtf);

        QmaiClient.DeclareOrderListResult result = qmaiClient.getDeclareOrderList(
                qmaiStoreId, startStr, endStr, 1, 50);

        // 已完成(4)的订单过滤逻辑：
        // - 非采购单（无 purchaseApplyNoList）：updatedAt 超过 48h 不展示
        // - 采购单（有 purchaseApplyNoList）：先调控制台入库单 API 拿 inboundAt
        //   成功 → 用 inboundAt 判断 48h
        //   失败 → 用 updatedAt + 6 天判断
        java.time.LocalDateTime cutoff48h = java.time.LocalDateTime.now().minusHours(48);
        java.time.LocalDateTime cutoff6d = java.time.LocalDateTime.now().minusDays(6);

        // 收集采购订单的 warehouseNo，查控制台入库单
        Set<String> warehouseNos = new LinkedHashSet<>();
        boolean hasPurchaseOrder = false;
        for (QmaiClient.DeclareOrderSummary order : result.getRecords()) {
            if (order.getPurchaseApplyNoList() != null && !order.getPurchaseApplyNoList().isEmpty()) {
                hasPurchaseOrder = true;
                if (order.getStoreWarehouseNo() != null && !order.getStoreWarehouseNo().isBlank()) {
                    warehouseNos.add(order.getStoreWarehouseNo());
                }
            }
        }

        // 控制台入库单查询（方案 B）
        Map<String, String> consoleTimeMap = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> consoleProductMap = new LinkedHashMap<>();
        boolean consoleOk = false;
        if (hasPurchaseOrder && !warehouseNos.isEmpty()) {
            for (String whNo : warehouseNos) {
                try {
                    QmaiClient.ConsoleInboundResult cir = qmaiClient.getConsoleInboundOrders(
                            startStr, endStr, whNo);
                    if (!cir.getInboundTimeMap().isEmpty()) {
                        consoleTimeMap.putAll(cir.getInboundTimeMap());
                        consoleOk = true;
                    }
                    if (!cir.getProductMap().isEmpty()) {
                        consoleProductMap.putAll(cir.getProductMap());
                    }
                } catch (Exception e) {
                    log.warn("Console inbound API failed for warehouseNo={}: {}", whNo, e.getMessage());
                }
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (QmaiClient.DeclareOrderSummary order : result.getRecords()) {
            // 已完成(4)：过滤逻辑
            if (order.getOrderStatus() == 4) {
                boolean isPurchase = order.getPurchaseApplyNoList() != null
                        && !order.getPurchaseApplyNoList().isEmpty();
                if (isPurchase && consoleOk) {
                    // 方案 B 成功：用 inboundAt 判断 48h
                    String inboundAt = null;
                    for (String bizNo : order.getBizNoList() != null ? order.getBizNoList() : List.<String>of()) {
                        inboundAt = consoleTimeMap.get(bizNo);
                        if (inboundAt != null) break;
                    }
                    if (inboundAt == null) {
                        for (String pa : order.getPurchaseApplyNoList()) {
                            inboundAt = consoleTimeMap.get(pa);
                            if (inboundAt != null) break;
                        }
                    }
                    if (inboundAt != null && !inboundAt.isBlank()) {
                        try {
                            java.time.LocalDateTime ref = java.time.LocalDateTime.parse(inboundAt, dtf);
                            if (ref.isBefore(cutoff48h)) continue;
                        } catch (Exception ignored) {}
                    }
                } else if (isPurchase) {
                    // 方案 B 失败：用 updatedAt + 6 天
                    String updatedAt = order.getUpdatedAt();
                    if (updatedAt != null && !updatedAt.isBlank()) {
                        try {
                            java.time.LocalDateTime ref = java.time.LocalDateTime.parse(updatedAt, dtf);
                            if (ref.isBefore(cutoff6d)) continue;
                        } catch (Exception ignored) {}
                    }
                } else {
                    // 非采购单：用 updatedAt 判断 48h
                    String updatedAt = order.getUpdatedAt();
                    if (updatedAt != null && !updatedAt.isBlank()) {
                        try {
                            java.time.LocalDateTime ref = java.time.LocalDateTime.parse(updatedAt, dtf);
                            if (ref.isBefore(cutoff48h)) continue;
                        } catch (Exception ignored) {}
                    }
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("declareNo", order.getDeclareNo());
            item.put("requireNo", order.getRequireNo());
            item.put("requireNoList", order.getRequireNoList());
            item.put("purchaseApplyNoList", order.getPurchaseApplyNoList());
            item.put("bizNoList", order.getBizNoList());
            item.put("bizNo", order.getBizNo());
            item.put("storeWarehouseNo", order.getStoreWarehouseNo());
            item.put("createdAt", order.getCreatedAt());
            item.put("amount", order.getAmount());
            item.put("freight", order.getFreight());
            item.put("orderStatus", order.getOrderStatus());
            item.put("payStatus", order.getPayStatus());
            item.put("productNum", order.getProductNum());
            item.put("productCateNum", order.getProductCateNum());
            item.put("updatedAt", order.getUpdatedAt());
            item.put("statusText", statusText(order.getOrderStatus()));
            list.add(item);
        }
        // DEBUG: 打印第一单的字段
        if (!list.isEmpty()) {
            Map<String, Object> first = list.get(0);
            log.info("QMAI_LIST_DEBUG qmaiStoreId={} declareNo={} requireNoList={} purchaseApplyNoList={} bizNoList={} bizNo={} requireNo={}",
                    qmaiStoreId,
                    first.get("declareNo"), first.get("requireNoList"), first.get("purchaseApplyNoList"),
                    first.get("bizNoList"), first.get("bizNo"), first.get("requireNo"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orders", list);
        response.put("instoreProductMap", consoleProductMap);
        return response;
    }

    private Map<String, Object> buildDetailResult(QmaiClient.DeclareOrderDetail detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("declareNo", detail.getDeclareNo());
        result.put("requireNo", detail.getRequireNo());
        result.put("requireNoList", detail.getRequireNoList());
        result.put("purchaseApplyNoList", detail.getPurchaseApplyNoList());
        result.put("bizNoList", detail.getBizNoList());
        result.put("bizNo", detail.getBizNo());
        result.put("storeName", detail.getStoreName());
        result.put("createdAt", detail.getCreatedAt());
        result.put("amount", detail.getAmount());
        result.put("actualAmount", detail.getActualAmount());
        result.put("freight", detail.getFreight());
        result.put("orderStatus", detail.getOrderStatus());
        result.put("payStatus", detail.getPayStatus());
        result.put("productNum", detail.getProductNum());
        result.put("productCateNum", detail.getProductCateNum());
        result.put("statusText", statusText(detail.getOrderStatus()));
        result.put("remark", detail.getRemark() != null ? detail.getRemark() : "");

        // 批量收集 productCode，一次性查本地物料
        List<String> codes = detail.getProducts().stream()
                .map(QmaiClient.DeclareProduct::getProductCode)
                .filter(Objects::nonNull)
                .distinct().toList();
        Map<String, Material> matByCode = codes.isEmpty() ? Map.of()
                : materialMapper.selectList(new LambdaQueryWrapper<Material>()
                        .in(Material::getQmCode, codes)
                        .eq(Material::getDelFlag, 0))
                .stream().collect(Collectors.toMap(Material::getQmCode, m -> m, (a, b) -> a));
        Map<String, MaterialInventoryRule> ruleByMatId = matByCode.isEmpty() ? Map.of()
                : ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                        .in(MaterialInventoryRule::getMaterialId, matByCode.values().stream().map(Material::getMaterialId).toList())
                        .eq(MaterialInventoryRule::getDelFlag, 0))
                .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));
        Map<String, List<MaterialConversionRule>> convByRuleId = ruleByMatId.isEmpty() ? Map.of()
                : conversionRuleMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .in(MaterialConversionRule::getRuleId, ruleByMatId.values().stream().map(MaterialInventoryRule::getRuleId).toList())
                        .eq(MaterialConversionRule::getDelFlag, 0))
                .stream().collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));

        List<Map<String, Object>> prods = new ArrayList<>();
        for (QmaiClient.DeclareProduct p : detail.getProducts()) {
            Map<String, Object> pd = new LinkedHashMap<>();
            pd.put("productCode", p.getProductCode());
            pd.put("productId", p.getProductId());
            pd.put("productName", p.getProductName());
            pd.put("productNum", p.getProductNum());
            pd.put("productSpec", p.getProductSpec() != null ? p.getProductSpec() : "");
            pd.put("productUnit", p.getProductUnit() != null ? p.getProductUnit() : "");
            pd.put("price", p.getPrice());
            pd.put("amount", p.getAmount());
            pd.put("examineNum", p.getExamineNum());
            pd.put("isGift", p.getIsGift());
            pd.put("tagName", p.getTagName() != null ? p.getTagName() : "");
            // 匹配本地物料（通过 productCode ↔ qm_code）
            Map<String, Object> matched = buildMatchedMaterial(p.getProductCode(), matByCode, ruleByMatId, convByRuleId);
            if (matched != null) pd.put("matchedMaterial", matched);
            prods.add(pd);
        }
        result.put("products", prods);
        return result;
    }

    /** 通过企迈 productCode 匹配本地物料，返回单位/换算/价格信息 */
    private Map<String, Object> buildMatchedMaterial(String productCode,
                                                      Map<String, Material> matByCode,
                                                      Map<String, MaterialInventoryRule> ruleByMatId,
                                                      Map<String, List<MaterialConversionRule>> convByRuleId) {
        if (productCode == null || productCode.isBlank()) return null;
        Material m = matByCode.get(productCode);
        if (m == null) return null;
        MaterialInventoryRule rule = ruleByMatId.get(m.getMaterialId());
        String baseUnit = rule != null && rule.getBaseUnit() != null ? rule.getBaseUnit() : "";
        String stockUnit = rule != null && rule.getStockUnit() != null ? rule.getStockUnit() : "";
        // 收集单位
        Set<String> unitSet = new LinkedHashSet<>();
        if (!baseUnit.isEmpty()) unitSet.add(baseUnit);
        List<MaterialConversionRule> convs = convByRuleId.getOrDefault(rule != null ? rule.getRuleId() : "", List.of());
        for (MaterialConversionRule cr : convs) {
            if ("unit".equals(cr.getConversionType())) {
                if (cr.getFromUnit() != null) unitSet.add(cr.getFromUnit());
                if (cr.getToUnit() != null) unitSet.add(cr.getToUnit());
            }
        }
        List<String> units = new ArrayList<>(unitSet);
        // 换算提示
        List<Map<String, String>> unitInfos = new ArrayList<>();
        for (String u : units) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("unit", u);
            if (u.equals(baseUnit)) {
                info.put("hint", "1" + baseUnit);
            } else {
                BigDecimal factor = computeConversionFactor(u, baseUnit, convs);
                if (factor != null) {
                    info.put("hint", "1" + u + "=" + fmtFactor(factor) + baseUnit);
                } else {
                    info.put("hint", "1" + u);
                }
            }
            unitInfos.add(info);
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("materialId", m.getMaterialId());
        item.put("materialName", m.getMaterialName());
        item.put("spec", m.getSpec() != null ? m.getSpec() : "");
        item.put("category", m.getCategory() != null ? m.getCategory() : "");
        item.put("baseUnit", baseUnit);
        item.put("stockUnit", stockUnit);
        item.put("units", units);
        item.put("unitInfos", unitInfos);
        item.put("unitPrice", rule != null ? rule.getUnitPrice() : null);
        return item;
    }

    /** 报货单状态文本映射 */
    private static String statusText(int status) {
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "待接单";
            case 2 -> "已接单";
            case 3 -> "履约中";
            case 4 -> "已完成";
            case 5 -> "已取消";
            case 6 -> "已驳回";
            default -> "未知(" + status + ")";
        };
    }

    /** 根据物料ID查询验收标准 */
    @GetMapping("/standard/{materialId}")
    public R<Map<String, Object>> getStandard(@PathVariable String materialId) {
        // 查询物料分类
        String category = "";
        try {
            Material m = materialMapper.selectById(materialId);
            if (m != null && m.getCategory() != null) category = m.getCategory();
        } catch (Exception ignored) {}

        Map<String, Object> data = lossStandardService.getByMaterial(materialId, category);

        // 拼接 media_urls 完整URL前缀
        String base = publicUrl.isBlank() ? "" : publicUrl;
        for (String key : data.keySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) data.get(key);
            @SuppressWarnings("unchecked")
            List<String> urls = (List<String>) item.get("mediaUrls");
            if (urls != null && !base.isBlank()) {
                List<String> fullUrls = new java.util.ArrayList<>();
                for (String url : urls) {
                    if (!url.startsWith("http")) {
                        fullUrls.add(base + (url.startsWith("/") ? "" : "/") + url);
                    } else {
                        fullUrls.add(url);
                    }
                }
                item.put("mediaUrls", fullUrls);
            }
        }
        return R.ok(data);
    }

}
