import api from './index'

export function getLossManageList(params: {
  storeId?: string; lossType?: string; status?: string
  startDate?: string; endDate?: string
  pageNum?: number; pageSize?: number
}) { return api.get('/admin/loss-report/list', { params }) }

export function getLossContainers(storeId?: string) {
  return api.get('/admin/loss-report/containers', { params: { storeId } })
}

export function addContainer(data: any) { return api.post('/admin/loss-report/containers', data) }

export function updateContainer(id: number, data: any) { return api.put(`/admin/loss-report/containers/${id}`, data) }

export function exportLossReport(params: Record<string, any>) {
  // 导出数据量大，放宽超时（实例默认 30s）
  return api.get('/admin/loss-report/export', { params, responseType: 'blob', timeout: 120000 })
}

export function getLossReportDetail(id: number) { return api.get(`/admin/loss-report/${id}`) }

export function confirmLossReport(id: number) { return api.post(`/admin/loss-report/${id}/confirm`) }

export function rejectLossReport(id: number, reason: string) { return api.post(`/admin/loss-report/${id}/reject`, { reason }) }

export function getLossDashboard(params?: {
  storeId?: string; supervisorName?: string; lossType?: string; startDate?: string; endDate?: string
}) { return api.get('/admin/loss-report/dashboard', { params }) }

export function getStandards(params?: { standardType?: string; materialId?: string; pageNum?: number; pageSize?: number }) {
  return api.get('/admin/loss-report/standards', { params })
}
export function createStandard(data: any) { return api.post('/admin/loss-report/standards', data) }
export function updateStandard(id: number, data: any) { return api.put(`/admin/loss-report/standards/${id}`, data) }
export function deleteStandard(id: number) { return api.delete(`/admin/loss-report/standards/${id}`) }
