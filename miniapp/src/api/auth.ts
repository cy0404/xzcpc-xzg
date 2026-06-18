import { request } from '@/utils/request'

export function wxLogin(code: string, wxNickname = '') {
  return request({ url: '/auth/wx/login', method: 'POST', data: { code, wxNickname } })
}

export function bindStore(storeId: string) {
  return request({ url: '/auth/bind-store', method: 'POST', data: { storeId } })
}

export function logout() {
  return request({ url: '/auth/logout', method: 'POST' })
}

export function fetchMe() {
  return request({ url: '/auth/me' })
}

export function switchStore(storeId: string) {
  return request({ url: '/auth/switch-store', method: 'POST', data: { storeId } })
}

export function fetchOwnerDashboard() {
  return request({ url: '/owner/dashboard' })
}

export function fetchMyStores() {
  return request({ url: '/auth/stores' })
}

export function fetchOwnerMyStatus(wxCode: string) {
  return request({ url: '/auth/owner/my-status', data: { wxCode }})
}

export function fetchOwnerLatestApplication() {
  return request({ url: '/auth/owner/latest-application' })
}

export function queryOwnerStores(bindCode: string, wxCode: string, name: string, phone: string) {
  return request({ url: '/auth/owner/query-stores', method: 'POST', data: { bindCode, wxCode, name, phone } })
}

export function confirmOwnerBind(data: { bindCode: string; wxCode: string; name: string; phone: string; storeIds: string[] }) {
  return request({ url: '/auth/owner/confirm-bind', method: 'POST', data })
}
