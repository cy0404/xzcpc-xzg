import api from './index'

/** 台账分页 */
export function getSupervisorVisits(params?: {
  storeId?: string
  supervisorName?: string
  visitStatus?: string
  confirmStatus?: string
  hasOverdue?: boolean
  visitDateStart?: string
  visitDateEnd?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/admin/supervisor-visit', { params })
}

/** 拜访单详情 */
export function getSupervisorVisitDetail(id: number) {
  return api.get(`/admin/supervisor-visit/${id}`)
}

/** 创建拜访单 */
export function createSupervisorVisit(data: any) {
  return api.post('/admin/supervisor-visit', data)
}

/** 编辑拜访单 */
export function updateSupervisorVisit(id: number, data: any) {
  return api.put(`/admin/supervisor-visit/${id}`, data)
}

/** 提交确认 */
export function submitSupervisorVisit(id: number) {
  return api.post(`/admin/supervisor-visit/${id}/submit`)
}

/** 处理异议后重新提交 */
export function handleObjection(id: number, data: any) {
  return api.post(`/admin/supervisor-visit/${id}/handle-objection`, data)
}

/** 门店经营数据 + 历史拜访 */
export function getStoreBizData(storeId: string) {
  return api.get(`/admin/supervisor-visit/store-data/${storeId}`)
}

/** 行动计划列表 */
export function getVisitActions(id: number) {
  return api.get(`/admin/supervisor-visit/${id}/actions`)
}

/** 审核通过 */
export function approveAction(actionId: number) {
  return api.post(`/admin/supervisor-visit/actions/${actionId}/approve`)
}

/** 审核退回 */
export function rejectAction(actionId: number, returnReason?: string, newTrackingTime?: string) {
  return api.post(`/admin/supervisor-visit/actions/${actionId}/reject`, null, {
    params: { returnReason, newTrackingTime }
  })
}

/** 可选的督导列表 */
export function getSupervisorOptions() {
  return api.get('/admin/supervisor-visit/supervisor-options')
}

/** 督导负责的门店列表（按 supervisor_name 过滤） */
export function getSupervisorStores() {
  return api.get('/admin/supervisor-visit/supervisor-stores')
}

/** 门店员工列表（确认人候选） */
export function getStoreEmployees(storeId: string) {
  return api.get(`/admin/supervisor-visit/store-employees/${storeId}`)
}
