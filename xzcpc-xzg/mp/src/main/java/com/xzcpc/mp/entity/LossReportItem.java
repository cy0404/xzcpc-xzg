package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 日常报损明细（多物料）
 */
@Data
@TableName("loss_report_item")
public class LossReportItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;
    private String lossObject;     // finished|semi_finished
    private String materialId;
    private String materialName;
    private String spec;
    private String inputUnit;
    private BigDecimal inputQty;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String baseUnit;
    private BigDecimal baseQty;
    private BigDecimal grossWeight;
    private Long containerId;
    private String containerName;
    private BigDecimal containerWeight;
    private BigDecimal netWeight;
    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
