package com.xzcpc.mp.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支出记录明细统一返回体：
 * 自购食材 = 物料（materialId/name/分类/unit/qty/unitPrice/amount 全填）；
 * 其他类型 = 名称+金额（仅 name/amount）
 */
@Data
public class ExpenseItemVO {

    private String materialId;
    private String name;
    private String parentCategory;
    private String category;
    private String unit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private Integer sortNo;
    private String remark;
}
