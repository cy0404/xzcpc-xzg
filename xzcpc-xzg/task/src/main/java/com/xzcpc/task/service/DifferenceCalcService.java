package com.xzcpc.task.service;

import java.util.Map;

/**
 * 盘点差异计算服务
 * 计算公式：理论剩余 = 上月剩余 + 采购 + 订货 + 调货净值 + 还货净值 - 报损 + 自购 - 消耗
 */
public interface DifferenceCalcService {

    /**
     * 对已提交任务执行差异计算并保存结果
     * @param taskId 任务ID
     * @return 生成的差异项数量
     */
    int calculateAndSaveDifferences(Integer taskId);

    /**
     * 获取差异阈值（从 sys_config 读取，默认 0.5）
     */
    double getThresholdRate();

    /**
     * 更新差异阈值
     */
    void updateThresholdRate(double rate);

    /**
     * 根据新阈值重算所有差异的 is_large 标记
     */
    int recalcIsLarge(double rate);

    /**
     * 批量计算所有未计算的差异任务
     */
    int batchCalculateUncounted();

    /**
     * 按盘点月份批量重算差异（该月所有 submitted monthly 任务，已算任务覆盖重算）
     */
    int recalculateByMonth(String taskMonth);

    /**
     * 获取差异任务列表（按任务分组，仅 submitted 状态，支持门店/督导/月份筛选）
     */
    Map<String, Object> listDiffTasks(int pageNum, int pageSize, String storeIds, String supervisorName, String taskMonth);

    /**
     * 获取某任务的所有差异项明细
     */
    Map<String, Object> getDiffDetail(Integer taskId);

    /**
     * 修改 adjusted_qty 并重算差异（未计算差异行只改数量，不重算）
     */
    void modifyAdjustedQty(Long diffId, java.math.BigDecimal newAdjustedQty, String operator);

    /**
     * 按物料维度聚合差异数据（跨任务/跨门店）
     * @param taskMonth 盘点月份（必填）
     * @param storeIds 门店筛选（选填，逗号分隔）
     * @param supervisorName 督导筛选（选填）
     * @return materials（按物料聚合的差异汇总）+ months（可用月份列表）
     */
    Map<String, Object> listDiffMaterials(String taskMonth, String storeIds, String supervisorName);
}
