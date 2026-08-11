package com.xzcpc.mp.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.BusinessOverviewResp;
import com.xzcpc.mp.dto.BusinessTrendResp;
import com.xzcpc.mp.dto.BusinessReportSummaryResp;
import com.xzcpc.mp.dto.BusinessReportDetailResp;
import com.xzcpc.mp.dto.HomeOverviewResp;
import com.xzcpc.mp.service.MpBusinessService;
import com.xzcpc.mp.service.impl.MpBusinessServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mp/business")
@RequiredArgsConstructor
public class MpBusinessController {

    private final MpBusinessService businessService;
    private final MpBusinessServiceImpl businessServiceImpl;

    @GetMapping("/overview")
    public R<BusinessOverviewResp> overview(
            @RequestParam(defaultValue = "30") String period,
            @RequestParam(defaultValue = "all") String scope) {
        LoginUser user = UserContextHolder.get();
        if (!canAccess(user)) return R.fail(403, "无权限查看经营数据");
        return R.ok(businessService.getOverview(resolveStoreIds(user, scope), period));
    }

    @GetMapping("/trend")
    public R<BusinessTrendResp> trend(
            @RequestParam(defaultValue = "7") String period,
            @RequestParam(defaultValue = "sales") String metric,
            @RequestParam(defaultValue = "all") String scope) {
        LoginUser user = UserContextHolder.get();
        if (!canAccess(user)) return R.fail(403, "无权限查看经营数据");
        return R.ok(businessService.getTrend(resolveStoreIds(user, scope), period, metric));
    }

    @GetMapping("/yesterday")
    public R<HomeOverviewResp> yesterday(
            @RequestParam(defaultValue = "all") String scope) {
        LoginUser user = UserContextHolder.get();
        if (!canAccess(user)) return R.fail(403, "无权限查看经营数据");
        return R.ok(businessService.getYesterday(resolveStoreIds(user, scope)));
    }

    @GetMapping("/reports")
    public R<List<BusinessReportSummaryResp>> reports(
            @RequestParam(defaultValue = "all") String scope) {
        LoginUser user = UserContextHolder.get();
        if (!canAccess(user)) return R.fail(403, "无权限查看经营数据");
        if (!isOwner(user)) return R.ok(List.of()); // 仅老板可见财报列表
        return R.ok(businessService.getReports(resolveStoreIds(user, scope)));
    }

    @GetMapping("/reports/{yearMonth}")
    public R<BusinessReportDetailResp> reportDetail(
            @PathVariable String yearMonth,
            @RequestParam(required = false) String storeId) {
        LoginUser user = UserContextHolder.get();
        if (!canAccess(user)) return R.fail(403, "无权限查看经营数据");
        if (!isOwner(user)) return R.fail(403, "仅老板可查看财报详情");
        String targetStoreId = (storeId != null && !storeId.isEmpty()) ? storeId : user.getStoreId();
        BusinessReportDetailResp detail = businessService.getReportDetail(targetStoreId, yearMonth);
        if (detail == null) return R.fail(404, "未找到该月财报数据");
        return R.ok(detail);
    }

    @GetMapping("/reports/{yearMonth}/cost-breakdown")
    public R<Map<String, Object>> costBreakdown(
            @PathVariable String yearMonth,
            @RequestParam String storeId,
            @RequestParam(defaultValue = "operation") String group,
            @RequestParam(required = false) String category) {
        LoginUser user = UserContextHolder.get();
        if (!canAccess(user)) return R.fail(403, "无权限查看经营数据");
        if (!isOwner(user)) return R.fail(403, "仅老板可查看成本下钻");
        Map<String, Object> data = businessService.getCostBreakdown(storeId, yearMonth, group, category);
        if (data == null) return R.fail(404, "未找到成本下钻数据");
        return R.ok(data);
    }

    private boolean canAccess(LoginUser user) {
        String role = user.getRole();
        return "owner".equals(role) || "老板".equals(role)
            || "store_manager".equals(role) || "店长".equals(role);
    }
    private boolean isOwner(LoginUser user) {
        String role = user.getRole();
        return "owner".equals(role) || "老板".equals(role);
    }

    private List<String> resolveStoreIds(LoginUser user, String scope) {
        if (!"all".equals(scope)) {
            return Collections.singletonList(scope);
        }
        List<String> storeIds = businessServiceImpl.getOwnerStoreIds(user.getOpenid());
        if (storeIds.isEmpty()) {
            storeIds = Collections.singletonList(user.getStoreId());
        }
        return storeIds;
    }
}
