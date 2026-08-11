package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.common.util.BizCodeUtil;
import com.xzcpc.mp.dto.SupervisorVisitResp;
import com.xzcpc.mp.dto.SupervisorVisitSaveReq;
import com.xzcpc.mp.entity.SupervisorStoreAccess;
import com.xzcpc.mp.entity.SupervisorVisit;
import com.xzcpc.mp.entity.SupervisorVisitAction;
import com.xzcpc.mp.mapper.SupervisorStoreAccessMapper;
import com.xzcpc.mp.mapper.SupervisorVisitActionMapper;
import com.xzcpc.mp.mapper.SupervisorVisitMapper;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.mp.service.SupervisorVisitService;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 督导拜访服务实现 — P2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupervisorVisitServiceImpl implements SupervisorVisitService {

    private final SupervisorVisitMapper visitMapper;
    private final SupervisorVisitActionMapper actionMapper;
    private final SupervisorStoreAccessMapper accessMapper;
    private final StoreMapper storeMapper;
    private final EmployeeMapper employeeMapper;
    private final MpStaffService staffService;
    private final StoreAccessService storeAccessService;

    // ==================== Admin 端 ====================

    @Override
    public Page<SupervisorVisit> adminPage(String storeId, String supervisorName, String visitStatus,
                                           String confirmStatus, Boolean hasOverdue,
                                           String visitDateStart, String visitDateEnd,
                                           String keyword, int pageNum, int pageSize) {
        AdminUser admin = AdminContextHolder.get();

        var qw = new LambdaQueryWrapper<SupervisorVisit>();

        // 非全量管理员（仅督导等）：从 supervisor_store_access 取可访问门店
        if (storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                qw.eq(SupervisorVisit::getStoreId, "__NONE__"); // 无权限门店
            } else {
                qw.in(SupervisorVisit::getStoreId, accessibleStoreIds);
            }
        } else {
            qw.eq(StringUtils.hasText(supervisorName), SupervisorVisit::getSupervisorName, supervisorName);
        }

        qw.eq(StringUtils.hasText(storeId), SupervisorVisit::getStoreId, storeId);
        qw.eq(StringUtils.hasText(visitStatus), SupervisorVisit::getVisitStatus, visitStatus);
        qw.eq(StringUtils.hasText(confirmStatus), SupervisorVisit::getConfirmStatus, confirmStatus);

        if (StringUtils.hasText(visitDateStart)) {
            qw.ge(SupervisorVisit::getVisitDate, LocalDate.parse(visitDateStart));
        }
        if (StringUtils.hasText(visitDateEnd)) {
            qw.le(SupervisorVisit::getVisitDate, LocalDate.parse(visitDateEnd));
        }

        // 关键词搜索：拜访编号 / 门店名称
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SupervisorVisit::getVisitNo, keyword)
                    .or().like(SupervisorVisit::getStoreName, keyword));
        }

        // 存在逾期任务：子查询
        if (Boolean.TRUE.equals(hasOverdue)) {
            qw.exists("SELECT 1 FROM supervisor_visit_action a WHERE a.visit_id = supervisor_visit.id"
                    + " AND a.task_status = 'overdue' AND a.del_flag = 0");
        }

        qw.orderByDesc(SupervisorVisit::getCreatedAt);

        Page<SupervisorVisit> page = visitMapper.selectPage(new Page<>(pageNum, pageSize), qw);

        // 批量填充任务进度统计
        if (!page.getRecords().isEmpty()) {
            fillActionCounts(page.getRecords());
        }

        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupervisorVisit create(SupervisorVisitSaveReq req) {
        AdminUser admin = AdminContextHolder.get();

        SupervisorVisit visit = new SupervisorVisit();
        visit.setVisitNo(BizCodeUtil.of("SV"));
        visit.setStoreId(req.getStoreId());
        visit.setStoreName(getStoreName(req.getStoreId()));
        visit.setSupervisorName(StringUtils.hasText(req.getSupervisorName())
                ? req.getSupervisorName() : admin.getName());
        visit.setVisitDate(req.getVisitDate() != null ? req.getVisitDate() : LocalDate.now());
        visit.setConfirmPersonType(StringUtils.hasText(req.getConfirmPersonType())
                ? req.getConfirmPersonType() : "store_manager");
        visit.setConfirmManagerOpenid(req.getConfirmManagerOpenid());
        visit.setConfirmManagerName(req.getConfirmManagerName());
        visit.setConfirmOwnerOpenid(req.getConfirmOwnerOpenid());
        visit.setConfirmOwnerName(req.getConfirmOwnerName());
        visit.setVisitStatus("draft");
        visit.setConfirmStatus("unsent");

        // 四段沟通记录
        visit.setLastIssueReview(req.getLastIssueReview());
        visit.setCurrentFocus(req.getCurrentFocus());
        visit.setImprovementFocus(req.getImprovementFocus());
        visit.setStoreFeedback(req.getStoreFeedback());

        // 经营数据快照（静态 mock）
        visit.setBizDataSnapshot(buildBizDataSnapshot(req.getStoreId()));

        visitMapper.insert(visit);

        // 保存行动计划
        if (req.getActions() != null && !req.getActions().isEmpty()) {
            saveActions(visit.getId(), req.getActions(),
                    req.getConfirmManagerOpenid(), req.getConfirmOwnerOpenid());
        }

        return visit;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupervisorVisit update(Long id, SupervisorVisitSaveReq req) {
        SupervisorVisit visit = getVisitOrThrow(id);

        if (!"draft".equals(visit.getVisitStatus()) && !"objection".equals(visit.getVisitStatus()) && !"pending_confirm".equals(visit.getVisitStatus())) {
            throw new BusinessException("仅草稿、待确认或异议待处理状态的拜访单可编辑");
        }

        visit.setStoreId(req.getStoreId());
        visit.setStoreName(getStoreName(req.getStoreId()));
        visit.setSupervisorName(req.getSupervisorName());
        visit.setVisitDate(req.getVisitDate());
        visit.setConfirmPersonType(req.getConfirmPersonType());
        visit.setConfirmManagerOpenid(req.getConfirmManagerOpenid());
        visit.setConfirmManagerName(req.getConfirmManagerName());
        visit.setConfirmOwnerOpenid(req.getConfirmOwnerOpenid());
        visit.setConfirmOwnerName(req.getConfirmOwnerName());
        visit.setLastIssueReview(req.getLastIssueReview());
        visit.setCurrentFocus(req.getCurrentFocus());
        visit.setImprovementFocus(req.getImprovementFocus());
        visit.setStoreFeedback(req.getStoreFeedback());
        visit.setBizDataSnapshot(buildBizDataSnapshot(req.getStoreId()));

        visitMapper.updateById(visit);

        // 删除旧行动计划，重新保存
        deleteActionsByVisitId(id);
        if (req.getActions() != null && !req.getActions().isEmpty()) {
            saveActions(id, req.getActions(),
                    req.getConfirmManagerOpenid(), req.getConfirmOwnerOpenid());
        }

        return visit;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        SupervisorVisit visit = getVisitOrThrow(id);

        if (!"draft".equals(visit.getVisitStatus()) && !"objection".equals(visit.getVisitStatus()) && !"pending_confirm".equals(visit.getVisitStatus())) {
            throw new BusinessException("仅草稿、待确认或异议待处理状态可提交确认");
        }

        visit.setVisitStatus("pending_confirm");
        visit.setConfirmStatus("pending");
        visit.setObjectionReason(null);
        visitMapper.updateById(visit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleObjection(Long id, SupervisorVisitSaveReq req) {
        SupervisorVisit visit = getVisitOrThrow(id);

        if (!"objection".equals(visit.getVisitStatus())) {
            throw new BusinessException("仅异议待处理状态可处理");
        }

        // 先更新拜访单内容
        update(id, req);

        // 重新提交
        visit = getVisitOrThrow(id);
        visit.setVisitStatus("pending_confirm");
        visit.setConfirmStatus("pending");
        visit.setObjectionReason(null);
        visitMapper.updateById(visit);
    }

    @Override
    public SupervisorVisitResp getDetail(Long id) {
        return buildDetailResponse(getVisitOrThrow(id));
    }

    @Override
    public SupervisorVisitResp getDetail(Long id, String storeId) {
        SupervisorVisit visit = getVisitOrThrow(id);
        if (!visit.getStoreId().equals(storeId)) {
            throw new BusinessException(403, "无权查看此拜访单");
        }
        return buildDetailResponse(visit);
    }

    private SupervisorVisitResp buildDetailResponse(SupervisorVisit visit) {
        List<SupervisorVisitAction> actions = getActions(visit.getId());

        SupervisorVisitResp resp = new SupervisorVisitResp();
        resp.setVisit(visit);
        resp.setActions(actions);
        resp.setBizData(parseBizDataSnapshot(visit.getBizDataSnapshot()));
        resp.setHistoryVisits(getHistoryVisits(visit.getStoreId(), visit.getId()));
        return resp;
    }

    @Override
    public Map<String, Object> getStoreBizData(String storeId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 静态 mock 经营数据
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("gmv", "¥238,500");
        bizData.put("targetRate", "92.3%");
        bizData.put("realCollectionRate", "88.7%");
        bizData.put("avgOrderPrice", "¥32.5");
        bizData.put("ebitda", "¥45,200");
        bizData.put("staffEfficiency", "¥1,250/人");
        bizData.put("qscScore", "86分");
        bizData.put("negativeRate", "2.1%");
        bizData.put("recentRatings", List.of(
                Map.of("month", "2026-06", "rating", "B+"),
                Map.of("month", "2026-05", "rating", "B"),
                Map.of("month", "2026-04", "rating", "B+")
        ));
        result.put("bizData", bizData);

        // 最近 3 次历史拜访
        List<SupervisorVisit> history = visitMapper.selectList(
                new LambdaQueryWrapper<SupervisorVisit>()
                        .eq(SupervisorVisit::getStoreId, storeId)
                        .orderByDesc(SupervisorVisit::getCreatedAt)
                        .last("LIMIT 3"));
        result.put("historyVisits", history);

        return result;
    }

    @Override
    public List<SupervisorVisitAction> getActions(Long visitId) {
        List<SupervisorVisitAction> actions = actionMapper.selectList(
                new LambdaQueryWrapper<SupervisorVisitAction>()
                        .eq(SupervisorVisitAction::getVisitId, visitId)
                        .orderByAsc(SupervisorVisitAction::getCreatedAt));
        // 动态标记逾期
        for (SupervisorVisitAction a : actions) {
            if ("pending".equals(a.getTaskStatus()) && a.getTrackingTime() != null
                    && a.getTrackingTime().isBefore(LocalDate.now())) {
                a.setTaskStatus("overdue");
            }
        }
        return actions;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAction(Long actionId, String reviewedBy) {
        SupervisorVisitAction action = getActionOrThrow(actionId);

        if (!"pending_review".equals(action.getTaskStatus())) {
            throw new BusinessException("仅待审核状态的任务可审核");
        }

        action.setTaskStatus("completed");
        action.setReviewResult("approved");
        action.setReviewedBy(reviewedBy);
        action.setReviewedAt(LocalDateTime.now());
        actionMapper.updateById(action);

        // 检查是否所有任务都已审核，若是则完成拜访单
        checkAndCompleteVisit(action.getVisitId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectAction(Long actionId, String returnReason, String reviewedBy, LocalDate newTrackingTime) {
        SupervisorVisitAction action = getActionOrThrow(actionId);

        if (!"pending_review".equals(action.getTaskStatus())) {
            throw new BusinessException("仅待审核状态的任务可退回");
        }

        action.setTaskStatus("returned");
        action.setReviewResult("returned");
        action.setReturnReason(returnReason);
        action.setReviewedBy(reviewedBy);
        action.setReviewedAt(LocalDateTime.now());
        if (newTrackingTime != null) {
            action.setTrackingTime(newTrackingTime);
        }
        actionMapper.updateById(action);
    }

    // ==================== MP 端 ====================

    @Override
    public List<SupervisorVisit> pendingVisits(String storeId) {
        LoginUser user = UserContextHolder.get();
        String openid = user != null ? user.getOpenid() : "";
        return visitMapper.selectList(
                new LambdaQueryWrapper<SupervisorVisit>()
                        .eq(SupervisorVisit::getStoreId, storeId)
                        .in(SupervisorVisit::getVisitStatus, "pending_confirm", "objection")
                        .and(w -> w.eq(SupervisorVisit::getConfirmManagerOpenid, openid)
                                .or().eq(SupervisorVisit::getConfirmOwnerOpenid, openid))
                        .orderByDesc(SupervisorVisit::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupervisorVisit confirm(Long visitId, String openid) {
        SupervisorVisit visit = getVisitOrThrow(visitId);

        if (!"pending_confirm".equals(visit.getVisitStatus())) {
            throw new BusinessException("仅待确认状态的拜访单可确认");
        }

        visit.setVisitStatus("in_progress");
        visit.setConfirmStatus("confirmed");
        visit.setConfirmedBy(openid);
        visit.setConfirmedAt(LocalDateTime.now());
        visitMapper.updateById(visit);

        return visit;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupervisorVisit object(Long visitId, String openid, String reason) {
        SupervisorVisit visit = getVisitOrThrow(visitId);

        if (!"pending_confirm".equals(visit.getVisitStatus())) {
            throw new BusinessException("仅待确认状态的拜访单可提出异议");
        }

        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("提出异议必须填写异议说明");
        }

        visit.setVisitStatus("objection");
        visit.setConfirmStatus("objected");
        visit.setObjectionReason(reason);
        visitMapper.updateById(visit);

        return visit;
    }

    @Override
    public Page<SupervisorVisitAction> myActions(String storeId, String openid, String status,
                                                  int pageNum, int pageSize) {
        // 获取该门店下所有拜访单ID
        List<SupervisorVisit> storeVisits = visitMapper.selectList(
                new LambdaQueryWrapper<SupervisorVisit>()
                        .eq(SupervisorVisit::getStoreId, storeId)
                        .eq(SupervisorVisit::getVisitStatus, "in_progress"));

        if (storeVisits.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        List<Long> visitIds = storeVisits.stream()
                .map(SupervisorVisit::getId)
                .collect(Collectors.toList());

        var qw = new LambdaQueryWrapper<SupervisorVisitAction>()
                .in(SupervisorVisitAction::getVisitId, visitIds)
                .orderByDesc(SupervisorVisitAction::getCreatedAt);

        if (StringUtils.hasText(status)) {
            qw.eq(SupervisorVisitAction::getTaskStatus, status);
        }

        Page<SupervisorVisitAction> page = actionMapper.selectPage(new Page<>(pageNum, pageSize), qw);

        // 批量填充关联信息
        if (!page.getRecords().isEmpty()) {
            Map<Long, SupervisorVisit> visitMap = storeVisits.stream()
                    .collect(Collectors.toMap(SupervisorVisit::getId, v -> v));
            for (SupervisorVisitAction action : page.getRecords()) {
                SupervisorVisit v = visitMap.get(action.getVisitId());
                if (v != null) {
                    action.setVisitNo(v.getVisitNo());
                    action.setStoreName(v.getStoreName());
                }
            }
        }

        return page;
    }

    @Override
    public SupervisorVisitAction getActionDetail(Long actionId) {
        SupervisorVisitAction action = getActionOrThrow(actionId);

        // 动态判断逾期
        if ("pending".equals(action.getTaskStatus()) && action.getTrackingTime() != null
                && action.getTrackingTime().isBefore(LocalDate.now())) {
            action.setTaskStatus("overdue");
        }

        SupervisorVisit visit = visitMapper.selectById(action.getVisitId());
        if (visit != null) {
            action.setVisitNo(visit.getVisitNo());
            action.setStoreName(visit.getStoreName());
        }

        return action;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeAction(Long actionId, String openid, String note, String images) {
        SupervisorVisitAction action = getActionOrThrow(actionId);

        if (!"pending".equals(action.getTaskStatus())
                && !"overdue".equals(action.getTaskStatus())
                && !"returned".equals(action.getTaskStatus())) {
            throw new BusinessException("当前任务状态不允许提交完成反馈");
        }

        if (!StringUtils.hasText(note)) {
            throw new BusinessException("完成说明不能为空");
        }

        action.setTaskStatus("pending_review");
        action.setCompleteNote(note);
        action.setCompleteImages(images);
        action.setSubmittedAt(LocalDateTime.now());
        actionMapper.updateById(action);
    }

    // ==================== Private 辅助方法 ====================

    private SupervisorVisit getVisitOrThrow(Long id) {
        SupervisorVisit visit = visitMapper.selectById(id);
        if (visit == null) {
            throw new BusinessException(404, "拜访单不存在");
        }
        return visit;
    }

    private SupervisorVisitAction getActionOrThrow(Long id) {
        SupervisorVisitAction action = actionMapper.selectById(id);
        if (action == null) {
            throw new BusinessException(404, "行动计划不存在");
        }
        return action;
    }

    private String getStoreName(String storeId) {
        if (!StringUtils.hasText(storeId)) {
            return null;
        }
        Store store = storeMapper.selectOne(
                new LambdaQueryWrapper<Store>().eq(Store::getStoreId, storeId));
        return store != null ? store.getStoreName() : null;
    }

    private void saveActions(Long visitId, List<SupervisorVisitSaveReq.ActionItem> items,
                              String confirmManagerOpenid, String confirmOwnerOpenid) {
        for (SupervisorVisitSaveReq.ActionItem item : items) {
            SupervisorVisitAction action = new SupervisorVisitAction();
            action.setVisitId(visitId);
            action.setActionName(item.getActionName());
            action.setTargetValue(item.getTargetValue());
            action.setSpecificAction(item.getSpecificAction());
            action.setTrackingTime(item.getTrackingTime());
            // 根据角色分配对应确认人的 openid
            String role = StringUtils.hasText(item.getResponsibleRole())
                    ? item.getResponsibleRole() : "store_manager";
            String personOpenid = StringUtils.hasText(item.getResponsiblePerson())
                    ? item.getResponsiblePerson() : "";
            if (!StringUtils.hasText(personOpenid)) {
                personOpenid = "owner".equals(role) ?
                    (StringUtils.hasText(confirmOwnerOpenid) ? confirmOwnerOpenid : "") :
                    (StringUtils.hasText(confirmManagerOpenid) ? confirmManagerOpenid : "");
            }
            action.setResponsiblePerson(StringUtils.hasText(personOpenid) ? personOpenid : "");
            action.setResponsibleRole(role);
            action.setTaskStatus("pending");
            actionMapper.insert(action);
        }
    }

    private void deleteActionsByVisitId(Long visitId) {
        actionMapper.delete(
                new LambdaQueryWrapper<SupervisorVisitAction>()
                        .eq(SupervisorVisitAction::getVisitId, visitId));
    }

    private void fillActionCounts(List<SupervisorVisit> visits) {
        List<Long> visitIds = visits.stream()
                .map(SupervisorVisit::getId)
                .collect(Collectors.toList());

        // 批量查询每个拜访单的行动计划状态分布
        List<SupervisorVisitAction> allActions = actionMapper.selectList(
                new LambdaQueryWrapper<SupervisorVisitAction>()
                        .in(SupervisorVisitAction::getVisitId, visitIds));

        Map<Long, List<SupervisorVisitAction>> grouped = allActions.stream()
                .collect(Collectors.groupingBy(SupervisorVisitAction::getVisitId));

        for (SupervisorVisit visit : visits) {
            List<SupervisorVisitAction> actions = grouped.getOrDefault(visit.getId(), List.of());
            visit.setActionCount(actions.size());
            visit.setCompletedCount((int) actions.stream()
                    .filter(a -> "completed".equals(a.getTaskStatus())).count());
            visit.setOverdueCount((int) actions.stream()
                    .filter(a -> isActionOverdue(a)).count());
        }
    }

    private void checkAndCompleteVisit(Long visitId) {
        Long pendingCount = actionMapper.selectCount(
                new LambdaQueryWrapper<SupervisorVisitAction>()
                        .eq(SupervisorVisitAction::getVisitId, visitId)
                        .ne(SupervisorVisitAction::getTaskStatus, "completed"));

        if (pendingCount == 0) {
            SupervisorVisit visit = visitMapper.selectById(visitId);
            if (visit != null && "in_progress".equals(visit.getVisitStatus())) {
                visit.setVisitStatus("completed");
                visitMapper.updateById(visit);
            }
        }
    }

    private String buildBizDataSnapshot(String storeId) {
        // 静态 mock，后续对接外部经营数据接口时替换
        return "{\"gmv\":\"¥238,500\",\"targetRate\":\"92.3%\",\"realCollectionRate\":\"88.7%\","
                + "\"avgOrderPrice\":\"¥32.5\",\"ebitda\":\"¥45,200\",\"staffEfficiency\":\"¥1,250/人\","
                + "\"qscScore\":\"86分\",\"negativeRate\":\"2.1%\","
                + "\"recentRatings\":[{\"month\":\"2026-06\",\"rating\":\"B+\"},"
                + "{\"month\":\"2026-05\",\"rating\":\"B\"},{\"month\":\"2026-04\",\"rating\":\"B+\"}]}";
    }

    private Map<String, Object> parseBizDataSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            // 简单 JSON 解析（避免引入 Jackson 依赖问题）
            // 一期静态数据，直接返回 mock 结构
            return getStoreBizData(null);
        } catch (Exception e) {
            log.warn("解析经营数据快照失败", e);
            return Map.of();
        }
    }

    private boolean isActionOverdue(SupervisorVisitAction a) {
        if ("overdue".equals(a.getTaskStatus()) || "returned".equals(a.getTaskStatus())) return true;
        if ("pending".equals(a.getTaskStatus()) && a.getTrackingTime() != null
                && a.getTrackingTime().isBefore(LocalDate.now())) return true;
        return false;
    }

    private List<SupervisorVisit> getHistoryVisits(String storeId, Long excludeId) {
        return visitMapper.selectList(
                new LambdaQueryWrapper<SupervisorVisit>()
                        .eq(SupervisorVisit::getStoreId, storeId)
                        .ne(excludeId != null, SupervisorVisit::getId, excludeId)
                        .orderByDesc(SupervisorVisit::getCreatedAt)
                        .last("LIMIT 3"));
    }

    @Override
    public Map<String, Object> overview(String storeId, String openid, String role) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 待确认+异议待处理拜访单数（仅当前用户被选为确认人时计数）
        Long pendingConfirm = visitMapper.selectCount(
                new LambdaQueryWrapper<SupervisorVisit>()
                        .eq(SupervisorVisit::getStoreId, storeId)
                        .in(SupervisorVisit::getVisitStatus, "pending_confirm", "objection")
                        .and(w -> w.eq(SupervisorVisit::getConfirmManagerOpenid, openid)
                                .or().eq(SupervisorVisit::getConfirmOwnerOpenid, openid)));
        result.put("pendingConfirm", pendingConfirm);

        // 第一个待确认/异议拜访单ID（点击直接跳详情）
        if (pendingConfirm > 0) {
            SupervisorVisit first = visitMapper.selectOne(
                    new LambdaQueryWrapper<SupervisorVisit>()
                            .eq(SupervisorVisit::getStoreId, storeId)
                            .in(SupervisorVisit::getVisitStatus, "pending_confirm", "objection")
                            .and(w -> w.eq(SupervisorVisit::getConfirmManagerOpenid, openid)
                                    .or().eq(SupervisorVisit::getConfirmOwnerOpenid, openid))
                            .orderByDesc(SupervisorVisit::getCreatedAt)
                            .last("LIMIT 1"));
            result.put("firstPendingVisitId", first != null ? first.getId() : null);
        }

        // 待处理任务数（含逾期和退回）
        List<SupervisorVisit> inProgressVisits = visitMapper.selectList(
                new LambdaQueryWrapper<SupervisorVisit>()
                        .eq(SupervisorVisit::getStoreId, storeId)
                        .eq(SupervisorVisit::getVisitStatus, "in_progress"));

        // 店员看不到任何任务
        if (role != null && role.contains("staff") && !role.contains("store_manager") && !role.contains("owner")) {
            result.put("pendingTasks", 0L);
            result.put("taskItems", List.of());
            return result;
        }

        // 根据用户角色确定可看的任务角色
        String userRole = (role != null && role.contains("owner")) ? "owner" : "store_manager";

        long pendingTasks = 0;
        List<Long> visitIds = inProgressVisits.stream()
                .map(SupervisorVisit::getId)
                .collect(Collectors.toList());
        if (!visitIds.isEmpty()) {
            pendingTasks = actionMapper.selectCount(
                    new LambdaQueryWrapper<SupervisorVisitAction>()
                            .in(SupervisorVisitAction::getVisitId, visitIds)
                            .in(SupervisorVisitAction::getTaskStatus, "pending", "overdue", "returned")
                            .eq(SupervisorVisitAction::getResponsibleRole, userRole));
        }
        result.put("pendingTasks", pendingTasks);

        // 待处理任务列表（首页逐条展示，最多5条）
        List<Map<String, Object>> taskItems = new ArrayList<>();
        if (pendingTasks > 0) {
            List<SupervisorVisitAction> actions = actionMapper.selectList(
                    new LambdaQueryWrapper<SupervisorVisitAction>()
                            .in(SupervisorVisitAction::getVisitId, visitIds)
                            .in(SupervisorVisitAction::getTaskStatus, "pending", "overdue", "returned")
                            .eq(SupervisorVisitAction::getResponsibleRole, userRole)
                            .orderByAsc(SupervisorVisitAction::getTrackingTime)
                            .last("LIMIT 5"));
            for (SupervisorVisitAction a : actions) {
                String status = a.getTaskStatus();
                // 动态判断逾期：pending 且已过追踪日期
                if ("pending".equals(status) && a.getTrackingTime() != null
                        && a.getTrackingTime().isBefore(LocalDate.now())) {
                    status = "overdue";
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", a.getId());
                item.put("actionName", a.getActionName());
                item.put("trackingTime", a.getTrackingTime() != null ? a.getTrackingTime().toString() : null);
                item.put("taskStatus", status);
                item.put("visitNo", getVisitNo(a.getVisitId()));
                item.put("storeName", getVisitStoreName(a.getVisitId()));
                taskItems.add(item);
            }
        }
        result.put("taskItems", taskItems);

        return result;
    }

    @Override
    public List<Map<String, Object>> getStoreEmployees(String storeId) {
        List<Employee> employees = employeeMapper.selectList(
                new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getStoreId, storeId)
                        .eq(Employee::getStatus, "在职"));
        return employees.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getName());
            m.put("openid", e.getOpenid());
            m.put("role", e.getRole());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getSupervisorStores() {
        AdminUser admin = AdminContextHolder.get();
        if (!storeAccessService.isSupervisorOnly(admin)) {
            return storeMapper.selectList(
                    new LambdaQueryWrapper<Store>()
                            .eq(Store::getDelFlag, 0)
                            .orderByAsc(Store::getStoreName))
                    .stream().map(s -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", s.getStoreId());
                        m.put("mendianmingcheng", s.getStoreName());
                        return m;
                    }).collect(Collectors.toList());
        }

        // 从映射表取可访问门店
        List<String> storeIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
        if (storeIds.isEmpty()) return List.of();

        return storeMapper.selectList(
                new LambdaQueryWrapper<Store>()
                        .in(Store::getStoreId, storeIds)
                        .eq(Store::getDelFlag, 0)
                        .orderByAsc(Store::getStoreName))
                .stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getStoreId());
                    m.put("mendianmingcheng", s.getStoreName());
                    return m;
                }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getSupervisorOptions() {
        AdminUser admin = AdminContextHolder.get();
        var qw = new LambdaQueryWrapper<SupervisorStoreAccess>()
                .select(SupervisorStoreAccess::getAdminName, SupervisorStoreAccess::getOpenId)
                .groupBy(SupervisorStoreAccess::getAdminName, SupervisorStoreAccess::getOpenId)
                .orderByAsc(SupervisorStoreAccess::getAdminName);

        if (storeAccessService.isSupervisorOnly(admin)) {
            qw.eq(SupervisorStoreAccess::getOpenId, admin.getOpenId());
        }

        return accessMapper.selectList(qw).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", a.getAdminName());
            m.put("value", a.getAdminName());
            return m;
        }).collect(Collectors.toList());
    }

    private String getVisitNo(Long visitId) {
        SupervisorVisit v = visitMapper.selectById(visitId);
        return v != null ? v.getVisitNo() : null;
    }

    private String getVisitStoreName(Long visitId) {
        SupervisorVisit v = visitMapper.selectById(visitId);
        return v != null ? v.getStoreName() : null;
    }

    /** 从 supervisor_store_access 取当前用户可访问的门店ID列表 */
}
