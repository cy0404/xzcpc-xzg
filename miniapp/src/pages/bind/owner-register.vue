<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const bindCode = ref('')
const form = ref({ name: '', phone: '', birthday: '' })
const phoneError = ref(false)
const submitting = ref(false)

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

function onBirthdayChange(e: any) {
  form.value.birthday = e.detail.value
}

function validate(): boolean {
  if (!form.value.name.trim()) {
    uni.showToast({ title: '请输入姓名', icon: 'none' })
    return false
  }
  if (!/^1[3-9]\d{9}$/.test(form.value.phone)) {
    phoneError.value = true
    return false
  }
  if (!form.value.birthday) {
    uni.showToast({ title: '请选择生日', icon: 'none' })
    return false
  }
  return true
}

async function handleSubmit() {
  if (submitting.value) return
  if (!validate() || !bindCode.value) return
  submitting.value = true

  try {
    const loginRes: any = await uni.login()
    const data: any = await request({
      url: '/auth/owner/bind',
      method: 'POST',
      data: {
        bindCode: bindCode.value,
        wxCode: loginRes.code,
        name: form.value.name.trim(),
        phone: form.value.phone,
        birthday: form.value.birthday,
      },
    })

    const params = [
      `bindCode=${bindCode.value}`,
      `state=${data.status}`,
      `name=${encodeURIComponent(form.value.name)}`,
      `phone=${form.value.phone}`,
    ]
    if (data.status === 'auto_bound') {
      params.push(`storeCount=${data.storeCount || 0}`)
      params.push(`storeNames=${encodeURIComponent((data.storeNames || []).join(','))}`)
    }

    uni.redirectTo({ url: `/pages/bind/owner-register-status?${params.join('&')}` })
  } catch {
    // request 已 toast 错误信息
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="header">
      <view class="hint">二维码 24 小时有效</view>
    </view>

    <view class="title-area">
      <text class="label">老板微信绑定申请</text>
      <text class="title">象子茶铺老板登记表</text>
      <text class="desc">系统自动匹配门店信息，匹配成功即时绑定；未匹配则提交总部核验。</text>
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
        <picker mode="date" :value="form.birthday" :end="new Date().toISOString().slice(0,10)" @change="onBirthdayChange">
          <view class="r last">
            <text class="rk"><text class="req">*</text>生日</text>
            <text class="rv sel">{{ form.birthday || '请选择' }} ›</text>
          </view>
        </picker>
      </view>
    </view>

    <view class="bar">
      <view class="bar-icon">ℹ️</view>
      <text class="bar-text">手机号与生日用于自动匹配门店信息。匹配成功立即绑定，匹配失败由总部人工审核。</text>
    </view>

    <view class="bottom">
      <view class="btn" :class="{ loading: submitting }" @click="handleSubmit">
        {{ submitting ? '提交中...' : '提交验证' }}
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #F7F8F6; padding-bottom: 160rpx; }

// 顶栏
.header { display: flex; justify-content: flex-end; padding: 24rpx 32rpx; }
.hint { font-size: 24rpx; color: #66706A; background: #fff; padding: 8rpx 24rpx; border-radius: 100rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.06); }

// 标题
.title-area { padding: 24rpx 32rpx 0; }
.label { font-size: 28rpx; font-weight: 600; color: #2F8F57; }
.title { display: block; margin-top: 8rpx; font-size: 48rpx; font-weight: 700; color: #1F2421; line-height: 60rpx; }
.desc { display: block; margin-top: 16rpx; font-size: 26rpx; color: #66706A; line-height: 40rpx; }

// 表单
.section { padding: 40rpx 32rpx 0; }
.sec-title { font-size: 26rpx; font-weight: 600; color: #66706A; margin-bottom: 20rpx; }
.card { background: #fff; border-radius: 24rpx; overflow: hidden; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.04); }
.r { display: flex; align-items: center; justify-content: space-between; min-height: 100rpx; padding: 0 32rpx; border-bottom: 1px solid #EEF1EF; gap: 20rpx; }
.last { border-bottom: 0; }
.rk { flex-shrink: 0; font-size: 28rpx; font-weight: 600; color: #1F2421; }
.req { color: #E05A47; }
.rv { flex: 1; min-width: 0; text-align: right; font-size: 30rpx; color: #1F2421; background: transparent; }
.rv.sel { color: #98A19C; }
.rv.err { /* error state */ }
.err-tip { display: block; padding: 0 32rpx 12rpx; font-size: 24rpx; color: #E05A47; }

// 提示条
.bar { display: flex; margin: 32rpx 32rpx 0; padding: 24rpx; border-radius: 20rpx; background: #FAFBF9; border: 1px solid #E8ECE9; gap: 16rpx; }
.bar-icon { flex-shrink: 0; font-size: 32rpx; }
.bar-text { font-size: 24rpx; color: #66706A; line-height: 36rpx; }

// 底部
.bottom { position: fixed; bottom: 0; left: 0; right: 0; padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom)); background: rgba(255,255,255,0.95); border-top: 1px solid #EEF1EF; }
.btn { height: 96rpx; border-radius: 24rpx; background: #2F8F57; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 30rpx; font-weight: 600; box-shadow: 0 8rpx 24rpx rgba(31,36,33,0.1); }
.btn.loading { opacity: 0.6; }
</style>
