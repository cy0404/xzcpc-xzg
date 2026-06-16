import axios from 'axios'
import { getToken, clearAuth, isDev, devLogin } from '../utils/auth'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器：自动附加 Authorization Bearer token
api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：业务 code 非 200 时拒绝并提示错误信息
api.interceptors.response.use(
  (res) => {
    if (res.data.code !== 200) {
      if (res.data.code === 401) {
        handle401()
      }
      return Promise.reject(new Error(res.data.message || '请求失败'))
    }
    return res.data
  },
  (err) => {
    if (err.response?.status === 401) {
      handle401()
    }
    return Promise.reject(err)
  },
)

/** 401 处理：dev 环境重试 Mock 登录，生产环境跳飞书 */
async function handle401() {
  clearAuth()
  if (isDev()) {
    const ok = await devLogin()
    if (ok) {
      // Mock 登录后刷新页面重试
      window.location.reload()
      return
    }
    redirectToFeishuAuth()
    return
  }
  redirectToFeishuAuth()
}

/** 跳转飞书 H5 授权 */
function redirectToFeishuAuth() {
  const appId = 'cli_aa90f3d66d7adbb7'
  const redirectUri = encodeURIComponent(window.location.origin + window.location.pathname + window.location.hash)
  window.location.href = `https://open.feishu.cn/open-apis/authen/v1/index?app_id=${appId}&redirect_uri=${redirectUri}`
}

export default api
