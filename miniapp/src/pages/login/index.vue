<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/store/user'
import { fetchOwnerMyStatus } from '@/api/auth'

const userStore = useUserStore()
const loading = ref(false)
const agreed = ref(false)

function openService() {
  uni.navigateTo({ url: '/pages/agreement/service' })
}

function openPrivacy() {
  uni.navigateTo({ url: '/pages/agreement/privacy' })
}

async function getWxNickname() {
  try {
    const profile: any = await uni.getUserProfile({
      desc: '用于展示盘点任务提交人',
    })
    return profile?.userInfo?.nickName || ''
  } catch {
    return ''
  }
}

async function handleLogin() {
  if (loading.value) return
  loading.value = true
  try {
    // 1. 先获取微信 code
    const loginRes: any = await uni.login()
    const wxCode = loginRes.code

    // 2. 检查老板绑定状态
    let ownerStatus: any = null
    try {
      ownerStatus = await fetchOwnerMyStatus(wxCode)
    } catch {
      // 静默，继续走正常登录
    }

    // 3. 有申请但未绑定 → 跳到状态页
    if (ownerStatus && !ownerStatus.hasBound && ownerStatus.latestApplication) {
      const app = ownerStatus.latestApplication
      const params = [
        `bindCode=${app.bindCode}`,
        `state=${app.status}`,
        `name=${encodeURIComponent(app.name || '')}`,
        `phone=${app.phoneMasked || ''}`,
      ]
      uni.redirectTo({ url: `/pages/bind/owner-register-status?${params.join('&')}` })
      return
    }

    // 4. 有绑定门店或状态未知 → 正常登录
    const wxNickname = await getWxNickname()
    const isBound = await userStore.wxLogin(wxNickname)
    if (isBound) {
      uni.switchTab({ url: '/pages/home/index/index' })
    } else {
      uni.showModal({
        title: '暂无权限',
        content: '暂无权限访问小程序，请联系店长或管理员增加权限。',
        showCancel: false,
        confirmText: '知道了',
        success: () => {
          uni.navigateBackMiniProgram({ success: () => {} })
        },
      })
    }
  } catch {
    uni.showToast({ title: '登录失败，请重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function handleSkip() {
  uni.navigateBackMiniProgram({ success: () => {} })
}

function onLoginClick() {
  if (agreed.value) handleLogin()
}
</script>

<template>
  <view class="login-page">
    <!-- 主体 -->
    <view class="hero">
      <!-- Logo -->
      <view class="logo-wrap">
        <image class="logo-img" src="/static/logo.png" mode="aspectFit" />
      </view>

      <text class="app-name">象掌柜</text>
      <text class="app-subtitle">象子茶铺的门店经营工具</text>

      <!-- 底部按钮 -->
      <view class="bottom-actions">
        <view class="login-btn tap" :class="{ loading, disabled: !agreed }" @click="onLoginClick">
          <text v-if="loading">登录中...</text>
          <text v-else>微信授权登录</text>
        </view>
        <view class="agree-row" @click="agreed = !agreed">
          <view class="agree-check" :class="{ on: agreed }">
            <text v-if="agreed" class="agree-icon">✓</text>
          </view>
          <text class="agree-text">
            已阅读并同意
            <text class="agree-link" @click.stop="openService">《用户服务协议》</text>
            和
            <text class="agree-link" @click.stop="openPrivacy">《隐私政策》</text>
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg: #F7F8F6;
$surface: #FFFFFF;
$primary: #2F8F57;
$primary-soft: #E7F4EB;
$text-1: #1F2421;
$text-2: #66706A;
$text-3: #98A19C;
$border: #E8ECE9;

.login-page {
  min-height: 100vh;
  background: $bg;
  display: flex;
  flex-direction: column;
}

// ====== 顶部导航 ======
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 32rpx;
}

.back-btn {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $surface;
  box-shadow: 0 4px 16px rgba(31, 36, 33, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  padding: 0;
  line-height: 1;

  &::after { border: none; }
}

.back-icon {
  font-size: 52rpx;
  font-weight: 300;
  color: #56605B;
  line-height: 1;
}

.header-badge {
  padding: 8rpx 24rpx;
  border-radius: 100rpx;
  background: $surface;
  box-shadow: 0 4px 16px rgba(31, 36, 33, 0.06);
}

.header-badge text {
  font-size: 24rpx;
  color: $text-2;
}

// ====== 主体 ======
.hero {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 260rpx 32rpx 32rpx;
  text-align: center;
}

.logo-wrap {
  width: 192rpx;
  height: 192rpx;
  border-radius: 24rpx;
  background: $surface;
  box-shadow: 0 4px 16px rgba(31, 36, 33, 0.06);
  overflow: hidden;
}

.logo-img {
  width: 100%;
  height: 100%;
}

.app-name {
  display: block;
  margin-top: 32rpx;
  font-size: 44rpx;
  font-weight: 600;
  color: $text-1;
  line-height: 60rpx;
}

.app-subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 32rpx;
  font-weight: 500;
  color: $text-1;
  line-height: 44rpx;
}

// ====== 协议勾选 ======
.agree-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-top: 24rpx;
  padding: 0 4rpx;
}

.agree-check {
  width: 40rpx;
  height: 40rpx;
  border-radius: 8rpx;
  border: 2rpx solid #C4C9C7;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4rpx;
  transition: all 0.2s;

  &.on {
    background: $primary;
    border-color: $primary;
  }
}

.agree-icon {
  font-size: 28rpx;
  color: #fff;
  font-weight: 700;
  line-height: 1;
}

.agree-text {
  flex: 1;
  font-size: 26rpx;
  color: $text-2;
  line-height: 44rpx;
}

.agree-link {
  color: $primary;
  font-weight: 500;
}

// ====== 底部按钮 ======
.bottom-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 32rpx 32rpx calc(env(safe-area-inset-bottom) + 32rpx);
  background: $bg;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 16rpx;
  background: $primary;
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(31, 36, 33, 0.10);

  &.loading {
    opacity: 0.7;
  }

  &.disabled {
    opacity: 0.45;
    pointer-events: none;
  }
}

.skip-btn {
  width: 100%;
  height: 88rpx;
  border-radius: 16rpx;
  background: $surface;
  border: 2rpx solid $border;
  color: $text-1;
  font-size: 30rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 24rpx;
}

// ====== 交互 ======
.tap {
  transition: transform 160ms ease;
  &:active { transform: scale(0.98); }
}
</style>
