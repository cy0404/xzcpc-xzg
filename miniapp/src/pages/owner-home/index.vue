<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { fetchOwnerDashboard, switchStore } from '@/api/auth'

const userStore = useUserStore()

const loading = ref(true)
const dashboard = ref<any>(null)

onShow(async () => {
  if (!userStore.token) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  await loadDashboard()
})

async function loadDashboard() {
  loading.value = true
  try {
    dashboard.value = await fetchOwnerDashboard()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

async function handleSwitchStore(storeId: string) {
  try {
    const data: any = await switchStore(storeId)
    if (data) {
      // 更新 token（switchStore 返回了新 token，必须替换）
      if (data.token) {
        uni.setStorageSync('token', data.token)
        userStore.token = data.token
      }
      userStore.storeId = data.storeId || ''
      userStore.storeName = data.storeName || ''
      userStore.role = data.role || ''
      userStore.permissions = Array.isArray(data.permissions) ? data.permissions : []
      userStore.storeCount = data.storeCount || 1
      // 持久化到本地缓存
      uni.setStorageSync('userInfo', {
        storeId: userStore.storeId,
        storeName: userStore.storeName,
        employeeId: userStore.employeeId,
        employeeName: userStore.employeeName,
        role: userStore.role,
        permissions: userStore.permissions,
        storeCount: userStore.storeCount,
        bound: userStore.bound,
      })
    }
    // 设标记，告诉首页跳过老板多门店跳转逻辑
    uni.setStorageSync('_skip_owner_redirect', '1')
    uni.switchTab({ url: '/pages/home/index/index' })
  } catch {
    // switchStore 内部已 toast 错误
  }
}

function goExpenseList() {
  uni.navigateTo({ url: '/pages/expense/list/index' })
}

function goStaffList() {
  uni.navigateTo({ url: '/pages/staff/list/index' })
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index/index' })
}
</script>

<template>
  <view class="owner-page">
    <!-- 标题栏 -->
    <view class="header">
      <view class="header-left">
        <text class="greeting">多门店总览</text>
        <view class="store-pill">
          <text class="pill-icon">▣</text>
          <text class="pill-text">{{ dashboard?.storeCount || 0 }} 家门店</text>
        </view>
      </view>
      <view class="notify-btn">
        <text class="notify-icon">🔔</text>
      </view>
    </view>

    <view class="panel">
      <!-- 多门店概况 -->
      <view class="overview-card">
        <text class="card-label">多门店概况</text>
        <view class="overview-grid">
          <view class="ov-item tap" @click="goExpenseList">
            <view class="ov-icon ov-icon--green">💰</view>
            <text class="ov-label">本月支出</text>
            <text class="ov-value">{{ dashboard?.totalExpense || '¥0' }}</text>
          </view>
          <view class="ov-item tap" @click="goStaffList">
            <view class="ov-icon ov-icon--gray">👥</view>
            <text class="ov-label">在职员工</text>
            <text class="ov-value">{{ dashboard?.totalStaff || 0 }} 人</text>
          </view>
        </view>
      </view>

      <!-- 门店列表 -->
      <view class="section" v-if="dashboard?.stores?.length">
        <view class="section-head">
          <text class="section-title">门店列表</text>
          <text class="section-hint">点击进入单店</text>
        </view>
        <view class="store-list">
          <view
            v-for="store in dashboard.stores"
            :key="store.storeId"
            class="store-card tap"
            @click="handleSwitchStore(store.storeId)"
          >
            <view class="store-top">
              <view class="store-info">
                <text class="store-name">{{ store.storeName }}</text>
              </view>
              <text class="store-arrow">›</text>
            </view>
            <text class="store-manager">店长 {{ store.manager }} · {{ store.staffCount }} 名员工</text>
            <view class="store-metrics">
              <view class="metric">
                <text class="metric-label">本月支出</text>
                <text class="metric-value">{{ store.expense }}</text>
              </view>
              <view class="metric">
                <text class="metric-label">员工数</text>
                <text class="metric-value">{{ store.staffCount }} 人</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="safe-bottom" />
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

.owner-page {
  min-height: 100vh;
  background: $bg;
  padding-bottom: 120rpx;
}

// ====== 标题栏 ======
.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  padding: 24rpx 32rpx;
}
.header-left { min-width: 0; }
.greeting {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: $text-1;
  letter-spacing: -0.02em;
}
.store-pill {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 16rpx;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: $surface;
  box-shadow: 0 4rpx 16rpx rgba(31, 36, 33, 0.06);
  font-size: 26rpx;
}
.pill-icon { font-size: 28rpx; }
.pill-text { color: $text-2; font-weight: 500; }
.notify-btn {
  width: 88rpx; height: 88rpx;
  border-radius: 50%;
  background: $surface;
  box-shadow: 0 4rpx 16rpx rgba(31, 36, 33, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  flex-shrink: 0;
}
.notify-icon { font-size: 40rpx; }

.panel { padding: 0 32rpx; }

// ====== 概况卡片 ======
.overview-card {
  margin-top: 16rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: $surface;
  box-shadow: 0 4rpx 16rpx rgba(31, 36, 33, 0.04);
  border: 2rpx solid $border;
}
.card-label {
  font-size: 26rpx;
  font-weight: 600;
  color: $primary;
  margin-bottom: 24rpx;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8rpx;
}
.ov-item { text-align: center; }
.ov-icon {
  width: 72rpx; height: 72rpx;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 12rpx; font-size: 32rpx;
}
.ov-icon--green { background: $primary-soft; }
.ov-icon--gray { background: #FAFBF9; }
.ov-label { display: block; font-size: 24rpx; color: $text-3; }
.ov-value { display: block; margin-top: 4rpx; font-size: 44rpx; font-weight: 800; color: $text-1; }

// ====== 通用 ======
.section { margin-top: 40rpx; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.section-title { font-size: 34rpx; font-weight: 700; color: $text-1; }
.section-hint { font-size: 24rpx; color: $text-3; }

// ====== 门店列表 ======
.store-list { display: flex; flex-direction: column; gap: 20rpx; }
.store-card {
  padding: 28rpx;
  border-radius: 20rpx;
  border: 2rpx solid $border;
  background: $surface;
  box-shadow: 0 4rpx 16rpx rgba(31, 36, 33, 0.04);
}
.store-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.store-info { display: flex; align-items: center; gap: 12rpx; flex: 1; min-width: 0; }
.store-name { font-size: 32rpx; font-weight: 700; color: $text-1; }
.store-arrow { font-size: 48rpx; color: #8C9691; font-weight: 300; }
.store-manager { display: block; margin-top: 12rpx; font-size: 24rpx; color: $text-3; }
.store-metrics { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12rpx; margin-top: 28rpx; }
.metric { padding: 16rpx; border-radius: 8rpx; background: #FAFBF9; }
.metric-label { display: block; font-size: 22rpx; color: $text-3; }
.metric-value { display: block; margin-top: 4rpx; font-size: 28rpx; font-weight: 700; color: $text-1; }

// ====== 交互 ======
.tap {
  transition: transform 160ms ease;
  &:active { transform: scale(0.98); }
}

.safe-bottom { height: calc(env(safe-area-inset-bottom) + 32rpx); }
</style>
