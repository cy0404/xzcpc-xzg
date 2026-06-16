package com.xzcpc.mp.dto;

import lombok.Data;

import java.util.List;

@Data
public class OwnerDashboardResp {
    private String ownerName;
    private int storeCount;
    private String totalExpense;
    private int totalStaff;
    private List<StoreSummary> stores;

    @Data
    public static class StoreSummary {
        private String storeId;
        private String storeName;
        private String manager;
        private int staffCount;
        private String expense;
    }
}
