package com.xzcpc.people.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("owner_registration")
public class OwnerRegistration {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String bindCode;
    private String name;
    private String phone;
    private String storeId;
    private String storeName;
    private String role;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
