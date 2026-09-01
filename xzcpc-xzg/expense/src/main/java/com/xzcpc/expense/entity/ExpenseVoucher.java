package com.xzcpc.expense.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支出凭证子表：一单多张（最多9张），主表 voucher_url 冗余首张保持报表契约不变
 */
@Data
@TableName("expense_voucher")
public class ExpenseVoucher {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支出业务ID（EXP... 或 SPM...） */
    private String expenseId;

    private String voucherUrl;

    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
