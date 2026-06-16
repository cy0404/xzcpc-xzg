package com.xzcpc.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xzcpc.common.entity.OperationLog;
import com.xzcpc.common.mapper.OperationLogMapper;
import com.xzcpc.common.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Async("logTaskExecutor")
    @Override
    public void save(OperationLog log) {
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(LocalDateTime.now());
        }
        operationLogMapper.insert(log);
    }

    @Override
    public IPage<OperationLog> page(IPage<OperationLog> page, String username, String module, String operation) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(username), OperationLog::getUsername, username);
        wrapper.eq(StringUtils.hasText(module), OperationLog::getModule, module);
        wrapper.eq(StringUtils.hasText(operation), OperationLog::getOperation, operation);
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        return operationLogMapper.selectPage(page, wrapper);
    }
}
