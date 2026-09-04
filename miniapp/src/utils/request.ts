import { BASE_URL, CODE_MAP } from './constants'

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: any
  header?: Record<string, string>
  showLoading?: boolean
  silent?: boolean
}

let requestCount = 0
let hasLoggedOut = false

function showLoading() {
  if (requestCount === 0) {
    uni.showLoading({ title: '加载中...', mask: true, fail: () => {} })
  }
  requestCount++
}

function hideLoading() {
  requestCount--
  if (requestCount <= 0) {
    requestCount = 0
    // 页面切换/并发场景下 toast 可能已被消费，hideLoading fail 属正常竞态，消音避免控制台报错
    uni.hideLoading({ fail: () => {} })
  }
}

export function resetLogoutFlag() { hasLoggedOut = false }

export function request<T = any>(options: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, header = {}, showLoading: isLoading = true, silent = false } = options

  if (isLoading) {
    showLoading()
  }

  const token = uni.getStorageSync('token')
  const headers: Record<string, string> = { 'Content-Type': 'application/json', ...header }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: headers,
      success: (res: any) => {
        if (isLoading) hideLoading()
        const body = res.data
        if (res.statusCode === 200 && body.code === 200) {
          resolve(body.data)
        } else if (body.code === 401) {
          if (!silent) {
            if (!hasLoggedOut) {
              hasLoggedOut = true
              uni.removeStorageSync('token')
              uni.reLaunch({ url: '/pages/login/index' })
            }
          }
          reject(body)
        } else {
          if (!silent) {
            const msg = CODE_MAP[body.code] || body.message || '请求失败'
            uni.showToast({ title: msg, icon: 'none' })
          }
          reject(body)
        }
      },
      fail: (err: any) => {
        if (isLoading) hideLoading()
        if (!silent) {
          uni.showToast({ title: '网络异常，请重试', icon: 'none' })
        }
        reject(err)
      },
    })
  })
}
