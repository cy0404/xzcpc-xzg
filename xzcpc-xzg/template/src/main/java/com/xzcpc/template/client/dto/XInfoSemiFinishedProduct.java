package com.xzcpc.template.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * xinfo 半成品列表（GET /api/semi-finished-products）。
 * 仅同步基础信息，formulaVersions（配方版本）不落库，由 ignoreUnknown 忽略。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class XInfoSemiFinishedProduct {

    /** 半成品 ID（内部主键），同步后作为 material.material_id */
    private String id;

    /** 半成品编码（SF 开头），同步后作为 material.qm_code */
    private String code;

    private String name;

    private String specification;

    /** 单位（如 g） */
    private String unit;

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
