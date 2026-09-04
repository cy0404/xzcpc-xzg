<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { fetchTransferDetail, confirmTransfer, shipTransfer, receiveTransfer, cancelTransfer, rejectTransfer, type TransferOrder, type TransferOrderItem } from '@/api/transfer'
import { useUserStore } from '@/store/user'
import Skeleton from '@/components/Skeleton.vue'
import { topUpSubscribeOnce } from '@/utils/subscribe'

const userStore = useUserStore()
const transferId = ref<number>(0)
const loading = ref(true)
const order = ref<TransferOrder | null>(null)
const items = ref<TransferOrderItem[]>([])
const returnRecords = ref<any[]>([])
const acting = ref(false)
const detailTab = ref('transfer')

onLoad((q: any) => { transferId.value = Number(q?.id) || 0 })
onShow(() => { if (transferId.value) loadDetail() })

async function loadDetail() {
  loading.value = true
  try {
    const data = await fetchTransferDetail(transferId.value)
    order.value = data.order
    items.value = data.items || []
    returnRecords.value = data.returnRecords || []
  } finally { loading.value = false }
}

function getItemRecords(itemId: number) {
  return returnRecords.value.filter((r: any) => r.itemId === itemId)
}
const isReturned = computed(() => order.value?.handoff === 'returned')

const isFromStore = computed(() => order.value?.fromStoreId === userStore.storeId)
const isToStore = computed(() => order.value?.toStoreId === userStore.storeId)

const directionLabel = computed(() => {
  if (!order.value) return ''
  if (isToStore.value) return '调入申请'
  if (isFromStore.value) return '调出申请'
  return '调货'
})

const statusLabel = computed(() => {
  const map: Record<string, string> = {
    pending_confirm: '待确认', confirmed: '待交接', pending_ship: '待收货',
    pending_receive: '已收货', completed: '已完成', cancelled: '已取消', rejected: '已拒绝', returned: '已归还',
  }
  return map[order.value?.status || ''] || order.value?.status || ''
})

const statusTagClass = computed(() => {
  const s = order.value?.status
  if (s === 'pending_confirm') return 'tag-warn'
  if (s === 'completed' || s === 'pending_ship' || s === 'pending_receive') return 'tag-primary'
  if (s === 'cancelled') return 'tag-gray'
  if (s === 'rejected') return 'tag-danger'
  return 'tag-primary'
})

const noteText = computed(() => {
  const s = order.value?.status
  if (s === 'pending_confirm') {
    return isToStore.value ? `等待${order.value?.fromStoreName}确认是否可调出。` : `需要本店确认是否可调出。`
  }
  if (s === 'confirmed') return '调出方已确认，等待发货交接。'
  if (s === 'pending_ship') {
    return isToStore.value ? '对方已发货，等待本店确认收货。' : '已发货，等待对方确认收货。'
  }
  if (s === 'pending_receive') {
    return isToStore.value ? '已确认收货，流程完成。' : '对方已确认收货，流程完成。'
  }
  if (s === 'completed') return '调货流程已完成。'
  if (s === 'cancelled') return '该调货单已取消。'
  if (s === 'rejected') return `拒绝原因：${order.value?.fromStoreName}无法调出。`
  return ''
})

const isMine = computed(() => order.value?.creatorStoreId === userStore.storeId)
const creatorName = computed(() => {
  if (!order.value) return ''
  return order.value.creatorStoreId === order.value.fromStoreId ? order.value.fromStoreName : order.value.toStoreName
})
const otherName = computed(() => {
  if (!order.value) return ''
  return order.value.creatorStoreId === order.value.fromStoreId ? order.value.toStoreName : order.value.fromStoreName
})
const isOut = computed(() => order.value?.creatorStoreId === order.value?.fromStoreId)

const steps = computed(() => {
  if (!order.value) return []
  const s = order.value.status
  const stepsList: string[] = []

  if (isOut.value) {
    stepsList.push(`${creatorName.value}发起调出申请`)
  } else {
    stepsList.push(`${creatorName.value}发起调入申请`)
  }

  if (s === 'rejected') {
    stepsList.push(`${otherName.value}已拒绝`)
    stepsList.push('调货单结束')
    return stepsList
  }
  if (s === 'cancelled') {
    stepsList.push('已取消')
    stepsList.push('调货单结束')
    return stepsList
  }

  if (s === 'pending_confirm') {
    stepsList.push(`等待${otherName.value}确认`)
    return stepsList
  }

  if (s === 'pending_ship') {
    stepsList.push(`等待${otherName.value}收货确认`)
    return stepsList
  }

  if (s === 'pending_receive' || s === 'completed' || s === 'returned') {
    stepsList.push(`${otherName.value}已收货`)
    if (s === 'returned') {
      stepsList.push('已完成')
      stepsList.push('物料已全部归还')
    } else {
      stepsList.push('已完成')
    }
  }

  return stepsList
})

const stepIndex = computed(() => {
  const s = order.value?.status
  if (s === 'pending_confirm' || s === 'cancelled') return 0
  if (s === 'rejected') return steps.value.length - 1
  if (s === 'pending_ship') return 0  // 等待收货=进行中，"发起"已完成
  if (s === 'pending_receive') return 1 // 已收货，"发起+收货"已完成，"完成"进行中
  if (s === 'completed') return steps.value.length - 1 // 全部完成
  if (s === 'returned') return steps.value.length - 1 // 全部归还
  return steps.value.length - 1
})

const totalAmount = computed(() => {
  if (!items.value.length) return '0.00'
  return items.value.reduce((s, i) => s + (i.transferQty || 0) * (i.unitPrice || 0), 0).toFixed(2)
})

const bottomActions = computed(() => {
  if (!order.value) return []
  const s = order.value.status
  const actions: { label: string; primary?: boolean; danger?: boolean; action: () => void }[] = []

  if (s === 'pending_confirm') {
    if (isToStore.value) {
      actions.push({ label: '取消调货', danger: true, action: handleCancel })
    }
    if (isFromStore.value) {
      actions.push({ label: '拒绝', danger: true, action: handleReject })
      actions.push({ label: '确认可调出', primary: true, action: handleConfirm })
    }
  }
  if (s === 'pending_ship' && isToStore.value) {
    actions.push({ label: '确认收货', primary: true, action: handleReceive })
  }
  if (s === 'completed' && order.value?.handoff !== 'returned' && isToStore.value) {
    actions.push({ label: '还货', primary: true, action: () => uni.navigateTo({ url: `/pages/transfer/return/index?id=${transferId.value}` }) })
  }
  if (s === 'completed' && order.value?.handoff !== 'returned' && isFromStore.value) {
    actions.push({ label: '还货记录', primary: true, action: () => uni.navigateTo({ url: `/pages/transfer/return/index?id=${transferId.value}&role=review` }) })
  }

  return actions
})

async function handleConfirm() { await doAction(() => confirmTransfer(transferId.value), '已确认') }
async function handleShip() { await doAction(() => shipTransfer(transferId.value), '已标记发货') }
async function handleReceive() { await doAction(() => receiveTransfer(transferId.value), '已收货') }
async function handleCancel() {
  const res = await new Promise<boolean>(resolve => {
    uni.showModal({ title: '确认取消', content: '确定要取消该调货单吗？', success: r => resolve(r.confirm) })
  })
  if (!res) return
  await doAction(() => cancelTransfer(transferId.value), '已取消')
}
async function handleReject() {
  const res = await new Promise<boolean>(resolve => {
    uni.showModal({ title: '确认拒绝', content: '确定要拒绝该调货申请吗？', success: r => resolve(r.confirm) })
  })
  if (!res) return
  await doAction(() => rejectTransfer(transferId.value), '已拒绝')
}

async function doAction(fn: () => Promise<any>, msg: string) {
  if (acting.value) return
  topUpSubscribeOnce() // 调货动作（确认/发货/收货/取消/拒绝，tap 内）→ 订阅授权充值
  acting.value = true
  try {
    await fn()
    uni.showToast({ title: msg, icon: 'success' })
    await loadDetail()
  } catch { /* handled */ }
  finally { acting.value = false }
}

function goBack() { uni.navigateBack() }

function fmtTime(t: string | undefined): string {
  if (!t) return '--'
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<template>
  <view class="page">
    <!-- Loading -->
    <Skeleton v-if="loading" :rows="5" />

    <template v-else-if="order">
      <!-- Tab 切换(已归还单) -->
      <view v-if="isReturned" class="tab-bar">
        <view class="tab-item" :class="{ on: detailTab === 'transfer' }" @click="detailTab = 'transfer'">调拨详情</view>
        <view class="tab-item" :class="{ on: detailTab === 'return' }" @click="detailTab = 'return'">还货详情</view>
      </view>

      <!-- 调拨详情 -->
      <template v-if="!isReturned || detailTab === 'transfer'">
      <view class="info-card">
        <view class="ic-header">
          <text class="ic-tag" :class="statusTagClass">{{ statusLabel }}</text>
        </view>
        <text class="ic-note">{{ noteText }}</text>

        <!-- Grid info -->
        <view class="ic-grid">
          <view class="ic-cell">
            <text class="ic-label">调货方向</text>
            <text class="ic-value">{{ directionLabel }}</text>
          </view>
          <view class="ic-cell">
            <text class="ic-label">调出门店</text>
            <text class="ic-value">{{ order.fromStoreName }}</text>
          </view>
          <view class="ic-cell">
            <text class="ic-label">调入门店</text>
            <text class="ic-value">{{ order.toStoreName }}</text>
          </view>
          <view class="ic-cell">
            <text class="ic-label">更新时间</text>
            <text class="ic-value">{{ fmtTime(order.updatedAt) }}</text>
          </view>
        </view>

        <!-- Items -->
        <view class="ic-items">
          <text class="ic-section-title">调拨物料</text>
          <view v-for="item in items" :key="item.id" class="ic-item-row">
            <view class="ic-item-info">
              <text class="ic-item-name">{{ item.materialName }}</text>
              <text class="ic-item-qty">{{ item.transferQty }} {{ item.unit }}</text>
            </view>
          </view>
          <view class="ic-total">
            <text class="ic-total-label">总金额</text>
            <text class="ic-total-val">¥{{ totalAmount }}</text>
          </view>
        </view>

        <!-- Reason -->
        <view v-if="order.remark" class="ic-reason">
          <text>调货原因：{{ order.remark }}</text>
        </view>
      </view>

      <!-- Timeline -->
      <view class="timeline-card">
        <text class="tl-title">流程记录</text>
        <view class="tl-list">
          <view v-for="(step, index) in steps" :key="index" class="tl-item" :class="{ active: index <= stepIndex }">
            <view class="tl-dot" :class="{ on: index <= stepIndex }"></view>
            <text class="tl-text" :class="{ on: index <= stepIndex }">{{ step }}</text>
          </view>
        </view>
      </view>
      </template>

      <!-- 还货详情(仅已归还单) -->
      <template v-if="isReturned && detailTab === 'return'">
        <view class="info-card" v-for="it in items" :key="it.id">
          <view class="return-card-head">
            <text class="return-card-name">{{ it.materialName }}</text>
          </view>
          <view class="return-card-meta">{{ it.transferQty }} {{ it.unit }}<text v-if="it.unitPrice" class="price-tag"> · 单价：¥{{ it.unitPrice }}</text></view>
          <view v-if="getItemRecords(it.id).length" class="return-records">
            <view v-for="(r, i) in getItemRecords(it.id)" :key="i" class="rr-item">
              <text class="rr-text">{{ r.returnType === 'goods' ? `还货 ${r.returnQty} ${it.unit}` : `还钱 ¥${r.returnAmount || 0}` }}</text>
              <text class="rr-meta">{{ r.handlerName || '' }} · {{ r.createdAt ? r.createdAt.substring(0, 16) : '' }}</text>
            </view>
          </view>
          <view v-else class="return-empty">暂无归还记录</view>
        </view>
      </template>
    </template>

    <!-- Bottom actions -->
    <view v-if="bottomActions.length" class="bottom-bar" :class="'btns-' + Math.min(bottomActions.length, 2)">
      <button
        v-for="act in bottomActions"
        :key="act.label"
        class="bb-btn"
        :class="act.primary ? 'bb-primary' : 'bb-danger'"
        @click="act.action()"
      >
        {{ act.label }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$w:#E58A2D;$d:#E05A47;$so:#FFF8EE;$sr:#FFF4F2;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}

/* Tab bar */
.tab-bar{display:flex;gap:0;margin-bottom:24rpx;background:$s;border-radius:16rpx;overflow:hidden;border:2rpx solid $b}
.tab-item{flex:1;text-align:center;padding:18rpx 0;font-size:28rpx;font-weight:600;color:$t2;border-bottom:4rpx solid transparent}
.tab-item.on{color:$p;border-bottom-color:$p}

/* Info card */
.info-card{background:$s;border-radius:20rpx;border:2rpx solid $b;padding:28rpx;margin-bottom:24rpx}
.ic-header{margin-bottom:12rpx}
.ic-tag{display:inline-block;padding:6rpx 20rpx;border-radius:999rpx;font-size:22rpx;font-weight:600;margin-bottom:14rpx}
.ic-tag.tag-warn{background:$so;color:$w}
.ic-tag.tag-primary{background:$ps;color:$p}
.ic-tag.tag-danger{background:$sr;color:$d}
.ic-tag.tag-gray{background:#FAFBF9;color:$t2}
.ic-title{display:block;font-size:34rpx;font-weight:700;color:$t1;line-height:44rpx}
.ic-flow{display:block;margin-top:8rpx;font-size:26rpx;color:$t2}
.ic-note{display:block;margin-top:8rpx;font-size:26rpx;color:$t2;line-height:38rpx}

.ic-grid{display:grid;grid-template-columns:1fr 1fr;gap:16rpx;margin-top:24rpx;padding:24rpx;background:#FAFBF9;border-radius:12rpx}
.ic-label{display:block;font-size:22rpx;color:$t3}
.ic-value{display:block;margin-top:6rpx;font-size:28rpx;font-weight:600;color:$t1}

.ic-items{margin-top:20rpx;padding:20rpx 24rpx;background:#FAFBF9;border-radius:12rpx}
.ic-section-title{display:block;font-size:28rpx;font-weight:600;color:$t1;margin-bottom:12rpx}
.ic-item-row{display:flex;justify-content:space-between;align-items:center;padding:16rpx 0;border-bottom:1px solid $b}
.ic-item-row:last-child{border-bottom:0}
.ic-item-name{font-size:26rpx;font-weight:500;color:$t1;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ic-item-qty{font-size:26rpx;font-weight:700;color:$t1;flex-shrink:0;margin-left:16rpx}
.ic-item-info{display:flex;justify-content:space-between;align-items:center;width:100%}
.ic-total{display:flex;justify-content:space-between;align-items:center;padding-top:16rpx;border-top:1px solid $b;margin-top:12rpx}
.ic-total-label{font-size:28rpx;font-weight:600;color:$t1}
.ic-total-val{font-size:36rpx;font-weight:800;color:#E05A47}

.ic-reason{margin-top:20rpx;padding:20rpx 24rpx;background:$so;border-radius:12rpx;font-size:26rpx;color:$w;line-height:38rpx}

/* Timeline */
.timeline-card{background:$s;border-radius:20rpx;border:2rpx solid $b;padding:28rpx}
.tl-title{font-size:30rpx;font-weight:700;color:$t1;display:block;margin-bottom:24rpx}
.tl-item{display:flex;align-items:flex-start;gap:20rpx;padding-left:10rpx;margin-bottom:24rpx;position:relative}
.tl-item:last-child{margin-bottom:0}
.tl-dot{width:28rpx;height:28rpx;border-radius:50%;background:$b;flex-shrink:0;position:relative;z-index:1}
.tl-dot.on{background:$p;box-shadow:0 0 0 8rpx $ps}
.tl-text{font-size:26rpx;color:$t2;line-height:40rpx;padding-top:0}
.tl-text.on{color:$t1;font-weight:500}

/* Bottom bar */
.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:grid;gap:16rpx;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:rgba(255,255,255,.95);border-top:1px solid $b}
.bottom-bar.btns-1{grid-template-columns:1fr}
.bottom-bar.btns-2{grid-template-columns:1fr 1fr}
.bb-btn{width:100%;height:96rpx;border-radius:16rpx;font-size:30rpx;font-weight:600;border:0;display:flex;align-items:center;justify-content:center}
.bb-primary{background:#247847;color:#fff}
.bb-danger{border:2rpx solid $b;background:$s;color:$d}

/* Return detail */
.return-card-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:8rpx}
.return-card-name{font-size:28rpx;font-weight:600;color:$t1}
.return-card-tag{font-size:22rpx;font-weight:600;padding:4rpx 14rpx;border-radius:999rpx}
.return-card-tag.rc-green{background:$ps;color:$p}
.return-card-tag.rc-orange{background:$so;color:$w}
.return-card-meta{font-size:24rpx;color:$t2;margin-bottom:12rpx}
.price-tag{display:inline-block;font-size:20rpx;color:$t3;padding:2rpx 10rpx;background:#FAFBF9;border-radius:6rpx}
.return-records{margin-top:12rpx;border-top:1px solid $b;padding-top:12rpx}
.rr-item{display:flex;justify-content:space-between;align-items:center;padding:6rpx 0}
.rr-text{font-size:24rpx;color:$t1}
.rr-meta{font-size:22rpx;color:$t3}
.return-empty{text-align:center;font-size:24rpx;color:$t3;padding:16rpx 0}
</style>
