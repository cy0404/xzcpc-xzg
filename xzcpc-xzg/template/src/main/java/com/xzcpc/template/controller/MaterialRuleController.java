package com.xzcpc.template.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.template.dto.MaterialRuleResp;
import com.xzcpc.template.dto.MaterialRuleSaveReq;
import com.xzcpc.template.service.MaterialRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/material-rules")
@RequiredArgsConstructor
public class MaterialRuleController {

    private final MaterialRuleService materialRuleService;

    @OpLog(module = "物料盘点规则", operation = "查询列表")
    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "") String parentCategory,
                                       @RequestParam(defaultValue = "") String category,
                                       @RequestParam(defaultValue = "") String baseUnit,
                                       @RequestParam(defaultValue = "") String conversionType,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(materialRuleService.page(keyword, parentCategory, category, baseUnit, conversionType, pageNum, pageSize));
    }

    @OpLog(module = "物料盘点规则", operation = "查询详情")
    @GetMapping("/{materialId}")
    public R<MaterialRuleResp> detail(@PathVariable String materialId) {
        return R.ok(materialRuleService.detail(materialId));
    }

    @OpLog(module = "物料盘点规则", operation = "保存")
    @PutMapping("/{materialId}")
    public R<MaterialRuleResp> save(@PathVariable String materialId,
                                    @RequestBody MaterialRuleSaveReq req) {
        return R.ok(materialRuleService.save(materialId, req));
    }

    @OpLog(module = "物料盘点规则", operation = "查询全部基础单位")
    @GetMapping("/base-units")
    public R<List<String>> baseUnits() {
        return R.ok(materialRuleService.getAllBaseUnits());
    }
}
