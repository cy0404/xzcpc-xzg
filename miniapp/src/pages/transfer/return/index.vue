<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchTransferDetail, doReturn, confirmReturn } from '@/api/transfer'
import { useUserStore } from '@/store/user'
import Skeleton from '@/components/Skeleton.vue'

interface ReturnItem {
  itemId: number
  materialName: string
  spec: string
  unit: string
  transferQty: number
  unitPrice: number
  returnedQty: number
  returnStatus: string
  returnAmount: number
  /** 模式: '' | 'goods' | 'money' | 'both' */
  mode: string
  /** 还货数量(仅还货/还货+还钱时) */
  goodsQty: string
  /** 还钱金额(仅还钱/还货+还钱时) */
  moneyAmount: string
}

const userStore = useUserStore()
const transferId = ref(0)
const loading = ref(true)
const submitting = ref(false)
const order = ref<any>(null)
const items = ref<ReturnItem[]>([])
const remark = ref('')
const returnRecords = ref<any[]>([])
const isReview = ref(false)

function getItemName(itemId: number): string {
  return items.value.find(it => it.itemId === itemId)?.materialName || ''
}
function getItemRecords(itemId: number) {
  return returnRecords.value.filter(r => r.itemId === itemId)
}

onLoad((q: any) => {
  transferId.value = Number(q?.id) || 0
  isReview.value = q?.role === 'review'
  if (transferId.value) loadDetail()
})

async function loadDetail() {
  loading.value = true
  try {
    const data = await fetchTransferDetail(transferId.value)
    order.value = data.order
    returnRecords.value = data.returnRecords || []
    items.value = (data.items || []).map((it: any) => ({
      itemId: it.id,
      materialName: it.materialName,
      spec: it.spec || '',
      unit: it.unit || '个',
      transferQty: it.transferQty || 0,
      unitPrice: it.unitPrice || 0,
      returnedQty: it.returnedQty || 0,
      returnStatus: it.returnStatus || '',
      returnAmount: it.returnAmount || 0,
      mode: '',
      goodsQty: '',
      moneyAmount: '',
    }))
  } finally { loading.value = false }
}

const remainingQty = (it: ReturnItem) => Math.max(0, it.transferQty - it.returnedQty)
const isReturned = (it: ReturnItem) => !!it.returnStatus

function setMode(it: ReturnItem, mode: string) {
  if (isReturned(it)) return
  // 已选中同一模式则取消
  if (it.mode === mode) { it.mode = ''; it.goodsQty = ''; it.moneyAmount = ''; return }
  it.mode = mode
  it.goodsQty = ''
  it.moneyAmount = ''
  if (mode === 'goods') {
    it.goodsQty = remainingQty(it).toFixed(2)
  } else if (mode === 'money') {
    const qty = remainingQty(it)
    it.moneyAmount = (qty * it.unitPrice).toFixed(2)
  } else if (mode === 'both') {
    it.goodsQty = remainingQty(it).toFixed(2)
    it.moneyAmount = ''
  }
}

const hasAction = computed(() => items.value.some(it => !isReturned(it) && it.mode))
const allReturned = computed(() => items.value.length > 0 && items.value.every(it => isReturned(it)))

async function submit() {
  if (submitting.value) return
  const reqItems: Array<{ itemId: number; returnType: string; returnQty: number; returnAmount: number }> = []
  for (const it of items.value) {
    if (isReturned(it) || !it.mode) continue
    if (it.mode === 'goods') {
      const qty = parseFloat(it.goodsQty) || 0
      if (qty <= 0) { uni.showToast({ title: `${it.materialName} 数量须>0`, icon: 'none' }); return }
      if (qty > remainingQty(it)) { uni.showToast({ title: `${it.materialName} 超过剩余`, icon: 'none' }); return }
      reqItems.push({ itemId: it.itemId, returnType: 'goods', returnQty: qty, returnAmount: 0 })
    } else if (it.mode === 'money') {
      const amt = parseFloat(it.moneyAmount) || 0
      if (amt <= 0) { uni.showToast({ title: `${it.materialName} 金额须>0`, icon: 'none' }); return }
      const qty = amt / (it.unitPrice || 1)
      reqItems.push({ itemId: it.itemId, returnType: 'money', returnQty: Math.round(qty * 100) / 100, returnAmount: amt })
    } else if (it.mode === 'both') {
      const gQty = parseFloat(it.goodsQty) || 0
      const amt = parseFloat(it.moneyAmount) || 0
      if (gQty <= 0 && amt <= 0) { uni.showToast({ title: `${it.materialName} 请填写数量或金额`, icon: 'none' }); return }
      const mQty = it.unitPrice ? amt / it.unitPrice : 0
      if (gQty > 0) reqItems.push({ itemId: it.itemId, returnType: 'goods', returnQty: gQty, returnAmount: 0 })
      if (amt > 0) reqItems.push({ itemId: it.itemId, returnType: 'money', returnQty: Math.round(mQty * 100) / 100, returnAmount: amt })
    }
  }
  if (!reqItems.length) { uni.showToast({ title: '请先选择归还方式', icon: 'none' }); return }

  submitting.value = true
  try {
    await doReturn(transferId.value, { action: 'mixed', items: reqItems, remark: remark.value })
    uni.showToast({ title: '提交成功', icon: 'success' })
    loadDetail()
  } catch { uni.showToast({ title: '提交失败', icon: 'none' }) }
  finally { submitting.value = false }
}

function bulkAction(type: 'goods' | 'money') {
  const pending = items.value.filter(it => !isReturned(it))
  if (!pending.length) { uni.showToast({ title: '所有物料已归还', icon: 'none' }); return }
  for (const it of pending) {
    it.mode = type
    it.goodsQty = ''
    it.moneyAmount = ''
    if (type === 'goods') {
      it.goodsQty = remainingQty(it).toFixed(2)
    } else {
      it.moneyAmount = (remainingQty(it) * it.unitPrice).toFixed(2)
    }
  }
}

async function handleConfirmReturn() {
  const res = await new Promise<boolean>(resolve => {
    uni.showModal({ title: '确认还货完成', content: '确认所有物料已全部归还？确认后不可撤销。', success: r => resolve(r.confirm) })
  })
  if (!res) return
  submitting.value = true
  try {
    await confirmReturn(transferId.value)
    uni.showToast({ title: '已确认还货完成', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch { uni.showToast({ title: '操作失败', icon: 'none' }) }
  finally { submitting.value = false }
}
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />

    <template v-else-if="order">
      <!-- 头部信息 -->
      <view class="header">
        <text class="h-title">还货 / 还钱</text>
        <text class="h-sub">{{ order.fromStoreName }} → {{ order.toStoreName }}</text>
        <text class="h-code">{{ order.bizCode || '' }}</text>
      </view>

      <!-- 物料列表 -->
      <view class="section">
        <view class="sec-head">
          <text class="sec-title" style="margin-bottom:0">调拨物料 ({{ items.length }}项)</text>
          <view v-if="!isReview" class="sec-btns">
            <text class="sec-btn" @click="bulkAction('goods')">全部还货</text>
            <text class="sec-btn sec-btn-money" @click="bulkAction('money')">全部还钱</text>
          </view>
        </view>
        <view v-for="it in items" :key="it.itemId" class="item-card" :class="{ done: isReturned(it) }">
          <view class="ic-top">
            <view class="ic-info">
              <text class="ic-name">{{ it.materialName }}</text>
              <text class="ic-meta">{{ it.transferQty }} {{ it.unit }}<text v-if="it.unitPrice" class="ic-price-tag"> · 单价：¥{{ it.unitPrice }}</text></text>
              <text v-if="(it.returnedQty > 0 || it.returnAmount > 0) && !isReturned(it)" class="ic-returned">{{ it.returnAmount > 0 ? `已还 ¥${it.returnAmount || 0}` : `已还 ${it.returnedQty} ${it.unit}` }}</text>
            </view>
            <text v-if="isReturned(it)" class="ic-done-tag">✓ 已还清</text>
          </view>

          <!-- 归还记录(始终展示) -->
          <view v-if="getItemRecords(it.itemId).length" class="ic-records">
            <view v-for="(r, ri) in getItemRecords(it.itemId)" :key="ri" class="icr-item">
              <text class="icr-text">{{ r.returnType === 'goods' ? `还货 ${r.returnQty} ${it.unit}` : `还钱 ¥${r.returnAmount || 0}` }}</text>
              <text class="icr-meta">{{ r.handlerName || '' }} · {{ r.createdAt ? r.createdAt.substring(0,16) : '' }}</text>
            </view>
          </view>

          <!-- 未归还的显示操作区(审查模式不显示) -->
          <view v-if="!isReturned(it) && !isReview" class="ic-actions">
            <view class="ica-row">
              <view class="ica-chip" :class="{ on: it.mode === 'goods' }" @click="setMode(it, 'goods')">仅还货</view>
              <view class="ica-chip ic-m" :class="{ on: it.mode === 'money' }" @click="setMode(it, 'money')">仅还钱</view>
              <view class="ica-chip ic-b" :class="{ on: it.mode === 'both' }" @click="setMode(it, 'both')">还货+还钱</view>
            </view>

            <!-- 仅还货 -->
            <view v-if="it.mode === 'goods'" class="ica-inputs">
              <view class="icai-field">
                <text class="icai-label">数量</text>
                <input class="icai-input" v-model="it.goodsQty" type="digit" placeholder="0" />
                <text class="icai-unit">{{ it.unit }}</text>
              </view>
              <text class="icai-remain">剩余 {{ remainingQty(it).toFixed(2) }} {{ it.unit }}</text>
            </view>

            <!-- 仅还钱 -->
            <view v-if="it.mode === 'money'" class="ica-inputs">
              <view class="icai-field">
                <text class="icai-label">金额</text>
                <view class="icai-amount-row"><text class="icai-symbol">¥</text><input class="icai-input" v-model="it.moneyAmount" type="digit" placeholder="0.00" /></view>
              </view>
              <text class="icai-remain">剩余 {{ remainingQty(it).toFixed(2) }} {{ it.unit }}{{ it.moneyAmount ? ` · ¥${it.moneyAmount}` : '' }}</text>
            </view>

            <!-- 还货+还钱 -->
            <view v-if="it.mode === 'both'" class="ica-inputs">
              <view class="icai-field">
                <text class="icai-label">还货</text>
                <input class="icai-input" v-model="it.goodsQty" type="digit" placeholder="0" />
                <text class="icai-unit">{{ it.unit }}</text>
              </view>
              <view class="icai-field">
                <text class="icai-label">还钱</text>
                <view class="icai-amount-row"><text class="icai-symbol">¥</text><input class="icai-input" v-model="it.moneyAmount" type="digit" placeholder="0.00" /></view>
              </view>
              <text class="icai-remain">剩余 {{ remainingQty(it).toFixed(2) }} {{ it.unit }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 备注 -->
      <view class="section">
        <text class="sec-title">备注</text>
        <text v-if="isReview" class="remark-text">{{ remark || '--' }}</text>
        <textarea v-else class="remark-input" v-model="remark" placeholder="选填" />
      </view>

      <!-- 底部操作栏 -->
      <view class="bottom" v-if="!loading && order">
        <template v-if="isReview && order && order.handoff !== 'returned'">
          <view class="b-btn b-primary" :class="{ disabled: !allReturned }" @click="allReturned ? handleConfirmReturn() : null">
            {{ submitting ? '提交中...' : '确认还货完成' }}
          </view>
        </template>
        <template v-else>
          <view class="b-btn b-primary" :class="{ disabled: !hasAction }" @click="submit">
            {{ submitting ? '提交中...' : '提交保存' }}
          </view>
        </template>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$w:#E58A2D;$d:#E05A47;

.page{min-height:100vh;background:$bg;padding-bottom:240rpx}
.header{background:$p;padding:32rpx;color:#fff}
.h-title{display:block;font-size:36rpx;font-weight:700}
.h-sub{display:block;font-size:26rpx;margin-top:8rpx;opacity:.85}
.h-code{display:block;font-size:22rpx;margin-top:6rpx;opacity:.6;font-family:monospace}

.section{padding:24rpx}
.sec-title{font-size:28rpx;font-weight:700;color:$t1;margin-bottom:16rpx;display:block}
.sec-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:16rpx}
.sec-btns{display:flex;gap:8rpx;flex-shrink:0}
.sec-btn{padding:12rpx 32rpx;border-radius:999rpx;font-size:26rpx;background:$s;color:$p;border:2rpx solid $p}
.sec-btn-money{color:$w;border-color:$w}

.item-card{background:$s;border-radius:16rpx;padding:24rpx;margin-bottom:16rpx;border:2rpx solid $b}
.item-card.done{opacity:.6;background:$ps;border-color:transparent}
.ic-top{display:flex;justify-content:space-between;align-items:center}
.ic-info{flex:1;min-width:0}
.ic-name{display:block;font-size:28rpx;font-weight:600;color:$t1}
.ic-meta{display:block;font-size:24rpx;color:$t2;margin-top:4rpx}
.ic-price-tag{display:inline-block;font-size:20rpx;color:$t3;padding:2rpx 10rpx;background:#FAFBF9;border-radius:6rpx}
.ic-returned{display:block;font-size:24rpx;color:$p;margin-top:4rpx}
.ic-done-tag{font-size:22rpx;color:$p;font-weight:600;padding:6rpx 16rpx;background:$ps;border-radius:999rpx;flex-shrink:0}

.ic-records{margin-top:16rpx;padding-top:16rpx;border-top:1px solid $b}
.icr-item{padding:6rpx 0;display:flex;justify-content:space-between;align-items:center}
.icr-text{font-size:24rpx;color:$t1}
.icr-meta{font-size:22rpx;color:$t3}

.ic-actions{margin-top:16rpx}
.ica-row{display:flex;gap:12rpx;margin-bottom:12rpx}
.ica-chip{padding:12rpx 32rpx;border-radius:999rpx;font-size:26rpx;background:$s;color:$t2;border:2rpx solid $b}
.ica-chip.on{background:$ps;color:$p;border-color:$p}
.ica-chip.ic-m.on{background:#FFF8EE;color:$w;border-color:$w}
.ica-chip.ic-b.on{background:$ps;color:$p;border-color:$p}

.ica-inputs{background:#FAFBF9;border-radius:12rpx;padding:16rpx;display:flex;flex-direction:column;gap:12rpx}
.icai-field{display:flex;align-items:center;gap:8rpx}
.icai-label{font-size:24rpx;color:$t2;width:60rpx;flex-shrink:0}
.icai-input{flex:1;height:64rpx;background:$s;border-radius:8rpx;padding:0 12rpx;font-size:28rpx;text-align:right}
.icai-unit{font-size:24rpx;color:$t2;width:40rpx}
.icai-symbol{font-size:24rpx;color:$t2}
.icai-amount-row{display:flex;align-items:center;gap:4rpx}
.icai-remain{font-size:22rpx;color:$t3;text-align:right}

.remark-input{width:100%;height:80rpx;padding:12rpx 16rpx;border:1px solid $b;border-radius:12rpx;font-size:26rpx;background:#FAFBF9;box-sizing:border-box}
.remark-text{display:block;font-size:26rpx;color:$t1;padding:12rpx 16rpx}

.bottom{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:16rpx 24rpx calc(env(safe-area-inset-bottom) + 16rpx);background:$s;box-shadow:0 -4rpx 20rpx rgba(0,0,0,.06)}
.bottom-row{display:flex;gap:16rpx;margin-bottom:12rpx}
.b-btn{flex:1;height:80rpx;border-radius:999rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.b-outline{border:2rpx solid $b;color:$t1;background:$s}
.b-primary{height:88rpx;border-radius:16rpx;background:$p;color:#fff}.b-primary.disabled{opacity:.5}

</style>
