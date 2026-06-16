package com.xzcpc.expense.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("expense_item")
public class ExpenseItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String itemId;
    private String typeId;
    private String name;
    private String description;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @TableField(exist = false)
    private String typeName;
}
