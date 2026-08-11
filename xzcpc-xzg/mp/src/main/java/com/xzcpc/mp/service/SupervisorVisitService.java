package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.mp.dto.SupervisorVisitResp;
import com.xzcpc.mp.dto.SupervisorVisitSaveReq;
import com.xzcpc.mp.entity.SupervisorVisit;
import com.xzcpc.mp.entity.SupervisorVisitAction;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 督导拜访服务 — P2
 */
public interface SupervisorVisitService {

    // ==================== Admin 端 ====================

    /** 总部台账分页查询 */
    Page<SupervisorVisit> adminPage(String storeId, String supervisorName, String visitStatus,
                                   String confirmStatus, Boolean hasOverdue,
                                   String visitDateStart, String visitDateEnd,
                                   String keyword, int pageNum, int pageSize);

    /** 创建拜访单（草稿） */
    SupervisorVisit create(SupervisorVisitSaveReq req);

    /** 编辑拜访单（仅草稿/异议待处理状态可编辑） */
    SupervisorVisit update(Long id, SupervisorVisitSaveReq req);

    /** 提交确认（草稿→待确认） */
    void submit(Long id);

    /** 处理异议后重新提交（异议待处理→待确认） */
    void handleObjection(Long id, SupervisorVisitSaveReq req);

    /** 拜访单详情（含门店经营数据 + 历史记录 + 行动计划），管理员调用 */
    SupervisorVisitResp getDetail(Long id);

    /** 拜访单详情（含门店经营数据 + 历史记录 + 行动计划），小程序端调用（校验门店归属） */
    SupervisorVisitResp getDetail(Long id, String storeId);

    /** 获取门店经营数据 + 最近3次历史拜访（一期静态mock） */
    Map<String, Object> getStoreBizData(String storeId);

    /** 获取行动计划列表 */
    List<SupervisorVisitAction> getActions(Long visitId);

    /** 审核通过 */
    void approveAction(Long actionId, String reviewedBy);

    /** 审核退回（可同时调整追踪时间） */
    void rejectAction(Long actionId, String returnReason, String reviewedBy, LocalDate newTrackingTime);

    // ==================== MP 端 ====================

    /** 待确认拜访单列表（按门店） */
    List<SupervisorVisit> pendingVisits(String storeId);

    /** 确认拜访单 */
    SupervisorVisit confirm(Long visitId, String openid);

    /** 提出异议 */
    SupervisorVisit object(Long visitId, String openid, String reason);

    /** 我的拜访任务列表 */
    Page<SupervisorVisitAction> myActions(String storeId, String openid, String status, int pageNum, int pageSize);

    /** 拜访任务详情 */
    SupervisorVisitAction getActionDetail(Long actionId);

    /** 提交完成反馈 */
    void completeAction(Long actionId, String openid, String note, String images);

    /** 首页概览：返回待确认数和待处理任务数 */
    Map<String, Object> overview(String storeId, String openid, String role);

    /** 查询门店的在职员工作为确认人候选 */
    List<Map<String, Object>> getStoreEmployees(String storeId);

    /** 查询当前督导负责的门店列表 */
    List<Map<String, Object>> getSupervisorStores();

    /** 查询当前用户可选的督导列表（普通督导只能选自己，领导/管理员选全部） */
    List<Map<String, Object>> getSupervisorOptions();
}
