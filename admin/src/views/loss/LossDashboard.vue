<template>
  <div class="loss-page">
    <LossModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">报损统计看板</h1>
        <p class="page-subtitle">汇总报损次数、门店对比、类型分布和月度趋势</p>
      </div>
    </div>

    <a-card class="filter-card" :bordered="false" style="margin-bottom:16px">
      <a-row :gutter="[16, 12]">
        <a-col :xs="12" :md="4">
          <div class="filter-label">督导</div>
          <a-select v-model:value="supervisorName" placeholder="全部督导" style="width:100%" allow-clear :options="supervisorOptions" @change="fetchDashboard" />
        </a-col>
        <a-col :xs="12" :md="4">
          <div class="filter-label">报损类型</div>
          <a-select v-model:value="lossType" placeholder="全部" style="width:100%" allow-clear @change="fetchDashboard">
            <a-select-option value="daily">日常报损</a-select-option>
            <a-select-option value="arrival">到货验收</a-select-option>
          </a-select>
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
          <div class="metric-label">总报损次数</div>
          <div class="metric-value">{{ summary.totalCount }}</div>
          <div class="metric-note">{{ summary.storeCount }} 家门店</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">日常报损</div>
          <div class="metric-value">{{ summary.dailyCount }}</div>
          <div class="metric-note">过期、破损、制作损耗</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">到货验收</div>
          <div class="metric-value">{{ summary.arrivalCount }}</div>
          <div class="metric-note">企迈到货问题报损</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">报损类型占比</div>
          <div class="metric-value">{{ dailyPct }}% / {{ arrivalPct }}%</div>
          <div class="metric-note">日常 / 到货</div>
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
                <a-radio-button value="count">次数</a-radio-button>
                <a-radio-button value="weight">重量</a-radio-button>
              </a-radio-group>
            </div>
          </template>
          <div class="bar-list">
            <div v-for="item in storeRankView" :key="item.name" class="bar-row">
              <span class="bar-label">{{ item.name }}</span>
              <span class="bar-track">
                <span class="bar-fill" :style="{ width: `${item.percent}%` }" />
              </span>
              <strong>{{ storeRankDim === 'count' ? `${item.count} 次` : `${item.weight} kg` }}</strong>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="10">
        <a-card title="按类型分布" :bordered="false" style="height:100%">
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
import LossModuleTabs from '../../components/LossModuleTabs.vue'
import { getLossDashboard } from '../../api/loss'
import { getSupervisorOptions } from '../../api/supervisor'
import dayjs from 'dayjs'

const dateRange = ref<any>([dayjs().startOf('month'), dayjs()])
const supervisorName = ref('')
const lossType = ref('')
const supervisorOptions = ref<{ label: string; value: string }[]>([])
const storeRankDim = ref('count')

const summary = ref({ totalCount: 0, storeCount: 0, dailyCount: 0, arrivalCount: 0 })
const storeRanking = ref<any[]>([])
const typeDistribution = ref<any[]>([])
const monthlyTrend = ref<any[]>([])

const dailyPct = computed(() => {
  const t = summary.value.totalCount; return t > 0 ? Math.round(summary.value.dailyCount * 100 / t) : 0
})
const arrivalPct = computed(() => {
  const t = summary.value.totalCount; return t > 0 ? Math.round(summary.value.arrivalCount * 100 / t) : 0
})

const storeRankView = computed(() => {
  const dim = storeRankDim.value
  return [...storeRanking.value].sort((a, b) => {
    const va = dim === 'count' ? a.count : a.weight
    const vb = dim === 'count' ? b.count : b.weight
    return vb - va
  })
})

const donutStyle = computed(() => {
  const parts = typeDistribution.value.map((d, i) => {
    const p = d.percent
    const start = i === 0 ? 0 : typeDistribution.value.slice(0, i).reduce((s: number, x: any) => s + x.percent, 0)
    return `${['#2F8F57','#E58A2D'][i]} ${start * 3.6}deg ${(start + p) * 3.6}deg`
  }).join(',')
  return { background: `conic-gradient(${parts})` }
})

async function fetchDashboard() {
  try {
    const params: any = {}
    if (supervisorName.value) params.supervisorName = supervisorName.value
    if (lossType.value) params.lossType = lossType.value
    if (dateRange.value && dateRange.value[0]) {
      params.startDate = dateRange.value[0].format('YYYY-MM-DD')
      params.endDate = dateRange.value[1].format('YYYY-MM-DD')
    }
    const res: any = await getLossDashboard(params)
    const data = res.data || res
    summary.value = data.summary || summary.value
    storeRanking.value = data.storeRanking || []
    typeDistribution.value = data.typeDistribution || []
    monthlyTrend.value = data.monthlyTrend || []
  } catch { /* ignore */ }
}

async function fetchSupervisors() {
  try { const res: any = await getSupervisorOptions(); supervisorOptions.value = res.data || [] } catch { /* */ }
}

onMounted(async () => {
  await fetchSupervisors()
  await fetchDashboard()
})
</script>

<style scoped>
.loss-page { max-width: 1280px; }
.page-title-row { margin-bottom: 16px; }
.page-title { margin: 0; font-size: 24px; font-weight: 600; color: #111827; }
.page-subtitle { margin: 6px 0 0; color: #6b7280; }
.filter-card { margin-bottom: 16px; }
.filter-label { margin-bottom: 6px; color: #6b7280; font-size: 13px; }
.metric-row { margin-bottom: 16px; }
.metric-card { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.metric-label { font-size: 13px; color: #6b7280; margin-bottom: 4px; }
.metric-value { font-size: 28px; font-weight: 700; color: #111827; }
.metric-note { font-size: 12px; color: #9ca3af; margin-top: 4px; }

.bar-list { display: flex; flex-direction: column; gap: 10px; max-height: 400px; overflow-y: auto; }
.bar-row { display: flex; align-items: center; gap: 10px; }
.bar-label { width: 120px; flex-shrink: 0; font-size: 13px; color: #374151; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-track { flex: 1; height: 18px; border-radius: 9px; background: #f3f4f6; overflow: hidden; }
.bar-fill { display: block; height: 100%; border-radius: 9px; background: linear-gradient(90deg, #2F8F57, #4db87a); transition: width .4s; }

.donut-wrap { display: flex; align-items: center; gap: 32px; }
.donut { width: 140px; height: 140px; border-radius: 50%; }
.legend { list-style: none; padding: 0; margin: 0; }
.legend li { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 14px; }
.legend li::before { content: ''; width: 12px; height: 12px; border-radius: 3px; }
.legend li:first-child::before { background: #2F8F57; }
.legend li:last-child::before { background: #E58A2D; }
.legend strong { margin-left: auto; }

.trend-card { margin-top: 16px; }
.trend-chart { display: flex; align-items: flex-end; gap: 24px; height: 200px; padding: 0 20px; }
.trend-item { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; justify-content: flex-end; }
.trend-bar { width: 40px; border-radius: 8px 8px 0 0; background: linear-gradient(180deg, #2F8F57, #7cc99a); transition: height .4s; min-height: 4px; }
.trend-month { margin-top: 8px; font-size: 12px; color: #6b7280; }
</style>
