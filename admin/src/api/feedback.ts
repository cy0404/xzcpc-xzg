import api from './index'

export function getFeedbackOptions() {
  return api.get('/admin/feedback/options')
}

export function getFeedbackList(params?: {
  feedbackType?: string
  channel?: string
  storeId?: string
  storeName?: string
  status?: string
  keyword?: string
  startDate?: string
  endDate?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/admin/feedback/list', { params })
}

export function getFeedbackDetail(id: number) {
  return api.get(`/admin/feedback/${id}`)
}

export function updateFeedbackStatus(id: number, data: { status: string; processNote?: string }) {
  return api.post(`/admin/feedback/${id}/status`, data)
}
