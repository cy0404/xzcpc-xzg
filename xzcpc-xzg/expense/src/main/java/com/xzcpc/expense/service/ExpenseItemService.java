package com.xzcpc.expense.service;

import com.xzcpc.expense.entity.ExpenseItem;

import java.util.List;

public interface ExpenseItemService {
    List<ExpenseItem> listByTypeId(String typeId);
    List<ExpenseItem> listAll();
    /** 只返回启用的项目（小程序端用） */
    List<ExpenseItem> listEnabled();
    ExpenseItem createItem(String typeId, String name, String description, String status);
    ExpenseItem updateItem(String itemId, String typeId, String name, String description, String status);
    void deleteItem(String itemId);
    void deleteByTypeId(String typeId);
}
