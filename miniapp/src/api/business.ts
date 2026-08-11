import { request } from '@/utils/request'

export interface KpiItem {
  value: number
  change: number
  changeRate: string   // "+8.6%" or "--"
  trend: 'up' | 'down' | 'flat'
}

export interface BusinessOverview {
  sales: KpiItem
  expense: KpiItem
  loss: KpiItem
}

export interface DailyPoint {
  date: string
  value: number
}

export interface BusinessTrend {
  metric: string
  caption: string
  points: DailyPoint[]
}

export interface BusinessReportSummary {
  yearMonth: string
  storeName: string
  storeId: string
}

export interface ReportIndicators {
  sales: number
  actualRevenue: number
  grossProfit: number
  grossProfitRate: number
  // 新增 — 月度经营指标
  theoryMaterialCost: number
  actualMaterialCost: number
  bookingRate: number
  productGrossProfitRate: number
  cashFlowRate: number
  lossRate: number
  materialCostRatio: number
  rentRatio: number
  laborRatio: number
}

export interface ChannelItem {
  channel: string           // 美团外卖 / 小程序
  gmv: number
  actualRevenue: number
  orderCount: number
}

export interface DailyItem {
  statDate: string          // YYYY-MM-DD
  gmv: number
  actualRevenue: number
  orderCount: number
  discountAmount: number
  refundAmount: number
  avgOrderValue: number
  bookingRate: number
  channels: ChannelItem[] | null
}

export interface ReportDetailItem {
  label: string
  value: number
  color?: string
  formula?: string     // 计算公式说明
}

export interface ReportCostItem {
  name: string
  amount: number
  ratio: number
  group?: string            // 下钻分组
}

export interface BusinessReportDetail {
  yearMonth: string
  storeName: string
  storeId: string
  indicators: ReportIndicators
  dailyItems: DailyItem[] | null   // 日营收 + 渠道拆分
  details: ReportDetailItem[]
  costStructure: ReportCostItem[]
}

export interface HomeOverview {
  yesterdaySales: number
  yesterdayExpense: number
}

export function fetchBusinessOverview(period = '30', scope = 'all') {
  return request<BusinessOverview>({ url: '/business/overview', data: { period, scope } })
}

export function fetchBusinessTrend(period = '30', metric = 'sales', scope = 'all') {
  return request<BusinessTrend>({ url: '/business/trend', data: { period, metric, scope } })
}

export function fetchBusinessReports(scope = 'all') {
  return request<BusinessReportSummary[]>({ url: '/business/reports', data: { scope } })
}

export function fetchBusinessReportDetail(yearMonth: string, storeId?: string) {
  return request<BusinessReportDetail>({ url: `/business/reports/${yearMonth}`, data: { storeId } })
}

/** 成本下钻 */
export function fetchCostBreakdown(yearMonth: string, storeId: string, group = 'operation', category?: string) {
  return request<any>({ url: `/business/reports/${yearMonth}/cost-breakdown`, data: { storeId, group, category } })
}

export function fetchHomeOverview(scope = 'all') {
  return request<HomeOverview>({ url: '/business/yesterday', data: { scope } })
}
