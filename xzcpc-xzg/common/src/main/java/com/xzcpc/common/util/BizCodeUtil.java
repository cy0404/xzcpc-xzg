package com.xzcpc.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务编码生成工具：前缀 + yyyyMMddHHmmss + 4位自增序号
 */
public final class BizCodeUtil {

    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static String of(String prefix) {
        return prefix + LocalDateTime.now().format(FMT)
                + String.format("%04d", SEQ.incrementAndGet() % 10000);
    }

    private BizCodeUtil() {}
}
