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
 * 半成品配方版本（xinfo 同步）。
 * 仅 status=ACTIVE 且 del_flag=0 的版本用于爆炸。
 */
@Data
@TableName("semi_formula_version")
public class SemiFormulaVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** xinfo 配方版本 ID */
    private Long versionId;

    /** 所属半成品 ID（冗余） */
    private String semiId;

    private String versionName;

    /** DRAFT / ACTIVE / DISABLED */
    private String status;

    private BigDecimal totalCost;

    private LocalDateTime syncedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
