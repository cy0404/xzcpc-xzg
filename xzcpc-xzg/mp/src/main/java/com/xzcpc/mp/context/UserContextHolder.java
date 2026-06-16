package com.xzcpc.mp.context;

public class UserContextHolder {
    private static final ThreadLocal<LoginUser> USER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        USER.set(user);
    }

    public static LoginUser get() {
        return USER.get();
    }

    public static void clear() {
        USER.remove();
    }
}
