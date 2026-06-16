package com.xzcpc.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.constant.AdminRole;
import com.xzcpc.dto.FeishuLoginResp;
import com.xzcpc.entity.AdminPermission;
import com.xzcpc.service.AdminPermissionService;
import com.xzcpc.util.AdminJwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * 本地 Mock 登录接口，绕过飞书 OAuth。
 * 默认关闭，只有显式配置 admin.local-dev-login.enabled=true 时启用。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "admin.local-dev-login", name = "enabled", havingValue = "true")
public class DevAuthController {

    private final AdminJwtUtil adminJwtUtil;
    private final AdminPermissionService adminPermissionService;

    /**
     * 开发环境直接登录，返回测试管理员的 JWT。
     */
    @PostMapping("/dev/login")
    public R<FeishuLoginResp> devLogin(HttpServletRequest request) {
        if (!isLocalRequest(request)) {
            return R.fail(403, "本地测试登录仅允许本机或内网访问");
        }
        String openId = "dev_admin_001";
        String name = "管理员（本地测试）";
        AdminPermission permission = adminPermissionService.ensureLocalDevAdmin(openId, name, "", "admin@test.com", "");
        String role = permission.getRole();
        String token = adminJwtUtil.generate(openId, name, role);
        return R.ok(new FeishuLoginResp(
                token,
                openId,
                name,
                "",
                "admin@test.com",
                "",
                role,
                AdminRole.labelOf(role),
                AdminRole.hasAdminAccess(role)
        ));
    }

    /**
     * 开发环境模拟登录。使用管理员 openId 但保留数据库中的角色，不会强制提权。
     * 先用 /admin 接口改好角色，再调此接口即可模拟任意角色。
     */
    @PostMapping("/dev/login/normal")
    public R<FeishuLoginResp> devLoginNormal(HttpServletRequest request) {
        if (!isLocalRequest(request)) {
            return R.fail(403, "本地测试登录仅允许本机或内网访问");
        }
        String openId = "dev_admin_001";
        AdminPermission permission = adminPermissionService.ensureLoginUser(openId, "模拟用户", "", "test@dev.com", "");
        String role = permission.getRole();
        String token = adminJwtUtil.generate(openId, permission.getName(), role);
        return R.ok(new FeishuLoginResp(
                token,
                openId,
                permission.getName(),
                "",
                "test@dev.com",
                "",
                role,
                AdminRole.labelOf(role),
                AdminRole.hasAdminAccess(role)
        ));
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String host = request.getHeader("Host");
        return isAllowedHost(remoteAddr) || isAllowedHost(host);
    }

    private boolean isAllowedHost(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String host = value.split(":", 2)[0];
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || host.startsWith("192.168.")
                || host.startsWith("10.")
                || host.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }
}
