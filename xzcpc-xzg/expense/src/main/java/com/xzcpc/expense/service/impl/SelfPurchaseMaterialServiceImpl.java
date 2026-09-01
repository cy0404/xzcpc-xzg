package com.xzcpc.expense.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.expense.entity.SelfPurchaseMaterial;
import com.xzcpc.expense.entity.SelfPurchaseMaterialItem;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialItemMapper;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialMapper;
import com.xzcpc.expense.service.SelfPurchaseMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfPurchaseMaterialServiceImpl implements SelfPurchaseMaterialService {

    private final SelfPurchaseMaterialMapper mapper;
    private final SelfPurchaseMaterialItemMapper itemMapper;
    private final StoreAccessService storeAccessService;

    @Override
    public Page<SelfPurchaseMaterial> page(String storeIds, String supervisorName, String startDate, String endDate,
                                            String handlerName, int pageNum, int pageSize) {
        LambdaQueryWrapper<SelfPurchaseMaterial> wrapper = new LambdaQueryWrapper<>();

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(SelfPurchaseMaterial::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            wrapper.in(SelfPurchaseMaterial::getStoreId, supervisorStoreIds);
        }

        if (StringUtils.hasText(storeIds)) {
            String[] ids = storeIds.split(",");
            if (ids.length == 1) {
                wrapper.eq(SelfPurchaseMaterial::getStoreId, ids[0].trim());
            } else {
                wrapper.in(SelfPurchaseMaterial::getStoreId, Arrays.asList(ids));
            }
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(SelfPurchaseMaterial::getPurchaseDate, startDate);
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(SelfPurchaseMaterial::getPurchaseDate, endDate);
        }
        if (StringUtils.hasText(handlerName)) {
            wrapper.like(SelfPurchaseMaterial::getHandlerName, handlerName);
        }
        wrapper.orderByDesc(SelfPurchaseMaterial::getPurchaseDate)
               .orderByDesc(SelfPurchaseMaterial::getId);
        List<SelfPurchaseMaterial> headers = mapper.selectList(wrapper);

        // 多物料：主表铺平到明细，每物料一行（保留分页粒度 = 物料行数）
        List<SelfPurchaseMaterial> records = new ArrayList<>();
        if (!headers.isEmpty()) {
            List<String> bizCodes = headers.stream().map(SelfPurchaseMaterial::getBizCode)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            Map<String, List<SelfPurchaseMaterialItem>> itemsByBiz = Collections.emptyMap();
            if (!bizCodes.isEmpty()) {
                itemsByBiz = itemMapper.selectList(new LambdaQueryWrapper<SelfPurchaseMaterialItem>()
                                .in(SelfPurchaseMaterialItem::getBizCode, bizCodes)
                                .orderByAsc(SelfPurchaseMaterialItem::getSortNo))
                        .stream().collect(Collectors.groupingBy(SelfPurchaseMaterialItem::getBizCode));
            }
            for (SelfPurchaseMaterial h : headers) {
                List<SelfPurchaseMaterialItem> items = itemsByBiz.getOrDefault(h.getBizCode(), Collections.emptyList());
                if (items.isEmpty()) {
                    // 迁移前无明细的兜底：直接返回主表行
                    records.add(h);
                    continue;
                }
                for (SelfPurchaseMaterialItem it : items) {
                    SelfPurchaseMaterial r = new SelfPurchaseMaterial();
                    r.setId(it.getId());
                    r.setBizCode(h.getBizCode());
                    r.setStoreId(h.getStoreId());
                    r.setStoreName(h.getStoreName());
                    r.setStoreMiniappNo(h.getStoreMiniappNo());
                    r.setMaterialId(it.getMaterialId());
                    r.setMaterialName(it.getMaterialName());
                    r.setParentCategory(it.getParentCategory());
                    r.setCategory(it.getCategory());
                    r.setUnit(it.getUnit());
                    r.setPurchaseMonth(h.getPurchaseMonth());
                    r.setPurchaseDate(h.getPurchaseDate());
                    r.setPurchaseQty(it.getPurchaseQty());
                    r.setUnitPrice(it.getUnitPrice());
                    r.setTotalAmount(it.getTotalAmount());
                    r.setHandlerName(h.getHandlerName());
                    r.setVoucherUrl(h.getVoucherUrl());
                    r.setRemark(h.getRemark());
                    r.setCreatedAt(h.getCreatedAt());
                    records.add(r);
                }
            }
        }

        if (!records.isEmpty()) {
            Set<String> storeIdSet = records.stream().map(SelfPurchaseMaterial::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
            if (!storeIdSet.isEmpty()) {
                Map<String, String> supervisorMap = storeAccessService.getSupervisorNamesByStoreIds(storeIdSet);
                records.forEach(r -> r.setSupervisorName(supervisorMap.getOrDefault(r.getStoreId(), "")));
            }
        }

        // 手动分页（铺平后按物料行数分页）
        int total = records.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        Page<SelfPurchaseMaterial> result = new Page<>(pageNum, pageSize, total);
        result.setRecords(records.subList(from, to));
        return result;
    }
}
