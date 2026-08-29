package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.mp.dto.IssueCreateReq;
import com.xzcpc.mp.entity.Issue;

import java.util.List;
import java.util.Map;

public interface IssueService {

    /** 门店上报问题 */
    Issue create(IssueCreateReq req);

    /** 门店问题列表（本店，可按状态筛选） */
    Page<Issue> pageByStore(String storeId, String status, int pageNum, int pageSize);

    /** 跨门店问题列表（名下所有门店） */
    Page<Issue> pageByStores(String openid, String status, int pageNum, int pageSize);

    /** 门店问题概览计数（处理中/待验收/已解决/全部） */
    Map<String, Long> overview(String storeId);

    /** 全部门店问题概览计数（汇总） */
    Map<String, Long> overviewTotal(String openid);

    /** 全部门店待验收计数（按门店分组） */
    List<Map<String, Object>> overviewByStores(String openid);

    /** 门店问题详情（校验归属本店） */
    Issue detail(Long id, String storeId);

    /** 门店验收（仅 pending_acceptance 可验收 → resolved） */
    Issue accept(Long id, String remark);

    /** 飞书卡片验收（无门店登录上下文，供卡片按钮回调；仅 pending_acceptance 可验收 → CLOSED） */
    Issue acceptFromFeishu(Long id);

    /** 总部台账多条件分页 */
    Page<Issue> pageAll(String storeId, String supervisorName, String issueType, String urgency, String status,
                        String keyword, String startDate, String endDate, String source, int pageNum, int pageSize);

    /** 总部详情（不限门店） */
    Issue detailForAdmin(Long id);

    /**
     * 应用外部（象目经理）回传的状态与处理结果。
     * 供 webhook 回调或过渡期人工端点调用。
     *
     * @param extStatus     外部/简化状态（processing|pending_acceptance|resolved|closed 或外部别名）
     * @param processResult 处理结果/最新进度，可空
     */
    Issue applyExternalStatus(Long id, String extStatus, String processResult);

    /** 同步外部表单提交结果（webview postMessage 回传），写入 issue 表 */
    Issue syncExternal(Long externalId, String issueNo, String externalStatus);

    /** 外部系统回调：按 chatId 查门店后 upsert issue 表（同一 externalId 重复调用 = 更新状态） */
    Issue syncByCallback(String chatId, Long externalId, String issueNo, String externalStatus,
                         String title, String storeName, String issueType, String severity, String handler,
                         String equipment, String source);


    /** 按外部 ID 反查 issue */
    Issue findByExternalId(Long externalId);

    /** 代理查询外部处理记录 */
    Map<String, Object> getExternalRecords(Long issueId);

    /** 外部回调推送处理记录（存 JSON 到 issue 表） */
    void saveRecords(Long externalId, String recordsJson);

    /** 代理门店回复 */
    Map<String, Object> replyExternal(Long issueId, String replyText, String mediaUrls);
}
