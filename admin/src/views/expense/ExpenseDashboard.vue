<template>
  <div class="expense-page">
    <ExpenseModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">支出统计看板</h1>
        <p class="page-subtitle">汇总支出趋势、门店对比和类型分布</p>
      </div>
    </div>

    <a-card class="filter-card" :bordered="false" style="margin-bottom:5px">
      <a-row :gutter="[16, 12]">
        <a-col :xs="12" :md="4">
          <div class="filter-label">督导</div>
          <a-select v-model:value="supervisorName" placeholder="全部督导" style="width:100%" allow-clear :options="supervisorOptions" @change="fetchDashboard" />
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">日期范围</div>
          <a-range-picker v-model:value="dateRange" style="width:100%" @change="fetchDashboard" />
        </a-col>
      </a-row>
    </a-card>

    <a-row :gutter="[16, 16]" class="metric-row">
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">总支出金额</div>
          <div class="metric-value">{{ formatMoney(summary.totalAmount) }}</div>
          <div class="metric-note">{{ summary.totalChange }}</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">单店平均支出</div>
          <div class="metric-value">{{ formatMoney(summary.avgStoreAmount) }}</div>
          <div class="metric-note">{{ summary.storeCount }} 家门店参与统计</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">最高支出门店</div>
          <div class="metric-value">{{ formatMoney(summary.topStoreAmount) }}</div>
          <div class="metric-note">{{ summary.topStoreName }}</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">凭证完整率</div>
          <div class="metric-value">{{ summary.voucherRate }}%</div>
          <div class="metric-note">{{ summary.voucherChange }}</div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="14">
        <a-card :bordered="false" style="height:100%">
          <template #title>
            <div style="display:flex;align-items:center;gap:12px">
              <span>按门店对比</span>
              <a-radio-group v-model:value="storeRankDim" size="small" button-style="solid">
                <a-radio-button value="amount">金额</a-radio-button>
                <a-radio-button value="count">次数</a-radio-button>
              </a-radio-group>
              <div style="flex:1" />
              <a-button size="small" type="text" @click="storeRankAsc = !storeRankAsc">
                {{ storeRankAsc ? '↑ 升序' : '↓ 降序' }}
              </a-button>
            </div>
          </template>
          <div class="bar-list">
            <div v-for="item in storeRankView" :key="item.name" class="bar-row">
              <span class="bar-label">{{ item.name }}{{ item.supervisorName ? ' (' + item.supervisorName + ')' : '' }}</span>
              <span class="bar-track">
                <span class="bar-fill" :style="{ width: `${item.percent}%` }" />
              </span>
              <strong>{{ storeRankDim === 'amount' ? formatMoney(item.amount) : `${item.count} 次` }}</strong>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="10">
        <a-card title="按支出类型分布" :bordered="false" style="height:100%">
          <div class="donut-wrap">
            <div class="donut" :style="donutStyle" />
            <ul class="legend">
              <li v-for="item in typeDistribution" :key="item.name">
                <span>{{ item.name }}</span>
                <strong>{{ item.percent }}%</strong>
              </li>
            </ul>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="月度趋势" :bordered="false" class="trend-card">
      <div class="trend-chart">
        <div v-for="item in monthlyTrend" :key="item.month" class="trend-item">
          <span class="trend-bar" :style="{ height: `${item.percent}%` }" />
          <span class="trend-month">{{ item.month }}</span>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ExpenseModuleTabs from '../../components/ExpenseModuleTabs.vue'
import { getExpenseDashboard } from '../../api/expense'
import { getSupervisorOptions } from '../../api/supervisor'

import dayjs from 'dayjs'

const dateRange = ref<any>([dayjs().startOf('month'), dayjs()])
const supervisorName = ref('')
const supervisorOptions = ref<{ label: string; value: string }[]>([])
const storeRankDim = ref('amount')
const storeRankAsc = ref(false)

const summary = ref({
  totalAmount: 0,
  totalChange: '',
  avgStoreAmount: 0,
  storeCount: 0,
  topStoreName: '--',
  topStoreAmount: 0,
  voucherRate: 0,
  voucherChange: '',
})

interface StoreRankItem { name: string; amount: number; count: number; pctAmount: number; pctCount: number }
const storeRankingRaw = ref<StoreRankItem[]>([])
const storeRankView = computed(() => {
  const dim = storeRankDim.value
  const list = storeRankingRaw.value.map(item => ({
    ...item,
    percent: dim === 'amount' ? item.pctAmount : item.pctCount,
  }))
  const asc = storeRankAsc.value
  const key = dim === 'amount' ? 'amount' : 'count'
  return list.sort((a: any, b: any) => asc ? a[key] - b[key] : b[key] - a[key])
})

const typeDistribution = ref<Array<{ name: string; percent: number }>>([])

const monthlyTrendRaw = ref<Array<{ month: string; amount: number }>>([])
const monthlyTrend = computed(() => {
  if (!monthlyTrendRaw.value.length) return []
  const max = Math.max(...monthlyTrendRaw.value.map((item) => item.amount), 1)
  return monthlyTrendRaw.value.map((item) => ({ ...item, percent: Math.round((item.amount / max) * 100) }))
})

const donutColors = ['#2F8F57', '#5AAA7A', '#85C59E', '#A8D5BA', '#C5E5D2', '#7AA98D', '#D7B36A', '#E8C98B', '#CFD6D1', '#B8C5BB']
const donutStyle = computed(() => {
  const items = typeDistribution.value
  if (!items.length) return { background: '#E8ECE9' }
  let acc = 0
  const segments = items.map((item: any, i: number) => {
    const start = acc
    acc += item.percent
    return `${donutColors[i % donutColors.length]} ${start}% ${acc}%`
  })
  return { background: `conic-gradient(${segments.join(',')})` }
})

function formatMoney(value: any) {
  return `¥${Number(value || 0).toLocaleString()}`
}

async function fetchDashboard() {
  try {
    const params: any = {}
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dayjs(dateRange.value[0]).format('YYYY-MM-DD')
      params.endDate = dayjs(dateRange.value[1]).format('YYYY-MM-DD')
    }
    if (supervisorName.value) params.supervisorName = supervisorName.value
    const res = (await getExpenseDashboard(params)) as any
    const data = res.data
    if (!data) return
    summary.value = data.summary || summary.value
    if (data.storeRanking) storeRankingRaw.value = data.storeRanking
    typeDistribution.value = data.typeDistribution || typeDistribution.value
    if (data.monthlyTrend) monthlyTrendRaw.value = data.monthlyTrend
  } catch { /* */ }
}

async function fetchSupervisors() {
  try { const res: any = await getSupervisorOptions(); supervisorOptions.value = res.data || [] } catch { /* */ }
}

onMounted(() => { fetchSupervisors(); fetchDashboard() })
</script>

<style scoped>
.expense-page {
  max-width: 1280px;
}

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #111827;
}

.page-subtitle {
  margin: 6px 0 0;
  color: #6b7280;
}

.metric-row {
  margin-bottom: 16px;
}

.metric-card {
  min-height: 116px;
  padding: 18px;
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--card-shadow);
}

.metric-label {
  color: #6b7280;
  font-size: 13px;
}

.metric-value {
  margin-top: 8px;
  color: #111827;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.metric-note {
  margin-top: 6px;
  color: #6b7280;
  font-size: 12px;
}

.bar-list {
  display: grid;
  gap: 14px;
  overflow-y: auto;
  padding-right: 8px;
  max-height: 340px;
}

.bar-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) 96px;
  align-items: center;
  gap: 12px;
}

.bar-label {
  color: #374151;
}

.bar-track {
  height: 10px;
  border-radius: 999px;
  background: #eef1f3;
  overflow: hidden;
}

.bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--primary);
}

.donut-wrap {
  display: flex;
  align-items: center;
  gap: 20px;
}

.donut {
  width: 100px;
  height: 100px;
  flex-shrink: 0;
  border-radius: 50%;
}

.donut {
  width: 120px;
  height: 120px;
  border-radius: 50%;
}

.legend {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.legend li {
  display: flex;
  justify-content: space-between;
  color: #4b5563;
  font-size: 13px;
}

.trend-card {
  margin-top: 16px;
}

.trend-chart {
  display: flex;
  align-items: end;
  justify-content: space-around;
  height: 240px;
  border-bottom: 1px solid #e5e7eb;
}

.trend-item {
  display: flex;
  height: 100%;
  align-items: end;
  gap: 8px;
}

.trend-bar {
  width: 28px;
  min-height: 20px;
  border-radius: 6px 6px 0 0;
  background: var(--primary);
}

.trend-month {
  margin-bottom: -24px;
  color: #6b7280;
  font-size: 12px;
}
</style>
