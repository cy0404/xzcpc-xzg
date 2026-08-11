<template>
  <div class="log-page">
    <LogModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">登录日志</h1>
        <p class="page-subtitle">查看小程序端用户的登录、登出、绑定门店记录</p>
      </div>
    </div>

    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="8" :md="6">
          <div class="filter-label">用户名</div>
          <a-input v-model:value="filters.username" placeholder="输入用户名搜索" allow-clear @pressEnter="handleSearch" />
        </a-col>
        <a-col :xs="24" :sm="8" :md="6">
          <div class="filter-label">登录类型</div>
          <a-select v-model:value="filters.loginType" placeholder="全部类型" allow-clear style="width: 100%">
            <a-select-option value="login">登录</a-select-option>
            <a-select-option value="logout">登出</a-select-option>
            <a-select-option value="bind_store">绑定门店</a-select-option>
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
          <template v-else-if="column.key === 'loginType'">
            <a-tag>{{ loginTypeLabel(record.loginType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'failReason'">
            <span v-if="record.failReason" class="error-msg">{{ record.failReason }}</span>
            <span v-else class="text-muted">--</span>
          </template>
          <template v-else-if="column.key === 'userAgent'">
            <span :title="record.userAgent">{{ truncate(record.userAgent, 30) }}</span>
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
import { fetchLoginLogs } from '../../api/log'

const loading = ref(false)
const records = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })

const filters = reactive({
  username: '',
  loginType: '' as string,
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '用户名', dataIndex: 'username', key: 'username', width: 130, ellipsis: true },
  { title: 'OpenID', dataIndex: 'openid', key: 'openid', width: 220, ellipsis: true },
  { title: '类型', dataIndex: 'loginType', key: 'loginType', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
  { title: '失败原因', dataIndex: 'failReason', key: 'failReason', width: 150 },
  { title: 'IP', dataIndex: 'requestIp', key: 'requestIp', width: 140 },
  { title: '客户端', dataIndex: 'userAgent', key: 'userAgent', width: 150 },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
]

const loginTypeMap: Record<string, string> = {
  login: '登录',
  logout: '登出',
  bind_store: '绑定门店',
}

function loginTypeLabel(type: string) {
  return loginTypeMap[type] || type
}

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
    const res = await fetchLoginLogs({
      page,
      size,
      username: filters.username || undefined,
      loginType: filters.loginType || undefined,
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
  filters.loginType = ''
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

.filter-card { margin-bottom: 16px; }

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
