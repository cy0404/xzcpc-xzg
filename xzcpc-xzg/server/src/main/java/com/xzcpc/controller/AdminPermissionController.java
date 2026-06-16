package com.xzcpc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.dto.AdminRoleUpdateReq;
import com.xzcpc.entity.AdminPermission;
import com.xzcpc.service.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final AdminPermissionService adminPermissionService;

    @GetMapping("/me")
    public R<AdminPermission> current() {
        return R.ok(adminPermissionService.current());
    }

    @OpLog(module = "权限", operation = "查询列表")
    @GetMapping
    public R<Page<AdminPermission>> list(@RequestParam(defaultValue = "") String name,
                                         @RequestParam(defaultValue = "") String role,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(adminPermissionService.page(name, role, pageNum, pageSize));
    }

    @OpLog(module = "权限", operation = "概览")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(adminPermissionService.overview());
    }

    @OpLog(module = "权限", operation = "授权")
    @PutMapping("/{id}/role")
    public R<AdminPermission> updateRole(@PathVariable Integer id, @RequestBody AdminRoleUpdateReq req) {
        return R.ok(adminPermissionService.updateRole(id, req.getRoles()));
    }
}
