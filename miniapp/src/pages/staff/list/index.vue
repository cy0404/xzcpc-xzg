<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShareAppMessage, onShow, onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchStaffApplications, fetchStaffList } from '@/api/staff'
import { useUserStore } from '@/store/user'
import { fetchMyStores, switchStore } from '@/api/auth'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'

const userStore = useUserStore()
const allMode = ref(false)
const loading = ref(true)
const status = ref('在职')
const overview = ref<any>({})
const allRecords = ref<any[]>([])  // allMode: all employees (unfiltered)
const records = ref<any[]>([])
const pendingApps = ref<any[]>([])
const showInvite = ref(false)

// Store picker for switching view
const showStoreSheet = ref(false)
// Store picker for add employee (allMode)
const showAddStoreSheet = ref(false)
const storeList = ref<any[]>([])

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

onLoad((q: any) => {
  allMode.value = q?.all === 'true'
  loadAll()
})

onShow(() => {
  if (!loading.value) loadAll()
})

onPullDownRefresh(async () => {
  await loadAll()
  uni.stopPullDownRefresh()
})

async function loadAll() {
  loading.value = true
  try {
    if (allMode.value) {
      // 全部门店：拉全部员工，前端过滤
      const data: any = await fetchStaffList('', true)
      allRecords.value = Array.isArray(data) ? data : (data?.records || [])
      overview.value = {}
    } else {
      const data: any = await fetchStaffList(status.value)
      overview.value = data?.overview || {}
      records.value = data?.records || []
    }
    pendingApps.value = (await fetchStaffApplications('pending')) as any[]
  } finally { loading.value = false }
}

// allMode: filter records from allRecords by status tab
const filteredRecords = computed(() => {
  if (!allMode.value) return records.value
  if (!status.value) return allRecords.value
  return allRecords.value.filter(r => r.status === status.value)
})

function changeStatus(s: string) {
  status.value = s
  if (allMode.value) return // allMode: filter locally, no reload needed
  loadAll()
}
async function goDetail(item: any) {
  // allMode: switch to employee's store first
  if (allMode.value && item.storeId && item.storeId !== userStore.storeId) {
    try {
      const data: any = await switchStore(item.storeId)
      if (data?.token) {
        uni.setStorageSync('token', data.token)
        userStore.token = data.token
      }
      userStore.storeId = data?.storeId || item.storeId
      userStore.storeName = data?.storeName || ''
    } catch { /* ignore */ }
  }
  uni.navigateTo({ url: `/pages/staff/detail/index?employeeId=${item.employeeId}` })
}
function goApproval() { uni.navigateTo({ url: '/pages/staff/approval/index' }) }

async function handleAddEmployee() {
  if (allMode.value) {
    try {
      storeList.value = (await fetchMyStores()) || []
    } catch { storeList.value = [] }
    showAddStoreSheet.value = true
  } else {
    showInvite.value = true
  }
}

async function selectAddStore(store: any) {
  showAddStoreSheet.value = false
  try {
    const data: any = await switchStore(store.storeId)
    if (data?.token) {
      uni.setStorageSync('token', data.token)
      userStore.token = data.token
    }
    userStore.storeId = data?.storeId || store.storeId
    userStore.storeName = data?.storeName || store.storeName
  } catch { /* ignore */ }
  showInvite.value = true
}

onShareAppMessage(() => ({
  title: `象子茶铺茶邀请你加入${userStore.storeName || '门店'}`,
  path: invitePath.value,
  imageUrl: '/static/icons/employee.png',
}))

function maskMobile(m: string) {
  if (!m || m.length < 7) return m || '--'
  return `${m.slice(0, 3)}****${m.slice(-4)}`
}

// Computed overview from allRecords (allMode) or backend overview (single store)
const ovManager = computed(() => {
  if (allMode.value) return allRecords.value.filter(r => r.status === '在职' && r.role === '店长').length
  return overview.value?.managerCount || 0
})
const ovStaff = computed(() => {
  if (allMode.value) return allRecords.value.filter(r => r.status === '在职' && r.role === '店员').length
  return overview.value?.staffCount || 0
})
const ovPartTime = computed(() => {
  if (allMode.value) return allRecords.value.filter(r => r.status === '在职' && (r.employmentType === '兼职' || r.role === '兼职')).length
  return overview.value?.partTimeCount || 0
})
const ovTotal = computed(() => {
  if (allMode.value) return allRecords.value.length
  return overview.value?.totalCount || 0
})
const ovActive = computed(() => {
  if (allMode.value) return allRecords.value.filter(r => r.status === '在职').length
  return overview.value?.activeCount || 0
})

// Store picker
async function openStorePicker() {
  try { storeList.value = (await fetchMyStores()) || [] } catch { storeList.value = [] }
  showStoreSheet.value = true
}
async function selectAllMode() {
  showStoreSheet.value = false
  allMode.value = true
  await loadAll()
}
async function selectPickerStore(store: any) {
  showStoreSheet.value = false
  try {
    const data: any = await switchStore(store.storeId)
    if (data?.token) { uni.setStorageSync('token', data.token); userStore.token = data.token }
    userStore.storeId = data?.storeId || store.storeId
    userStore.storeName = data?.storeName || store.storeName
  } catch { /* ignore */ }
  allMode.value = false
  await loadAll()
}

function staffStatus(item: any) {
  if (item.status === '离职') return { text: '离职', cls: 'tag-left' }
  if (item.leaveDate && item.leaveDate > new Date().toISOString().slice(0, 10)) return { text: '待离职', cls: 'tag-pending' }
  return { text: item.status || '--', cls: 'tag-active' }
}
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else>
      <!-- 概览卡片 -->
      <view class="overview-card">
        <view class="ov-head">
          <view class="store-title-row" @click="openStorePicker()">
            <text class="ov-store">{{ allMode ? '全部门店' : (userStore.storeName || '我的门店') }}</text>
            <text v-if="userStore.storeCount > 1" class="switch-icon">⇄</text>
          </view>
        </view>
        <view class="ov-stats">
          <view class="stat"><text class="stat-num">{{ ovManager }}</text><text class="stat-label">店长</text></view>
          <view class="stat"><text class="stat-num">{{ ovStaff }}</text><text class="stat-label">店员</text></view>
          <view class="stat"><text class="stat-num">{{ ovPartTime }}</text><text class="stat-label">兼职</text></view>
          <view class="stat"><text class="stat-num">{{ pendingApps.length }}</text><text class="stat-label">待审批</text></view>
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
          {{ t.label }} {{ t.value === '在职' ? ovActive : t.value === '离职' ? (ovTotal - ovActive) : ovTotal }}
        </view>
      </view>

      <!-- 员工列表 -->
      <view v-if="filteredRecords.length" class="staff-list">
        <view v-for="item in filteredRecords" :key="item.employeeId" class="staff-card" @click="goDetail(item)">
          <text v-if="allMode" class="card-store">{{ item.storeName || '' }}</text>
          <view class="card-body">
          <view class="avatar">👤</view>
          <view class="info">
            <view class="name-row">
              <text class="name">{{ item.name }}</text>
              <text class="tag" :class="item.role === '店长' ? 'tag-lead' : item.role === '老板' ? 'tag-boss' : 'tag-normal'">{{ item.role || '--' }}</text>
              <text class="tag tag-status" :class="staffStatus(item).cls">{{ staffStatus(item).text }}</text>
            </view>
            <text class="meta">入职时间：{{ item.entryDate || '--' }}</text>
            <text class="meta">手机号：{{ maskMobile(item.mobile) }}</text>
          </view>
          <text class="arrow">›</text>
          </view>
        </view>
      </view>
      <EmptyState v-else text="暂无员工数据" />
    </template>

    <StoreSwitcher ref="switcherRef" @switched="loadAll" />

    <!-- 新增按钮 -->
    <view class="bottom-bar">
      <view v-if="userStore.permissions.includes('staff:manage')" class="btn-primary" @click="handleAddEmployee">
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

    <!-- Store picker sheet -->
    <view v-if="showStoreSheet" class="mask" @click="showStoreSheet = false">
      <view class="ss-sheet" @click.stop>
        <view class="ss-handle"></view>
        <view class="ss-head">
          <text class="ss-title">切换门店</text>
          <text class="ss-close" @click="showStoreSheet = false">✕</text>
        </view>
        <view class="ss-list">
          <view class="ss-item ss-all" :class="{ active: allMode }" @click="selectAllMode()">
            <text class="ss-name">全部门店</text>
            <text v-if="allMode" class="ss-check">✓</text>
          </view>
          <view v-for="s in storeList" :key="s.storeId" class="ss-item" :class="{ active: !allMode && s.storeId === userStore.storeId }" @click="selectPickerStore(s)">
            <text class="ss-name">{{ s.storeName }}</text>
            <text v-if="!allMode && s.storeId === userStore.storeId" class="ss-check">✓</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Add-store picker sheet (allMode add employee) -->
    <view v-if="showAddStoreSheet" class="mask" @click="showAddStoreSheet = false">
      <view class="ss-sheet" @click.stop>
        <view class="ss-handle"></view>
        <view class="ss-head">
          <text class="ss-title">选择登记门店</text>
          <text class="ss-close" @click="showAddStoreSheet = false">✕</text>
        </view>
        <view class="ss-list">
          <view v-for="s in storeList" :key="s.storeId" class="ss-item" @click="selectAddStore(s)">
            <text class="ss-name">{{ s.storeName }}</text>
            <text class="ss-arrow">›</text>
          </view>
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
.overview-card { background: $surface; border-radius: 20rpx; padding: 20rpx 24rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.04); }
.ov-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.store-title-row { display: flex; align-items: baseline; gap: 8rpx; }
.ov-store { font-size: 30rpx; font-weight: 700; color: $text-1; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.switch-icon { font-size: 32rpx; color: $primary; font-weight: 700; flex-shrink: 0; }
.ov-stats { display: grid; grid-template-columns: repeat(2,1fr); gap: 5px; background: #FAFBF9; border-radius: 16rpx; }
.stat { text-align: center; padding: 16rpx 12rpx; background: $surface; border-radius: 12rpx; }
.stat-num { display: block; font-size: 36rpx; font-weight: 800; color: $primary; }
.stat-label { display: block; font-size: 22rpx; color: $text-2; margin-top: 8rpx; }

.approval-bar { display: flex; align-items: center; gap: 12rpx; margin-top: 24rpx; padding: 24rpx 28rpx; border-radius: 16rpx; background: #FFF8EE; border: 2rpx solid rgba(229,138,45,0.2); }
.appr-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: $warning; }
.appr-text { flex: 1; font-size: 28rpx; font-weight: 600; color: $text-1; }
.appr-arrow { color: $warning; font-size: 40rpx; }

.tabs { display: flex; gap: 40rpx; margin-top: 32rpx; padding-bottom: 20rpx; border-bottom: 2rpx solid #EEF1EF; }
.tab { font-size: 28rpx; color: $text-2; padding-bottom: 20rpx; margin-bottom: -22rpx; }
.tab.active { color: $primary; font-weight: 700; border-bottom: 4rpx solid $primary; }

.staff-list { display: flex; flex-direction: column; gap: 20rpx; margin-top: 24rpx; }
.card-store{display:block;font-size:22rpx;color:$text-2;margin-bottom:12rpx;padding:4rpx 16rpx;background:#FAFBF9;border-radius:8rpx;align-self:flex-start}
.card-body{display:flex;align-items:center;gap:24rpx}
.staff-card { display: flex; flex-direction: column; padding: 28rpx; background: $surface; border-radius: 20rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,0.04); margin-bottom: 16rpx; }
.avatar { width: 96rpx; height: 96rpx; border-radius: 50%; background: #EEF1EF; display: flex; align-items: center; justify-content: center; font-size: 40rpx; flex-shrink: 0; }
.info { flex: 1; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 8rpx; }
.name { font-size: 32rpx; font-weight: 700; color: $text-1; }
.tag { padding: 2rpx 12rpx; border-radius: 999rpx; font-size: 20rpx; font-weight: 600; }
.tag-lead { background: $primary-soft; color: $primary; }
.tag-boss { background: #FFF8EE; color: $warning; }
.tag-normal { background: #EEF1EF; color: $text-1; }
.tag-active { background: $primary-soft; color: $primary; }
.tag-pending { background: #FFF8EE; color: $warning; }
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
/* Store picker sheet */
.ss-sheet{width:100%;max-height:70vh;border-radius:32rpx 32rpx 0 0;background:$surface;display:flex;flex-direction:column;overflow:hidden}
.ss-handle{width:96rpx;height:6rpx;border-radius:999rpx;background:#EEF1EF;margin:20rpx auto;flex-shrink:0}
.ss-head{display:flex;justify-content:center;padding:8rpx 32rpx 24rpx;position:relative;flex-shrink:0}
.ss-title{font-size:36rpx;font-weight:700;color:$text-1}
.ss-close{position:absolute;right:32rpx;font-size:40rpx;color:$text-2;padding:8rpx}
.ss-list{padding:0 32rpx calc(env(safe-area-inset-bottom) + 32rpx);overflow-y:auto}
.ss-item{display:flex;align-items:center;justify-content:space-between;padding:28rpx 24rpx;margin-bottom:12rpx;border-radius:16rpx;background:#FAFBFC;border:2rpx solid transparent}
.ss-item.active{background:#F1F8F3;border-color:$primary}
.ss-all{border:2rpx solid #EEF1EF;background:#FAFBF9}
.ss-name{font-size:30rpx;font-weight:600;color:$text-1}
.ss-arrow{font-size:36rpx;color:#8C9691}
.ss-check{font-size:32rpx;color:$primary;font-weight:700}
</style>
