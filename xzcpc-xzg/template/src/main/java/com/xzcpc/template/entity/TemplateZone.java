package com.xzcpc.template.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 模板分区实体，关联模板与分区
@Data
@TableName("template_zone")
public class TemplateZone {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bizCode;
    private Integer zoneId;
    private Integer templateId;
    private String zoneName;
    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Integer materialCount;

    @TableField(exist = false)
    private List<Map<String, Object>> materials;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
