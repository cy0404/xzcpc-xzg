package com.xzcpc.expense.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("self_purchase_material")
public class SelfPurchaseMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizCode;
    private String storeId;
    private String storeName;
    private String storeMiniappNo;
    private String materialId;
    private String parentCategory;
    private String category;
    private String materialName;
    private String unit;
    private String purchaseMonth;
    private LocalDate purchaseDate;
    private BigDecimal purchaseQty;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String handlerName;
    private String voucherUrl;
    private String remark;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String supervisorName;
}
