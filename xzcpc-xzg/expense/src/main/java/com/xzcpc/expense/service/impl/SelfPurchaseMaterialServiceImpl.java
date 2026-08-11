package com.xzcpc.expense.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.expense.entity.SelfPurchaseMaterial;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialMapper;
import com.xzcpc.expense.service.SelfPurchaseMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SelfPurchaseMaterialServiceImpl implements SelfPurchaseMaterialService {

    private final SelfPurchaseMaterialMapper mapper;
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
        Page<SelfPurchaseMaterial> result = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<SelfPurchaseMaterial> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<String> storeIdSet = records.stream().map(SelfPurchaseMaterial::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
            if (!storeIdSet.isEmpty()) {
                Map<String, String> supervisorMap = storeAccessService.getSupervisorNamesByStoreIds(storeIdSet);
                records.forEach(r -> r.setSupervisorName(supervisorMap.getOrDefault(r.getStoreId(), "")));
            }
        }
        return result;
    }
}
