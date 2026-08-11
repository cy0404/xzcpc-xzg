package com.xzcpc.mp.dto;

import lombok.Data;

@Data
public class BusinessReportSummaryResp {
    private String yearMonth;   // "2026-06"
    private String storeName;
    private String storeId;
    // 不展示经营结余，仅保留字段供未来使用
}
