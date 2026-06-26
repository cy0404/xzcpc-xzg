package com.xzcpc.mp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkHoursSaveReq {

    @NotBlank(message = "请选择年月")
    private String recordTime;

    @NotNull(message = "请输入工时")
    @DecimalMin(value = "0", message = "工时不能为负数")
    private BigDecimal hours;
}
