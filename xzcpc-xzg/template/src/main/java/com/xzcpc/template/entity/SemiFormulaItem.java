package com.xzcpc.template.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 半成品配方行（BOM 明细，xinfo 同步）。
 * itemType=MATERIAL 走 materialId/qimaiProductCode 匹配原料；
 * itemType=SEMI_FINISHED 走 semiFinishedProductId 递归爆炸。
 * 同步按版本全量替换（物理删除后重插），无 @TableLogic。
 */
@Data
@TableName("semi_formula_item")
public class SemiFormulaItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属配方版本 ID */
    private Long versionId;

    /** 所属半成品 ID（冗余） */
    private String semiId;

    /** xinfo 配方行 ID */
    private Long itemId;

    /** MATERIAL 原料 / SEMI_FINISHED 半成品 */
    private String itemType;

    /** 原料 ID（itemType=MATERIAL，cm 开头，与 material.material_id 对应） */
    private String materialId;

    /** 半成品 ID（itemType=SEMI_FINISHED，与 semi_product.semi_id 对应） */
    private String semiFinishedProductId;

    private String itemName;

    /** 用量 */
    private BigDecimal quantity;

    /** 用量单位 */
    private String unit;

    /** 损耗率（0.0094 = 0.94%） */
    private BigDecimal lossRate;

    private Integer sortOrder;

    /** 企迈商品编码（WP 开头），爆炸目标匹配优先通道 */
    private String qimaiProductCode;

    private LocalDateTime syncedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
