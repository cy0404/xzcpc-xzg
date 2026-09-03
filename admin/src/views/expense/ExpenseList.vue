<template>
  <div class="expense-page">
    <ExpenseModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">支出明细列表</h1>
        <p class="page-subtitle">跨门店查询支出记录，核对类型、金额、经手人与凭证</p>
      </div>
    </div>

    <!-- Tab 切换 -->
    <a-tabs v-model:activeKey="activeTab" class="expense-tabs">
      <a-tab-pane key="expense" tab="普通支出" />
      <a-tab-pane key="selfPurchase" tab="自购成本" />
    </a-tabs>

    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">门店选择</div>
          <a-select
            v-model:value="filters.storeIds"
            mode="multiple"
            placeholder="全部门店"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 100%"
            :max-tag-count="10"
          >
            <a-select-option v-for="store in stores" :key="store.id" :value="store.id" :label="store.name">
              {{ store.name }}
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="4">
          <div class="filter-label">督导</div>
          <a-select v-model:value="filters.supervisorName" placeholder="全部督导" style="width: 100%" allow-clear :options="supervisorOptions" @change="handleSearch" />
        </a-col>
        <a-col v-if="activeTab === 'expense'" :xs="24" :sm="12" :md="5">
          <div class="filter-label">支出类型</div>
          <a-select v-model:value="filters.typeId" placeholder="全部类型" allow-clear style="width: 100%">
            <a-select-option v-for="type in expenseTypes" :key="type.typeId" :value="type.typeId">
              {{ type.name }}
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">经手人</div>
          <a-input v-model:value="filters.handlerName" placeholder="输入姓名搜索" allow-clear @pressEnter="handleSearch" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">日期范围</div>
          <a-range-picker v-model:value="filters.dateRange" style="width: 100%" format="YYYY-MM-DD" />
        </a-col>
      </a-row>
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:8px">
        <a-button @click="handleReset">重置</a-button>
        <a-button type="primary" @click="handleSearch">查询</a-button>
      </div>
    </a-card>

    <a-card class="table-card" :bordered="false" :key="activeTab">
      <a-table
        :columns="activeColumns"
        :data-source="records"
        :pagination="tablePagination"
        :loading="loading"
        :scroll="{ x: 1400 }"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <!-- 普通支出：支出项目/类型 -->
          <template v-if="column.key === 'typeName'">
            <span v-if="record.firstTypeName" class="item-name">{{ record.firstTypeName }}</span>
            <a-tag :color="getTypeColor(record.typeName)" :style="record.firstTypeName ? 'margin-left:8px' : ''">{{ record.typeName }}</a-tag>
          </template>
          <!-- 自购成本：支出项目/类型 -->
          <template v-else-if="column.key === 'selfPurchaseType'">
            <span class="item-name">{{ record.materialName }}</span>
            <a-tag color="blue" style="margin-left:8px">自购成本</a-tag>
          </template>
          <!-- 自购成本：重量 -->
          <template v-else-if="column.key === 'weight'">
            {{ record.purchaseQty }}{{ record.unit }}
          </template>
          <!-- 自购成本：单价 -->
          <template v-else-if="column.key === 'unitPrice'">
            <span v-if="record.unitPrice" class="money">¥{{ formatAmount(record.unitPrice) }}</span>
            <span v-else>--</span>
          </template>
          <!-- 金额 -->
          <template v-else-if="column.key === 'amount'">
            <span class="money">{{ activeTab === 'selfPurchase' ? formatMoney(record.totalAmount) : formatMoney(record.amount) }}</span>
          </template>
          <!-- 产生日期 -->
          <template v-else-if="column.key === 'occurredDate'">
            {{ activeTab === 'selfPurchase' ? (record.purchaseDate || '--') : record.occurredDate }}
          </template>
          <!-- 凭证 -->
          <template v-else-if="column.key === 'voucher'">
            <a-button type="link" size="small" @click="openVoucher(record)">查看凭证</a-button>
          </template>
          <template v-else>
            {{ record[column.dataIndex] }}
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="voucherOpen" title="支出凭证" :footer="null" width="640px">
      <div class="voucher-preview">
        <img
          v-if="currentVoucher?.voucherUrl"
          :src="currentVoucher.voucherUrl"
          class="voucher-img"
          @error="onVoucherImgError"
        />
        <div v-else class="voucher-placeholder">暂无凭证图片</div>
        <p class="voucher-info">{{ voucherInfoText }}</p>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import { useRoute } from 'vue-router'
import ExpenseModuleTabs from '../../components/ExpenseModuleTabs.vue'
import { getExpenseRecords, getExpenseTypes } from '../../api/expense'
import { getStores } from '../../api/store'
import { getSupervisorOptions } from '../../api/supervisor'
import api from '../../api/index'

type StoreOption = { id: string; name: string }
type ExpenseType = { id: number; typeId: string; name: string; description?: string; status: string }
type ExpenseRecord = {
  id: number
  expenseId?: string
  storeId: string
  storeMiniappNo?: string
  storeName: string
  typeId: string
  firstTypeName: string
  typeName: string
  remark?: string
  amount: number
  occurredDate: string
  handlerName: string
  voucherUrl?: string
}

const activeTab = ref<'expense' | 'selfPurchase'>('expense')
const loading = ref(false)
const voucherOpen = ref(false)
const currentVoucher = ref<any>(null)
const records = ref<any[]>([])
const stores = ref<StoreOption[]>([])
const expenseTypes = ref<ExpenseType[]>([])

const route = useRoute()

const filters = reactive({
  storeIds: [] as string[],
  supervisorName: '' as string,
  typeId: undefined as string | undefined,
  dateRange: [] as Dayjs[],
  handlerName: '',
})
const supervisorOptions = ref<{ label: string; value: string }[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const rowKey = computed(() => activeTab.value === 'selfPurchase' ? 'id' : 'id')

const expenseColumns = [
  { title: '门店名称', dataIndex: 'storeName', key: 'storeName', fixed: 'left' as const, width: 160 },
  { title: '督导', dataIndex: 'supervisorName', key: 'supervisorName', width: 100 },
  { title: '支出项目 / 类型', dataIndex: 'typeName', key: 'typeName', width: 220 },
  { title: '说明', dataIndex: 'remark', key: 'remark', width: 200, ellipsis: true },
  { title: '金额', dataIndex: 'amount', key: 'amount', width: 140, align: 'right' as const },
  { title: '产生日期', dataIndex: 'occurredDate', key: 'occurredDate', width: 140 },
  { title: '经手人', dataIndex: 'handlerName', key: 'handlerName', width: 120 },
  { title: '凭证缩略图', key: 'voucher', width: 140 },
]

const selfPurchaseColumns = [
  { title: '门店名称', dataIndex: 'storeName', key: 'storeName', fixed: 'left' as const, width: 160 },
  { title: '督导', dataIndex: 'supervisorName', key: 'supervisorName', width: 100 },
  { title: '支出项目 / 类型', key: 'selfPurchaseType', width: 200 },
  { title: '重量', key: 'weight', width: 100 },
  { title: '单价(kg)', key: 'unitPrice', width: 100, align: 'right' as const },
  { title: '总价', dataIndex: 'totalAmount', key: 'amount', width: 120, align: 'right' as const },
  { title: '说明', dataIndex: 'remark', key: 'remark', width: 160, ellipsis: true },
  { title: '产生日期', dataIndex: 'purchaseDate', key: 'occurredDate', width: 140 },
  { title: '经手人', dataIndex: 'handlerName', key: 'handlerName', width: 120 },
  { title: '凭证缩略图', key: 'voucher', width: 140 },
]

const activeColumns = computed(() => activeTab.value === 'selfPurchase' ? selfPurchaseColumns : expenseColumns)

const voucherInfoText = computed(() => {
  const v = currentVoucher.value
  if (!v) return ''
  if (activeTab.value === 'selfPurchase') {
    return `${v.storeName || ''} / 自购成本 / ${formatMoney(v.totalAmount || 0)}`
  }
  return `${v.storeName || ''} / ${v.typeName || ''} / ${formatMoney(v.amount || 0)}`
})

const tablePagination = computed(() => ({
  current: pagination.current,
  pageSize: pagination.pageSize,
  total: pagination.total,
  showTotal: (total: number) => `共 ${total} 条记录`,
}))

const fallbackStores: StoreOption[] = [
  { id: 's001', name: '南山万象店' },
  { id: 's002', name: '福田中心城店' },
  { id: 's003', name: '罗湖万象城店' },
  { id: 's004', name: '宝安壹方城店' },
]

const fallbackTypes: ExpenseType[] = [
  { id: 1, typeId: 'ET00000001', name: '物料采购', status: 'enabled' },
  { id: 2, typeId: 'ET00000002', name: '设备维护', status: 'enabled' },
  { id: 3, typeId: 'ET00000003', name: '门店杂费', status: 'enabled' },
  { id: 4, typeId: 'ET00000004', name: '租金', status: 'enabled' },
]

const fallbackRecords: ExpenseRecord[] = [
  { id: 1, expenseId: 'EX00000001', storeId: 's001', storeMiniappNo: 'mp_store_001', storeName: '南山万象店', typeId: 'ET00000001', firstTypeName: '日常支出', typeName: '物料采购', remark: '月度常规物料补货', amount: 8420, occurredDate: '2026-04-28', handlerName: '张明' },
  { id: 2, expenseId: 'EX00000002', storeId: 's002', storeMiniappNo: 'mp_store_002', storeName: '福田中心城店', typeId: 'ET00000002', firstTypeName: '设备支出', typeName: '设备维护', remark: '空调季度保养', amount: 1260, occurredDate: '2026-04-21', handlerName: '李娜' },
  { id: 3, expenseId: 'EX00000003', storeId: 's003', storeMiniappNo: 'mp_store_003', storeName: '罗湖万象城店', typeId: 'ET00000003', firstTypeName: '日常支出', typeName: '门店杂费', amount: 680, occurredDate: '2026-04-16', handlerName: '陈伟' },
  { id: 4, expenseId: 'EX00000004', storeId: 's004', storeMiniappNo: 'mp_store_004', storeName: '宝安壹方城店', typeId: 'ET00000001', firstTypeName: '日常支出', typeName: '物料采购', remark: '紧急补货-生鲜类', amount: 6950, occurredDate: '2026-04-12', handlerName: '周怡' },
]

function formatMoney(value: number) {
  return `¥${Number(value || 0).toLocaleString()}`
}

function formatAmount(value: number) {
  return Number(value || 0).toLocaleString()
}

function getTypeColor(typeName: string) {
  if (!typeName) return 'green'
  if (typeName.includes('维护')) return 'orange'
  if (typeName.includes('杂费')) return 'default'
  return 'green'
}

function normalizeStore(raw: any): StoreOption {
  return {
    id: raw.id || raw.storeId,
    name: raw.mendianmingcheng || raw.storeName || raw.name || raw.id,
  }
}

function getFilteredFallback() {
  return fallbackRecords.filter((item) => {
    if (filters.storeIds.length && !filters.storeIds.includes(item.storeId)) return false
    if (filters.typeId && String(item.typeId) !== filters.typeId) return false
    if (filters.handlerName && !item.handlerName.includes(filters.handlerName.trim())) return false
    return true
  })
}

async function fetchOptions() {
  try {
    const storeRes = (await getStores()) as any
    stores.value = (storeRes.data || []).map(normalizeStore)
  } catch {
    stores.value = fallbackStores
  }

  try {
    const typeRes = (await getExpenseTypes({ status: 'enabled' })) as any
    const typeRecords = typeRes.data?.records || typeRes.data || fallbackTypes
    expenseTypes.value = typeRecords.map((item: any) => ({
      id: item.id,
      typeId: item.typeId || item.type_id || String(item.id),
      name: item.name,
      description: item.description,
      status: item.status || 'enabled',
    }))
  } catch {
    expenseTypes.value = fallbackTypes
  }
}

async function fetchExpenseRecords() {
  loading.value = true
  try {
    const params = {
      storeId: filters.storeIds.join(','),
      supervisorName: filters.supervisorName,
      typeId: filters.typeId,
      startDate: filters.dateRange?.[0]?.format('YYYY-MM-DD'),
      endDate: filters.dateRange?.[1]?.format('YYYY-MM-DD'),
      handlerName: filters.handlerName.trim(),
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    }
    const res = (await getExpenseRecords(params)) as any
    records.value = res.data?.records || res.data || []
    pagination.total = res.data?.total || records.value.length
  } catch {
    const filtered = getFilteredFallback()
    records.value = filtered
    pagination.total = filtered.length
  } finally {
    loading.value = false
  }
}

async function fetchSelfPurchaseRecords() {
  loading.value = true
  try {
    const params: any = {
      storeIds: filters.storeIds.join(','),
      supervisorName: filters.supervisorName,
      startDate: filters.dateRange?.[0]?.format('YYYY-MM-DD') || '',
      endDate: filters.dateRange?.[1]?.format('YYYY-MM-DD') || '',
      handlerName: filters.handlerName.trim(),
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    }
    const res = (await api.get('/self-purchase-materials', { params })) as any
    records.value = res.data?.records || res.data || []
    pagination.total = res.data?.total || records.value.length
  } catch {
    records.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function fetchRecords() {
  if (activeTab.value === 'selfPurchase') {
    fetchSelfPurchaseRecords()
  } else {
    fetchExpenseRecords()
  }
}

watch(activeTab, () => {
  records.value = []
  pagination.total = 0
  handleReset()
})

function handleSearch() {
  pagination.current = 1
  fetchRecords()
}

function handleReset() {
  filters.storeIds = []
  filters.supervisorName = ''
  filters.typeId = undefined
  filters.dateRange = []
  filters.handlerName = ''
  pagination.current = 1
  fetchRecords()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchRecords()
}

function openVoucher(record: any) {
  currentVoucher.value = record
  voucherOpen.value = true
}

function onVoucherImgError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
  const placeholder = img.parentElement?.querySelector('.voucher-placeholder') as HTMLElement | null
  if (placeholder) {
    placeholder.style.display = 'flex'
    placeholder.textContent = '图片加载失败'
  }
}

async function fetchSupervisors() {
  try { const res: any = await getSupervisorOptions(); supervisorOptions.value = res.data || [] } catch { /* */ }
}

onMounted(async () => {
  // 支持卡片/外部链接带参直达：/#/expense?startDate=…&endDate=…（与 LossList 同款，
  // 预警卡「查看支出明细」按钮经 302 中介跳转后带上周日期自动查询；不带参则查全部）
  const q = route.query
  const qv = (v: unknown) => (Array.isArray(v) ? (v[0] || '') : (v || '')) as string
  const startDate = qv(q.startDate)
  const endDate = qv(q.endDate)
  if (startDate && endDate && dayjs(startDate).isValid() && dayjs(endDate).isValid()) {
    filters.dateRange = [dayjs(startDate), dayjs(endDate)]
  }
  await fetchOptions()
  await fetchSupervisors()
  await fetchRecords()
})
</script>

<style scoped>
.expense-page {
  max-width: 1280px;
}

.expense-tabs {
  margin-bottom: 0;
}

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
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

.item-name { font-weight: 600; color: #1f2421; }
.money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.voucher-preview {
  text-align: center;
}

.voucher-img {
  max-width: 100%;
  max-height: 480px;
  border-radius: 8px;
  object-fit: contain;
}

.voucher-info {
  margin-top: 12px;
  color: #6b7280;
  font-size: 13px;
}

.voucher-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 260px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  background: #f9fafb;
  color: #6b7280;
}

:deep(.ant-select-selection-overflow) {
  flex-wrap: nowrap !important;
  overflow-x: auto;
}
</style>
