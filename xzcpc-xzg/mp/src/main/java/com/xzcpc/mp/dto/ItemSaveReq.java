package com.xzcpc.mp.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ItemSaveReq {
    private String materialId;
    private BigDecimal qty;
    private String inputMode;
    private BigDecimal originalQty;
    private String originalUnit;
    private BigDecimal weightQuantity;
    private String weightUnit;
    private BigDecimal countQuantity;
    private String countUnit;
    private String remark;
    private String unitInputs;
}
