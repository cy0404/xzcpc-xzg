package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("store_work_hours")
public class StoreWorkHours {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String recordId;
    private String storeId;
    private String storeName;

    private String recordTime;
    private BigDecimal hours;
    private String employeeId;
    private String employeeName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
