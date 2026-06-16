package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.entity.ExpenseType;
import com.xzcpc.mp.dto.MpExpenseSaveReq;

import java.util.List;

public interface MpExpenseService {

    List<ExpenseType> listTypes();

    Page<ExpenseRecord> page(String storeId, String typeId, String startDate, String endDate,
                             int pageNum, int pageSize);

    ExpenseRecord detail(String storeId, String expenseId);

    ExpenseRecord create(String storeId, String storeName, MpExpenseSaveReq req);

    ExpenseRecord update(String storeId, String expenseId, MpExpenseSaveReq req);

    void delete(String storeId, String expenseId);
}
