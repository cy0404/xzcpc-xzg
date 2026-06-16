package com.xzcpc.common.context;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;

/**
 * 总部端管理员用户信息（从飞书登录获取）。
 */
@Data
@AllArgsConstructor
public class AdminUser {

    private static final Set<String> ADMIN_ROLES = Set.of(
            "headquarters_admin", "finance_admin", "hr_admin", "operation_admin"
    );
    private static final String HEADQUARTERS_ADMIN = "headquarters_admin";
    private static final String NORMAL_USER = "normal_user";

    /** 飞书 open_id */
    private String openId;
    /** 飞书用户姓名 */
    private String name;
    /** 飞书头像 */
    private String avatarUrl;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String mobile;
    /** 权限角色（逗号分隔） */
    private String role;

    /** 解析角色为列表 */
    public List<String> getRoleList() {
        if (role == null || role.isBlank()) {
            return Collections.singletonList(NORMAL_USER);
        }
        return Arrays.stream(role.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .toList();
    }

    /** 是否有后台管理权限（至少含一个管理员角色） */
    public boolean hasAdminAccess() {
        if (role == null || role.isBlank()) return false;
        return getRoleList().stream().anyMatch(ADMIN_ROLES::contains);
    }

    /** 是否为总部管理员 */
    public boolean isHeadquartersAdmin() {
        if (role == null || role.isBlank()) return false;
        return getRoleList().stream().anyMatch(HEADQUARTERS_ADMIN::equals);
    }
}
