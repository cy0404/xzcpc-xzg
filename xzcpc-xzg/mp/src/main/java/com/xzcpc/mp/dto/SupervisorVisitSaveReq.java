package com.xzcpc.mp.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 督导拜访单 — 创建/编辑请求
 */
@Data
public class SupervisorVisitSaveReq {

    /** 门店ID */
    private String storeId;

    /** 督导姓名 */
    private String supervisorName;

    /** 拜访日期 */
    private LocalDate visitDate;

    /** 确认人类型：store_manager / owner / both */
    private String confirmPersonType;

    /** 选中的店长确认人 openid */
    private String confirmManagerOpenid;

    /** 选中的店长确认人姓名 */
    private String confirmManagerName;

    /** 选中的加盟商老板确认人 openid */
    private String confirmOwnerOpenid;

    /** 选中的加盟商老板确认人姓名 */
    private String confirmOwnerName;

    /** 回顾上次访店问题与行动追踪 */
    private String lastIssueReview;

    /** 门店当下重点关注 */
    private String currentFocus;

    /** 本月提升重点沟通记录 */
    private String improvementFocus;

    /** 门店 & 加盟商反馈与所需支持 */
    private String storeFeedback;

    /** 行动计划列表 */
    private List<ActionItem> actions;

    @Data
    public static class ActionItem {
        /** 任务名称 */
        private String actionName;

        /** 目标值 */
        private String targetValue;

        /** 具体动作 */
        private String specificAction;

        /** 追踪时间（截止时间） */
        private LocalDate trackingTime;

        /** 负责人 openid */
        private String responsiblePerson;

        /** 负责人角色：store_manager / owner */
        private String responsibleRole;
    }
}
