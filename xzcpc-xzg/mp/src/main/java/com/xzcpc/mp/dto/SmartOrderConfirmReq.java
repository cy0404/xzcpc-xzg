package com.xzcpc.mp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 智能订货确认请求：逐项提交确认数量，qty=0 表示本次不订 */
@Data
public class SmartOrderConfirmReq {

    @NotEmpty(message = "订货明细不能为空")
    @Valid
    private List<ConfirmItemReq> items;

    @Data
    public static class ConfirmItemReq {
        @NotNull(message = "明细ID不能为空")
        private Long itemId;
        @NotNull(message = "数量不能为空")
        private BigDecimal qty;
    }
}
