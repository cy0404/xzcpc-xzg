package com.xzcpc.expense.service;

import com.xzcpc.expense.entity.ExpenseType;

import java.util.List;

public interface ExpenseTypeService {
    /** 查询全部分类列表 */
    List<ExpenseType> list(String status);

    /** 新增分类 */
    ExpenseType create(String firstTypeName, String name, String description, String status);

    /** 编辑分类 */
    ExpenseType update(String typeId, String firstTypeName, String name, String description, String status);

    /** 删除分类 */
    void delete(String typeId);
}
