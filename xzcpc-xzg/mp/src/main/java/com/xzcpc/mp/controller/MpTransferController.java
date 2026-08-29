package com.xzcpc.mp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.TransferCreateReq;
import com.xzcpc.mp.util.ConversionFactorUtil;
import com.xzcpc.mp.entity.TransferOrder;
import com.xzcpc.mp.entity.TransferOrderItem;
import com.xzcpc.mp.entity.TransferReturnRecord;
import com.xzcpc.mp.mapper.TransferReturnRecordMapper;
import com.xzcpc.mp.service.TransferOrderService;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialConversionRule;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialConversionRuleMapper;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mp/transfer")
@RequiredArgsConstructor
public class MpTransferController {

    private final TransferOrderService transferOrderService;
    private final MaterialMapper materialMapper;
    private final TransferReturnRecordMapper returnRecordMapper;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialConversionRuleMapper conversionMapper;

    @OpLog(module = "小程序-调货", operation = "发起调货")
    @PostMapping
    public R<TransferOrder> create(@Valid @RequestBody TransferCreateReq req) {
        return R.ok(transferOrderService.create(req));
    }

    @OpLog(module = "小程序-调货", operation = "确认调货")
    @PutMapping("/{id}/confirm")
    public R<TransferOrder> confirm(@PathVariable Long id) {
        return R.ok(transferOrderService.confirm(id));
    }

    @OpLog(module = "小程序-调货", operation = "发货")
    @PutMapping("/{id}/ship")
    public R<TransferOrder> ship(@PathVariable Long id) {
        return R.ok(transferOrderService.ship(id));
    }

    @OpLog(module = "小程序-调货", operation = "确认收货")
    @PutMapping("/{id}/receive")
    public R<TransferOrder> receive(@PathVariable Long id) {
        return R.ok(transferOrderService.receive(id));
    }

    @OpLog(module = "小程序-调货", operation = "取消调货")
    @PutMapping("/{id}/cancel")
    public R<TransferOrder> cancel(@PathVariable Long id) {
        return R.ok(transferOrderService.cancel(id));
    }

    @OpLog(module = "小程序-调货", operation = "拒绝调货")
    @PutMapping("/{id}/reject")
    public R<TransferOrder> reject(@PathVariable Long id) {
        return R.ok(transferOrderService.reject(id));
    }

    @GetMapping("/list")
    public R<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "false") boolean all) {
        Page<TransferOrder> page;
        if (all) {
            String openid = UserContextHolder.get().getOpenid();
            page = transferOrderService.pageByStores(openid, status, pageNum, pageSize);
        } else {
            String storeId = UserContextHolder.get().getStoreId();
            page = transferOrderService.pageByStore(storeId, status, pageNum, pageSize);
        }
        // 批量加载物料摘要
        Map<Long, List<TransferOrderItem>> itemsMap;
        if (page.getRecords().isEmpty()) {
            itemsMap = Map.of();
        } else {
            List<Long> ids = page.getRecords().stream().map(TransferOrder::getId).toList();
            itemsMap = transferOrderService.getItemsBatch(ids);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        result.put("itemsMap", itemsMap);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        TransferOrder order = transferOrderService.detail(id, null);
        var items = transferOrderService.getItems(id);
        // 填充归还信息
        var records = returnRecordMapper.selectList(
                new LambdaQueryWrapper<TransferReturnRecord>()
                        .eq(TransferReturnRecord::getTransferId, id));
        for (TransferOrderItem item : items) {
            BigDecimal goodsQty = records.stream()
                    .filter(r -> r.getItemId().equals(item.getId()) && "goods".equals(r.getReturnType()))
                    .map(r -> r.getReturnQty() != null ? r.getReturnQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal moneyQty = records.stream()
                    .filter(r -> r.getItemId().equals(item.getId()) && "money".equals(r.getReturnType()))
                    .map(r -> r.getReturnQty() != null ? r.getReturnQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.setReturnedQty(goodsQty.add(moneyQty));
            BigDecimal moneyAmount = records.stream()
                    .filter(r -> r.getItemId().equals(item.getId()) && "money".equals(r.getReturnType()))
                    .map(r -> r.getReturnAmount() != null ? r.getReturnAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.setReturnAmount(moneyAmount);
            if (item.getReturnedQty().compareTo(item.getTransferQty()) >= 0) {
                item.setReturnStatus("returned_" + (moneyQty.compareTo(BigDecimal.ZERO) > 0 ? "money" : "goods"));
            }
        }
        return R.ok(Map.of("order", order, "items", items, "returnRecords", records));
    }

    @GetMapping("/overview")
    public R<?> overview(@RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            String openid = UserContextHolder.get().getOpenid();
            return R.ok(transferOrderService.overviewByStores(openid));
        }
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(transferOrderService.overview(storeId));
    }

    @GetMapping("/overview-total")
    public R<Map<String, Long>> overviewTotal() {
        String openid = UserContextHolder.get().getOpenid();
        return R.ok(transferOrderService.overviewTotal(openid));
    }

    /** 搜索物料（调货用，返回盘点单位串+换算关系，半成品名后缀） */
    @GetMapping("/materials/search")
    public R<Map<String, Object>> searchMaterials(
            @RequestParam(defaultValue = "") String keyword) {
        var qw = new LambdaQueryWrapper<Material>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Material::getMaterialName, keyword)
                    .or().like(Material::getQmCode, keyword));
        }
        qw.orderByAsc(Material::getMaterialName).last("LIMIT 100");
        List<Material> materials = materialMapper.selectList(qw);

        List<String> matIds = materials.stream()
                .map(Material::getMaterialId).filter(Objects::nonNull).toList();
        Map<String, MaterialInventoryRule> ruleMap = matIds.isEmpty() ? Map.of()
                : ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                        .in(MaterialInventoryRule::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));

        // 批量取换算规则
        List<String> ruleIds = ruleMap.values().stream()
                .map(MaterialInventoryRule::getRuleId).filter(Objects::nonNull).distinct().toList();
        Map<String, List<MaterialConversionRule>> convMap = ruleIds.isEmpty() ? Map.of()
                : conversionMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .in(MaterialConversionRule::getRuleId, ruleIds))
                .stream().collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));

        List<Map<String, Object>> list = materials.stream().map(m -> {
            MaterialInventoryRule rule = ruleMap.get(m.getMaterialId());
            String baseUnit = rule != null ? rule.getBaseUnit() : "";
            java.math.BigDecimal unitPrice = rule != null ? rule.getUnitPrice() : null;

            String displayName = m.getMaterialName();
            String cat = m.getCategory();
            if (cat != null && cat.contains("半成品")) {
                displayName = displayName + "（半成品）";
            }

            // 只收集 unit 类型换算中涉及的单位（不含称重）
            java.util.Set<String> unitSet = new java.util.LinkedHashSet<>();
            if (baseUnit != null && !baseUnit.isEmpty()) unitSet.add(baseUnit);
            List<MaterialConversionRule> convs = convMap.getOrDefault(rule != null ? rule.getRuleId() : "", List.of());
            for (MaterialConversionRule cr : convs) {
                if ("unit".equals(cr.getConversionType())) {
                    if (cr.getFromUnit() != null) unitSet.add(cr.getFromUnit());
                    if (cr.getToUnit() != null) unitSet.add(cr.getToUnit());
                }
            }
            List<String> units = new ArrayList<>(unitSet);

            // 计算每个单位的单价和换算提示
            Map<String, java.math.BigDecimal> unitPrices = new LinkedHashMap<>();
            List<Map<String, String>> unitInfos = new ArrayList<>();
            for (String u : units) {
                Map<String, String> info = new LinkedHashMap<>();
                info.put("unit", u);
                if (u.equals(baseUnit)) {
                    unitPrices.put(u, unitPrice);
                    info.put("hint", "1" + baseUnit);
                } else {
                    java.math.BigDecimal factor = null;
                    if (unitPrice != null) {
                        factor = ConversionFactorUtil.computeConversionFactor(u, baseUnit, convs);
                        unitPrices.put(u, factor != null ? unitPrice.multiply(factor) : unitPrice);
                    }
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
            item.put("materialName", displayName);
            item.put("qmCode", m.getQmCode() != null ? m.getQmCode() : "");
            item.put("spec", m.getSpec() != null ? m.getSpec() : "");
            item.put("category", cat != null ? cat : "");
            item.put("baseUnit", baseUnit);
            item.put("unitPrice", unitPrice);
            item.put("units", units);
            item.put("unitPrices", unitPrices);
            item.put("unitInfos", unitInfos);
            return item;
        }).toList();

        return R.ok(Map.of("list", list));
    }

    /** 格式化换算系数，如 "200" 或 "0.5" */
    private String fmtFactor(java.math.BigDecimal f) {
        if (f == null) return "?";
        return f.stripTrailingZeros().toPlainString();
    }
}
