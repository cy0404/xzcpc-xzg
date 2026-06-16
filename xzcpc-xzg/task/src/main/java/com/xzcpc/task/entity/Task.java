package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task { // 月盘任务实体

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bizCode;
    private Integer taskId;
    private String storeId;
    private String taskName;
    private String taskMonth;
    private LocalDateTime deadline;
    private String status;
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer templateId;
    private String submittedBy;
    private LocalDateTime submittedAt;

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

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
