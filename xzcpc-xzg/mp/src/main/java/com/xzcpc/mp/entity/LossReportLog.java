package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("loss_report_log")
public class LossReportLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private String action;
    private String operator;
    private String remark;
    private String attachmentUrl;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
