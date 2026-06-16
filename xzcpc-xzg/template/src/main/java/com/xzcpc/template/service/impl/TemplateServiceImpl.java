package com.xzcpc.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.event.TemplateChangedEvent;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.template.entity.Template;
import com.xzcpc.template.entity.TemplateZone;
import com.xzcpc.template.entity.TemplateZoneMaterial;
import com.xzcpc.template.mapper.TemplateMapper;
import com.xzcpc.template.mapper.TemplateZoneMapper;
import com.xzcpc.template.mapper.TemplateZoneMaterialMapper;
import com.xzcpc.template.service.MaterialService;
import com.xzcpc.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

// 模板业务实现类
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final TemplateMapper templateMapper;
    private final TemplateZoneMapper templateZoneMapper;
    private final TemplateZoneMaterialMapper templateZoneMaterialMapper;
    private final MaterialService materialService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    // 分页查询模板，按创建时间倒序
    public Page<Template> page(String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Template::getTemplateName, keyword);
        }
        wrapper.orderByDesc(Template::getCreatedAt);
        return templateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    // 新增模板，校验名称非空且不重复
    public void add(Template template) {
        if (!StringUtils.hasText(template.getTemplateName())) {
            throw new BusinessException("模板名称不能为空");
        }
        Long count = templateMapper.selectCount(
                new LambdaQueryWrapper<Template>()
                        .eq(Template::getTemplateName, template.getTemplateName()));
        if (count > 0) {
            throw new BusinessException("模板名称已存在");
        }
        template.setStatus(2);
        templateMapper.insert(template);
        // 迁移兼容：同步旧主键为 id 值
        template.setTemplateId(template.getId());
        template.setBizCode("TPL" + String.format("%08d", template.getId()));
        templateMapper.updateById(template);
    }

    @Override
    // 更新模板，校验存在性和名称唯一性
    public void update(Template template) {
        Template exist = templateMapper.selectById(template.getId());
        if (exist == null) {
            throw new BusinessException("模板不存在");
        }
        if (template.getTemplateId() == null) {
            template.setTemplateId(exist.getTemplateId());
        }
        if (template.getBizCode() == null) {
            template.setBizCode(exist.getBizCode());
        }
        if (!exist.getTemplateName().equals(template.getTemplateName())) {
            Long count = templateMapper.selectCount(
                    new LambdaQueryWrapper<Template>()
                            .eq(Template::getTemplateName, template.getTemplateName()));
            if (count > 0) {
                throw new BusinessException("模板名称已存在");
            }
        }
        template.setStatus(2);
        templateMapper.updateById(template);
    }

    @Override
    @Transactional
    // 删除模板：先删所有分区的物料关系，再删分区，最后删模板
    public void delete(Integer id) {
        Template template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        List<TemplateZone> zones = templateZoneMapper.selectList(
                new LambdaQueryWrapper<TemplateZone>()
                        .eq(TemplateZone::getTemplateId, id));
        for (TemplateZone zone : zones) {
            templateZoneMaterialMapper.delete(
                    new LambdaQueryWrapper<TemplateZoneMaterial>()
                            .eq(TemplateZoneMaterial::getZoneId, zone.getZoneId()));
        }
        templateZoneMapper.delete(
                new LambdaQueryWrapper<TemplateZone>()
                        .eq(TemplateZone::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    @Override
    // 设置模板状态（1启用 0停用 2草稿）
    public void setStatus(Integer id, Integer status) {
        Template template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        template.setStatus(status);
        templateMapper.updateById(template);
    }

    @Override
    // 查询模板详情，不存在则抛异常
    public Template detail(Integer id) {
        Template template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        return template;
    }

    @Override
    // 查询指定模板的所有分区，按 sort_no 升序（null 排最后，与前端 sortedZones 一致），并填充物料数量
    public List<TemplateZone> zones(Integer templateId) {
        List<TemplateZone> zones = templateZoneMapper.selectList(
                new LambdaQueryWrapper<TemplateZone>()
                        .eq(TemplateZone::getTemplateId, templateId));
        // sort_no 可空，空值排在已有序号后面
        zones.sort((a, b) -> {
            if (a.getSortNo() != null && b.getSortNo() != null) return a.getSortNo() - b.getSortNo();
            if (a.getSortNo() != null) return -1;
            if (b.getSortNo() != null) return 1;
            return 0;
        });
        for (TemplateZone zone : zones) {
            // 迁移兼容：zoneId 同步为 id，前端统一用 zoneId 操作
            if (zone.getZoneId() == null || zone.getZoneId() == 0) {
                zone.setZoneId(zone.getId());
            }
            Long count = templateZoneMaterialMapper.selectCount(
                    new LambdaQueryWrapper<TemplateZoneMaterial>()
                            .eq(TemplateZoneMaterial::getZoneId, zone.getId()));
            zone.setMaterialCount(count != null ? count.intValue() : 0);
        }
        return zones;
    }

    @Override
    // 新增分区，校验名称非空且同一模板内不重复
    public void addZone(TemplateZone zone) {
        if (!StringUtils.hasText(zone.getZoneName())) {
            throw new BusinessException("分区名称不能为空");
        }
        Long count = templateZoneMapper.selectCount(
                new LambdaQueryWrapper<TemplateZone>()
                        .eq(TemplateZone::getTemplateId, zone.getTemplateId())
                        .eq(TemplateZone::getZoneName, zone.getZoneName()));
        if (count > 0) {
            throw new BusinessException("分区名称在该模板内已存在");
        }
        templateZoneMapper.insert(zone);
        zone.setZoneId(zone.getId());
        zone.setBizCode("TZ" + String.format("%08d", zone.getId()));
        templateZoneMapper.updateById(zone);
        eventPublisher.publishEvent(new TemplateChangedEvent(this, zone.getTemplateId()));
    }

    @Override
    // 更新分区，校验存在性和名称唯一性
    public void updateZone(Integer templateId, Integer zoneId, TemplateZone zone) {
        TemplateZone exist = templateZoneMapper.selectById(zoneId);
        if (exist == null || !exist.getTemplateId().equals(templateId)) {
            throw new BusinessException("分区不存在");
        }
        if (!exist.getZoneName().equals(zone.getZoneName())) {
            Long count = templateZoneMapper.selectCount(
                    new LambdaQueryWrapper<TemplateZone>()
                            .eq(TemplateZone::getTemplateId, templateId)
                            .eq(TemplateZone::getZoneName, zone.getZoneName()));
            if (count > 0) {
                throw new BusinessException("分区名称在该模板内已存在");
            }
        }
        zone.setId(zoneId);
        zone.setZoneId(zoneId);
        zone.setTemplateId(templateId);
        templateZoneMapper.updateById(zone);
        eventPublisher.publishEvent(new TemplateChangedEvent(this, templateId));
    }

    @Override
    @Transactional
    @CacheEvict(value = "templateZoneMaterials", key = "#root.args[1]")
    // 删除分区，同时删除该分区下的所有物料关系
    public void deleteZone(Integer templateId, Integer zoneId) {
        TemplateZone exist = templateZoneMapper.selectById(zoneId);
        if (exist == null || !exist.getTemplateId().equals(templateId)) {
            throw new BusinessException("分区不存在");
        }
        templateZoneMaterialMapper.delete(
                new LambdaQueryWrapper<TemplateZoneMaterial>()
                        .eq(TemplateZoneMaterial::getZoneId, zoneId));
        templateZoneMapper.deleteById(zoneId);
        eventPublisher.publishEvent(new TemplateChangedEvent(this, templateId));
    }

    @Override
    @CacheEvict(value = "templateZoneMaterials", key = "#root.args[1]")
    // 为分区添加物料，校验物料不重复
    public void addZoneMaterial(Integer templateId, Integer zoneId, TemplateZoneMaterial relation) {
        Long count = templateZoneMaterialMapper.selectCount(
                new LambdaQueryWrapper<TemplateZoneMaterial>()
                        .eq(TemplateZoneMaterial::getZoneId, zoneId)
                        .eq(TemplateZoneMaterial::getMaterialId, relation.getMaterialId()));
        if (count > 0) {
            throw new BusinessException("该物料已在当前分区中");
        }
        relation.setZoneId(zoneId);
        templateZoneMaterialMapper.insert(relation);
        eventPublisher.publishEvent(new TemplateChangedEvent(this, templateId));
    }

    @Override
    @CacheEvict(value = "templateZoneMaterials", key = "#root.args[1]")
    // 从分区中移除指定物料
    public void deleteZoneMaterial(Integer templateId, Integer zoneId, String materialId) {
        int rows = templateZoneMaterialMapper.delete(
                new LambdaQueryWrapper<TemplateZoneMaterial>()
                        .eq(TemplateZoneMaterial::getZoneId, zoneId)
                        .eq(TemplateZoneMaterial::getMaterialId, materialId));
        if (rows != 1) {
            log.error("删除模板分区物料异常！预期删除1条，实际删除了 {} 条 —— templateId={}, zoneId={}, materialId={}",
                    rows, templateId, zoneId, materialId);
        } else {
            log.info("删除模板分区物料成功: templateId={}, zoneId={}, materialId={}", templateId, zoneId, materialId);
        }
        eventPublisher.publishEvent(new TemplateChangedEvent(this, templateId));
    }

    @Override
    @Transactional
    // 批量更新分区排序（拖拽后）
    public void updateZoneSort(Integer templateId, List<TemplateZone> zones) {
        for (TemplateZone zone : zones) {
            templateZoneMapper.updateById(zone);
        }
        eventPublisher.publishEvent(new TemplateChangedEvent(this, templateId));
    }

    @Override
    @Transactional
    @CacheEvict(value = "templateZoneMaterials", key = "#root.args[1]")
    public void updateZoneMaterialSort(Integer templateId, Integer zoneId, List<TemplateZoneMaterial> materials) {
        for (TemplateZoneMaterial material : materials) {
            templateZoneMaterialMapper.update(null,
                    new LambdaUpdateWrapper<TemplateZoneMaterial>()
                            .eq(TemplateZoneMaterial::getZoneId, zoneId)
                            .eq(TemplateZoneMaterial::getMaterialId, material.getMaterialId())
                            .set(TemplateZoneMaterial::getSortNo, material.getSortNo()));
        }
        eventPublisher.publishEvent(new TemplateChangedEvent(this, templateId));
    }

    @Override
    @Cacheable(value = "templateZoneMaterials", key = "#root.args[1]")
    // 获取分区下的物料列表（带 Caffeine 缓存，10min TTL，写操作自动失效；用 args[1] 避免参数名丢失导致 key 为 null）
    public List<TemplateZoneMaterial> getZoneMaterials(Integer templateId, Integer zoneId) {
        List<TemplateZoneMaterial> materials = templateZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TemplateZoneMaterial>()
                        .eq(TemplateZoneMaterial::getZoneId, zoneId));
        // sort_no 可空，空值排在已有序号后面（与前端排序逻辑一致）
        materials.sort((a, b) -> {
            if (a.getSortNo() != null && b.getSortNo() != null) return a.getSortNo() - b.getSortNo();
            if (a.getSortNo() != null) return -1;
            if (b.getSortNo() != null) return 1;
            return 0;
        });
        return materials;
    }
}
