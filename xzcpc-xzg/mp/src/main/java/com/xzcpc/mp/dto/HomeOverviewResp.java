package com.xzcpc.mp.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class HomeOverviewResp {
    private BigDecimal yesterdaySales;
    private BigDecimal yesterdayExpense;
}
