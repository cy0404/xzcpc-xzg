package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.BusinessOverviewResp;
import com.xzcpc.mp.dto.BusinessTrendResp;
import com.xzcpc.mp.dto.BusinessReportSummaryResp;
import com.xzcpc.mp.dto.BusinessReportDetailResp;
import com.xzcpc.mp.dto.HomeOverviewResp;

import java.util.List;
import java.util.Map;

public interface MpBusinessService {

    /**
     * 经营概览 KPI（销售额、支出、订货及环比变化）
     * @param storeIds 门店ID列表（单门店传一个，全部门店传多个）
     * @param period   时间周期：7 / 30 / month
     */
    BusinessOverviewResp getOverview(List<String> storeIds, String period);

    /**
     * 趋势图每日数据
     * @param storeIds 门店ID列表
     * @param period   时间周期
     * @param metric   指标：sales / expense / order
     */
    BusinessTrendResp getTrend(List<String> storeIds, String period, String metric);

    /**
     * 财报列表（每月每店一期）
     * @param storeIds 门店ID列表
     */
    List<BusinessReportSummaryResp> getReports(List<String> storeIds);

    /**
     * 财报详情
     * @param storeId   单门店ID
     * @param yearMonth 月份 yyyy-MM
     */
    BusinessReportDetailResp getReportDetail(String storeId, String yearMonth);

    HomeOverviewResp getYesterday(List<String> storeIds);

    /**
     * 成本下钻明细
     * @param storeId   门店ID（store_info.store_id）
     * @param yearMonth 月份 yyyy-MM
     * @param group     下钻分组：operation / food / labor / energy / rent
     */
    Map<String, Object> getCostBreakdown(String storeId, String yearMonth, String group, String category);
}
