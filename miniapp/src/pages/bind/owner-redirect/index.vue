<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

onLoad((q: any) => {
  const rawUrl = q?.q ? decodeURIComponent(q.q) : ''
  const urlParts = rawUrl.split('?')
  const urlParams = new URLSearchParams(urlParts[1] || '')

  const storeId = urlParams.get('storeId') || ''
  const returnUrl = urlParams.get('returnUrl') ? decodeURIComponent(urlParams.get('returnUrl')!) : ''

  if (!storeId || !returnUrl) {
    uni.showToast({ title: '参数错误', icon: 'none' })
    return
  }

  uni.showLoading({ title: '加载中' })

  uni.login({
    success: async (loginRes: any) => {
      try {
        // 换 openid
        const tokenData: any = await request({
          url: '/public/exchange-openid?code=' + encodeURIComponent(loginRes.code),
          showLoading: false,
          silent: true,
        })
        const openid = tokenData?.openid || ''

        // 存 openid + 查回填信息
        const saveData: any = await request({
          url: '/public/save-openid',
          method: 'POST',
          data: { storeId, openid },
          showLoading: false,
          silent: true,
        })

        uni.hideLoading()

        // 拼接平台表单 URL
        const sep = returnUrl.includes('?') ? '&' : '?'
        let target = returnUrl + sep + 'storeId=' + encodeURIComponent(storeId)

        if (saveData?.alreadyOwner) {
          target += '&alreadyOwner=true'
          if (saveData.fromStoreId) target += '&fromStoreId=' + encodeURIComponent(saveData.fromStoreId)
        }

        uni.redirectTo({ url: '/pages/webview/index?url=' + encodeURIComponent(target) })
      } catch {
        uni.hideLoading()
        uni.showToast({ title: '获取用户信息失败，请重试', icon: 'none' })
      }
    },
    fail: () => {
      uni.hideLoading()
      uni.showToast({ title: '微信登录失败，请重试', icon: 'none' })
    },
  })
})
</script>

<template>
  <view class="page">
    <view class="loading-box">
      <text class="loading-text">正在跳转...</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #F7F8F6; }
.loading-box { text-align: center; }
.loading-text { font-size: 28rpx; color: #66706A; }
</style>
