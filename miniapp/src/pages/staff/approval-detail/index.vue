<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { approveStaffApplication, fetchStaffApplicationDetail } from '@/api/staff'
import { useUserStore } from '@/store/user'
import Skeleton from '@/components/Skeleton.vue'

const userStore = useUserStore()
const applicationId = ref('')
const loading = ref(true)
const saving = ref(false)
const detail = ref<any>({})
const showRejectSheet = ref(false)
const rejectReason = ref('')

const roles = ['店长', '店员', '兼职']
const employmentTypes = ['全职', '兼职', '临时工']
const rejectTags = ['手机号填写错误', '岗位选择错误', '入职日期错误', '信息不完整', '其他']

const roleValue = ref('')
const employmentTypeValue = ref('')

const isPending = computed(() => detail.value?.status === 'pending')

onLoad((query: any) => {
  if (!userStore.permissions.includes('staff:manage')) {
    uni.showToast({ title: '无审批权限', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 500)
    return
  }
  applicationId.value = query?.applicationId || ''
  loadDetail()
})

async function loadDetail() {
  if (!applicationId.value) return
  loading.value = true
  try {
    const data: any = await fetchStaffApplicationDetail(applicationId.value)
    detail.value = data || {}
    roleValue.value = data?.expectedRole || '店员'
    employmentTypeValue.value = data?.employmentType || '全职'
  } finally {
    loading.value = false
  }
}

function onRoleChange(event: any) {
  roleValue.value = roles[Number(event.detail.value)] || roleValue.value
}

function onEmploymentTypeChange(event: any) {
  employmentTypeValue.value = employmentTypes[Number(event.detail.value)] || employmentTypeValue.value
}

function appendRejectTag(tag: string) {
  if (rejectReason.value.includes(tag)) return
  rejectReason.value = rejectReason.value ? `${rejectReason.value}\n${tag}。` : `${tag}。`
}

async function approve() {
  if (saving.value) return
  saving.value = true
  try {
    await approveStaffApplication(applicationId.value, {
      action: 'approve',
      role: roleValue.value,
      employmentType: employmentTypeValue.value,
    })
    uni.showToast({ title: '审批通过', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } finally {
    saving.value = false
  }
}

async function reject() {
  if (!rejectReason.value.trim()) {
    uni.showToast({ title: '请填写驳回原因', icon: 'none' })
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    await approveStaffApplication(applicationId.value, {
      action: 'reject',
      rejectReason: rejectReason.value.trim(),
    })
    uni.showToast({ title: '已驳回', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } finally {
    saving.value = false
    showRejectSheet.value = false
  }
}
</script>

<template>
  <view class="approval-detail-page">
    <Skeleton v-if="loading" :rows="7" />
    <template v-else>
      <view class="status-card">
        <view class="status-row">
          <text class="status-icon">⏱</text>
          <text class="status-text">{{ detail.status === 'pending' ? '待审批' : detail.status }}</text>
        </view>
        <view class="applicant">
          <view class="avatar">人</view>
          <view class="applicant-info">
            <text class="name">{{ detail.name || '--' }}</text>
            <text class="meta">申请门店：{{ detail.storeName || '--' }}</text>
            <text class="time">申请时间：{{ detail.createdAt || '--' }}</text>
          </view>
        </view>
        <view class="note">
          <text>请确认员工信息和岗位角色。审批通过后，系统会根据岗位自动开通对应功能。</text>
        </view>
      </view>

      <view class="section-card">
        <view class="section-head">
          <text>个人信息</text>
          <text class="edit-text">修改</text>
        </view>
        <view class="info-list">
          <view class="info-row"><text>姓名</text><text>{{ detail.name || '--' }}</text></view>
          <view class="info-row"><text>手机号</text><text>{{ detail.mobile || '--' }}</text></view>
          <view class="info-row"><text>性别</text><text>{{ detail.gender || '--' }}</text></view>
          <view class="info-row"><text>出生年月</text><text>{{ detail.birthday || '--' }}</text></view>
          <view class="divider"></view>
          <view class="info-row"><text>紧急联系人</text><text>{{ detail.emergencyContactName || '--' }}</text></view>
          <view class="info-row"><text>紧急联系电话</text><text>{{ detail.emergencyContactPhone || '--' }}</text></view>
        </view>
      </view>

      <view class="section-card">
        <view class="section-head">
          <text>任职信息</text>
        </view>
        <view class="select-row">
          <text>归属门店</text>
          <text>{{ detail.storeName || '--' }} ›</text>
        </view>
        <picker :range="roles" @change="onRoleChange">
          <view class="select-row">
            <text>岗位角色</text>
            <text>{{ roleValue || '--' }} ›</text>
          </view>
        </picker>
        <view class="permission-tip">
          <text>系统会根据岗位角色自动开通对应功能，审批时无需手动设置。</text>
        </view>
        <picker :range="employmentTypes" @change="onEmploymentTypeChange">
          <view class="select-row">
            <text>员工类型</text>
            <text>{{ employmentTypeValue || '--' }} ›</text>
          </view>
        </picker>
        <view class="select-row last">
          <text>入职日期</text>
          <text>{{ detail.entryDate || '--' }}</text>
        </view>
      </view>

      <view v-if="detail.remark" class="section-card">
        <view class="section-head">
          <text>补充说明</text>
        </view>
        <text class="remark">{{ detail.remark }}</text>
      </view>

      <view v-if="detail.rejectReason" class="section-card reject-card">
        <view class="section-head">
          <text>驳回原因</text>
        </view>
        <text class="remark">{{ detail.rejectReason }}</text>
      </view>
    </template>

    <view v-if="isPending" class="bottom-actions safe-bottom">
      <view class="reject-btn" @click="showRejectSheet = true">驳回修改</view>
      <view class="approve-btn" @click="approve">审批通过</view>
    </view>

    <view v-if="showRejectSheet" class="sheet-mask" @click="showRejectSheet = false">
      <view class="reject-sheet" @click.stop>
        <view class="sheet-handle"></view>
        <view class="sheet-head">
          <text>驳回并要求修改</text>
          <text class="sheet-close" @click="showRejectSheet = false">×</text>
        </view>
        <view class="sheet-tip">
          <text>员工会收到驳回提醒，并可以重新修改登记信息。</text>
        </view>
        <view class="reject-field">
          <text class="field-label">驳回原因 <text class="required">*</text></text>
          <textarea v-model="rejectReason" placeholder="请说明需要员工修改的内容" />
        </view>
        <view class="tag-section">
          <text class="field-label">快捷标签</text>
          <view class="tag-list">
            <text v-for="tag in rejectTags" :key="tag" class="reject-tag" @click="appendRejectTag(tag)">
              {{ tag }}
            </text>
          </view>
        </view>
        <view class="sheet-actions">
          <view class="cancel-btn" @click="showRejectSheet = false">取消</view>
          <view class="confirm-reject-btn" @click="reject">确认驳回</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.approval-detail-page {
  min-height: 100vh;
  padding: 24rpx 32rpx 180rpx;
  background: #FAFBF9;
  color: #1F2421;
}

.status-card,
.section-card {
  margin-bottom: 24rpx;
  border: 2rpx solid #E8ECE9;
  border-radius: 24rpx;
  background: #F7F8F6;
}

.status-card {
  padding: 32rpx;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
  color: #C47B1D;
  font-size: 28rpx;
  font-weight: 700;
}

.applicant {
  display: flex;
  gap: 32rpx;
  margin-bottom: 28rpx;
}

.avatar {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  border: 2rpx solid #E8ECE9;
  background: #FAFBF9;
  color: #66706A;
  display: flex;
  align-items: center;
  justify-content: center;
}

.name {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
}

.meta,
.time {
  display: block;
  margin-top: 8rpx;
  color: #66706A;
  font-size: 26rpx;
}

.time {
  color: #A3AAA6;
  font-size: 22rpx;
}

.note,
.permission-tip {
  padding: 24rpx;
  border: 2rpx solid #E8ECE9;
  border-radius: 16rpx;
  background: #fff;
  color: #66706A;
  font-size: 24rpx;
  line-height: 1.5;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx;
  border-bottom: 2rpx solid #E8ECE9;
  font-size: 28rpx;
  font-weight: 700;
}

.edit-text {
  color: #2F8F57;
  font-size: 26rpx;
}

.info-list {
  padding: 28rpx 32rpx;
}

.info-row,
.select-row {
  display: flex;
  justify-content: space-between;
  gap: 32rpx;
  color: #66706A;
  font-size: 28rpx;
  line-height: 44rpx;

  text:last-child {
    color: #1F2421;
    text-align: right;
  }
}

.info-row + .info-row {
  margin-top: 20rpx;
}

.divider {
  height: 2rpx;
  margin: 20rpx 0;
  background: #E8ECE9;
}

.select-row {
  padding: 28rpx 32rpx;
  border-bottom: 2rpx solid #E8ECE9;
}

.select-row.last {
  border-bottom: 0;
}

.permission-tip {
  margin: 24rpx 32rpx;
  background: rgba(231, 244, 235, 0.35);
}

.remark {
  display: block;
  padding: 28rpx 32rpx;
  color: #66706A;
  font-size: 26rpx;
  line-height: 1.6;
}

.reject-card {
  background: #FFF4F2;
}

.bottom-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 24rpx;
  padding: 24rpx 32rpx;
  border-top: 2rpx solid #E8ECE9;
  background: #F7F8F6;
  box-shadow: 0 -8rpx 12rpx rgba(0, 0, 0, 0.04);
}

.reject-btn,
.approve-btn {
  height: 88rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
}

.reject-btn {
  flex: 1;
  border: 2rpx solid #2F8F57;
  color: #2F8F57;
  background: #F7F8F6;
}

.approve-btn {
  flex: 2;
  color: #fff;
  background: #2F8F57;
}

.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: flex-end;
  background: rgba(31, 36, 33, 0.4);
  backdrop-filter: blur(6rpx);
}

.reject-sheet {
  width: 100%;
  max-height: 85vh;
  border-radius: 32rpx 32rpx 0 0;
  background: #fff;
  box-shadow: 0 -16rpx 48rpx rgba(0, 0, 0, 0.12);
}

.sheet-handle {
  width: 96rpx;
  height: 8rpx;
  margin: 24rpx auto 8rpx;
  border-radius: 999rpx;
  background: #EEF1EF;
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  border-bottom: 2rpx solid #EEF1EF;
  font-size: 36rpx;
  font-weight: 700;
}

.sheet-close {
  color: #66706A;
  font-size: 44rpx;
}

.sheet-tip {
  margin: 32rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  background: #FFF4F2;
  color: #66706A;
  font-size: 26rpx;
  line-height: 1.5;
}

.reject-field,
.tag-section {
  margin: 0 32rpx 32rpx;
}

.field-label {
  display: block;
  margin-bottom: 16rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.required {
  color: #E05A47;
}

textarea {
  width: 100%;
  height: 220rpx;
  box-sizing: border-box;
  padding: 24rpx;
  border: 2rpx solid #EEF1EF;
  border-radius: 16rpx;
  background: #FAFBF9;
  color: #1F2421;
  font-size: 28rpx;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.reject-tag {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: #FAFBF9;
  color: #66706A;
  font-size: 24rpx;
}

.sheet-actions {
  display: flex;
  gap: 24rpx;
  padding: 24rpx 32rpx 36rpx;
  border-top: 2rpx solid #EEF1EF;
}

.cancel-btn,
.confirm-reject-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
}

.cancel-btn {
  border: 2rpx solid #E8ECE9;
  color: #1F2421;
}

.confirm-reject-btn {
  background: #E05A47;
  color: #fff;
}
</style>
