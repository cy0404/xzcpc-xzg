package com.xzcpc.template.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.template.entity.Template;
import com.xzcpc.template.entity.TemplateZone;
import com.xzcpc.template.entity.TemplateZoneMaterial;

import java.util.List;

// 模板业务接口，定义模板/分区/物料关系的操作方法
public interface TemplateService {

    // 分页查询模板，支持名称模糊搜索
    Page<Template> page(String keyword, int pageNum, int pageSize);

    // 新增模板
    void add(Template template);

    // 更新模板
    void update(Template template);

    // 设置模板状态（1启用 0停用 2草稿）
    void setStatus(Integer id, Integer status);

    // 删除模板及其分区和物料关系
    void delete(Integer id);

    // 查询模板详情
    Template detail(Integer id);

    // 获取模板下的所有分区
    List<TemplateZone> zones(Integer templateId);

    // 新增分区
    void addZone(TemplateZone zone);

    // 更新分区
    void updateZone(Integer templateId, Integer zoneId, TemplateZone zone);

    // 删除分区及其物料关系
    void deleteZone(Integer templateId, Integer zoneId);

    // 为分区添加物料
    void addZoneMaterial(Integer templateId, Integer zoneId, TemplateZoneMaterial relation);

    // 移除分区中的物料
    void deleteZoneMaterial(Integer templateId, Integer zoneId, String materialId);

    // 获取分区下的物料列表
    List<TemplateZoneMaterial> getZoneMaterials(Integer templateId, Integer zoneId);

    // 拖拽排序分区
    void updateZoneSort(Integer templateId, List<TemplateZone> zones);

    // 拖拽排序分区内物料
    void updateZoneMaterialSort(Integer templateId, Integer zoneId, List<TemplateZoneMaterial> materials);
}
