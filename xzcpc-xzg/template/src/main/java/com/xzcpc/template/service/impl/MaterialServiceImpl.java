package com.xzcpc.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.event.MaterialChangedEvent;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.MaterialInfo;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.entity.TemplateZoneMaterial;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import com.xzcpc.template.mapper.TemplateZoneMaterialMapper;
import com.xzcpc.template.service.MaterialService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final RestTemplate restTemplate;
    private final MaterialMapper materialMapper;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final TemplateZoneMaterialMapper templateZoneMaterialMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${material.api.url}")
    private String apiUrl;

    @Value("${material.api.key}")
    private String apiKey;

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    // ==================== 本地 DB 查询 ====================

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "materials", unless = "#result.isEmpty()")
    public List<MaterialInfo> getAllMaterials() {
        List<Material> materials = materialMapper.selectList(
                new LambdaQueryWrapper<Material>().orderByDesc(Material::getUpdatedAt).orderByDesc(Material::getId));
        if (materials.isEmpty() && syncEnabled()) {
            syncFromApi();
            materials = materialMapper.selectList(
                    new LambdaQueryWrapper<Material>().orderByDesc(Material::getUpdatedAt).orderByDesc(Material::getId));
        }
        return fillInventoryUnit(materials);
    }

    private List<MaterialInfo> fillInventoryUnit(List<Material> materials) {
        List<String> materialIds = materials.stream().map(Material::getMaterialId).collect(Collectors.toList());
        Map<String, String> unitMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                    .in(MaterialInventoryRule::getMaterialId, materialIds))
                    .forEach(r -> unitMap.put(r.getMaterialId(), r.getBaseUnit() != null ? r.getBaseUnit() : ""));
        }
        return materials.stream().map(m -> {
            MaterialInfo info = toInfo(m);
            info.setPandiandanwei(unitMap.getOrDefault(m.getMaterialId(), ""));
            return info;
        }).collect(Collectors.toList());
    }

    @Override
    public List<MaterialInfo> searchMaterials(String keyword) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .orderByDesc(Material::getUpdatedAt).orderByDesc(Material::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Material::getMaterialName, keyword.trim());
        }
        List<Material> materials = materialMapper.selectList(wrapper);
        if (materials.isEmpty() && !StringUtils.hasText(keyword) && syncEnabled()) {
            syncFromApi();
            materials = materialMapper.selectList(wrapper);
        }
        return fillInventoryUnit(materials);
    }

    @Override
    public MaterialInfo getByQmCode(String qmCode) {
        if (!StringUtils.hasText(qmCode)) return null;
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getQmCode, qmCode.trim()));
        if (material == null) return null;
        MaterialInfo info = toInfo(material);
        MaterialInventoryRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<MaterialInventoryRule>()
                .eq(MaterialInventoryRule::getMaterialId, material.getMaterialId()));
        if (rule != null && StringUtils.hasText(rule.getBaseUnit())) {
            info.setPandiandanwei(rule.getBaseUnit());
        }
        return info;
    }

    @Override
    public MaterialInfo getMaterialInfoById(String materialId) {
        if (!StringUtils.hasText(materialId)) return null;
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialId, materialId));
        if (material == null) return null;
        MaterialInfo info = toInfo(material);
        MaterialInventoryRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<MaterialInventoryRule>()
                .eq(MaterialInventoryRule::getMaterialId, material.getMaterialId()));
        if (rule != null && StringUtils.hasText(rule.getBaseUnit())) {
            info.setPandiandanwei(rule.getBaseUnit());
        }
        return info;
    }

    @Override
    public Map<String, Object> page(String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .orderByDesc(Material::getUpdatedAt).orderByDesc(Material::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Material::getMaterialName, keyword.trim());
        }
        Page<Material> page = materialMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 批量查询盘点单位，避免 N+1
        List<String> materialIds = page.getRecords().stream()
                .map(Material::getMaterialId).filter(StringUtils::hasText).distinct()
                .collect(Collectors.toList());
        Map<String, String> inventoryUnitMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<MaterialInventoryRule> rules = ruleMapper.selectList(
                    new LambdaQueryWrapper<MaterialInventoryRule>()
                            .in(MaterialInventoryRule::getMaterialId, materialIds));
            for (MaterialInventoryRule rule : rules) {
                if (StringUtils.hasText(rule.getBaseUnit())) {
                    inventoryUnitMap.put(rule.getMaterialId(), rule.getBaseUnit());
                }
            }
        }

        List<Map<String, Object>> records = page.getRecords().stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("materialId", m.getMaterialId());
            map.put("materialName", m.getMaterialName());
            map.put("qmCode", nvl(m.getQmCode()));
            map.put("parentCategory", nvl(m.getParentCategory()));
            map.put("category", nvl(m.getCategory()));
            map.put("spec", nvl(m.getSpec()));
            map.put("unit", inventoryUnitMap.getOrDefault(m.getMaterialId(), ""));
            map.put("inventoryUnit", inventoryUnitMap.getOrDefault(m.getMaterialId(), ""));
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        return result;
    }

    // ==================== 外部 API → 本地 DB 同步 ====================

    /**
     * 每天凌晨 3 点全量同步 + 首次查询时懒加载。
     * 从外部 API 翻页拉取物料主数据，upsert 到本地 material 表。
     */
    // @Scheduled(cron = "0 0 3 * * *")  // 已关闭定时同步
    public void scheduledSync() {
        if (!syncEnabled()) return;
        syncFromApi();
    }

    /** 可通过配置 app.sync.enabled=false 关闭所有外部API同步 */
    @org.springframework.beans.factory.annotation.Value("${app.sync.enabled:false}")
    private boolean syncEnabled;

    private boolean syncEnabled() { return syncEnabled; }

    @Override
    public int syncFromApi() {
        log.info("开始从外部 API 同步物料到本地数据库...");
        try {
            List<MaterialInfo> materials = fetchAllFromApi();
            upsertMaterials(materials);
            log.info("物料同步完成，共 {} 条记录", materials.size());
            return materials.size();
        } catch (Exception e) {
            log.error("物料同步失败", e);
            throw new RuntimeException("物料同步失败: " + e.getMessage(), e);
        }
    }

    private List<MaterialInfo> fetchAllFromApi() {
        List<MaterialInfo> allMaterials = new ArrayList<>();
        int page = 1;
        int pageSize = 100;
        boolean hasMore = true;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Publish-Api-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        while (hasMore) {
            String url = apiUrl + "?page=" + page + "&pageSize=" + pageSize;
            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity,
                        new ParameterizedTypeReference<Map<String, Object>>() {});

                Map<String, Object> body = response.getBody();
                if (body == null) break;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) body.get("records");
                if (records == null || records.isEmpty()) break;

                for (Map<String, Object> record : records) {
                    MaterialInfo material = new MaterialInfo();
                    material.setId(toString(record.get("id")));
                    material.setPinxiangbianma((String) record.get("pinxiangbianma"));
                    material.setLeibie((String) record.get("leibie"));
                    material.setLeibie2((String) record.get("leibie2"));
                    material.setYuancailiaomingcheng((String) record.get("yuancailiaomingcheng"));
                    material.setGuige((String) record.get("guige"));
                    material.setPandiandanwei((String) record.get("pandiandanwei"));
                    allMaterials.add(material);
                }

                Object totalObj = body.get("total");
                int total = totalObj instanceof Number ? ((Number) totalObj).intValue() : 0;
                hasMore = page * pageSize < total;
                page++;
            } catch (Exception e) {
                log.error("获取物料信息失败，page={}", page, e);
                break;
            }
        }

        log.info("从外部 API 获取物料完成，共 {} 条记录，请求 {} 页", allMaterials.size(), page - 1);
        return allMaterials;
    }

    private void upsertMaterials(List<MaterialInfo> items) {
        for (MaterialInfo item : items) {
            if (!StringUtils.hasText(item.getId()) || !StringUtils.hasText(item.getYuancailiaomingcheng())) {
                continue;
            }
            Material material = materialMapper.selectOne(
                    new LambdaQueryWrapper<Material>().eq(Material::getMaterialId, item.getId()));
            if (material == null) {
                material = new Material();
                material.setMaterialId(item.getId());
            }
            material.setQmCode(item.getPinxiangbianma());
            material.setParentCategory(item.getLeibie());
            material.setCategory(item.getLeibie2());
            material.setMaterialName(item.getYuancailiaomingcheng());
            material.setSpec(item.getGuige());
            if (material.getId() == null) {
                materialMapper.insert(material);
            } else {
                materialMapper.updateById(material);
            }
        }
    }

    // ==================== 盘点单位迁移 ====================

    /**
     * 一次性脚本：从外部 API 拉取盘点单位，写入 material_inventory_rule。
     * 只处理尚未维护规则的物料（rule 不存在时才创建）。
     * @return 迁移成功的条数
     */
    @Override
    @Transactional
    public int migrateInventoryUnits() {
        log.info("开始迁移外部 API 盘点单位到 material_inventory_rule...");
        List<MaterialInfo> apiMaterials = fetchAllFromApi();
        int count = 0;
        for (MaterialInfo item : apiMaterials) {
            String pandiandanwei = item.getPandiandanwei();
            if (!StringUtils.hasText(pandiandanwei)) {
                continue;
            }
            String materialId = item.getId();
            // 已有规则则跳过
            Long exists = ruleMapper.selectCount(
                    new LambdaQueryWrapper<MaterialInventoryRule>().eq(MaterialInventoryRule::getMaterialId, materialId));
            if (exists > 0) {
                continue;
            }
            MaterialInventoryRule rule = new MaterialInventoryRule();
            rule.setRuleId("TMP");
            rule.setMaterialId(materialId);
            rule.setBaseUnit(pandiandanwei.trim());
            rule.setInventoryUnits(pandiandanwei.trim());
            rule.setUnitPrice(java.math.BigDecimal.ZERO);
            ruleMapper.insert(rule);
            rule.setRuleId("MR" + String.format("%08d", rule.getId()));
            ruleMapper.updateById(rule);
            count++;
        }
        log.info("盘点单位迁移完成，共创建 {} 条规则", count);
        return count;
    }

    // ==================== 总部端 CRUD ====================

    @Override
    public Material getByMaterialId(String materialId) {
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialId, materialId));
        if (material == null) {
            throw new BusinessException(404, "物料不存在");
        }
        return material;
    }

    @Override
    @Transactional
    public Material create(Material material) {
        if (!StringUtils.hasText(material.getMaterialName())) {
            throw new BusinessException(400, "物料名称不能为空");
        }
        Long count = materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialName, material.getMaterialName().trim()));
        if (count > 0) {
            throw new BusinessException(400, "物料名称已存在");
        }
        material.setMaterialId("M" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase());
        material.setMaterialName(material.getMaterialName().trim());
        material.setQmCode(trimToNull(material.getQmCode()));
        material.setParentCategory(trimToNull(material.getParentCategory()));
        material.setCategory(trimToNull(material.getCategory()));
        material.setSpec(trimToNull(material.getSpec()));
        materialMapper.insert(material);
        return material;
    }

    @Override
    @Transactional
    public Material update(String materialId, Material req) {
        Material material = getByMaterialId(materialId);
        if (StringUtils.hasText(req.getMaterialName())) {
            Long dup = materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                    .eq(Material::getMaterialName, req.getMaterialName().trim())
                    .ne(Material::getId, material.getId()));
            if (dup > 0) {
                throw new BusinessException(400, "物料名称已存在");
            }
            material.setMaterialName(req.getMaterialName().trim());
        }
        if (req.getQmCode() != null) material.setQmCode(trimToNull(req.getQmCode()));
        if (req.getParentCategory() != null) material.setParentCategory(trimToNull(req.getParentCategory()));
        if (req.getCategory() != null) material.setCategory(trimToNull(req.getCategory()));
        if (req.getSpec() != null) material.setSpec(trimToNull(req.getSpec()));
        materialMapper.updateById(material);
        // 发布物料变更事件，触发未开始任务重新快照
        eventPublisher.publishEvent(new MaterialChangedEvent(this, materialId));
        return material;
    }

    @Override
    @Transactional
    public void deleteByMaterialId(String materialId) {
        Material material = getByMaterialId(materialId);
        Long refCount = templateZoneMaterialMapper.selectCount(new LambdaQueryWrapper<TemplateZoneMaterial>()
                .eq(TemplateZoneMaterial::getMaterialId, materialId));
        if (refCount > 0) {
            throw new BusinessException(400, "该物料已被模板引用，不可删除");
        }
        materialMapper.deleteById(material.getId());
    }

    @Override
    @Transactional
    public void toggleStatus(String materialId) {
        Material material = getByMaterialId(materialId);
        material.setDelFlag(material.getDelFlag() == 1 ? 0 : 1);
        materialMapper.updateById(material);
    }

    @Override
    public List<String> getAllCategories() {
        return materialMapper.selectList(new LambdaQueryWrapper<Material>())
                .stream()
                .map(Material::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAllParentCategories() {
        return materialMapper.selectList(new LambdaQueryWrapper<Material>())
                .stream()
                .map(Material::getParentCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // ==================== 工具方法 ====================

    private MaterialInfo toInfo(Material m) {
        MaterialInfo info = new MaterialInfo();
        info.setId(m.getMaterialId());
        info.setPinxiangbianma(m.getQmCode());
        info.setLeibie(m.getParentCategory());
        info.setLeibie2(m.getCategory());
        info.setYuancailiaomingcheng(m.getMaterialName());
        info.setGuige(m.getSpec());
        info.setPandiandanwei(null); // 由 searchMaterials 批量填充
        return info;
    }

    private String nvl(String val) {
        return val == null ? "" : val;
    }

    private String trimToNull(String val) {
        return StringUtils.hasText(val) ? val.trim() : null;
    }

    private static String toString(Object value) {
        return value == null ? null : value.toString();
    }
}
