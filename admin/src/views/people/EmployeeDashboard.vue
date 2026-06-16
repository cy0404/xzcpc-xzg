<template>
  <div class="people-page">
    <PeopleModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">员工统计看板</h1>
        <p class="page-subtitle">汇总人员规模、门店分布、岗位结构与入离职趋势</p>
      </div>
      <a-segmented v-model:value="range" :options="rangeOptions" @change="fetchDashboard" />
    </div>

    <a-row :gutter="[16, 16]" class="metric-row">
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">在职总人数</div>
          <div class="metric-value">{{ summary.activeCount }}</div>
          <div class="metric-note">覆盖 {{ summary.storeCount }} 家门店</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">本月新入职</div>
          <div class="metric-value">{{ summary.newHireCount }}</div>
          <div class="metric-note">{{ summary.newHireChange }}</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">本月离职</div>
          <div class="metric-value">{{ summary.leaveCount }}</div>
          <div class="metric-note">{{ summary.leaveNote }}</div>
        </div>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <div class="metric-card">
          <div class="metric-label">流动率</div>
          <div class="metric-value">{{ summary.turnoverRate }}%</div>
          <div class="metric-note">{{ summary.turnoverNote }}</div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="14">
        <a-card title="各门店人员分布" :bordered="false">
          <div class="bar-list">
            <div v-for="item in storeDistribution" :key="item.name" class="bar-row">
              <span class="bar-label">{{ item.name }}</span>
              <span class="bar-track">
                <span class="bar-fill" :style="{ width: `${item.percent}%` }" />
              </span>
              <strong>{{ item.count }} 人</strong>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="10">
        <a-card title="岗位占比" :bordered="false">
          <div class="donut-wrap">
            <div class="donut" />
            <ul class="legend">
              <li v-for="item in roleDistribution" :key="item.name">
                <span>{{ item.name }}</span>
                <strong>{{ item.percent }}%</strong>
              </li>
            </ul>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="月度入离职趋势" :bordered="false" class="trend-card">
      <div class="trend-legend">
        <span><i class="dot hire-dot" /> 新入职</span>
        <span><i class="dot leave-dot" /> 离职</span>
      </div>
      <div class="trend-chart">
        <div v-for="item in trendWithPercent" :key="item.month" class="trend-group">
          <span class="trend-bar hire-bar" :style="{ height: `${item.hirePercent}%` }" />
          <span class="trend-bar leave-bar" :style="{ height: `${item.leavePercent}%` }" />
          <span class="trend-month">{{ item.month }}</span>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PeopleModuleTabs from '../../components/PeopleModuleTabs.vue'
import { getPeopleDashboard } from '../../api/people'

const range = ref('month')
const rangeOptions = [
  { label: '本月', value: 'month' },
  { label: '近3月', value: 'quarter' },
  { label: '近半年', value: 'halfYear' },
]

const summary = ref({
  activeCount: 86,
  storeCount: 8,
  newHireCount: 6,
  newHireChange: '较上月增加 2 人',
  leaveCount: 3,
  leaveNote: '主要来自兼职岗位',
  turnoverRate: 3.5,
  turnoverNote: '低于近半年均值',
})

const storeDistribution = ref([
  { name: '南山万象店', count: 14, percent: 100 },
  { name: '福田中心城店', count: 12, percent: 86 },
  { name: '罗湖万象城店', count: 11, percent: 79 },
  { name: '宝安壹方城店', count: 10, percent: 71 },
  { name: '龙岗星河店', count: 9, percent: 64 },
])

const roleDistribution = ref([
  { name: '咖啡师', percent: 44 },
  { name: '店员', percent: 26 },
  { name: '值班主管', percent: 16 },
  { name: '店长', percent: 14 },
])

const trend = ref([
  { month: '10月', hire: 5, leave: 2 },
  { month: '11月', hire: 8, leave: 3 },
  { month: '12月', hire: 7, leave: 4 },
  { month: '1月', hire: 9, leave: 3 },
  { month: '2月', hire: 10, leave: 5 },
  { month: '3月', hire: 8, leave: 4 },
  { month: '4月', hire: 11, leave: 3 },
])

const trendWithPercent = computed(() => {
  const max = Math.max(...trend.value.flatMap((item) => [item.hire, item.leave]))
  return trend.value.map((item) => ({
    ...item,
    hirePercent: Math.round((item.hire / max) * 100),
    leavePercent: Math.round((item.leave / max) * 100),
  }))
})

async function fetchDashboard() {
  try {
    const res = (await getPeopleDashboard({ range: range.value })) as any
    const data = res.data
    if (!data) return
    summary.value = data.summary || summary.value
    storeDistribution.value = data.storeDistribution || storeDistribution.value
    roleDistribution.value = data.roleDistribution || roleDistribution.value
    trend.value = data.trend || trend.value
  } catch {
    // 接口未上线时使用页面内样例数据。
  }
}

onMounted(fetchDashboard)
</script>

<style scoped>
.people-page {
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
  grid-template-columns: 120px minmax(0, 1fr) 64px;
  align-items: center;
  gap: 12px;
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

.trend-legend {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  color: #6b7280;
  font-size: 13px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 4px;
  border-radius: 999px;
}

.hire-dot {
  background: var(--primary);
}

.leave-dot {
  background: #d7b36a;
}

.trend-chart {
  display: flex;
  align-items: end;
  justify-content: space-around;
  height: 240px;
  margin-top: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.trend-group {
  position: relative;
  display: flex;
  height: 100%;
  align-items: end;
  gap: 6px;
}

.trend-bar {
  width: 18px;
  min-height: 12px;
  border-radius: 6px 6px 0 0;
}

.hire-bar {
  background: var(--primary);
}

.leave-bar {
  background: #d7b36a;
}

.trend-month {
  position: absolute;
  bottom: -24px;
  left: 50%;
  color: #6b7280;
  font-size: 12px;
  transform: translateX(-50%);
}
</style>
