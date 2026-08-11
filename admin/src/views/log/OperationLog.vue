<template>
  <div class="log-page">
    <LogModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">操作日志</h1>
        <p class="page-subtitle">查看总部端和小程序端的操作记录，按操作人、模块、操作类型筛选</p>
      </div>
    </div>

    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="8" :md="6">
          <div class="filter-label">操作人</div>
          <a-input v-model:value="filters.username" placeholder="输入用户名搜索" allow-clear @pressEnter="handleSearch" />
        </a-col>
        <a-col :xs="24" :sm="8" :md="6">
          <div class="filter-label">来源</div>
          <a-select v-model:value="filters.source" placeholder="全部" allow-clear style="width: 100%">
            <a-select-option value="admin">总部端</a-select-option>
            <a-select-option value="mp">小程序端</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="8" :md="6">
          <div class="filter-label">模块</div>
          <a-select v-model:value="filters.module" placeholder="全部模块" allow-clear show-search style="width: 100%">
            <a-select-option value="人员">人员</a-select-option>
            <a-select-option value="任务">任务</a-select-option>
            <a-select-option value="小程序-上传">小程序-上传</a-select-option>
            <a-select-option value="小程序-人员">小程序-人员</a-select-option>
            <a-select-option value="小程序-任务">小程序-任务</a-select-option>
            <a-select-option value="小程序-分区">小程序-分区</a-select-option>
            <a-select-option value="小程序-工时">小程序-工时</a-select-option>
            <a-select-option value="小程序-报损">小程序-报损</a-select-option>
            <a-select-option value="小程序-支出">小程序-支出</a-select-option>
            <a-select-option value="小程序-物料">小程序-物料</a-select-option>
            <a-select-option value="小程序-调货">小程序-调货</a-select-option>
            <a-select-option value="小程序-问题处理">小程序-问题处理</a-select-option>
            <a-select-option value="支出">支出</a-select-option>
            <a-select-option value="支出类型">支出类型</a-select-option>
            <a-select-option value="权限">权限</a-select-option>
            <a-select-option value="模板">模板</a-select-option>
            <a-select-option value="模板分区">模板分区</a-select-option>
            <a-select-option value="模板分区物料">模板分区物料</a-select-option>
            <a-select-option value="物料">物料</a-select-option>
            <a-select-option value="物料盘点规则">物料盘点规则</a-select-option>
            <a-select-option value="认证">认证</a-select-option>
            <a-select-option value="门店">门店</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="8" :md="6">
          <div class="filter-label">操作类型</div>
          <a-select v-model:value="filters.operation" placeholder="全部操作" allow-clear style="width: 100%">
            <a-select-option value="新增">新增</a-select-option>
            <a-select-option value="编辑">编辑</a-select-option>
            <a-select-option value="删除">删除</a-select-option>
            <a-select-option value="启停">启停</a-select-option>
            <a-select-option value="提交">提交</a-select-option>
          </a-select>
        </a-col>
      </a-row>
      <div class="filter-actions">
        <a-button @click="handleReset">重置</a-button>
        <a-button type="primary" @click="handleSearch">查询</a-button>
      </div>
    </a-card>

    <a-card class="table-card" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="records"
        :pagination="tablePagination"
        :loading="loading"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'red'">
              {{ record.status === 1 ? '成功' : '失败' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'description'">
            <span :title="record.description">{{ truncate(record.description, 60) }}</span>
          </template>
          <template v-else-if="column.key === 'errorMsg'">
            <span v-if="record.errorMsg" class="error-msg">{{ truncate(record.errorMsg, 40) }}</span>
            <span v-else class="text-muted">--</span>
          </template>
          <template v-else>
            {{ record[column.dataIndex] }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import LogModuleTabs from '../../components/LogModuleTabs.vue'
import { fetchOperationLogs } from '../../api/log'

const loading = ref(false)
const records = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })

const filters = reactive({
  username: '',
  source: '' as string,
  module: '' as string,
  operation: '' as string,
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '用户名', dataIndex: 'username', key: 'username', width: 120, ellipsis: true },
  { title: '模块', dataIndex: 'module', key: 'module', width: 100 },
  { title: '操作', dataIndex: 'operation', key: 'operation', width: 100 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: 'IP', dataIndex: 'requestIp', key: 'requestIp', width: 140 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
  { title: '错误信息', dataIndex: 'errorMsg', key: 'errorMsg', width: 120 },
  { title: '操作时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
]

const tablePagination = ref({
  current: pagination.current,
  pageSize: pagination.pageSize,
  total: pagination.total,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
})

function truncate(s: string, n: number) {
  if (!s) return ''
  return s.length > n ? s.substring(0, n) + '...' : s
}

async function loadData(page = 1, size = 20) {
  loading.value = true
  try {
    const res = await fetchOperationLogs({
      page,
      size,
      username: filters.username || undefined,
      source: filters.source || undefined,
      module: filters.module || undefined,
      operation: filters.operation || undefined,
    })
    const data = res.data || res
    records.value = data.records || []
    pagination.current = data.current || page
    pagination.pageSize = data.size || size
    // searchCount=false 时 total=0，根据当前页数据估算
    pagination.total = (data.records?.length || 0) === size ? page * size + 1 : (page - 1) * size + (data.records?.length || 0)
    tablePagination.value.current = pagination.current
    tablePagination.value.pageSize = pagination.pageSize
    tablePagination.value.total = pagination.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData(1, pagination.pageSize)
}

function handleReset() {
  filters.username = ''
  filters.source = ''
  filters.module = ''
  filters.operation = ''
  loadData(1, pagination.pageSize)
}

function handleTableChange(pag: any) {
  loadData(pag.current, pag.pageSize)
}

onMounted(() => loadData())
</script>

<style scoped>
.log-page { max-width: 1400px; }

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 4px;
  color: #111827;
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 4px;
}

.filter-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 16px;
}

.table-card { overflow-x: auto; }

.text-muted { color: #d1d5db; }
.error-msg { color: #ef4444; font-size: 12px; }
</style>
