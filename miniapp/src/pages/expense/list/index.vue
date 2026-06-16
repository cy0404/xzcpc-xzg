<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchExpenses, fetchExpenseTypes, type ExpenseRecord, type ExpenseType } from '@/api/expense'
import { useUserStore } from '@/store/user'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'
import StoreSwitcher from '@/components/StoreSwitcher.vue'

const userStore = useUserStore()
const switcherRef = ref<InstanceType<typeof StoreSwitcher> | null>(null)
const loading = ref(true)
const records = ref<ExpenseRecord[]>([])
const types = ref<ExpenseType[]>([])
const selectedTypeId = ref('')
const total = ref(0)
const totalCount = ref(0)

const totalAmount = ref(0) // 始终是全部类型的总计，不随筛选变化

const groupedRecords = computed(() => {
  const groups: { date: string; items: ExpenseRecord[] }[] = []
  for (const r of records.value) {
    const d = r.occurredDate || ''
    let g = groups[groups.length - 1]
    if (!g || g.date !== d) { g = { date: d, items: [] }; groups.push(g) }
    g.items.push(r)
  }
  return groups
})

onLoad(init)
onShow(() => { if (!loading.value) { loadRecords(true); loadTotal() } })
onPullDownRefresh(async () => { await loadRecords(true); uni.stopPullDownRefresh() })

async function init() { loading.value = true; try { await Promise.all([loadTypes(), loadRecords(true), loadTotal()]) } finally { loading.value = false } }
async function loadTypes() { types.value = await fetchExpenseTypes() }
async function loadRecords(reset = false) {
  const data = await fetchExpenses({ typeId: selectedTypeId.value, pageNum: 1, pageSize: 100 })
  total.value = data.total || 0; records.value = data.records || []
}
async function loadTotal() {
  const data = await fetchExpenses({ pageNum: 1, pageSize: 999 })
  const all = data.records || []
  totalAmount.value = all.reduce((s, i) => s + Number(i.amount || 0), 0)
  totalCount.value = all.length
}
function selectType(id: string) { selectedTypeId.value = id; loading.value = true; loadRecords(true).finally(() => loading.value = false) }
function goCreate() { uni.navigateTo({ url: '/pages/expense/form/index' }) }
function goDetail(item: ExpenseRecord) { uni.navigateTo({ url: `/pages/expense/detail/index?expenseId=${item.expenseId}` }) }
function fmtMoney(n: number) { return Number(n || 0).toFixed(2) }
function voucherLabel(item: ExpenseRecord) { return item.voucherUrl ? '已上传凭证' : '未上传凭证' }
</script>

<template>
  <view class="page">
    <!-- 汇总卡片 -->
    <view class="sum-card">
      <view class="sum-top">
        <view><text class="sum-label">当前门店</text><view class="store-row"><text class="sum-store">{{ userStore.storeName }}</text><text v-if="userStore.storeCount > 1" class="switch-link" @click="switcherRef?.open()">切换</text></view></view>
        <text class="sum-month">📅 本月</text>
      </view>
      <text class="sum-amount">¥{{ fmtMoney(totalAmount) }}</text>
      <text class="sum-count">共 {{ totalCount }} 笔记录</text>
    </view>

    <!-- 筛选 -->
    <scroll-view scroll-x class="filter-scroll">
      <view class="filter-row">
        <view class="chip" :class="{on:!selectedTypeId}" @click="selectType('')">全部类型</view>
        <view v-for="t in types" :key="t.typeId" class="chip" :class="{on:selectedTypeId===t.typeId}" @click="selectType(t.typeId)">{{ t.name }}</view>
      </view>
    </scroll-view>

    <Skeleton v-if="loading" :rows="4" />
    <template v-else-if="records.length">
      <view v-for="g in groupedRecords" :key="g.date" class="group">
        <text class="group-label">{{ g.date }}</text>
        <view v-for="r in g.items" :key="r.expenseId" class="exp-card" @click="goDetail(r)">
          <view class="ec-icon">{{ (r.typeName||'支')[0] }}</view>
          <view class="ec-info">
            <view class="ec-top"><text class="ec-name">{{ r.itemName || r.typeName }}</text><text class="ec-amount">¥{{ fmtMoney(r.amount) }}</text></view>
            <text class="ec-desc">{{ r.remark || '--' }}</text>
            <view class="ec-meta">
              <text>{{ r.handlerName }} · {{ r.occurredDate }}</text>
              <text class="ec-voucher" :class="{has:r.voucherUrl}">{{ voucherLabel(r) }}</text>
            </view>
          </view>
          <text class="ec-arrow">›</text>
        </view>
      </view>
    </template>
    <EmptyState v-else text="暂无支出记录" />

    <view class="fab"><view class="fab-btn" @click="goCreate">＋ 新增支出</view></view>

    <StoreSwitcher ref="switcherRef" @switched="loadRecords(true); loadTotal()" />
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}
.sum-card{background:$s;border-radius:24rpx;padding:32rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:24rpx}
.sum-top{display:flex;justify-content:space-between;gap:16rpx}.sum-label{font-size:24rpx;color:$t2}.store-row{display:flex;align-items:baseline;gap:12rpx}.sum-store{display:block;margin-top:6rpx;font-size:34rpx;font-weight:700;color:$t1}.switch-link{font-size:24rpx;color:$p;padding:4rpx 0}.sum-month{padding:8rpx 20rpx;border-radius:999rpx;background:#FAFBF9;font-size:24rpx;color:$t2}
.sum-amount{display:block;margin-top:20rpx;font-size:56rpx;font-weight:800;color:$t1}.sum-count{display:block;margin-top:8rpx;font-size:24rpx;color:$t2}
.filter-scroll{white-space:nowrap;margin:0 -32rpx 20rpx}.filter-row{display:flex;gap:16rpx;padding:0 32rpx}.chip{padding:14rpx 28rpx;border-radius:999rpx;font-size:26rpx;background:$s;color:$t2;flex-shrink:0}.chip.on{background:$p;color:#fff}
.group{margin-bottom:24rpx}.group-label{display:block;font-size:24rpx;font-weight:600;color:$t3;margin-bottom:12rpx}
.exp-card{display:flex;align-items:center;gap:20rpx;padding:24rpx 28rpx;background:$s;border-radius:20rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:12rpx}
.ec-icon{width:80rpx;height:80rpx;border-radius:50%;background:$ps;color:$p;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700;flex-shrink:0}
.ec-info{flex:1;min-width:0}.ec-top{display:flex;justify-content:space-between;gap:12rpx}.ec-name{font-size:28rpx;font-weight:600;color:$t1}.ec-amount{font-size:28rpx;font-weight:800;color:$t1}
.ec-desc{display:block;margin-top:6rpx;font-size:24rpx;color:$t2}.ec-meta{display:flex;align-items:center;gap:12rpx;margin-top:10rpx;font-size:22rpx;color:$t3}
.ec-voucher{padding:2rpx 12rpx;border-radius:999rpx;font-size:20rpx;color:$t3}.ec-voucher.has{background:$ps;color:$p}.ec-arrow{font-size:36rpx;color:#8C9691}
.fab{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:linear-gradient(to top,#fff,transparent)}
.fab-btn{width:100%;height:96rpx;border-radius:999rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700;box-shadow:0 8rpx 24rpx rgba(47,143,87,.3)}
</style>
