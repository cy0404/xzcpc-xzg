package com.xzcpc.template.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

// 模板实体，对应 template 表
@Data
@TableName("template")
public class Template {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bizCode;
    private String templateName;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
