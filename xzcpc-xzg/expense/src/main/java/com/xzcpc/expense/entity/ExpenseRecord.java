package com.xzcpc.expense.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("expense_record")
public class ExpenseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String expenseId;
    private String storeId;
    private String storeMiniappNo;
    private String storeName;
    private String warehouseCode;
    private String typeId;
    private String typeName;
    private String firstTypeId;
    private String firstTypeName;
    private String itemId;
    private String itemName;
    private BigDecimal amount;
    private LocalDate occurredDate;
    private String handlerName;
    private String voucherUrl;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
