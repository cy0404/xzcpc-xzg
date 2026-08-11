package com.xzcpc.expense.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.response.R;
import com.xzcpc.expense.entity.SelfPurchaseMaterial;
import com.xzcpc.expense.service.SelfPurchaseMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/self-purchase-materials")
@RequiredArgsConstructor
public class SelfPurchaseMaterialController {

    private final SelfPurchaseMaterialService service;

    @GetMapping
    public R<Page<SelfPurchaseMaterial>> page(
            @RequestParam(defaultValue = "") String storeIds,
            @RequestParam(defaultValue = "") String supervisorName,
            @RequestParam(defaultValue = "") String startDate,
            @RequestParam(defaultValue = "") String endDate,
            @RequestParam(defaultValue = "") String handlerName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(storeIds, supervisorName, startDate, endDate, handlerName, pageNum, pageSize));
    }
}
