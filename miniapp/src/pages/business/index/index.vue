<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { fetchMyStores, switchStore } from '@/api/auth'
import { fetchBusinessOverview, fetchBusinessTrend, fetchBusinessReports, BusinessOverview, BusinessTrend, BusinessReportSummary } from '@/api/business'

const userStore = useUserStore()

interface StoreOption { storeId: string; storeName: string }

const scope = ref<'all' | string>('all')
const myStores = ref<StoreOption[]>([])
const period = ref('30')
const selectedMetric = ref('sales')
const overview = ref<BusinessOverview | null>(null)
const trend = ref<BusinessTrend | null>(null)
const reports = ref<BusinessReportSummary[]>([])
const loading = ref(false)

const isOwner = computed(() => userStore.role === 'owner' || userStore.role === '老板')
const isStaff = computed(() => userStore.role === 'staff' || userStore.role === '店员')
const isSingle = computed(() => myStores.value.length <= 1)
const scopeAll = computed(() => scope.value === 'all')

const scopeLabel = computed(() => {
  if (scope.value === 'all') return '全部门店'
  const s = myStores.value.find(s => s.storeId === scope.value)
  return s?.storeName || userStore.storeName || '当前门店'
})

const periodOptions = [
  { value: '7', label: '近 7 天' },
  { value: '30', label: '近 30 天' },
  { value: 'month', label: '本月' },
]

const metricOptions = [
  { value: 'sales', label: '销售额' },
  { value: 'expense', label: '支出' },
  { value: 'loss', label: '日常报损' },
]

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
  if (userStore.role === 'staff' || userStore.role === '店员') return

  // 确保门店列表已加载
  if (myStores.value.length === 0) {
    try {
      const data = await fetchMyStores()
      myStores.value = (data || []).map((s: any) => ({ storeId: s.storeId || s.id, storeName: s.storeName || s.mendianmingcheng }))
    } catch { /* empty */ }
  }

  const savedScope = userStore.selectedScope
  const prevScope = scope.value
  scope.value = myStores.value.length === 1
    ? myStores.value[0].storeId
    : (savedScope && myStores.value.some(s => s.storeId === savedScope) ? savedScope : 'all')
  if (prevScope === scope.value) {
    await loadData()
  }
})

function setScope(val: 'all' | string) {
  scope.value = val
  userStore.selectedScope = val
}

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
    } catch { /* ignore */ }
  }
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const [ov, tr, rp] = await Promise.all([
      fetchBusinessOverview(period.value, scope.value),
      fetchBusinessTrend('7', selectedMetric.value, scope.value),
      fetchBusinessReports(scope.value),
    ])
    overview.value = ov
    trend.value = tr
    reports.value = rp || []
  } catch { /* handled */ }
  finally { loading.value = false }
}

function setPeriod(val: string) {
  period.value = val
  loadData()
}

async function setMetric(val: string) {
  selectedMetric.value = val
  try {
    trend.value = await fetchBusinessTrend('7', val, scope.value)
  } catch { /* handled */ }
}

function openReportDetail(item: BusinessReportSummary) {
  uni.navigateTo({ url: `/pages/business/report-detail/index?yearMonth=${item.yearMonth}&storeId=${item.storeId || ''}&storeName=${encodeURIComponent(item.storeName || '')}` })
}

// formatting

function fmtMoney(val: number | null | undefined): string {
  if (val == null) return '--'
  return '¥' + Math.round(val).toLocaleString()
}

function fmtChangeRate(item: { changeRate?: string; trend?: string } | null | undefined): string {
  if (!item || !item.changeRate || item.changeRate === '--') return '--'
  return item.changeRate
}

function changeClass(item: { trend?: string } | null | undefined): string {
  if (!item) return ''
  return item.trend === 'up' ? 'c-up' : item.trend === 'down' ? 'c-down' : ''
}

function maxTrendValue(): number {
  if (!trend.value?.points?.length) return 1
  return Math.max(...trend.value.points.map(p => Number(p.value) || 0), 1)
}

function barHeight(val: number, max: number): string {
  if (max <= 0) return '24'
  return Math.max(24, Math.round((val / max) * 92)) + ''
}

function fmtChartValue(val: number): string {
  if (val == null) return '--'
  if (val >= 10000) return (val / 10000).toFixed(1) + '万'
  if (val >= 1000) return (val / 1000).toFixed(1) + 'k'
  return '¥' + val.toFixed(1)
}

function reportTitle(yearMonth: string): string {
  const [y, m] = yearMonth.split('-')
  return `${y}年${parseInt(m)}月财报`
}
</script>

<template>
  <view class="page">
    <!-- 店员无权限 -->
    <view v-if="isStaff" class="no-perm">
      <image class="np-icon" src="/static/icons/LoadingImage.png" mode="aspectFit" />
      <text class="np-title">暂无权限查看</text>
    </view>

    <!-- 门店胶囊切换（参考首页） -->
    <template v-if="!isStaff">
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

    <!-- 时间切换 + KPI -->
    <view class="period-card">
      <view class="period-row">
        <view
          v-for="p in periodOptions" :key="p.value"
          class="chip" :class="{ on: period === p.value }"
          @click="setPeriod(p.value)"
        >{{ p.label }}</view>
      </view>
      <view class="kpi-grid">
        <view class="kpi-item">
          <text class="kpi-label">销售额</text>
          <text class="kpi-value">{{ overview ? fmtMoney(overview.sales.value) : '--' }}</text>
          <text class="kpi-change" :class="changeClass(overview?.sales)">{{ fmtChangeRate(overview?.sales) === '--' ? '--' : '较上期 ' + fmtChangeRate(overview?.sales) }}</text>
        </view>
        <view class="kpi-item">
          <text class="kpi-label">支出</text>
          <text class="kpi-value">{{ overview ? fmtMoney(overview.expense.value) : '--' }}</text>
          <text class="kpi-change" :class="changeClass(overview?.expense)">{{ fmtChangeRate(overview?.expense) === '--' ? '--' : '较上期 ' + fmtChangeRate(overview?.expense) }}</text>
        </view>
        <view class="kpi-item">
          <text class="kpi-label">日常报损</text>
          <text class="kpi-value">{{ overview ? fmtMoney(overview.loss.value) : '--' }}</text>
          <text class="kpi-change" :class="changeClass(overview?.loss)">{{ fmtChangeRate(overview?.loss) === '--' ? '--' : '报损金额' }}</text>
        </view>
      </view>
    </view>

    <!-- 近一周趋势图 -->
    <view class="chart-card">
      <text class="chart-title">近一周趋势图</text>
      <view class="metric-row">
        <view
          v-for="m in metricOptions" :key="m.value"
          class="chip chip-sm" :class="{ on: selectedMetric === m.value }"
          @click="setMetric(m.value)"
        >{{ m.label }}</view>
      </view>
      <view class="chart-area">
        <view v-if="trend?.points?.length" class="chart-bars">
          <view v-for="(p, i) in trend.points" :key="i" class="bar-col">
            <text class="bar-val">{{ fmtChartValue(Number(p.value)) }}</text>
            <view class="bar-wrap">
              <view class="bar" :style="{ height: barHeight(Number(p.value), maxTrendValue()) + 'px' }"></view>
            </view>
            <text class="bar-date">{{ p.date }}</text>
          </view>
        </view>
        <view v-else class="chart-empty">
          <text class="chart-empty-text">暂无趋势数据</text>
        </view>
      </view>
      <text v-if="trend?.caption" class="chart-caption">{{ trend.caption }}</text>
    </view>

    </template>

    <!-- 财报列表（仅老板） -->
    <view v-if="isOwner" class="section">
      <view class="sec-head">
        <view>
          <text class="sec-title">财报列表</text>
          <text class="sec-sub">每月每店一期财报</text>
        </view>
      </view>
      <view class="report-list">
        <view
          v-for="r in reports" :key="r.yearMonth + (r.storeId || '')"
          class="report-card"
          @click="openReportDetail(r)"
        >
          <view class="rc-body">
            <text class="rc-title">{{ reportTitle(r.yearMonth) }}</text>
            <text class="rc-store">{{ r.storeName }}</text>
          </view>
          <text class="rc-arrow">›</text>
        </view>
      </view>
      <view v-if="reports.length === 0 && !loading" class="report-empty">
        <text class="empty-text">暂无财报数据</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg: #F7F8F6;$s: #FFFFFF;$p: #2F8F57;$t1: #1F2421;$t2: #66706A;$t3: #98A19C;$b: #E8ECE9;$warn: #E58A2D;$ss: #FAFBF9;

.page { min-height: 100vh; background: $bg; padding-bottom: 120rpx; }

/* 无权限 */
.no-perm { display: flex; flex-direction: column; align-items: center; justify-content: center; padding-top: 200rpx; }
.np-icon { width: 320rpx; height: 320rpx; margin-bottom: 32rpx; }
.np-title { font-size: 30rpx; font-weight: 600; color: $t3; }

/* 门店胶囊（参考首页） */
.scope-card {
  margin: 24rpx 24rpx 0;
  background: $s; border: 1rpx solid $b; border-radius: 16rpx;
  padding: 24rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,.06);
}
.sc-top { display: flex; flex-direction: column; gap: 4rpx; }
.sc-kicker { font-size: 22rpx; color: $t3; }
.sc-label { font-size: 32rpx; font-weight: 700; color: $t1; }
.sc-scroll { margin-top: 20rpx; white-space: nowrap; }
.sc-row { display: inline-flex; gap: 12rpx; }
.sc-chip {
  display: inline-flex; align-items: center; justify-content: center;
  min-height: 64rpx; padding: 0 28rpx; border-radius: 999rpx;
  font-size: 26rpx; font-weight: 500; white-space: nowrap;
  border: 1.5px solid $b; color: $t2; background: $s; transition: all .16s;
}
.sc-chip.on { background: $p; color: #fff; border-color: $p; }

/* 时间切换 + KPI */
.period-card {
  margin: 24rpx 24rpx 0;
  background: $s; border: 1rpx solid $b; border-radius: 16rpx;
  padding: 24rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,.06);
}
.period-row { display: flex; gap: 12rpx; }

.chip-sm { min-height: 52rpx; font-size: 24rpx; }

.chip {
  flex: 1; min-height: 52rpx; display: flex; align-items: center; justify-content: center;
  padding: 0 20rpx; border: 1.5px solid $b; border-radius: 999rpx; font-size: 24rpx; font-weight: 500;
  color: $t2; background: $s; transition: all .16s;
}
.chip.on { background: $p; color: #fff; border-color: $p; }

.kpi-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12rpx; margin-top: 20rpx; }
.kpi-item { background: $ss; border-radius: 16rpx; padding: 20rpx 12rpx; text-align: center; }
.kpi-label { font-size: 22rpx; color: $t3; }
.kpi-value { display: block; margin-top: 6rpx; font-size: 30rpx; font-weight: 700; color: $t1; }
.kpi-change { display: block; margin-top: 4rpx; font-size: 20rpx; color: $t3; }
.kpi-change.c-up { color: $p; }
.kpi-change.c-down { color: $warn; }

/* 近一周趋势图 */
.metric-row { display: flex; gap: 12rpx; margin-top: 24rpx; }
.chart-card {
  margin: 24rpx 24rpx 0;
  background: $s; border: 1rpx solid $b; border-radius: 16rpx;
  padding: 28rpx 24rpx; box-shadow: 0 4rpx 16rpx rgba(31,36,33,.06);
}
.chart-title { font-size: 32rpx; font-weight: 600; color: $t1; }
.chart-area { margin-top: 24rpx; background: $ss; border-radius: 12rpx; padding: 28rpx 12rpx 20rpx; }
.chart-bars { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4rpx; }
.bar-col { display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.bar-val { font-size: 18rpx; font-weight: 600; color: $t1; }
.bar-wrap { height: 180rpx; display: flex; align-items: flex-end; justify-content: center; width: 100%; }
.bar {
  width: 100%; max-width: 48rpx; min-height: 24rpx;
  border-radius: 999rpx 999rpx 6rpx 6rpx;
  background: linear-gradient(180deg, #62B87B 0%, $p 100%);
}
.bar-date { font-size: 18rpx; color: $t3; }
.chart-empty { padding: 60rpx 0; display: flex; align-items: center; justify-content: center; }
.chart-empty-text { font-size: 26rpx; color: $t3; }
.chart-caption { display: block; margin-top: 20rpx; font-size: 24rpx; color: $t2; }

/* 财报列表 */
.section { margin: 24rpx 24rpx 0; }
.sec-head { display: flex; align-items: center; justify-content: space-between; gap: 24rpx; margin-bottom: 20rpx; }
.sec-title { font-size: 32rpx; font-weight: 600; color: $t1; }
.sec-sub { display: block; margin-top: 4rpx; font-size: 22rpx; color: $t3; }
.report-list { display: flex; flex-direction: column; gap: 16rpx; }
.report-card {
  display: flex; align-items: center; justify-content: space-between;
  background: $s; border: 1rpx solid $b; border-radius: 16rpx; padding: 24rpx; gap: 24rpx;
}
.rc-body { flex: 1; min-width: 0; }
.rc-title { font-size: 30rpx; font-weight: 600; color: $t1; display: block; }
.rc-store { font-size: 22rpx; color: $t3; display: block; margin-top: 4rpx; }
.rc-arrow { font-size: 36rpx; color: $p; font-weight: 300; flex-shrink: 0; }
.report-empty { padding: 60rpx 0; display: flex; align-items: center; justify-content: center; }
.empty-text { font-size: 26rpx; color: $t3; }
</style>
