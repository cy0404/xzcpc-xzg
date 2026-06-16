package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("task_zone_material")
public class TaskZoneMaterial { // 任务分区物料快照实体，记录录入数量与状态

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bizCode;
    private Integer taskZoneMaterialId;
    private Integer taskId;
    private Integer taskZoneId;
    private String materialId;
    private String materialName;
    private String spec;
    private String unit;
    private String inventoryUnit;
    private Integer sortNo;
    private BigDecimal inputQty;
    private String remark;
    private String inputStatus;
    private String inputMode;
    private BigDecimal inputOriginalQty;
    private String inputOriginalUnit;
    private String baseUnitSnapshot;
    private String ruleIdSnapshot;
    private BigDecimal baseQty;
    private String conversionSnapshot;
    private BigDecimal unitPriceSnapshot;
    private String unitInputs;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
