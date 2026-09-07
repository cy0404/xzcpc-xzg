// 订阅消息授权充值工具
//
// 机制：小程序一次性订阅消息 = 1 次授权 = 1 条消息额度。
// 前端在"用户主动点击"时调 requestSubscribeMessage（微信要求必须在用户 TAP 手势内调用，
// onShow/onLoad 等非点击时机调用会 fail），accept 后向后端上报额度 +1
// （POST /api/mp/notify/quota/plus，按 openid 累加，无上限）；后端发送队列消费时额度 -1。
// 用户勾选"总是保持以上选择"后不再弹窗、直接 success → 每次点击静默累积额度。
//
// 任一环节失败均静默（授权取消/拒绝/模板未配置/网络异常都不打扰用户），
// 额度不足时后端自动降级站内（首页待办卡实时统计兜底），微信通道只是增强。

import { request } from './request'
import { SUBSCRIBE_TEMPLATE_ID } from './constants'
import { useUserStore } from '@/store/user'
import { switchStore } from '@/api/auth'

// 节流：仅 accept 成功后 5 秒内不重复调（防同一手势重复触发；"总是保持"后不弹窗、无频率问题，
// 放开节流让每次点击都充值）；fail（非点击时机等）不锁定时长——否则一次失败会吞掉后续合法点击
let lastAcceptAt = 0
const TOP_UP_INTERVAL = 5 * 1000

/**
 * 静默请求订阅授权；accept 时上报额度 +1。
 * 必须在用户点击（tap）回调内同步调用，页面生命周期里调用会被微信 fail。
 * 模板未配置（开发/测试期）→ 直接跳过。永不抛异常。
 */
export async function topUpSubscribeOnce(): Promise<void> {
  if (!SUBSCRIBE_TEMPLATE_ID) return
  const now = Date.now()
  if (now - lastAcceptAt < TOP_UP_INTERVAL) return
  try {
    const res: any = await new Promise((resolve, reject) => {
      uni.requestSubscribeMessage({
        tmplIds: [SUBSCRIBE_TEMPLATE_ID],
        success: resolve,
        fail: reject,
      })
    })
    // accept = 本次授权成功（额度 +1）；reject = 用户拒绝，静默不报错也不锁（微信端自行节流弹窗）
    if (res && res[SUBSCRIBE_TEMPLATE_ID] === 'accept') {
      lastAcceptAt = Date.now()
      await request({ url: '/notify/quota/plus', method: 'POST', showLoading: false, silent: true })
    }
  } catch {
    /* 用户取消 / 非点击时机调用被拒 / 网络异常：静默，不锁节流 */
  }
}

// 订阅消息冷启动直达处理（落地页 onLoad 调用）：
// 点服务通知卡片进入小程序时页面栈只有落地页一页，微信返回键无页可退会直接退出小程序。
// 检测到栈深=1（非首页）→ 目标路径暂存本地（switchTab 不支持 URL 参数），
// switchTab 切回首页 tab（首页是 tabBar 页，redirectTo/reLaunch 均不适用/过重），
// 首页 onShow 读到暂存路径后 navigateTo 转发回目标页 → 页面栈变 [首页, 目标页]，
// 用户点"返回"回到首页（首页待办卡正好承接后续操作）。
// 对分享卡片/扫码等其他冷启动直达场景同样生效，行为更一致。
const PENDING_REDIRECT_KEY = 'notifyPendingRedirect'

/** 首页 onShow 调用：如有垫层待转发路径则跳转（调用后自动清除，避免重复） */
export function forwardPendingRedirect(): void {
  try {
    const pending = uni.getStorageSync(PENDING_REDIRECT_KEY) as string
    if (!pending) return
    uni.removeStorageSync(PENDING_REDIRECT_KEY)
    setTimeout(() => {
      uni.navigateTo({ url: pending })
    }, 400)
  } catch { /* 静默 */ }
}

/**
 * 落地页门店适配（onLoad 调用，需在数据加载前 await）：
 * 订阅消息跳转 URL 带业务门店 storeId（后端入队时自动拼接），多门店店长点消息卡片时
 * 先把登录态切到对应门店再加载数据，避免看到的是当前默认店（空列表）。
 * 返回是否实际切换了门店（切换后建议重新触发一次数据加载）。
 */
export async function applyStoreFromQuery(q: any): Promise<boolean> {
  try {
    const sid = q?.storeId
    if (!sid) return false
    const userStore = useUserStore()
    if (userStore.storeId === sid) {
      return false
    }
    const data: any = await switchStore(sid)
    if (data?.token) {
      uni.setStorageSync('token', data.token)
      userStore.token = data.token
    }
    userStore.storeId = data?.storeId || sid
    userStore.storeName = data?.storeName || ''
    userStore.chatId = data?.chatId || ''
    return true
  } catch (e: any) {
    return false
  }
}

export function ensureBackHome(): void {
  try {
    const pages = getCurrentPages()
    if (pages.length !== 1) return
    const cur = pages[0] as any
    const route: string = cur?.route || ''
    if (!route || route === 'pages/home/index/index') return
    const qs: Record<string, any> = cur?.options || {}
    const query = Object.keys(qs)
      .filter(k => qs[k] !== undefined && qs[k] !== '')
      .map(k => `${k}=${encodeURIComponent(qs[k])}`)
      .join('&')
    const target = '/' + route + (query ? '?' + query : '')
    uni.setStorageSync(PENDING_REDIRECT_KEY, target)
    uni.switchTab({
      url: '/pages/home/index/index',
      fail: (err) => {
        uni.removeStorageSync(PENDING_REDIRECT_KEY)
      },
    })
  } catch (e) {
  }
}
