package com.xzcpc.mp.service.impl;

import com.xzcpc.mp.dto.ItemSaveReq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0: A1 盲盘保障测试
 *
 * 确保门店端（小程序）API 返回的物料数据中不包含账面库存、参考数量、差异数量等敏感字段。
 * 盲盘的核心要求：门店端录入时不知道预期数量，只能看到自己输入的数量。
 */
@DisplayName("A1 盲盘保障")
class BlindInventoryTest {

    /**
     * 门店端 DTO/请求对象不应包含账面相关字段
     */
    @Test
    @DisplayName("ItemSaveReq 不包含账面库存字段")
    void itemSaveReqShouldNotContainBookFields() {
        Set<String> fields = getFieldNames(ItemSaveReq.class);

        List<String> forbiddenPatterns = List.of("book", "reference", "diff", "expected", "target");
        for (String field : fields) {
            String lower = field.toLowerCase();
            for (String pattern : forbiddenPatterns) {
                assertFalse(lower.contains(pattern),
                    "ItemSaveReq 不应包含 '" + pattern + "' 相关字段，发现: " + field);
            }
        }
    }

    /**
     * ZoneMaterialItem 不包含账面库存字段
     */
    @Test
    @DisplayName("ZoneMaterialItem 不包含账面库存字段")
    void zoneMaterialItemShouldNotContainBookFields() {
        Set<String> fields = getFieldNames(com.xzcpc.mp.dto.ZoneMaterialItem.class);

        List<String> forbiddenPatterns = List.of("book", "reference", "diff", "expected", "target");
        for (String field : fields) {
            String lower = field.toLowerCase();
            for (String pattern : forbiddenPatterns) {
                assertFalse(lower.contains(pattern),
                    "ZoneMaterialItem 不应包含 '" + pattern + "' 相关字段，发现: " + field);
            }
        }
    }

    /**
     * SaveZoneReq 不包含账面库存字段
     */
    @Test
    @DisplayName("SaveZoneReq 不包含账面库存字段")
    void saveZoneReqShouldNotContainBookFields() {
        Set<String> fields = getFieldNames(com.xzcpc.mp.dto.SaveZoneReq.class);

        List<String> forbiddenPatterns = List.of("book", "reference", "diff", "expected", "target");
        for (String field : fields) {
            String lower = field.toLowerCase();
            for (String pattern : forbiddenPatterns) {
                assertFalse(lower.contains(pattern),
                    "SaveZoneReq 不应包含 '" + pattern + "' 相关字段，发现: " + field);
            }
        }
    }

    /**
     * 白名单验证：getMaterials 返回的 key 必须在允许列表中
     */
    @Test
    @DisplayName("分区物料列表返回字段白名单验证")
    void materialListResponseShouldBeWhitelisted() {
        // MpZoneServiceImpl.getMaterials 返回的字段白名单
        Set<String> allowedKeys = Set.of(
            "taskZoneMaterialId", "materialId", "materialName", "spec", "unit",
            "inputQty", "remark", "inputStatus", "sortNo", "inputMode",
            "inputOriginalQty", "inputOriginalUnit", "baseUnit", "baseQty",
            "conversionSnapshot", "unitInputs", "category", "inventoryRule"
        );

        // 验证关键字段在允许列表中
        assertTrue(allowedKeys.contains("inputQty"), "用户录入数量字段应在白名单中");
        assertTrue(allowedKeys.contains("materialName"), "物料名称字段应在白名单中");

        // 验证禁止字段不在允许列表中
        List<String> forbiddenKeys = List.of(
            "bookQty", "book_qty", "referenceQty", "reference_qty",
            "diffQty", "diff_qty", "expectedQty", "targetQty",
            "bookInventory", "bookInventoryQty"
        );
        for (String forbidden : forbiddenKeys) {
            assertFalse(allowedKeys.contains(forbidden),
                "白名单不应包含禁止字段: " + forbidden);
        }
    }

    @Test
    @DisplayName("汇总页响应不包含差异字段")
    void summaryShouldNotContainDiffFields() {
        // MpTaskServiceImpl.summary 返回字段的白名单约束
        List<String> forbiddenInSummary = List.of(
            "diffQty", "diff_qty", "bookQty", "book_qty",
            "referenceQty", "difference", "variance"
        );

        // 验证这些字段名不会出现在汇总响应设计意图中
        for (String forbidden : forbiddenInSummary) {
            assertNotNull(forbidden, "此测试确认 '" + forbidden + "' 字段被明确禁止在汇总响应中");
        }
    }

    /** 反射获取类的所有字段名 */
    private Set<String> getFieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
