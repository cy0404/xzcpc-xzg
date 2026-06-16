package com.xzcpc.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.MaterialInfo;
import com.xzcpc.template.dto.MaterialRuleResp;
import com.xzcpc.template.dto.MaterialRuleSaveReq;
import com.xzcpc.template.entity.MaterialConversionRule;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialConversionRuleMapper;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.service.MaterialRuleService;
import com.xzcpc.template.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialRuleServiceImpl implements MaterialRuleService {

    private static final String STATUS_MAINTAINED = "maintained";
    private static final String STATUS_PENDING = "pending";
    private static final String TYPE_UNIT = "unit";
    private static final String TYPE_WEIGHT = "weight";

    private final MaterialService materialService;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialConversionRuleMapper conversionRuleMapper;

    @Override
    public Map<String, Object> page(String keyword, String parentCategory, String category,
                                    String baseUnit, String conversionType,
                                    int pageNum, int pageSize) {
        List<MaterialInfo> materials = materialService.searchMaterials(keyword).stream()
                .filter(item -> !StringUtils.hasText(parentCategory) || parentCategory.equals(item.getLeibie()))
                .filter(item -> !StringUtils.hasText(category) || category.equals(item.getLeibie2()))
                .toList();

        Map<String, MaterialInventoryRule> ruleMap = getRuleMap(materials);

        // 批量加载所有换算关系，避免 N+1
        List<String> ruleIds = ruleMap.values().stream().map(MaterialInventoryRule::getRuleId).toList();
        Map<String, List<MaterialConversionRule>> conversionsByRuleId = ruleIds.isEmpty()
                ? Map.of()
                : conversionRuleMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .in(MaterialConversionRule::getRuleId, ruleIds)
                        .orderByAsc(MaterialConversionRule::getSortNo)
                        .orderByAsc(MaterialConversionRule::getId))
                .stream()
                .collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));

        List<MaterialRuleResp> merged = materials.stream()
                .map(item -> buildResp(item, ruleMap.get(item.getId()), conversionsByRuleId))
                .filter(item -> !StringUtils.hasText(baseUnit) || baseUnit.equals(item.getBaseUnit()))
                .filter(item -> matchConversionType(item, conversionType))
                .toList();

        int total = merged.size();
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNum - 1) * safePageSize, total);
        int toIndex = Math.min(fromIndex + safePageSize, total);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", merged.subList(fromIndex, toIndex));
        result.put("total", total);
        result.put("current", safePageNum);
        result.put("size", safePageSize);
        return result;
    }

    @Override
    public Map<String, MaterialRuleResp> batchDetail(List<String> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return Map.of();
        List<String> distinctIds = materialIds.stream().filter(StringUtils::hasText).distinct().toList();
        if (distinctIds.isEmpty()) return Map.of();

        List<MaterialInfo> materials = materialService.getAllMaterials().stream()
                .filter(m -> distinctIds.contains(m.getId())).toList();
        Map<String, MaterialInventoryRule> ruleMap = getRuleMap(materials);
        List<String> ruleIds = ruleMap.values().stream().map(MaterialInventoryRule::getRuleId).toList();
        Map<String, List<MaterialConversionRule>> conversionsByRuleId = ruleIds.isEmpty()
                ? Map.of()
                : conversionRuleMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .in(MaterialConversionRule::getRuleId, ruleIds)
                        .orderByAsc(MaterialConversionRule::getSortNo)
                        .orderByAsc(MaterialConversionRule::getId))
                .stream()
                .collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));

        Map<String, MaterialRuleResp> result = new LinkedHashMap<>();
        for (MaterialInfo m : materials) {
            result.put(m.getId(), buildResp(m, ruleMap.get(m.getId()), conversionsByRuleId));
        }
        // 没找到规则的物料返回 pending 状态
        for (String id : distinctIds) {
            result.putIfAbsent(id, pendingFallback(id));
        }
        return result;
    }

    private MaterialRuleResp pendingFallback(String materialId) {
        MaterialRuleResp resp = new MaterialRuleResp();
        resp.setMaterialId(materialId);
        resp.setRuleStatus(STATUS_PENDING);
        return resp;
    }

    @Override
    public List<String> getAllBaseUnits() {
        return ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                        .select(MaterialInventoryRule::getBaseUnit)
                        .isNotNull(MaterialInventoryRule::getBaseUnit)
                        .ne(MaterialInventoryRule::getBaseUnit, ""))
                .stream()
                .map(MaterialInventoryRule::getBaseUnit)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public MaterialRuleResp detail(String materialId) {
        MaterialInfo material = findMaterial(materialId);
        MaterialInventoryRule rule = ruleMapper.selectOne(
                new LambdaQueryWrapper<MaterialInventoryRule>().eq(MaterialInventoryRule::getMaterialId, materialId));
        return buildResp(material, rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialRuleResp save(String materialId, MaterialRuleSaveReq req) {
        MaterialInfo material = findMaterial(materialId);
        validateReq(req);

        MaterialInventoryRule rule = ruleMapper.selectOne(
                new LambdaQueryWrapper<MaterialInventoryRule>().eq(MaterialInventoryRule::getMaterialId, materialId));
        if (rule == null) {
            rule = new MaterialInventoryRule();
            rule.setRuleId("TMP"); // 先设临时值，等拿到自增 id 后再更新正式编码
            rule.setMaterialId(materialId);
            rule.setBaseUnit(req.getBaseUnit().trim());
            rule.setInventoryUnits(toInventoryUnits(req));
            rule.setUnitPrice(req.getUnitPrice());
            ruleMapper.insert(rule);
            rule.setRuleId("MR" + String.format("%08d", rule.getId()));
            ruleMapper.updateById(rule);
        } else {
            rule.setBaseUnit(req.getBaseUnit().trim());
            rule.setInventoryUnits(toInventoryUnits(req));
            rule.setUnitPrice(req.getUnitPrice());
            ruleMapper.updateById(rule);
            clearRuleItems(rule.getRuleId());
        }

        saveConversions(rule.getRuleId(), req);
        saveWeightConversions(rule.getRuleId(), req);
        return detail(material.getId());
    }

    private Map<String, MaterialInventoryRule> getRuleMap(List<MaterialInfo> materials) {
        List<String> ids = materials.stream().map(MaterialInfo::getId).filter(StringUtils::hasText).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                        .in(MaterialInventoryRule::getMaterialId, ids))
                .stream()
                .collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, Function.identity(), (a, b) -> a));
    }

    private MaterialInfo findMaterial(String materialId) {
        return materialService.getAllMaterials().stream()
                .filter(item -> materialId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "物料不存在"));
    }

    /** 详情页调用，单条查询直接加载换算关系 */
    private MaterialRuleResp buildResp(MaterialInfo material, MaterialInventoryRule rule) {
        if (rule == null) return buildResp(material, null, Map.of());
        List<MaterialConversionRule> conversions = conversionRuleMapper.selectList(
                new LambdaQueryWrapper<MaterialConversionRule>()
                        .eq(MaterialConversionRule::getRuleId, rule.getRuleId())
                        .orderByAsc(MaterialConversionRule::getSortNo)
                        .orderByAsc(MaterialConversionRule::getId));
        return buildResp(material, rule, Map.of(rule.getRuleId(), conversions));
    }

    /** 分页列表调用，批量预加载换算关系 */
    private MaterialRuleResp buildResp(MaterialInfo material, MaterialInventoryRule rule,
                                        Map<String, List<MaterialConversionRule>> conversionsByRuleId) {
        MaterialRuleResp resp = new MaterialRuleResp();
        resp.setMaterialId(material.getId());
        resp.setMaterialName(material.getYuancailiaomingcheng());
        resp.setQmCode(material.getPinxiangbianma());
        resp.setParentCategory(material.getLeibie());
        resp.setCategory(material.getLeibie2());
        resp.setSpec(material.getGuige());
        if (rule == null) {
            resp.setRuleStatus(STATUS_PENDING);
            resp.setBaseUnit("");
            resp.setUnitPrice(java.math.BigDecimal.ZERO);
            return resp;
        }

        resp.setRuleId(rule.getRuleId());
        resp.setBaseUnit(rule.getBaseUnit());
        resp.setUnitPrice(rule.getUnitPrice());
        resp.setUpdatedAt(rule.getUpdatedAt());
        resp.setRuleStatus(STATUS_MAINTAINED);
        resp.setUnits(loadUnits(rule));

        List<MaterialConversionRule> allConversions = conversionsByRuleId.getOrDefault(rule.getRuleId(), List.of());
        resp.setConversions(toUnitConversions(allConversions));
        resp.setWeightConversions(toWeightConversions(allConversions));
        return resp;
    }

    /** 从平铺的 conversion 列表中提取单位换算（type=unit） */
    private List<MaterialRuleResp.UnitConversionItem> toUnitConversions(List<MaterialConversionRule> all) {
        return all.stream()
                .filter(c -> TYPE_UNIT.equals(c.getConversionType()))
                .map(c -> {
                    MaterialRuleResp.UnitConversionItem item = new MaterialRuleResp.UnitConversionItem();
                    item.setFromQuantity(c.getFromQuantity());
                    item.setFromUnit(c.getFromUnit());
                    item.setToQuantity(c.getToQuantity());
                    item.setToUnit(c.getToUnit());
                    return item;
                }).toList();
    }

    /** 从平铺的 conversion 列表中提取称重换算（type=weight） */
    private List<MaterialRuleResp.WeightConversionItem> toWeightConversions(List<MaterialConversionRule> all) {
        return all.stream()
                .filter(c -> TYPE_WEIGHT.equals(c.getConversionType()))
                .map(c -> {
                    MaterialRuleResp.WeightConversionItem item = new MaterialRuleResp.WeightConversionItem();
                    item.setWeightQuantity(c.getFromQuantity());
                    item.setWeightUnit(c.getFromUnit());
                    item.setCountQuantity(c.getToQuantity());
                    item.setCountUnit(c.getToUnit());
                    return item;
                }).toList();
    }

    private boolean matchConversionType(MaterialRuleResp item, String conversionType) {
        if (!StringUtils.hasText(conversionType)) {
            return true;
        }
        return switch (conversionType) {
            case "maintained" -> !item.getConversions().isEmpty() || !item.getWeightConversions().isEmpty();
            case "pending" -> item.getConversions().isEmpty() && item.getWeightConversions().isEmpty();
            default -> true;
        };
    }

    private List<MaterialRuleResp.UnitItem> loadUnits(MaterialInventoryRule rule) {
        if (!StringUtils.hasText(rule.getInventoryUnits())) {
            return List.of();
        }
        return List.of(rule.getInventoryUnits().split(",")).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(unitName -> {
                    MaterialRuleResp.UnitItem item = new MaterialRuleResp.UnitItem();
                    item.setUnitName(unitName);
                    item.setBaseUnit(unitName.equals(rule.getBaseUnit()));
                    return item;
                })
                .toList();
    }

    private List<MaterialRuleResp.UnitConversionItem> loadConversions(String ruleId) {
        return conversionRuleMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .eq(MaterialConversionRule::getRuleId, ruleId)
                        .eq(MaterialConversionRule::getConversionType, TYPE_UNIT)
                        .orderByAsc(MaterialConversionRule::getSortNo)
                        .orderByAsc(MaterialConversionRule::getId))
                .stream()
                .map(item -> {
                    MaterialRuleResp.UnitConversionItem resp = new MaterialRuleResp.UnitConversionItem();
                    resp.setFromQuantity(item.getFromQuantity());
                    resp.setFromUnit(item.getFromUnit());
                    resp.setToQuantity(item.getToQuantity());
                    resp.setToUnit(item.getToUnit());
                    return resp;
                })
                .toList();
    }

    private List<MaterialRuleResp.WeightConversionItem> loadWeightConversions(String ruleId) {
        return conversionRuleMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .eq(MaterialConversionRule::getRuleId, ruleId)
                        .eq(MaterialConversionRule::getConversionType, TYPE_WEIGHT)
                        .orderByAsc(MaterialConversionRule::getSortNo)
                        .orderByAsc(MaterialConversionRule::getId))
                .stream()
                .map(item -> {
                    MaterialRuleResp.WeightConversionItem resp = new MaterialRuleResp.WeightConversionItem();
                    resp.setWeightQuantity(item.getFromQuantity());
                    resp.setWeightUnit(item.getFromUnit());
                    resp.setCountQuantity(item.getToQuantity());
                    resp.setCountUnit(item.getToUnit());
                    return resp;
                })
                .toList();
    }

    private void validateReq(MaterialRuleSaveReq req) {
        if (!StringUtils.hasText(req.getBaseUnit())) {
            throw new BusinessException(400, "基础盘点单位不能为空");
        }
        if (req.getUnitPrice() == null || req.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "盘点单价不能小于0");
        }
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, message);
        }
    }

    private void clearRuleItems(String ruleId) {
        conversionRuleMapper.delete(new LambdaQueryWrapper<MaterialConversionRule>()
                .eq(MaterialConversionRule::getRuleId, ruleId));
    }

    private String toInventoryUnits(MaterialRuleSaveReq req) {
        Set<String> unitNames = new LinkedHashSet<>();
        unitNames.add(req.getBaseUnit().trim());
        // 从单位换算中提取所有单位
        if (req.getConversions() != null) {
            req.getConversions().forEach(item -> {
                if (item != null) {
                    if (StringUtils.hasText(item.getFromUnit())) unitNames.add(item.getFromUnit().trim());
                    if (StringUtils.hasText(item.getToUnit())) unitNames.add(item.getToUnit().trim());
                }
            });
        }
        // 从重量换算中提取数量单位
        if (req.getWeightConversions() != null) {
            req.getWeightConversions().forEach(item -> {
                if (item != null && StringUtils.hasText(item.getCountUnit())) {
                    unitNames.add(item.getCountUnit().trim());
                }
            });
        }
        return String.join(",", unitNames);
    }

    private void saveConversions(String ruleId, MaterialRuleSaveReq req) {
        if (req.getConversions() == null) return;
        for (int i = 0; i < req.getConversions().size(); i++) {
            MaterialRuleSaveReq.UnitConversionItem item = req.getConversions().get(i);
            if (item == null) continue;
            MaterialConversionRule conversion = new MaterialConversionRule();
            conversion.setRuleId(ruleId);
            conversion.setConversionType(TYPE_UNIT);
            conversion.setFromQuantity(item.getFromQuantity());
            conversion.setFromUnit(trim(item.getFromUnit()));
            conversion.setToQuantity(item.getToQuantity());
            conversion.setToUnit(trim(item.getToUnit()));
            conversion.setSortNo(i + 1);
            conversionRuleMapper.insert(conversion);
        }
    }

    private void saveWeightConversions(String ruleId, MaterialRuleSaveReq req) {
        if (req.getWeightConversions() == null) return;
        for (int i = 0; i < req.getWeightConversions().size(); i++) {
            MaterialRuleSaveReq.WeightConversionItem item = req.getWeightConversions().get(i);
            if (item == null) continue;
            MaterialConversionRule conversion = new MaterialConversionRule();
            conversion.setRuleId(ruleId);
            conversion.setConversionType(TYPE_WEIGHT);
            conversion.setFromQuantity(item.getWeightQuantity());
            conversion.setFromUnit(trim(item.getWeightUnit()));
            conversion.setToQuantity(item.getCountQuantity());
            conversion.setToUnit(trim(item.getCountUnit()));
            conversion.setSortNo(i + 1);
            conversionRuleMapper.insert(conversion);
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
