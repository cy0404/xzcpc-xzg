package com.xzcpc.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.template.entity.Template;
import com.xzcpc.template.entity.TemplateZone;
import com.xzcpc.template.entity.TemplateZoneMaterial;
import com.xzcpc.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// 模板管理控制器，提供模板/分区/物料关系的 RESTful API
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @OpLog(module = "模板", operation = "查询列表")
    @GetMapping
    public R<Page<Template>> list(@RequestParam(defaultValue = "") String keyword,
                                   @RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(templateService.page(keyword, pageNum, pageSize));
    }

    @OpLog(module = "模板", operation = "新增")
    @PostMapping
    public R<Void> add(@RequestBody Template template) {
        templateService.add(template);
        return R.ok();
    }

    @OpLog(module = "模板", operation = "编辑")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Integer id, @RequestBody Template template) {
        template.setId(id);
        templateService.update(template);
        return R.ok();
    }

    @OpLog(module = "模板", operation = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Integer id) {
        templateService.delete(id);
        return R.ok();
    }

    @OpLog(module = "模板", operation = "启停")
    @PatchMapping("/{id}/status")
    public R<Void> setStatus(@PathVariable Integer id, @RequestBody Template template) {
        templateService.setStatus(id, template.getStatus());
        return R.ok();
    }

    @OpLog(module = "模板", operation = "查询详情")
    @GetMapping("/{id}")
    public R<Template> detail(@PathVariable Integer id) {
        return R.ok(templateService.detail(id));
    }

    @OpLog(module = "模板分区", operation = "查询")
    @GetMapping("/{id}/zones")
    // 获取模板下的所有分区
    public R<List<TemplateZone>> zones(@PathVariable Integer id) {
        return R.ok(templateService.zones(id));
    }

    @OpLog(module = "模板分区", operation = "新增")
    @PostMapping("/{id}/zones")
    // 新增分区
    public R<TemplateZone> addZone(@PathVariable Integer id, @RequestBody TemplateZone zone) {
        zone.setTemplateId(id);
        templateService.addZone(zone);
        return R.ok(zone);
    }

    @OpLog(module = "模板分区", operation = "编辑")
    @PutMapping("/{id}/zones/{zoneId}")
    // 更新分区
    public R<Void> updateZone(@PathVariable Integer id,
                               @PathVariable Integer zoneId,
                               @RequestBody TemplateZone zone) {
        templateService.updateZone(id, zoneId, zone);
        return R.ok();
    }

    @OpLog(module = "模板分区", operation = "删除")
    @DeleteMapping("/{id}/zones/{zoneId}")
    // 删除分区
    public R<Void> deleteZone(@PathVariable Integer id, @PathVariable Integer zoneId) {
        templateService.deleteZone(id, zoneId);
        return R.ok();
    }

    @OpLog(module = "模板分区物料", operation = "查询")
    @GetMapping("/{id}/zones/{zoneId}/materials")
    // 获取分区下的物料列表（直接读取存储的快照字段）
    public R<List<Map<String, Object>>> zoneMaterials(@PathVariable Integer id,
                                                       @PathVariable Integer zoneId) {
        List<TemplateZoneMaterial> relations = templateService.getZoneMaterials(id, zoneId);
        List<Map<String, Object>> result = relations.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("materialId", r.getMaterialId());
            map.put("materialName", r.getMaterialName());
            map.put("spec", r.getSpec());
            map.put("inventoryUnit", r.getInventoryUnit());
            map.put("sortNo", r.getSortNo());
            return map;
        }).collect(Collectors.toList());
        return R.ok(result);
    }

    @OpLog(module = "模板分区物料", operation = "添加")
    @PostMapping("/{id}/zones/{zoneId}/materials")
    // 为分区添加物料
    public R<Void> addZoneMaterial(@PathVariable Integer id,
                                    @PathVariable Integer zoneId,
                                    @RequestBody TemplateZoneMaterial relation) {
        templateService.addZoneMaterial(id, zoneId, relation);
        return R.ok();
    }

    @OpLog(module = "模板分区物料", operation = "移除")
    @DeleteMapping("/{id}/zones/{zoneId}/materials/{materialId}")
    // 从分区移除物料
    public R<Void> deleteZoneMaterial(@PathVariable Integer id,
                                       @PathVariable Integer zoneId,
                                       @PathVariable String materialId) {
        log.info("删除模板分区物料: templateId={}, zoneId={}, materialId={}", id, zoneId, materialId);
        templateService.deleteZoneMaterial(id, zoneId, materialId);
        return R.ok();
    }

    @OpLog(module = "模板分区", operation = "排序")
    @PutMapping("/{id}/zones/sort")
    // 拖拽排序分区
    public R<Void> updateZoneSort(@PathVariable Integer id,
                                   @RequestBody List<TemplateZone> zones) {
        templateService.updateZoneSort(id, zones);
        return R.ok();
    }

    @OpLog(module = "模板分区物料", operation = "排序")
    @PutMapping("/{id}/zones/{zoneId}/materials/sort")
    // 拖拽排序分区内物料
    public R<Void> updateZoneMaterialSort(@PathVariable Integer id,
                                           @PathVariable Integer zoneId,
                                           @RequestBody List<TemplateZoneMaterial> materials) {
        templateService.updateZoneMaterialSort(id, zoneId, materials);
        return R.ok();
    }
}
