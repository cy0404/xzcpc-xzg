package com.xzcpc.mp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DailyLossCreateReq {
    @NotBlank(message = "请选择报损原因")
    private String reason;

    private String remark;

    /** 逗号分隔的图片URL，所有物料共用 */
    private String voucherUrl;

    @NotEmpty(message = "请至少添加一个报损物料")
    @Valid
    private List<ItemReq> items;

    @Data
    public static class ItemReq {
        /** finished|semi_finished，默认 semi_finished */
        private String lossObject;

        @NotBlank(message = "物料ID不能为空")
        private String materialId;

        @NotBlank(message = "物料名称不能为空")
        private String materialName;

        private String spec;

        // ---- finished 成品报损 ----
        private String inputUnit;
        private BigDecimal inputQty;

        // ---- semi_finished 半成品称重去皮 ----
        private BigDecimal grossWeight;
        private Long containerId;

        /** 物料单价（未配置则为 null） */
        private BigDecimal unitPrice;
    }
}
