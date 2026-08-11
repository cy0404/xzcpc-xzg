package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("difference_modify_log")
public class DifferenceModifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long diffId;
    private Integer taskId;
    private String storeId;
    private String storeName;
    private String materialId;
    private String materialName;
    private BigDecimal initialQty;
    private BigDecimal oldQty;
    private BigDecimal newQty;
    private String operator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
