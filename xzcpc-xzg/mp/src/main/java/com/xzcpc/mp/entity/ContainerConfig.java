package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("container_config")
public class ContainerConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String containerName;
    private String alias;          // 别称，胶囊展示用
    private BigDecimal tareWeight;
    private String image;          // 容器图片URL
    private String storeId;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
