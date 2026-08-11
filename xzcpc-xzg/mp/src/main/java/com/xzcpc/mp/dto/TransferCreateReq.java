package com.xzcpc.mp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class TransferCreateReq {
    @NotBlank(message = "调出地不能为空")
    private String fromStoreId;
    private String fromStoreName;
    @NotBlank(message = "调入地不能为空")
    private String toStoreId;
    private String toStoreName;
    private String remark;

    @NotEmpty(message = "调货物料不能为空")
    @Valid
    private List<TransferItemReq> items;

    @Data
    public static class TransferItemReq {
        @NotBlank(message = "物料名称不能为空")
        private String materialName;
        private String spec;
        @NotBlank(message = "单位不能为空")
        private String unit;
        @NotNull(message = "数量不能为空")
        private BigDecimal transferQty;
        private String baseUnit;
        private BigDecimal baseQty;
        private String inputUnit;
        private BigDecimal inputQty;
        private BigDecimal unitPrice;
        private String remark;
    }
}
