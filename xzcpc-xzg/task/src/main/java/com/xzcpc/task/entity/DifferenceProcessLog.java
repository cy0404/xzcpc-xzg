package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * P0 A1: 差异处理日志
 */
@Data
@TableName("difference_process_log")
public class DifferenceProcessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long diffId;
    private String action;      // processing|adjust|close|convert
    private String operator;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
