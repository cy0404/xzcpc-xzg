<template>
  <div class="settings-page">
    <div class="settings-tabs">
      <button
        type="button"
        class="settings-tab"
        :class="{ active: activeTab === 'permissions' }"
        @click="switchTab('permissions')"
      >
        权限管理
      </button>
      <button
        type="button"
        class="settings-tab"
        :class="{ active: activeTab === 'feishu' }"
        @click="switchTab('feishu')"
      >
        用户列表
      </button>
    </div>

    <div class="page-title-row">
      <div>
        <h1 class="page-title">系统设置</h1>
        <p class="page-subtitle">通过飞书授权登录，管理总部端用户、角色与数据访问范围</p>
      </div>
      <a-tag color="green">飞书已授权</a-tag>
    </div>

    <template v-if="activeTab === 'permissions'">
      <a-row :gutter="[16, 16]" class="stats-row">
        <a-col :xs="24" :sm="12" :lg="6">
          <div class="summary-card">
            <div class="summary-label">总部端用户</div>
            <div class="summary-value">{{ overview.totalUsers }}</div>
            <div class="summary-desc">均通过飞书授权</div>
          </div>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <div class="summary-card">
            <div class="summary-label">角色总量</div>
            <div class="summary-value">{{ overview.roleCount }}</div>
            <div class="summary-desc">总部管理员、财务、人事、运营</div>
          </div>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <div class="summary-card">
            <div class="summary-label">数据范围</div>
            <div class="summary-value">{{ overview.permissionScopes }}</div>
            <div class="summary-desc">部门店与模块授权</div>
          </div>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <div class="summary-card">
            <div class="summary-label">最近同步</div>
            <div class="summary-value summary-time">{{ overview.latestSync }}</div>
            <div class="summary-desc">来自飞书组织架构</div>
          </div>
        </a-col>
      </a-row>

      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :lg="24">
          <a-card title="角色与模块权限" :bordered="false" class="settings-card">
            <div
              v-for="role in roleCards"
              :key="role.value"
              class="role-card"
              @click="goFeishu(role.value)"
            >
              <div>
                <div class="role-title">{{ role.label }}</div>
                <div class="role-desc">{{ role.desc }}</div>
              </div>
              <a-tag color="green">{{ role.scope }}</a-tag>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </template>

    <template v-else>
      <a-card class="filter-card" :bordered="false">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :md="8">
            <div class="filter-label">姓名</div>
            <a-input
              v-model:value="filters.name"
              placeholder="输入姓名搜索"
              allow-clear
              @pressEnter="handleSearch"
            />
          </a-col>
          <a-col :xs="24" :md="8">
            <div class="filter-label">角色</div>
            <a-select v-model:value="filters.role" placeholder="全部角色" allow-clear style="width: 100%">
              <a-select-option v-for="item in roleOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-select-option>
            </a-select>
          </a-col>
          <a-col :xs="24" :md="8" class="filter-actions-col">
            <a-button @click="handleReset">重置</a-button>
            <a-button class="btn-query" @click="handleSearch">查询</a-button>
          </a-col>
        </a-row>
      </a-card>

      <a-card class="table-card" :bordered="false">
        <a-table
          :columns="columns"
          :data-source="users"
          :pagination="tablePagination"
          :loading="loading"
          row-key="id"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'role'">
              <a-tag v-for="r in parseRoles(record.role)" :key="r" color="green" style="margin-right: 4px; margin-bottom: 2px;">
                {{ getRoleLabel(r) }}
              </a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-button
                type="link"
                class="authorize-btn"
                :disabled="!canAuthorize"
                @click="openAuthorize(record)"
              >
                授权
              </a-button>
            </template>
          </template>
        </a-table>
      </a-card>
    </template>

    <a-modal
      v-model:open="authorizeOpen"
      title="飞书用户授权"
      ok-text="授权"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="submitAuthorize"
    >
      <div class="modal-user-name">{{ currentUser?.name || '-' }}</div>
      <a-select v-model:value="authorizeRoles" mode="multiple" style="width: 100%" placeholder="选择角色（可多选）">
        <a-select-option v-for="item in roleOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </a-select-option>
      </a-select>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getUser, parseRoles } from '../../utils/auth'
import {
  ROLE_OPTIONS,
  getPermissionOverview,
  getPermissions,
  getRoleLabel,
  updatePermissionRole,
} from '../../api/permission'

const route = useRoute()
const router = useRouter()
const activeTab = ref<'permissions' | 'feishu'>('permissions')
const loading = ref(false)
const saving = ref(false)
const authorizeOpen = ref(false)
const authorizeRoles = ref<string[]>([])
const currentUser = ref<any>(null)
const users = ref<any[]>([])
const roleOptions = ROLE_OPTIONS

const filters = reactive({
  name: '',
  role: '',
})

const overview = reactive({
  totalUsers: 0,
  roleCount: 0,
  permissionScopes: 8,
  latestSync: '09:30',
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const canAuthorize = computed(() => {
  const user = getUser()
  return user ? parseRoles(user.role).includes('headquarters_admin') : false
})

const roleCards = [
  { value: 'headquarters_admin', label: '总部管理员', desc: '可查看和管理全部门店、全部模块与系统设置', scope: '全量权限' },
  { value: 'finance_admin', label: '财务负责人', desc: '可查看支出明细、支出统计和相关数据报表', scope: '支出范围' },
  { value: 'hr_admin', label: '人事负责人', desc: '可查看员工列表、员工详情和人员统计', scope: '人员范围' },
  { value: 'operation_admin', label: '运营负责人', desc: '可查看任务、盘点管理和门店经营数据', scope: '运营范围' },
]

const columns = [
  { title: '姓名', dataIndex: 'name', key: 'name' },
  { title: '角色', dataIndex: 'role', key: 'role' },
  { title: '登录时间', dataIndex: 'lastLoginAt', key: 'lastLoginAt' },
  { title: '操作', key: 'action', width: 120 },
]

const tablePagination = computed(() => ({
  current: pagination.current,
  pageSize: pagination.pageSize,
  total: pagination.total,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条记录`,
}))

function syncFromRoute() {
  activeTab.value = route.query.tab === 'feishu' ? 'feishu' : 'permissions'
  filters.role = (route.query.role as string) || ''
}

function switchTab(tab: 'permissions' | 'feishu') {
  router.replace({ path: '/settings', query: tab === 'feishu' ? { tab } : {} })
}

function goFeishu(role: string) {
  router.replace({ path: '/settings', query: { tab: 'feishu', role } })
}

async function fetchOverview() {
  const res = (await getPermissionOverview()) as any
  Object.assign(overview, res.data || {})
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = (await getPermissions({
      name: filters.name.trim(),
      role: filters.role,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })) as any
    users.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchUsers()
}

function handleReset() {
  filters.name = ''
  filters.role = ''
  pagination.current = 1
  router.replace({ path: '/settings', query: { tab: 'feishu' } })
  fetchUsers()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchUsers()
}

function openAuthorize(record: any) {
  currentUser.value = record
  authorizeRoles.value = parseRoles(record.role)
  authorizeOpen.value = true
}

async function submitAuthorize() {
  if (!currentUser.value || authorizeRoles.value.length === 0) return
  saving.value = true
  try {
    await updatePermissionRole(currentUser.value.id, authorizeRoles.value)
    message.success('授权成功')
    authorizeOpen.value = false
    fetchUsers()
    fetchOverview()
  } catch (e: any) {
    message.error(e.message || '授权失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  syncFromRoute()
  fetchOverview()
  if (activeTab.value === 'feishu') fetchUsers()
})

watch(
  () => route.query,
  () => {
    syncFromRoute()
    pagination.current = 1
    if (activeTab.value === 'feishu') fetchUsers()
  },
)
</script>

<style scoped>
.settings-page {
  color: #111827;
}

.settings-tabs {
  display: flex;
  gap: 32px;
  margin-bottom: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.settings-tab {
  background: none;
  border: 0;
  padding: 0 0 12px;
  font-size: 15px;
  font-weight: 500;
  color: #6b7280;
  cursor: pointer;
  position: relative;
  margin-bottom: -1px;
}

.settings-tab.active {
  color: var(--primary, #0d7a3d);
  font-weight: 600;
}

.settings-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--primary, #0d7a3d);
  border-radius: 2px 2px 0 0;
}

.settings-tab:hover:not(.active) {
  color: #374151;
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

.stats-row,
.filter-card,
.table-card {
  margin-bottom: 16px;
}

.filter-card,
.table-card {
  border-radius: var(--radius, 8px);
}

.summary-card,
.settings-card {
  border-radius: 12px;
}

.summary-card {
  background: #fff;
  padding: 18px;
  box-shadow: var(--card-shadow);
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.summary-label,
.auth-rule-label {
  color: #6b7280;
  font-size: 13px;
}

.filter-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}

.summary-value {
  font-size: 26px;
  font-weight: 700;
  margin-top: 8px;
}

.summary-time {
  font-size: 22px;
}

.summary-desc {
  color: #9ca3af;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-desc {
  color: #9ca3af;
  margin-top: 6px;
}

.role-card,
.auth-rule {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid #f0f2f5;
  border-radius: 10px;
  margin-bottom: 12px;
  background: #fff;
}

.role-card {
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  align-items: center;
  min-height: 72px;
}

.role-card:hover {
  border-color: var(--primary, #0d7a3d);
  background: #f8fffb;
}

.role-title {
  font-weight: 600;
  font-size: 15px;
  line-height: 22px;
}

.auth-rule-value,
.modal-user-name {
  font-weight: 600;
}

.filter-actions-col {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  justify-content: flex-end;
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

.authorize-btn {
  padding: 0;
}

.modal-user-name {
  margin-bottom: 12px;
}
</style>
