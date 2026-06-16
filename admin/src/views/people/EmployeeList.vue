<template>
  <div class="people-page">
    <PeopleModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">员工列表</h1>
        <p class="page-subtitle">跨门店查询员工信息，查看岗位、入职日期和在职状态</p>
      </div>
    </div>

    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">门店选择</div>
          <a-select v-model:value="filters.storeId" placeholder="全部门店" allow-clear style="width: 100%">
            <a-select-option v-for="store in stores" :key="store.id" :value="store.id">
              {{ store.name }}
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">岗位角色</div>
          <a-select v-model:value="filters.role" placeholder="全部岗位" allow-clear style="width: 100%">
            <a-select-option v-for="role in roleOptions" :key="role" :value="role">{{ role }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">在职/离职状态</div>
          <a-select v-model:value="filters.status" placeholder="全部状态" allow-clear style="width: 100%">
            <a-select-option value="在职">在职</a-select-option>
            <a-select-option value="离职">离职</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">员工姓名</div>
          <a-input v-model:value="filters.name" placeholder="输入姓名搜索" allow-clear @pressEnter="handleSearch" />
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
        :data-source="employees"
        :pagination="tablePagination"
        :loading="loading"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a @click="goDetail(record)">{{ record.name }}</a>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === '在职' ? 'green' : 'default'">{{ record.status }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="goDetail(record)">查看</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PeopleModuleTabs from '../../components/PeopleModuleTabs.vue'
import { getEmployees } from '../../api/people'
import { getStores } from '../../api/store'

type StoreOption = { id: string; name: string }
type Employee = {
  id: number
  employeeId: string
  name: string
  storeId: string
  storeMiniappNo?: string
  storeName: string
  role: string
  entryDate: string
  status: string
}

const router = useRouter()
const loading = ref(false)
const employees = ref<Employee[]>([])
const stores = ref<StoreOption[]>([])
const roleOptions = ['店长', '咖啡师', '值班主管', '兼职店员']

const filters = reactive({
  storeId: undefined as string | undefined,
  role: undefined as string | undefined,
  status: undefined as string | undefined,
  name: '',
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const fallbackStores: StoreOption[] = [
  { id: 's001', name: '南山万象店' },
  { id: 's002', name: '福田中心城店' },
  { id: 's003', name: '罗湖万象城店' },
  { id: 's004', name: '宝安壹方城店' },
]

const fallbackEmployees: Employee[] = [
  { id: 1, employeeId: 'EMP00000001', name: '王小丽', storeId: 's001', storeMiniappNo: 'mp_store_001', storeName: '南山万象店', role: '店长', entryDate: '2024-08-12', status: '在职' },
  { id: 2, employeeId: 'EMP00000002', name: '张明', storeId: 's002', storeMiniappNo: 'mp_store_002', storeName: '福田中心城店', role: '咖啡师', entryDate: '2025-02-18', status: '在职' },
  { id: 3, employeeId: 'EMP00000003', name: '李娜', storeId: 's003', storeMiniappNo: 'mp_store_003', storeName: '罗湖万象城店', role: '值班主管', entryDate: '2023-11-03', status: '在职' },
  { id: 4, employeeId: 'EMP00000004', name: '陈伟', storeId: 's004', storeMiniappNo: 'mp_store_004', storeName: '宝安壹方城店', role: '兼职店员', entryDate: '2024-05-21', status: '离职' },
]

const columns = [
  { title: '姓名', dataIndex: 'name', key: 'name' },
  { title: '所属门店', dataIndex: 'storeName', key: 'storeName' },
  { title: '岗位', dataIndex: 'role', key: 'role', width: 140 },
  { title: '入职日期', dataIndex: 'entryDate', key: 'entryDate', width: 140 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 100 },
]

const tablePagination = computed(() => ({
  current: pagination.current,
  pageSize: pagination.pageSize,
  total: pagination.total,
  showTotal: (total: number) => `共 ${total} 名员工`,
}))

function normalizeStore(raw: any): StoreOption {
  return {
    id: raw.id || raw.storeId,
    name: raw.mendianmingcheng || raw.storeName || raw.name || raw.id,
  }
}

function normalizeEmployee(raw: any): Employee {
  return {
    id: raw.id,
    employeeId: raw.employeeId || raw.employee_id || String(raw.id),
    name: raw.name,
    storeId: raw.storeId,
    storeMiniappNo: raw.storeMiniappNo || raw.store_miniapp_no,
    storeName: raw.storeName,
    role: raw.role || raw.position,
    entryDate: raw.entryDate || raw.hireDate,
    status: raw.status || '在职',
  }
}

function getFilteredFallback() {
  return fallbackEmployees.filter((item) => {
    if (filters.storeId && item.storeId !== filters.storeId) return false
    if (filters.role && item.role !== filters.role) return false
    if (filters.status && item.status !== filters.status) return false
    if (filters.name && !item.name.includes(filters.name.trim())) return false
    return true
  })
}

async function fetchStores() {
  try {
    const res = (await getStores()) as any
    stores.value = (res.data || []).map(normalizeStore)
  } catch {
    stores.value = fallbackStores
  }
}

async function fetchEmployees() {
  loading.value = true
  try {
    const res = (await getEmployees({
      storeId: filters.storeId,
      role: filters.role,
      status: filters.status,
      name: filters.name.trim(),
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })) as any
    const records = res.data?.records || res.data || []
    employees.value = records.map(normalizeEmployee)
    pagination.total = res.data?.total || employees.value.length
  } catch {
    const filtered = getFilteredFallback()
    employees.value = filtered
    pagination.total = filtered.length
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchEmployees()
}

function handleReset() {
  filters.storeId = undefined
  filters.role = undefined
  filters.status = undefined
  filters.name = ''
  pagination.current = 1
  fetchEmployees()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchEmployees()
}

function goDetail(record: Employee) {
  router.push(`/people/${record.employeeId}`)
}

onMounted(async () => {
  await fetchStores()
  await fetchEmployees()
})
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

.filter-card {
  margin-bottom: 16px;
}

.filter-label {
  margin-bottom: 6px;
  color: #6b7280;
  font-size: 13px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>
