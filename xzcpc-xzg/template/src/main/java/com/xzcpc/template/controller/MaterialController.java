package com.xzcpc.template.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @OpLog(module = "物料", operation = "分页查询")
    @GetMapping
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(materialService.page(keyword, pageNum, pageSize));
    }

    @OpLog(module = "物料", operation = "查询详情")
    @GetMapping("/{materialId}")
    public R<Material> detail(@PathVariable String materialId) {
        return R.ok(materialService.getByMaterialId(materialId));
    }

    @OpLog(module = "物料", operation = "新增")
    @PostMapping
    public R<Material> create(@RequestBody Material material) {
        return R.ok(materialService.create(material));
    }

    @OpLog(module = "物料", operation = "编辑")
    @PutMapping("/{materialId}")
    public R<Material> update(@PathVariable String materialId, @RequestBody Material material) {
        return R.ok(materialService.update(materialId, material));
    }

    @OpLog(module = "物料", operation = "启停")
    @PatchMapping("/{materialId}/status")
    public R<Void> toggleStatus(@PathVariable String materialId) {
        materialService.toggleStatus(materialId);
        return R.ok();
    }

    @OpLog(module = "物料", operation = "删除")
    @DeleteMapping("/{materialId}")
    public R<Void> delete(@PathVariable String materialId) {
        materialService.deleteByMaterialId(materialId);
        return R.ok();
    }

    @OpLog(module = "物料", operation = "手动同步物料")
    @PostMapping("/sync")
    public R<Map<String, Object>> sync() {
        int count = materialService.syncFromApi();
        return R.ok(Map.of("count", count, "message", "同步完成，共 " + count + " 条"));
    }

    @OpLog(module = "物料", operation = "迁移盘点单位")
    @PostMapping("/migrate-inventory-units")
    public R<Map<String, Object>> migrateInventoryUnits() {
        int count = materialService.migrateInventoryUnits();
        return R.ok(Map.of("count", count, "message", "迁移完成，共 " + count + " 条"));
    }

    @OpLog(module = "物料", operation = "查询全部分类")
    @GetMapping("/categories")
    public R<List<String>> categories() {
        return R.ok(materialService.getAllCategories());
    }

    @OpLog(module = "物料", operation = "查询全部父级分类")
    @GetMapping("/parent-categories")
    public R<List<String>> parentCategories() {
        return R.ok(materialService.getAllParentCategories());
    }
}
