<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchTransferList, fetchTransferOverview, fetchTransferOverviewTotal, cancelTransfer, confirmTransfer, rejectTransfer, receiveTransfer, type TransferOrder, type TransferOrderItem } from '@/api/transfer'
import { useUserStore } from '@/store/user'
import { fetchMyStores, switchStore } from '@/api/auth'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'
import { topUpSubscribeOnce, ensureBackHome, applyStoreFromQuery } from '@/utils/subscribe'

const userStore = useUserStore()
const allMode = ref(false)
const loading = ref(true)
const records = ref<TransferOrder[]>([])
const itemsMap = ref<Record<string, TransferOrderItem[]>>({})
const overview = ref({ pendingConfirm: 0, pendingShip: 0, completed: 0, rejected: 0 })
const acting = ref(false)

// Custom store picker
const showStoreSheet = ref(false)
const storeList = ref<any[]>([])

// 订阅消息落地页：冷启动直达垫首页；URL 带业务门店（storeId）且与当前店不同 → 先切店再加载
const storeSwitching = ref(false)
onLoad(async (q: any) => {
  ensureBackHome() // 订阅消息冷启动直达：垫首页让返回可回首页
  allMode.value = q?.all === 'true'
  if (q?.storeId && q.storeId !== userStore.storeId) {
    storeSwitching.value = true
    await applyStoreFromQuery(q)
    storeSwitching.value = false
  }
  init()
})
onShow(() => { if (!storeSwitching.value && !loading.value) { loadAll() } })
onPullDownRefresh(async () => { await loadAll(); uni.stopPullDownRefresh() })

async function init() {
  loading.value = true
  try { await loadAll() } finally { loading.value = false }
}

async function loadAll() {
  await Promise.all([loadRecords(), loadOverview()])
}

async function loadRecords() {
  const params: any = { pageNum: 1, pageSize: 100 }
  if (allMode.value) params.all = true
  const data: any = await fetchTransferList(params)
  records.value = data.records || []
  itemsMap.value = data.itemsMap || {}
}

async function loadOverview() {
  try {
    if (allMode.value) {
      overview.value = await fetchTransferOverviewTotal()
    } else {
      overview.value = await fetchTransferOverview()
    }
  } catch { /* keep defaults */ }
}

async function openStorePicker() {
  try {
    storeList.value = (await fetchMyStores()) || []
  } catch { storeList.value = [] }
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
  } catch { /* ignore */ }
  allMode.value = false
  await loadAll()
}

function goCreate() {
  uni.navigateTo({ url: '/pages/transfer/form/index' })
}

function goDetail(item: TransferOrder) {
  uni.navigateTo({ url: `/pages/transfer/detail/index?id=${item.id}` })
}

// --- Card helpers ---
function isFromStore(item: TransferOrder): boolean {
  return item.fromStoreId === userStore.storeId
}

function isToStore(item: TransferOrder): boolean {
  return item.toStoreId === userStore.storeId
}

function directionLabel(item: TransferOrder): string {
  if (isToStore(item)) return '调入申请'
  if (isFromStore(item)) return '调出申请'
  return '调货'
}

function directionClass(item: TransferOrder): string {
  if (isToStore(item)) return 'dir-in'
  if (isFromStore(item)) return 'dir-out'
  return ''
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    pending_confirm: '待确认',
    confirmed: '待交接',
    pending_ship: '待收货',
    pending_receive: '已收货',
    completed: '已完成',
    cancelled: '已取消',
    rejected: '已拒绝',
  }
  return map[status] || status
}

function statusClass(status: string): string {
  const map: Record<string, string> = {
    pending_confirm: 'st-warn',
    confirmed: 'st-warn',
    pending_ship: 'st-primary',
    pending_receive: 'st-primary',
    completed: 'st-done',
    cancelled: 'st-gray',
    rejected: 'st-danger',
  }
  return map[status] || ''
}

function returnLabel(item: TransferOrder): string {
  if (item.handoff === 'returned') return '已还'
  if (item.status === 'completed') return '待还'
  return ''
}
function returnClass(item: TransferOrder): string {
  if (item.handoff === 'returned') return 'st-done'
  if (item.status === 'completed') return 'st-warn'
  return ''
}

/** Whether the current user can act on this card */
function cardActions(item: TransferOrder): { label: string; type: 'primary' | 'secondary' | 'danger'; action: () => void }[] {
  const actions: { label: string; type: 'primary' | 'secondary' | 'danger'; action: () => void }[] = []

  if (item.status === 'pending_confirm') {
    if (isToStore(item)) {
      // 发起方（调入方）：可取消
      actions.push({ label: '取消', type: 'secondary', action: () => handleCancel(item) })
    }
    if (isFromStore(item)) {
      // 调出方：可拒绝、可确认
      actions.push({ label: '拒绝', type: 'danger', action: () => handleReject(item) })
      actions.push({ label: '确认可调出', type: 'primary', action: () => handleConfirm(item) })
    }
  }

  if (item.status === 'pending_ship' && isToStore(item)) {
    actions.push({ label: '确认收货', type: 'primary', action: () => handleReceive(item) })
  }

  if (item.status === 'completed' && item.handoff !== 'returned' && isToStore(item)) {
    actions.push({ label: '还货', type: 'primary', action: () => uni.navigateTo({ url: `/pages/transfer/return/index?id=${item.id}` }) })
  }
  if (item.status === 'completed' && item.handoff !== 'returned' && isFromStore(item)) {
    actions.push({ label: '还货记录', type: 'primary', action: () => uni.navigateTo({ url: `/pages/transfer/return/index?id=${item.id}&role=review` }) })
  }

  return actions
}

async function handleCancel(item: TransferOrder) {
  if (acting.value) return
  acting.value = true
  try {
    await cancelTransfer(item.id)
    uni.showToast({ title: '已取消', icon: 'success' })
    await loadAll()
  } catch { /* error toast handled by request.ts */ }
  finally { acting.value = false }
}

async function handleReject(item: TransferOrder) {
  if (acting.value) return
  acting.value = true
  try {
    await rejectTransfer(item.id)
    uni.showToast({ title: '已拒绝', icon: 'success' })
    await loadAll()
  } catch { /* handled */ }
  finally { acting.value = false }
}

async function handleConfirm(item: TransferOrder) {
  if (acting.value) return
  topUpSubscribeOnce() // 调货确认（tap 内）→ 订阅授权充值（调入方将收到待收货提醒）
  acting.value = true
  try {
    await confirmTransfer(item.id)
    uni.showToast({ title: '已确认', icon: 'success' })
    await loadAll()
  } catch { /* handled */ }
  finally { acting.value = false }
}

async function handleReceive(item: TransferOrder) {
  if (acting.value) return
  topUpSubscribeOnce() // 调货收货（tap 内）→ 订阅授权充值
  acting.value = true
  try {
    await receiveTransfer(item.id)
    uni.showToast({ title: '已收货', icon: 'success' })
    await loadAll()
  } catch { /* handled */ }
  finally { acting.value = false }
}

function completedLabel(item: TransferOrder): string {
  if (isFromStore(item)) return '调出记录'
  if (isToStore(item)) return '调入记录'
  return '调货记录'
}

function cardMaterialSummary(item: TransferOrder): string {
  const items = itemsMap.value[String(item.id)] || []
  if (items.length === 0) return item.remark || '查看详情'
  const firstName = items[0].materialName
  return items.length > 1 ? `${firstName}等${items.length}种` : firstName
}

function cardFlow(item: TransferOrder): string {
  return `调出：${item.fromStoreName} → 调入：${item.toStoreName}`
}

function overviewItemLabel(item: TransferOrder): string {
  return isToStore(item) ? '调入申请' : '调出申请'
}
</script>

<template>
  <view class="page">
    <!-- Overview panel -->
    <view class="overview-card">
      <view class="ov-top">
        <view class="ov-title-row" @click="openStorePicker()">
          <text class="ov-title">{{ allMode ? '全部门店' : (userStore.storeName || '当前门店') }}</text>
          <text v-if="userStore.storeCount > 1" class="ov-switch-icon">⇄</text>
        </view>
        <text class="ov-sub">{{ allMode ? '全部门店调货汇总' : '本店调货汇总' }}</text>
      </view>
      <view class="ov-metrics">
        <view class="ov-item">
          <text class="ov-label">待确认</text>
          <text class="ov-val warn">{{ overview.pendingConfirm }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">待收货</text>
          <text class="ov-val primary">{{ overview.pendingShip }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">已完成</text>
          <text class="ov-val">{{ overview.completed }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">已拒绝</text>
          <text class="ov-val reject">{{ overview.rejected }}</text>
        </view>
      </view>
    </view>

    <!-- Section title -->
    <view class="sec-title">
      <text class="sec-title-text">调货单</text>
    </view>

    <!-- Loading -->
    <Skeleton v-if="loading" :rows="4" />

    <!-- Card list -->
    <template v-else-if="records.length">
      <view v-for="r in records" :key="r.id" class="t-card" @click="goDetail(r)">
        <view class="tc-header">
          <view>
            <text class="tc-dir-tag" :class="directionClass(r)">{{ overviewItemLabel(r) }}</text>
            <text class="tc-title">{{ cardMaterialSummary(r) }}</text>
          </view>
          <view style="display:flex;gap:8px;align-items:center">
            <text class="tc-status" :class="statusClass(r.status)">{{ statusLabel(r.status) }}</text>
            <text v-if="returnLabel(r)" class="tc-status" :class="returnClass(r)">{{ returnLabel(r) }}</text>
          </view>
        </view>

        <text class="tc-summary">{{ cardFlow(r) }}</text>

        <!-- Action buttons -->
        <view v-if="cardActions(r).length" class="tc-actions">
          <button
            v-for="act in cardActions(r)"
            :key="act.label"
            class="tc-btn"
            :class="act.type === 'primary' ? 'tc-btn-primary' : act.type === 'danger' ? 'tc-btn-danger' : 'tc-btn-secondary'"
            @click.stop="act.action()"
          >
            {{ act.label }}
          </button>
        </view>

        <!-- View link -->
        <view class="tc-link">
          <text>查看调货单</text>
          <text class="tc-arrow">›</text>
        </view>
      </view>
    </template>

    <EmptyState v-else text="暂无调货记录" />

    <!-- Bottom action -->
    <view class="fab">
      <view class="fab-btn" @click="goCreate">发起调货</view>
    </view>

    <!-- Store picker sheet -->
    <view v-if="showStoreSheet" class="mask" @click="showStoreSheet = false">
      <view class="sheet" @click.stop>
        <view class="sh-handle"></view>
        <view class="sheet-head">
          <text class="sh-title">切换门店</text>
          <text class="sh-close" @click="showStoreSheet = false">✕</text>
        </view>
        <!-- 全部门店 -->
        <view class="sh-item sh-all" :class="{ active: allMode }" @click="selectAllStores()">
          <text class="shi-name">全部门店</text>
          <text v-if="allMode" class="shi-check">✓</text>
        </view>
        <!-- 各门店 -->
        <view
          v-for="s in storeList"
          :key="s.storeId"
          class="sh-item"
          :class="{ active: !allMode && s.storeId === userStore.storeId }"
          @click="selectStore(s)"
        >
          <text class="shi-name">{{ s.storeName }}</text>
          <text v-if="!allMode && s.storeId === userStore.storeId" class="shi-check">✓</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$w:#E58A2D;$d:#E05A47;$so:#FFF8EE;$sr:#FFF4F2;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}

/* Overview */
.overview-card{background:$s;border-radius:24rpx;padding:32rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:32rpx}
.ov-top{display:flex;flex-direction:column;gap:8rpx}
.ov-title-row{display:flex;align-items:center;gap:12rpx}
.ov-title{font-size:34rpx;font-weight:700;color:$t1}
.ov-switch-icon{font-size:32rpx;color:$p;font-weight:700}
.ov-sub{font-size:24rpx;color:$t2}
.ov-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:8rpx;margin-top:28rpx}
.ov-item{text-align:center}
.ov-label{display:block;font-size:22rpx;color:$t3}
.ov-val{display:block;margin-top:6rpx;font-size:36rpx;font-weight:800;color:$t1}
.ov-val.warn{color:$w}
.ov-val.primary{color:$p}
.ov-val.reject{color:$d}

/* Section */
.sec-title{margin-bottom:20rpx}
.sec-title-text{font-size:34rpx;font-weight:700;color:$t1}

/* Cards */
.t-card{background:$s;border-radius:20rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);padding:28rpx;margin-bottom:20rpx}
.tc-header{display:flex;justify-content:space-between;align-items:flex-start;gap:16rpx}
.tc-dir-tag{display:inline-block;padding:4rpx 18rpx;border-radius:999rpx;font-size:22rpx;font-weight:600;margin-bottom:10rpx}
.tc-dir-tag.dir-in{background:$ps;color:$p}
.tc-dir-tag.dir-out{background:$so;color:$w}
.tc-title{display:block;font-size:30rpx;font-weight:700;color:$t1;line-height:40rpx}
.tc-status{padding:6rpx 18rpx;border-radius:999rpx;font-size:22rpx;font-weight:600;flex-shrink:0}
.tc-status.st-warn{background:$so;color:$w}
.tc-status.st-primary{background:$ps;color:$p}
.tc-status.st-done{background:#F1F8F3;color:$p}
.tc-status.st-danger{background:$sr;color:$d}
.tc-status.st-gray{background:#FAFBF9;color:$t2}
.tc-summary{display:block;margin-top:14rpx;font-size:26rpx;color:$t2;line-height:38rpx}
.tc-actions{display:grid;grid-template-columns:repeat(auto-fit,minmax(0,1fr));gap:16rpx;margin-top:20rpx}
.tc-btn{width:100%;height:80rpx;border-radius:999rpx;font-size:28rpx;font-weight:600;border:0;display:flex;align-items:center;justify-content:center}
.tc-btn-primary{background:$p;color:#fff}
.tc-btn-secondary{border:2rpx solid $b;background:$s;color:$p}
.tc-btn-danger{border:2rpx solid $b;background:$s;color:$d}
.tc-link{display:flex;justify-content:flex-end;align-items:center;gap:4rpx;margin-top:20rpx}
.tc-link text{font-size:26rpx;color:$p}
.tc-arrow{font-size:32rpx;color:$p}

/* FAB */
.fab{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:linear-gradient(to top,#fff 60%,transparent)}
.fab-btn{width:100%;height:96rpx;border-radius:16rpx;background:#247847;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:600;box-shadow:0 8rpx 24rpx rgba(36,120,71,.3)}

/* Store picker sheet */
.mask{position:fixed;inset:0;z-index:200;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;max-height:70vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column;overflow:hidden}
.sh-handle{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:20rpx auto;flex-shrink:0}
.sheet-head{display:flex;justify-content:center;padding:8rpx 32rpx 24rpx;position:relative;flex-shrink:0}
.sh-title{font-size:36rpx;font-weight:700;color:$t1}
.sh-close{position:absolute;right:32rpx;font-size:40rpx;color:$t2;padding:8rpx}
.sh-item{display:flex;align-items:center;justify-content:space-between;padding:28rpx 32rpx;margin:0 32rpx 12rpx;border-radius:16rpx;background:#FAFBFC;border:2rpx solid transparent}
.sh-item.active{background:#F1F8F3;border-color:$p}
.sh-all{border:2rpx solid $b;background:#FAFBF9}
.shi-name{font-size:30rpx;font-weight:600;color:$t1}
.shi-check{font-size:32rpx;color:$p;font-weight:700}
</style>
