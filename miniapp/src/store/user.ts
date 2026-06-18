import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { request } from '@/utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const storeId = ref<string>('')
  const storeName = ref<string>('')
  const employeeId = ref<string>('')
  const employeeName = ref<string>('')
  const role = ref<string>('')
  const permissions = ref<string[]>([])
  const storeCount = ref<number>(1)
  /** bound 表示已绑定门店，true 才能进入业务页面 */
  const bound = ref(false)

  const isLoggedIn = computed(() => !!token.value && bound.value)

  function setToken(val: string) {
    token.value = val
    uni.setStorageSync('token', val)
  }

  function persistUser() {
    uni.setStorageSync('userInfo', {
      storeId: storeId.value,
      storeName: storeName.value,
      employeeId: employeeId.value,
      employeeName: employeeName.value,
      role: role.value,
      permissions: permissions.value,
      storeCount: storeCount.value,
      bound: bound.value,
    })
  }

  function applyLoginResp(data: any) {
    if (!data) return
    if (!data.bound) {
      // 已离职或无权限，清空登录态
      logout()
      return
    }
    if (data.token) setToken(data.token)
    bound.value = true
    storeId.value = data.storeId || ''
    storeName.value = data.storeName || ''
    employeeId.value = data.employeeId || ''
    employeeName.value = data.employeeName || ''
    role.value = data.role || ''
    permissions.value = Array.isArray(data.permissions) ? data.permissions : []
    storeCount.value = data.storeCount || 1
    persistUser()
  }

  function checkLogin() {
    const saved = uni.getStorageSync('token')
    if (saved) {
      token.value = saved
      // 先从本地缓存恢复用户信息，避免页面 onShow 误判
      const cachedUser = uni.getStorageSync('userInfo')
      if (cachedUser) {
        storeId.value = cachedUser.storeId || ''
        storeName.value = cachedUser.storeName || ''
        employeeId.value = cachedUser.employeeId || ''
        employeeName.value = cachedUser.employeeName || ''
        role.value = cachedUser.role || ''
        permissions.value = Array.isArray(cachedUser.permissions) ? cachedUser.permissions : []
        storeCount.value = cachedUser.storeCount || 1
        bound.value = !!cachedUser.bound
      }
      // 异步校验 token 并刷新最新数据
      fetchMe()
    }
  }

  async function fetchMe() {
    try {
      const data: any = await request({ url: '/auth/me', showLoading: false, silent: true })
      applyLoginResp(data)
      if (!data.bound) {
        uni.reLaunch({ url: '/pages/login/index' })
      }
    } catch {
      token.value = ''
      uni.removeStorageSync('token')
      uni.removeStorageSync('userInfo')
    }
  }

  /**
   * 微信授权登录：
   * - 返回 true：已绑定门店，可直接进入任务列表
   * - 返回 false：未绑定门店，需要跳到门店选择页
   */
  async function wxLogin(wxNickname = ''): Promise<boolean> {
    const loginRes = await uni.login()
    const data: any = await request({
      url: '/auth/wx/login',
      method: 'POST',
      data: { code: loginRes.code, wxNickname },
      showLoading: false,
    })
    if (data) {
      applyLoginResp(data)
      return bound.value
    }
    throw new Error('微信登录响应为空')
  }

  /**
   * 绑定门店：选定门店后调用，绑定成功后返回 true
   */
  async function bindStore(targetStoreId: string): Promise<boolean> {
    try {
      const data: any = await request({
        url: '/auth/bind-store',
        method: 'POST',
        data: { storeId: targetStoreId },
      })
      if (data) {
        applyLoginResp(data)
        return bound.value
      }
      return false
    } catch {
      return false
    }
  }

  function logout() {
    token.value = ''
    storeId.value = ''
    storeName.value = ''
    employeeId.value = ''
    employeeName.value = ''
    role.value = ''
    permissions.value = []
    storeCount.value = 1
    bound.value = false
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
  }

  return {
    token,
    storeId,
    storeName,
    employeeId,
    employeeName,
    role,
    permissions,
    storeCount,
    bound,
    isLoggedIn,
    checkLogin,
    wxLogin,
    bindStore,
    fetchMe,
    logout,
  }
})
