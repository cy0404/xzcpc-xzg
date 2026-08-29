package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.entity.IssueFeedback;
import com.xzcpc.mp.service.MpFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * P2: 小程序客诉处理 — 列表（本店）、详情、标记已处理（说明+凭证，仅内部可见）。
 * 仅店长/老板可操作（顾客 H5 提交入口在总部端 /api/xzg/feedback）。
 */
@Slf4j
@RestController
@RequestMapping("/api/mp/feedback")
@RequiredArgsConstructor
public class MpFeedbackController {

    private final MpFeedbackService mpFeedbackService;

    /** 本店客诉列表（状态可选：pending/processing/done/closed，默认全部） */
    @GetMapping("/list")
    public R<List<IssueFeedback>> list(@RequestParam(required = false) String status) {
        LoginUser user = UserContextHolder.get();
        return R.ok(mpFeedbackService.listByStore(user.getStoreId(), status));
    }

    /** 各店未处理客诉数（首页全部门店视图用：pending/processing > 0 的门店） */
    @GetMapping("/overview-stores")
    public R<List<Map<String, Object>>> overviewByStores() {
        String openid = UserContextHolder.get().getOpenid();
        return R.ok(mpFeedbackService.overviewByStores(openid));
    }

    /** 未处理客诉数：all=true 返回跨店总数，否则返回当前门店数（首页卡片显隐用） */
    @GetMapping("/overview")
    public R<Map<String, Long>> overview(@RequestParam(defaultValue = "false") boolean all) {
        LoginUser user = UserContextHolder.get();
        if (all) {
            return R.ok(mpFeedbackService.overviewTotal(user.getOpenid()));
        }
        return R.ok(mpFeedbackService.overview(user.getStoreId()));
    }

    /** 客诉详情（仅本门店） */
    @GetMapping("/{id}")
    public R<IssueFeedback> detail(@PathVariable Long id) {
        LoginUser user = UserContextHolder.get();
        return R.ok(mpFeedbackService.detail(user.getStoreId(), id));
    }

    /** 标记已处理：处理说明 + 凭证图片/视频（仅店长/老板） */
    @OpLog(module = "小程序-客诉处理", operation = "标记客诉已处理")
    @PostMapping("/{id}/done")
    public R<IssueFeedback> done(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LoginUser user = UserContextHolder.get();
        if (!user.isStoreManagerOrOwner()) {
            throw new BusinessException(403, "仅店长或老板可处理客诉");
        }
        String note = body != null ? body.getOrDefault("processNote", "") : "";
        String evidence = body != null ? body.getOrDefault("evidence", "") : "";
        return R.ok(mpFeedbackService.markDone(user.getStoreId(), id,
                user.getOpenid(), user.getEmployeeName(), note, evidence));
    }
}
