package com.xzcpc.common.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xzcpc.common.entity.OperationLog;

/**
 * 操作日志服务
 */
public interface OperationLogService {

    /** 异步保存操作日志 */
    void save(OperationLog log);

    /** 分页查询操作日志 */
    IPage<OperationLog> page(IPage<OperationLog> page, String username, String module, String operation);
}
