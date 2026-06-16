package com.xzcpc.constant;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 总部后台角色常量。支持多角色（逗号分隔字符串）。
 */
public final class AdminRole {

    public static final String HEADQUARTERS_ADMIN = "headquarters_admin";
    public static final String FINANCE_ADMIN = "finance_admin";
    public static final String HR_ADMIN = "hr_admin";
    public static final String OPERATION_ADMIN = "operation_admin";
    public static final String NORMAL_USER = "normal_user";

    private static final Set<String> ROLES = Set.of(
            HEADQUARTERS_ADMIN,
            FINANCE_ADMIN,
            HR_ADMIN,
            OPERATION_ADMIN,
            NORMAL_USER
    );

    /** 具有后台访问权限的角色（排除普通用户） */
    private static final Set<String> ADMIN_ROLES = Set.of(
            HEADQUARTERS_ADMIN,
            FINANCE_ADMIN,
            HR_ADMIN,
            OPERATION_ADMIN
    );

    private static final Map<String, String> ROLE_NAMES = Map.of(
            HEADQUARTERS_ADMIN, "总部管理员",
            FINANCE_ADMIN, "财务负责人",
            HR_ADMIN, "人事负责人",
            OPERATION_ADMIN, "运营负责人",
            NORMAL_USER, "普通用户"
    );

    private AdminRole() {
    }

    /** 将逗号分隔的角色字符串解析为列表 */
    public static List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Collections.singletonList(NORMAL_USER);
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toList());
    }

    /** 将角色列表合并为逗号分隔字符串，过滤无效角色 */
    public static String joinRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return NORMAL_USER;
        }
        String joined = roles.stream()
                .filter(ROLES::contains)
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? NORMAL_USER : joined;
    }

    /** 校验逗号分隔的角色字符串中每个角色是否合法 */
    public static boolean isValid(String role) {
        return ROLES.contains(role);
    }

    /** 角色列表是否全部合法 */
    public static boolean allValid(List<String> roles) {
        return roles != null && roles.stream().allMatch(ROLES::contains);
    }

    /** 是否有后台管理访问权限（至少含一个管理员角色） */
    public static boolean hasAdminAccess(String roles) {
        if (roles == null || roles.isBlank()) return false;
        return parseRoles(roles).stream().anyMatch(ADMIN_ROLES::contains);
    }

    /** 是否为总部管理员 */
    public static boolean isHeadquartersAdmin(String roles) {
        if (roles == null || roles.isBlank()) return false;
        return parseRoles(roles).stream().anyMatch(HEADQUARTERS_ADMIN::equals);
    }

    /** 获取角色展示名（多角色逗号分隔） */
    public static String labelOf(String roles) {
        if (roles == null || roles.isBlank()) return "普通用户";
        return parseRoles(roles).stream()
                .map(r -> ROLE_NAMES.getOrDefault(r, "普通用户"))
                .collect(Collectors.joining("、"));
    }
}
