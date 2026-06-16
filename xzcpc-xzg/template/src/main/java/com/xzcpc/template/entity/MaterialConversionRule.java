package com.xzcpc.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("material_conversion_rule")
public class MaterialConversionRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleId;
    private String conversionType;
    private BigDecimal fromQuantity;
    private String fromUnit;
    private BigDecimal toQuantity;
    private String toUnit;
    private Integer sortNo;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
