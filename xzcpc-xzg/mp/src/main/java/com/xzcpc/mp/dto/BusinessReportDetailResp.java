package com.xzcpc.mp.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BusinessReportDetailResp {
    private String yearMonth;
    private String storeName;
    private String storeId;
    private Indicators indicators;
    private List<DailyItem> dailyItems;          // 日营收 + 渠道拆分
    private List<DetailItem> details;
    private List<CostItem> costStructure;

    @Data
    public static class Indicators {
        // 已有
        private BigDecimal sales;                  // GMV
        private BigDecimal actualRevenue;          // 实收
        private BigDecimal grossProfit;            // 毛利
        private BigDecimal grossProfitRate;        // 毛利率
        // 新增 — 月度经营指标
        private BigDecimal theoryMaterialCost;     // 理论物料成本
        private BigDecimal actualMaterialCost;     // 实际物料成本
        private BigDecimal bookingRate;            // 核销率 = 实收 / gmv
        private BigDecimal productGrossProfitRate; // 产品毛利率（OpenAPI 口径）
        private BigDecimal cashFlowRate;           // 现金流率
        private BigDecimal lossRate;               // 损耗率
        private BigDecimal materialCostRatio;      // 物料成本占比
        private BigDecimal rentRatio;              // 租金占比
        private BigDecimal laborRatio;             // 人工占比
        private BigDecimal cashFlow;               // 现金流
        private BigDecimal netProfit;              // 净利润
        private BigDecimal netProfitRate;          // 净利率
    }

    @Data
    public static class DailyItem {
        private String statDate;                   // 日期 YYYY-MM-DD
        private BigDecimal gmv;
        private BigDecimal actualRevenue;
        private Integer orderCount;
        private BigDecimal discountAmount;
        private BigDecimal refundAmount;
        private BigDecimal avgOrderValue;          // 客单价
        private BigDecimal bookingRate;            // 当日核销率
        private List<ChannelItem> channels;        // 渠道拆分
    }

    @Data
    public static class ChannelItem {
        private String channel;                    // 美团外卖 / 小程序
        private BigDecimal gmv;
        private BigDecimal actualRevenue;
        private Integer orderCount;
    }

    @Data
    public static class DetailItem {
        private String label;
        private BigDecimal value;
        private String color;   // "primary" | "danger" | "default"
        private String formula; // 计算公式说明
    }

    @Data
    public static class CostItem {
        private String name;        // "食材成本" etc.
        private BigDecimal amount;
        private BigDecimal ratio;   // 成本占比 %
        private String group;       // 下钻分组：operation / food / labor / energy / rent
    }
}
