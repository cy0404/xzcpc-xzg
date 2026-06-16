<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const title = ref('功能建设中')
const desc = ref('该功能将在后续阶段接入，请先使用已上线功能。')

onLoad((query: any) => {
  if (query?.title) title.value = decodeURIComponent(query.title)
  if (query?.desc) desc.value = decodeURIComponent(query.desc)
})

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: '/pages/home/index/index' })
}
</script>

<template>
  <view class="placeholder-page">
    <view class="empty-card">
      <view class="empty-icon">建</view>
      <text class="empty-title">{{ title }}</text>
      <text class="empty-desc">{{ desc }}</text>
      <view class="back-btn" @click="goBack">返回</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.placeholder-page {
  min-height: 100vh;
  padding: 120rpx 48rpx;
  background: #F4F5F7;
}

.empty-card {
  padding: 64rpx 40rpx;
  border-radius: 32rpx;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  width: 132rpx;
  height: 132rpx;
  border-radius: 40rpx;
  background: #E8F5EE;
  color: #00734A;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
  font-weight: 800;
}

.empty-title {
  margin-top: 36rpx;
  font-size: 36rpx;
  font-weight: 800;
  color: #1A1A1A;
}

.empty-desc {
  margin-top: 16rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #8C8C8C;
  text-align: center;
}

.back-btn {
  margin-top: 44rpx;
  height: 84rpx;
  min-width: 220rpx;
  border-radius: 999rpx;
  background: #00734A;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 700;
}
</style>
