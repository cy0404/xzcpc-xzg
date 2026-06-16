package com.xzcpc.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xzcpc.common.entity.LoginLog;
import com.xzcpc.common.mapper.LoginLogMapper;
import com.xzcpc.common.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final LoginLogMapper loginLogMapper;

    @Async("logTaskExecutor")
    @Override
    public void save(LoginLog log) {
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(LocalDateTime.now());
        }
        loginLogMapper.insert(log);
    }

    @Override
    public IPage<LoginLog> page(IPage<LoginLog> page, String username, String loginType) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(username), LoginLog::getUsername, username);
        wrapper.eq(StringUtils.hasText(loginType), LoginLog::getLoginType, loginType);
        wrapper.orderByDesc(LoginLog::getCreatedAt);
        return loginLogMapper.selectPage(page, wrapper);
    }
}
