package com.xzcpc.common.response;

import lombok.Data;

@Data
public class R<T> {
    // 统一响应体，包含 code、message 和泛型数据 data

    private int code;
    private String message;
    private T data;

    // 私有构造，禁止外部直接实例化
    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 返回成功响应，无数据体
    public static <T> R<T> ok() {
        return new R<>(200, "success", null);
    }

    // 返回成功响应，携带数据
    public static <T> R<T> ok(T data) {
        return new R<>(200, "success", data);
    }

    // 返回失败响应，默认状态码 500
    public static <T> R<T> fail(String message) {
        return new R<>(500, message, null);
    }

    // 返回失败响应，自定义状态码
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
