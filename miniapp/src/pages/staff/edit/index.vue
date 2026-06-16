<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchStaffDetail, updateStaff } from '@/api/staff'
import Skeleton from '@/components/Skeleton.vue'

const loading = ref(true)
const saving = ref(false)
const employeeId = ref('')
const form = ref<any>({ name: '', mobile: '', gender: '', birthday: '', emergencyContactName: '', emergencyContactPhone: '', storeName: '', storeId: '', role: '', employmentType: '', entryDate: '', remark: '' })
const roles = ['店长', '店员', '兼职']
const empTypes = ['全职', '兼职', '临时工']
const today = new Date().toISOString().slice(0, 10)

onLoad((query: any) => {
  employeeId.value = query?.employeeId || ''
  if (employeeId.value) loadDetail()
  else loading.value = false
})

async function loadDetail() {
  loading.value = true
  try {
    const data = await fetchStaffDetail(employeeId.value)
    if (data) {
      form.value.name = data.name || ''
      form.value.mobile = data.mobile || ''
      form.value.gender = data.gender || ''
      form.value.birthday = data.birthday || ''
      form.value.emergencyContactName = data.emergencyContactName || ''
      form.value.emergencyContactPhone = data.emergencyContactPhone || ''
      form.value.storeName = data.storeName || ''
      form.value.storeId = data.storeId || ''
      form.value.role = data.role || '店员'
      form.value.employmentType = data.employmentType || '全职'
      form.value.entryDate = data.entryDate || ''
      form.value.remark = data.remark || ''
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

function onGenderChange(e: any) { form.value.gender = ['男','女'][e.detail.value] }
function onBirthdayChange(e: any) { form.value.birthday = e.detail.value }
function onRoleChange(e: any) { form.value.role = roles[e.detail.value] }
function onEmpTypeChange(e: any) { form.value.employmentType = empTypes[e.detail.value] }

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    await updateStaff(employeeId.value, form.value)
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch { /* handled */ }
  finally { saving.value = false }
}
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="6" />
    <template v-else>
    <!-- 个人信息 -->
    <view class="card">
      <text class="card-title">个人信息</text>
      <view class="row"><text class="k">姓名 *</text><input class="v" v-model="form.name" placeholder="请输入" /></view>
      <view class="row"><text class="k">手机号 *</text><input class="v" v-model="form.mobile" placeholder="请输入" /></view>
      <picker :range="['男','女']" @change="onGenderChange">
        <view class="row"><text class="k">性别</text><text class="v sel">{{ form.gender || '请选择' }} ›</text></view>
      </picker>
      <picker mode="date" :value="form.birthday" :end="today" @change="onBirthdayChange">
        <view class="row"><text class="k">出生日期</text><text class="v sel">{{ form.birthday || '请选择' }} ›</text></view>
      </picker>
      <view class="row"><text class="k">紧急联系人</text><input class="v" v-model="form.emergencyContactName" placeholder="选填" /></view>
      <view class="row last"><text class="k">紧急联系电话</text><input class="v" v-model="form.emergencyContactPhone" placeholder="选填" /></view>
    </view>

    <!-- 任职信息 -->
    <view class="card">
      <text class="card-title">任职信息</text>
      <view class="row"><text class="k">所属门店 *</text><text class="v">{{ form.storeName }}</text></view>
      <picker :range="roles" @change="onRoleChange">
        <view class="row"><text class="k">岗位 *</text><text class="v sel">{{ form.role || '请选择' }} ›</text></view>
      </picker>
      <picker :range="empTypes" @change="onEmpTypeChange">
        <view class="row"><text class="k">用工类型 *</text><text class="v sel">{{ form.employmentType || '请选择' }} ›</text></view>
      </picker>
      <view class="row last"><text class="k">入职日期 *</text><text class="v">{{ form.entryDate }}</text></view>
    </view>

    <!-- 补充说明 -->
    <view class="card">
      <text class="card-title">补充说明</text>
      <textarea class="remark" v-model="form.remark" placeholder="填写其他备注" />
    </view>

    <view class="tip">员工信息变更后，历史记录仍会保留。</view>

    <view class="bottom"><view class="btn" @click="save">{{ saving ? '保存中...' : '保存修改' }}</view></view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 160rpx}
.card{background:$s;border-radius:20rpx;padding:28rpx;margin-bottom:24rpx;border:2rpx solid $b}
.card-title{display:block;font-size:26rpx;font-weight:600;color:$t3;margin-bottom:16rpx}
.row{display:flex;justify-content:space-between;align-items:center;padding:20rpx 0;border-bottom:2rpx solid #EEF1EF}.row.last{border:0}
.k{font-size:28rpx;color:$t1;flex-shrink:0}.v{text-align:right;font-size:28rpx;color:$t1;background:transparent;border:none}.sel{color:$p}
.remark{width:100%;height:140rpx;padding:20rpx;border:2rpx solid $b;border-radius:12rpx;font-size:28rpx;background:#FAFBF9;box-sizing:border-box}
.tip{padding:20rpx;border-radius:12rpx;background:#FAFBF9;color:$t2;font-size:24rpx}
.bottom{position:fixed;left:0;right:0;bottom:0;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:#fff}
.btn{width:100%;height:88rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700}
</style>
