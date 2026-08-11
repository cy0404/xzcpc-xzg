package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("report_visibility_config")
public class ReportVisibilityConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String storeId;
    private String statMonth;   // yyyy-MM
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
