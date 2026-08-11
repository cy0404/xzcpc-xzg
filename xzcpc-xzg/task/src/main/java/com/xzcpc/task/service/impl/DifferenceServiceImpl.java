package com.xzcpc.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.task.entity.DifferenceProcessLog;
import com.xzcpc.task.entity.InventoryDifference;
import com.xzcpc.task.mapper.DifferenceProcessLogMapper;
import com.xzcpc.task.mapper.InventoryDifferenceMapper;
import com.xzcpc.task.service.DifferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 旧版差异处理服务 — 保持兼容，新逻辑请使用 {@link DifferenceCalcServiceImpl}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifferenceServiceImpl implements DifferenceService {

    private final InventoryDifferenceMapper diffMapper;
    private final DifferenceProcessLogMapper logMapper;

    @Override
    @Transactional
    public List<InventoryDifference> generateDifferences(Integer taskId, Map<String, BigDecimal> bookQtyMap) {
        // 旧版逻辑已废弃，新逻辑请调用 DifferenceCalcService.calculateAndSaveDifferences()
        log.warn("generateDifferences 已废弃，请使用 DifferenceCalcService");
        return new ArrayList<>();
    }

    @Override
    public Page<InventoryDifference> listDifferences(String storeId, String status, String diffType,
                                                      int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryDifference> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(InventoryDifference::getStatus, status);
        }
        if (diffType != null && !diffType.isEmpty()) {
            // diffType 在新 entity 中已移除，忽略此筛选
        }
        wrapper.orderByDesc(InventoryDifference::getCreatedAt);
        return diffMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional
    public void adjust(Long diffId, String operator, String remark) {
        InventoryDifference diff = getPendingDiff(diffId);
        diff.setStatus("adjusted");
        diff.setHandler(operator);
        diff.setHandledAt(LocalDateTime.now());
        diff.setRemark(remark);
        diffMapper.updateById(diff);
        saveLog(diffId, "adjust", operator, remark);
    }

    @Override
    @Transactional
    public void close(Long diffId, String operator, String remark) {
        InventoryDifference diff = getPendingDiff(diffId);
        diff.setStatus("closed");
        diff.setHandler(operator);
        diff.setHandledAt(LocalDateTime.now());
        diff.setRemark(remark);
        diffMapper.updateById(diff);
        saveLog(diffId, "close", operator, remark);
    }

    @Override
    @Transactional
    public void convertToIssue(Long diffId, String operator, String remark) {
        InventoryDifference diff = getPendingDiff(diffId);
        diff.setStatus("closed");
        diff.setHandler(operator);
        diff.setHandledAt(LocalDateTime.now());
        diff.setRemark("转问题单: " + (remark != null ? remark : ""));
        diffMapper.updateById(diff);
        saveLog(diffId, "convert", operator, remark);
    }

    private InventoryDifference getPendingDiff(Long diffId) {
        InventoryDifference diff = diffMapper.selectById(diffId);
        if (diff == null) throw new BusinessException("差异项不存在");
        if (diff.isTerminal()) throw new BusinessException("差异项已是终态，不可重复操作");
        return diff;
    }

    private void saveLog(Long diffId, String action, String operator, String remark) {
        DifferenceProcessLog log = new DifferenceProcessLog();
        log.setDiffId(diffId);
        log.setAction(action);
        log.setOperator(operator);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }
}
