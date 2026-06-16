package com.xzcpc.common.context;

/**
 * 总部端管理员 ThreadLocal 上下文。
 */
public class AdminContextHolder {
    private static final ThreadLocal<AdminUser> USER = new ThreadLocal<>();

    public static void set(AdminUser user) {
        USER.set(user);
    }

    public static AdminUser get() {
        return USER.get();
    }

    public static void clear() {
        USER.remove();
    }
}
