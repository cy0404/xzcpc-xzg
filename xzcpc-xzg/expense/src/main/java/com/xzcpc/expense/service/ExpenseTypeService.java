package com.xzcpc.expense.service;

import com.xzcpc.expense.entity.ExpenseType;

import java.util.List;

public interface ExpenseTypeService {
    /** 查询一级分类列表（只读，数据库预设） */
    List<ExpenseType> list(String status);
}
