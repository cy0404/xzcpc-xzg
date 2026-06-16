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

function goPlaceholder(title: string, desc: string) {
  uni.navigateTo({
    url: `/pages/placeholder/index/index?title=${encodeURIComponent(title)}&desc=${encodeURIComponent(desc)}`,
  })
}
</script>

<template>
  <view class="business-page">
    <view class="page-header">
      <text class="page-title">经营</text>
      <text class="page-sub">{{ userStore.storeName || '我的门店' }}</text>
    </view>

    <view class="overview-card">
      <text class="overview-label">经营概览</text>
      <text class="overview-title">数据看板将在后续阶段接入</text>
      <text class="overview-desc">后续可在这里查看门店支出、员工、盘点完成情况等核心指标。</text>
    </view>

    <view class="entry-list">
      <view
        class="entry-card"
        @click="goPlaceholder('支出统计', '支出统计将在支出模块阶段接入')"
      >
        <view class="entry-icon">支</view>
        <view class="entry-content">
          <text class="entry-title">支出统计</text>
          <text class="entry-desc">查看支出趋势和类型分布</text>
        </view>
        <text class="arrow">›</text>
      </view>

      <view
        class="entry-card"
        @click="goPlaceholder('人员统计', '人员统计将在员工模块阶段接入')"
      >
        <view class="entry-icon">员</view>
        <view class="entry-content">
          <text class="entry-title">人员统计</text>
          <text class="entry-desc">查看员工结构和在职情况</text>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.business-page {
  min-height: 100vh;
  padding: 36rpx 24rpx 48rpx;
  background: #F4F5F7;
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

.overview-card {
  padding: 36rpx 32rpx;
  border-radius: 32rpx;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.overview-label {
  display: inline-flex;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #E8F5EE;
  color: #00734A;
  font-size: 24rpx;
}

.overview-title {
  display: block;
  margin-top: 28rpx;
  font-size: 36rpx;
  font-weight: 800;
  color: #1A1A1A;
}

.overview-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #8C8C8C;
}

.entry-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 28rpx;
}

.entry-card {
  display: flex;
  align-items: center;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.entry-icon {
  width: 76rpx;
  height: 76rpx;
  border-radius: 22rpx;
  background: #E8F5EE;
  color: #00734A;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 700;
}

.entry-content {
  flex: 1;
  margin-left: 22rpx;
}

.entry-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1A1A1A;
}

.entry-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8C8C8C;
}

.arrow {
  color: #B0B0B0;
  font-size: 44rpx;
}
</style>
