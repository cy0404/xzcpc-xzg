package com.xzcpc.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ExpenseDashboardResp {

    private Summary summary;
    private List<StoreRanking> storeRanking;
    private List<TypeDistribution> typeDistribution;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private BigDecimal totalAmount;
        private String totalChange;
        private BigDecimal avgStoreAmount;
        private long storeCount;
        private String topStoreName;
        private BigDecimal topStoreAmount;
        private int voucherRate;
        private String voucherChange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreRanking {
        private String name;
        private BigDecimal amount;
        private int percent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeDistribution {
        private String name;
        private int percent;
    }
}
