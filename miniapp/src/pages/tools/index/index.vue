<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { fetchMyStores, switchStore } from '@/api/auth'
import { fetchSmartOrderOverviewTotal } from '@/api/smart-order'
import { H5_BASE } from '@/utils/constants'

const userStore = useUserStore()

interface StoreOption { storeId: string; storeName: string }

type Scope = 'all' | string

const scope = ref<Scope>('all')
const myStores = ref<StoreOption[]>([])
const showStoreSheet = ref(false)
const pendingExecUrl = ref('')
const pendingExecIsH5 = ref(false)
const pendingExecComplaint = ref(false)
const pendingExecExpense = ref(false)
const smartOrderPending = ref(0)

const isStaff = computed(() => userStore.role === 'staff' || userStore.role === '店员')
const isSingle = computed(() => myStores.value.length <= 1)

const scopeLabel = computed(() => {
  if (scope.value === 'all') return '全部门店'
  const s = myStores.value.find(s => s.storeId === scope.value)
  return s?.storeName || userStore.storeName || '当前门店'
})

const execLabel = computed(() => scope.value === 'all' ? '需先选门店' : '直接执行')

// 查看类工具 — role-aware
const viewTools = computed(() => {
  if (isStaff.value) {
    return [
      { title: '调货管理', desc: '店间物料调拨', icon: '⇄', url: '/pages/transfer/list/index' },
      { title: '我的信息', desc: '查看个人资料', icon: '👤', url: '/pages/staff/detail/index?employeeId=me' },
      // 暂隐藏「问题处理」入口，需要时恢复：
      { title: '问题处理', desc: '门店问题上报与跟进', icon: '📌', url: '/pages/issue/list/index' },
    ]
  }
  return [
    // 暂隐藏「智能订货」入口，需要时恢复：
    // { title: '智能订货', desc: smartOrderPending.value > 0 ? `每周建议订货单 · ${smartOrderPending.value} 张待确认` : '每周建议订货单', icon: '📋', url: '', h5: true },
    { title: '调货管理', desc: '店间物料调拨', icon: '⇄', url: '/pages/transfer/list/index' },
    { title: '人员管理', desc: '查看门店人员', icon: '👥', url: '/pages/staff/list/index' },
    // 暂隐藏「问题处理」入口，需要时恢复：
    { title: '问题处理', desc: '门店问题上报与跟进', icon: '📌', url: '/pages/issue/list/index' },
  ]
})

// 入库管理 H5 入口（web-view 打开，token 走 URL；导航时动态构建保证 token 最新）
function buildInboundUrl(): string {
  const token = uni.getStorageSync('token') || userStore.token || ''
  const storeName = userStore.storeName || ''
  const h5url = H5_BASE + '/upload/h5/inbound-list.html?token=' + encodeURIComponent(token) + '&storeName=' + encodeURIComponent(storeName)
  return '/pages/common/webview/index?url=' + encodeURIComponent(h5url)
}

// 智能订货 H5 入口（web-view 打开；跨店视图走 all 模式，单店先确保 token 已切到该门店）
function buildSmartOrderUrl(all: boolean): string {
  const token = uni.getStorageSync('token') || userStore.token || ''
  const storeName = userStore.storeName || ''
  const h5url = H5_BASE + '/upload/h5/smart-order-list.html?v=3&token=' + encodeURIComponent(token)
    + (all ? '&all=1' : '&storeName=' + encodeURIComponent(storeName))
  return '/pages/common/webview/index?url=' + encodeURIComponent(h5url) + '&title=' + encodeURIComponent('智能订货待确认')
}

async function goSmartOrder() {
  if (scope.value === 'all') {
    uni.navigateTo({ url: buildSmartOrderUrl(true) })
    return
  }
  // 单店：先同步后端 token 到该门店，再打开 H5
  try {
    const data: any = await switchStore(scope.value)
    if (data?.token) {
      uni.setStorageSync('token', data.token)
      userStore.token = data.token
    }
    userStore.storeId = data?.storeId || scope.value
    userStore.storeName = data?.storeName || userStore.storeName || ''
    userStore.chatId = data?.chatId || ''
  } catch { /* ignore, use current token */ }
  uni.navigateTo({ url: buildSmartOrderUrl(false) })
}

interface ToolItem { title: string; desc: string; icon: string; url: string; h5?: boolean; complaint?: boolean; expense?: boolean }

// 执行类工具 — role-aware
const execTools = computed<ToolItem[]>(() => {
  if (isStaff.value) {
    return [
      { title: '盘点', desc: '进入盘点任务', icon: '✅', url: '/pages/task/list/index' },
      // 暂隐藏「入库管理」入口，需要时恢复：
      // { title: '入库管理', desc: '报货单收货入库', icon: '📦', url: '/pages/common/webview/index', h5: true },
      { title: '支出登记', desc: '记录门店费用', icon: '🧾', url: '', expense: true },
      { title: '门店报损', desc: '日常/到货报损', icon: '📋', url: '/pages/loss-report/list/index' },
    ]
  }
  return [
    { title: '盘点', desc: '进入盘点任务', icon: '✅', url: '/pages/task/list/index' },
    // 暂隐藏「入库管理」入口，需要时恢复：
    // { title: '入库管理', desc: '报货单收货入库', icon: '📦', url: '/pages/common/webview/index', h5: true },
    { title: '支出登记', desc: '记录门店费用', icon: '🧾', url: '', expense: true },
    { title: '工时登记', desc: '录入上月汇总', icon: '⏱', url: '/pages/work-hours/index/index' },
    { title: '门店报损', desc: '日常/到货报损', icon: '📋', url: '/pages/loss-report/list/index' },
    { title: '客诉处理', desc: '处理顾客投诉', icon: '💬', url: '', complaint: true },
  ]
})

const colors = ['green', 'blue', 'amber', 'orange', 'red', 'purple']

onLoad(async () => {
  try {
    const data = await fetchMyStores()
    myStores.value = (data || []).map((s: any) => ({ storeId: s.storeId || s.id, storeName: s.storeName || s.mendianmingcheng }))
    const savedScope = userStore.selectedScope
    scope.value = myStores.value.length === 1
      ? myStores.value[0].storeId
      : (savedScope && myStores.value.some((s: any) => s.storeId === savedScope) ? savedScope : 'all')
  } catch { /* empty */ }
})

onShow(() => {
  if (!userStore.token || !userStore.bound) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  const savedScope = userStore.selectedScope
  scope.value = myStores.value.length === 1
    ? myStores.value[0].storeId
    : (savedScope && myStores.value.some((s: any) => s.storeId === savedScope) ? savedScope : 'all')
  // loadSmartOrderPending()  // 暂隐藏智能订货，不请求待确认数
})

// 智能订货待确认数（员工无入口，不请求；暂隐藏智能订货，函数保留待恢复）
async function loadSmartOrderPending() {
  if (isStaff.value) { smartOrderPending.value = 0; return }
  try {
    const data: any = await fetchSmartOrderOverviewTotal()
    smartOrderPending.value = data?.pending || 0
  } catch { /* keep defaults */ }
}

function setScope(val: Scope) {
  scope.value = val
  userStore.selectedScope = val
}

// 切换门店时同步后端
watch(scope, async (newVal) => {
  if (newVal === 'all') return
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
})
function goTool(url: string) {
  if (scope.value === 'all') {
    uni.navigateTo({ url: `${url}?all=true` })
  } else {
    uni.navigateTo({ url: `${url}?storeId=${encodeURIComponent(scope.value)}` })
  }
}

// 执行类工具点击分发：客诉/支出走专用 H5 流程，其余按原逻辑
function goExecTool(t: ToolItem) {
  if (t.complaint) { goComplaintTool(); return }
  if (t.expense) { goExpenseTool(); return }
  handleExecTool(t.url, t.h5)
}

function handleExecTool(url: string, isH5 = false) {
  if (scope.value === 'all') {
    pendingExecUrl.value = url
    pendingExecIsH5.value = isH5
    pendingExecComplaint.value = false
    showStoreSheet.value = true
  } else {
    uni.navigateTo({ url: isH5 ? buildInboundUrl() : url })
  }
}

// 客诉处理 H5 入口（web-view 打开，token 走 URL；导航时动态构建保证 token 最新）
function goComplaint() {
  const token = uni.getStorageSync('token') || userStore.token || ''
  const storeName = userStore.storeName || ''
  const h5url = H5_BASE + '/upload/h5/complaint.html?v=1&token=' + encodeURIComponent(token) + '&storeName=' + encodeURIComponent(storeName)
  uni.navigateTo({ url: '/pages/common/webview/index?url=' + encodeURIComponent(h5url) + '&title=' + encodeURIComponent('客诉处理') })
}

// 客诉处理：全部门店视图下先弹选门店 sheet（客诉按门店处理），单店视图直接打开
function goComplaintTool() {
  if (scope.value === 'all') {
    pendingExecComplaint.value = true
    showStoreSheet.value = true
  } else {
    goComplaint()
  }
}

// 支出登记 H5 入口（web-view 打开，token 走 URL；导航时动态构建保证 token 最新）
function goExpense() {
  const token = uni.getStorageSync('token') || userStore.token || ''
  const storeName = userStore.storeName || ''
  const h5url = H5_BASE + '/upload/h5/expense-list.html?v=1&token=' + encodeURIComponent(token) + '&storeName=' + encodeURIComponent(storeName)
  uni.navigateTo({ url: '/pages/common/webview/index?url=' + encodeURIComponent(h5url) + '&title=' + encodeURIComponent('支出登记') })
}

// 支出登记：全部门店视图下先弹选门店 sheet（支出按门店登记），单店视图直接打开
function goExpenseTool() {
  if (scope.value === 'all') {
    pendingExecExpense.value = true
    showStoreSheet.value = true
  } else {
    goExpense()
  }
}

async function confirmExecStore(store: StoreOption) {
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
  scope.value = store.storeId
  showStoreSheet.value = false
  if (pendingExecComplaint.value) { pendingExecComplaint.value = false; goComplaint(); return }
  if (pendingExecExpense.value) { pendingExecExpense.value = false; goExpense(); return }
  uni.navigateTo({ url: pendingExecIsH5.value ? buildInboundUrl() : pendingExecUrl.value })
}
</script>

<template>
  <view class="page">
    <!-- Scope card (same style as home page) -->
    <view class="scope-card">
      <view class="sc-top">
        <text class="sc-kicker">{{ isSingle ? '当前门店' : '当前查看范围' }}</text>
        <text class="sc-label">{{ scopeLabel }}</text>
      </view>
      <scroll-view v-if="!isSingle" scroll-x class="sc-scroll">
        <view class="sc-row">
          <view class="sc-chip" :class="{ on: scope === 'all' }" @click="setScope('all')">全部门店</view>
          <view v-for="s in myStores" :key="s.storeId" class="sc-chip" :class="{ on: scope === s.storeId }" @click="setScope(s.storeId)">{{ s.storeName }}</view>
        </view>
      </scroll-view>
    </view>

    <!-- 查看类工具 -->
    <view class="section">
      <view class="sec-head">
        <text class="sec-title">查看类工具</text>
        <text class="sec-tag">可跨门店</text>
      </view>
      <view class="tool-grid">
        <view v-for="(t, i) in viewTools" :key="t.title" class="tool-card" :class="'tc-' + colors[i % colors.length]" @click="t.h5 ? goSmartOrder() : goTool(t.url)">
          <text class="tc-title">{{ t.title }}</text>
          <text class="tc-desc">{{ t.desc }}</text>
          <text class="tc-icon">{{ t.icon }}</text>
        </view>
      </view>
    </view>

    <!-- 执行类工具 -->
    <view class="section">
      <view class="sec-head">
        <text class="sec-title">执行类工具</text>
        <text class="sec-tag warn">{{ execLabel }}</text>
      </view>
      <view class="tool-grid">
        <view v-for="(t, i) in execTools" :key="t.title" class="tool-card" :class="'tc-' + colors[(i + viewTools.length) % colors.length]" @click="goExecTool(t)">
          <text class="tc-title">{{ t.title }}</text>
          <text class="tc-desc">{{ t.desc }}</text>
          <text class="tc-icon">{{ t.icon }}</text>
        </view>
      </view>
    </view>

    <!-- Store picker sheet -->
    <view v-if="showStoreSheet" class="mask" @click="showStoreSheet = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sh-title">选择执行门店</text>
          <text class="sh-close" @click="showStoreSheet = false">✕</text>
        </view>
        <text class="sh-hint">只作为本次工具操作上下文，不改变首页、工具、经营页的当前查看范围。</text>
        <view class="sh-list">
          <view v-for="s in myStores" :key="s.storeId" class="sh-item" @click="confirmExecStore(s)">
            <text class="shi-name">{{ s.storeName }}</text>
            <text class="shi-arrow">›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$w:#E58A2D;$so:#FFF8EE;
.page{min-height:100vh;background:$bg;padding:24rpx 24rpx 180rpx}

/* Scope card — same as home page */
.scope-card{background:$s;border-radius:16rpx;padding:24rpx;border:1.5px solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:36rpx}
.sc-kicker{font-size:22rpx;color:$t3}
.sc-label{display:block;margin-top:4rpx;font-size:32rpx;font-weight:700;color:$t1}
.sc-scroll{white-space:nowrap;margin:0 -24rpx;margin-top:20rpx}
.sc-row{display:flex;gap:16rpx;padding:0 24rpx}
.sc-chip{padding:14rpx 28rpx;border-radius:999rpx;font-size:26rpx;background:$s;color:$t2;flex-shrink:0;border:1.5px solid $b}
.sc-chip.on{background:$p;color:#fff;border-color:$p}

/* Sections */
.section{margin-bottom:48rpx}
.sec-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:24rpx}
.sec-title{font-size:34rpx;font-weight:700;color:$t1}
.sec-tag{font-size:22rpx;padding:4rpx 18rpx;border-radius:999rpx;background:$ps;color:$p}
.sec-tag.warn{background:$so;color:$w}

/* Tool grid — same style as home page */
.tool-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx}
.tool-card{border-radius:16rpx;padding:20rpx 24rpx;position:relative;overflow:hidden;border:1.5px solid $b;box-shadow:0 3rpx 10rpx rgba(31,36,33,.035)}
.tc-green{border-color:#CBEED8;background:linear-gradient(135deg,#ECFBF3,#F7FFFA)}
.tc-blue{border-color:#CBE7FA;background:linear-gradient(135deg,#EEF8FF,#F8FCFF)}
.tc-amber{border-color:#F1DDB7;background:linear-gradient(135deg,#FFF6E2,#FFFCF4)}
.tc-orange{border-color:#EFD6C8;background:linear-gradient(135deg,#FFF1E8,#FFFAF6)}
.tc-red{border-color:#EECFCA;background:linear-gradient(135deg,#FFF1F0,#FFFAFA)}
.tc-purple{border-color:#DDD5F2;background:linear-gradient(135deg,#F6F2FF,#FCFAFF)}
.tc-title{display:block;font-size:30rpx;font-weight:700;color:$t1;position:relative;z-index:1}
.tc-desc{display:block;margin-top:8rpx;font-size:24rpx;color:$t2;position:relative;z-index:1}
.tc-icon{position:absolute;right:12rpx;bottom:8rpx;font-size:44rpx;opacity:.18;z-index:0}

/* Sheet */
.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;border-radius:32rpx 32rpx 0 0;background:$s;padding:28rpx 32rpx calc(env(safe-area-inset-bottom) + 28rpx)}
.sheet-head{display:flex;justify-content:space-between;align-items:center}
.sh-title{font-size:34rpx;font-weight:700;color:$t1}
.sh-close{font-size:40rpx;color:$t2;padding:8rpx}
.sh-hint{display:block;font-size:26rpx;color:$t2;margin-top:8rpx;line-height:38rpx}
.sh-list{margin-top:32rpx}
.sh-item{display:flex;justify-content:space-between;align-items:center;padding:24rpx;border:1.5px solid $b;border-radius:16rpx;margin-bottom:16rpx}
.shi-name{font-size:30rpx;font-weight:600;color:$t1}
.shi-arrow{font-size:36rpx;color:#8C9691}
</style>
