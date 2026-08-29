package com.xzcpc.mp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.SmartOrderAddItemReq;
import com.xzcpc.mp.dto.SmartOrderConfirmReq;
import com.xzcpc.mp.entity.SmartOrder;
import com.xzcpc.mp.entity.SmartOrderItem;
import com.xzcpc.mp.service.SmartOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * P1: 智能订货（店长/老板可用，员工一期不开放）。
 * 范围规则：list/overview 支持 ?all=true 跨店视图；确认动作始终使用单据所属门店上下文。
 */
@RestController
@RequestMapping("/api/mp/smart-order")
@RequiredArgsConstructor
public class SmartOrderController {

    private final SmartOrderService smartOrderService;

    /** 员工无权使用智能订货（PRD §7 一期不开放） */
    private void assertNotStaff() {
        var user = UserContextHolder.get();
        if (user != null && user.isStaffOnly()) {
            throw new BusinessException(403, "员工无权使用智能订货");
        }
    }

    @OpLog(module = "小程序-智能订货", operation = "手动生成建议订货单")
    @PostMapping("/generate")
    public R<Map<String, Object>> generate() {
        assertNotStaff();
        return R.ok(smartOrderService.generateAll());
    }

    /** 按已提交周盘任务生成（周盘提交后补触发同款逻辑）：以任务的门店和物料池为准，不依赖登录用户绑定门店 */
    @OpLog(module = "小程序-智能订货", operation = "按任务生成建议订货单")
    @PostMapping("/generate-by-task/{taskId}")
    public R<Boolean> generateByTask(@PathVariable Integer taskId) {
        assertNotStaff();
        return R.ok(smartOrderService.generateByWeeklyTask(taskId));
    }

    /** P2B 回测：以历史周为基准模拟生成（不落库），与窗口内实际消耗对比评估预测准确性（仅测试/分析用） */
    @PostMapping("/backtest")
    public R<Map<String, Object>> backtest(@RequestParam Integer taskId,
                                           @RequestParam String weekStart) {
        assertNotStaff();
        return R.ok(smartOrderService.backtest(taskId, LocalDate.parse(weekStart)));
    }

    @GetMapping("/list")
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "false") boolean all) {
        assertNotStaff();
        var user = UserContextHolder.get();
        Page<SmartOrder> page;
        if (all) {
            page = smartOrderService.pageByStores(user.getOpenid(), pageNum, pageSize);
        } else {
            page = smartOrderService.pageByStore(user.getStoreId(), pageNum, pageSize);
        }
        return R.ok(Map.of("records", page.getRecords(), "total", page.getTotal(),
                "current", page.getCurrent(), "size", page.getSize()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        assertNotStaff();
        return R.ok(smartOrderService.detail(id));
    }

    @GetMapping("/materials/search")
    public R<Map<String, Object>> searchMaterials(@RequestParam(defaultValue = "") String keyword,
                                                  @RequestParam(defaultValue = "") String category) {
        assertNotStaff();
        return R.ok(Map.of("list", smartOrderService.searchMaterials(keyword, category)));
    }

    @GetMapping("/materials/categories")
    public R<Map<String, Object>> materialCategories() {
        assertNotStaff();
        return R.ok(Map.of("list", smartOrderService.materialCategories()));
    }

    @OpLog(module = "小程序-智能订货", operation = "手动新增物料")
    @PostMapping("/{id}/items")
    public R<SmartOrderItem> addItem(@PathVariable Long id, @Valid @RequestBody SmartOrderAddItemReq req) {
        assertNotStaff();
        return R.ok(smartOrderService.addItem(id, req));
    }

    @OpLog(module = "小程序-智能订货", operation = "删除物料")
    @DeleteMapping("/{id}/items/{itemId}")
    public R<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        assertNotStaff();
        smartOrderService.deleteItem(id, itemId);
        return R.ok();
    }

    @OpLog(module = "小程序-智能订货", operation = "确认订货")
    @PutMapping("/{id}/confirm")
    public R<SmartOrder> confirm(@PathVariable Long id, @Valid @RequestBody SmartOrderConfirmReq req) {
        assertNotStaff();
        return R.ok(smartOrderService.confirm(id, req));
    }

    @GetMapping("/overview")
    public R<?> overview(@RequestParam(defaultValue = "false") boolean all) {
        assertNotStaff();
        var user = UserContextHolder.get();
        if (all) {
            return R.ok(smartOrderService.overviewByStores(user.getOpenid()));
        }
        return R.ok(smartOrderService.overview(user.getStoreId()));
    }

    @GetMapping("/overview-total")
    public R<Map<String, Long>> overviewTotal() {
        assertNotStaff();
        var user = UserContextHolder.get();
        return R.ok(smartOrderService.overviewTotal(user.getOpenid()));
    }
}
