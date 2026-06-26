<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

onShow(() => {
  if (!userStore.token) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!userStore.bound) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})
</script>

<template>
  <view class="business-page">
    <view class="placeholder">
      <image class="ph-icon" src="/static/icons/LoadingImage.png" mode="aspectFit" />
      <text class="ph-title">门店经营情况分析</text>
      <text class="ph-sub">即将开放</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.business-page {
  min-height: 100vh;
  padding: 36rpx 24rpx 48rpx;
  background: #F4F5F7;
  display: flex;
  flex-direction: column;
}

.page-header {
  padding: 8rpx 8rpx 24rpx;
}

.page-title {
  display: block;
  font-size: 44rpx;
  font-weight: 800;
  color: #1A1A1A;
}

.page-sub {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #8C8C8C;
}

.placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 120rpx;
}

.ph-icon {
  width: 400rpx;
  height: 400rpx;
  margin-bottom: 32rpx;
}

.ph-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1A1A1A;
  margin-bottom: 12rpx;
}

.ph-sub {
  font-size: 26rpx;
  color: #8C8C8C;
}
</style>
