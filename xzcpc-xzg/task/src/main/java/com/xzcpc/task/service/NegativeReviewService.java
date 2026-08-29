package com.xzcpc.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.task.dto.NegativeReviewSyncReq;
import com.xzcpc.task.entity.NegativeReview;

import java.util.List;

/**
 * 门店差评记录服务。
 */
public interface NegativeReviewService {

    /**
     * 批量同步外部推送的差评数据（按 externalId upsert）。
     *
     * @param items 推送的差评列表
     * @return 同步结果统计
     */
    int batchSync(List<NegativeReviewSyncReq> items);

    /**
     * 总部差评台账分页查询。
     */
    Page<NegativeReview> pageAll(String storeId, String supervisorName, String reviewPlatform,
                                  Integer isProcessed, String startDate, String endDate,
                                  String keyword, int pageNum, int pageSize);

    /**
     * 详情查询。
     */
    NegativeReview detail(Long id);

    /**
     * 更新督导和处理信息。
     */
    NegativeReview updateProcess(Long id, String supervisorName, Integer isProcessed,
                                  String processNote, String processMedia);
}
