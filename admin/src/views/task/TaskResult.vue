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

    <a-card class="summary-table-card scroll-area" :bordered="false" :loading="loading">
      <template #title>
        <span>物料汇总</span>
        <a-input
          v-model:value="searchKeyword"
          placeholder="搜索物料名称"
          allow-clear
          size="small"
          style="width:200px;margin-left:16px"
        />
      </template>
      <a-table
        :columns="summaryColumns"
        :data-source="filteredSummary"
        :pagination="false"
        row-key="materialId"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'totalQuantity'">
            {{ formatNumber(record.totalQuantity) }}
          </template>
          <template v-else-if="column.key === 'multiUnit'">
            <a-tag v-if="formatUnitInputs(record.unitInputs)" color="processing">{{ formatUnitInputs(record.unitInputs) }}</a-tag>
            <a-tag v-else color="processing">{{ formatNumber(record.totalQuantity) }}{{ record.unit }}</a-tag>
          </template>
          <template v-else-if="column.key === 'unitPrice'">
            <span v-if="record.unitPrice != null">¥{{ formatNumber(record.unitPrice) }}</span>
            <span v-else class="text-gray">--</span>
          </template>
          <template v-else-if="column.key === 'amount'">
            <span v-if="record.amount != null" class="amount-cell">¥{{ (record.amount as number).toFixed(2) }}</span>
            <span v-else class="text-gray">--</span>
          </template>
          <template v-else>
            {{ record[column.dataIndex] }}
          </template>
        </template>
        <template #footer v-if="totalAmount > 0">
          <div class="table-footer">
            <span class="footer-label">盘点金额合计</span>
            <span class="footer-amount">¥{{ totalAmount.toFixed(2) }}</span>
          </div>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined, ShopOutlined, CheckCircleOutlined,
} from '@ant-design/icons-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import { getTaskDetail, getTaskResult } from '../../api/task'

const route = useRoute()
const taskId = Number(route.params.id)
const loading = ref(false)
const taskInfo = reactive({ taskName: '', storeName: '', taskMonth: '', submittedBy: '', submittedAt: '', status: '' })
const summaryData = ref<any[]>([])
const searchKeyword = ref('')

const filteredSummary = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return summaryData.value
  return summaryData.value.filter((r: any) => (r.materialName || '').toLowerCase().includes(kw))
})

const totalAmount = ref(0)

const summaryColumns = [
  { title: '物料名称', dataIndex: 'materialName', key: 'materialName', width: 120},
  { title: '规格', dataIndex: 'spec', key: 'spec', width: 100 },
  { title: '录入明细', key: 'multiUnit', width: 130 },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 60 },
  { title: '单价', key: 'unitPrice', width: 72, align: 'right' as const },
  { title: '最小单位总量', key: 'totalQuantity', width: 90 },
  { title: '金额', key: 'amount', width: 88, align: 'right' as const },
]

function statusLabel(s: string) { const m: any = { not_started: '未开始', in_progress: '进行中', submitted: '已提交' }; return m[s] || s || '-' }
function formatMonth(m: string) { if (!m) return '-'; const [y, mo] = m.split('-'); return mo ? `${y}年${parseInt(mo, 10)}月` : m }
function formatSubmittedAt(s: string) { if (!s) return '-'; return s.substring(0, 16).replace('T', ' ') }
function formatNumber(n: number) { return n?.toLocaleString?.() ?? n }

function formatUnitInputs(raw: string): string {
  if (!raw) return ''
  try {
    const obj = JSON.parse(raw)
    return Object.entries(obj).filter(([, v]) => v !== '0' && v !== 0).map(([u, q]) => `${q}${u}`).join('·')
  } catch { return '' }
}

async function fetchTaskDetail() {
  try {
    const res: any = await getTaskDetail(taskId)
    const d = res?.data
    if (d) { taskInfo.taskName = d.taskName ?? ''; taskInfo.storeName = d.storeName ?? ''; taskInfo.taskMonth = d.taskMonth ?? ''; taskInfo.submittedBy = d.submittedBy ?? ''; taskInfo.submittedAt = d.submittedAt ?? ''; taskInfo.status = d.status ?? '' }
  } catch { /* ignore */ }
}

async function fetchTaskResult() {
  try {
    const res: any = await getTaskResult(taskId)
    const d = res?.data
    if (d?.summary?.length) summaryData.value = d.summary
    if (d?.totalAmount != null) totalAmount.value = Number(d.totalAmount)
  } catch { /* ignore */ }
}

async function initPage() {
  loading.value = true
  try { await Promise.all([fetchTaskDetail(), fetchTaskResult()]) }
  catch (e: any) { message.error('加载失败: ' + e.message) }
  finally { loading.value = false }
}

onMounted(initPage)
</script>

<style scoped>
.task-result-page { max-width: 1280px; height: calc(100vh - 64px); display: flex; flex-direction: column; overflow: hidden; }
.scroll-area { flex: 1; overflow-y: auto; min-height: 0; }
.result-header { display: flex; align-items: center; gap: 8px; margin-bottom: 20px; }
.back-btn { font-size: 18px; }
.result-title { margin: 0; font-size: 22px; font-weight: 700; }
.summary-card { margin-bottom: 20px; border-radius: var(--radius, 8px); }
.summary-grid { display: grid; grid-template-columns: repeat(3, 1fr) auto; gap: 16px 24px; align-items: start; }
.summary-label { display: block; font-size: 12px; color: #9ca3af; margin-bottom: 4px; }
.summary-value { font-size: 14px; color: #374151; }
.summary-value.strong { font-size: 16px; font-weight: 600; color: #1f2937; }
.store-icon { margin-right: 6px; color: var(--primary, #0d7a3d); }
.status-badge { display: inline-flex; align-items: center; gap: 6px; padding: 2px 10px; border-radius: 12px; font-size: 12px; margin-left: 12px; vertical-align: middle; }
.status-badge--in_progress { background: #eff6ff; color: #1d4ed8; }
.status-badge--submitted { background: #ecfdf5; color: #047857; }
.status-badge--not_started { background: #f3f4f6; color: #9ca3af; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; background: #22c55e; }
.status-check { font-size: 11px; }
.summary-table-card { border-radius: var(--radius, 8px); }
.table-footer { display: flex; justify-content: flex-end; align-items: center; gap: 16px; }
.footer-label { font-size: 14px; font-weight: 600; color: #374151; }
.footer-amount { font-size: 16px; font-weight: 700; color: #e65c2e; }
.amount-cell { font-weight: 600; color: #e65c2e; }
.text-gray { color: #9ca3af; }
@media (max-width: 992px) { .summary-grid { grid-template-columns: 1fr 1fr; } }
</style>
