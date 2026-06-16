package com.xzcpc.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    // 业务异常类，携带错误码，由 GlobalExceptionHandler 统一处理

    private final int code;

    // 构造业务异常，默认错误码 500
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    // 构造业务异常，自定义错误码
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
