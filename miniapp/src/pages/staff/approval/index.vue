<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { approveStaffApplication, fetchStaffApplications, fetchStaffApplicationDetail } from '@/api/staff'
import { useUserStore } from '@/store/user'
import EmptyState from '@/components/EmptyState.vue'

const userStore = useUserStore()
const tab = ref('pending')
const list = ref<any[]>([])
const loading = ref(true)
const detail = ref<any>(null)
const roleVal = ref('店员')
const empTypeVal = ref('全职')
const showReject = ref(false)
const rejectReason = ref('')
const roles = ['店长', '店员', '兼职']
const empTypes = ['全职', '兼职', '临时工']
const rejectTags = ['手机号填写错误', '岗位选择错误', '入职日期错误', '信息不完整', '其他']

const tabs = [{ label: '待审批', value: 'pending' }, { label: '已通过', value: 'approved' }, { label: '已驳回', value: 'rejected' }]

onShow(() => {
  if (!userStore.permissions.includes('staff:manage')) {
    uni.showToast({ title: '无权限', icon: 'none' })
    uni.navigateBack()
    return
  }
  loadList()
})

async function loadList() {
  loading.value = true
  try { list.value = (await fetchStaffApplications(tab.value)) as any[] }
  finally { loading.value = false }
}

function changeTab(v: string) { tab.value = v; detail.value = null; loadList() }
async function openDetail(item: any) {
  detail.value = await fetchStaffApplicationDetail(item.applicationId)
  roleVal.value = detail.value?.expectedRole || '店员'
  empTypeVal.value = detail.value?.employmentType || '全职'
}

function backToList() { detail.value = null }

async function approve() {
  await approveStaffApplication(detail.value.applicationId, { action: 'approve', role: roleVal.value, employmentType: empTypeVal.value })
  uni.showToast({ title: '审批通过', icon: 'success' })
  detail.value = null; loadList()
}

async function reject() {
  if (!rejectReason.value.trim()) { uni.showToast({ title: '请填写原因', icon: 'none' }); return }
  await approveStaffApplication(detail.value.applicationId, { action: 'reject', rejectReason: rejectReason.value.trim() })
  uni.showToast({ title: '已驳回', icon: 'success' })
  showReject.value = false; detail.value = null; loadList()
}

function appendTag(t: string) { if (!rejectReason.value.includes(t)) rejectReason.value = rejectReason.value ? `${rejectReason.value}。${t}` : t }
function onRoleChange(e: any) { roleVal.value = roles[e.detail.value] }
function onEmpTypeChange(e: any) { empTypeVal.value = empTypes[e.detail.value] }
</script>

<template>
  <view class="page">
    <!-- 列表视图 -->
    <template v-if="!detail">
      <view class="tabs">
        <view v-for="t in tabs" :key="t.value" class="tab" :class="{ on: tab === t.value }" @click="changeTab(t.value)">{{ t.label }}</view>
      </view>
      <view v-if="list.length" class="list">
        <view v-for="item in list" :key="item.applicationId" class="card" @click="openDetail(item)">
          <view class="card-top"><text class="cname">{{ item.name }}</text><text class="cstatus">{{ item.status === 'pending' ? '待审批' : item.status === 'approved' ? '已通过' : '已驳回' }}</text></view>
          <text class="cmeta">{{ item.mobile }} · {{ item.expectedRole }}</text>
          <view class="cinfo"><text>入职日期 {{ item.entryDate }}</text><text class="clink">查看详情 ›</text></view>
        </view>
      </view>
      <EmptyState v-else text="暂无申请" />
    </template>

    <!-- 详情视图 -->
    <template v-else>
      <view class="back" @click="backToList">‹ 返回列表</view>
      <view class="status-card">
        <text class="st-text">⏱ {{ detail.status === 'pending' ? '待审批' : detail.status }}</text>
        <view class="applicant">
          <view class="av">👤</view>
          <view><text class="an">{{ detail.name }}</text><text class="as">{{ detail.storeName }}</text></view>
        </view>
        <view class="note">请确认员工信息和岗位角色。审批通过后，员工才能登录系统。</view>
      </view>

      <view class="card">
        <text class="card-t">个人信息</text>
        <view class="r"><text class="rk">姓名</text><text class="rv">{{ detail.name }}</text></view>
        <view class="r"><text class="rk">手机号</text><text class="rv">{{ detail.mobile }}</text></view>
        <view class="r"><text class="rk">性别</text><text class="rv">{{ detail.gender }}</text></view>
        <view class="r"><text class="rk">出生日期</text><text class="rv">{{ detail.birthday }}</text></view>
        <view class="r"><text class="rk">紧急联系人</text><text class="rv">{{ detail.emergencyContactName || '--' }}</text></view>
        <view class="r last"><text class="rk">紧急联系电话</text><text class="rv">{{ detail.emergencyContactPhone || '--' }}</text></view>
      </view>

      <view class="card">
        <text class="card-t">任职信息</text>
        <view class="r"><text class="rk">归属门店</text><text class="rv">{{ detail.storeName }}</text></view>
        <picker :range="roles" @change="onRoleChange">
          <view class="r"><text class="rk">岗位角色</text><text class="rv sel">{{ roleVal }} ›</text></view>
        </picker>
        <picker :range="empTypes" @change="onEmpTypeChange">
          <view class="r"><text class="rk">员工类型</text><text class="rv sel">{{ empTypeVal }} ›</text></view>
        </picker>
        <view class="r last"><text class="rk">入职日期</text><text class="rv">{{ detail.entryDate }}</text></view>
      </view>

      <view v-if="detail.status==='pending'" class="bottom-actions">
        <view class="ba-outline" @click="showReject=true">驳回修改</view>
        <view class="ba-primary" @click="approve">审批通过</view>
      </view>
    </template>

    <!-- 驳回弹窗 -->
    <view v-if="showReject" class="mask" @click="showReject=false">
      <view class="sheet" @click.stop>
        <view class="sh"></view>
        <view class="sheet-h"><text class="sht">驳回并要求修改</text><text class="shx" @click="showReject=false">×</text></view>
        <view class="sheet-b">
          <view class="stip">员工会收到驳回提醒，可重新修改登记信息。</view>
          <view class="sf"><text class="sfl">驳回原因 *</text><textarea class="sft" v-model="rejectReason" placeholder="请说明需要修改的内容" /></view>
          <text class="stl">快捷标签</text>
          <view class="stags"><text v-for="t in rejectTags" :key="t" class="stag" @click="appendTag(t)">{{ t }}</text></view>
        </view>
        <view class="sheet-f"><view class="ba-cancel" @click="showReject=false">取消</view><view class="ba-confirm" @click="reject">确认驳回</view></view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$w:#E58A2D;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 160rpx}
.tabs{display:flex;gap:40rpx;border-bottom:2rpx solid #EEF1EF;padding-bottom:20rpx}.tab{font-size:28rpx;color:$t2}.tab.on{color:$p;font-weight:700;border-bottom:4rpx solid $p;padding-bottom:20rpx;margin-bottom:-22rpx}
.list{display:flex;flex-direction:column;gap:16rpx;margin-top:24rpx}
.card{background:$s;border-radius:20rpx;padding:24rpx 28rpx;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:16rpx}
.card-top{display:flex;justify-content:space-between}.cname{font-size:32rpx;font-weight:700;color:$t1}.cstatus{font-size:24rpx;color:$p}
.cmeta{display:block;margin-top:8rpx;font-size:24rpx;color:$t2}
.cinfo{display:flex;justify-content:space-between;margin-top:16rpx;font-size:24rpx;color:$t2}.clink{color:$p;font-weight:600}
.back{color:$p;font-size:28rpx;font-weight:600;padding:8rpx 0 16rpx}
.status-card{background:#FFF8EE;border-radius:20rpx;padding:28rpx;margin-bottom:16rpx;border:2rpx solid rgba($w,.2)}.st-text{color:$w;font-size:28rpx;font-weight:700}
.applicant{display:flex;align-items:center;gap:20rpx;margin:20rpx 0}.av{width:100rpx;height:100rpx;border-radius:50%;background:$s;display:flex;align-items:center;justify-content:center;font-size:44rpx}.an{display:block;font-size:32rpx;font-weight:700;color:$t1}.as{font-size:24rpx;color:$t2}
.note{padding:20rpx;border-radius:12rpx;background:rgba(255,255,255,.7);font-size:24rpx;color:$t2}
.card-t{display:block;font-size:26rpx;font-weight:600;color:$t3;margin-bottom:12rpx}
.r{display:flex;justify-content:space-between;padding:18rpx 0;border-bottom:2rpx solid #EEF1EF}.r.last{border:0}.rk{color:$t2;font-size:28rpx}.rv{color:$t1;font-size:28rpx}.sel{color:$p}
.tip{padding:16rpx 20rpx;margin:12rpx 0;border-radius:12rpx;background:rgba($ps,.5);font-size:24rpx;color:$t2}
.bottom-actions{position:fixed;left:0;right:0;bottom:0;display:flex;gap:24rpx;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:$s;border-top:2rpx solid #EEF1EF}
.ba-outline{flex:1;height:88rpx;border-radius:16rpx;border:2rpx solid $p;color:$p;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}
.ba-primary{flex:2;height:88rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}
// 驳回弹窗
.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;max-height:85vh;border-radius:32rpx 32rpx 0 0;background:#fff;display:flex;flex-direction:column}
.sh{width:80rpx;height:8rpx;border-radius:999rpx;background:$b;margin:20rpx auto}
.sheet-h{display:flex;justify-content:center;padding:8rpx 32rpx 20rpx;position:relative}.sht{font-size:36rpx;font-weight:700;color:$t1}.shx{position:absolute;right:32rpx;font-size:44rpx;color:$t2}
.sheet-b{flex:1;overflow-y:auto;padding:0 32rpx;display:flex;flex-direction:column;gap:20rpx}
.stip{padding:20rpx;border-radius:12rpx;background:#FFF4F2;font-size:26rpx;color:$t2}
.sf{display:flex;flex-direction:column;gap:12rpx}.sfl{font-size:28rpx;font-weight:600;color:$t1}.sft{padding:20rpx;border:2rpx solid $b;border-radius:12rpx;font-size:28rpx;height:160rpx;background:#FAFBF9}
.stl{font-size:26rpx;font-weight:600;color:$t1}.stags{display:flex;flex-wrap:wrap;gap:12rpx}.stag{padding:12rpx 24rpx;border-radius:999rpx;background:#FAFBF9;color:$t2;font-size:24rpx}
.sheet-f{display:flex;gap:20rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);border-top:2rpx solid #EEF1EF}
.ba-cancel{flex:1;height:88rpx;border-radius:16rpx;border:2rpx solid $b;color:$t1;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.ba-confirm{flex:1;height:88rpx;border-radius:16rpx;background:$d;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
</style>
