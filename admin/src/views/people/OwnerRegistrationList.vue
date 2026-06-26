<template>
  <div class="people-page">
    <PeopleModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">门店绑定记录</h1>
        <p class="page-subtitle">查看老板/店长扫码登记记录，对未关联记录执行手动绑定</p>
      </div>
    </div>

    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">门店</div>
          <a-select v-model:value="filters.storeId" placeholder="全部门店" allow-clear show-search :filter-option="filterStore" style="width: 100%">
            <a-select-option v-for="store in stores" :key="store.id" :value="store.id" :label="store.name">
              {{ store.name }}
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">状态</div>
          <a-select v-model:value="filters.status" placeholder="全部状态" allow-clear style="width: 100%">
            <a-select-option value="未关联">未关联</a-select-option>
            <a-select-option value="已绑定">已绑定</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">姓名</div>
          <a-input v-model:value="filters.name" placeholder="输入姓名" allow-clear @pressEnter="handleSearch" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">手机号</div>
          <a-input v-model:value="filters.phone" placeholder="输入手机号" allow-clear @pressEnter="handleSearch" />
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
        :scroll="{ x: 1200 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'openid'">
            <span>{{ maskOpenid(record.openid) }}</span>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === '已绑定' ? 'green' : 'orange'">{{ record.status }}</a-tag>
          </template>
          <template v-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-button
              v-if="record.status === '未关联'"
              type="link"
              size="small"
              @click="openBind(record)"
            >手动绑定</a-button>
            <a-button
              v-else
              type="link"
              size="small"
              @click="openEdit(record)"
            >编辑</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalVisible"
      :title="modalMode === 'bind' ? '手动绑定' : '编辑绑定信息'"
      :footer="null"
      :mask-closable="false"
    >
      <a-form layout="vertical">
        <a-form-item label="门店">
          <a-input :value="current?.storeName" disabled />
        </a-form-item>
        <a-form-item label="OpenID">
          <a-input :value="maskOpenid(current?.openid || '')" disabled />
        </a-form-item>
        <a-form-item label="姓名" required>
          <a-input v-model:value="form.name" placeholder="请输入姓名" />
        </a-form-item>
        <a-form-item label="手机号" required>
          <a-input v-model:value="form.phone" placeholder="请输入手机号" />
        </a-form-item>
        <a-form-item label="角色" required>
          <a-select v-model:value="form.role" style="width: 100%">
            <a-select-option value="老板">老板</a-select-option>
            <a-select-option value="店长">店长</a-select-option>
          </a-select>
        </a-form-item>
        <a-alert
          v-if="form.role === '老板'"
          type="info"
          show-icon
          message="老板角色将更新门店 owner 信息并写入员工表；每个门店仅允许一位老板。"
          style="margin-bottom: 8px"
        />
        <a-alert
          v-else
          type="info"
          show-icon
          message="店长角色仅创建/更新员工记录，不修改门店 owner 信息。"
        />
      </a-form>
      <div class="modal-footer">
        <a-button @click="modalVisible = false">取消</a-button>
        <a-button type="primary" :loading="saving" @click="handleSubmit">确认</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import PeopleModuleTabs from '../../components/PeopleModuleTabs.vue'
import {
  getOwnerRegistrations,
  bindOwnerRegistration,
  updateOwnerRegistration,
  type OwnerRegistration,
} from '../../api/ownerRegistration'
import { getStores } from '../../api/store'

type StoreOption = { id: string; name: string }

const loading = ref(false)
const saving = ref(false)
const records = ref<OwnerRegistration[]>([])
const stores = ref<StoreOption[]>([])
const modalVisible = ref(false)
const modalMode = ref<'bind' | 'edit'>('bind')
const current = ref<OwnerRegistration | null>(null)

const form = reactive({
  name: '',
  phone: '',
  role: '老板',
})

const filters = reactive({
  storeId: undefined as string | undefined,
  status: undefined as string | undefined,
  name: '',
  phone: '',
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const columns = [
  { title: '姓名', dataIndex: 'name', key: 'name', width: 100 },
  { title: '手机号', dataIndex: 'phone', key: 'phone', width: 130 },
  { title: '角色', dataIndex: 'role', key: 'role', width: 80 },
  { title: '门店', dataIndex: 'storeName', key: 'storeName', width: 160, ellipsis: true },
  { title: '门店ID', dataIndex: 'storeId', key: 'storeId', width: 120, ellipsis: true },
  { title: '状态', key: 'status', width: 90 },
  { title: '登记时间', key: 'createdAt', width: 160 },
  { title: 'OpenID', key: 'openid', width: 160 },
  { title: '绑定码', dataIndex: 'bindCode', key: 'bindCode', width: 120, ellipsis: true },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const },
]

const tablePagination = computed(() => ({
  current: pagination.current,
  pageSize: pagination.pageSize,
  total: pagination.total,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
}))

function maskOpenid(openid?: string) {
  if (!openid) return '--'
  if (openid.length <= 8) return openid
  return `${openid.slice(0, 4)}****${openid.slice(-4)}`
}

function formatTime(val?: string) {
  if (!val) return '--'
  return val.replace('T', ' ').slice(0, 19)
}

function filterStore(input: string, option: any) {
  return (option.label || option.value || '').toLowerCase().includes(input.toLowerCase())
}

async function loadStores() {
  try {
    const res: any = await getStores()
    const list = res.data || []
    stores.value = list.map((s: any) => ({
      id: s.storeId || s.id,
      name: s.storeName || s.mendianmingcheng || s.name,
    }))
  } catch { /* ignore */ }
}

async function loadRecords() {
  loading.value = true
  try {
    const res: any = await getOwnerRegistrations({
      storeId: filters.storeId,
      status: filters.status,
      name: filters.name || undefined,
      phone: filters.phone || undefined,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })
    records.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadRecords()
}

function handleReset() {
  filters.storeId = undefined
  filters.status = undefined
  filters.name = ''
  filters.phone = ''
  handleSearch()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadRecords()
}

function openBind(record: OwnerRegistration) {
  modalMode.value = 'bind'
  current.value = record
  form.name = record.name
  form.phone = record.phone
  form.role = record.role || '老板'
  modalVisible.value = true
}

function openEdit(record: OwnerRegistration) {
  modalMode.value = 'edit'
  current.value = record
  form.name = record.name
  form.phone = record.phone
  form.role = record.role || '店长'
  modalVisible.value = true
}

function handleSubmit() {
  if (!current.value) return
  if (!form.name.trim()) {
    message.warning('请输入姓名')
    return
  }
  if (!form.phone.trim()) {
    message.warning('请输入手机号')
    return
  }
  const payload = {
    name: form.name.trim(),
    phone: form.phone.trim(),
    role: form.role,
  }
  const title = modalMode.value === 'bind' ? '确认手动绑定' : '确认保存修改'
  const content = modalMode.value === 'bind'
    ? `确定为 ${current.value.storeName} 绑定 ${payload.name}（${payload.role}）吗？`
    : `确定更新 ${current.value.storeName} 的绑定信息吗？`
  Modal.confirm({
    title,
    content,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      saving.value = true
      try {
        if (modalMode.value === 'bind') {
          await bindOwnerRegistration(current.value!.id, payload)
          message.success('绑定成功')
        } else {
          await updateOwnerRegistration(current.value!.id, payload)
          message.success('保存成功')
        }
        modalVisible.value = false
        await loadRecords()
      } finally {
        saving.value = false
      }
    },
  })
}

onMounted(async () => {
  await loadStores()
  await loadRecords()
})
</script>

<style scoped>
.people-page {
  padding: 0;
}

.page-title-row {
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #111827;
}

.page-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: #6b7280;
}

.filter-card,
.table-card {
  margin-bottom: 16px;
  border-radius: 12px;
}

.filter-label {
  margin-bottom: 8px;
  font-size: 13px;
  color: #6b7280;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
</style>
