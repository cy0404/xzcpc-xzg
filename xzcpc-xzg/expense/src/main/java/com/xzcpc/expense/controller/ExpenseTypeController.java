package com.xzcpc.expense.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.expense.entity.ExpenseItem;
import com.xzcpc.expense.entity.ExpenseType;
import com.xzcpc.expense.service.ExpenseItemService;
import com.xzcpc.expense.service.ExpenseTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expense-types")
@RequiredArgsConstructor
public class ExpenseTypeController {

    private final ExpenseTypeService expenseTypeService;
    private final ExpenseItemService itemService;

    @GetMapping
    public R<List<ExpenseType>> list(@RequestParam(defaultValue = "") String status) {
        return R.ok(expenseTypeService.list(status));
    }

    // ====== 二级项目管理 ======

    @GetMapping("/items")
    public R<List<ExpenseItem>> listItems(@RequestParam(defaultValue = "") String typeId) {
        if (typeId != null && !typeId.isBlank()) {
            return R.ok(itemService.listByTypeId(typeId));
        }
        return R.ok(itemService.listAll());
    }

    @PostMapping("/items")
    public R<ExpenseItem> createItem(@RequestBody Map<String, String> body) {
        return R.ok(itemService.createItem(
                body.get("typeId"), body.get("name"),
                body.get("description"), body.get("status")));
    }

    @PutMapping("/items/{itemId}")
    public R<ExpenseItem> updateItem(@PathVariable String itemId, @RequestBody Map<String, String> body) {
        return R.ok(itemService.updateItem(
                itemId, body.get("typeId"), body.get("name"),
                body.get("description"), body.get("status")));
    }

    @DeleteMapping("/items/{itemId}")
    public R<Void> deleteItem(@PathVariable String itemId) {
        itemService.deleteItem(itemId);
        return R.ok();
    }
}
