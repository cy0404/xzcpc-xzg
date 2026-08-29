package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.DailyLossCreateReq;
import com.xzcpc.mp.entity.ContainerConfig;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.entity.LossReportItem;
import com.xzcpc.mp.entity.LossReportLog;
import com.xzcpc.mp.mapper.ContainerConfigMapper;
import com.xzcpc.mp.mapper.LossReportItemMapper;
import com.xzcpc.mp.mapper.LossReportLogMapper;
import com.xzcpc.mp.mapper.LossReportMapper;
import com.xzcpc.mp.service.LossReportService;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.template.entity.MaterialConversionRule;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialConversionRuleMapper;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LossReportServiceImpl implements LossReportService {

    private final LossReportMapper lossReportMapper;
    private final LossReportItemMapper lossReportItemMapper;
    private final ContainerConfigMapper containerConfigMapper;
    private final LossReportLogMapper logMapper;
    private final MpStaffService staffService;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialConversionRuleMapper conversionRuleMapper;
    private final StoreAccessService storeAccessService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public LossReportServiceImpl(LossReportMapper lossReportMapper, LossReportItemMapper lossReportItemMapper,
            ContainerConfigMapper containerConfigMapper,
            LossReportLogMapper logMapper, MpStaffService staffService,
            MaterialInventoryRuleMapper ruleMapper, MaterialConversionRuleMapper conversionRuleMapper,
            StoreAccessService storeAccessService,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.lossReportMapper = lossReportMapper;
        this.lossReportItemMapper = lossReportItemMapper;
        this.containerConfigMapper = containerConfigMapper;
        this.logMapper = logMapper;
        this.staffService = staffService;
        this.ruleMapper = ruleMapper;
        this.conversionRuleMapper = conversionRuleMapper;
        this.storeAccessService = storeAccessService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Value("${app.public-url:}")
    private String publicUrl;
    @Value("${app.server-url:http://localhost:4026}")
    private String serverUrl;

    @Override
    @Transactional
    public LossReport create(LossReport report, String openid) {
        var user = UserContextHolder.get();

        report.setBizCode(com.xzcpc.common.util.BizCodeUtil.of("LOSS"));
        report.setStoreId(user.getStoreId());
        report.setStoreName(user.getStoreName());
        report.setSubmittedBy(openid);
        // 登记人存员工姓名
        if (user.getEmployeeName() != null) {
            report.setHandlerName(user.getEmployeeName());
        }
        // 店员提交 → 需店长审批；店长/老板直接进入下一步
        if (user.isStaffOnly()) {
            report.setStatus("pending_approval");
        } else if ("daily".equals(report.getLossType())) {
            report.setStatus("completed");
        } else {
            report.setStatus("pending");
        }
        report.setOccurredDate(LocalDate.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        // 到货验收：数量必填
        if ("arrival".equals(report.getLossType()) && !"semi_finished".equals(report.getLossObject())
                && (report.getInputQty() == null || report.getInputQty().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("请填写报损数量");
        }
        // 到货验收：企迈单号必填（牛油果泥除外）
        if ("arrival".equals(report.getLossType()) && !StringUtils.hasText(report.getQimaiOrderNo())
                && !(report.getMaterialName() != null && report.getMaterialName().contains("牛油果泥"))) {
            throw new BusinessException("请填写企迈单号");
        }

        // 半成品去皮计算
        if ("semi_finished".equals(report.getLossObject())) {
            if (report.getGrossWeight() == null || report.getGrossWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("请填写含容器重量");
            }
            BigDecimal tare = BigDecimal.ZERO;
            if (report.getContainerId() != null) {
                ContainerConfig cc = containerConfigMapper.selectById(report.getContainerId());
                if (cc != null && cc.getStatus() != null && cc.getStatus() == 1) {
                    tare = cc.getTareWeight();
                    report.setContainerName(cc.getContainerName());
                    report.setContainerWeight(tare);
                }
            }
            BigDecimal net = report.getGrossWeight().subtract(tare);
            if (net.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("净重必须大于0：总重" + report.getGrossWeight() + "g - 皮重" + tare + "g");
            }
            report.setNetWeight(net);
        }

        // 到货验收：多单位换算到最小单位
        if ("arrival".equals(report.getLossType()) && report.getInputUnit() != null && report.getInputQty() != null) {
            MaterialInventoryRule rule = ruleMapper.selectOne(
                    new LambdaQueryWrapper<MaterialInventoryRule>()
                            .eq(MaterialInventoryRule::getMaterialId, report.getMaterialId()));
            if (rule != null && rule.getBaseUnit() != null) {
                String baseUnit = rule.getBaseUnit();
                report.setBaseUnit(baseUnit);
                if (baseUnit.equals(report.getInputUnit())) {
                    report.setBaseQty(report.getInputQty());
                } else {
                    List<MaterialConversionRule> convs = conversionRuleMapper.selectList(
                            new LambdaQueryWrapper<MaterialConversionRule>()
                                    .eq(MaterialConversionRule::getRuleId, rule.getRuleId()));
                    BigDecimal factor = computeConversionFactor(report.getInputUnit(), baseUnit, convs);
                    if (factor != null) {
                        report.setBaseQty(report.getInputQty().multiply(factor));
                    } else {
                        throw new BusinessException("找不到单位换算关系：" + report.getInputUnit() + " → " + baseUnit);
                    }
                }
            }
        }

        lossReportMapper.insert(report);
        addLog(report.getId(), "submit", report.getHandlerName(), null);
        // 加急：仅在无需店长审批（已到pending状态）时才发卡片；需要店长审批的在approve方法中发送
        if (report.getUrgent() != null && report.getUrgent() == 1 && "arrival".equals(report.getLossType())
                && !"pending_approval".equals(report.getStatus())) {
            sendUrgentCardAfterCommit(report);
        }
        return report;
    }

    @Override
    @Transactional
    public LossReport createDaily(DailyLossCreateReq req) {
        var user = UserContextHolder.get();

        // 1. 构建主记录
        LossReport report = new LossReport();
        report.setBizCode(com.xzcpc.common.util.BizCodeUtil.of("LOSS"));
        report.setStoreId(user.getStoreId());
        report.setStoreName(user.getStoreName());
        report.setLossType("daily");
        report.setSubmittedBy(user.getOpenid());
        if (user.getEmployeeName() != null) {
            report.setHandlerName(user.getEmployeeName());
        }
        // 店员提交 → 需店长审批；店长/老板直接完成
        if (user.isStaffOnly()) {
            report.setStatus("pending_approval");
        } else {
            report.setStatus("completed");
        }
        report.setOccurredDate(LocalDate.now());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        report.setReason(req.getReason());
        report.setRemark(req.getRemark());
        report.setVoucherUrl(req.getVoucherUrl());
        report.setUrgent(0);
        if (req.getItems().size() > 20) {
            throw new BusinessException("最多添加20种物料");
        }
        report.setItemCount(req.getItems().size());

        // 2. 处理每个物料项
        List<LossReportItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalBaseQty = BigDecimal.ZERO;
        boolean allSemi = true, allFinished = true;

        int sortNo = 0;
        for (DailyLossCreateReq.ItemReq itemReq : req.getItems()) {
            LossReportItem item = new LossReportItem();
            item.setMaterialId(itemReq.getMaterialId());
            item.setMaterialName(itemReq.getMaterialName());
            item.setSpec(itemReq.getSpec() != null ? itemReq.getSpec() : "");
            item.setSortNo(sortNo++);

            String lossObject = itemReq.getLossObject() != null ? itemReq.getLossObject() : "semi_finished";
            item.setLossObject(lossObject);

            if ("semi_finished".equals(lossObject)) {
                allFinished = false;
                // 半成品去皮
                if (itemReq.getGrossWeight() == null || itemReq.getGrossWeight().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("物料[" + itemReq.getMaterialName() + "]请填写含容器重量");
                }
                BigDecimal tare = BigDecimal.ZERO;
                if (itemReq.getContainerId() != null) {
                    ContainerConfig cc = containerConfigMapper.selectById(itemReq.getContainerId());
                    if (cc != null && cc.getStatus() != null && cc.getStatus() == 1) {
                        tare = cc.getTareWeight();
                        item.setContainerId(cc.getId());
                        item.setContainerName(cc.getContainerName());
                        item.setContainerWeight(tare);
                    }
                }
                item.setGrossWeight(itemReq.getGrossWeight());
                BigDecimal net = itemReq.getGrossWeight().subtract(tare);
                if (net.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("物料[" + itemReq.getMaterialName() + "]净重必须大于0：总重"
                            + itemReq.getGrossWeight() + "g - 皮重" + tare + "g");
                }
                item.setNetWeight(net);
                item.setInputQty(net);
                item.setInputUnit("g");
            } else {
                allSemi = false;
                // 成品：数量必填
                if (itemReq.getInputQty() == null || itemReq.getInputQty().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("物料[" + itemReq.getMaterialName() + "]请填写报损数量");
                }
                item.setInputQty(itemReq.getInputQty());
                item.setInputUnit(itemReq.getInputUnit());
            }

            // 物料单价
            BigDecimal unitPrice = itemReq.getUnitPrice();
            if (unitPrice == null && itemReq.getMaterialId() != null) {
                MaterialInventoryRule rule = ruleMapper.selectOne(
                        new LambdaQueryWrapper<MaterialInventoryRule>()
                                .eq(MaterialInventoryRule::getMaterialId, itemReq.getMaterialId()));
                if (rule != null && rule.getUnitPrice() != null) {
                    unitPrice = rule.getUnitPrice();
                }
            }
            if (unitPrice != null) {
                item.setUnitPrice(unitPrice);
                item.setTotalAmount(item.getInputQty().multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
                totalAmount = totalAmount.add(item.getTotalAmount());
            }

            // baseQty（日常报损直接等于 inputQty）
            item.setBaseQty(item.getInputQty());
            item.setBaseUnit(item.getInputUnit());
            if (item.getInputQty() != null) {
                totalBaseQty = totalBaseQty.add(item.getInputQty());
            }

            items.add(item);
        }

        // 推导 lossObject
        if (allSemi && !allFinished) {
            report.setLossObject("semi_finished");
        } else if (allFinished && !allSemi) {
            report.setLossObject("finished");
        } else {
            report.setLossObject("mixed");
        }

        // 汇总金额和 baseQty
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            report.setTotalAmount(totalAmount);
        }
        if (totalBaseQty.compareTo(BigDecimal.ZERO) > 0) {
            report.setBaseQty(totalBaseQty);
        }

        // 3. 插主表 → 逐条插子表
        lossReportMapper.insert(report);
        for (LossReportItem item : items) {
            item.setReportId(report.getId());
            lossReportItemMapper.insert(item);
        }
        report.setItems(items);
        updateItemNames(report);

        addLog(report.getId(), "submit", report.getHandlerName(), null);
        return report;
    }

    @Override
    @Transactional
    public LossReport updateDaily(Long id, DailyLossCreateReq req) {
        var user = UserContextHolder.get();
        LossReport report = lossReportMapper.selectById(id);
        if (report == null || !report.getStoreId().equals(user.getStoreId())) {
            throw new BusinessException("报损记录不存在");
        }
        if (!"daily".equals(report.getLossType())) {
            throw new BusinessException("仅日常报损支持修改");
        }

        // 软删除旧明细
        List<LossReportItem> oldItems = lossReportItemMapper.selectList(
                new LambdaQueryWrapper<LossReportItem>().eq(LossReportItem::getReportId, id));
        for (LossReportItem oi : oldItems) {
            lossReportItemMapper.deleteById(oi.getId());
        }

        // 重建明细
        report.setReason(req.getReason());
        report.setRemark(req.getRemark());
        report.setVoucherUrl(req.getVoucherUrl());
        if (req.getItems().size() > 20) {
            throw new BusinessException("最多添加20种物料");
        }
        report.setItemCount(req.getItems().size());
        report.setUpdatedAt(LocalDateTime.now());

        List<LossReportItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalBaseQty = BigDecimal.ZERO;
        boolean allSemi = true, allFinished = true;
        int sortNo = 0;

        for (DailyLossCreateReq.ItemReq itemReq : req.getItems()) {
            LossReportItem item = new LossReportItem();
            item.setReportId(id);
            item.setMaterialId(itemReq.getMaterialId());
            item.setMaterialName(itemReq.getMaterialName());
            item.setSpec(itemReq.getSpec() != null ? itemReq.getSpec() : "");
            item.setSortNo(sortNo++);

            String lossObject = itemReq.getLossObject() != null ? itemReq.getLossObject() : "semi_finished";
            item.setLossObject(lossObject);

            if ("semi_finished".equals(lossObject)) {
                allFinished = false;
                if (itemReq.getGrossWeight() == null || itemReq.getGrossWeight().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("物料[" + itemReq.getMaterialName() + "]请填写含容器重量");
                }
                BigDecimal tare = BigDecimal.ZERO;
                if (itemReq.getContainerId() != null) {
                    ContainerConfig cc = containerConfigMapper.selectById(itemReq.getContainerId());
                    if (cc != null && cc.getStatus() != null && cc.getStatus() == 1) {
                        tare = cc.getTareWeight();
                        item.setContainerId(cc.getId());
                        item.setContainerName(cc.getContainerName());
                        item.setContainerWeight(tare);
                    }
                }
                item.setGrossWeight(itemReq.getGrossWeight());
                BigDecimal net = itemReq.getGrossWeight().subtract(tare);
                if (net.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("物料[" + itemReq.getMaterialName() + "]净重必须大于0");
                }
                item.setNetWeight(net);
                item.setInputQty(net);
                item.setInputUnit("g");
            } else {
                allSemi = false;
                if (itemReq.getInputQty() == null || itemReq.getInputQty().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("物料[" + itemReq.getMaterialName() + "]请填写报损数量");
                }
                item.setInputQty(itemReq.getInputQty());
                item.setInputUnit(itemReq.getInputUnit());
            }

            BigDecimal unitPrice = itemReq.getUnitPrice();
            if (unitPrice == null && itemReq.getMaterialId() != null) {
                MaterialInventoryRule rule = ruleMapper.selectOne(
                        new LambdaQueryWrapper<MaterialInventoryRule>()
                                .eq(MaterialInventoryRule::getMaterialId, itemReq.getMaterialId()));
                if (rule != null && rule.getUnitPrice() != null) {
                    unitPrice = rule.getUnitPrice();
                }
            }
            if (unitPrice != null) {
                item.setUnitPrice(unitPrice);
                item.setTotalAmount(item.getInputQty().multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
                totalAmount = totalAmount.add(item.getTotalAmount());
            }
            item.setBaseQty(item.getInputQty());
            item.setBaseUnit(item.getInputUnit());
            if (item.getInputQty() != null) {
                totalBaseQty = totalBaseQty.add(item.getInputQty());
            }
            items.add(item);
        }

        if (allSemi && !allFinished) {
            report.setLossObject("semi_finished");
        } else if (allFinished && !allSemi) {
            report.setLossObject("finished");
        } else {
            report.setLossObject("mixed");
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            report.setTotalAmount(totalAmount);
        } else {
            report.setTotalAmount(null);
        }
        if (totalBaseQty.compareTo(BigDecimal.ZERO) > 0) {
            report.setBaseQty(totalBaseQty);
        } else {
            report.setBaseQty(null);
        }

        lossReportMapper.updateById(report);
        for (LossReportItem item : items) {
            lossReportItemMapper.insert(item);
        }
        report.setItems(items);
        updateItemNames(report);
        addLog(id, "update", report.getHandlerName(), null);
        return report;
    }

    @Override
    @Transactional
    public void deleteDaily(Long id, String storeId, String operator) {
        LossReport report = lossReportMapper.selectById(id);
        if (report == null || !report.getStoreId().equals(storeId)) {
            throw new BusinessException("报损记录不存在");
        }
        if (!"daily".equals(report.getLossType())) {
            throw new BusinessException("仅日常报损支持删除");
        }
        // 软删除明细
        List<LossReportItem> items = lossReportItemMapper.selectList(
                new LambdaQueryWrapper<LossReportItem>().eq(LossReportItem::getReportId, id));
        for (LossReportItem item : items) {
            lossReportItemMapper.deleteById(item.getId());
        }
        addLog(id, "delete", operator, null);
        lossReportMapper.deleteById(id);
    }

    /** 批量加载多物料报损的明细 */
    private Map<Long, List<LossReportItem>> getItemsBatch(List<Long> reportIds) {
        if (reportIds.isEmpty()) return Map.of();
        List<LossReportItem> allItems = lossReportItemMapper.selectList(
                new LambdaQueryWrapper<LossReportItem>()
                        .in(LossReportItem::getReportId, reportIds)
                        .orderByAsc(LossReportItem::getSortNo));
        return allItems.stream().collect(Collectors.groupingBy(LossReportItem::getReportId));
    }

    /** 为多物料报损设置 itemNames */
    private void updateItemNames(LossReport r) {
        List<LossReportItem> items = r.getItems();
        if (items == null || items.isEmpty()) return;
        String firstName = items.get(0).getMaterialName();
        if (items.size() == 1) {
            r.setItemNames(firstName);
        } else {
            r.setItemNames(firstName + " 等 " + items.size() + " 种物料");
        }
    }

    /** 批量填充多物料报损的 items 和 itemNames */
    private void enrichMultiItemReports(List<LossReport> records) {
        List<Long> multiIds = records.stream()
                .filter(r -> "daily".equals(r.getLossType()) && r.getItemCount() != null && r.getItemCount() > 0)
                .map(LossReport::getId)
                .collect(Collectors.toList());
        if (multiIds.isEmpty()) return;
        Map<Long, List<LossReportItem>> itemsMap = getItemsBatch(multiIds);
        for (LossReport r : records) {
            List<LossReportItem> items = itemsMap.get(r.getId());
            if (items != null && !items.isEmpty()) {
                r.setItems(items);
                updateItemNames(r);
            }
        }
    }

    @Override
    public Page<LossReport> pageByStore(String storeId, String lossType, int pageNum, int pageSize) {
        var qw = new LambdaQueryWrapper<LossReport>()
                .eq(LossReport::getStoreId, storeId)
                .orderByDesc(LossReport::getCreatedAt);
        if (StringUtils.hasText(lossType)) qw.eq(LossReport::getLossType, lossType);
        Page<LossReport> page = lossReportMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        resolveFruitVeg(page.getRecords());
        enrichMultiItemReports(page.getRecords());
        fillLatestLog(page.getRecords());
        return page;
    }

    @Override
    public LossReport detail(Long id, String storeId) {
        LossReport r = lossReportMapper.selectById(id);
        if (r == null || !r.getStoreId().equals(storeId)) {
            throw new BusinessException("报损记录不存在");
        }
        resolveFruitVeg(List.of(r));
        enrichMultiItemReports(List.of(r));
        return r;
    }

    @Override
    public Page<LossReport> pageAll(String storeId, String supervisorName, String lossType, String status,
                                     String startDate, String endDate, int pageNum, int pageSize) {
        var qw = new LambdaQueryWrapper<LossReport>()
                .orderByDesc(LossReport::getCreatedAt);

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            qw.in(LossReport::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            qw.in(LossReport::getStoreId, supervisorStoreIds);
        }

        if (StringUtils.hasText(storeId)) qw.eq(LossReport::getStoreId, storeId);
        if (StringUtils.hasText(lossType)) qw.eq(LossReport::getLossType, lossType);
        if (StringUtils.hasText(status)) qw.eq(LossReport::getStatus, status);
        if (StringUtils.hasText(startDate)) qw.ge(LossReport::getOccurredDate, LocalDate.parse(startDate));
        if (StringUtils.hasText(endDate)) qw.le(LossReport::getOccurredDate, LocalDate.parse(endDate));
        Page<LossReport> result = lossReportMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        List<LossReport> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<String> storeIds = records.stream().map(LossReport::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
            if (!storeIds.isEmpty()) {
                Map<String, String> supervisorMap = storeAccessService.getSupervisorNamesByStoreIds(storeIds);
                records.forEach(r -> r.setSupervisorName(supervisorMap.getOrDefault(r.getStoreId(), "")));
            }
            enrichMultiItemReports(records);
            fillLatestLog(records);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listContainers(String storeId) {
        return containerConfigMapper.selectList(
                new LambdaQueryWrapper<ContainerConfig>()
                        .eq(ContainerConfig::getStatus, 1)
                        .and(w -> w.isNull(ContainerConfig::getStoreId).or().eq(ContainerConfig::getStoreId, storeId))
                        .orderByAsc(ContainerConfig::getId))
                .stream().map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getContainerName());
                    m.put("alias", c.getAlias() != null ? c.getAlias() : "");
                    m.put("tareWeight", c.getTareWeight());
                    // 容器图片拼接完整 URL
                    String img = c.getImage() != null ? c.getImage() : "";
                    if (!img.isEmpty() && !img.startsWith("http") && !publicUrl.isBlank()) {
                        img = publicUrl + (img.startsWith("/") ? "" : "/") + img;
                    }
                    m.put("image", img);
                    return m;
                }).collect(Collectors.toList());
    }

    @Override
    public LossReport getById(Long id) {
        return lossReportMapper.selectById(id);
    }

    @Override
    public void updateById(LossReport report) {
        lossReportMapper.updateById(report);
    }

    private void addLog(Long reportId, String action, String operator, String remark) {
        LossReportLog log = new LossReportLog();
        log.setReportId(reportId);
        log.setAction(action);
        log.setOperator(operator);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    @Override
    public Page<LossReport> pageApprovalByStore(String storeId, int pageNum, int pageSize) {
        return lossReportMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<LossReport>()
                        .eq(LossReport::getStoreId, storeId)
                        .eq(LossReport::getStatus, "pending_approval")
                        .orderByDesc(LossReport::getCreatedAt));
    }

    @Override
    @Transactional
    public LossReport approve(Long id, String storeId) {
        LossReport r = lossReportMapper.selectById(id);
        if (r == null || !Objects.equals(r.getStoreId(), storeId)) throw new BusinessException("报损记录不存在");
        if (!"pending_approval".equals(r.getStatus())) return r;  // 已处理过，幂等
        if ("daily".equals(r.getLossType())) {
            r.setStatus("completed");
        } else {
            r.setStatus("pending");
        }
        r.setConfirmedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        if (lossReportMapper.updateById(r) != 1) return r;  // 乐观锁冲突：他人已处理
        addLog(id, "approve", r.getHandlerName(), null);
        // 加急到货报损：店长审批通过后发加急卡片
        sendUrgentCardAfterCommit(r);
        return r;
    }

    @Override
    public void rejectApproval(Long id, String storeId) {
        LossReport r = lossReportMapper.selectById(id);
        if (r == null || !Objects.equals(r.getStoreId(), storeId)) throw new BusinessException("报损记录不存在");
        if (!"pending_approval".equals(r.getStatus())) return;  // 已处理过，幂等
        r.setStatus("rejected");
        r.setUpdatedAt(LocalDateTime.now());
        if (lossReportMapper.updateById(r) != 1) return;  // 乐观锁冲突：他人已处理
        addLog(id, "reject_approval", r.getHandlerName(), null);
    }

    @Override
    @Transactional
    public Map<String, Object> batchApprove(List<Long> ids, String action, String storeId) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要处理的报损记录");
        if (!"approve".equals(action) && !"reject".equals(action)) throw new BusinessException("无效的处理动作");
        if (ids.size() > MAX_BATCH_SIZE) throw new BusinessException("一次最多批量处理" + MAX_BATCH_SIZE + "条");
        int ok = 0, skip = 0;
        for (Long id : ids) {
            LossReport r = lossReportMapper.selectById(id);
            // 已删除或非本店记录：跳过并计数，不中断批次
            if (r == null || !Objects.equals(r.getStoreId(), storeId)) { skip++; continue; }
            if (!"pending_approval".equals(r.getStatus())) { skip++; continue; }  // 已处理过，幂等跳过
            if ("approve".equals(action)) {
                if ("daily".equals(r.getLossType())) {
                    r.setStatus("completed");
                } else {
                    r.setStatus("pending");
                }
                r.setConfirmedAt(LocalDateTime.now());
                r.setUpdatedAt(LocalDateTime.now());
            } else {
                r.setStatus("rejected");
                r.setUpdatedAt(LocalDateTime.now());
            }
            // 乐观锁冲突（他人并发处理）：updateById 返回 0，不计成功、不写日志、不发卡片
            if (lossReportMapper.updateById(r) != 1) { skip++; continue; }
            if ("approve".equals(action)) {
                addLog(id, "approve", r.getHandlerName(), null);
                // 加急到货报损：店长审批通过后发加急卡片（照单条 approve）
                sendUrgentCardAfterCommit(r);
            } else {
                addLog(id, "reject_approval", r.getHandlerName(), null);
            }
            ok++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("skipped", skip);
        return result;
    }

    @Override
    public void receive(Long id, String storeId, String operator, String remark) {
        LossReport r = lossReportMapper.selectById(id);
        if (r == null || !r.getStoreId().equals(storeId)) throw new BusinessException("报损记录不存在");
        r.setStatus("received");
        r.setUpdatedAt(LocalDateTime.now());
        lossReportMapper.updateById(r);
        addLog(id, "receive", operator, remark);
    }

    @Override
    public void notReceive(Long id, String storeId, String operator, String remark) {
        LossReport r = lossReportMapper.selectById(id);
        if (r == null || !r.getStoreId().equals(storeId)) throw new BusinessException("报损记录不存在");
        r.setStatus("not_received");
        r.setUpdatedAt(LocalDateTime.now());
        lossReportMapper.updateById(r);
        addLog(id, "not_receive", operator, remark);
    }

    @Override
    public List<Map<String, Object>> getLogs(Long reportId) {
        return logMapper.selectList(new LambdaQueryWrapper<LossReportLog>()
                .eq(LossReportLog::getReportId, reportId)
                .ne(LossReportLog::getAction, "download")
                .orderByAsc(LossReportLog::getCreatedAt))
                .stream().map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("action", l.getAction());
                    m.put("operator", l.getOperator());
                    m.put("remark", l.getRemark());
                    m.put("attachmentUrl", l.getAttachmentUrl() != null ? l.getAttachmentUrl() : "");
                    m.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString().replace("T", " ").substring(0, 16) : "");
                    return m;
                }).toList();
    }

    @Override
    public List<Map<String, Object>> overviewByStores(String openid) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> s : stores) {
            String sid = (String) s.get("storeId");
            String sname = (String) s.get("storeName");
            // 待处理：pending_approval / pending / confirmed_resend
            Long pending = lossReportMapper.selectCount(
                    new LambdaQueryWrapper<LossReport>()
                            .eq(LossReport::getStoreId, sid)
                            .in(LossReport::getStatus, "pending_approval", "confirmed_resend"));
            if (pending != null && pending > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("storeId", sid);
                item.put("storeName", sname);
                item.put("pending", pending);
                result.add(item);
            }
        }
        return result;
    }

    /**
     * BFS 换算链：1 inputUnit = ? baseUnit
     */
    private BigDecimal computeConversionFactor(String inputUnit, String baseUnit, List<MaterialConversionRule> rules) {
        if (rules.isEmpty()) return null;
        Map<String, List<ConversionEdge>> graph = new HashMap<>();
        for (MaterialConversionRule r : rules) {
            if (!"unit".equals(r.getConversionType())) continue;
            BigDecimal fromToTo = r.getToQuantity().divide(r.getFromQuantity(), 10, RoundingMode.HALF_UP);
            BigDecimal toToFrom = r.getFromQuantity().divide(r.getToQuantity(), 10, RoundingMode.HALF_UP);
            graph.computeIfAbsent(r.getFromUnit(), k -> new ArrayList<>())
                    .add(new ConversionEdge(r.getToUnit(), fromToTo));
            graph.computeIfAbsent(r.getToUnit(), k -> new ArrayList<>())
                    .add(new ConversionEdge(r.getFromUnit(), toToFrom));
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

    private record ConversionEdge(String toUnit, BigDecimal ratio) {}

    /** 填充列表：最新操作日志 */
    private void fillLatestLog(List<LossReport> records) {
        if (records.isEmpty()) return;
        List<Long> ids = records.stream().map(LossReport::getId).toList();
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT t.report_id, t.action, t.remark FROM loss_report_log t " +
                "INNER JOIN (SELECT report_id, MAX(created_at) AS max_created FROM loss_report_log WHERE report_id IN (" + placeholders + ") AND action NOT IN ('submit','delete','download') GROUP BY report_id) latest " +
                "ON t.report_id = latest.report_id AND t.created_at = latest.max_created",
                ids.toArray());
        Map<Long, Map<String, Object>> logMap = new HashMap<>();
        for (Map<String, Object> l : logs) {
            logMap.put(((Number) l.get("report_id")).longValue(), l);
        }
        for (LossReport r : records) {
            Map<String, Object> l = logMap.get(r.getId());
            if (l != null) {
                String action = (String) l.get("action");
                if (!"submit".equals(action) && !"delete".equals(action)) {
                    r.setLatestLogAction(actionLabel(action));
                    r.setLatestLogRemark((String) l.getOrDefault("remark", ""));
                }
            }
        }
    }

    private static String actionLabel(String a) {
        return switch (a) {
            case "submit" -> "提交报损";
            case "approve" -> "审批通过";
            case "reject_approval" -> "审批拒绝";
            case "confirm" -> "厂家确认补发";
            case "reject" -> "厂家拒绝";
            case "register" -> "厂家确认登记";
            case "issue_voucher" -> "厂家确认发券";
            case "receive" -> "已收货";
            case "not_receive" -> "未收到货";
            default -> a;
        };
    }

    /** 单次批量审批最大条数 */
    private static final int MAX_BATCH_SIZE = 200;

    /** 加急到货报损：事务提交后发加急卡片（3s 连接 / 5s 读取超时，失败静默） */
    private void sendUrgentCardAfterCommit(LossReport r) {
        if (r.getUrgent() == null || r.getUrgent() != 1 || !"arrival".equals(r.getLossType())) return;
        Long rid = r.getId();
        String url = serverUrl;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(3000);
                    factory.setReadTimeout(5000);
                    new RestTemplate(factory).postForEntity(
                            url + "/api/public/loss-report/send-urgent-card",
                            Map.of("reportId", rid.toString()),
                            String.class);
                } catch (Exception ignored) {}
            }
        });
    }

    /** 批量解析报损记录是否属于水果蔬菜类 */
    private void resolveFruitVeg(List<LossReport> records) {
        if (records.isEmpty()) return;
        Set<String> matIds = records.stream()
                .map(LossReport::getMaterialId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (matIds.isEmpty()) return;
        Map<String, String> catMap = new HashMap<>();
        for (String mid : matIds) {
            try {
                String cat = jdbcTemplate.queryForObject(
                        "SELECT category FROM material WHERE material_id=? LIMIT 1", String.class, mid);
                if (cat != null) catMap.put(mid, cat);
            } catch (Exception ignored) {}
        }
        for (LossReport r : records) {
            String cat = catMap.get(r.getMaterialId());
            if (cat == null || cat.isEmpty()) { r.setIsFruitVeg(false); continue; }
            try {
                String groupKey = jdbcTemplate.queryForObject(
                        "SELECT DISTINCT category FROM loss_notify_card_config WHERE status=1 AND category != '其他类' AND FIND_IN_SET(?, category) > 0 ORDER BY LENGTH(category) ASC LIMIT 1",
                        String.class, cat);
                r.setIsFruitVeg("水果蔬菜".equals(groupKey));
            } catch (Exception e) { r.setIsFruitVeg(false); }
        }
    }
}
