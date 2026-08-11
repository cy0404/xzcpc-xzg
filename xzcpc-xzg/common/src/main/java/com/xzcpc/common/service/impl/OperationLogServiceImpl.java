package com.xzcpc.common.service.impl;

import com.xzcpc.common.entity.OperationLog;
import com.xzcpc.common.mapper.OperationLogMapper;
import com.xzcpc.common.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

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

    /**
     * 手写分页，绕过 MyBatis-Plus 分页插件的 COUNT(*)。
     * 多查一行来判断是否有下一页，前端用估算 total。
     */
    @Override
    public Map<String, Object> page(int page, int size, String username, String module, String operation, String source) {
        long offset = (long) (page - 1) * size;
        // 多查 1 条，判断有没有下一页
        List<OperationLog> list = operationLogMapper.selectPageRaw(
                source, username, module, operation, offset, size + 1);

        boolean hasMore = list.size() > size;
        if (hasMore) list = list.subList(0, size);

        long total = hasMore ? (long) page * size + 1 : (long) (page - 1) * size + list.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("current", page);
        result.put("size", size);
        return result;
    }
}
