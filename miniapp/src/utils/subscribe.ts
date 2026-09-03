// 订阅消息授权充值工具
//
// 机制：小程序一次性订阅消息 = 1 次授权 = 1 条消息额度。
// 前端在"用户主动进入/操作"时静默调 requestSubscribeMessage，accept 后向后端上报额度 +1
// （POST /api/mp/notify/quota/plus，按 openid 累加，无上限）；后端发送队列消费时额度 -1。
// 用户勾选"总是保持以上选择"后不再弹窗、直接成功 → 每次进入首页静默累积额度。
//
// 任一环节失败均静默（授权取消/拒绝/模板未配置/网络异常都不打扰用户），
// 额度不足时后端自动降级站内（首页待办卡实时统计兜底），微信通道只是增强。

import { request } from './request'
import { SUBSCRIBE_TEMPLATE_ID } from './constants'

// 节流：30 秒内不重复弹授权框（避免 onShow 高频触发反复打扰；弹窗/拒绝/成功均重置计时）
let lastAttemptAt = 0
const TOP_UP_INTERVAL = 30 * 1000

/**
 * 静默请求订阅授权；accept 时上报额度 +1。
 * 模板未配置（开发/测试期）→ 直接跳过。永不抛异常。
 */
export async function topUpSubscribeOnce(): Promise<void> {
  if (!SUBSCRIBE_TEMPLATE_ID) return
  const now = Date.now()
  if (now - lastAttemptAt < TOP_UP_INTERVAL) return
  lastAttemptAt = now
  try {
    const res: any = await new Promise((resolve, reject) => {
      uni.requestSubscribeMessage({
        tmplIds: [SUBSCRIBE_TEMPLATE_ID],
        success: resolve,
        fail: reject,
      })
    })
    // accept = 本次授权成功（额度 +1）；reject / 未订阅 = 用户拒绝，静默不报错
    if (res && res[SUBSCRIBE_TEMPLATE_ID] === 'accept') {
      await request({ url: '/notify/quota/plus', method: 'POST', showLoading: false, silent: true })
    }
  } catch {
    /* 用户取消或接口异常：静默 */
  }
}
