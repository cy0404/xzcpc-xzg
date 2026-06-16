<template>
  <div class="expense-page">
    <ExpenseModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">支出统计看板</h1>
        <p class="page-subtitle">汇总支出趋势、门店对比和类型分布</p>
      </div>
      <a-segmented v-model:value="range" :options="rangeOptions" @change="fetchDashboard" />
    </div>

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
        <a-card title="按门店对比" :bordered="false">
          <div class="bar-list">
            <div v-for="item in storeRanking" :key="item.name" class="bar-row">
              <span class="bar-label">{{ item.name }}</span>
              <span class="bar-track">
                <span class="bar-fill" :style="{ width: `${item.percent}%` }" />
              </span>
              <strong>{{ formatMoney(item.amount) }}</strong>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="10">
        <a-card title="按支出类型分布" :bordered="false">
          <div class="donut-wrap">
            <div class="donut" />
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

const range = ref('month')
const rangeOptions = [
  { label: '本月', value: 'month' },
  { label: '近3月', value: 'quarter' },
  { label: '近半年', value: 'halfYear' },
]

const summary = ref({
  totalAmount: 128640,
  totalChange: '环比下降 4.8%',
  avgStoreAmount: 16080,
  storeCount: 8,
  topStoreName: '南山万象店',
  topStoreAmount: 24600,
  voucherRate: 96,
  voucherChange: '较上月提升 3%',
})

const storeRanking = ref([
  { name: '南山万象店', amount: 24600, percent: 100 },
  { name: '宝安壹方城店', amount: 20180, percent: 82 },
  { name: '福田中心城店', amount: 17420, percent: 71 },
  { name: '罗湖万象城店', amount: 15760, percent: 64 },
  { name: '龙岗星河店', amount: 12540, percent: 51 },
])

const typeDistribution = ref([
  { name: '物料采购', percent: 44 },
  { name: '设备维护', percent: 26 },
  { name: '门店杂费', percent: 16 },
  { name: '其他', percent: 14 },
])

const monthlyTrend = computed(() => {
  const values = [
    { month: '10月', amount: 98000 },
    { month: '11月', amount: 106000 },
    { month: '12月', amount: 118000 },
    { month: '1月', amount: 110000 },
    { month: '2月', amount: 136000 },
    { month: '3月', amount: 132000 },
    { month: '4月', amount: 156000 },
  ]
  const max = Math.max(...values.map((item) => item.amount))
  return values.map((item) => ({ ...item, percent: Math.round((item.amount / max) * 100) }))
})

function formatMoney(value: number) {
  return `¥${Number(value || 0).toLocaleString()}`
}

async function fetchDashboard() {
  try {
    const res = (await getExpenseDashboard({ range: range.value })) as any
    const data = res.data
    if (!data) return
    summary.value = data.summary || summary.value
    storeRanking.value = data.storeRanking || storeRanking.value
    typeDistribution.value = data.typeDistribution || typeDistribution.value
  } catch {
    // 接口未上线时使用页面内样例数据，便于先完成总部端页面联调。
  }
}

onMounted(fetchDashboard)
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
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
}

.donut {
  width: 138px;
  height: 138px;
  border-radius: 50%;
  background: conic-gradient(var(--primary) 0 44%, #7aa98d 44% 70%, #d7b36a 70% 86%, #cfd6d1 86% 100%);
}

.legend {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.legend li {
  display: flex;
  justify-content: space-between;
  color: #4b5563;
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
