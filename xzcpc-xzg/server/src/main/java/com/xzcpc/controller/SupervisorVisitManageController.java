package com.xzcpc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.dto.SupervisorVisitResp;
import com.xzcpc.mp.dto.SupervisorVisitSaveReq;
import com.xzcpc.mp.entity.SupervisorVisit;
import com.xzcpc.mp.entity.SupervisorVisitAction;
import com.xzcpc.mp.service.SupervisorVisitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * P2: 总部端督导拜访管理
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/supervisor-visit")
@RequiredArgsConstructor
public class SupervisorVisitManageController {

    private final SupervisorVisitService supervisorVisitService;

    /** 督导拜访台账 */
    @GetMapping
    public R<Page<SupervisorVisit>> list(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String supervisorName,
            @RequestParam(required = false) String visitStatus,
            @RequestParam(required = false) String confirmStatus,
            @RequestParam(required = false) Boolean hasOverdue,
            @RequestParam(required = false) String visitDateStart,
            @RequestParam(required = false) String visitDateEnd,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(supervisorVisitService.adminPage(storeId, supervisorName, visitStatus,
                confirmStatus, hasOverdue, visitDateStart, visitDateEnd,
                keyword, pageNum, pageSize));
    }

    /** 拜访单详情 */
    @GetMapping("/{id}")
    public R<SupervisorVisitResp> detail(@PathVariable Long id) {
        return R.ok(supervisorVisitService.getDetail(id));
    }

    /** 创建拜访单 */
    @OpLog(module = "督导拜访", operation = "创建拜访单")
    @PostMapping
    public R<SupervisorVisit> create(@RequestBody SupervisorVisitSaveReq req) {
        return R.ok(supervisorVisitService.create(req));
    }

    /** 编辑拜访单 */
    @OpLog(module = "督导拜访", operation = "编辑拜访单")
    @PutMapping("/{id}")
    public R<SupervisorVisit> update(@PathVariable Long id, @RequestBody SupervisorVisitSaveReq req) {
        return R.ok(supervisorVisitService.update(id, req));
    }

    /** 提交确认 */
    @OpLog(module = "督导拜访", operation = "提交确认")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        supervisorVisitService.submit(id);
        return R.ok();
    }

    /** 处理异议后重新提交 */
    @OpLog(module = "督导拜访", operation = "处理异议")
    @PostMapping("/{id}/handle-objection")
    public R<Void> handleObjection(@PathVariable Long id, @RequestBody SupervisorVisitSaveReq req) {
        supervisorVisitService.handleObjection(id, req);
        return R.ok();
    }

    /** 可选的督导列表（普通督导只能选自己，领导/管理员选全部） */
    @GetMapping("/supervisor-options")
    public R<List<Map<String, Object>>> supervisorOptions() {
        return R.ok(supervisorVisitService.getSupervisorOptions());
    }

    /** 督导负责的门店列表（按 supervisor_name 过滤） */
    @GetMapping("/supervisor-stores")
    public R<List<Map<String, Object>>> supervisorStores() {
        return R.ok(supervisorVisitService.getSupervisorStores());
    }

    /** 门店员工列表（确认人候选） */
    @GetMapping("/store-employees/{storeId}")
    public R<List<Map<String, Object>>> storeEmployees(@PathVariable String storeId) {
        return R.ok(supervisorVisitService.getStoreEmployees(storeId));
    }

    /** 门店经营数据 + 历史拜访记录 */
    @GetMapping("/store-data/{storeId}")
    public R<Map<String, Object>> storeData(@PathVariable String storeId) {
        return R.ok(supervisorVisitService.getStoreBizData(storeId));
    }

    /** 行动计划列表 */
    @GetMapping("/{id}/actions")
    public R<List<SupervisorVisitAction>> actions(@PathVariable Long id) {
        return R.ok(supervisorVisitService.getActions(id));
    }

    /** 审核通过 */
    @OpLog(module = "督导拜访", operation = "审核通过")
    @PostMapping("/actions/{actionId}/approve")
    public R<Void> approveAction(@PathVariable Long actionId) {
        supervisorVisitService.approveAction(actionId, null);
        return R.ok();
    }

    /** 审核退回 */
    @OpLog(module = "督导拜访", operation = "审核退回")
    @PostMapping("/actions/{actionId}/reject")
    public R<Void> rejectAction(@PathVariable Long actionId,
                                @RequestParam(required = false) String returnReason,
                                @RequestParam(required = false) String newTrackingTime) {
        LocalDate newDate = null;
        if (newTrackingTime != null && !newTrackingTime.isEmpty()) {
            newDate = LocalDate.parse(newTrackingTime);
        }
        supervisorVisitService.rejectAction(actionId, returnReason, null, newDate);
        return R.ok();
    }
}
