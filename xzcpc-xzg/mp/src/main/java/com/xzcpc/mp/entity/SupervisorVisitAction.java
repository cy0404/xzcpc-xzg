package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 督导拜访行动计划 — P2
 * 拜访单确认后拆分，在门店端作为待处理任务展示。
 */
@Data
@TableName("supervisor_visit_action")
public class SupervisorVisitAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联拜访单ID */
    private Long visitId;

    /** 任务名称 */
    private String actionName;

    /** 目标值 */
    private String targetValue;

    /** 具体动作 */
    private String specificAction;

    /** 追踪时间（任务截止时间，退回时督导可调整） */
    private LocalDate trackingTime;

    /** 负责人 openid */
    private String responsiblePerson;

    /** 负责人角色：store_manager / owner */
    private String responsibleRole;

    /** 任务状态：pending / overdue / pending_review / returned / completed */
    private String taskStatus;

    /** 完成说明 */
    private String completeNote;

    /** 完成图片URL（逗号分隔） */
    private String completeImages;

    /** 完成反馈提交时间 */
    private LocalDateTime submittedAt;

    /** 审核结果：approved / returned */
    private String reviewResult;

    /** 退回原因 */
    private String returnReason;

    /** 审核人 openid */
    private String reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    // ---- 非数据库字段 ----

    /** 关联拜访单编号 */
    @TableField(exist = false)
    private String visitNo;

    /** 所属门店名称 */
    @TableField(exist = false)
    private String storeName;
}
