package com.xzcpc.expense.entity;

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
 * 支出记录明细（expense_record_item）：非自购类型的支出记录可一次登记最多 10 条明细，
 * 每行「名称 + 金额」，主表 expense_record.amount = Σ 明细
 */
@Data
@TableName("expense_record_item")
public class ExpenseRecordItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String expenseId;
    private String itemName;
    private BigDecimal amount;
    private Integer sortNo;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
