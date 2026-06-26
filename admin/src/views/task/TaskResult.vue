<template>
  <div class="task-result-page">
    <PageModuleTabs />

    <div class="result-header">
      <a-button type="text" class="back-btn" @click="$router.push('/tasks')">
        <ArrowLeftOutlined />
      </a-button>
      <h1 class="result-title">门店盘点详情</h1>
    </div>

    <a-card class="summary-card" :loading="loading" :bordered="false">
      <div class="summary-grid">
        <div class="summary-item">
          <span class="summary-label">任务名称</span>
          <span class="summary-value strong">
            {{ taskInfo.taskName }}
            <span class="status-badge" :class="'status-badge--' + taskInfo.status">
              <span v-if="taskInfo.status === 'in_progress'" class="status-dot" />
              <CheckCircleOutlined v-if="taskInfo.status === 'submitted'" class="status-check" />
              {{ statusLabel(taskInfo.status) }}
            </span>
          </span>
        </div>
        <div class="summary-item">
          <span class="summary-label">门店名称</span>
          <span class="summary-value">
            <ShopOutlined class="store-icon" />
            {{ taskInfo.storeName }}
          </span>
        </div>
        <div class="summary-item">
          <span class="summary-label">盘点月份</span>
          <span class="summary-value">{{ formatMonth(taskInfo.taskMonth) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">提交时间</span>
          <span class="summary-value">{{ formatSubmittedAt(taskInfo.submittedAt) }}</span>
        </div>
      </div>
    </a-card>

    <a-radio-group v-model:value="viewMode" class="view-tabs" button-style="solid">
      <a-radio-button value="zone">按分区看</a-radio-button>
      <a-radio-button value="material">按物料汇总看</a-radio-button>
    </a-radio-group>

    <div v-if="viewMode === 'zone'" class="zone-grid scroll-area">
      <a-card
        v-for="zone in zoneData"
        :key="zone.id"
        class="zone-result-card"
        :bordered="false"
      >
        <template #title>
          <div class="zone-card-title">
            <component :is="getZoneIcon(zone.zoneName)" class="zone-title-icon" />
            <div>
              <div>{{ zone.zoneName }}</div>
              <div class="zone-subtitle">物料数: {{ zone.materials?.length || 0 }}项</div>
            </div>
          </div>
        </template>
        <a-table
          :columns="zoneColumns"
          :data-source="zone.materials || []"
          :pagination="false"
          row-key="materialId"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'multiUnit'">
              <template v-if="formatUnitInputs(record.unitInputs)">
                <a-tag color="processing">{{ formatUnitInputs(record.unitInputs) }}</a-tag>
              </template>
              <template v-else-if="record.inventoryUnit">
                <a-tag color="processing">{{ record.inventoryUnit }}</a-tag>
              </template>
              <span v-else>-</span>
            </template>
            <template v-if="column.key === 'baseQty'">
              <span :class="{ 'qty-zero': record.quantity === 0 }">
                {{ record.quantity === 0 ? '0' : formatNumber(record.quantity) }}
              </span>
              <span style="margin-left: 4px; font-size: 12px; color: #9ca3af;">
                {{ record.baseUnit || record.unit || '' }}
              </span>
            </template>
            <template v-if="column.key === 'remark'">
              <span v-if="record.remark" class="remark-warn">{{ record.remark }}</span>
              <span v-else>-</span>
            </template>
          </template>
        </a-table>
      </a-card>
      <a-empty v-if="!zoneData.length && !loading" description="暂无分区数据" class="grid-empty" />
    </div>

    <a-card v-else title="物料汇总" class="summary-table-card scroll-area" :bordered="false" :loading="loading">
      <a-table
        :columns="summaryColumns"
        :data-source="summaryData"
        :pagination="false"
        row-key="materialId"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'multiUnit'">
            <template v-if="formatUnitInputs(record.unitInputs)">
              <a-tag color="processing">{{ formatUnitInputs(record.unitInputs) }}</a-tag>
            </template>
            <template v-else-if="record.inventoryUnit">
              <a-tag color="processing">{{ record.inventoryUnit }}</a-tag>
            </template>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ShopOutlined,
  CheckCircleOutlined,
  CloudOutlined,
  AppstoreOutlined,
  InboxOutlined,
  GiftOutlined,
} from '@ant-design/icons-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import { getTaskDetail, getTaskResult } from '../../api/task'

const route = useRoute()
const taskId = Number(route.params.id)
const viewMode = ref<'zone' | 'material'>('zone')
const loading = ref(false)

const taskInfo = reactive({
  taskName: '',
  storeName: '',
  taskMonth: '',
  submittedBy: '',
  submittedAt: '',
  status: '',
})

const zoneData = ref<any[]>([])
const summaryData = ref<any[]>([])

const zoneColumns = [
  { title: '物料名称', dataIndex: 'materialName', key: 'materialName' },
  { title: '规格', dataIndex: 'spec', key: 'spec', width: 120 },
  { title: '多单位', key: 'multiUnit', width: 110 },
  { title: '最小单位总量', key: 'baseQty', width: 110 },
  { title: '备注', key: 'remark' },
]

const summaryColumns = [
  { title: '物料名称', dataIndex: 'materialName', key: 'materialName' },
  { title: '规格', dataIndex: 'spec', key: 'spec', width: 120 },
  { title: '多单位', key: 'multiUnit', width: 140 },
  { title: '最小单位总量', dataIndex: 'totalQuantity', key: 'totalQuantity', width: 110 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 72 },
  { title: '分区总数', dataIndex: 'zoneCount', key: 'zoneCount', width: 88 },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
]

function statusLabel(s: string) {
  const map: Record<string, string> = { not_started: '未开始', in_progress: '进行中', submitted: '已提交' }
  return map[s] || s || '-'
}

function getZoneIcon(name: string) {
  if (/冷藏|冷/.test(name)) return CloudOutlined
  if (/操作|台/.test(name)) return AppstoreOutlined
  if (/包材|杯|吸管/.test(name)) return GiftOutlined
  return InboxOutlined
}

function formatMonth(m: string) {
  if (!m) return '-'
  const [y, mo] = m.split('-')
  return mo ? `${y}年${parseInt(mo, 10)}月` : m
}

function formatSubmittedAt(s: string) {
  if (!s) return '-'
  return s.substring(0, 16).replace('T', ' ')
}

function formatNumber(n: number) {
  return n?.toLocaleString?.() ?? n
}

/** 将 unitInputs JSON (如 {"箱":"1","支":"40"}) 格式化为 "1箱·40支" */
function formatUnitInputs(raw: string): string {
  if (!raw) return ''
  try {
    const obj = JSON.parse(raw)
    return Object.entries(obj)
      .filter(([, v]) => v !== '0' && v !== 0)
      .map(([unit, qty]) => `${qty}${unit}`)
      .join('·')
  } catch {
    return ''
  }
}

async function fetchTaskDetail() {
  try {
    const res: any = await getTaskDetail(taskId)
    const data = res?.data
    if (data) {
      taskInfo.taskName = data.taskName ?? ''
      taskInfo.storeName = data.storeName ?? String(data.storeId ?? '')
      taskInfo.taskMonth = data.taskMonth ?? ''
      taskInfo.submittedBy = data.submittedBy ?? ''
      taskInfo.submittedAt = data.submittedAt ?? ''
      taskInfo.status = data.status ?? ''
    }
  } catch {
    /* API 未就绪时保留空值 */
  }
}

function loadPlaceholderData() {
  taskInfo.taskName = taskInfo.taskName || '-'
  taskInfo.storeName = taskInfo.storeName || '-'
  taskInfo.taskMonth = taskInfo.taskMonth || '-'
  taskInfo.submittedBy = taskInfo.submittedBy || '-'
  taskInfo.submittedAt = taskInfo.submittedAt || '-'
  zoneData.value = []
  summaryData.value = []
}

async function fetchTaskResult() {
  try {
    const res: any = await getTaskResult(taskId)
    const data = res?.data
    if (data?.zones?.length) zoneData.value = data.zones
    if (data?.summary?.length) summaryData.value = data.summary
    if (!zoneData.value.length) loadPlaceholderData()
  } catch {
    loadPlaceholderData()
  }
}

async function initPage() {
  loading.value = true
  try {
    await Promise.all([fetchTaskDetail(), fetchTaskResult()])
  } catch (e: any) {
    message.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(initPage)
</script>

<style scoped>
.task-result-page {
  max-width: 1280px;
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.back-btn {
  font-size: 18px;
}

.result-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.summary-card {
  margin-bottom: 20px;
  border-radius: var(--radius, 8px);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr) auto;
  gap: 16px 24px;
  align-items: start;
}

.summary-label {
  display: block;
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.summary-value {
  font-size: 14px;
  color: #374151;
}

.summary-value.strong {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.store-icon {
  margin-right: 6px;
  color: var(--primary, #0d7a3d);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  margin-left: 12px;
  vertical-align: middle;
}

.status-badge--in_progress {
  background: #eff6ff;
  color: #1d4ed8;
}

.status-badge--in_progress .status-dot {
  background: #3b82f6;
}

.status-badge--submitted {
  background: #ecfdf5;
  color: #047857;
}

.status-badge--not_started {
  background: #f3f4f6;
  color: #9ca3af;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
}

.status-check {
  font-size: 11px;
}

.view-tabs {
  margin-bottom: 20px;
}

:deep(.view-tabs .ant-radio-button-wrapper-checked) {
  background: var(--primary, #0d7a3d) !important;
  border-color: var(--primary, #0d7a3d) !important;
}

.zone-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

@media (max-width: 992px) {
  .zone-grid {
    grid-template-columns: 1fr;
  }
  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }
}

.zone-result-card,
.summary-table-card {
  border-radius: var(--radius, 8px);
}

.zone-card-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.zone-title-icon {
  font-size: 22px;
  color: var(--primary, #0d7a3d);
}

.zone-subtitle {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 400;
  margin-top: 2px;
}

.qty-zero {
  color: #ef4444;
  font-weight: 600;
}

.remark-warn {
  color: #ef4444;
  font-size: 12px;
}

.grid-empty {
  grid-column: 1 / -1;
  padding: 40px;
}
</style>
