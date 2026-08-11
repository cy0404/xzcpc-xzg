package com.xzcpc.mp.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 还货/还钱请求
 */
@Data
public class TransferReturnReq {
    /** 操作类型: goods(还货)|money(还钱)|bulk_goods(全部还货)|bulk_money(全部还钱) */
    private String action;

    /** 逐项物料明细(非 bulk 操作时必填) */
    private List<ReturnItem> items;

    /** 备注 */
    private String remark;

    @Data
    public static class ReturnItem {
        private Long itemId;
        private String returnType; // goods|money
        private BigDecimal returnQty;
        private BigDecimal returnAmount;
    }
}
