package com.xzcpc.mp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.entity.SupervisorVisit;
import com.xzcpc.mp.entity.SupervisorVisitAction;
import com.xzcpc.mp.service.SupervisorVisitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Map;

/**
 * P2: 小程序端督导拜访 — 确认、异议、任务执行
 */
@Slf4j
@RestController
@RequestMapping("/api/mp/supervisor-visit")
@RequiredArgsConstructor
public class MpSupervisorVisitController {

    private final SupervisorVisitService supervisorVisitService;

    /** 待确认拜访单列表 */
    @GetMapping("/pending")
    public R<List<SupervisorVisit>> pendingVisits() {
        LoginUser user = UserContextHolder.get();
        return R.ok(supervisorVisitService.pendingVisits(user.getStoreId()));
    }

    /** 首页概览 */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        LoginUser user = UserContextHolder.get();
        return R.ok(supervisorVisitService.overview(user.getStoreId(), user.getOpenid(), user.getRole()));
    }

    /** 拜访单详情（仅本门店查看） */
    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        LoginUser user = UserContextHolder.get();
        return R.ok(supervisorVisitService.getDetail(id, user.getStoreId()));
    }

    /** 确认拜访单 */
    @OpLog(module = "小程序-督导拜访", operation = "确认拜访单")
    @PostMapping("/{id}/confirm")
    public R<SupervisorVisit> confirm(@PathVariable Long id) {
        LoginUser user = UserContextHolder.get();
        if (!user.isStoreManagerOrOwner()) {
            throw new BusinessException(403, "仅店长或老板可确认拜访单");
        }
        return R.ok(supervisorVisitService.confirm(id, user.getOpenid()));
    }

    /** 提出异议 */
    @OpLog(module = "小程序-督导拜访", operation = "提出异议")
    @PostMapping("/{id}/object")
    public R<SupervisorVisit> object(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LoginUser user = UserContextHolder.get();
        if (!user.isStoreManagerOrOwner()) {
            throw new BusinessException(403, "仅店长或老板可提出异议");
        }
        String reason = body != null ? body.get("reason") : "";
        return R.ok(supervisorVisitService.object(id, user.getOpenid(), reason));
    }

    /** 我的拜访任务列表 */
    @GetMapping("/actions")
    public R<Page<SupervisorVisitAction>> myActions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        LoginUser user = UserContextHolder.get();
        return R.ok(supervisorVisitService.myActions(
                user.getStoreId(), user.getOpenid(), status, pageNum, pageSize));
    }

    /** 拜访任务详情 */
    @GetMapping("/actions/{actionId}")
    public R<SupervisorVisitAction> actionDetail(@PathVariable Long actionId) {
        return R.ok(supervisorVisitService.getActionDetail(actionId));
    }

    /** 提交完成反馈（仅店长/老板） */
    @OpLog(module = "小程序-督导拜访", operation = "提交完成反馈")
    @PostMapping("/actions/{actionId}/complete")
    public R<Void> completeAction(@PathVariable Long actionId, @RequestBody Map<String, String> body) {
        LoginUser user = UserContextHolder.get();
        if (!user.isStoreManagerOrOwner()) {
            throw new BusinessException(403, "仅店长或老板可提交完成反馈");
        }
        String note = body != null ? body.getOrDefault("note", "") : "";
        String images = body != null ? body.getOrDefault("images", "") : "";
        supervisorVisitService.completeAction(actionId, user.getOpenid(), note, images);
        return R.ok();
    }
}
