package com.xzcpc.task.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialUpdateReq {
    private Integer id;
    private BigDecimal inputQty;
    private BigDecimal baseQty;
    private String unitInputs;
}
