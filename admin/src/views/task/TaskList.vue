<template>
  <div class="task-list-page">
    <PageModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">盘点列表</h1>
        <p class="page-subtitle">监控和管理全门店盘点进度</p>
      </div>
      <a-button type="primary" size="large" class="btn-create" @click="$router.push('/tasks/create')">
        <template #icon><PlusOutlined /></template>
        新建盘点任务
      </a-button>
    </div>

    <a-tabs v-model:activeKey="tabKey" @change="onTabChange" style="margin-bottom:8px">
      <a-tab-pane key="list" tab="盘点列表" />
      <a-tab-pane key="report" tab="统计报表" />
    </a-tabs>

    <!-- 指标卡片 -->
    <a-row :gutter="12" class="metric-row" v-if="tabKey === 'report'">
      <a-col :xs="8" :md="8"><a-card :bordered="false" class="metric-card"><a-statistic title="未开始" :value="stats.notStarted" value-style="font-size:24px;font-weight:700;color:#909399" /></a-card></a-col>
      <a-col :xs="8" :md="8"><a-card :bordered="false" class="metric-card"><a-statistic title="进行中" :value="stats.inProgress" value-style="font-size:24px;font-weight:700;color:#1989fa" /></a-card></a-col>
      <a-col :xs="8" :md="8"><a-card :bordered="false" class="metric-card"><a-statistic title="已提交" :value="stats.submitted" value-style="font-size:24px;font-weight:700;color:#07c160" /></a-card></a-col>
    </a-row>

    <a-card class="filter-card" :bordered="false" v-if="tabKey === 'list'">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">月份</div>
          <a-date-picker v-model:value="filters.month" picker="month" placeholder="选择月份" style="width:100%" format="YYYY年M月" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">门店</div>
          <a-select v-model:value="filters.storeIds" mode="multiple" placeholder="全部门店" style="width:100%" allow-clear show-search option-filter-prop="label" :loading="storesLoading" :max-tag-count="10">
            <a-select-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.mendianmingcheng">{{ s.mendianmingcheng }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">督导</div>
          <a-select v-model:value="filters.supervisorName" placeholder="全部督导" style="width:100%" allow-clear :options="supervisorOptions" @change="handleSearch" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">状态</div>
          <a-select v-model:value="filters.status" placeholder="全部状态" style="width:100%" allow-clear>
            <a-select-option value="">全部状态</a-select-option>
            <a-select-option value="not_started">未开始</a-select-option>
            <a-select-option value="in_progress">进行中</a-select-option>
            <a-select-option value="submitted">已提交</a-select-option>
          </a-select>
        </a-col>
      </a-row>
      <div class="filter-actions" v-if="tabKey === 'list'">
        <a-button @click="handleReset">重置</a-button>
        <a-button type="primary" @click="handleSearch">查询</a-button>
      </div>
    </a-card>

    <a-card class="table-card" :bordered="false" v-if="tabKey === 'list'">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :pagination="tablePagination"
        :loading="loading"
        row-key="id"
        :scroll="{ x: 1050 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taskName'">
            <a class="task-name-link" @click="goResult(record)">{{ record.taskName }}</a>
          </template>
          <template v-else-if="column.key === 'store'">
            {{ record.storeName || record.storeId || '-' }}
          </template>
          <template v-else-if="column.key === 'deadline'">
            {{ formatDate(record.deadline) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <span class="status-badge" :class="'status-badge--' + record.status">
              <span v-if="record.status === 'in_progress'" class="status-dot" />
              <CheckOutlined v-if="record.status === 'submitted'" class="status-check" />
              {{ statusLabelMap[record.status] || record.status }}
            </span>
          </template>
          <template v-else-if="column.key === 'submittedAt'">
            {{ record.submittedAt ? formatDate(record.submittedAt) : '-' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <span class="action-cell">
              <a-tag class="action-tag action-view-tag" @click="goResult(record)">查看</a-tag>
              <template v-if="record.status !== 'submitted'">
                <a-popover
                  trigger="click"
                  :open="extendingId === record.id"
                  @openChange="(v: boolean) => { if (!v) extendingId = null }"
                  placement="left"
                >
                  <template #content>
                    <div style="display:flex;flex-direction:column;gap:8px;min-width:220px">
                      <a-date-picker
                        v-model:value="extendValue"
                        show-time
                        format="YYYY-MM-DD HH:mm:ss"
                        style="width:100%"
                        placeholder="新截止时间"
                        :input-read-only="true"
                      />
                      <div style="display:flex;gap:8px">
                        <a-button size="small" type="primary" @click="handleExtend(record)">确认</a-button>
                        <a-button size="small" @click="extendingId = null">取消</a-button>
                      </div>
                    </div>
                  </template>
                  <a-tag class="action-tag action-edit-tag" @click="startExtend(record)"> 延期</a-tag>
                </a-popover>
              </template>
              <a-popconfirm
                v-if="record.status === 'not_started'"
                title="确定删除该任务？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleDelete(record)"
              >
                <a-tag class="action-tag action-delete-tag">删除</a-tag>
              </a-popconfirm>
            </span>
          </template>
          <template v-else>
            {{ record[column.dataIndex] }}
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 统计报表 Tab -->
    <a-card :bordered="false" v-if="tabKey === 'report'" title="督导完成度统计" :loading="reportLoading">
      <div v-if="reportData.length === 0" style="text-align:center;padding:40px;color:#98A19C">暂无数据</div>
      <div v-else>
        <template v-for="item in reportData" :key="item.name">
        <div class="report-item">
          <div class="report-label"><a @click="showStoreDetail(item)">{{ item.name }}</a></div>
          <div class="report-bar-wrap">
            <span class="report-bar" :style="{ width: pct(item.submitted + item.inProgress + item.notStarted, maxTotal) }">
              <span class="bar-seg bar-submitted" :style="{ width: pct(item.submitted, item.total) }" />
              <span class="bar-seg bar-progress" :style="{ width: pct(item.inProgress, item.total) }" />
              <span class="bar-seg bar-notstart" :style="{ width: pct(item.notStarted, item.total) }" />
            </span>
          </div>
          <span class="report-nums"><span style="color:#07c160;font-weight:600">{{ item.submitted }}</span>/<span style="color:#1989fa;font-weight:600">{{ item.inProgress }}</span>/<span style="color:#909399;font-weight:600">{{ item.notStarted }}</span> ({{ item.total }}总)</span>
        </div>
        </template>
        <div class="report-legend">
          <span class="legend-dot" style="background:#07c160" /> 已提交
          <span class="legend-dot" style="background:#1989fa" /> 进行中
          <span class="legend-dot" style="background:#909399" /> 未开始
        </div>
      </div>
    </a-card>

    <!-- 门店详情弹窗 -->
    <a-modal v-model:open="storeModalOpen" :title="storeModalTitle" :footer="null" width="500">
      <div v-if="storeModalNot.length" style="margin-bottom:12px">
        <a-tag color="default">未开始 ({{ storeModalNot.length }})</a-tag>
        <div style="margin-top:4px;font-size:13px;color:#666">{{ storeModalNot.join('、') }}</div>
      </div>
      <div v-if="storeModalIng.length">
        <a-tag color="processing">进行中 ({{ storeModalIng.length }})</a-tag>
        <div style="margin-top:4px;font-size:13px;color:#666">{{ storeModalIng.join('、') }}</div>
      </div>
    </a-modal>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, CheckOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import { getTasks, getLatestMonth, deleteTask, updateTask } from '../../api/task'
import { getStores } from '../../api/store'
import { getSupervisorOptions } from '../../api/supervisor'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const storesLoading = ref(false)
const dataSource = ref<any[]>([])
const stores = ref<any[]>([])

const filters = reactive({
  month: null as any,
  storeIds: [] as string[],
  supervisorName: '' as string,
  status: '' as string,
})
const supervisorOptions = ref<{ label: string; value: string }[]>([])
const stats = reactive({ notStarted: 0, inProgress: 0, submitted: 0 })
const tabKey = ref('list')
const reportLoading = ref(false)
const reportData = ref<any[]>([])
const storeModalOpen = ref(false)
const storeModalTitle = ref('')
const storeModalNot = ref<string[]>([])
const storeModalIng = ref<string[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const tablePagination = computed(() => ({
  current: pagination.current,
  pageSize: pagination.pageSize,
  total: pagination.total,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条记录`,
}))

const statusLabelMap: Record<string, string> = {
  not_started: '未开始',
  in_progress: '进行中',
  submitted: '已提交',
}

const columns = [
  { title: '门店', key: 'store', width: 180, ellipsis: true, fixed: 'left' as const },
  { title: '督导', dataIndex: 'supervisorName', key: 'supervisorName', width: 140, ellipsis: true },
  { title: '任务名称', dataIndex: 'taskName', key: 'taskName', width: 140, ellipsis: true },
  { title: '月份', dataIndex: 'taskMonth', key: 'taskMonth', width: 100 },
  { title: '截止时间', key: 'deadline', width: 160 },
  { title: '状态', key: 'status', width: 110 },
  { title: '物料数', dataIndex: 'materialCount', key: 'materialCount', width: 80, align: 'center' as const },
  { title: '提交时间', key: 'submittedAt', width: 160 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const },
]

function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.substring(0, 16).replace('T', ' ')
}

function goResult(record: any) {
  router.push(`/tasks/${record.id}/result`)
}

async function handleDelete(record: any) {
  try {
    await deleteTask(record.id)
    message.success('删除成功')
    fetchData()
  } catch (e: any) {
    message.error(e.message || '删除失败')
  }
}

// ====== 延期 ======
const extendingId = ref<number | null>(null)
const extendValue = ref<any>(null)

function startExtend(record: any) {
  extendingId.value = record.id
  extendValue.value = null
}

async function handleExtend(record: any) {
  if (!extendValue.value) { message.warning('请选择截止时间'); return }
  try {
    const dl = extendValue.value.format('YYYY-MM-DD HH:mm:ss')
    await updateTask(record.id, { deadline: dl })
    message.success('已延期')
    extendingId.value = null
    fetchData()
  } catch (e: any) {
    message.error(e.message || '延期失败')
  }
}

function goEditResult(record: any) {
  router.push(`/tasks/${record.id}/result?edit=1`)
}

async function fetchStores() {
  storesLoading.value = true
  try {
    const res = (await getStores()) as any
    stores.value = res.data || []
  } finally {
    storesLoading.value = false
  }
}

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    }
    if (filters.month) params.taskMonth = filters.month.format('YYYY-MM')
    if (filters.storeIds.length) params.storeIds = filters.storeIds.join(',')
    if (filters.supervisorName) params.supervisorName = filters.supervisorName
    if (filters.status) params.status = filters.status
    const res = (await getTasks(params)) as any
    dataSource.value = res.data?.records || []
    pagination.total = res.data?.total || 0
    fetchStats()
  } finally { loading.value = false }
}

async function fetchStats() {
  if (!filters.month) { stats.notStarted = 0; stats.inProgress = 0; stats.submitted = 0; return }
  try {
    const m = filters.month.format('YYYY-MM')
    const [ns, ip, sb] = await Promise.all([
      getTasks({ taskMonth: m, status: 'not_started', pageNum: 1, pageSize: 1 }),
      getTasks({ taskMonth: m, status: 'in_progress', pageNum: 1, pageSize: 1 }),
      getTasks({ taskMonth: m, status: 'submitted', pageNum: 1, pageSize: 1 }),
    ])
    stats.notStarted = (ns as any).data?.total || 0
    stats.inProgress = (ip as any).data?.total || 0
    stats.submitted = (sb as any).data?.total || 0
  } catch { /* ignore */ }
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  filters.month = null
  filters.storeIds = []
  filters.supervisorName = ''
  filters.status = ''
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

async function fetchSupervisors() {
  try {
    const res = await getSupervisorOptions() as any
    supervisorOptions.value = res.data || []
  } catch { /* ignore */ }
}

const maxTotal = computed(() => Math.max(1, ...reportData.value.map(d => d.total || 0)))
function pct(v: number, max: number) { return max === 0 ? '0%' : ((v / max) * 100).toFixed(1) + '%' }
function onTabChange(key: string) { if (key === 'report') fetchReport() }
function showStoreDetail(item: any) {
  storeModalTitle.value = item.name
  storeModalNot.value = item.notStartedStores || []
  storeModalIng.value = item.inProgressStores || []
  storeModalOpen.value = true
}

async function fetchReport() {
  reportLoading.value = true
  try {
    const params: any = {}
    if (filters.month) params.taskMonth = filters.month.format('YYYY-MM')
    const res: any = await getTasks({ ...params, pageNum: 1, pageSize: 10000 })
    const records = res.data?.records || []
    const agg: Record<string, any> = {}
    for (const r of records) {
      const sv = r.supervisorName || '未分配'
      if (!agg[sv]) agg[sv] = { name: sv, notStarted: 0, inProgress: 0, submitted: 0, total: 0, notStartedStores: [] as string[], inProgressStores: [] as string[] }
      if (r.status === 'not_started') { agg[sv].notStarted++; agg[sv].notStartedStores.push(r.storeName || r.storeId) }
      else if (r.status === 'in_progress') { agg[sv].inProgress++; agg[sv].inProgressStores.push(r.storeName || r.storeId) }
      else if (r.status === 'submitted') agg[sv].submitted++
      agg[sv].total++
    }
    reportData.value = Object.values(agg).sort((a: any, b: any) => b.total - a.total)
  } catch { /* ignore */ }
  finally { reportLoading.value = false }
}

onMounted(async () => {
  fetchStores()
  fetchSupervisors()
  // 默认选中最近月份
  try { const r: any = await getLatestMonth(); if (r.data) filters.month = dayjs(r.data) } catch { }
  fetchData()
})

</script>

<style scoped>
.task-list-page {
  max-width: 1280px;
}

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  gap: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 14px;
  color: #6b7280;
}

.btn-create {
  height: 44px;
  padding: 0 24px;
  font-weight: 500;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(13, 122, 61, 0.25);
}

.filter-card,
.table-card {
  margin-bottom: 16px;
  border-radius: var(--radius, 8px);
}

.filter-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.task-name-link {
  color: var(--primary, #0d7a3d);
  font-weight: 500;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
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

.action-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.action-tag {
  cursor: pointer;
  border-radius: 4px;
  font-size: 12px;
  padding: 0 7px;
  line-height: 20px;
}

.action-view-tag {
  color: #0d7a3d;
  background: rgba(13, 122, 61, 0.1);
  border: 1px solid rgba(13, 122, 61, 0.2);
}

.action-delete-tag {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.1);
  border: 1px solid rgba(220, 38, 38, 0.2);
}

.action-edit-tag {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
  border: 1px solid rgba(37, 99, 235, 0.2);
}

.action-save-tag {
  color: #fff;
  background: #0d7a3d;
  border: 1px solid #0d7a3d;
}

:deep(.ant-select-selection-overflow) {
  flex-wrap: nowrap !important;
  overflow-x: auto;
}
.metric-row { margin-bottom: 12px }
.metric-card { min-height: 80px }
.report-item{display:flex;align-items:center;gap:12px;margin-bottom:8px}
.report-label{width:100px;font-size:13px;font-weight:500;color:#1F2421;flex-shrink:0}
.report-bar-wrap{flex:1;height:20px;background:#F0F2F0;border-radius:6px;overflow:hidden;min-width:60px}
.report-bar{display:flex;height:100%;border-radius:6px}
.bar-seg{height:100%}
.bar-submitted{background:#07c160}
.bar-progress{background:#1989fa}
.bar-notstart{background:#909399}
.report-nums{font-size:12px;color:#66706A;width:120px;flex-shrink:0}
.report-legend{display:flex;gap:16px;margin-top:12px;font-size:12px;color:#66706A}
.legend-dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:4px}
.report-stores{font-size:12px;color:#66706A;margin-left:112px;margin-bottom:4px}
.store-tag-not{color:#909399}
.store-tag-ing{color:#1989fa}
</style>
