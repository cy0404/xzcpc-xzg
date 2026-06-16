package com.xzcpc.template.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

// 模板分区物料关系实体，关联分区与物料
@Data
@TableName("template_zone_material")
public class TemplateZoneMaterial {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bizCode;
    private Integer zoneId;
    private String materialId;
    private String materialName;
    private String spec;
    private String inventoryUnit;
    private Integer sortNo;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
