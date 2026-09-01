package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.entity.ExpenseType;
import com.xzcpc.mp.dto.ExpenseItemVO;
import com.xzcpc.mp.dto.MpExpenseBatchSaveReq;
import com.xzcpc.mp.dto.MpExpenseSaveReq;

import java.util.List;

public interface MpExpenseService {

    List<ExpenseType> listTypes();

    Page<ExpenseRecord> page(String storeId, String typeId, String startDate, String endDate,
                             int pageNum, int pageSize, String handlerName);

    ExpenseRecord detail(String storeId, String expenseId, String handlerName);

    List<ExpenseItemVO> listItems(String storeId, String expenseId);

    ExpenseRecord create(String storeId, String storeName, MpExpenseSaveReq req);

    /** 多类型组批量登记：一个表单一次提交多个支出类型（事务，凭证复制到每条记录） */
    List<ExpenseRecord> createBatch(String storeId, String storeName, MpExpenseBatchSaveReq req);

    ExpenseRecord update(String storeId, String expenseId, MpExpenseSaveReq req, String handlerName);

    void delete(String storeId, String expenseId, String handlerName);
}
