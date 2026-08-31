package com.xzcpc.template.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * xinfo 原料列表（GET /api/materials）。
 * 服务端 @JsonInclude(NON_NULL)，无值字段在 JSON 中省略，缺省即 null。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class XInfoMaterial {

    /** 原料 ID（内部主键），同步后作为 material.material_id */
    private String id;

    /** 原料编码（RM/WP/TJ 开头），同步后作为 material.qm_code */
    private String code;

    private String name;

    private String primaryCategory;

    private String secondaryCategory;

    private String specification;

    /** 使用单位（订货单位） */
    private String usageUnit;

    /** 库存单位（企迈库存口径） */
    private String stockUnit;

    /** 单位换算关系，如 "1瓶=950g, 1件=6瓶" */
    private String unitConversion;

    private String baseUnit;

    /** 称重换算 */
    private String weighingConversion;

    /** 盘点价（元，按使用单位） */
    private BigDecimal inventoryPrice;

    private BigDecimal standardCostPrice;

    private BigDecimal pricePerKilogram;

    /** 采购单价（元，按采购单位；企迈同步写入，手工编辑不覆盖） */
    private BigDecimal purchasePrice;

    /** 采购单位（接口暂未返回该字段，预留——接口补充后自动填充，缺失即 null） */
    private String purchaseUnit;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 图片 URL 列表（服务端序列化为 JSON 字符串数组：[{"url":...,"fileName":...,"uploadedAt":...}]），无图为 null */
    private String imageUrls;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 解析 imageUrls（JSON 字符串数组）取第一张图 URL；无图/解析失败返回 null。
     * 企迈 CDN 链接（images.qmai.cn），直接存 URL 不下载。
     */
    public String firstImageUrl() {
        if (!StringUtils.hasText(imageUrls)) {
            return null;
        }
        try {
            JsonNode arr = MAPPER.readTree(imageUrls);
            if (arr.isArray() && !arr.isEmpty()) {
                JsonNode first = arr.get(0);
                if (first.hasNonNull("url") && StringUtils.hasText(first.get("url").asText())) {
                    return first.get("url").asText().trim();
                }
            }
        } catch (Exception e) {
            // 图片是辅助展示信息，解析失败不阻断同步
        }
        return null;
    }
}
