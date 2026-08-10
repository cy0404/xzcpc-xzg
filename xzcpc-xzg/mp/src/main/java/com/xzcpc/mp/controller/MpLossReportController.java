package com.xzcpc.mp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.DailyLossCreateReq;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.service.LossReportService;
import com.xzcpc.mp.service.LossStandardService;
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
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mp/loss-report")
@RequiredArgsConstructor
public class MpLossReportController {

    private final LossReportService lossReportService;
    private final LossStandardService lossStandardService;
    private final MaterialMapper materialMapper;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialConversionRuleMapper conversionRuleMapper;

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
