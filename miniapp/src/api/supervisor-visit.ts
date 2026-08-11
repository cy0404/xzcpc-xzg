import { request } from '@/utils/request'

export interface SupervisorVisit {
  id: number
  visitNo: string
  storeId: string
  storeName: string
  supervisorName: string
  visitDate: string
  confirmPersonType: string
  visitStatus: string
  confirmStatus: string
  confirmedBy: string
  confirmedAt: string
  objectionReason: string
  lastIssueReview: string
  currentFocus: string
  improvementFocus: string
  storeFeedback: string
  bizDataSnapshot: string
  createdAt: string
  updatedAt: string
}

export interface SupervisorVisitAction {
  id: number
  visitId: number
  actionName: string
  targetValue: string
  specificAction: string
  trackingTime: string
  responsiblePerson: string
  responsibleRole: string
  taskStatus: string
  completeNote: string
  completeImages: string
  submittedAt: string
  reviewResult: string
  returnReason: string
  reviewedBy: string
  reviewedAt: string
  visitNo?: string
  storeName?: string
}

/** 待确认拜访单列表 */
export function fetchPendingVisits() {
  return request<SupervisorVisit[]>({ url: '/supervisor-visit/pending', showLoading: false })
}

/** 拜访单详情 */
export function fetchVisitDetail(id: number) {
  return request<{ visit: SupervisorVisit; actions: SupervisorVisitAction[]; bizData: any; historyVisits: SupervisorVisit[] }>(
    { url: `/supervisor-visit/${id}`, showLoading: false }
  )
}

/** 确认拜访单 */
export function confirmVisit(id: number) {
  return request<SupervisorVisit>({ url: `/supervisor-visit/${id}/confirm`, method: 'POST' })
}

/** 提出异议 */
export function objectVisit(id: number, reason: string) {
  return request<SupervisorVisit>({ url: `/supervisor-visit/${id}/object`, method: 'POST', data: { reason } })
}

/** 我的拜访任务列表 */
export function fetchMyActions(status?: string, pageNum?: number, pageSize?: number) {
  return request<{ records: SupervisorVisitAction[]; total: number }>(
    { url: '/supervisor-visit/actions', data: { status, pageNum, pageSize }, showLoading: false }
  )
}

/** 拜访任务详情 */
export function fetchActionDetail(id: number) {
  return request<SupervisorVisitAction>({ url: `/supervisor-visit/actions/${id}`, showLoading: false })
}

/** 提交完成反馈 */
export function completeAction(id: number, note: string, images?: string) {
  return request({ url: `/supervisor-visit/actions/${id}/complete`, method: 'POST', data: { note, images } })
}

/** 首页概览：待确认数 + 待处理任务数 */
export interface TaskItem {
  id: number
  actionName: string
  trackingTime: string
  taskStatus: string
  visitNo: string
  storeName: string
}

export function fetchSupervisorVisitOverview() {
  return request<{ pendingConfirm: number; pendingTasks: number; firstPendingVisitId?: number; firstPendingActionId?: number; taskItems?: TaskItem[] }>(
    { url: '/supervisor-visit/overview', showLoading: false }
  )
}
