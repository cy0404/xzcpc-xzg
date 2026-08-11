package com.xzcpc.mp.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BusinessOverviewResp {
    private KpiItem sales;
    private KpiItem expense;
    private KpiItem loss;       // 日常报损（原订货）

    @Data
    public static class KpiItem {
        private BigDecimal value;
        private BigDecimal change;
        private String changeRate;   // "+8.6%" or "--"
        private String trend;        // "up" | "down" | "flat"
    }
}
