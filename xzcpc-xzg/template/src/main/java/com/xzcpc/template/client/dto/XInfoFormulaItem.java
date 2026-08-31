package com.xzcpc.template.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * xinfo 配方行（BOM 明细，formulaVersions[].items[]）。
 *
 * itemType=MATERIAL → 原料（materialId + qimaiProductCode）；
 * itemType=SEMI_FINISHED → 子半成品（semiFinishedProductId，递归爆炸）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class XInfoFormulaItem {

    /** 配方行 ID（内部主键） */
    private Long id;

    /** 行类型：MATERIAL 原料 / SEMI_FINISHED 半成品 */
    private String itemType;

    /** 原料 ID（itemType=MATERIAL；cm 开头，与 material.material_id 对应） */
    private String materialId;

    /** 半成品 ID（itemType=SEMI_FINISHED；cm 开头，与 semi_product.semi_id 对应） */
    private String semiFinishedProductId;

    /** 用料名称 */
    private String itemName;

    /** 用量（对应半成品净出量的用料量） */
    private BigDecimal quantity;

    /** 用量单位（g/kg 等） */
    private String unit;

    /** 损耗率（0.0094 = 0.94%） */
    private BigDecimal lossRate;

    /** 排序 */
    private Integer sortOrder;

    /**
     * 企迈商品编码（WP 开头）。爆炸目标匹配优先用它（qm_code 通道，
     * 与全链路关联键一致），materialId 兜底。
     */
    private String qimaiProductCode;
}
