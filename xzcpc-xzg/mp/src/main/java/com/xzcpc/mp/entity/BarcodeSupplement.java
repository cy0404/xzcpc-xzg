package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * P0 A1: 条码补充申请
 */
@Data
@TableName("barcode_supplement")
public class BarcodeSupplement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String barcode;
    private String materialName;
    private String storeId;
    private String submittedBy;
    private String status;  // pending|processed|rejected
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
