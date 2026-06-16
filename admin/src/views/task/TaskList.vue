<template>
  <div class="task-list-page">
    <PageModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">盘点任务管理</h1>
        <p class="page-subtitle">监控和管理全门店盘点进度</p>
      </div>
      <a-button type="primary" size="large" class="btn-create" @click="$router.push('/tasks/create')">
        <template #icon><PlusOutlined /></template>
        新建盘点任务
      </a-button>
    </div>

    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">月份</div>
          <a-date-picker
            v-model:value="filters.month"
            picker="month"
            placeholder="选择月份"
            style="width: 100%"
            format="YYYY年M月"
          />
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">门店</div>
          <a-select
            v-model:value="filters.storeId"
            placeholder="全部门店"
            style="width: 100%"
            allow-clear
            show-search
            option-filter-prop="label"
            :loading="storesLoading"
          >
            <a-select-option value="">全部门店</a-select-option>
            <a-select-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.mendianmingcheng">
              {{ s.mendianmingcheng }}
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">状态</div>
          <a-select v-model:value="filters.status" placeholder="全部状态" style="width: 100%" allow-clear>
            <a-select-option value="">全部状态</a-select-option>
            <a-select-option value="not_started">未开始</a-select-option>
            <a-select-option value="in_progress">进行中</a-select-option>
            <a-select-option value="submitted">已提交</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">模板名称</div>
          <a-input
            v-model:value="filters.templateName"
            placeholder="输入模板名称搜索"
            allow-clear
            @pressEnter="handleSearch"
          />
        </a-col>
      </a-row>
      <div class="filter-actions">
        <a-button @click="handleReset">重置</a-button>
        <a-button class="btn-query" @click="handleSearch">查询</a-button>
      </div>
    </a-card>

    <a-card class="table-card" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :pagination="tablePagination"
        :loading="loading"
        row-key="taskId"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taskName'">
            <a class="task-name-link" @click="goResult(record)">{{ record.taskName }}</a>
          </template>
          <template v-if="column.key === 'store'">
            {{ record.storeName || record.storeId || '-' }}
          </template>
          <template v-if="column.key === 'deadline'">
            {{ formatDate(record.deadline) }}
          </template>
          <template v-if="column.key === 'status'">
            <span class="status-badge" :class="'status-badge--' + record.status">
              <span v-if="record.status === 'in_progress'" class="status-dot" />
              <CheckOutlined v-if="record.status === 'submitted'" class="status-check" />
              {{ statusLabelMap[record.status] || record.status }}
            </span>
          </template>
          <template v-if="column.key === 'submittedAt'">
            {{ record.submittedAt ? formatDate(record.submittedAt) : '-' }}
          </template>
          <template v-if="column.key === 'action'">
            <span class="action-cell">
              <a-tag class="action-tag action-view-tag" @click="goResult(record)">查看</a-tag>
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
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, CheckOutlined } from '@ant-design/icons-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import { getTasks, deleteTask } from '../../api/task'
import { getStores } from '../../api/store'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const storesLoading = ref(false)
const dataSource = ref<any[]>([])
const stores = ref<any[]>([])

const filters = reactive({
  month: null as any,
  storeId: '' as string,
  status: '' as string,
  templateName: '',
})

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
  { title: '任务名称', dataIndex: 'taskName', key: 'taskName', ellipsis: true },
  { title: '门店', key: 'store', width: 140 },
  { title: '月份', dataIndex: 'taskMonth', key: 'taskMonth', width: 100 },
  { title: '模板', dataIndex: 'templateName', key: 'templateName', ellipsis: true },
  { title: '截止时间', key: 'deadline', width: 160 },
  { title: '状态', key: 'status', width: 110 },
  { title: '分区数', dataIndex: 'zoneCount', key: 'zoneCount', width: 80, align: 'center' as const },
  { title: '物料数', dataIndex: 'materialCount', key: 'materialCount', width: 80, align: 'center' as const },
  { title: '提交时间', key: 'submittedAt', width: 160 },
  { title: '操作', key: 'action', width: 130 },
]

function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.substring(0, 16).replace('T', ' ')
}

function goResult(record: any) {
  router.push(`/tasks/${record.taskId}/result`)
}

async function handleDelete(record: any) {
  try {
    await deleteTask(record.taskId)
    message.success('删除成功')
    fetchData()
  } catch (e: any) {
    message.error(e.message || '删除失败')
  }
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
    if (filters.storeId) params.storeId = filters.storeId
    if (filters.status) params.status = filters.status
    if (filters.templateName) params.templateName = filters.templateName
    const headerKeyword = (route.query.keyword as string) || ''
    if (headerKeyword) params.keyword = headerKeyword

    const res = (await getTasks(params)) as any
    dataSource.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  filters.month = null
  filters.storeId = ''
  filters.status = ''
  filters.templateName = ''
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

onMounted(() => {
  fetchStores()
  fetchData()
})

watch(
  () => route.query.keyword,
  () => {
    pagination.current = 1
    fetchData()
  },
)
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
  margin-top: 16px;
  display: flex;
  gap: 12px;
}

.btn-query {
  background: rgba(13, 122, 61, 0.1);
  border-color: rgba(13, 122, 61, 0.2);
  color: #0D7A3D;
}

.btn-query:hover {
  background: rgba(13, 122, 61, 0.18) !important;
  border-color: #0D7A3D !important;
  color: #0D7A3D !important;
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
</style>
