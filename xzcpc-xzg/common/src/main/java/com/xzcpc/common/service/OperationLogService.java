package com.xzcpc.common.service;

import com.xzcpc.common.entity.OperationLog;

import java.util.Map;

/**
 * 操作日志服务
 */
public interface OperationLogService {

    /** 异步保存操作日志 */
    void save(OperationLog log);

    /** 分页查询操作日志（跳过 COUNT 走裸 SQL） */
    Map<String, Object> page(int page, int size, String username, String module, String operation, String source);
}
