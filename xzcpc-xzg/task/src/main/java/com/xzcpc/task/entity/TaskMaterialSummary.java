package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("task_material_summary")
public class TaskMaterialSummary { // 物料跨分区汇总实体，提交时自动汇总

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer taskId;
    private String materialId;
    private String materialName;
    private String spec;
    private String baseUnit;
    private BigDecimal totalQty;
    private Integer zoneCount;
    private String unitBreakdown;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
