<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { submitStaffRegistration, checkExistingApplication, checkApplicationStatus } from '@/api/staff'

const userStore = useUserStore()
const storeId = ref('')
const storeName = ref('')
const reapplyId = ref('')
const checking = ref(true)
const form = ref<any>({ name: '', mobile: '', gender: '男', birthday: '', expectedRole: '店员', employmentType: '全职', entryDate: '', emergencyContactName: '', emergencyContactPhone: '', remark: '' })
const roles = ['店员', '店长', '兼职']
const today = new Date().toISOString().slice(0, 10)
const saving = ref(false)

onLoad(async (query: any) => {
  storeId.value = query?.storeId || userStore.storeId
  storeName.value = query?.storeName ? decodeURIComponent(query.storeName) : userStore.storeName
  form.value.entryDate = new Date().toISOString().slice(0, 10)

  // 重新申请：预填之前的数据
  if (query?.applicationId) {
    reapplyId.value = query.applicationId
    try {
      const data: any = await checkApplicationStatus(reapplyId.value)
      if (data?.found) {
        storeId.value = data.storeId || storeId.value
        storeName.value = data.storeName || storeName.value
        form.value.name = data.name || ''
        form.value.mobile = data.mobile || ''
        form.value.gender = data.gender || '男'
        form.value.birthday = data.birthday || ''
        form.value.expectedRole = data.expectedRole || '店员'
        form.value.employmentType = data.employmentType || '全职'
        form.value.entryDate = data.entryDate || form.value.entryDate
        form.value.emergencyContactName = data.emergencyContactName || ''
        form.value.emergencyContactPhone = data.emergencyContactPhone || ''
        form.value.remark = data.remark || ''
      }
    } catch { /* ignore */ }
    checking.value = false
    return
  }

  // 自动检查该用户在此门店是否已有申请
  if (storeId.value) {
    try {
      const loginRes = await uni.login()
      const existing: any = await checkExistingApplication(loginRes.code, storeId.value)
      if (existing?.found) {
        uni.redirectTo({
          url: `/pages/staff/register-success/index?applicationId=${existing.applicationId}&name=${encodeURIComponent(existing.name || '')}&storeName=${encodeURIComponent(existing.storeName || storeName.value)}`,
        })
        return
      }
    } catch {
      // 检查失败，继续展示表单
    }
  }
  checking.value = false
})

async function submit() {
  if (saving.value) return
  if (!form.value.name || !form.value.mobile) { uni.showToast({ title: '请填写姓名和手机号', icon: 'none' }); return }
  saving.value = true
  try {
    const body: any = { ...form.value, storeId: storeId.value }
    if (reapplyId.value) body.applicationId = reapplyId.value
    const res: any = await submitStaffRegistration(body)
    uni.redirectTo({ url: `/pages/staff/register-success/index?applicationId=${res?.applicationId || ''}&name=${encodeURIComponent(form.value.name)}&storeName=${encodeURIComponent(storeName.value)}` })
  } finally { saving.value = false }
}
</script>

<template>
  <view class="page">
    <view class="header">
      <text class="ht">员工信息登记</text>
      <view class="hs">🏪 {{ storeName }}</view>
    </view>

    <view class="tip">请填写真实信息。提交后门店负责人会进行审核。</view>

    <view class="sec-title">个人信息</view>
    <view class="card">
      <view class="r"><text class="rk"><text class="req">*</text>姓名</text><input class="rv" v-model="form.name" placeholder="请输入真实姓名" /></view>
      <view class="r"><text class="rk"><text class="req">*</text>手机号</text><input class="rv" v-model="form.mobile" type="number" placeholder="请输入手机号" /></view>
      <view class="r"><text class="rk">性别</text>
        <view class="radio-group">
          <text class="radio" :class="{on:form.gender==='男'}" @click="form.gender='男'">男</text>
          <text class="radio" :class="{on:form.gender==='女'}" @click="form.gender='女'">女</text>
        </view>
      </view>
      <picker mode="date" :value="form.birthday" :end="today" @change="(e:any)=>form.birthday=e.detail.value">
        <view class="r last"><text class="rk">出生日期</text><text class="rv" :class="{sel:!form.birthday}">{{ form.birthday || '请选择' }} ›</text></view>
      </picker>
    </view>

    <view class="sec-title">任职信息</view>
    <view class="card">
      <view class="r"><text class="rk">所属门店</text><text class="rv plain">{{ storeName }}</text></view>
      <picker :range="roles" @change="(e:any)=>form.expectedRole=roles[e.detail.value]">
        <view class="r"><text class="rk"><text class="req">*</text>期望岗位</text><text class="rv sel">{{ form.expectedRole }} ›</text></view>
      </picker>
      <view class="r"><text class="rk"><text class="req">*</text>入职类型</text>
        <view class="radio-group">
          <text class="radio" :class="{on:form.employmentType==='全职'}" @click="form.employmentType='全职'">全职</text>
          <text class="radio" :class="{on:form.employmentType==='兼职'}" @click="form.employmentType='兼职'">兼职</text>
        </view>
      </view>
      <view class="r last"><text class="rk"><text class="req">*</text>入职日期</text><input class="rv" v-model="form.entryDate" /></view>
    </view>

    <view class="sec-title">紧急联系人 <text class="opt">选填</text></view>
    <view class="card">
      <view class="r"><text class="rk">联系人姓名</text><input class="rv" v-model="form.emergencyContactName" placeholder="请输入" /></view>
      <view class="r last"><text class="rk">联系人电话</text><input class="rv" v-model="form.emergencyContactPhone" placeholder="请输入" /></view>
    </view>

    <view class="sec-title">补充说明 <text class="opt">选填</text></view>
    <view class="card">
      <textarea class="remark" v-model="form.remark" placeholder="可填写需要说明的情况" />
    </view>

    <view class="info">提交后负责人可检查和修改员工信息，审批通过后系统根据岗位开通功能。</view>

    <view class="bottom"><view class="btn" @click="submit">{{ saving ? '提交中...' : '提交登记申请' }}</view></view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 160rpx}
.header{padding:8rpx 0 16rpx}.ht{display:block;font-size:40rpx;font-weight:700;color:$t1}.hs{display:block;margin-top:12rpx;font-size:26rpx;color:$t2}
.tip{padding:20rpx;border-radius:12rpx;background:rgba($ps,.5);font-size:26rpx;color:$t2;margin-bottom:24rpx}
.sec-title{font-size:26rpx;font-weight:600;color:$t3;margin-bottom:12rpx}.opt{font-weight:400;color:$t3}
.card{background:$s;border-radius:20rpx;padding:0;border:2rpx solid $b;margin-bottom:24rpx}
.r{display:flex;justify-content:space-between;align-items:center;padding:24rpx 28rpx;border-bottom:2rpx solid #EEF1EF}.r.last{border:0}
.rk{font-size:28rpx;color:$t1;flex-shrink:0}.req{color:#E05A47;margin-right:4rpx}
.rv{text-align:right;font-size:28rpx;color:$t1;background:transparent;border:none;flex:1}.rv.plain{color:$t2}.sel{color:$p}
.radio-group{display:flex;gap:16rpx}.radio{padding:8rpx 24rpx;border-radius:999rpx;border:2rpx solid $b;font-size:26rpx;color:$t2}.radio.on{background:$ps;border-color:$p;color:$p}
.remark{width:100%;height:120rpx;padding:24rpx 28rpx;font-size:28rpx;border:none;background:transparent;box-sizing:border-box}
.info{padding:20rpx;border-radius:12rpx;background:#FAFBF9;font-size:26rpx;color:$t2;margin-bottom:24rpx}
.bottom{position:fixed;left:0;right:0;bottom:0;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:$s;border-top:2rpx solid #EEF1EF}
.btn{width:100%;height:88rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700}
</style>
