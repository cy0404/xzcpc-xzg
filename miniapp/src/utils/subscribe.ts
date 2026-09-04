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
