<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { useTaskStore } from '@/store/task'
import { fetchMyStores, switchStore } from '@/api/auth'
import { fetchExpenses } from '@/api/expense'
import { fetchTransferOverview } from '@/api/transfer'
import { getLossList, getLossOverview } from '@/api/loss-report'
import { fetchIssueOverview, fetchIssueOverviewStores } from '@/api/issue'
import { fetchStaffOverview } from '@/api/staff'
import { fetchSupervisorVisitOverview } from '@/api/supervisor-visit'
import { fetchSmartOrderOverview } from '@/api/smart-order'
import { fetchTaskDetail } from '@/api/task'
import { fetchHomeOverview } from '@/api/business'
import { fetchFeedbackOverview, fetchFeedbackOverviewStores } from '@/api/feedback'
import { H5_BASE } from '@/utils/constants'

const userStore = useUserStore()
const taskStore = useTaskStore()

interface StoreOption { storeId: string; storeName: string }
interface PendingItem { icon: string; title: string; desc: string; btn: string; url: string; warn?: boolean; storeId?: string; h5?: boolean }

const scope = ref<'all' | string>('all')
const myStores = ref<StoreOption[]>([])
const taskRemaining = ref(0)
const todayExpense = ref(0)
const yesterdaySales = ref(0)
const yesterdayExpense = ref(0)
const transferPending = ref(0)
const lossPending = ref(0)
const issuePending = ref(0)
const allIssueStores = ref<any[]>([])
const allTransferStores = ref<any[]>([])
const allLossStores = ref<any[]>([])
const allStaffStores = ref<any[]>([])
const smartOrderPending = ref(0)
const allSmartOrderStores = ref<any[]>([])
const staffApprovalPending = ref(0)
const supervisorPendingConfirm = ref(0)
const supervisorPendingTasks = ref(0)
const firstPendingVisitId = ref<number>(0)
const taskItems = ref<any[]>([])
const complaintPending = ref(0)
const allComplaintStores = ref<any[]>([])
const switching = ref(false)
const dataReady = ref(false)

const isManagerOrOwner = computed(() => {
  const r = userStore.role
  return r === 'owner' || r === '老板' || r === 'store_manager' || r === '店长'
})
const isOwner = computed(() => userStore.role === 'owner' || userStore.role === '老板')
const isSingle = computed(() => myStores.value.length <= 1)
const scopeAll = computed(() => scope.value === 'all')

const roleLabel = computed(() => {
  const map: Record<string, string> = { owner: '老板', store_manager: '店长', staff: '店员' }
  return map[userStore.role] || userStore.role || '店员'
})

const scopeLabel = computed(() => {
  if (scope.value === 'all') return '全部门店'
  const s = myStores.value.find(s => s.storeId === scope.value)
  return s?.storeName || userStore.storeName || '当前门店'
})

onLoad(async () => {
  try {
    const data = await fetchMyStores()
    myStores.value = (data || []).map((s: any) => ({ storeId: s.storeId || s.id, storeName: s.storeName || s.mendianmingcheng }))
  } catch { /* use empty */ }
})

onShow(async () => {
  if (!userStore.token || !userStore.bound) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  await userStore.fetchMe()
  const prevScope = scope.value
  // 单门店默认选该门店；多门店恢复上次选中的门店，否则全部门店
  const savedScope = userStore.selectedScope
  scope.value = myStores.value.length === 1
    ? myStores.value[0].storeId
    : (savedScope && myStores.value.some(s => s.storeId === savedScope) ? savedScope : 'all')
  if (prevScope === scope.value) {
    await loadDataForScope()
    dataReady.value = true
  }
})

async function loadDataForScope() {
  switching.value = true
  try {
    if (scope.value === 'all') {
      await taskStore.fetchTaskList(true)
      taskRemaining.value = taskStore.currentTasks.reduce((s, t) => s + Math.max((t.totalMaterials || 0) - (t.enteredMaterials || 0), 0), 0)
      const wrap = (p: Promise<any>) => p.then(v => v).catch(() => null)
      const [tData, lData, sData, iData, soData] = await Promise.all([
        wrap(fetchTransferOverview(true)),
        wrap(getLossOverview(true)),
        wrap(fetchStaffOverview(true)),
        wrap(fetchIssueOverviewStores()),
        // 智能订货员工 403，仅店长/老板请求；暂隐藏智能订货，不请求
        // isManagerOrOwner.value ? wrap(fetchSmartOrderOverview(true)) : Promise.resolve(null),
        Promise.resolve(null),
      ])
      allTransferStores.value = Array.isArray(tData) ? tData : []
      allLossStores.value = Array.isArray(lData) ? lData : []
      allStaffStores.value = Array.isArray(sData) ? sData : []
      allIssueStores.value = Array.isArray(iData) ? iData : []
      allSmartOrderStores.value = Array.isArray(soData) ? soData : []
      loadYesterdayOverview()
      loadComplaintOverview()
    } else {
      await taskStore.fetchTaskList()
      taskRemaining.value = taskStore.currentTasks.reduce((s, t) => s + Math.max((t.totalMaterials || 0) - (t.enteredMaterials || 0), 0), 0)
      if (userStore.token) {
        loadTodayExpense()
        loadYesterdayOverview()
        loadTransferPending()
        loadLossPending()
        loadApprovalPending()
        loadIssuePending()
        loadSupervisorPending()
        // loadSmartOrderPending()  // 暂隐藏智能订货，不请求待确认数
        loadComplaintOverview()
      }
      try {
        const tData = await fetchTransferOverview(true)
        allTransferStores.value = Array.isArray(tData) ? tData : []
      } catch { allTransferStores.value = [] }
    }
  } finally {
    switching.value = false
  }
}

async function loadTodayExpense() {
  try {
    const today = new Date()
    const d = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    const data = await fetchExpenses({ startDate: d, endDate: d, pageNum: 1, pageSize: 999 })
    const records = data?.records || []
    todayExpense.value = records.reduce((s: number, r: any) => s + (Number(r.amount) || 0), 0)
  } catch { todayExpense.value = 0 }
}


async function loadYesterdayOverview() {
  if (!isManagerOrOwner.value) return
  try {
    const data = await fetchHomeOverview(scope.value)
    yesterdaySales.value = data?.yesterdaySales || 0
    yesterdayExpense.value = data?.yesterdayExpense || 0
  } catch { yesterdaySales.value = 0; yesterdayExpense.value = 0 }
}


async function loadTransferPending() {
  try {
    const data = await fetchTransferOverview()
    transferPending.value = (data?.pendingConfirm || 0) + (data?.pendingShip || 0)
  } catch { transferPending.value = 0 }
}

// For single store home page: count only orders where current store needs to act
function transferActionCount(storeId?: string): number {
  const sid = storeId || userStore.storeId
  let count = 0
  for (const s of allTransferStores.value) {
    if (s.storeId === sid) {
      count = s.pending || 0
      break
    }
  }
  return count
}

async function loadLossPending() {
  try {
    const data = await getLossList({ pageNum: 1, pageSize: 999 })
    const records = data?.records || []
    lossPending.value = records.filter((r: any) => r.status === 'pending_approval' || r.status === 'confirmed_resend').length
  } catch { lossPending.value = 0 }
}

async function loadIssuePending() {
  try {
    const overview = await fetchIssueOverview(scopeAll.value)
    issuePending.value = overview?.pendingAcceptance || 0
  } catch { issuePending.value = 0 }
  try {
    const stores = await fetchIssueOverviewStores()
    allIssueStores.value = Array.isArray(stores) ? stores : []
  } catch { allIssueStores.value = [] }
}

async function loadApprovalPending() {
  try {
    const data = await fetchStaffOverview()
    staffApprovalPending.value = data?.pending || 0
  } catch { staffApprovalPending.value = 0 }
}

async function loadSmartOrderPending() {
  if (!isManagerOrOwner.value) { smartOrderPending.value = 0; return }
  try {
    const data = await fetchSmartOrderOverview()
    smartOrderPending.value = (data?.pending || 0) + (data?.submitFailed || 0)
  } catch { smartOrderPending.value = 0 }
}

async function loadSupervisorPending() {
  try {
    const data = await fetchSupervisorVisitOverview()
    supervisorPendingConfirm.value = data?.pendingConfirm || 0
    supervisorPendingTasks.value = data?.pendingTasks || 0
    firstPendingVisitId.value = data?.firstPendingVisitId || 0
    taskItems.value = data?.taskItems || []
  } catch { supervisorPendingConfirm.value = 0; supervisorPendingTasks.value = 0 }
}

// 客诉卡片按门店联动：全部门店视图统计各店未处理客诉；单店视图只统计当前门店
async function loadComplaintOverview() {
  if (!isManagerOrOwner.value) { complaintPending.value = 0; allComplaintStores.value = []; return }
  if (scopeAll.value) {
    try {
      const stores = await fetchFeedbackOverviewStores()
      allComplaintStores.value = Array.isArray(stores) ? stores : []
      complaintPending.value = allComplaintStores.value.reduce((s, x) => s + (x.pending || 0), 0)
    } catch { allComplaintStores.value = []; complaintPending.value = 0 }
  } else {
    try {
      const data = await fetchFeedbackOverview(false)
      complaintPending.value = data?.pending || 0
    } catch { complaintPending.value = 0 }
  }
}

// Dynamic pending items for store view
const storePendingItems = computed<PendingItem[]>(() => {
  const items: PendingItem[] = []

  if (scopeAll.value) {
    // 全部门店：按门店分组展示。未提交即未完成——不管物料录没录完、录了多少
    const byStore: Record<string, { name: string; remaining: number; weekly: boolean; hasLoss: boolean }> = {}
    for (const t of taskStore.currentTasks) {
      const sid = t.storeId || ''
      if (!byStore[sid]) byStore[sid] = { name: t.storeName || '', remaining: 0, weekly: false, hasLoss: false }
      byStore[sid].remaining += Math.max((t.totalMaterials || 0) - (t.enteredMaterials || 0), 0)
      if (t.taskType === 'weekly') byStore[sid].weekly = true
    }
    for (const [sid, info] of Object.entries(byStore)) {
      items.push({
        icon: '✅', title: info.weekly ? '完成本周盘点' : '完成本月盘点',
        desc: info.remaining > 0 ? `${info.name} · ${info.remaining} 项待录入` : `${info.name} · 已录完，待提交`,
        btn: '去盘点', url: '/pages/task/list/index', storeId: sid,
      })
    }
    // 报损：有 pending 才展示
    for (const s of allLossStores.value) {
      items.push({
        icon: '📋', title: '门店报损',
        desc: `${s.storeName} · ${s.pending} 条待处理`,
        btn: '查看', url: '/pages/loss-report/list/index', storeId: s.storeId,
      })
    }
    // 调货：有 pending 才展示
    for (const s of allTransferStores.value) {
      items.push({
        icon: '📦', title: '调货管理',
        desc: `${s.storeName} · ${s.pending || 0} 单需要操作`,
        btn: '查看', url: '/pages/transfer/list/index', storeId: s.storeId,
      })
    }
    // 智能订货：有待确认单据才展示（仅店长/老板，数据源已按角色门控；H5 入口）——暂隐藏，需要时恢复
    // for (const s of allSmartOrderStores.value) {
    //   items.push({
    //     icon: '🛒', title: '智能订货',
    //     desc: `${s.storeName} · ${s.pending || 0} 张订货单待确认`,
    //     btn: '去确认', url: '', h5: true, storeId: s.storeId,
    //   })
    // }
    // 员工审批：有 pending 才展示（仅店长/老板可见）
    for (const s of allStaffStores.value) {
      items.push({
        icon: '👥', title: '员工审批',
        desc: `${s.storeName} · ${s.pending} 项待审批`,
        btn: '去审批', url: '/pages/staff/approval/index', storeId: s.storeId,
      })
    }
    // 问题处理：有待验收才展示
    for (const s of allIssueStores.value) {
      items.push({
        icon: '📌', title: '问题处理',
        desc: `${s.storeName} · ${s.pending || 0} 个待验收`,
        btn: '查看', url: '/pages/issue/list/index', storeId: s.storeId,
      })
    }
  } else {
    // 单门店：未提交即未完成，不管物料录完没有
    if (taskStore.currentTasks.length > 0) {
      const hasWeekly = taskStore.currentTasks.some((t: any) => t.taskType === 'weekly')
      items.push({
        icon: '✅', title: hasWeekly ? '完成本周盘点' : '完成本月盘点',
        desc: taskRemaining.value > 0 ? `${taskRemaining.value} 项待录入` : '已录完，待提交',
        btn: '去盘点', url: '/pages/task/list/index',
      })
    }
    const tCount = transferActionCount()
    if (tCount > 0) {
      items.push({
        icon: '📦', title: '调货待处理', desc: `${tCount} 单需要操作`, btn: '去处理', url: '/pages/transfer/list/index',
      })
    }
    // if (smartOrderPending.value > 0) {  // 暂隐藏智能订货，需要时恢复
    //   items.push({
    //     icon: '🛒', title: '智能订货', desc: `${smartOrderPending.value} 张订货单待确认`, btn: '去确认', url: '', h5: true,
    //   })
    // }
    if (lossPending.value > 0) {
      items.push({
        icon: '📋', title: '报损记录', desc: `${lossPending.value} 条报损记录`, btn: '查看', url: '/pages/loss-report/list/index',
      })
    }
    if (issuePending.value > 0) {
      items.push({
        icon: '📌', title: '问题处理', desc: `${issuePending.value} 个待验收`, btn: '去处理', url: '/pages/issue/list/index',
      })
    }
    if (staffApprovalPending.value > 0) {
      items.push({
        icon: '👥', title: '员工审批', desc: `${staffApprovalPending.value} 项待审批`, btn: '去审批', url: '/pages/staff/approval/index',
      })
    }
    // 督导拜访
    if (supervisorPendingConfirm.value > 0) {
      items.push({
        icon: '🕵️', title: '督导拜访确认',
        desc: `${supervisorPendingConfirm.value} 张拜访单待确认`,
        btn: '去确认', url: `/pages/supervisor-visit/detail/index?id=${firstPendingVisitId.value}`,
      })
    }
    for (const t of taskItems.value) {
      const isOverdue = t.taskStatus === 'overdue'
      const isWarn = isOverdue || t.taskStatus === 'returned'
      items.push({
        icon: '📝', title: t.actionName,
        desc: isOverdue
          ? `追踪时间：${t.trackingTime || '--'}已到，仍可补交反馈`
          : `追踪：${t.trackingTime || '--'} · ${t.taskStatus === 'returned' ? '已退回' : '待执行'}`,
        btn: '去处理', url: `/pages/supervisor-visit/action-detail/index?id=${t.id}`,
        warn: isWarn,
      })
    }
  }

  return items
})

const storePendingCount = computed(() => storePendingItems.value.length)

function setScope(val: 'all' | string) {
  scope.value = val
  userStore.selectedScope = val
}

// 切换门店时同步后端并刷新数据
watch(scope, async (newVal) => {
  if (newVal !== 'all') {
    try {
      const data: any = await switchStore(newVal)
      if (data?.token) {
        uni.setStorageSync('token', data.token)
        userStore.token = data.token
      }
      userStore.storeId = data?.storeId || newVal
      userStore.storeName = data?.storeName || ''
      userStore.chatId = data?.chatId || ''
    } catch { /* ignore */ }
  }
  await loadDataForScope()
})

async function go(url: string) {
  uni.navigateTo({ url })
}

// 客诉处理 H5 入口（web-view 打开，token 走 URL；导航时动态构建保证 token 最新）
function goComplaint() {
  const token = uni.getStorageSync('token') || userStore.token || ''
  const storeName = userStore.storeName || ''
  const h5url = H5_BASE + '/upload/h5/complaint.html?v=1&token=' + encodeURIComponent(token) + '&storeName=' + encodeURIComponent(storeName)
  uni.navigateTo({ url: '/pages/common/webview/index?url=' + encodeURIComponent(h5url) + '&title=' + encodeURIComponent('客诉处理') })
}

// 客诉卡片入口（单店视图）：确保 token 切到当前门店再进客诉处理页
async function goComplaintEntry() {
  if (scope.value !== userStore.storeId) {
    try {
      const data: any = await switchStore(scope.value as string)
      if (data?.token) {
        uni.setStorageSync('token', data.token)
        userStore.token = data.token
      }
      userStore.storeId = data?.storeId || scope.value
      userStore.storeName = data?.storeName || userStore.storeName || ''
      userStore.chatId = data?.chatId || ''
    } catch { /* ignore */ }
  }
  goComplaint()
}

// 客诉卡片入口（全部门店视图）：按门店卡片直接切到该店并进客诉处理页
async function goComplaintForStore(store: any) {
  try {
    const data: any = await switchStore(store.storeId)
    if (data?.token) {
      uni.setStorageSync('token', data.token)
      userStore.token = data.token
    }
    userStore.storeId = data?.storeId || store.storeId
    userStore.storeName = data?.storeName || store.storeName
    userStore.chatId = data?.chatId || ''
  } catch { /* ignore, use local switch */ }
  goComplaint()
}

// 智能订货 H5 入口（goPendingItem 已先切门店，此处动态构建保证 token 最新）
function buildSmartOrderUrl(): string {
  const token = uni.getStorageSync('token') || userStore.token || ''
  const storeName = userStore.storeName || ''
  const h5url = H5_BASE + '/upload/h5/smart-order-list.html?v=3&token=' + encodeURIComponent(token) + '&storeName=' + encodeURIComponent(storeName)
  return '/pages/common/webview/index?url=' + encodeURIComponent(h5url) + '&title=' + encodeURIComponent('智能订货待确认')
}

async function goPendingItem(item: PendingItem) {
  // 全部门店视图：先切到该门店再跳转
  if (item.storeId) {
    try {
      const data: any = await switchStore(item.storeId)
      if (data?.token) {
        uni.setStorageSync('token', data.token)
        userStore.token = data.token
      }
      userStore.storeId = data?.storeId || item.storeId
      userStore.storeName = data?.storeName || ''
      userStore.chatId = data?.chatId || ''
    } catch { /* ignore */ }
  } else if (!scopeAll.value && scope.value !== userStore.storeId) {
    try {
      const data: any = await switchStore(scope.value)
      if (data?.token) {
        uni.setStorageSync('token', data.token)
        userStore.token = data.token
      }
      userStore.storeId = data?.storeId || scope.value
      userStore.storeName = data?.storeName || ''
      userStore.chatId = data?.chatId || ''
    } catch { /* ignore */ }
  }
  // 盘点待办：直接进入第一个未完成分区的物料录入页
  if (item.url === '/pages/task/list/index') {
    // 切换门店后刷新任务列表，确保 currentTasks 属于当前门店
    await taskStore.fetchTaskList()
    if (taskStore.currentTasks.length) {
      const task = taskStore.currentTasks[0]
      try {
        const detail = await fetchTaskDetail(task.taskId) as any
        const zones = detail?.zones || []
        const firstZone = zones.find((z: any) => !z.isComplete) || zones[0]
        if (firstZone) {
          uni.navigateTo({ url: `/pages/task/zone-entry/index?taskId=${task.taskId}&zoneId=${firstZone.taskZoneId || firstZone.id}` })
          return
        }
      } catch { /* fall through */ }
    }
  }
  uni.navigateTo({ url: item.h5 ? buildSmartOrderUrl() : item.url })
}
</script>

<template>
  <view class="page">
    <!-- Scope card -->
    <view class="scope-card">
      <view class="sc-top">
        <view>
          <text class="sc-kicker">{{ isSingle ? '当前门店' : '当前查看范围' }}</text>
          <text class="sc-label">{{ scopeLabel }}</text>
        </view>
        <text class="sc-role">{{ roleLabel }}</text>
      </view>
      <scroll-view v-if="!isSingle" scroll-x class="sc-scroll">
        <view class="sc-row">
          <view class="sc-chip" :class="{ on: scope === 'all' }" @click="setScope('all')">全部门店</view>
          <view v-for="s in myStores" :key="s.storeId" class="sc-chip" :class="{ on: scope === s.storeId }" @click="setScope(s.storeId)">{{ s.storeName }}</view>
        </view>
      </scroll-view>
    </view>

    <!-- scope=all & owner: 全部门店概况 -->
    <section v-if="scopeAll && isManagerOrOwner" class="overview-card">
      <text class="ov-title">全部门店概况</text>
      <view class="ov-grid">
        <view class="ov-item" @click="go('/pages/business/index/index')">
          <text class="ov-label">昨日销售额</text>
          <text class="ov-val ov-val-long">{{ yesterdaySales > 0 ? '¥' + yesterdaySales.toLocaleString() : '--' }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">昨日支出</text>
          <text class="ov-val">{{ yesterdayExpense > 0 ? '¥' + yesterdayExpense.toLocaleString() : '--' }}</text>
        </view>
      </view>
    </section>

    <!-- scope=store & owner: 经营概览 -->
    <section v-if="!scopeAll && isManagerOrOwner" class="overview-card">
      <text class="ov-title">经营概览</text>
      <view class="ov-grid">
        <view class="ov-item" @click="go('/pages/business/index/index')">
          <text class="ov-label">昨日销售</text>
          <text class="ov-val">{{ yesterdaySales > 0 ? '¥' + yesterdaySales.toLocaleString() : '--' }}</text>
        </view>
        <view class="ov-item" @click="go('/pages/expense/list/index')">
          <text class="ov-label">昨日支出</text>
          <text class="ov-val">{{ yesterdayExpense > 0 ? '¥' + yesterdayExpense.toLocaleString() : '--' }}</text>
        </view>
      </view>
    </section>

    <!-- 待处理事项 (dynamic, scope=all and scope=store) -->
    <section v-if="storePendingItems.length > 0" class="section">
      <view class="sec-head">
        <text class="sec-title">待处理事项</text>
        <text class="sec-count">{{ storePendingCount }} 项</text>
      </view>
      <view class="focus-list">
        <view v-for="item in storePendingItems" :key="item.title + (item.storeId || '')" class="focus-card" :class="{ 'focus-warn': item.warn }" @click="goPendingItem(item)">
          <view class="fc-icon-wrap" :class="{ 'fc-icon-warn': item.warn }"><text class="fc-icon">{{ item.icon }}</text></view>
          <view class="fc-body">
            <text class="fc-title">{{ item.title }}</text>
            <text class="fc-desc">{{ item.desc }}</text>
          </view>
          <text class="fc-btn">{{ item.btn }}</text>
        </view>
      </view>
    </section>

    <!-- 客诉处理入口（仅店长/老板；与门店联动——有未处理客诉的门店各一张卡片） -->
    <section v-if="isManagerOrOwner && (scopeAll ? allComplaintStores.length > 0 : complaintPending > 0)" class="section">
      <text class="sec-title sec-title-block">客诉处理</text>
      <!-- 全部门店视图：按门店一张卡片，点击直接带门店进客诉处理页 -->
      <view v-if="scopeAll" v-for="s in allComplaintStores" :key="s.storeId" class="complaint-entry" @click="goComplaintForStore(s)">
        <text class="ce-icon">💬</text>
        <view class="ce-body">
          <text class="ce-title">{{ s.storeName }}</text>
          <text class="ce-desc">{{ s.pending }} 条待处理，点击跟进</text>
        </view>
        <text class="ce-arrow">›</text>
      </view>
      <!-- 单店视图：当前门店一张卡片 -->
      <view v-else class="complaint-entry" @click="goComplaintEntry">
        <text class="ce-icon">💬</text>
        <view class="ce-body">
          <text class="ce-title">处理顾客投诉</text>
          <text class="ce-desc">{{ complaintPending }} 条待处理，请及时跟进</text>
        </view>
        <text class="ce-arrow">›</text>
      </view>
    </section>

    <!-- 常用工具 (scope=store) -->
    <section v-if="!scopeAll" class="section">
      <text class="sec-title sec-title-block">常用工具</text>
      <view class="tool-grid">
        <view class="tool-card tool-blue" @click="go('/pages/expense/list/index')">
          <text class="tc-title">新增支出</text>
          <text class="tc-desc">记录门店费用</text>
          <text class="tc-icon">🧾</text>
        </view>
        <view class="tool-card tool-green" @click="go('/pages/transfer/list/index')">
          <text class="tc-title">调货管理</text>
          <text class="tc-desc">查看待收货调货单</text>
          <text class="tc-icon">📦</text>
        </view>
        <view class="tool-card tool-purple" @click="go('/pages/issue/list/index')">
          <text class="tc-title">问题处理</text>
          <text class="tc-desc">门店问题上报跟进</text>
          <text class="tc-icon">📌</text>
        </view>
        <view class="tool-card tool-red" @click="go('/pages/loss-report/list/index')">
          <text class="tc-title">门店报损</text>
          <text class="tc-desc">日常/到货报损</text>
          <text class="tc-icon">📋</text>
        </view>
      </view>
    </section>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$w:#E58A2D;$so:#FFF8EE;
.page{min-height:100vh;background:$bg;padding:24rpx 24rpx 180rpx}

/* Scope card */
.scope-card{margin-top:24rpx;background:$s;border-radius:16rpx;padding:24rpx;border:1.5px solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:28rpx}
.sc-top{display:flex;justify-content:space-between;align-items:flex-start}
.sc-kicker{font-size:22rpx;color:$t3}
.sc-label{display:block;margin-top:4rpx;font-size:32rpx;font-weight:700;color:$t1}
.sc-role{font-size:22rpx;padding:6rpx 16rpx;border-radius:999rpx;background:#FFF8EE;color:#E58A2D;flex-shrink:0}
.sc-scroll{white-space:nowrap;margin:0 -24rpx;margin-top:20rpx}
.sc-row{display:flex;gap:16rpx;padding:0 24rpx}
.sc-chip{padding:14rpx 28rpx;border-radius:999rpx;font-size:26rpx;background:$s;color:$t2;flex-shrink:0;border:1.5px solid $b}
.sc-chip.on{background:$p;color:#fff}

/* Overview cards */
.overview-card{background:$s;border-radius:16rpx;padding:28rpx;border:1.5px solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:36rpx}
.ov-title{display:block;font-size:28rpx;font-weight:600;color:$p;margin-bottom:20rpx}
.ov-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx}
.ov-item{background:#FAFBF9;border-radius:24rpx;padding:24rpx;box-shadow:inset 0 0 0 1px rgba(232,236,233,.72)}
.ov-label{display:block;font-size:26rpx;color:#8C9691}
.ov-val{display:block;margin-top:14rpx;font-size:48rpx;font-weight:750;color:$t1}
.ov-val-long{font-size:44rpx}
.ov-split{display:grid;grid-template-columns:1fr 1fr}
.ovs-item{padding:12rpx 24rpx;text-align:center}
.ovs-item:first-child{border-right:1px solid #EEF1EF}
.ovs-val{display:block;margin-top:8rpx;font-size:44rpx;font-weight:700;color:$t1}

/* Sections */
.section{margin-bottom:48rpx}
.sec-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:24rpx}
.sec-title{font-size:34rpx;font-weight:700;color:$t1}
.sec-title-block{display:block;margin-bottom:24rpx}
.sec-count{font-size:26rpx;color:$t2}

/* Focus cards (待处理事项) */
.focus-list{display:flex;flex-direction:column;gap:20rpx}
.focus-card{display:flex;align-items:center;gap:20rpx;padding:28rpx 24rpx;border-radius:16rpx;background:#F1F8F3;border:1.5px solid #D4E8DA;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}
.focus-warn{background:$so;border-color:#F5D89A}
.fc-icon-wrap{width:80rpx;height:80rpx;border-radius:50%;background:$ps;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.fc-icon-warn{background:$s}
.fc-icon{font-size:36rpx}
.fc-body{flex:1;min-width:0}
.fc-title{display:block;font-size:30rpx;font-weight:700;color:$t1}
.fc-desc{display:block;margin-top:4rpx;font-size:24rpx;color:$t2}
.fc-btn{padding:14rpx 28rpx;border-radius:999rpx;background:$p;color:#fff;font-size:24rpx;font-weight:600;flex-shrink:0}
.fc-btn-warn{background:$w}

/* Tool grid (常用工具) */
.tool-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx}
.tool-card{border-radius:16rpx;padding:20rpx 24rpx;position:relative;overflow:hidden;border:1.5px solid $b;box-shadow:0 3rpx 10rpx rgba(31,36,33,.035)}
.tc-title{display:block;font-size:30rpx;font-weight:700;color:$t1;position:relative;z-index:1}
.tc-desc{display:block;margin-top:8rpx;font-size:24rpx;color:$t2;position:relative;z-index:1}
.tc-icon{position:absolute;right:12rpx;bottom:8rpx;font-size:44rpx;opacity:.18;z-index:0}
.tool-blue{border-color:#CBE7FA;background:linear-gradient(135deg,#EEF8FF,#F8FCFF)}
.tool-orange{border-color:#EFD6C8;background:linear-gradient(135deg,#FFF1E8,#FFFAF6)}
.tool-green{border-color:#CBEED8;background:linear-gradient(135deg,#ECFBF3,#F7FFFA)}
.tool-red{border-color:#EECFCA;background:linear-gradient(135deg,#FFF1F0,#FFFAFA)}
.tool-purple{border-color:#D9D1F0;background:linear-gradient(135deg,#F5F2FF,#FBFAFF)}

/* 客诉处理入口（独立卡片，全部门店/单店视图均显示；红色警示风格） */
.complaint-entry{margin-top:24rpx;background:linear-gradient(135deg,#FFF1F0,#FFFAFA);border-radius:20rpx;border:1.5px solid #EECFCA;padding:28rpx 24rpx;display:flex;align-items:center;gap:20rpx;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}
.ce-icon{font-size:44rpx;width:88rpx;height:88rpx;border-radius:20rpx;background:#FEEBEA;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.ce-body{flex:1;display:flex;flex-direction:column}
.ce-title{font-size:30rpx;font-weight:700;color:#C0392B}
.ce-desc{font-size:24rpx;color:#D96C61;margin-top:6rpx}
.ce-arrow{font-size:44rpx;color:#D96C61;flex-shrink:0}
</style>
