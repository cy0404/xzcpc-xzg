<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { useTaskStore } from '@/store/task'
import { fetchStaffApplications, fetchStaffList } from '@/api/staff'
import { fetchExpenses } from '@/api/expense'
import { fetchOwnerDashboard, switchStore, fetchOwnerLatestApplication } from '@/api/auth'

const userStore = useUserStore()
const taskStore = useTaskStore()

let initialized = false

const pendingAppCount = ref(0)
const pendingTaskCount = ref(0)
const staffCount = ref(0)
const monthlyExpense = ref(0)
const appBanner = ref<{ status: string; storeNames: string[]; rejectReason?: string } | null>(null)
const firstTask = computed(() => taskStore.currentTasks?.[0] || null)
const isOwner = computed(() => userStore.role === '老板')
const showStoreDrawer = ref(false)
const storeList = ref<any[]>([])

onLoad(() => {
  if (isOwner.value && userStore.storeCount > 1) {
    const skipRedirect = uni.getStorageSync('_skip_owner_redirect')
    uni.removeStorageSync('_skip_owner_redirect')
    if (!skipRedirect) {
      uni.redirectTo({ url: '/pages/owner-home/index' })
    } else {
      initialized = true
    }
  } else {
    initialized = true
  }
})

onShow(async () => {
  if (!userStore.token) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!userStore.bound) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (initialized) {
    await userStore.fetchMe()
  }
  initialized = true
  await taskStore.fetchTaskList()
  loadStats()
  checkBanner()
  if (isOwner.value && userStore.storeCount > 1) {
    loadStoreList()
  }
})

async function loadStats() {
  try {
    const apps: any = await fetchStaffApplications('pending')
    pendingAppCount.value = Array.isArray(apps) ? apps.length : 0
  } catch { /* ignore */ }
  pendingTaskCount.value = taskStore.currentTasks?.length || 0
  try {
    const list: any = await fetchStaffList('')
    staffCount.value = list?.overview?.active || list?.records?.length || 0
  } catch { /* ignore */ }
  try {
    const d = new Date()
    const start = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
    const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()
    const end = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
    const res: any = await fetchExpenses({ startDate: start, endDate: end, pageSize: 999 })
    const records = res?.records || []
    monthlyExpense.value = records.reduce((sum: number, r: any) => sum + (r.amount || 0), 0)
  } catch { /* ignore */ }
}

async function checkBanner() {
  try {
    const data: any = await fetchOwnerLatestApplication()
    if (data && (data.status === 'pending' || data.status === 'rejected')) {
      appBanner.value = {
        status: data.status,
        storeNames: data.storeNames || [],
        rejectReason: data.rejectReason,
      }
    } else {
      appBanner.value = null
    }
  } catch {
    appBanner.value = null
  }
}

async function loadStoreList() {
  try {
    const data: any = await fetchOwnerDashboard()
    storeList.value = data?.stores || []
  } catch { /* ignore */ }
}

async function onSwitchStore(storeId: string) {
  showStoreDrawer.value = false
  try {
    const data: any = await switchStore(storeId)
    if (data?.token) {
      uni.setStorageSync('token', data.token)
      userStore.token = data.token
    }
    userStore.storeId = data?.storeId || ''
    userStore.storeName = data?.storeName || ''
    userStore.role = data?.role || ''
    await userStore.fetchMe()
    await taskStore.fetchTaskList()
    loadStats()
  } catch { /* ignore */ }
}

function goPending() {
  if (pendingAppCount.value > 0) {
    uni.navigateTo({ url: '/pages/staff/approval/index' })
  } else if (firstTask.value) {
    uni.navigateTo({ url: `/pages/task/detail/index?taskId=${firstTask.value.taskId}` })
  }
}

function goTaskList() {
  uni.navigateTo({ url: '/pages/task/list/index' })
}

function goCurrentTask() {
  if (firstTask.value) {
    uni.navigateTo({ url: `/pages/task/detail/index?taskId=${firstTask.value.taskId}` })
  } else {
    uni.navigateTo({ url: '/pages/task/list/index' })
  }
}

function goStaffApproval() {
  uni.navigateTo({ url: '/pages/staff/approval/index' })
}

function goStaffList() {
  uni.navigateTo({ url: '/pages/staff/list/index' })
}

function goExpenseList() {
  uni.navigateTo({ url: '/pages/expense/list/index' })
}

function goExpenseForm() {
  uni.navigateTo({ url: '/pages/expense/form/index' })
}

function goOwnerHome() {
  showStoreDrawer.value = false
  uni.redirectTo({ url: '/pages/owner-home/index' })
}

function goMyProfile() {
  if (userStore.employeeId) {
    uni.navigateTo({ url: `/pages/staff/detail/index?employeeId=${userStore.employeeId}` })
  } else {
    uni.navigateTo({ url: '/pages/staff/detail/index?employeeId=me' })
  }
}
</script>

<template>
  <view class="page">
    <view class="header">
      <view class="header-left">
        <text class="title">今日经营概览</text>
        <view class="store-pill" @click="isOwner && userStore.storeCount > 1 ? showStoreDrawer = true : null">
          <text class="sp-icon">🏪</text>
          <text class="sp-name">{{ userStore.storeName }}</text>
          <text class="sp-role" v-if="isOwner">老板</text>
          <text class="sp-role" v-else>{{ userStore.role }}</text>
          <text class="sp-switch-btn" v-if="isOwner && userStore.storeCount > 1">⇄</text>
        </view>
      </view>
      <view class="header-right">
        <view class="bell-wrap">
          <text class="bell-icon">🔔</text>
          <view class="bell-dot" v-if="pendingAppCount > 0"></view>
        </view>
      </view>
    </view>

    <!-- 新门店审核状态 banner -->
    <view v-if="appBanner" class="banner" :class="appBanner.status === 'rejected' ? 'banner-reject' : 'banner-pending'" @click="appBanner.status === 'pending' ? ()=>({}) : uni.showModal({ title: '审核未通过', content: appBanner.rejectReason || '信息不符', showCancel: false })">
      <text class="banner-icon">{{ appBanner.status === 'pending' ? '⏳' : '⚠️' }}</text>
      <text class="banner-text">
        <template v-if="appBanner.status === 'pending'">
          新门店审核中{{ appBanner.storeNames.length > 0 ? '：' + appBanner.storeNames.join('、') : '' }}，请耐心等待
        </template>
        <template v-else>
          新门店审核未通过{{ appBanner.rejectReason ? '：' + appBanner.rejectReason : '' }}，点击查看详情
        </template>
      </text>
    </view>

    <view class="stats-card">
      <text class="sc-label">本月概况</text>
      <view class="sc-grid">
        <view class="sc-item" @click="goExpenseList">
          <view class="sci-icon-wrap green"><text class="sci-icon">📋</text></view>
          <text class="sci-label">本月支出</text>
          <text class="sci-val">¥{{ monthlyExpense }}</text>
        </view>
        <view class="sc-item" @click="goStaffList">
          <view class="sci-icon-wrap"><text class="sci-icon">👥</text></view>
          <text class="sci-label">员工数</text>
          <text class="sci-val">{{ staffCount }} 人</text>
        </view>
        <view class="sc-item" @click="goPending">
          <view class="sci-icon-wrap"><text class="sci-icon">📝</text></view>
          <text class="sci-label">待处理</text>
          <text class="sci-val">{{ pendingAppCount + pendingTaskCount }} 项</text>
        </view>
      </view>
    </view>

    <view class="section" v-if="pendingAppCount + pendingTaskCount > 0">
      <view class="sec-top"><text class="sec-title">待处理事项</text><text class="sec-count">{{ pendingAppCount + pendingTaskCount }} 项</text></view>
      <view class="todo-list">
        <view class="todo-card green" @click="goCurrentTask">
          <view class="tc-icon-wrap"><text class="tc-icon">✅</text></view>
          <view class="tc-body">
            <text class="tc-title">完成本月门店盘点</text>
            <text class="tc-desc">{{ firstTask ? '进行中' : '暂无任务' }}</text>
          </view>
          <text class="tc-btn" v-if="firstTask">去盘点</text>
        </view>
        <view class="todo-card" @click="goStaffApproval" v-if="pendingAppCount > 0">
          <view class="tc-icon-wrap"><text class="tc-icon">👤</text></view>
          <view class="tc-body">
            <text class="tc-title">审批新员工登记</text>
            <text class="tc-desc">{{ pendingAppCount }} 项待审批</text>
          </view>
          <text class="tc-btn outline">去审批</text>
        </view>
      </view>
    </view>

    <view class="section">
      <text class="sec-title" style="margin-bottom:20rpx">常用工具</text>
      <view class="tool-grid">
        <view class="tool-item" @click="goCurrentTask">
          <view class="ti-icon-wrap green"><text class="ti-icon">📦</text></view>
          <text class="ti-name">盘点</text>
          <text class="ti-desc">快速完成库存盘点</text>
        </view>
        <view class="tool-item" @click="goExpenseList">
          <view class="ti-icon-wrap"><text class="ti-icon">🧾</text></view>
          <text class="ti-name">支出登记</text>
          <text class="ti-desc">记录门店支出</text>
        </view>
        <view class="tool-item" @click="goStaffList">
          <view class="ti-icon-wrap"><text class="ti-icon">👥</text></view>
          <text class="ti-name">人员管理</text>
          <text class="ti-desc">查看员工与审批</text>
        </view>
      </view>
    </view>

    <view class="section">
      <text class="sec-title" style="margin-bottom:20rpx">门店成长</text>
      <view class="learn-item">
        <view class="li-icon-wrap"><text class="li-icon">🎓</text></view>
        <view class="li-body">
          <text class="li-title">新员工入职后，店长应该关注什么？</text>
          <text class="li-desc">3 分钟阅读</text>
        </view>
        <text class="li-arrow">›</text>
      </view>
    </view>

    <!-- 门店切换抽屉 -->
    <view v-if="showStoreDrawer" class="mask" @click="showStoreDrawer = false">
      <view class="drawer" @click.stop>
        <view class="dr-handle"></view>
        <view class="dr-head"><text class="dr-title">切换门店</text><text class="dr-close" @click="showStoreDrawer = false">✕</text></view>
        <view class="dr-list">
          <view class="dr-item" :class="{active: s.storeId === userStore.storeId}" v-for="s in storeList" :key="s.storeId" @click="onSwitchStore(s.storeId)">
            <text class="dr-name">{{ s.storeName }}</text>
            <text class="dr-check" v-if="s.storeId === userStore.storeId">✓</text>
          </view>
          <view class="dr-item dr-item--owner" @click="goOwnerHome">
            <text class="dr-name">🏠 多门店总览</text>
            <text class="dr-arrow">›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 120rpx}
.header{display:flex;justify-content:space-between;align-items:flex-start;gap:24rpx}
.title{font-size:44rpx;font-weight:700;color:$t1;display:block}
.store-pill{display:inline-flex;align-items:center;gap:8rpx;margin-top:12rpx;padding:12rpx 24rpx;background:$s;border-radius:100rpx;box-shadow:0 4rpx 16rpx rgba(31,36,33,.06)}
.sp-icon{font-size:28rpx}.sp-name{font-size:26rpx;color:$t2;font-weight:500}.sp-role{font-size:22rpx;background:$ps;color:$p;padding:4rpx 12rpx;border-radius:100rpx}.sp-switch-btn{font-size:24rpx;color:$p;margin-left:4rpx}
.header-right{flex-shrink:0;position:relative}
.bell-wrap{width:88rpx;height:88rpx;border-radius:50%;background:$s;display:flex;align-items:center;justify-content:center;box-shadow:0 4rpx 16rpx rgba(31,36,33,.06)}
.bell-icon{font-size:36rpx}.bell-dot{position:absolute;top:16rpx;right:16rpx;width:16rpx;height:16rpx;border-radius:50%;background:$d}
.banner{margin-top:24rpx;padding:20rpx 28rpx;border-radius:16rpx;display:flex;align-items:center;gap:16rpx}
.banner-pending{background:#FFF8EE;border:2rpx solid #F5D89A}
.banner-reject{background:#FFF4F2;border:2rpx solid #F5B8B2}
.banner-icon{font-size:32rpx;flex-shrink:0}
.banner-text{flex:1;font-size:26rpx;line-height:38rpx;color:$t1}
.stats-card{margin-top:32rpx;padding:28rpx;border-radius:20rpx;background:$s;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}
.sc-label{font-size:28rpx;font-weight:600;color:$p;margin-bottom:24rpx;display:block}
.sc-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8rpx}
.sc-item{text-align:center}.sci-icon-wrap{width:72rpx;height:72rpx;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;background:#FAFBF9;margin-bottom:12rpx}.sci-icon-wrap.green{background:$ps}
.sci-icon{font-size:32rpx}.sci-label{display:block;font-size:24rpx;color:$t3}.sci-val{display:block;margin-top:4rpx;font-size:40rpx;font-weight:800;color:$t1}
.section{margin-top:48rpx}
.sec-top{display:flex;justify-content:space-between;align-items:center;margin-bottom:20rpx}
.sec-title{font-size:36rpx;font-weight:700;color:$t1}.sec-count{font-size:26rpx;color:$t2}
.todo-list{display:flex;flex-direction:column;gap:16rpx}
.todo-card{display:flex;align-items:center;gap:20rpx;padding:28rpx 24rpx;border-radius:20rpx;background:$s;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}.todo-card.green{background:#F1F8F3}
.tc-icon-wrap{width:80rpx;height:80rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#FAFBF9;flex-shrink:0}
.tc-icon{font-size:36rpx}.tc-body{flex:1;min-width:0}.tc-title{font-size:30rpx;font-weight:600;color:$t1;display:block}.tc-desc{font-size:26rpx;color:$t2;margin-top:4rpx}
.tc-btn{padding:14rpx 32rpx;border-radius:100rpx;background:$p;color:#fff;font-size:26rpx;font-weight:500;flex-shrink:0}.tc-btn.outline{background:$s;color:$p;border:2rpx solid $p}
.tool-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16rpx}
.tool-item{padding:32rpx 24rpx;border-radius:20rpx;background:$s;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}.ti-icon-wrap{width:88rpx;height:88rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#FAFBF9;margin-bottom:16rpx}.ti-icon-wrap.green{background:$ps}
.ti-icon{font-size:40rpx}.ti-name{display:block;font-size:30rpx;font-weight:600;color:$t1}.ti-desc{display:block;font-size:24rpx;color:$t3;margin-top:8rpx}
.learn-item{display:flex;align-items:center;gap:20rpx;padding:24rpx;border-radius:20rpx;background:$s;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}.li-icon-wrap{width:96rpx;height:96rpx;border-radius:16rpx;display:flex;align-items:center;justify-content:center;background:#F1F8F3;flex-shrink:0}.li-icon{font-size:44rpx}.li-body{flex:1;min-width:0}.li-title{font-size:28rpx;font-weight:600;color:$t1;display:block}.li-desc{font-size:24rpx;color:$t3;margin-top:4rpx}.li-arrow{font-size:40rpx;color:$t3}
.mask{position:fixed;inset:0;z-index:100;background:rgba(31,36,33,.4);display:flex;align-items:flex-end}
.drawer{width:100%;max-height:70vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column;overflow:hidden}
.dr-handle{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:20rpx auto 0}
.dr-head{display:flex;justify-content:center;align-items:center;padding:16rpx 32rpx 20rpx;position:relative}.dr-title{font-size:34rpx;font-weight:700;color:$t1}.dr-close{position:absolute;right:32rpx;font-size:40rpx;color:$t2}
.dr-list{padding:0 32rpx calc(env(safe-area-inset-bottom) + 20rpx);display:flex;flex-direction:column;gap:12rpx;overflow-y:auto}
.dr-item{display:flex;align-items:center;justify-content:space-between;padding:28rpx 24rpx;border-radius:16rpx;background:#FAFBF9}.dr-item.active{background:$ps}
.dr-name{font-size:30rpx;font-weight:500;color:$t1}.dr-check{font-size:32rpx;color:$p;font-weight:700}
.dr-item--owner{background:$ps;border:2rpx solid $p;margin-top:8rpx}.dr-arrow{font-size:36rpx;color:$p}
</style>
