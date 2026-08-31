package com.xzcpc.template.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * xinfo 半成品列表（GET /api/semi-finished-products）。
 * 基础信息同步进 material 表；formulaVersions（配方版本）同步进 semi_formula_* 表
 * （半成品成本卡 BOM，差异计算爆炸用）。
 *
 * 2026-08-31 实测：编码全部 WP 开头（与原料前缀无法区分）；版本层无净出量字段，
 * 净出量/得率在半成品主对象层（netOutputQuantity / netOutputUnit / yieldRate）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class XInfoSemiFinishedProduct {

    /** 半成品 ID（内部主键，cm 开头）；与 material.material_id 不对应，关联靠 code↔qm_code */
    private String id;

    /** 半成品编码（WP 开头），同步后作为 material.qm_code（全链路关联键） */
    private String code;

    private String name;

    private String specification;

    /** 库存单位（如 kg），同步后作为 material_inventory_rule.base_unit */
    private String unit;

    /** 生效配方净出量（如 1259.34 kg），爆炸比例基准（× 1/净出量） */
    private BigDecimal netOutputQuantity;

    /** 生效配方净出量单位（如 kg） */
    private String netOutputUnit;

    /** 生效配方得率（如 99.06） */
    private BigDecimal yieldRate;

    /** 配方版本列表（含完整 items 配方行），同步进 semi_formula_version / semi_formula_item */
    private List<XInfoFormulaVersion> formulaVersions;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 企迈库存单位单价（元，如牛油果泥 0.0537 元/g；库存口径，非订货价） */
    private BigDecimal qimaiStockPrice;

    /** 企迈价格（元，按 unit 口径，如牛油果泥 53.7 元/kg；非订货价） */
    private BigDecimal qimaiPrice;

    /**
     * 实际成本价（元，number 4 位小数）= netOutputQuantity × bomPricePerGram，
     * 业务口径即「半成品订货价格」。接口字段名为 cost（netOutputQuantity 当前不返回，
     * cost 由服务端算好直接给）。同步为 order_price。
     */
    private BigDecimal cost;

    /**
     * 采购单价（元）。接口当前未返回该字段（2026-08-27 实测 0/86），
     * 预留：接口补充后同步自动补录 material_inventory_rule.purchase_price。
     */
    private BigDecimal purchasePrice;

    /**
     * 采购单位。接口当前未返回该字段（2026-08-27 实测 0/86），
     * 预留：接口补充后同步自动补录 material_inventory_rule.purchase_unit。
     */
    private String purchaseUnit;
}
