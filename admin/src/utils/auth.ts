import { ref } from 'vue'

const TOKEN_KEY = 'admin_token'
const USER_KEY = 'admin_user'

export interface AdminUser {
  openId?: string
  name: string
  avatarUrl: string
  email: string
  mobile: string
  role?: string
  roleName?: string
  hasAdminAccess?: boolean
}

const ADMIN_ROLES = ['headquarters_admin', 'finance_admin', 'hr_admin', 'operation_admin']

export const authRevision = ref(0)

/** 保存 token 和用户信息 */
export function setAuth(token: string, user: AdminUser) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  authRevision.value += 1
}

/** 获取 token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 获取当前用户信息 */
export function getUser(): AdminUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AdminUser
  } catch {
    return null
  }
}

/** 清除登录状态 */
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  authRevision.value += 1
}

/** 是否已登录 */
export function isLoggedIn(): boolean {
  return !!getToken()
}

/** 是否具备总部后台管理员权限 */
export function hasAdminAccess(): boolean {
  return parseRoles(getUser()?.role).some(role => ADMIN_ROLES.includes(role))
}

/** 解析角色字符串为数组 */
export function parseRoles(role?: string): string[] {
  if (!role) return ['normal_user']
  return role.split(',').map(r => r.trim()).filter(r => r)
}

/** 判断当前用户是否拥有某个角色 */
export function hasRole(roleName: string): boolean {
  return parseRoles(getUser()?.role).includes(roleName)
}

/** 是否本地开发环境 */
export function isDev(): boolean {
  if (import.meta.env.DEV) return true
  const host = window.location.hostname
  if (host === 'localhost' || host === '127.0.0.1' || host === '[::1]') return true
  if (host.startsWith('192.168.')) return true
  if (host.startsWith('10.')) return true
  if (/^172\.(1[6-9]|2\d|3[01])\./.test(host)) return true
  return false
}

/** 开发环境 Mock 登录（管理员） */
export async function devLogin(): Promise<boolean> {
  try {
    const res = await fetch('/api/auth/dev/login', { method: 'POST' })
    const data = await res.json()
    if (data.code === 200) {
      const { token, openId, name, avatarUrl, email, mobile, role, roleName, hasAdminAccess } = data.data
      setAuth(token, { openId, name, avatarUrl, email, mobile, role, roleName, hasAdminAccess })
      return true
    }
    return false
  } catch {
    return false
  }
}

/** 开发环境 Mock 登录（普通用户） */
export async function devLoginNormal(): Promise<boolean> {
  try {
    const res = await fetch('/api/auth/dev/login/normal', { method: 'POST' })
    const data = await res.json()
    if (data.code === 200) {
      const { token, openId, name, avatarUrl, email, mobile, role, roleName, hasAdminAccess } = data.data
      setAuth(token, { openId, name, avatarUrl, email, mobile, role, roleName, hasAdminAccess })
      return true
    }
    return false
  } catch {
    return false
  }
}
