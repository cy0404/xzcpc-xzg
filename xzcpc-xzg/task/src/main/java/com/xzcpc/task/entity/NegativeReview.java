package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店差评记录。
 *
 * 外部系统每天推送差评数据（external_id 去重），总部督导跟进处理。
 * 推送字段：externalId / storeId / storeName / reviewPlatform / reviewScore / reviewDate / reviewContent
 * 内部管理字段：supervisorName / isProcessed / processNote / processMedia
 */
@Data
@TableName("store_negative_review")
public class NegativeReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 外部系统唯一ID，用于去重 */
    private String externalId;

    /** 门店ID */
    private String storeId;

    /** 门店名称（冗余快照） */
    private String storeName;

    /** 评价平台（美团/大众点评/饿了么等） */
    private String reviewPlatform;

    /** 评价分数 */
    private BigDecimal reviewScore;

    /** 评价日期 */
    private LocalDate reviewDate;

    /** 评价内容 */
    private String reviewContent;

    /** 督导姓名（内部指派） */
    private String supervisorName;

    /** 是否处理：0=未处理 1=已处理 */
    private Integer isProcessed;

    /** 处理说明 */
    private String processNote;

    /** 处理图片/视频URL（逗号分隔或JSON数组） */
    private String processMedia;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @Version
    private Integer version;
}
