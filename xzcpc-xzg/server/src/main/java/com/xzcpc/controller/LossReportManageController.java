package com.xzcpc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.common.response.R;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.mp.entity.ContainerConfig;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.entity.LossReportItem;
import com.xzcpc.mp.entity.LossStandard;
import com.xzcpc.mp.mapper.ContainerConfigMapper;
import com.xzcpc.mp.mapper.LossReportItemMapper;
import com.xzcpc.mp.mapper.LossStandardMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.mp.service.LossReportService;
import com.xzcpc.task.service.StoreService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/loss-report")
@RequiredArgsConstructor
public class LossReportManageController {

    private final LossReportService lossReportService;
    private final ContainerConfigMapper containerConfigMapper;
    private final LossStandardMapper lossStandardMapper;
    private final LossReportItemMapper lossReportItemMapper;
    private final StoreService storeService;
    private final StoreAccessService storeAccessService;

    /** 全门店报损列表 */
    @GetMapping("/list")
    public R<Page<LossReport>> list(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String supervisorName,
            @RequestParam(required = false) String lossType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(lossReportService.pageAll(storeId, supervisorName, lossType, status, startDate, endDate, pageNum, pageSize));
    }

    /** 导出 Excel */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String lossType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws Exception {

        List<LossReport> list = lossReportService.pageAll(storeId, null, lossType, status, startDate, endDate, 1, 99999).getRecords();

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("报损记录");
        Row header = sheet.createRow(0);
        String[] heads = {"业务编码", "门店", "类型", "物品", "数量/重量", "单位", "原因", "状态", "登记人", "时间", "计算公式", "备注"};
        CellStyle headStyle = wb.createCellStyle();
        Font headFont = wb.createFont(); headFont.setBold(true); headStyle.setFont(headFont);
        for (int i = 0; i < heads.length; i++) { Cell c = header.createCell(i); c.setCellValue(heads[i]); c.setCellStyle(headStyle); }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = 0; i < list.size(); i++) {
            LossReport r = list.get(i);
            Row row = sheet.createRow(i + 1);
            int col = 0;
            row.createCell(col++).setCellValue(r.getBizCode() != null ? r.getBizCode() : "");
            row.createCell(col++).setCellValue(r.getStoreName() != null ? r.getStoreName() : "");
            row.createCell(col++).setCellValue("daily".equals(r.getLossType()) ? "日常报损" : "到货验收");
            row.createCell(col++).setCellValue(getItemColumn(r));
            row.createCell(col++).setCellValue(getQuantityColumn(r));
            row.createCell(col++).setCellValue(r.getItemCount() != null && r.getItemCount() > 0 ? "" : (r.getInputUnit() != null ? r.getInputUnit() : ""));
            row.createCell(col++).setCellValue(r.getReason() != null ? r.getReason() : "");
            row.createCell(col++).setCellValue(statusLabel(r.getStatus(), r.getLossType()));
            row.createCell(col++).setCellValue(r.getHandlerName() != null ? r.getHandlerName() : "");
            row.createCell(col++).setCellValue(r.getCreatedAt() != null ? r.getCreatedAt().format(dtf) : "");
            String formula = getFormulaColumn(r);
            row.createCell(col++).setCellValue(formula);
            row.createCell(col++).setCellValue(r.getRemark() != null ? r.getRemark() : "");
        }

        String filename = "报损记录_" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        try (OutputStream os = response.getOutputStream()) { wb.write(os); }
        wb.close();
    }

    private String getItemColumn(LossReport r) {
        if (r.getItemNames() != null) return r.getItemNames();
        return r.getMaterialName() != null ? r.getMaterialName() : "";
    }

    private String getQuantityColumn(LossReport r) {
        if (r.getItemCount() != null && r.getItemCount() > 0) return "共 " + r.getItemCount() + " 种";
        if (r.getNetWeight() != null) return "净重" + r.getNetWeight() + "g";
        return r.getInputQty() != null ? r.getInputQty().toString() : "";
    }

    private String getFormulaColumn(LossReport r) {
        if (r.getItems() != null && !r.getItems().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (LossReportItem item : r.getItems()) {
                if (sb.length() > 0) sb.append("\n");
                if (item.getContainerName() != null) {
                    sb.append(item.getMaterialName()).append(": 净重").append(item.getNetWeight())
                            .append("g = 含容器重量").append(item.getGrossWeight())
                            .append("g - ").append(item.getContainerName()).append(" ")
                            .append(item.getContainerWeight()).append("g");
                } else if (item.getNetWeight() != null) {
                    sb.append(item.getMaterialName()).append(": 净重").append(item.getNetWeight()).append("g");
                } else {
                    sb.append(item.getMaterialName()).append(": ").append(item.getInputQty())
                            .append(item.getInputUnit() != null ? " " + item.getInputUnit() : "");
                }
            }
            return sb.toString();
        }
        if (r.getContainerName() != null) {
            return "净重" + r.getNetWeight() + "g = 含容器重量" + r.getGrossWeight() + "g - " + r.getContainerName() + " " + r.getContainerWeight() + "g";
        }
        return "";
    }

    private String statusLabel(String s, String lossType) {
        if ("pending_approval".equals(s)) return "待店长审批";
        if ("pending".equals(s)) return "待厂家确认";
        if ("registered".equals(s)) return "已登记";
        if ("confirmed_resend".equals(s)) return "已确认补发";
        if ("rejected".equals(s)) return "daily".equals(lossType) ? "已拒绝" : "厂家拒绝";
        if ("completed".equals(s)) return "已录入";
        if ("received".equals(s)) return "已收货";
        if ("not_received".equals(s)) return "未收到货";
        if ("closed".equals(s)) return "已关闭";
        return s;
    }

    /** 容器列表 */
    @GetMapping("/containers")
    public R<Object> containers(@RequestParam(required = false) String storeId) {
        return R.ok(lossReportService.listContainers(storeId));
    }

    /** 新增容器 */
    @PostMapping("/containers")
    public R<Void> addContainer(@RequestBody ContainerConfig config) {
        containerConfigMapper.insert(config);
        return R.ok();
    }

    /** 编辑容器 */
    @PutMapping("/containers/{id}")
    public R<Void> updateContainer(@PathVariable Long id, @RequestBody ContainerConfig config) {
        config.setId(id);
        containerConfigMapper.updateById(config);
        return R.ok();
    }

    /** H5 报损详情（无门店隔离，飞书链接用） */
    @GetMapping("/{id}")
    public R<LossReport> h5Detail(@PathVariable Long id) {
        LossReport r = lossReportService.getById(id);
        if (r == null) throw new com.xzcpc.common.exception.BusinessException(404, "报损记录不存在");
        return R.ok(r);
    }

    /** H5 确认补发 */
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        LossReport r = lossReportService.getById(id);
        if (r == null) throw new com.xzcpc.common.exception.BusinessException(404, "报损记录不存在");
        r.setStatus("confirmed_resend");
        r.setConfirmedAt(java.time.LocalDateTime.now());
        lossReportService.updateById(r);
        return R.ok();
    }

    /** 报损统计看板 */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String supervisorName,
            @RequestParam(required = false) String lossType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        // 全量报损记录（不按门店过滤：排行需对比全部门店）
        List<LossReport> all = lossReportService.pageAll(null, supervisorName, lossType, null, startDate, endDate, 1, 99999).getRecords();
        // 选中门店时：汇总/类型分布/月度趋势只看该店，排行仍展示全部门店（含0报损门店）
        List<LossReport> scope = all;
        if (StringUtils.hasText(storeId)) {
            scope = all.stream().filter(r -> storeId.equals(r.getStoreId())).collect(Collectors.toList());
        }

        // 汇总指标
        long totalCount = scope.size();
        Set<String> storeIds = scope.stream().map(LossReport::getStoreId).filter(Objects::nonNull).collect(Collectors.toSet());
        long dailyCount = scope.stream().filter(r -> "daily".equals(r.getLossType())).count();
        long arrivalCount = scope.stream().filter(r -> "arrival".equals(r.getLossType())).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", totalCount);
        summary.put("storeCount", storeIds.size());
        summary.put("dailyCount", dailyCount);
        summary.put("arrivalCount", arrivalCount);

        // 按门店排行：全部门店参与（含0报损门店），次数 + 净重（克）
        Map<String, Long> countByStore = new LinkedHashMap<>();
        Map<String, BigDecimal> gramsByStore = new LinkedHashMap<>();
        Map<String, String> nameByStore = new LinkedHashMap<>();
        // 1) 门店全集（督导/管理员可见范围过滤），0 补全
        for (StoreInfo s : scopedStores(supervisorName)) {
            countByStore.put(s.getId(), 0L);
            gramsByStore.put(s.getId(), BigDecimal.ZERO);
            nameByStore.put(s.getId(), s.getMendianmingcheng() != null ? s.getMendianmingcheng() : s.getId());
        }
        // 2) 报损记录累计：次数 + 净重（报单级 netWeight；多物料报损净重在明细表，按报单汇总）
        Map<Long, BigDecimal> itemGramsByReport = itemNetWeightByReport(all);
        for (LossReport r : all) {
            String key = r.getStoreId() != null && !r.getStoreId().isBlank() ? r.getStoreId() : r.getStoreName();
            if (key == null) continue;
            countByStore.merge(key, 1L, Long::sum);
            BigDecimal grams = r.getNetWeight();
            if (grams == null) grams = itemGramsByReport.getOrDefault(r.getId(), BigDecimal.ZERO);
            gramsByStore.merge(key, grams, BigDecimal::add);
            nameByStore.putIfAbsent(key, r.getStoreName() != null ? r.getStoreName() : key);
        }

        long maxCount = countByStore.values().stream().max(Long::compareTo).orElse(1L);
        List<Map<String, Object>> storeRanking = countByStore.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Long.compare(b.getValue(), a.getValue());
                    if (c != 0) return c;
                    return gramsByStore.getOrDefault(b.getKey(), BigDecimal.ZERO)
                            .compareTo(gramsByStore.getOrDefault(a.getKey(), BigDecimal.ZERO));
                })
                .map(e -> {
                    String key = e.getKey();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", key);
                    m.put("name", nameByStore.getOrDefault(key, key));
                    m.put("count", e.getValue());
                    m.put("weight", gramsByStore.getOrDefault(key, BigDecimal.ZERO));
                    m.put("percent", (int) (e.getValue() * 100 / maxCount));
                    return m;
                }).collect(Collectors.toList());

        // 按类型分布
        long total = totalCount > 0 ? totalCount : 1;
        List<Map<String, Object>> typeDistribution = new ArrayList<>();
        Map<String, Object> daily = new LinkedHashMap<>();
        daily.put("name", "日常报损"); daily.put("value", dailyCount); daily.put("percent", (int)(dailyCount * 100 / total));
        typeDistribution.add(daily);
        Map<String, Object> arrival = new LinkedHashMap<>();
        arrival.put("name", "到货验收"); arrival.put("value", arrivalCount); arrival.put("percent", (int)(arrivalCount * 100 / total));
        typeDistribution.add(arrival);

        // 月度趋势
        Map<String, Long> monthMap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 5; i >= 0; i--) monthMap.put(today.minusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM")), 0L);
        for (LossReport r : scope) {
            if (r.getCreatedAt() != null) {
                String m = r.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));
                monthMap.merge(m, 1L, Long::sum);
            }
        }
        long maxMonth = monthMap.values().stream().max(Long::compareTo).orElse(1L);
        List<Map<String, Object>> monthlyTrend = monthMap.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("month", e.getKey().substring(5) + "月");
                    m.put("value", e.getValue());
                    m.put("percent", maxMonth > 0 ? (int)(e.getValue() * 100 / maxMonth) : 0);
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("storeRanking", storeRanking);
        result.put("typeDistribution", typeDistribution);
        result.put("monthlyTrend", monthlyTrend);
        return R.ok(result);
    }

    /** 门店范围：管理员可见门店 ∩ 指定督导门店（传督导名时），用于排行0门店补全 */
    private List<StoreInfo> scopedStores(String supervisorName) {
        List<StoreInfo> stores = storeService.getAllStores();
        AdminUser admin = AdminContextHolder.get();
        final Set<String> adminScope;
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            adminScope = new HashSet<>(storeAccessService.getAccessibleStoreIds(admin.getOpenId()));
        } else {
            adminScope = null;
        }
        final Set<String> supervisorScope = StringUtils.hasText(supervisorName)
                ? new HashSet<>(storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName))
                : null;
        return stores.stream()
                .filter(s -> adminScope == null || adminScope.contains(s.getId()))
                .filter(s -> supervisorScope == null || supervisorScope.contains(s.getId()))
                .collect(Collectors.toList());
    }

    /** 按报单汇总明细净重（克）：日常/多物料报损的净重存在明细表 */
    private Map<Long, BigDecimal> itemNetWeightByReport(List<LossReport> reports) {
        Map<Long, BigDecimal> map = new HashMap<>();
        List<Long> ids = reports.stream().map(LossReport::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) return map;
        for (LossReportItem it : lossReportItemMapper.selectList(
                new LambdaQueryWrapper<LossReportItem>().in(LossReportItem::getReportId, ids))) {
            if (it.getNetWeight() != null) {
                map.merge(it.getReportId(), it.getNetWeight(), BigDecimal::add);
            }
        }
        return map;
    }

    /** H5 拒绝 */
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LossReport r = lossReportService.getById(id);
        if (r == null) throw new com.xzcpc.common.exception.BusinessException(404, "报损记录不存在");
        r.setStatus("rejected");
        r.setRejectReason(body.getOrDefault("reason", ""));
        lossReportService.updateById(r);
        return R.ok();
    }

    // ========== 验收标准 CRUD ==========

    @GetMapping("/standards")
    public R<Page<LossStandard>> listStandards(
            @RequestParam(required = false) String standardType,
            @RequestParam(required = false) String materialId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        var qw = new LambdaQueryWrapper<LossStandard>().orderByDesc(LossStandard::getCreatedAt);
        if (standardType != null && !standardType.isEmpty()) qw.eq(LossStandard::getStandardType, standardType);
        if (materialId != null && !materialId.isEmpty()) qw.eq(LossStandard::getMaterialId, materialId);
        return R.ok(lossStandardMapper.selectPage(new Page<>(pageNum, pageSize), qw));
    }

    @PostMapping("/standards")
    public R<Void> createStandard(@RequestBody LossStandard standard) {
        lossStandardMapper.insert(standard);
        return R.ok();
    }

    @PutMapping("/standards/{id}")
    public R<Void> updateStandard(@PathVariable Long id, @RequestBody LossStandard standard) {
        standard.setId(id);
        lossStandardMapper.updateById(standard);
        return R.ok();
    }

    @DeleteMapping("/standards/{id}")
    public R<Void> deleteStandard(@PathVariable Long id) {
        lossStandardMapper.deleteById(id);
        return R.ok();
    }
}
