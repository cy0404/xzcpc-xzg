import api from './index'

export function getEmployees(params?: {
  storeId?: string
  role?: string
  status?: string
  name?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/employees', { params })
}

export function getEmployeeDetail(id: string | number) {
  return api.get(`/employees/${id}`)
}

export function getPeopleDashboard(params?: { range?: string }) {
  return api.get('/employees/dashboard', { params })
}
