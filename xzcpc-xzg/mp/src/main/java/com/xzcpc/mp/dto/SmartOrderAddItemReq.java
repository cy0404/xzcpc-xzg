package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 智能订货新增物料请求：materialId 为 material 主键（BIGINT），数量按库存单位整数 */
@Data
public class SmartOrderAddItemReq {

    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    @NotNull(message = "数量不能为空")
    private BigDecimal qty;
}
