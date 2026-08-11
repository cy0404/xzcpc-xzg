package com.xzcpc.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.task.entity.InventoryDifference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * P0 A1: 盘点差异处理服务
 */
public interface DifferenceService {

    /**
     * 任务提交后异步生成差异项
     * 差异 = 实盘数量 - 账面数量
     * @return 生成的差异项列表
     */
    List<InventoryDifference> generateDifferences(Integer taskId, Map<String, BigDecimal> bookQtyMap);

    /**
     * 差异项列表（管理端）
     */
    Page<InventoryDifference> listDifferences(String storeId, String status, String diffType,
                                               int pageNum, int pageSize);

    /**
     * 确认调整
     */
    void adjust(Long diffId, String operator, String remark);

    /**
     * 标记无需调整
     */
    void close(Long diffId, String operator, String remark);

    /**
     * 转问题单
     */
    void convertToIssue(Long diffId, String operator, String remark);
}
