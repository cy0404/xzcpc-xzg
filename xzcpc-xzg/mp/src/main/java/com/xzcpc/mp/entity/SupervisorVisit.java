package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 督导拜访单 — P2
 */
@Data
@TableName("supervisor_visit")
public class SupervisorVisit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 拜访单编号（系统生成，如 SV202607210001） */
    private String visitNo;

    /** 拜访门店ID */
    private String storeId;

    /** 门店名称（冗余快照） */
    private String storeName;

    /** 督导姓名 */
    private String supervisorName;

    /** 拜访日期 */
    private LocalDate visitDate;

    /** 确认人类型：store_manager(店长) / owner(老板) / both(同时发送) */
    private String confirmPersonType;

    /** 选中的店长确认人 openid（创建时记录，编辑时回填） */
    private String confirmManagerOpenid;

    /** 选中的店长确认人姓名 */
    private String confirmManagerName;

    /** 选中的加盟商老板确认人 openid（创建时记录，编辑时回填） */
    private String confirmOwnerOpenid;

    /** 选中的加盟商老板确认人姓名 */
    private String confirmOwnerName;

    /** 拜访状态：draft / pending_confirm / objection / in_progress / completed */
    private String visitStatus;

    /** 确认状态：unsent / pending / confirmed / objected */
    private String confirmStatus;

    /** 实际确认人 openid */
    private String confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 异议说明 */
    private String objectionReason;

    /** 回顾上次访店问题与行动追踪 */
    private String lastIssueReview;

    /** 门店当下重点关注 */
    private String currentFocus;

    /** 本月提升重点沟通记录 */
    private String improvementFocus;

    /** 门店 & 加盟商反馈与所需支持 */
    private String storeFeedback;

    /** 门店经营数据快照（JSON）：GMV/目标达成率/实收率/客单价/EBITDA/人效/QSC分数/差评率/最近3次门店评级 */
    private String bizDataSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    // ---- 非数据库字段，列表展示用 ----

    /** 行动计划总数 */
    @TableField(exist = false)
    private Integer actionCount;

    /** 已完成任务数 */
    @TableField(exist = false)
    private Integer completedCount;

    /** 逾期任务数 */
    @TableField(exist = false)
    private Integer overdueCount;
}
