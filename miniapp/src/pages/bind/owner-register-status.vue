<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const bindCode = ref('')
const status = ref('pending')
const name = ref('')
const phone = ref('')
const storeCount = ref(0)
const storeNames = ref<string[]>([])
const submitTime = ref('')

const phoneMasked = computed(() => {
  const p = phone.value
  if (!p || p.length < 7) return p
  return p.substring(0, 3) + '****' + p.substring(7)
})

const primaryText = computed(() => {
  if (status.value === 'auto_bound' || status.value === 'approved') return '进入老板首页'
  if (status.value === 'rejected') return '联系总部'
  return '知道了'
})

const secondaryText = computed(() => {
  if (status.value === 'pending') return '联系总部'
  if (status.value === 'rejected') return '返回微信'
  return '返回微信'
})

const badgeText = computed(() => {
  if (status.value === 'pending') return '总部核验中'
  if (status.value === 'rejected') return '未通过'
  return ''
})

const badgeStyle = computed(() => {
  if (status.value === 'pending') return 'badge-pending'
  if (status.value === 'rejected') return 'badge-rejected'
  return ''
})

onLoad((query: any) => {
  bindCode.value = query?.bindCode || ''
  status.value = query?.state || 'pending'
  name.value = decodeURIComponent(query?.name || '') || '--'
  phone.value = query?.phone || ''
  storeCount.value = parseInt(query?.storeCount || '0')
  storeNames.value = query?.storeNames ? decodeURIComponent(query.storeNames).split(',').filter(Boolean) : []
  submitTime.value = formatNow()
})

onShow(() => {
  // pending 状态时每次回看页面都刷新后端状态
  if (status.value === 'pending' && bindCode.value) {
    checkStatus()
  }
})

function formatNow(): string {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function checkStatus() {
  try {
    const data: any = await request({ url: `/auth/owner/status?bindCode=${bindCode.value}`, showLoading: false, silent: true })
    if (data && data.status !== 'pending') {
      status.value = data.status
      if (data.status === 'approved' || data.status === 'auto_bound') {
        storeCount.value = data.storeCount || 0
        storeNames.value = data.storeNames || []
      }
    }
  } catch {
    // 静默失败
  }
}

async function onPrimary() {
  if (status.value === 'auto_bound' || status.value === 'approved') {
    // 先登录获取 token
    try {
      await userStore.wxLogin('')
    } catch {
      uni.showToast({ title: '登录失败，请重试', icon: 'none' })
      return
    }
    // 按门店数跳转
    if (userStore.storeCount > 1) {
      // 多门店 → 多门店总览
      uni.redirectTo({ url: '/pages/owner-home/index' })
    } else {
      // 单门店 → 首页
      uni.switchTab({ url: '/pages/home/index/index' })
    }
  } else if (status.value === 'rejected') {
    uni.showModal({ title: '联系总部', content: '请联系总部人工处理', showCancel: false })
  } else {
    // pending：保存 bindCode 到本地，跳登录页
    if (bindCode.value) {
      uni.setStorageSync('_pending_bind_code', bindCode.value)
    }
    uni.reLaunch({ url: '/pages/login/index' })
  }
}

function onSecondary() {
  if (status.value === 'pending') {
    uni.showModal({ title: '联系总部', content: '请联系总部人工处理', showCancel: false })
  } else {
    uni.reLaunch({ url: '/pages/login/index' })
  }
}
</script>

<template>
  <view class="page">
    <view class="header">
      <view class="hint">老板绑定</view>
    </view>

    <!-- ====== 🟢 自动绑定成功 / 审核通过 ====== -->
    <view class="hero" v-if="status === 'auto_bound' || status === 'approved'">
      <view class="hero-icon icon-success">✓</view>
      <text class="hero-title">绑定成功，欢迎回来</text>
      <text class="hero-desc">已关联 {{ storeCount }} 家门店</text>

      <view class="store-list" v-if="storeNames.length">
        <view class="store-item" v-for="(sn, idx) in storeNames" :key="idx">
          <view class="si-icon">🏪</view>
          <text class="si-name">{{ sn }}</text>
        </view>
      </view>
    </view>

    <!-- ====== 🟠 总部核验中 ====== -->
    <view class="hero" v-else-if="status === 'pending'">
      <view class="hero-icon icon-pending">⏳</view>
      <text class="hero-title">登记申请已提交</text>
      <text class="hero-desc">总部核验通过后，会自动绑定当前微信并开放门店管理权限。</text>
    </view>

    <!-- ====== 🔴 审核拒绝 ====== -->
    <view class="hero" v-else-if="status === 'rejected'">
      <view class="hero-icon icon-rejected">✕</view>
      <text class="hero-title">信息未通过验证</text>
      <text class="hero-desc">请联系总部人工处理。为保障门店账号安全，页面不会展示具体未通过原因。</text>
    </view>

    <!-- 信息卡片（pending/rejected 显示） -->
    <view class="info-card" v-if="status === 'pending' || status === 'rejected'">
      <view class="ir"><text class="il">姓名</text><text class="iv">{{ name }}</text></view>
      <view class="ir"><text class="il">手机号</text><text class="iv">{{ phoneMasked }}</text></view>
      <view class="ir"><text class="il">提交时间</text><text class="iv">{{ submitTime || '--' }}</text></view>
      <view class="ir last">
        <text class="il">当前状态</text>
        <view class="badge" :class="badgeStyle">{{ badgeText }}</view>
      </view>
    </view>

    <!-- 底部按钮 -->
    <view class="bottom">
      <view class="btn" @click="onPrimary">{{ primaryText }}</view>
      <view class="btn-sec" @click="onSecondary">{{ secondaryText }}</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #F7F8F6; display: flex; flex-direction: column; }

// 顶栏
.header { display: flex; justify-content: flex-end; padding: 24rpx 32rpx; }
.hint { font-size: 24rpx; color: #66706A; background: #fff; padding: 8rpx 24rpx; border-radius: 100rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.06); }

// 状态区
.hero { display: flex; flex-direction: column; align-items: center; padding: 80rpx 32rpx 0; text-align: center; }
.hero-icon { width: 140rpx; height: 140rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 64rpx; margin-bottom: 32rpx; }
.icon-success { background: #F1F8F3; color: #2F8F57; }
.icon-pending { background: #FFF8EE; color: #E58A2D; }
.icon-rejected { background: #FFF4F2; color: #E05A47; }
.hero-title { font-size: 44rpx; font-weight: 700; color: #1F2421; line-height: 56rpx; }
.hero-desc { margin-top: 16rpx; max-width: 520rpx; font-size: 26rpx; color: #66706A; line-height: 40rpx; }

// 门店列表
.store-list { width: 100%; margin-top: 48rpx; display: flex; flex-direction: column; gap: 16rpx; }
.store-item { display: flex; align-items: center; gap: 20rpx; padding: 24rpx; border-radius: 20rpx; background: #fff; border: 1px solid #E8ECE9; }
.si-icon { font-size: 32rpx; flex-shrink: 0; }
.si-name { font-size: 28rpx; font-weight: 500; color: #1F2421; }

// 信息卡片
.info-card { margin: 48rpx 32rpx 0; padding: 28rpx; border-radius: 20rpx; background: #fff; border: 1px solid #E8ECE9; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.04); }
.ir { display: flex; align-items: center; justify-content: space-between; padding: 18rpx 0; border-bottom: 1px solid #EEF1EF; }
.last { border-bottom: 0; }
.il { font-size: 28rpx; color: #66706A; flex-shrink: 0; }
.iv { font-size: 28rpx; font-weight: 500; color: #1F2421; text-align: right; }
.badge { padding: 4rpx 16rpx; border-radius: 8rpx; font-size: 24rpx; font-weight: 500; }
.badge-pending { background: #FFF8EE; color: #E58A2D; }
.badge-rejected { background: #FFF4F2; color: #E05A47; }

// 底部
.bottom { margin-top: auto; padding: 56rpx 32rpx 0; }
.btn { height: 96rpx; border-radius: 24rpx; background: #2F8F57; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 30rpx; font-weight: 600; box-shadow: 0 8rpx 24rpx rgba(31,36,33,0.1); }
.btn-sec { height: 88rpx; display: flex; align-items: center; justify-content: center; margin-top: 20rpx; border-radius: 24rpx; color: #2F8F57; font-size: 28rpx; font-weight: 600; }
</style>
