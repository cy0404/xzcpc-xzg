<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { fetchStaffDetail, resignStaff } from '@/api/staff'
import { useUserStore } from '@/store/user'
import Skeleton from '@/components/Skeleton.vue'

const userStore = useUserStore()
const loading = ref(true)
const detail = ref<any>({})
const showResign = ref(false)
const resignDate = ref('')
const resignReason = ref('')
const resignNote = ref('')
const resigning = ref(false)
const resignReasons = ['主动离职', '门店调整', '转店', '试用期未通过', '其他原因']
const isManager = computed(() => userStore.permissions.includes('staff:manage'))

const displayStatus = computed(() => {
  if (!detail.value || detail.value.status === '离职') return detail.value?.status || ''
  if (detail.value.leaveDate && detail.value.leaveDate > new Date().toISOString().slice(0, 10)) return '待离职'
  return detail.value.status || ''
})

const isEditable = computed(() => isManager.value && displayStatus.value === '在职')

const currentEmployeeId = ref('')

onLoad((query: any) => {
  currentEmployeeId.value = query?.employeeId || ''
})

onShow(() => {
  if (currentEmployeeId.value) loadDetail(currentEmployeeId.value)
})

async function loadDetail(id: string) {
  loading.value = true
  try { detail.value = (await fetchStaffDetail(id)) || {} }
  finally { loading.value = false }
}
function goEdit() { uni.navigateTo({ url: `/pages/staff/edit/index?employeeId=${detail.value.employeeId}` }) }
async function confirmResign() {
  if (resigning.value) return
  if (!resignDate.value || !resignReason.value) { uni.showToast({ title: '请填写日期和原因', icon: 'none' }); return }
  resigning.value = true
  try {
    await resignStaff(detail.value.employeeId, { resignDate: resignDate.value, resignReason: resignReason.value, remark: resignNote.value })
    uni.showToast({ title: '离职已办理', icon: 'success' })
    showResign.value = false
    setTimeout(() => uni.navigateBack(), 800)
  } catch { /* handled */ }
  finally { resigning.value = false }
}
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="7" />
    <template v-else>
      <view class="head-card">
        <view class="head-left">
          <view class="avatar">👤</view>
          <view>
            <view class="name-row">
              <text class="name">{{ detail.name }}</text>
              <text class="tag tag-status" :class="{ 'tag-pending': displayStatus === '待离职' }">{{ displayStatus }}</text>
            </view>
            <text class="sub">{{ detail.role }}  |  {{ detail.storeName }}</text>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="card-h"><text class="card-t">基础信息</text><text v-if="isEditable" class="card-link" @click="goEdit">编辑</text></view>
        <view class="row"><text class="k">手机号</text><text class="v">{{ detail.mobile }}</text></view>
        <view class="row"><text class="k">性别</text><text class="v">{{ detail.gender }}</text></view>
        <view class="row"><text class="k">出生日期</text><text class="v">{{ detail.birthday }}</text></view>
        <view class="row"><text class="k">入职日期</text><text class="v">{{ detail.entryDate }}</text></view>
        <view class="row" v-if="detail.leaveDate"><text class="k">离职日期</text><text class="v">{{ detail.leaveDate }}</text></view>
        <view class="row"><text class="k">用工类型</text><text class="v">{{ detail.employmentType }}</text></view>
        <view class="row"><text class="k">紧急联系人</text><text class="v">{{ detail.emergencyContactName || '--' }}</text></view>
        <view class="row last"><text class="k">联系电话</text><text class="v">{{ detail.emergencyContactPhone || '--' }}</text></view>
      </view>

      <view class="sec-label">员工管理</view>
      <view v-for="t in ['排班记录','培训记录','任务记录']" :key="t" class="upcoming"><text class="up-icon">📋</text><text class="up-text">{{ t }}</text><text class="up-badge">即将上线</text></view>
    </template>

    <view v-if="isEditable" class="bottom">
      <view class="btn-outline" @click="goEdit">编辑员工信息</view>
      <view class="btn-danger" @click="showResign = true">办理离职</view>
    </view>

    <!-- 离职弹窗 -->
    <view v-if="showResign" class="mask" @click="showResign = false">
      <view class="sheet" @click.stop>
        <view class="sh"></view>
        <view class="sheet-h"><text class="st">办理离职</text><text class="sx" @click="showResign = false">×</text></view>
        <view class="sheet-b">
          <view class="warn">办理离职后进入已离职列表，历史记录保留。</view>
          <view class="emp"><text class="ea">👤</text><view><text class="en">{{ detail.name }}</text><text class="er">{{ detail.role }}</text></view></view>
          <view class="f">
            <text class="fl">离职日期 *</text>
            <picker mode="date" @change="(e:any) => resignDate = e.detail.value">
              <view class="fi picker-row">
                <text :class="{ placeholder: !resignDate }">{{ resignDate || '请选择日期' }}</text>
                <text class="picker-arrow">📅</text>
              </view>
            </picker>
          </view>
          <view class="f">
            <text class="fl">离职原因 *</text>
            <picker :range="resignReasons" @change="(e:any) => resignReason = resignReasons[e.detail.value]">
              <view class="fi picker-row">
                <text :class="{ placeholder: !resignReason }">{{ resignReason || '请选择离职原因' }}</text>
                <text class="picker-arrow">▾</text>
              </view>
            </picker>
          </view>
          <view class="f"><text class="fl">补充说明</text><textarea class="ft" v-model="resignNote" placeholder="选填" /></view>
        </view>
        <view class="sheet-act"><view class="ba" @click="showResign=false">取消</view><view class="bb" @click="confirmResign">确认离职</view></view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 200rpx}
.head-card{display:flex;align-items:center;background:$s;border-radius:20rpx;padding:28rpx;border:2rpx solid $b}
.head-left{display:flex;align-items:center;gap:24rpx}
.avatar{width:128rpx;height:128rpx;border-radius:50%;background:#EEF1EF;display:flex;align-items:center;justify-content:center;font-size:56rpx}
.name{font-size:36rpx;font-weight:700;color:$t1}.name-row{display:flex;align-items:center;gap:8rpx}.sub{display:block;margin-top:8rpx;font-size:26rpx;color:$t2}
.tag{padding:4rpx 16rpx;border-radius:999rpx;font-size:22rpx;font-weight:600}.tag-status{background:$ps;color:$p}.tag-pending{background:#FFF8EE;color:#E58A2D}
.card{background:$s;border-radius:20rpx;padding:28rpx;margin-top:24rpx;border:2rpx solid $b}.card-sm{padding:24rpx}
.card-h{display:flex;justify-content:space-between;margin-bottom:16rpx}.card-t{font-size:28rpx;font-weight:700;color:$t1}.card-link{font-size:26rpx;color:$p;font-weight:600}
.row{display:flex;justify-content:space-between;padding:16rpx 0;border-bottom:2rpx solid #EEF1EF}.row.last{border:0}.k{color:$t2;font-size:28rpx}.v{color:$t1;font-size:28rpx}
.role-tip{display:flex;gap:16rpx}.rt{font-size:28rpx;font-weight:700;color:$t1;display:block}.rd{font-size:24rpx;color:$t2;margin-top:4rpx}
.sec-label{font-size:26rpx;font-weight:600;color:$t2;margin-top:68rpx;margin-bottom:20rpx}
.upcoming{display:flex;align-items:center;gap:20rpx;padding:32rpx;background:$s;border:2rpx solid $b;border-radius:16rpx;margin-bottom:16rpx;opacity:.7}.up-icon{font-size:32rpx}.up-text{flex:1;font-size:28rpx;color:$t2}.up-badge{padding:4rpx 16rpx;border-radius:999rpx;border:2rpx solid $b;font-size:20rpx;color:$t3}
.bottom{position:fixed;left:0;right:0;bottom:0;display:flex;gap:20rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:#fff;border-top:2rpx solid #EEF1EF}
.btn-outline{flex:1;height:80rpx;border-radius:16rpx;border:2rpx solid $b;color:$p;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.btn-danger{flex:1;height:80rpx;border-radius:16rpx;border:2rpx solid $d;color:$d;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;max-height:85vh;border-radius:32rpx 32rpx 0 0;background:#fff;display:flex;flex-direction:column}
.sh{width:80rpx;height:8rpx;border-radius:999rpx;background:$b;margin:20rpx auto}
.sheet-h{display:flex;justify-content:center;padding:8rpx 32rpx 20rpx;position:relative}.st{font-size:36rpx;font-weight:700;color:$t1}.sx{position:absolute;right:32rpx;font-size:44rpx;color:$t2}
.sheet-b{flex:1;overflow-y:auto;padding:0 32rpx;display:flex;flex-direction:column;gap:24rpx}
.warn{padding:20rpx;border-radius:12rpx;background:#FFF4F2;color:$t2;font-size:26rpx;border:2rpx solid rgba(224,90,71,.15)}
.emp{display:flex;align-items:center;gap:20rpx;padding:20rpx;background:#FAFBF9;border-radius:16rpx}.ea{font-size:48rpx}.en{font-size:28rpx;font-weight:700;color:$t1;display:block}.er{font-size:24rpx;color:$t2}
.f{display:flex;flex-direction:column;gap:12rpx}.fl{font-size:28rpx;font-weight:600;color:$t1}.fi{padding:20rpx;border:2rpx solid $b;border-radius:12rpx;font-size:28rpx;background:#FAFBF9}
.picker-row{display:flex;justify-content:space-between;align-items:center}.picker-arrow{font-size:28rpx;color:$t3}.placeholder{color:$t3}
.ft{padding:20rpx;border:2rpx solid $b;border-radius:12rpx;font-size:28rpx;background:#FAFBF9;height:140rpx}
.sheet-act{display:flex;gap:20rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);border-top:2rpx solid #EEF1EF}
.ba{flex:1;height:88rpx;border-radius:16rpx;border:2rpx solid $b;color:$t1;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.bb{flex:2;height:88rpx;border-radius:16rpx;background:$d;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
</style>
