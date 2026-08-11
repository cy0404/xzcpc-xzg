<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { fetchBusinessReportDetail, fetchCostBreakdown, BusinessReportDetail, DailyItem } from '@/api/business'

const userStore = useUserStore()
const yearMonth = ref('')
const storeId = ref('')
const storeNameParam = ref('')
const detail = ref<BusinessReportDetail | null>(null)
const loading = ref(true)

// 日营收展开
const showDaily = ref(false)
// 成本下钻
const breakdownLoading = ref(false)
const breakdownSheet = ref(false)
const breakdownTitle = ref('')
const breakdownData = ref<any>(null)
// 财报明细公式提示
const activeFormula = ref<string | null>(null)

const reportTitle = computed(() => {
  if (!yearMonth.value) return ''
  const [y, m] = yearMonth.value.split('-')
  return `${y}年${parseInt(m)}月财报`
})

const storeDisplayName = computed(() => {
  return storeNameParam.value || detail.value?.storeName || storeId.value || '--'
})

const hasDaily = computed(() => {
  return detail.value?.dailyItems && detail.value.dailyItems.length > 0
})

onLoad((query: any) => {
  yearMonth.value = query?.yearMonth || ''
  storeId.value = query?.storeId || ''
  storeNameParam.value = decodeURIComponent(query?.storeName || '')

  if (!userStore.token || !userStore.bound) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!yearMonth.value) return
  loadDetail()
})

onShow(() => {
  if (yearMonth.value && detail.value) {
    loadDetail()
  }
})

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await fetchBusinessReportDetail(yearMonth.value, storeId.value || undefined)
  } catch { /* handled */ }
  finally { loading.value = false }
}

function toggleFormula(label: string) {
  activeFormula.value = activeFormula.value === label ? null : label
}

async function openBreakdown(costName: string, group: string) {
  breakdownTitle.value = costName
  breakdownSheet.value = true
  breakdownLoading.value = true
  breakdownData.value = null
  try {
    breakdownData.value = await fetchCostBreakdown(yearMonth.value, storeId.value, group, costName)
  } catch { /* handled */ }
  finally { breakdownLoading.value = false }
}

// --- formatting ---

function fmtMoney(val: number | null | undefined): string {
  if (val == null) return '--'
  const sign = val < 0 ? '-' : ''
  return sign + '¥' + Math.round(Math.abs(val)).toLocaleString()
}

function fmtRatePercent(val: number | null | undefined): string {
  if (val == null) return '--'
  const v = val > 1 ? val : val * 100
  return Number(v).toFixed(2) + '%'
}

function fmtDate(dateStr: string): string {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length >= 3) return parts[1] + '/' + parts[2]
  return dateStr
}

// --- indicator helpers ---

interface IndicatorItem {
  label: string
  value: string
  cls: string
}

const mainIndicators = computed<IndicatorItem[]>(() => {
  const ind = detail.value?.indicators
  if (!ind) return []
  return [
    { label: '营业额', value: fmtMoney(ind.sales), cls: '' },
    { label: '营业实收', value: fmtMoney(ind.actualRevenue), cls: '' },
    { label: '入账率', value: fmtRatePercent(ind.bookingRate), cls: '' },
    { label: '产品毛利率', value: fmtRatePercent(ind.productGrossProfitRate), cls: 'primary' },
    { label: '损耗率', value: fmtRatePercent(ind.lossRate), cls: 'danger' },
    { label: '物料占比', value: fmtRatePercent(ind.materialCostRatio), cls: '' },
    { label: '租金占比', value: fmtRatePercent(ind.rentRatio), cls: '' },
    { label: '人工占比', value: fmtRatePercent(ind.laborRatio), cls: '' },
  ]
})

const expandedDay = ref<string | null>(null)
function toggleDay(dateStr: string) {
  expandedDay.value = expandedDay.value === dateStr ? null : dateStr
}
function getDayChannels(dateStr: string): any[] {
  const d = detail.value?.dailyItems?.find(d => d.statDate === dateStr)
  return d?.channels || []
}
</script>

<template>
  <view class="page">
    <!-- 信息卡片 -->
    <view class="card info-card">
      <text class="info-store">{{ storeDisplayName }}</text>
      <text class="info-title">{{ reportTitle }}</text>
    </view>

    <!-- Loading -->
    <view v-if="loading && !detail">
      <view class="card skeleton-card"><view class="sk-row" v-for="i in 5" :key="i"></view></view>
    </view>

    <template v-if="!loading && detail">
      <!-- 核心经营指标 2×4 网格 -->
      <view class="card">
        <text class="card-title">核心经营指标</text>
        <view class="ind-grid">
          <view v-for="item in mainIndicators" :key="item.label" class="ind-cell">
            <text class="ind-label">{{ item.label }}</text>
            <text class="ind-value" :class="item.cls ? 'v-' + item.cls : ''">{{ item.value }}</text>
          </view>
        </view>
      </view>

      <!-- 财报明细（紧凑列表） -->
      <view class="card">
        <text class="card-title">财报明细</text>
        <view class="detail-row" v-for="d in detail.details" :key="d.label" @click="d.formula ? toggleFormula(d.label) : null">
          <view class="dr-main">
            <text class="dk">{{ d.label }}</text>
            <text class="dv" :class="{ 'dv-primary': d.color === 'primary', 'dv-danger': d.color === 'danger' }">
              {{ d.label.includes('率') ? fmtRatePercent(d.value) : fmtMoney(d.value) }}
            </text>
          </view>
          <view v-if="activeFormula === d.label && d.formula" class="dr-formula">
            <text class="dr-ftext">{{ d.formula }}</text>
          </view>
        </view>
      </view>

      <!-- 日营收（可折叠） -->
      <view class="card" v-if="hasDaily">
        <view class="card-head" @click="showDaily = !showDaily">
          <text class="card-title">日营收明细（{{ detail.dailyItems.length }}天）</text>
          <text class="card-arrow" :class="{ open: showDaily }">›</text>
        </view>
        <view v-if="showDaily" class="daily-list">
          <view class="di-header">
            <text class="di-date">日期</text>
            <text class="di-val">商品销售额</text>
            <text class="di-val">商品实收额</text>
            <text class="di-orders">订单数</text>
            <text class="di-arrow-hd"></text>
          </view>
          <view v-for="d in detail.dailyItems" :key="d.statDate" class="daily-item">
            <view class="di-row" @click="toggleDay(d.statDate)">
              <text class="di-date">{{ fmtDate(d.statDate) }}</text>
              <text class="di-val">{{ fmtMoney(d.gmv) }}</text>
              <text class="di-val">{{ fmtMoney(d.actualRevenue) }}</text>
              <text class="di-val di-orders">{{ d.orderCount }}单</text>
              <text class="di-arrow" :class="{ open: expandedDay === d.statDate }">›</text>
            </view>
            <!-- 渠道拆分 -->
            <view v-if="expandedDay === d.statDate && getDayChannels(d.statDate).length" class="di-channels">
              <view v-for="ch in getDayChannels(d.statDate)" :key="ch.channel" class="ch-row">
                <text class="ch-name">{{ ch.channel }}</text>
                <view class="ch-right">
                  <text class="ch-val">{{ fmtMoney(ch.gmv) }}</text>
                  <text class="ch-val ch-rev">{{ fmtMoney(ch.actualRevenue) }}</text>
                  <text class="ch-orders">{{ ch.orderCount }}单</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 成本结构 -->
      <view class="card" v-if="detail.costStructure && detail.costStructure.length">
        <text class="card-title">成本结构</text>
        <view v-for="c in detail.costStructure" :key="c.name" class="cost-row" @click="openBreakdown(c.name, c.group || 'operation')">
          <view class="cr-left">
            <text class="cr-name">{{ c.name }}</text>
            <view class="cr-bar-bg"><view class="cr-bar" :style="{ width: Math.min(c.ratio || 0, 100) + '%' }"></view></view>
          </view>
          <view class="cr-right">
            <text class="cr-amount">{{ fmtMoney(c.amount) }}</text>
            <text class="cr-ratio">{{ fmtRatePercent(c.ratio) }}</text>
            <text class="cr-arrow">›</text>
          </view>
        </view>
      </view>
    </template>

    <!-- 空状态 -->
    <view v-if="!loading && !detail" class="empty-wrap">
      <text class="empty-text">暂无该月财报数据</text>
    </view>

    <!-- 成本下钻底部抽屉 -->
    <view class="mask" v-if="breakdownSheet" @click="breakdownSheet = false"></view>
    <view class="sheet" :class="{ show: breakdownSheet }">
      <view class="sh-bar"><view class="sh-drag"></view></view>
      <view class="sh-title">{{ breakdownTitle }} — 明细</view>

      <view v-if="breakdownLoading" class="sh-loading"><text>加载中...</text></view>

      <template v-else-if="breakdownData">
        <view v-if="breakdownData.materialDetail && breakdownData.materialDetail.length" class="bd-section">
          <text class="bd-kicker">物料明细</text>
          <view v-for="m in breakdownData.materialDetail" :key="m.code" class="bd-row">
            <view class="bdr-left">
              <text class="bdr-code">{{ m.code }}</text>
              <text class="bdr-name">{{ m.name }}</text>
            </view>
            <view class="bdr-right">
              <text class="bdr-cost">{{ fmtMoney(m.cost) }}</text>
              <text class="bdr-formula">期初{{ m.opening || 0 }}+采购{{ m.purchase || 0 }}-期末{{ m.closing || 0 }}</text>
            </view>
          </view>
        </view>
        <view v-if="breakdownData.materialItems && breakdownData.materialItems.length" class="bd-section">
          <text class="bd-kicker">物料汇总</text>
          <view v-for="m in breakdownData.materialItems" :key="m.label" class="bd-row">
            <text class="bdr-name">{{ m.label }}</text>
            <view class="bdr-right">
              <text class="bdr-cost">{{ fmtMoney(m.amount) }}</text>
              <text class="bdr-formula">期初{{ m.opening || 0 }}+采购{{ m.purchase || 0 }}-期末{{ m.closing || 0 }}</text>
            </view>
          </view>
        </view>
        <view v-if="breakdownData.expenseItems && breakdownData.expenseItems.length" class="bd-section">
          <text class="bd-kicker">费用明细</text>
          <view v-for="e in breakdownData.expenseItems" :key="e.label" class="bd-row">
            <text class="bdr-name">{{ e.label }}</text>
            <text class="bdr-cost">{{ fmtMoney(e.amount) }}</text>
          </view>
        </view>
        <view v-if="!breakdownData.materialDetail?.length && !breakdownData.materialItems?.length && !breakdownData.expenseItems?.length" class="bd-empty">
          <text>暂无明细数据</text>
        </view>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$ps:#E7F4EB;$ss:#FAFBF9;

.page { min-height: 100vh; background: $bg; padding: 24rpx 32rpx 120rpx; }

.card { background: $s; border-radius: 20rpx; padding: 28rpx; border: 2rpx solid $b; margin-bottom: 24rpx; }
.card-title { display: block; font-size: 26rpx; font-weight: 600; color: $t3; margin-bottom: 16rpx; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-arrow { font-size: 36rpx; color: $p; font-weight: 300; transition: transform .2s; }
.card-arrow.open { transform: rotate(90deg); }

/* Info */
.info-card { text-align: center; }
.info-store { font-size: 22rpx; color: $t3; }
.info-title { display: block; margin-top: 6rpx; font-size: 36rpx; font-weight: 600; color: $t1; }

/* Skeleton */
.skeleton-card { display: flex; flex-direction: column; gap: 20rpx; padding: 36rpx 28rpx; }
.sk-row { height: 28rpx; background: linear-gradient(90deg, #E8ECE9 25%, #F0F2F1 50%, #E8ECE9 75%); border-radius: 8rpx; background-size: 200% 100%; animation: shimmer 1.5s infinite; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* 核心指标 2×4 网格 */
.ind-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx; }
.ind-cell { background: $ss; border-radius: 14rpx; padding: 20rpx 16rpx; text-align: center; }
.ind-label { font-size: 22rpx; color: $t3; }
.ind-value { display: block; margin-top: 6rpx; font-size: 28rpx; font-weight: 700; color: $t1; }
.ind-value.v-primary { color: $p; }
.ind-value.v-danger { color: $d; }

/* 财报明细 */
.detail-row { flex-direction: column; padding: 14rpx 0; border-bottom: 2rpx solid #EEF1EF; }
.detail-row:last-child { border: none; }
.detail-row:active { opacity: .7; }
.dr-main { display: flex; justify-content: space-between; align-items: center; }
.dk { font-size: 26rpx; color: $t2; }
.dv { font-size: 26rpx; color: $t1; font-weight: 500; }
.dv-primary { color: $p; }
.dv-danger { color: $d; }
.dr-formula { margin-top: 8rpx; padding: 12rpx 16rpx; background: #F1F8F3; border-radius: 10rpx; }
.dr-ftext { font-size: 22rpx; color: $t2; line-height: 34rpx; white-space: pre-line; }

/* 日营收 */
.daily-list { margin-top: 8rpx; }
.daily-item { border-bottom: 2rpx solid #EEF1EF; padding: 10rpx 0; }
.daily-item:last-child { border: none; }
.di-header { display: flex; align-items: center; gap: 16rpx; padding: 10rpx 0; border-bottom: 2rpx solid $b; margin-bottom: 6rpx; }
.di-header .di-date, .di-header .di-val, .di-header .di-orders { font-size: 22rpx; color: $t3; font-weight: 400; }
.di-arrow-hd { width: 36rpx; flex-shrink: 0; }
.di-row { display: flex; align-items: center; gap: 16rpx; padding: 10rpx 0; }
.di-date { font-size: 24rpx; color: $t3; width: 80rpx; flex-shrink: 0; }
.di-val { font-size: 26rpx; color: $t1; font-weight: 500; flex: 1; text-align: right; }
.di-orders { font-size: 22rpx; color: $t2; flex: 0.5; text-align: right; }
.di-arrow { font-size: 28rpx; color: $p; transition: transform .2s; width: 36rpx; text-align: center; }
.di-arrow.open { transform: rotate(90deg); }
/* 渠道拆分 */
.di-channels { margin: 4rpx 0 10rpx 80rpx; background: $ss; border-radius: 12rpx; padding: 12rpx 16rpx; }
.ch-row { display: flex; align-items: center; justify-content: space-between; padding: 8rpx 0; }
.ch-name { font-size: 22rpx; color: $t2; }
.ch-right { display: flex; gap: 16rpx; align-items: center; }
.ch-val { font-size: 24rpx; color: $t1; }
.ch-rev { color: $p; }
.ch-orders { font-size: 20rpx; color: $t3; }

/* 成本结构 */
.cost-row { display: flex; align-items: center; justify-content: space-between; padding: 18rpx 0; border-bottom: 2rpx solid #EEF1EF; gap: 16rpx; }
.cost-row:last-child { border: none; }
.cr-left { flex: 1; min-width: 0; }
.cr-name { font-size: 26rpx; color: $t1; font-weight: 500; display: block; margin-bottom: 8rpx; }
.cr-bar-bg { height: 8rpx; background: #EEF1EF; border-radius: 4rpx; overflow: hidden; }
.cr-bar { height: 100%; background: $p; border-radius: 4rpx; transition: width .4s; min-width: 8rpx; }
.cr-right { display: flex; flex-direction: column; align-items: flex-end; flex-shrink: 0; }
.cr-amount { font-size: 26rpx; color: $t1; font-weight: 600; }
.cr-ratio { font-size: 22rpx; color: $t3; margin-top: 2rpx; }
.cr-arrow { font-size: 28rpx; color: $p; margin-top: 2rpx; }

/* 空状态 */
.empty-wrap { padding: 120rpx 0; display: flex; align-items: center; justify-content: center; }
.empty-text { font-size: 26rpx; color: $t3; }

/* 成本下钻底部抽屉 */
.mask { position: fixed; inset: 0; background: rgba(31,36,33,.4); z-index: 100; }
.sheet {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 101;
  background: $s; border-radius: 32rpx 32rpx 0 0;
  padding: 0 32rpx; padding-bottom: calc(env(safe-area-inset-bottom) + 32rpx);
  max-height: 70vh; overflow-y: auto;
  transform: translateY(100%); transition: transform .25s ease;
}
.sheet.show { transform: translateY(0); }
.sh-bar { display: flex; justify-content: center; padding: 16rpx 0; }
.sh-drag { width: 56rpx; height: 8rpx; background: #DDE1DE; border-radius: 4rpx; }
.sh-title { font-size: 30rpx; font-weight: 600; color: $t1; padding: 8rpx 0 20rpx; text-align: center; }
.sh-loading { padding: 60rpx 0; text-align: center; color: $t3; font-size: 26rpx; }

.bd-section { margin-bottom: 20rpx; }
.bd-kicker { display: block; font-size: 22rpx; color: $t3; margin-bottom: 10rpx; padding-top: 8rpx; }
.bd-row { display: flex; justify-content: space-between; align-items: flex-start; padding: 14rpx 0; border-bottom: 2rpx solid #EEF1EF; gap: 16rpx; }
.bd-row:last-child { border: none; }
.bdr-left { flex: 1; min-width: 0; }
.bdr-code { display: block; font-size: 20rpx; color: $t3; }
.bdr-name { font-size: 26rpx; color: $t1; display: block; }
.bdr-right { text-align: right; flex-shrink: 0; }
.bdr-cost { font-size: 26rpx; font-weight: 600; color: $t1; display: block; }
.bdr-formula { display: block; font-size: 20rpx; color: $t3; margin-top: 2rpx; }
.bd-empty { padding: 60rpx 0; text-align: center; color: $t3; font-size: 26rpx; }
</style>
