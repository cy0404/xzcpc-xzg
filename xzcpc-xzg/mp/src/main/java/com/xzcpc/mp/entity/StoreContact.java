package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("store_contact")
public class StoreContact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String storeId;
    private String storeName;
    private String contactName;
    private String contactPhone;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
