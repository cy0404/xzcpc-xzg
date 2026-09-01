package com.xzcpc.expense.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("self_purchase_material_item")
public class SelfPurchaseMaterialItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizCode;
    private String materialId;
    private String materialName;
    private String parentCategory;
    private String category;
    private String unit;
    private BigDecimal purchaseQty;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Integer sortNo;
    private String remark;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
