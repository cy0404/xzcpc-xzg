<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const bindCode = ref('')
const state = ref('pending')
const name = ref('')
const phone = ref('')
const storeCount = ref(0)
const storeNames = ref<string[]>([])

const phoneMasked = computed(() => {
  const p = phone.value
  if (!p || p.length < 7) return p
  return p.substring(0, 3) + '****' + p.substring(7)
})

const now = computed(() => {
  const d = new Date()
  return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日 ' +
    String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
})

onLoad((query: any) => {
  bindCode.value = query?.bindCode || query?.scene || ''
  state.value = query?.state || 'pending'
  name.value = decodeURIComponent(query?.name || '') || '--'
  phone.value = query?.phone || ''
  storeCount.value = parseInt(query?.storeCount || '0')
  storeNames.value = query?.storeNames ? decodeURIComponent(query.storeNames).split(',').filter(Boolean) : []
})

function goLogin() { uni.reLaunch({ url: '/pages/login/index' }) }
function goBack() {
  uni.redirectTo({ url: '/pages/bind/owner-register-qr/index' + (bindCode.value ? '?bindCode=' + bindCode.value : '') })
}
</script>

<template>
  <view class="page">

    <!-- ===== 绑定成功 ===== -->
    <template v-if="state === 'auto_bound' || state === 'approved'">
      <view class="hero">
        <view class="hero-icon hero-icon--success">
          <text class="icon-text">✓</text>
        </view>
        <text class="hero-title">绑定成功，欢迎回来</text>
        <text class="hero-desc">已关联 {{ storeCount }} 家门店</text>

        <view class="store-list" v-if="storeNames.length">
          <view class="store-item" v-for="(sn, idx) in storeNames" :key="idx">
            <view class="store-icon-wrap">
              <text class="store-icon">🏪</text>
            </view>
            <text class="store-name">{{ sn }}</text>
          </view>
        </view>
      </view>
    </template>

    <!-- ===== 总部核验中 ===== -->
    <template v-else-if="state === 'pending'">
      <view class="hero">
        <view class="hero-icon hero-icon--pending">
          <text class="icon-text">⏳</text>
        </view>
        <text class="hero-title">登记申请已提交</text>
        <text class="hero-desc">总部核验通过后，会自动绑定当前微信并开放门店管理权限。</text>
      </view>

      <view class="info-card">
        <view class="info-row">
          <text class="info-label">姓名</text>
          <text class="info-value">{{ name }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">手机号</text>
          <text class="info-value">{{ phoneMasked }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">提交时间</text>
          <text class="info-value">{{ now }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">当前状态</text>
          <view class="info-badge badge--pending">
            <text>总部核验中</text>
          </view>
        </view>
      </view>
    </template>

    <!-- ===== 审核拒绝 ===== -->
    <template v-else-if="state === 'rejected'">
      <view class="hero">
        <view class="hero-icon hero-icon--rejected">
          <text class="icon-text">✕</text>
        </view>
        <text class="hero-title">信息未通过验证</text>
        <text class="hero-desc">请联系总部人工处理。为保障门店账号安全，页面不会展示具体未通过原因。</text>
      </view>

      <view class="info-card">
        <view class="info-row">
          <text class="info-label">姓名</text>
          <text class="info-value">{{ name }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">手机号</text>
          <text class="info-value">{{ phoneMasked }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">提交时间</text>
          <text class="info-value">{{ now }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">当前状态</text>
          <view class="info-badge badge--rejected">
            <text>未通过</text>
          </view>
        </view>
      </view>
    </template>

    <!-- 底部按钮 -->
    <view class="bottom-bar">
      <view v-if="state === 'auto_bound' || state === 'approved'" class="primary-btn" @click="goLogin">进入首页</view>
      <view v-else class="primary-btn" @click="goBack">返回</view>
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
$warning: #E58A2D;
$danger: #E05A47;

.page{min-height:100vh;background:$bg;display:flex;flex-direction:column;padding-bottom:calc(180rpx + env(safe-area-inset-bottom))}

.hero{display:flex;flex-direction:column;align-items:center;padding:96rpx 32rpx 0;text-align:center}
.hero-icon{width:160rpx;height:160rpx;border-radius:50%;display:flex;align-items:center;justify-content:center}
.hero-icon--success{background:#F1F8F3;color:$primary}
.hero-icon--pending{background:#FFF8EE;color:$warning}
.hero-icon--rejected{background:#FFF4F2;color:$danger}
.icon-text{font-size:84rpx;font-weight:700}
.hero-title{margin-top:40rpx;font-size:48rpx;font-weight:600;color:$text-1}
.hero-desc{margin-top:16rpx;max-width:600rpx;font-size:28rpx;color:$text-2;line-height:1.5}

.store-list{width:100%;margin-top:40rpx;display:flex;flex-direction:column;gap:16rpx}
.store-item{display:flex;align-items:center;gap:24rpx;padding:24rpx;border-radius:24rpx;border:1px solid $border;background:$surface}
.store-icon-wrap{width:72rpx;height:72rpx;border-radius:50%;background:$primary-soft;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.store-icon{font-size:38rpx}
.store-name{font-size:28rpx;font-weight:500;color:$text-1}

.info-card{margin:56rpx 32rpx 0;padding:32rpx;border-radius:24rpx;border:1px solid $border;background:$surface;box-shadow:0 4px 16px rgba(31,36,33,.04)}
.info-row{display:flex;align-items:center;justify-content:space-between;padding:16rpx 0}
.info-label{font-size:28rpx;color:$text-2;flex-shrink:0}
.info-value{font-size:28rpx;font-weight:500;color:$text-1;text-align:right}
.info-badge{padding:4rpx 16rpx;border-radius:8rpx;font-size:24rpx;font-weight:500}
.badge--pending{background:#FFF8EE;color:$warning}
.badge--rejected{background:#FFF4F2;color:$danger}

.bottom-bar{margin-top:auto;padding:56rpx 32rpx 0}
.primary-btn{width:100%;height:96rpx;border-radius:24rpx;background:$primary;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600;box-shadow:0 8px 24px rgba(31,36,33,.1)}
</style>
