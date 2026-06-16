import api from './index'

export const ROLE_OPTIONS = [
  { value: 'headquarters_admin', label: '总部管理员' },
  { value: 'finance_admin', label: '财务负责人' },
  { value: 'hr_admin', label: '人事负责人' },
  { value: 'operation_admin', label: '运营负责人' },
  { value: 'normal_user', label: '普通用户' },
]

export function getRoleLabel(role: string) {
  return ROLE_OPTIONS.find((item) => item.value === role)?.label || '普通用户'
}

export function getCurrentPermission() {
  return api.get('/permissions/me')
}

export function getPermissionOverview() {
  return api.get('/permissions/overview')
}

export function getPermissions(params: {
  name?: string
  role?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/permissions', { params })
}

export function updatePermissionRole(id: number, roles: string[]) {
  return api.put(`/permissions/${id}/role`, { roles })
}
