package com.xzcpc.task.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 外部系统推送的差评数据条目。
 * 字段对应对方每天推送的数据结构。
 */
@Data
public class NegativeReviewSyncReq {

    /** 外部系统唯一ID */
    private String externalId;

    /** 门店ID */
    private String storeId;

    /** 门店名称 */
    private String storeName;

    /** 评价平台 */
    private String reviewPlatform;

    /** 评价分数 */
    private BigDecimal reviewScore;

    /** 评价日期（格式 yyyy-MM-dd） */
    private String reviewDate;

    /** 评价内容 */
    private String reviewContent;
}
