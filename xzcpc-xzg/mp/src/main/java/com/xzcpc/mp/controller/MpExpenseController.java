package com.xzcpc.mp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.model.MaterialInfo;
import com.xzcpc.common.response.R;
import com.xzcpc.expense.entity.ExpenseItem;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.entity.ExpenseType;
import com.xzcpc.common.model.MaterialInfo;
import com.xzcpc.expense.service.ExpenseItemService;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.ExpenseItemVO;
import com.xzcpc.mp.dto.MpExpenseSaveReq;
import com.xzcpc.mp.service.MpExpenseService;
import com.xzcpc.template.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mp")
@RequiredArgsConstructor
public class MpExpenseController {

    private final MpExpenseService expenseService;
    private final ExpenseItemService itemService;
    private final Cache<String, List<ExpenseItem>> itemCache;
    private final MaterialService materialService;
    private final Cache<String, List<MaterialInfo>> materialByCategoryCache;

    @OpLog(module = "小程序-物料", operation = "按分类搜索")
    @GetMapping("/materials/by-category")
    public R<List<MaterialInfo>> listMaterialsByCategory(@RequestParam String parentCategory,
                                                          @RequestParam(defaultValue = "") String keyword) {
        String cacheKey = parentCategory + ":" + keyword;
        return R.ok(materialByCategoryCache.get(cacheKey,
                k -> materialService.searchByParentCategory(keyword, parentCategory)));
    }

    @OpLog(module = "小程序-支出", operation = "查询类型")
    @GetMapping("/expense-types")
    public R<List<ExpenseType>> listTypes() {
        return R.ok(expenseService.listTypes());
    }

    @OpLog(module = "小程序-支出", operation = "查询项目列表")
    @GetMapping("/expense-items")
    public R<List<ExpenseItem>> listItems() {
        return R.ok(itemCache.get("enabled", key -> itemService.listEnabled()));
    }

    @GetMapping("/expenses")
    public R<Page<ExpenseRecord>> list(@RequestParam(defaultValue = "") String typeId,
                                       @RequestParam(defaultValue = "") String startDate,
                                       @RequestParam(defaultValue = "") String endDate,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        LoginUser user = UserContextHolder.get();
        String handlerName = user.isStaffOnly() ? user.getEmployeeName() : null;
        return R.ok(expenseService.page(user.getStoreId(), typeId, startDate, endDate, pageNum, pageSize, handlerName));
    }

    @OpLog(module = "小程序-支出", operation = "查询详情")
    @GetMapping("/expenses/{expenseId}")
    public R<ExpenseRecord> detail(@PathVariable String expenseId) {
        LoginUser user = UserContextHolder.get();
        String handlerName = user.isStaffOnly() ? user.getEmployeeName() : null;
        return R.ok(expenseService.detail(user.getStoreId(), expenseId, handlerName));
    }

    @OpLog(module = "小程序-支出", operation = "新增记录")
    @PostMapping("/expenses")
    public R<ExpenseRecord> create(@Valid @RequestBody MpExpenseSaveReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(expenseService.create(user.getStoreId(), user.getStoreName(), req));
    }

    @OpLog(module = "小程序-支出", operation = "修改记录")
    @PutMapping("/expenses/{expenseId}")
    public R<ExpenseRecord> update(@PathVariable String expenseId, @Valid @RequestBody MpExpenseSaveReq req) {
        LoginUser user = UserContextHolder.get();
        String handlerName = user.isStaffOnly() ? user.getEmployeeName() : null;
        return R.ok(expenseService.update(user.getStoreId(), expenseId, req, handlerName));
    }

    @GetMapping("/expenses/{expenseId}/items")
    public R<List<ExpenseItemVO>> getItems(@PathVariable String expenseId) {
        LoginUser user = UserContextHolder.get();
        return R.ok(expenseService.listItems(user.getStoreId(), expenseId));
    }

    @OpLog(module = "小程序-支出", operation = "删除记录")
    @DeleteMapping("/expenses/{expenseId}")
    public R<Void> delete(@PathVariable String expenseId) {
        LoginUser user = UserContextHolder.get();
        String handlerName = user.isStaffOnly() ? user.getEmployeeName() : null;
        expenseService.delete(user.getStoreId(), expenseId, handlerName);
        return R.ok();
    }
}
