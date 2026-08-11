package com.xzcpc.mp.service.impl;

import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.mp.entity.SupervisorStoreAccess;
import com.xzcpc.mp.mapper.SupervisorStoreAccessMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门店访问权限服务实现。
 * 基于 supervisor_store_access 表控制督导可访问的门店范围。
 */
@Service
@RequiredArgsConstructor
public class StoreAccessServiceImpl implements StoreAccessService {

    private final SupervisorStoreAccessMapper accessMapper;

    @Override
    public List<String> getAccessibleStoreIds(String openId) {
        return accessMapper.selectList(
                new LambdaQueryWrapper<SupervisorStoreAccess>()
                        .eq(SupervisorStoreAccess::getOpenId, openId))
                .stream()
                .map(SupervisorStoreAccess::getStoreId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> getSupervisorNamesByStoreIds(Collection<String> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) return Map.of();
        return accessMapper.selectList(
                new LambdaQueryWrapper<SupervisorStoreAccess>()
                        .in(SupervisorStoreAccess::getStoreId, storeIds))
                .stream()
                .collect(Collectors.toMap(
                        SupervisorStoreAccess::getStoreId,
                        SupervisorStoreAccess::getAdminName,
                        (a, b) -> a));
    }

    @Override
    public boolean isSupervisorOnly(AdminUser admin) {
        if (admin == null) return false;
        return admin.isSupervisorOnly();
    }

    @Override
    public List<String> getAccessibleStoreIdsBySupervisorName(String supervisorName) {
        return accessMapper.selectList(
                new LambdaQueryWrapper<SupervisorStoreAccess>()
                        .eq(SupervisorStoreAccess::getAdminName, supervisorName))
                .stream()
                .map(SupervisorStoreAccess::getStoreId)
                .distinct()
                .collect(Collectors.toList());
    }
}
