package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * P0 A2: 门店报损记录
 */
@Data
@TableName("loss_report")
public class LossReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizCode;
    private String storeId;
    private String storeName;
    private String lossType;       // daily|arrival
    private String lossObject;     // finished|semi_finished
    private String materialId;
    private String materialName;
    private String spec;
    @TableField("input_unit")
    private String inputUnit;
    @TableField("input_qty")
    private BigDecimal inputQty;
    private BigDecimal unitPrice;    // 单价
    private BigDecimal totalAmount;  // 金额（数量×单价）
    private String baseUnit;
    private BigDecimal baseQty;
    private String qimaiOrderNo;    // 企迈单号（到货验收必填）
    // 半成品称重去皮
    private BigDecimal grossWeight;
    private Long containerId;
    private String containerName;
    private BigDecimal containerWeight;
    private BigDecimal netWeight;
    // 通用
    private String reason;
    private LocalDate occurredDate;
    private String handlerName;
    private String voucherUrl;
    private String remark;
    private String status;         // pending|confirmed_resend|rejected|closed
    private String rejectReason;  // 拒绝原因
    private String submittedBy;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
    @TableField(exist = false)
    private String supervisorName;
    @TableField(exist = false)
    private Boolean isFruitVeg;
    private Integer urgent;  // 0=否 1=加急

    /** 明细条数（多物料报损用；0=扁平单物料/到货） */
    private Integer itemCount;

    /** 多物料明细（详情/列表填充，非DB字段） */
    @TableField(exist = false)
    private List<LossReportItem> items;

    /** 列表摘要（如"牛油果等 3 种物料"，非DB字段） */
    @TableField(exist = false)
    private String itemNames;

    /** 最新操作（非DB字段） */
    @TableField(exist = false)
    private String latestLogAction;

    /** 最新操作备注（非DB字段） */
    @TableField(exist = false)
    private String latestLogRemark;

    /** 蓝蛙厂家复核状态（非DB字段）：空=非复核单；pending=厂家拒绝待总部复核；pass=复核通过；reject=复核不通过 */
    @TableField(exist = false)
    private String recheckState;

    @Version
    private Integer version;
}
