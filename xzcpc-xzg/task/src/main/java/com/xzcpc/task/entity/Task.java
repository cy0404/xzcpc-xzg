package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task { // 盘点任务实体（月盘/周盘）

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bizCode;
    private String storeId;
    private String taskName;
    private String taskMonth;

    /** 任务类型: monthly月盘|weekly周盘 */
    private String taskType;

    /** 盘点周 YYYY-Www（仅周盘） */
    private String taskWeek;

    private LocalDateTime deadline;
    private String status;
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer templateId;
    private String submittedBy;
    private LocalDateTime submittedAt;

    /** 盘点金额：提交时自动计算 sum(baseQty × 单价) */
    private BigDecimal totalAmount;

    private String storeName;
    private String storeCode;
    private String xiaochengxuid;
    private String warehouseCode;

    @TableField(exist = false)
    private String templateName;

    @TableField(exist = false)
    private Integer zoneCount;

    @TableField(exist = false)
    private Integer materialCount;

    @TableField(exist = false)
    private String supervisorName;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
