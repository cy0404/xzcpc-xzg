package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("store_manager_session")
public class StoreManagerSession {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String openid;
    private String unionid;
    private String sessionKey;
    private String wxNickname;
    private String storeId;
    private String storeName;
    private String token;
    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
