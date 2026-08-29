package com.xzcpc.template.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material_inventory_rule")
public class MaterialInventoryRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleId;
    private String materialId;
    private String baseUnit;
    private String inventoryUnits;
    private String stockUnit;
    private BigDecimal unitPrice;

    /** 采购单价（元，按采购单位；xinfo purchasePrice 同步） */
    private BigDecimal purchasePrice;

    /** 采购单位（xinfo purchaseUnit 同步，接口未返回时为空） */
    private String purchaseUnit;

    /** 订货单位（xinfo usageUnit 同步） */
    private String orderUnit;

    /** 订货单价（元，按订货单位；xinfo standardCostPrice 同步） */
    private BigDecimal orderPrice;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
