<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { fetchStaffApplications, fetchStaffList } from '@/api/staff'
import { useUserStore } from '@/store/user'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'
import StoreSwitcher from '@/components/StoreSwitcher.vue'

const userStore = useUserStore()
const switcherRef = ref<InstanceType<typeof StoreSwitcher> | null>(null)
const loading = ref(true)
const status = ref('在职')
const overview = ref<any>({})
const records = ref<any[]>([])
const pendingApps = ref<any[]>([])
const showInvite = ref(false)

const tabs = [
  { label: '在职员工', value: '在职' },
  { label: '已离职员工', value: '离职' },
  { label: '全部员工', value: '' },
]

const invitePath = computed(() => {
  const sid = encodeURIComponent(userStore.storeId || '')
  const sn = encodeURIComponent(userStore.storeName || '')
  return `/pages/staff/register/index?storeId=${sid}&storeName=${sn}&from=invite`
})

onShow(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const data: any = await fetchStaffList(status.value)
    overview.value = data?.overview || {}
    records.value = data?.records || []
    pendingApps.value = (await fetchStaffApplications('pending')) as any[]
  } finally { loading.value = false }
}

function changeStatus(s: string) { status.value = s; loadAll() }
function goDetail(item: any) { uni.navigateTo({ url: `/pages/staff/detail/index?employeeId=${item.employeeId}` }) }
function goApproval() { uni.navigateTo({ url: '/pages/staff/approval/index' }) }

onShareAppMessage(() => ({
  title: `象子茶铺茶邀请你加入${userStore.storeName || '门店'}`,
  path: invitePath.value,
  imageUrl: '/static/employee.png',
}))

function maskMobile(m: string) {
  if (!m || m.length < 7) return m || '--'
  return `${m.slice(0, 3)}****${m.slice(-4)}`
}
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else>
      <!-- 概览卡片 -->
      <view class="overview-card">
        <view class="ov-head">
          <view class="store-title-row">
            <text class="ov-store">{{ userStore.storeName || '我的门店' }}</text>
            <text v-if="userStore.storeCount > 1" class="switch-link" @click="switcherRef?.open()">切换</text>
          </view>
        </view>
        <view class="ov-stats">
          <view class="stat"><text class="stat-num">{{ overview.managerCount || 0 }}</text><text class="stat-label">店长</text></view>
          <view class="stat stat--border"><text class="stat-num">{{ overview.staffCount || 0 }}</text><text class="stat-label">店员</text></view>
          <view class="stat stat--border"><text class="stat-num">{{ overview.partTimeCount || 0 }}</text><text class="stat-label">兼职</text></view>
        </view>
      </view>

      <!-- 审批提醒 -->
      <view v-if="pendingApps.length" class="approval-bar" @click="goApproval">
        <text class="appr-dot"></text>
        <text class="appr-text">{{ pendingApps.length }} 条待审批申请</text>
        <text class="appr-arrow">›</text>
      </view>

      <!-- 筛选标签 -->
      <view class="tabs">
        <view v-for="t in tabs" :key="t.value" class="tab" :class="{ active: status === t.value }" @click="changeStatus(t.value)">
          {{ t.label }} {{ t.value === '在职' ? (overview.activeCount || 0) : t.value === '离职' ? ((overview.totalCount || 0) - (overview.activeCount || 0)) : (overview.totalCount || 0) }}
        </view>
      </view>

      <!-- 员工列表 -->
      <view v-if="records.length" class="staff-list">
        <view v-for="item in records" :key="item.employeeId" class="staff-card" @click="goDetail(item)">
          <view class="avatar">👤</view>
          <view class="info">
            <view class="name-row">
              <text class="name">{{ item.name }}</text>
              <text class="tag" :class="item.role === '店长' ? 'tag-lead' : 'tag-normal'">{{ item.role || '--' }}</text>
              <text class="tag tag-status" :class="item.status === '在职' ? 'tag-active' : 'tag-left'">{{ item.status || '--' }}</text>
            </view>
            <text class="meta">入职时间：{{ item.entryDate || '--' }}</text>
            <text class="meta">手机号：{{ maskMobile(item.mobile) }}</text>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>
      <EmptyState v-else text="暂无员工数据" />
    </template>

    <StoreSwitcher ref="switcherRef" @switched="loadAll" />

    <!-- 新增按钮 -->
    <view class="bottom-bar">
      <view class="btn-primary" @click="showInvite = true">
        <text>＋ 新增员工</text>
      </view>
    </view>

    <!-- 邀请弹窗 -->
    <view v-if="showInvite" class="mask" @click="showInvite = false">
      <view class="sheet" @click.stop>
        <view class="sheet-handle"></view>
        <view class="sheet-head">
          <text class="sheet-title">邀请员工</text>
          <text class="sheet-close" @click="showInvite = false">×</text>
        </view>
        <view class="sheet-body">
          <view class="store-row">
            <text class="store-label">登记门店</text>
            <text class="store-val">{{ userStore.storeName || '--' }}</text>
          </view>
          <view class="expire-tip">
            <text>邀请有效期：7 天</text>
            <text>过期后可重新生成邀请</text>
          </view>
        </view>
        <view class="share-btn-wrap">
          <button class="share-btn" open-type="share">⌯ 分享给微信好友</button>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg: #F7F8F6; $surface: #FFFFFF; $primary: #2F8F57; $primary-soft: #E7F4EB;
$text-1: #1F2421; $text-2: #66706A; $text-3: #98A19C; $border: #E8ECE9;
$warning: #E58A2D;

.page { min-height: 100vh; background: $bg; padding: 24rpx 32rpx 160rpx; }
.overview-card { background: $surface; border-radius: 24rpx; padding: 32rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.04); }
.ov-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24rpx; }
.store-title-row { display: flex; align-items: baseline; gap: 12rpx; }
.ov-store { font-size: 36rpx; font-weight: 800; color: $text-1; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.switch-link { font-size: 24rpx; color: $primary; padding: 4rpx 0; flex-shrink: 0; }
.ov-stats { display: grid; grid-template-columns: repeat(3,1fr); gap: 8rpx; background: #FAFBF9; border-radius: 16rpx; padding: 20rpx; }
.stat { text-align: center; }
.stat--border { border-left: 2rpx solid #E8ECE9; }
.stat-num { display: block; font-size: 44rpx; font-weight: 800; color: $primary; }
.stat-label { display: block; font-size: 24rpx; color: $text-2; margin-top: 4rpx; }

.approval-bar { display: flex; align-items: center; gap: 12rpx; margin-top: 24rpx; padding: 24rpx 28rpx; border-radius: 16rpx; background: #FFF8EE; border: 2rpx solid rgba(229,138,45,0.2); }
.appr-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: $warning; }
.appr-text { flex: 1; font-size: 28rpx; font-weight: 600; color: $text-1; }
.appr-arrow { color: $warning; font-size: 40rpx; }

.tabs { display: flex; gap: 40rpx; margin-top: 32rpx; padding-bottom: 20rpx; border-bottom: 2rpx solid #EEF1EF; }
.tab { font-size: 28rpx; color: $text-2; padding-bottom: 20rpx; margin-bottom: -22rpx; }
.tab.active { color: $primary; font-weight: 700; border-bottom: 4rpx solid $primary; }

.staff-list { display: flex; flex-direction: column; gap: 20rpx; margin-top: 24rpx; }
.staff-card { display: flex; align-items: center; gap: 24rpx; padding: 28rpx; background: $surface; border-radius: 20rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.04); }
.avatar { width: 96rpx; height: 96rpx; border-radius: 50%; background: #EEF1EF; display: flex; align-items: center; justify-content: center; font-size: 40rpx; flex-shrink: 0; }
.info { flex: 1; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 8rpx; }
.name { font-size: 32rpx; font-weight: 700; color: $text-1; }
.tag { padding: 2rpx 12rpx; border-radius: 999rpx; font-size: 20rpx; font-weight: 600; }
.tag-lead { background: $primary-soft; color: $primary; }
.tag-normal { background: #EEF1EF; color: $text-1; }
.tag-active { background: $primary-soft; color: $primary; }
.tag-left { background: #EEF1EF; color: $text-2; }
.meta { display: block; margin-top: 6rpx; font-size: 24rpx; color: $text-2; }
.arrow { font-size: 48rpx; color: #8C9691; }

.bottom-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 10; padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx); background: linear-gradient(to top, #fff 0%, rgba(255,255,255,0.9) 80%, transparent); }
.btn-primary { height: 96rpx; border-radius: 999rpx; background: $primary; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 30rpx; font-weight: 700; }

// 邀请弹窗
.mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; background: rgba(31,36,33,0.4); }
.sheet { width: 100%; max-height: 85vh; border-radius: 32rpx 32rpx 0 0; background: #fff; display: flex; flex-direction: column; }
.sheet-handle { width: 80rpx; height: 8rpx; border-radius: 999rpx; background: #E8ECE9; margin: 20rpx auto 8rpx; }
.sheet-head { display: flex; justify-content: center; align-items: center; padding: 16rpx 32rpx; position: relative; }
.sheet-title { font-size: 36rpx; font-weight: 700; color: $text-1; }
.sheet-close { position: absolute; right: 32rpx; font-size: 44rpx; color: $text-2; }
.sheet-body { flex: 1; overflow-y: auto; padding: 0 32rpx; display: flex; flex-direction: column; gap: 24rpx; }
.store-row { display: flex; justify-content: space-between; padding: 28rpx 0; border-bottom: 2rpx solid #EEF1EF; font-size: 28rpx; }
.store-label { font-weight: 700; color: $text-1; }
.store-val { color: $primary; }
.expire-tip { display: flex; flex-direction: column; align-items: center; gap: 4rpx; padding: 24rpx 0; color: $text-2; font-size: 26rpx; }
.share-btn-wrap { padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx); }
.share-btn { width: 100%; height: 96rpx; background: $primary; color: #fff; border: none; border-radius: 16rpx; font-size: 28rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 16rpx; }
</style>
