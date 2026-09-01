package com.xzcpc.mp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MpExpenseSaveReq {

    @NotBlank(message = "支出类型不能为空")
    private String typeId;

    @NotNull(message = "支出金额不能为空")
    @DecimalMin(value = "0.01", message = "支出金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "支出日期不能为空")
    private LocalDate occurredDate;

    @NotBlank(message = "经手人不能为空")
    private String handlerName;

    private String voucherUrl;

    private String remark;

    // 自购食材物料明细（仅当支出类型为"自购食材"时填写）
    private String materialId;
    private String materialName;
    private String materialUnit;
    private String parentCategory;
    private String category;
    private BigDecimal weight;
    private BigDecimal unitPrice;

    // 自购食材多物料（一次登记最多 10 个，优先于上面单物料字段）
    private List<ItemReq> items;

    // 其他支出类型多明细（一次登记最多 10 条「名称+金额」；非空时 amount 取 Σ 明细）
    private List<AmountItem> amountItems;

    @Data
    public static class ItemReq {
        private String materialId;
        private String materialName;
        private String parentCategory;
        private String category;
        private BigDecimal weight;
        private BigDecimal unitPrice;
    }

    @Data
    public static class AmountItem {
        private String name;
        private BigDecimal amount;
    }
}
