<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { logout as apiLogout } from '@/api/auth'

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

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号吗？',
    success: async (res: any) => {
      if (!res.confirm) return
      try {
        await apiLogout()
      } catch {
        // 本地退出优先，接口失败不阻塞用户操作。
      }
      userStore.logout()
      uni.reLaunch({ url: '/pages/login/index' })
    },
  })
}
</script>

<template>
  <view class="profile-page">
    <view class="user-card">
      <view class="avatar">象</view>
      <view class="user-info">
        <text class="user-name">门店用户</text>
        <text class="user-sub">象掌柜门店端</text>
      </view>
    </view>

    <view class="store-card">
      <text class="section-title">当前门店</text>
      <view class="store-row">
        <view>
          <text class="store-name">{{ userStore.storeName || '未绑定门店' }}</text>
          <text class="store-id">门店 ID：{{ userStore.storeId || '--' }}</text>
        </view>
      </view>
    </view>

    <view class="menu-list">
      <view class="menu-item">
        <text>版本信息</text>
        <text class="menu-value">1.0.0</text>
      </view>
      <view class="menu-item">
        <text>帮助与反馈</text>
        <text class="menu-value">后续接入</text>
      </view>
    </view>

    <view class="logout-btn" @click="handleLogout">退出登录</view>
  </view>
</template>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  padding: 36rpx 24rpx 48rpx;
  background: #F4F5F7;
}

.user-card,
.store-card,
.menu-list {
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.user-card {
  display: flex;
  align-items: center;
  padding: 36rpx 32rpx;
}

.avatar {
  width: 104rpx;
  height: 104rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #00734A 0%, #07B85D 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 42rpx;
  font-weight: 800;
}

.user-info {
  margin-left: 24rpx;
}

.user-name {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: #1A1A1A;
}

.user-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8C8C8C;
}

.store-card {
  margin-top: 24rpx;
  padding: 30rpx;
}

.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #4A4A4A;
}

.store-row,
.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.store-row {
  margin-top: 24rpx;
}

.store-name {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #1A1A1A;
}

.store-id {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8C8C8C;
}

.switch-btn {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: #E8F5EE;
  color: #00734A;
  font-size: 24rpx;
}

.menu-list {
  margin-top: 24rpx;
  overflow: hidden;
}

.menu-item {
  padding: 30rpx;
  border-bottom: 1rpx solid #EDEFF2;
  font-size: 28rpx;
  color: #1A1A1A;

  &:last-child {
    border-bottom: 0;
  }
}

.menu-value {
  color: #8C8C8C;
  font-size: 26rpx;
}

.logout-btn {
  margin-top: 40rpx;
  height: 88rpx;
  border-radius: 999rpx;
  background: #fff;
  color: #E84B61;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 700;
}
</style>
