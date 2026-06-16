package com.xzcpc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.entity.AdminPermission;

import java.util.List;
import java.util.Map;

public interface AdminPermissionService {

    AdminPermission ensureLoginUser(String openId, String name, String avatarUrl, String email, String mobile);

    AdminPermission ensureLocalDevAdmin(String openId, String name, String avatarUrl, String email, String mobile);

    AdminPermission current();

    Page<AdminPermission> page(String name, String role, int pageNum, int pageSize);

    AdminPermission updateRole(Integer id, List<String> roles);

    Map<String, Object> overview();
}
