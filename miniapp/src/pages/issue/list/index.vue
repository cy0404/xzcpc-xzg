<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchIssueList, fetchIssueOverview, acceptIssue, storeConfirm, reportIssueClick, type Issue } from '@/api/issue'
import { useUserStore } from '@/store/user'
import { fetchMyStores, switchStore } from '@/api/auth'
import { formatDateTime } from '@/utils/formatter'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'
import IssueRejectSheet from '@/components/IssueRejectSheet.vue'

const userStore = useUserStore()
const allMode = ref(false)
const loading = ref(true)
const records = ref<Issue[]>([])
const overview = ref({ processing: 0, pendingAcceptance: 0, resolved: 0, all: 0 })
const filter = ref<'all' | 'pending_acceptance' | 'processing' | 'resolved'>('all')
const acting = ref(false)

// 未解决原因弹窗
const showRejectSheet = ref(false)
const rejectIssueId = ref(0)

// 验收弹窗
const showAcceptSheet = ref(false)
const acceptTarget = ref<Issue | null>(null)
const acceptRemark = ref('')

// 门店选择 sheet
const showStoreSheet = ref(false)
const storeList = ref<any[]>([])
// 全部门店下点击"上报"时选门店
const pickingForReport = ref(false)

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'PENDING_CONFIRMATION', label: '待处理' },
  { key: 'IN_PROGRESS', label: '处理中' },
  { key: 'PENDING_ACCEPTANCE', label: '待验收' },
  { key: 'CLOSED', label: '已解决' },
] as const

const filtered = computed(() => {
  if (filter.value === 'all') return records.value
  if (filter.value === 'IN_PROGRESS') return records.value.filter((r) => r.status === 'IN_PROGRESS' || r.status === 'PENDING_CONTACT')
  return records.value.filter((r) => r.status === filter.value)
})

onLoad((q: any) => {
  allMode.value = q?.all === 'true'
  init()
})
onShow(() => { if (!loading.value) loadAll() })
onPullDownRefresh(async () => { await loadAll(); uni.stopPullDownRefresh() })

async function init() {
  loading.value = true
  try { await loadAll() } finally { loading.value = false }
}

async function loadAll() {
  await Promise.all([loadRecords(), loadOverview()])
}

async function loadRecords() {
  const params: any = { pageNum: 1, pageSize: 200 }
  if (allMode.value) params.all = true
  const data: any = await fetchIssueList(params)
  records.value = data.records || []
}

async function loadOverview() {
  try {
    if (allMode.value) {
      overview.value = await fetchIssueOverview(true)
    } else {
      overview.value = await fetchIssueOverview()
    }
  } catch { /* keep defaults */ }
}

function setFilter(key: typeof filter.value) {
  filter.value = key
}

function goDetail(item: Issue) {
  uni.navigateTo({ url: `/pages/issue/detail/index?id=${item.id}` })
}

// --- 门店切换 ---
async function openStorePicker() {
  try {
    storeList.value = (await fetchMyStores()) || []
  } catch { storeList.value = [] }
  pickingForReport.value = false
  showStoreSheet.value = true
}

async function selectAllStores() {
  showStoreSheet.value = false
  allMode.value = true
  await loadAll()
}

async function selectStore(store: any) {
  showStoreSheet.value = false
  try {
    const data: any = await switchStore(store.storeId)
    if (data?.token) {
      uni.setStorageSync('token', data.token)
      userStore.token = data.token
    }
    userStore.storeId = data?.storeId || store.storeId
    userStore.storeName = data?.storeName || store.storeName
    userStore.chatId = data?.chatId || ''
  } catch { /* ignore */ }
  allMode.value = false
  await loadAll()
}

// --- 上报新问题 ---
const ISSUE_FORM_BASE = 'https://www.xzcpc-9pd.top/task_platform/store-issue-form.html'

function goCreate() {
  if (allMode.value) {
    // 全部门店 → 先选门店再跳转
    openStorePickerForReport()
  } else {
    // 单门店 → 直接跳
    navigateToReport()
  }
}

async function openStorePickerForReport() {
  try {
    storeList.value = (await fetchMyStores()) || []
  } catch { storeList.value = [] }
  pickingForReport.value = true
  showStoreSheet.value = true
}

async function pickStoreForReport(store: any) {
  showStoreSheet.value = false
  pickingForReport.value = false
  try {
    const data: any = await switchStore(store.storeId)
    if (data?.token) {
      uni.setStorageSync('token', data.token)
      userStore.token = data.token
    }
    userStore.storeId = data?.storeId || store.storeId
    userStore.storeName = data?.storeName || store.storeName
    userStore.chatId = data?.chatId || ''
  } catch (e) {
    /* ignore */
  }
  allMode.value = false
  navigateToReport()
}

function navigateToReport() {
  reportIssueClick() // 记录"上报问题"日志
  const chatId = userStore.chatId || ''
  const target = `${ISSUE_FORM_BASE}?chatId=${encodeURIComponent(chatId)}&source=wxapp`
  uni.navigateTo({
    url: `/pages/webview/index?url=${encodeURIComponent(target)}`,
  })
}

// --- 验收 ---
function openAccept(item: Issue) {
  acceptTarget.value = item
  acceptRemark.value = ''
  showAcceptSheet.value = true
}

async function confirmAccept() {
  if (acting.value || !acceptTarget.value) return
  acting.value = true
  try {
    await acceptIssue(acceptTarget.value.id, acceptRemark.value)
    showAcceptSheet.value = false
    uni.showToast({ title: '已验收', icon: 'success' })
    await loadAll()
  } catch { /* handled by request.ts */ }
  finally { acting.value = false }
}

async function handleStoreConfirm(item: Issue, action: 'accept' | 'reject') {
  if (acting.value) return
  if (action === 'reject') {
    rejectIssueId.value = item.id
    showRejectSheet.value = true
    return
  }
  // accept 保持原有确认弹窗
  const res: any = await new Promise(resolve => {
    uni.showModal({ title: '确认已解决', content: '确定问题已解决吗？', success: r => resolve(r.confirm) })
  })
  if (!res) return
  acting.value = true
  try {
    await storeConfirm(item.id, action)
    uni.showToast({ title: '操作成功', icon: 'success' })
    await loadAll()
  } catch { /* handled */ }
  finally { acting.value = false }
}

function onRejectSuccess() {
  loadAll()
}

/** 从 recordsData JSON 提取最新回复 text */
function latestRecordText(item: Issue): string {
  if (!item.recordsData) return ''
  try {
    const data = JSON.parse(item.recordsData)
    const inner = data?.data || data
    return inner?.latestRecord?.text || ''
  } catch { return '' }
}

// --- 展示辅助 ---
function severityClass(s: string): string {
  if (!s) return 'sv-default'
  if (s.includes('严重')) return 'sv-danger'
  if (s.includes('较大')) return 'sv-warn'
  if (s.includes('一般')) return 'sv-normal'
  if (s.includes('轻微')) return 'sv-light'
  return 'sv-default'
}
function statusLabel(s: string): string {
  return ({
    PENDING_CONFIRMATION: '待处理',
    IN_PROGRESS: '处理中',
    PENDING_CONTACT: '未联系上',
    PENDING_ACCEPTANCE: '待验收',
    RESOLVED: '已解决',
    CLOSED: '已解决',
    OVERDUE: '逾期',
  } as Record<string, string>)[s] || s
}
function statusClass(s: string): string {
  return ({
    PENDING_CONFIRMATION: 'st-gray',
    IN_PROGRESS: 'st-warn',
    PENDING_CONTACT: 'st-danger',
    PENDING_ACCEPTANCE: 'st-warn',
    RESOLVED: 'st-primary',
    CLOSED: 'st-primary',
    OVERDUE: 'st-danger',
  } as Record<string, string>)[s] || 'st-gray'
}
</script>

<template>
  <view class="page">
    <!-- 概览面板 -->
    <view class="overview-card">
      <view class="ov-top">
        <view class="ov-title-row" @click="openStorePicker()">
          <text class="ov-title">{{ allMode ? '全部门店' : (userStore.storeName || '当前门店') }}</text>
          <text v-if="userStore.storeCount > 1" class="ov-switch-icon">⇄</text>
        </view>
        <text class="ov-sub">{{ allMode ? '全部门店问题汇总' : '本店问题汇总' }}</text>
      </view>
      <view class="ov-metrics">
        <view class="ov-item">
          <text class="ov-label">处理中</text>
          <text class="ov-val warn">{{ overview.processing }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">所有问题</text>
          <text class="ov-val">{{ overview.all }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">已解决</text>
          <text class="ov-val primary">{{ overview.resolved }}</text>
        </view>
      </view>
    </view>

    <!-- 状态筛选 tab -->
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ active: filter === t.key }"
        @click="setFilter(t.key)"
      >{{ t.label }}</view>
    </view>

    <!-- 加载 -->
    <Skeleton v-if="loading" :rows="4" />

    <!-- 列表 -->
    <template v-else-if="filtered.length">
      <view v-for="r in filtered" :key="r.id" class="i-card" @click="goDetail(r)">
        <view class="ic-header">
          <view class="ic-tags">
            <text class="ic-tag" :class="severityClass(r.urgency)">{{ r.urgency }}</text>
            <text class="ic-tag" :class="statusClass(r.status)">{{ statusLabel(r.status) }}</text>
          </view>
          <text class="ic-time">{{ formatDateTime(r.createdAt) }}</text>
        </view>
        <text class="ic-title">{{ r.title }}</text>
        <text class="ic-type">{{ r.issueType }}</text>
        <view v-if="latestRecordText(r)" class="ic-progress">
          <text class="ip-label">最新进度</text>
          <text class="ip-text">{{ latestRecordText(r) }}</text>
        </view>
        <view class="ic-foot">
          <view v-if="r.status === 'PENDING_ACCEPTANCE'" class="ic-btns">
            <button class="tc-btn tc-btn-secondary" @click.stop="handleStoreConfirm(r, 'reject')">未解决</button>
            <button class="tc-btn tc-btn-primary" @click.stop="handleStoreConfirm(r, 'accept')">已解决</button>
          </view>
          <view class="ic-link">
            <text>查看详情</text>
            <text class="ic-arrow">›</text>
          </view>
        </view>
      </view>
    </template>

    <EmptyState v-else text="当前没有这类问题" />

    <!-- 底部上报 -->
    <view class="fab">
      <view class="fab-btn" @click="goCreate">＋ 上报新问题</view>
    </view>

    <!-- 门店选择 sheet -->
    <view v-if="showStoreSheet" class="mask" @click="showStoreSheet = false">
      <view class="sheet" @click.stop>
        <view class="sh-handle"></view>
        <view class="sheet-head">
          <text class="sh-title">{{ pickingForReport ? '选择上报门店' : '切换门店' }}</text>
          <text class="sh-close" @click="showStoreSheet = false">✕</text>
        </view>
        <view v-if="!pickingForReport" class="sh-item sh-all" :class="{ active: allMode }" @click="selectAllStores()">
          <text class="shi-name">全部门店</text>
          <text v-if="allMode" class="shi-check">✓</text>
        </view>
        <view
          v-for="s in storeList"
          :key="s.storeId"
          class="sh-item"
          :class="{ active: !allMode && s.storeId === userStore.storeId }"
          @click="pickingForReport ? pickStoreForReport(s) : selectStore(s)"
        >
          <text class="shi-name">{{ s.storeName }}</text>
          <text v-if="!allMode && s.storeId === userStore.storeId && !pickingForReport" class="shi-check">✓</text>
        </view>
      </view>
    </view>

    <!-- 验收弹窗 -->
    <view v-if="showAcceptSheet" class="mask" @click="showAcceptSheet = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <view>
            <text class="sh-title">验收问题</text>
            <text class="sh-sub">如果问题已解决，可点击「确认已解决」</text>
          </view>
          <text class="sh-close" @click="showAcceptSheet = false">✕</text>
        </view>
        <text class="sh-label">验收备注</text>
        <textarea v-model="acceptRemark" class="sh-input" placeholder="可填写验收说明或补充信息" :maxlength="200" />
        <view class="sh-actions">
          <button class="sh-btn sh-cancel" @click="showAcceptSheet = false">取消</button>
          <button class="sh-btn sh-confirm" :disabled="acting" @click="confirmAccept">确认已解决</button>
        </view>
      </view>
    </view>
    <!-- 未解决原因弹窗 -->
    <IssueRejectSheet
      :visible="showRejectSheet"
      :issue-id="rejectIssueId"
      @close="showRejectSheet = false"
      @success="onRejectSuccess"
    />
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$w:#E58A2D;$d:#E05A47;$so:#FFF8EE;$sr:#FFF4F2;$sub:#FAFBF9;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}

/* Overview */
.overview-card{background:$s;border-radius:24rpx;padding:32rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:24rpx}
.ov-top{display:flex;flex-direction:column;gap:8rpx}
.ov-title-row{display:flex;align-items:center;gap:12rpx}
.ov-title{font-size:34rpx;font-weight:700;color:$t1}
.ov-switch-icon{font-size:32rpx;color:$p;font-weight:700}
.ov-sub{font-size:24rpx;color:$t2}
.ov-metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:8rpx;margin-top:28rpx}
.ov-item{text-align:center}
.ov-label{display:block;font-size:22rpx;color:$t3}
.ov-val{display:block;margin-top:6rpx;font-size:40rpx;font-weight:800;color:$t1}
.ov-val.warn{color:$w}
.ov-val.primary{color:$p}

/* Tabs */
.tabs{display:grid;grid-template-columns:repeat(5,1fr);gap:6rpx;background:$s;border-radius:999rpx;padding:8rpx;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:24rpx}
.tab{height:64rpx;display:flex;align-items:center;justify-content:center;border-radius:999rpx;font-size:24rpx;font-weight:500;color:$t2}
.tab.active{background:$p;color:#fff}

/* Cards */
.i-card{background:$s;border-radius:20rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);padding:28rpx;margin-bottom:20rpx}
.ic-header{display:flex;justify-content:space-between;align-items:center;gap:16rpx;margin-bottom:14rpx}
.ic-tags{display:flex;gap:12rpx}
.ic-tag{padding:4rpx 18rpx;border-radius:999rpx;font-size:22rpx;font-weight:600}
.ic-tag.sv-danger{background:$sr;color:$d}
.ic-tag.sv-warn{background:$so;color:$w}
.ic-tag.sv-normal{background:#EEF4FA;color:#435F7C}
.ic-tag.sv-light{background:$sub;color:$t2}
.ic-tag.sv-default{background:$sub;color:$t2}
.ic-tag.st-warn{background:$so;color:$w}
.ic-tag.st-primary{background:$ps;color:$p}
.ic-tag.st-danger{background:$sr;color:$d}
.ic-tag.st-gray{background:$sub;color:$t2}
.ic-time{font-size:22rpx;color:$t3}
.ic-title{display:block;font-size:30rpx;font-weight:700;color:$t1;line-height:42rpx}
.ic-type{display:block;margin-top:8rpx;font-size:26rpx;color:$t2}
.ic-progress{margin-top:16rpx;background:$sub;border-radius:12rpx;padding:16rpx 20rpx}
.ip-label{display:block;font-size:22rpx;font-weight:600;color:$t3}
.ip-text{display:block;margin-top:4rpx;font-size:26rpx;color:$t2;line-height:38rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ic-foot{display:flex;flex-direction:column;gap:16rpx;margin-top:20rpx}
.ic-foot .tc-btn{flex:1;height:64rpx;border-radius:999rpx;font-size:26rpx;font-weight:600;border:0;display:flex;align-items:center;justify-content:center}
.ic-foot .tc-btn-primary{background:$p;color:#fff}
.ic-foot .tc-btn-secondary{border:2rpx solid $b;background:$s;color:$d}
.ic-foot .ic-btns{display:flex;gap:16rpx}
.ic-foot .ic-btns .tc-btn{flex:1}
.ic-link{display:flex;align-items:center;gap:4rpx;align-self:flex-end}
.ic-link text{font-size:26rpx;color:$p}
.ic-arrow{font-size:32rpx;color:$p}

/* FAB */
.fab{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:linear-gradient(to top,#fff 60%,transparent)}
.fab-btn{width:100%;height:96rpx;border-radius:16rpx;background:#247847;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:600;box-shadow:0 8rpx 24rpx rgba(36,120,71,.3)}

/* Store picker sheet */
.mask{position:fixed;inset:0;z-index:200;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;max-height:70vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column;overflow:hidden;padding-bottom:32rpx}
.sh-handle{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:20rpx auto;flex-shrink:0}
.sheet-head{display:flex;justify-content:center;padding:8rpx 32rpx 24rpx;position:relative;flex-shrink:0}
.sh-title{font-size:36rpx;font-weight:700;color:$t1}
.sh-close{position:absolute;right:32rpx;font-size:40rpx;color:$t2;padding:8rpx}
.sh-item{display:flex;align-items:center;justify-content:space-between;padding:28rpx 32rpx;margin:0 32rpx 12rpx;border-radius:16rpx;background:#FAFBFC;border:2rpx solid transparent}
.sh-item.active{background:#F1F8F3;border-color:$p}
.sh-all{border:2rpx solid $b;background:#FAFBF9}
.shi-name{font-size:30rpx;font-weight:600;color:$t1}
.shi-check{font-size:32rpx;color:$p;font-weight:700}
.sh-label{display:block;margin-top:28rpx;font-size:26rpx;font-weight:600;color:$t1}
.sh-input{margin-top:16rpx;width:100%;min-height:160rpx;box-sizing:border-box;border:2rpx solid $b;border-radius:16rpx;background:$sub;padding:20rpx;font-size:28rpx;color:$t1}
.sh-actions{display:grid;grid-template-columns:1fr 1fr;gap:20rpx;margin-top:28rpx}
.sh-btn{height:88rpx;border-radius:16rpx;font-size:30rpx;font-weight:600;border:0;display:flex;align-items:center;justify-content:center}
.sh-cancel{border:2rpx solid $b;background:$s;color:$t2}
.sh-confirm{background:$p;color:#fff}
</style>
