package com.xzcpc.expense.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("expense_type")
public class ExpenseType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String typeId;
    private String name;
    private String firstTypeId;
    private String firstTypeName;
    private String description;
    private String status;

    /** 排序号，越小越靠前，NULL 排最后 */
    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @TableField(exist = false)
    private Long usageCount;
}
