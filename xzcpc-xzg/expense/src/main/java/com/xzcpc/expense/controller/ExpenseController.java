package com.xzcpc.expense.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.expense.dto.ExpenseDashboardResp;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @OpLog(module = "支出", operation = "查询明细")
    @GetMapping
    public R<Page<ExpenseRecord>> list(@RequestParam(defaultValue = "") String storeId,
                                       @RequestParam(defaultValue = "") String typeId,
                                       @RequestParam(defaultValue = "") String startDate,
                                       @RequestParam(defaultValue = "") String endDate,
                                       @RequestParam(defaultValue = "") String handlerName,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(expenseService.page(storeId, typeId, startDate, endDate, handlerName, pageNum, pageSize));
    }

    @OpLog(module = "支出", operation = "查看统计")
    @GetMapping("/dashboard")
    public R<ExpenseDashboardResp> dashboard(@RequestParam(defaultValue = "month") String range) {
        return R.ok(expenseService.dashboard(range));
    }
}
