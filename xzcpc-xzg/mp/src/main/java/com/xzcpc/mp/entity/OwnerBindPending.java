package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("owner_bind_pending")
public class OwnerBindPending {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String storeId;
    private String openid;
    private String name;
    private String mobile;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
