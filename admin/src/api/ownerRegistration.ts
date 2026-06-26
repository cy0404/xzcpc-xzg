import api from './index'

export interface OwnerRegistration {
  id: number
  openid: string
  bindCode?: string
  name: string
  phone: string
  storeId: string
  storeName: string
  role: string
  status: string
  createdAt: string
}

export function getOwnerRegistrations(params?: {
  storeId?: string
  status?: string
  name?: string
  phone?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/owner-registrations', { params })
}

export function bindOwnerRegistration(id: number, data: { name: string; phone: string; role: string }) {
  return api.post(`/owner-registrations/${id}/bind`, data)
}

export function updateOwnerRegistration(id: number, data: { name: string; phone: string; role: string }) {
  return api.put(`/owner-registrations/${id}`, data)
}
