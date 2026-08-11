import api from './index'

export function getTransferOrders(params?: {
  fromStoreId?: string
  toStoreId?: string
  status?: string
  keyword?: string
  startDate?: string
  endDate?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/admin/transfer/list', { params })
}

export function getTransferOrderDetail(id: number) {
  return api.get(`/admin/transfer/${id}`)
}

export function exportTransferOrders(params?: {
  fromStoreId?: string
  toStoreId?: string
  status?: string
  keyword?: string
  startDate?: string
  endDate?: string
}) {
  return api.get('/admin/transfer/export', { params, responseType: 'blob' })
}
