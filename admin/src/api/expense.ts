import api from './index'

export function getExpenseRecords(params?: {
  storeId?: string
  typeId?: string
  startDate?: string
  endDate?: string
  handlerName?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/expenses', { params })
}

export function getExpenseDashboard(params?: { range?: string }) {
  return api.get('/expenses/dashboard', { params })
}

export function getExpenseTypes(params?: { status?: string }) {
  return api.get('/expense-types', { params })
}

export function createExpenseType(data: { name: string; description?: string; status: string }) {
  return api.post('/expense-types', data)
}

export function updateExpenseType(typeId: string, data: { name: string; description?: string; status: string }) {
  return api.put(`/expense-types/${typeId}`, data)
}

export function deleteExpenseType(typeId: string) {
  return api.delete(`/expense-types/${typeId}`)
}
