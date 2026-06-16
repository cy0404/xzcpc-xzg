package com.xzcpc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.constant.AdminRole;
import com.xzcpc.entity.AdminPermission;
import com.xzcpc.mapper.AdminPermissionMapper;
import com.xzcpc.service.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPermissionServiceImpl implements AdminPermissionService {

    private final AdminPermissionMapper adminPermissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminPermission ensureLoginUser(String openId, String name, String avatarUrl, String email, String mobile) {
        if (!StringUtils.hasText(openId)) {
            throw new BusinessException("飞书用户 openId 为空");
        }

        String finalName = StringUtils.hasText(name) ? name : "飞书用户";
        String finalAvatar = avatarUrl != null ? avatarUrl : "";
        String finalEmail = email != null ? email : "";
        String finalMobile = mobile != null ? mobile : "";
        LocalDateTime now = LocalDateTime.now();

        // 先尝试更新，更新成功直接返回；同时修复 del_flag 不为 0 的历史数据
        LambdaUpdateWrapper<AdminPermission> uw = new LambdaUpdateWrapper<>();
        uw.eq(AdminPermission::getOpenId, openId);
        uw.set(AdminPermission::getName, finalName);
        uw.set(AdminPermission::getAvatarUrl, finalAvatar);
        uw.set(AdminPermission::getEmail, finalEmail);
        uw.set(AdminPermission::getMobile, finalMobile);
        uw.set(AdminPermission::getLastLoginAt, now);
        uw.setSql("del_flag = 0, role = IF(role IS NULL OR role = '', 'normal_user', role)");
        int rows = adminPermissionMapper.update(null, uw);
        if (rows > 0) {
            log.info("更新飞书用户登录信息: openId={}, name={}", openId, finalName);
            return adminPermissionMapper.selectByOpenIdRaw(openId);
        }

        // 无记录则插入
        AdminPermission permission = new AdminPermission();
        permission.setOpenId(openId);
        permission.setName(finalName);
        permission.setAvatarUrl(finalAvatar);
        permission.setEmail(finalEmail);
        permission.setMobile(finalMobile);
        permission.setRole(AdminRole.NORMAL_USER);
        permission.setAuthorizedAt(now);
        permission.setLastLoginAt(now);
        permission.setDelFlag(0);
        try {
            adminPermissionMapper.insert(permission);
            log.info("新增飞书用户权限记录: openId={}, name={}, role={}",
                    permission.getOpenId(), permission.getName(), permission.getRole());
        } catch (DuplicateKeyException e) {
            // 并发插入冲突，其它线程已插入，改为更新（含 del_flag 修复）
            log.warn("openId={} 并发插入冲突，改为更新", openId);
            LambdaUpdateWrapper<AdminPermission> retryUw = new LambdaUpdateWrapper<>();
            retryUw.eq(AdminPermission::getOpenId, openId);
            retryUw.set(AdminPermission::getName, finalName);
            retryUw.set(AdminPermission::getAvatarUrl, finalAvatar);
            retryUw.set(AdminPermission::getEmail, finalEmail);
            retryUw.set(AdminPermission::getMobile, finalMobile);
            retryUw.set(AdminPermission::getLastLoginAt, now);
            retryUw.setSql("del_flag = 0, role = IF(role IS NULL OR role = '', 'normal_user', role)");
            adminPermissionMapper.update(null, retryUw);
        }
        return adminPermissionMapper.selectByOpenIdRaw(openId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminPermission ensureLocalDevAdmin(String openId, String name, String avatarUrl, String email, String mobile) {
        AdminPermission permission = ensureLoginUser(openId, name, avatarUrl, email, mobile);
        if (!AdminRole.isHeadquartersAdmin(permission.getRole())) {
            permission.setRole(AdminRole.HEADQUARTERS_ADMIN);
            permission.setAuthorizedAt(LocalDateTime.now());
            adminPermissionMapper.updateById(permission);
        }
        return permission;
    }

    @Override
    public AdminPermission current() {
        AdminUser user = AdminContextHolder.get();
        if (user == null || !StringUtils.hasText(user.getOpenId())) {
            throw new BusinessException("未登录");
        }
        AdminPermission permission = findByOpenId(user.getOpenId());
        if (permission == null) {
            permission = ensureLoginUser(user.getOpenId(), user.getName(), "", "", "");
        }
        return permission;
    }

    @Override
    public Page<AdminPermission> page(String name, String role, int pageNum, int pageSize) {
        requireAdminAccess();
        LambdaQueryWrapper<AdminPermission> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(AdminPermission::getName, name);
        }
        if (StringUtils.hasText(role)) {
            wrapper.like(AdminPermission::getRole, role);
        }
        wrapper.orderByDesc(AdminPermission::getLastLoginAt)
                .orderByDesc(AdminPermission::getCreatedAt);
        return adminPermissionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminPermission updateRole(Integer id, List<String> roles) {
        requireHeadquartersAdmin();
        if (!AdminRole.allValid(roles)) {
            throw new BusinessException("角色不合法");
        }
        AdminPermission permission = adminPermissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException("用户权限记录不存在");
        }
        permission.setRole(AdminRole.joinRoles(roles));
        permission.setAuthorizedAt(LocalDateTime.now());
        adminPermissionMapper.updateById(permission);
        return permission;
    }

    @Override
    public Map<String, Object> overview() {
        requireAdminAccess();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", adminPermissionMapper.selectCount(null));
        result.put("roleCount", adminPermissionMapper.selectCount(new LambdaQueryWrapper<AdminPermission>()
                .ne(AdminPermission::getRole, AdminRole.NORMAL_USER)));
        result.put("permissionScopes", 8);
        result.put("latestSync", "09:30");
        return result;
    }

    private AdminPermission findByOpenId(String openId) {
        return adminPermissionMapper.selectOne(new LambdaQueryWrapper<AdminPermission>()
                .eq(AdminPermission::getOpenId, openId)
                .last("LIMIT 1"));
    }

    private void requireAdminAccess() {
        if (!current().hasAdminAccess()) {
            throw new BusinessException("暂无权限");
        }
    }

    private void requireHeadquartersAdmin() {
        if (!AdminRole.isHeadquartersAdmin(current().getRole())) {
            throw new BusinessException("仅总部管理员可授权");
        }
    }
}
