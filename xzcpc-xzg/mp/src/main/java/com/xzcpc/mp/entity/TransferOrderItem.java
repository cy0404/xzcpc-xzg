package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * P0 A3: 调货单明细
 */
@Data
@TableName("transfer_order_item")
public class TransferOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联调货单ID */
    private Long transferId;

    /** 物料名称 */
    private String materialName;

    /** 物料ID */
    private String materialId;

    /** 规格 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 调货数量 */
    private BigDecimal transferQty;

    /** 基础单位（最小盘点单位） */
    private String baseUnit;

    /** 基础单位数量（换算后用于跨分区汇总） */
    private BigDecimal baseQty;

    /** 录入单位 */
    private String inputUnit;

    /** 录入单位数量 */
    private BigDecimal inputQty;

    /** 物料单价 */
    private BigDecimal unitPrice;

    /** 备注 */
    private String remark;

    /** 已归还数量(非DB字段,接口查询时填充) */
    @TableField(exist = false)
    private java.math.BigDecimal returnedQty;

    /** 已归还金额(非DB字段,接口查询时填充,还钱场景) */
    @TableField(exist = false)
    private java.math.BigDecimal returnAmount;

    /** 归还状态(非DB字段,接口查询时填充): returned_goods|returned_money|null */
    @TableField(exist = false)
    private String returnStatus;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
