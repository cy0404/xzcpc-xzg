package com.xzcpc.people.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class PeopleDashboardResp {

    private Summary summary;
    private List<StoreDistribution> storeDistribution;
    private List<RoleDistribution> roleDistribution;
    private List<TrendPoint> trend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long activeCount;
        private long storeCount;
        private long newHireCount;
        private String newHireChange;
        private long leaveCount;
        private String leaveNote;
        private double turnoverRate;
        private String turnoverNote;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreDistribution {
        private String name;
        private long count;
        private int percent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleDistribution {
        private String name;
        private int percent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String month;
        private long hire;
        private long leave;
    }
}
