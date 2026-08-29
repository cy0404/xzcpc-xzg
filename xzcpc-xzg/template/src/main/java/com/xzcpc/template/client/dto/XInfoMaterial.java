package com.xzcpc.template.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

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
}
