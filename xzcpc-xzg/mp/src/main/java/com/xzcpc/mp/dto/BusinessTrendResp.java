package com.xzcpc.mp.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BusinessTrendResp {
    private String metric;
    private String caption;
    private List<DailyPoint> points;

    @Data
    public static class DailyPoint {
        private String date;     // "6/24"
        private BigDecimal value;
    }
}
