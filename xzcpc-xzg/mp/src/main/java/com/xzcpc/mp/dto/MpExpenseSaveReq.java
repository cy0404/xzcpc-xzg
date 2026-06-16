package com.xzcpc.mp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MpExpenseSaveReq {

    @NotBlank(message = "支出类型不能为空")
    private String typeId;

    private String typeName;

    private String itemId;

    private String itemName;

    @NotNull(message = "支出金额不能为空")
    @DecimalMin(value = "0.01", message = "支出金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "支出日期不能为空")
    private LocalDate occurredDate;

    @NotBlank(message = "经手人不能为空")
    private String handlerName;

    private String voucherUrl;

    private String remark;
}
