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

/**
 * 半成品成本卡主表（xinfo 同步）。
 * 关联键：code ↔ material.qm_code（全链路），semi_id 与 material.material_id 不对应。
 */
@Data
@TableName("semi_product")
public class SemiProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** xinfo 半成品 ID（cm 开头） */
    private String semiId;

    /** 半成品编码（WP 开头，与 material.qm_code 对应） */
    private String code;

    private String name;

    private String specification;

    /** 库存单位（kg 等，与 material_inventory_rule.base_unit 一致） */
    private String unit;

    /** 生效配方净出量（爆炸比例基准 × 1/净出量） */
    private BigDecimal netOutputQuantity;

    /** 净出量单位 */
    private String netOutputUnit;

    /** 得率 */
    private BigDecimal yieldRate;

    /** 生效配方总成本 */
    private BigDecimal cost;

    /** ENABLED / DISABLED */
    private String status;

    private LocalDateTime syncedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
