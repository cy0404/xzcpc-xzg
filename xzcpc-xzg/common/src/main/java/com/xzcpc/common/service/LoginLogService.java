package com.xzcpc.common.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xzcpc.common.entity.LoginLog;

/**
 * 登录日志服务
 */
public interface LoginLogService {

    /** 异步保存登录日志 */
    void save(LoginLog log);

    /** 分页查询登录日志 */
    IPage<LoginLog> page(IPage<LoginLog> page, String username, String loginType);
}
