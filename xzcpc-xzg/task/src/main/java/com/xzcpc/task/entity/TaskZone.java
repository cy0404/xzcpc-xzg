package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_zone")
public class TaskZone { // 任务分区快照实体，创建任务时从模板分区复制

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bizCode;
    private Integer taskZoneId;
    private Integer taskId;
    private String zoneName;
    private Integer sortNo;
    private String sourceType;
    private Integer zoneSaved;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
