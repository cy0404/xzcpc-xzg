<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/store/user'
import { fetchOwnerMyStatus } from '@/api/auth'

const userStore = useUserStore()
const loading = ref(false)

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
      <text class="app-desc">授权后，我们会根据你的门店和岗位，帮你进入对应的工作台。</text>

      <!-- 权限说明卡片 -->
      <view class="perm-card">
        <view class="perm-icon-wrap">
          <text class="perm-icon">🛡</text>
        </view>
        <view class="perm-body">
          <text class="perm-title">将用于匹配门店身份</text>
          <text class="perm-desc">读取微信头像和昵称，匹配你在门店中的员工身份，用于盘点、支出登记和员工管理。</text>
        </view>
      </view>

      <!-- 底部按钮 -->
      <view class="bottom-actions">
        <view class="login-btn tap" :class="{ loading }" @click="handleLogin">
          <text v-if="loading">登录中...</text>
          <text v-else>微信授权登录</text>
        </view>
        <text class="footer-note">登录即表示你同意象掌柜根据门店授权范围提供经营工具服务。</text>
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
  padding: 128rpx 32rpx 0;
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

.app-desc {
  display: block;
  margin-top: 24rpx;
  max-width: 600rpx;
  font-size: 28rpx;
  color: $text-2;
  line-height: 44rpx;
}

// ====== 权限说明卡片 ======
.perm-card {
  margin-top: 32rpx;
  width: 100%;
  border-radius: 16rpx;
  background: $surface;
  padding: 32rpx;
  text-align: left;
  box-shadow: 0 4px 16px rgba(31, 36, 33, 0.06);
  display: flex;
  gap: 24rpx;
}

.perm-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: $primary-soft;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.perm-icon {
  font-size: 40rpx;
}

.perm-body {
  flex: 1;
  min-width: 0;
}

.perm-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $text-1;
}

.perm-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  color: $text-2;
  line-height: 40rpx;
}

// ====== 底部按钮 ======
.bottom-actions {
  margin-top: auto;
  width: 100%;
  padding-top: 64rpx;
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

.footer-note {
  display: block;
  margin: 32rpx auto 0;
  max-width: 600rpx;
  font-size: 24rpx;
  color: $text-3;
  line-height: 36rpx;
}

// ====== 交互 ======
.tap {
  transition: transform 160ms ease;
  &:active { transform: scale(0.98); }
}
</style>
