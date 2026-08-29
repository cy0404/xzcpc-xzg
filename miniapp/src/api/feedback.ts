import { request } from '@/utils/request'

export interface FeedbackStoreOverview {
  storeId: string
  storeName: string
  pending: number
}

export interface FeedbackOverview {
  pending: number
}

/** 各店未处理客诉数（首页全部门店视图用） */
export function fetchFeedbackOverviewStores() {
  return request<FeedbackStoreOverview[]>({ url: '/feedback/overview-stores', showLoading: false, silent: true })
}

/** 未处理客诉数：all=true 跨店总数，否则当前门店（首页卡片显隐用） */
export function fetchFeedbackOverview(all?: boolean) {
  return request<FeedbackOverview>({ url: '/feedback/overview', data: all ? { all: true } : undefined, showLoading: false, silent: true })
}
