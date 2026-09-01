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
import java.util.List;

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

    @TableField(exist = false)
    private String supervisorName;

    /** 明细概要（列表页展示用）：首条明细名称 + 明细条数，page() 批量填充 */
    @TableField(exist = false)
    private String firstItemName;

    @TableField(exist = false)
    private Integer itemCount;

    /** 凭证列表（多张；主表 voucherUrl 冗余首张，detail() 从子表填充） */
    @TableField(exist = false)
    private List<String> voucherUrls;
}
