import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken, getUser, setAuth, isLoggedIn, isDev, devLogin, hasAdminAccess, hasRole } from '../utils/auth'

const FEISHU_LOGIN_SYNC_KEY = 'feishu_login_synced'

/** 获取用户有权限访问的第一个页面路径 */
function defaultRoute(): string {
  if (hasRole('headquarters_admin')) return '/tasks'
  if (hasRole('operation_admin')) return '/tasks'
  if (hasRole('finance_admin')) return '/expense'
  if (hasRole('hr_admin')) return '/people'
  return '/no-permission'
}

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: () => defaultRoute(),
    },
    {
      path: '/no-permission',
      name: 'NoPermission',
      component: () => import('../views/NoPermission.vue'),
    },
    {
      path: '/templates',
      name: 'Templates',
      component: () => import('../views/template/TemplateList.vue'),
      meta: { roles: ['headquarters_admin', 'operation_admin'] },
    },
    {
      path: '/templates/:id',
      name: 'TemplateDetail',
      component: () => import('../views/template/TemplateDetail.vue'),
      meta: { roles: ['headquarters_admin', 'operation_admin'] },
    },
    {
      path: '/materials',
      name: 'MaterialManagement',
      component: () => import('../views/material/MaterialManagement.vue'),
      meta: { roles: ['headquarters_admin', 'operation_admin'] },
    },
    {
      path: '/tasks',
      name: 'Tasks',
      component: () => import('../views/task/TaskList.vue'),
      meta: { roles: ['headquarters_admin', 'operation_admin'] },
    },
    {
      path: '/tasks/create',
      name: 'TaskCreate',
      component: () => import('../views/task/TaskCreate.vue'),
      meta: { roles: ['headquarters_admin', 'operation_admin'] },
    },
    {
      path: '/tasks/:id/result',
      name: 'TaskResult',
      component: () => import('../views/task/TaskResult.vue'),
      meta: { roles: ['headquarters_admin', 'operation_admin'] },
    },
    {
      path: '/settings',
      name: 'SystemSettings',
      component: () => import('../views/settings/SystemSettings.vue'),
      meta: { roles: ['headquarters_admin'] },
    },
    {
      path: '/expense',
      name: 'Expense',
      component: () => import('../views/expense/ExpenseList.vue'),
      meta: { roles: ['headquarters_admin', 'finance_admin'] },
    },
    {
      path: '/expense/dashboard',
      name: 'ExpenseDashboard',
      component: () => import('../views/expense/ExpenseDashboard.vue'),
      meta: { roles: ['headquarters_admin', 'finance_admin'] },
    },
    {
      path: '/expense/types',
      name: 'ExpenseTypes',
      component: () => import('../views/expense/ExpenseTypeManagement.vue'),
      meta: { roles: ['headquarters_admin', 'finance_admin'] },
    },
    {
      path: '/people',
      name: 'People',
      component: () => import('../views/people/EmployeeList.vue'),
      meta: { roles: ['headquarters_admin', 'hr_admin'] },
    },
    {
      path: '/people/dashboard',
      name: 'PeopleDashboard',
      component: () => import('../views/people/EmployeeDashboard.vue'),
      meta: { roles: ['headquarters_admin', 'hr_admin'] },
    },
    {
      path: '/people/:id',
      name: 'EmployeeDetail',
      component: () => import('../views/people/EmployeeDetail.vue'),
      meta: { roles: ['headquarters_admin', 'hr_admin'] },
    },
    {
      path: '/reports',
      name: 'Reports',
      redirect: () => defaultRoute(),
    },
  ],
})

function getFeishuRedirectUrl() {
  const cleanHash = window.location.hash.replace(/[?&]code=[^&]+/, '').replace(/\?$/, '')
  return window.location.origin + window.location.pathname + (cleanHash || '#/tasks')
}

function isFeishuLaunch() {
  const search = window.location.search
  const userAgent = navigator.userAgent || ''
  return !!search.match(/lang=zh-CN|open_in_browser|from=|tenant_key=|app_id=/i)
    || /Lark|Feishu/i.test(userAgent)
}

/** 生产环境：用飞书 code 换 JWT */
async function feishuLogin(code: string): Promise<boolean> {
  try {
    const res = await fetch('/api/auth/feishu/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, redirectUri: getFeishuRedirectUrl() }),
    })
    const data = await res.json()
    if (data.code === 200) {
      const { token, openId, name, avatarUrl, email, mobile, role, roleName, hasAdminAccess } = data.data
      setAuth(token, { openId, name, avatarUrl, email, mobile, role, roleName, hasAdminAccess })
      sessionStorage.setItem(FEISHU_LOGIN_SYNC_KEY, '1')
      return true
    }
    return false
  } catch {
    return false
  }
}

/** 跳转飞书授权页 */
function redirectToFeishu() {
  const appId = 'cli_aa90f3d66d7adbb7'
  const currentUrl = getFeishuRedirectUrl()
  const redirectUri = encodeURIComponent(currentUrl)
  window.location.href = `https://open.feishu.cn/open-apis/authen/v1/index?app_id=${appId}&redirect_uri=${redirectUri}`
}

function removeFeishuCodeFromUrl() {
  const url = new URL(window.location.href)
  url.searchParams.delete('code')
  const cleanHash = window.location.hash.replace(/[?&]code=[^&]+/, '').replace(/\?$/, '')
  history.replaceState(null, '', url.pathname + url.search + (cleanHash || '#/tasks'))
}

async function refreshCurrentPermission(): Promise<boolean> {
  const token = getToken()
  if (!token) return false
  try {
    const res = await fetch('/api/permissions/me', {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!res.ok) return false
    const data = await res.json()
    if (data.code !== 200 || !data.data) return false

    const current = getUser()
    const permission = data.data
    const role = permission.role || current?.role || 'normal_user'
    setAuth(token, {
      openId: permission.openId || current?.openId || '',
      name: permission.name || current?.name || '',
      avatarUrl: permission.avatarUrl || current?.avatarUrl || '',
      email: permission.email || current?.email || '',
      mobile: permission.mobile || current?.mobile || '',
      role,
      roleName: current?.roleName,
      hasAdminAccess: role.split(',').some((item: string) =>
        ['headquarters_admin', 'finance_admin', 'hr_admin', 'operation_admin'].includes(item.trim()),
      ),
    })
    return true
  } catch {
    return false
  }
}

// 全局前置守卫
router.beforeEach(async (to, _from, next) => {
  // === 提取飞书 code（URL 查询串 ?code=xxx#/tasks 或 hash 串 #/tasks?code=xxx）===
  let code: string | null = null
  const searchParams = new URLSearchParams(window.location.search)
  code = searchParams.get('code')
  if (!code) {
    const hash = window.location.hash
    const codeMatch = hash.match(/[?&]code=([^&]+)/)
    if (codeMatch) code = codeMatch[1]
  }

  // 有 code → 始终走飞书登录（生产 + 本地 IP 测试）
  if (code) {
    const ok = await feishuLogin(code)
    if (ok) {
      removeFeishuCodeFromUrl()
      window.location.reload()
      return
    }
    removeFeishuCodeFromUrl()
    console.error('飞书登录失败，请刷新页面重试')
    next(false)
    return
  }

  // 检测是否从飞书客户端打开（URL 带飞书参数但没有 code）
  const isFeishuClient = isFeishuLaunch()

  if (!isLoggedIn()) {
    // 飞书客户端打开但没有 code → 跳飞书 OAuth 拿 code
    if (isFeishuClient) {
      redirectToFeishu()
      return
    }

    // 本地开发：自动 Mock 登录
    if (isDev()) {
      const ok = await devLogin()
      if (ok) {
        next()
        return
      }
      redirectToFeishu()
      return
    }

    // 生产环境：没有 code 且未登录 → 跳飞书授权
    redirectToFeishu()
    return
  }

  // 飞书客户端内已登录：信任已有 token，不强制跳转外部 OAuth
  // （飞书 WebView 会拦截对 open.feishu.cn 的跳转，导致白屏）
  if (isFeishuClient && !sessionStorage.getItem(FEISHU_LOGIN_SYNC_KEY)) {
    sessionStorage.setItem(FEISHU_LOGIN_SYNC_KEY, '1')
  }

  await refreshCurrentPermission()

  if (!hasAdminAccess() && to.path !== '/no-permission') {
    next('/no-permission')
    return
  }

  // 按路由 meta.roles 校验角色
  const requiredRoles = to.meta?.roles as string[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    const hasRequired = requiredRoles.some(r => hasRole(r))
    if (!hasRequired) {
      next('/no-permission')
      return
    }
  }

  if (hasAdminAccess() && to.path === '/no-permission') {
    next(defaultRoute())
    return
  }

  next()
})

export default router
