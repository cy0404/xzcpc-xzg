package com.xzcpc.expense.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.expense.dto.ExpenseDashboardResp;
import com.xzcpc.expense.entity.ExpenseRecord;

public interface ExpenseService {

    Page<ExpenseRecord> page(String storeId, String typeId, String startDate, String endDate,
                             String handlerName, int pageNum, int pageSize);

    ExpenseDashboardResp dashboard(String range);
}
