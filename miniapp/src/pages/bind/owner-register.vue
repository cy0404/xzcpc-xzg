<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { queryOwnerStores, confirmOwnerBind } from '@/api/auth'

const bindCode = ref('')
const form = ref({ name: '', phone: '' })
const phoneError = ref(false)
const submitting = ref(false)
const binding = ref(false)
const agreed = ref(false)

type Step = 'form' | 'select' | 'result'
const step = ref<Step>('form')
const matchedStores = ref<any[]>([])
const selectedIds = ref<Set<string>>(new Set())
const result = ref<any>(null)

onLoad((query: any) => {
  bindCode.value = query?.scene || query?.code || ''
  if (!bindCode.value) {
    uni.showToast({ title: '缺少绑定码，请重新扫码', icon: 'none' })
  }
})

function onPhoneInput(e: any) {
  form.value.phone = (e.detail.value || '').replace(/\D/g, '').slice(0, 11)
  phoneError.value = false
}

async function handleQuery() {
  if (!form.value.name.trim()) {
    uni.showToast({ title: '请输入姓名', icon: 'none' }); return
  }
  if (!/^1[3-9]\d{9}$/.test(form.value.phone)) {
    phoneError.value = true; return
  }
  if (!bindCode.value) return

  submitting.value = true
  try {
    const loginRes: any = await uni.login()
    const data: any = await queryOwnerStores(bindCode.value, loginRes.code, form.value.name.trim(), form.value.phone)

    if (!data?.stores?.length) {
      uni.showModal({
        title: '暂无关联门店',
        content: '未找到关联的门店信息，请核对姓名和手机号是否正确。',
        showCancel: false,
        confirmText: '知道了',
        success: () => { submitting.value = false },
      })
      return
    }

    matchedStores.value = data.stores
    selectedIds.value = new Set()
    step.value = 'select'
  } catch {
    /* request 已 toast */
  } finally {
    submitting.value = false
  }
}

function toggleStore(storeId: string) {
  const s = new Set(selectedIds.value)
  if (s.has(storeId)) s.delete(storeId); else s.add(storeId)
  selectedIds.value = s
}

async function handleConfirmBind() {
  if (selectedIds.value.size === 0) {
    uni.showToast({ title: '请至少选择一个门店', icon: 'none' }); return
  }
  binding.value = true
  try {
    const loginRes: any = await uni.login()
    const data: any = await confirmOwnerBind({
      bindCode: bindCode.value,
      wxCode: loginRes.code,
      name: form.value.name.trim(),
      phone: form.value.phone,
      storeIds: [...selectedIds.value],
    })

    result.value = data
    step.value = 'result'
  } catch {
    /* request 已 toast */
  } finally {
    binding.value = false
  }
}

function goBack() { step.value = 'form'; matchedStores.value = []; selectedIds.value = new Set() }
function contactHQ() {
  uni.showModal({ title: '联系总部', content: '请联系总部人工处理', showCancel: false })
}
function goLogin() {
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<template>
  <view class="page">

    <!-- ===== 表单 ===== -->
    <template v-if="step === 'form'">
      <view class="header">
        <view class="hint">二维码 24 小时有效</view>
      </view>
      <view class="title-area">
        <text class="label">老板微信绑定</text>
        <text class="title">象子茶铺老板登记</text>
        <text class="desc">输入姓名和手机号，系统自动匹配您的门店。</text>
      </view>
      <view class="section">
        <view class="sec-title">登记信息</view>
        <view class="card">
          <view class="r">
            <text class="rk"><text class="req">*</text>姓名</text>
            <input class="rv" v-model="form.name" placeholder="请输入真实姓名" />
          </view>
          <view class="r">
            <text class="rk"><text class="req">*</text>手机号</text>
            <input class="rv" :class="{ err: phoneError }" v-model="form.phone" type="number" maxlength="11" placeholder="总部预留手机号" @input="onPhoneInput" />
          </view>
          <text class="err-tip" v-if="phoneError">请输入11位手机号（1开头）</text>
        </view>
      </view>
      <view class="bar">
        <view class="bar-icon">ℹ️</view>
        <text class="bar-text">姓名和手机号用于匹配您的门店信息。匹配到的门店需要您确认绑定。</text>
      </view>
      <view class="bottom">
        <view class="agree-row" @click="agreed = !agreed">
          <view class="agree-check" :class="{ on: agreed }">
            <text v-if="agreed">✓</text>
          </view>
          <text class="agree-text">已阅读并同意《用户服务协议》和《隐私政策》</text>
        </view>
        <view class="btn" :class="{ loading: submitting || !agreed }" @click="agreed ? handleQuery() : uni.showToast({ title: '请先同意协议', icon: 'none' })">
          {{ submitting ? '匹配中...' : '提交校验' }}
        </view>
        <view class="skip-link" @click="goLogin">进入小程序</view>
      </view>
    </template>

    <!-- ===== 门店选择 ===== -->
    <template v-if="step === 'select'">
      <view class="header">
        <view class="hint">{{ matchedStores.length }} 家门店匹配</view>
      </view>
      <view class="title-area">
        <text class="label">选择要绑定的门店</text>
        <text class="title">确认您的门店</text>
        <text class="desc">以下门店与您填写的姓名和手机号匹配，请选择需要绑定的门店（可多选）。</text>
      </view>
      <view class="store-list">
        <view v-for="store in matchedStores" :key="store.storeId" class="store-item" :class="{ selected: selectedIds.has(store.storeId), disabled: store.alreadyBound }" @click="store.alreadyBound ? null : toggleStore(store.storeId)">
          <view class="si-check" :class="{ on: selectedIds.has(store.storeId) || store.alreadyBound }">
            <text v-if="selectedIds.has(store.storeId)">✓</text>
            <text v-else-if="store.alreadyBound">✓</text>
          </view>
          <view class="si-info">
            <text class="si-name">{{ store.storeName }}</text>
            <text class="si-tag" v-if="store.alreadyBound">已绑定</text>
          </view>
        </view>
      </view>
      <view class="bottom select-step">
        <view class="btn-outline" @click="goBack">重新填写</view>
        <view class="btn" :class="{ loading: binding }" @click="handleConfirmBind">
          {{ binding ? '绑定中...' : `确认绑定 (${selectedIds.size})` }}
        </view>
      </view>
    </template>

    <!-- ===== 绑定成功 ===== -->
    <template v-if="step === 'result'">
      <view class="hero">
        <view class="hero-icon">✓</view>
        <text class="hero-title">绑定成功</text>
        <text class="hero-desc">已绑定 {{ result?.storeCount || 0 }} 家门店</text>
        <view class="store-cards" v-if="result?.storeNames?.length">
          <view class="sc-result" v-for="(sn, idx) in result.storeNames" :key="idx">
            <text class="scr-name">🏪 {{ sn }}</text>
          </view>
        </view>
      </view>
      <view class="bottom">
        <view class="btn" @click="goLogin">进入首页</view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
$bg: #F7F8F6; $s: #fff; $p: #2F8F57; $ps: #E7F4EB; $t1: #1F2421; $t2: #66706A; $t3: #98A19C; $b: #E8ECE9;

.page { min-height: 100vh; background: $bg; padding-bottom: 160rpx; }

.header { display: flex; justify-content: flex-end; padding: 24rpx 32rpx; }
.hint { font-size: 24rpx; color: $t2; background: $s; padding: 8rpx 24rpx; border-radius: 100rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.06); }

.title-area { padding: 24rpx 32rpx 0; }
.label { font-size: 28rpx; font-weight: 600; color: $p; }
.title { display: block; margin-top: 8rpx; font-size: 48rpx; font-weight: 700; color: $t1; line-height: 60rpx; }
.desc { display: block; margin-top: 16rpx; font-size: 26rpx; color: $t2; line-height: 40rpx; }

.section { padding: 40rpx 32rpx 0; }
.sec-title { font-size: 26rpx; font-weight: 600; color: $t2; margin-bottom: 20rpx; }
.card { background: $s; border-radius: 24rpx; overflow: hidden; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.04); }
.r { display: flex; align-items: center; justify-content: space-between; min-height: 110rpx; padding: 0 32rpx; border-bottom: 1px solid #EEF1EF; gap: 20rpx; }
.rk { flex-shrink: 0; font-size: 28rpx; font-weight: 600; color: $t1; }
.req { color: #E05A47; }
.rv { flex: 1; min-width: 0; text-align: right; font-size: 30rpx; color: $t1; background: transparent; }
.err-tip { display: block; padding: 0 32rpx 12rpx; font-size: 24rpx; color: #E05A47; }

.bar { display: flex; margin: 32rpx 32rpx 0; padding: 24rpx; border-radius: 20rpx; background: #FAFBF9; border: 1px solid $b; gap: 16rpx; }
.bar-icon { flex-shrink: 0; font-size: 32rpx; }
.bar-text { font-size: 24rpx; color: $t2; line-height: 36rpx; }

/* 门店选择 */
.store-list { padding: 24rpx 32rpx; display: flex; flex-direction: column; gap: 16rpx; }
.store-item { display: flex; align-items: center; gap: 20rpx; padding: 28rpx; background: $s; border-radius: 20rpx; border: 2rpx solid $b; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.04); }
.store-item.selected { border-color: $p; background: #F1F8F3; }
.store-item.disabled { opacity: 0.6; }
.si-check { width: 44rpx; height: 44rpx; border-radius: 8rpx; border: 2rpx solid #C4C9C7; display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: #fff; flex-shrink: 0; }
.si-check.on { background: $p; border-color: $p; }
.si-info { flex: 1; display: flex; align-items: center; gap: 12rpx; }
.si-name { font-size: 30rpx; font-weight: 600; color: $t1; }
.si-tag { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 999rpx; background: $ps; color: $p; }

/* 结果 */
.hero { display: flex; flex-direction: column; align-items: center; padding: 100rpx 32rpx 0; text-align: center; }
.hero-icon { width: 140rpx; height: 140rpx; border-radius: 50%; background: #F1F8F3; color: $p; display: flex; align-items: center; justify-content: center; font-size: 64rpx; margin-bottom: 32rpx; }
.hero-title { font-size: 44rpx; font-weight: 700; color: $t1; }
.hero-desc { margin-top: 16rpx; font-size: 26rpx; color: $t2; }
.store-cards { width: 100%; margin-top: 48rpx; display: flex; flex-direction: column; gap: 16rpx; }
.sc-result { padding: 24rpx; border-radius: 16rpx; background: $s; border: 1px solid $b; }
.scr-name { font-size: 28rpx; font-weight: 500; color: $t1; }

/* 底部 */
.agree-row { display: flex; align-items: flex-start; gap: 12rpx; padding: 0 0 12rpx; }
.agree-check { width: 36rpx; height: 36rpx; border-radius: 8rpx; border: 2rpx solid #C4C9C7; background: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 2rpx; }
.agree-check.on { background: $p; border-color: $p; color: #fff; font-size: 24rpx; font-weight: 700; }
.agree-text { flex: 1; font-size: 22rpx; color: $t2; line-height: 36rpx; }

.bottom { position: fixed; bottom: 0; left: 0; right: 0; padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom)); background: rgba(255,255,255,0.95); border-top: 1px solid #EEF1EF; display: flex; flex-direction: column; gap: 12rpx; }
.skip-link { text-align: center; font-size: 24rpx; color: $p; padding: 8rpx 0; font-weight: 500; }
.btn { flex: none; width: 100%; height: 110rpx; border-radius: 16rpx; background: $p; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 30rpx; font-weight: 700; box-shadow: 0 8rpx 24rpx rgba(31,36,33,0.1); }
.btn.loading { opacity: 0.6; }
.btn-outline { flex: none; width: 100%; height: 110rpx; border-radius: 16rpx; background: $s; border: 2rpx solid $b; display: flex; align-items: center; justify-content: center; color: $t1; font-size: 30rpx; font-weight: 700; }
.bottom.select-step { flex-direction: column; }
.bottom.select-step .btn, .bottom.select-step .btn-outline { flex: none; width: 100%; }
</style>
