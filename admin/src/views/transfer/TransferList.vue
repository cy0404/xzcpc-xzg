<template>
  <div class="transfer-page">
    <div class="page-title-row">
      <div>
        <h1 class="page-title">调货台账</h1>
        <p class="page-subtitle">记录门店间直接调货流程。总部仅查看台账，不审批、不干预、不影响库存。</p>
      </div>
      <!-- <a-button @click="handleExport">导出调货记录</a-button> -->
    </div>

    <!-- 筛选 -->
    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">调出门店</div>
          <a-select v-model:value="filters.fromStoreId" placeholder="全部门店" style="width:100%" allow-clear show-search :field-names="{label:'mendianmingcheng',value:'id'}" :options="storeOptions" @change="fetchList" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">调入门店</div>
          <a-select v-model:value="filters.toStoreId" placeholder="全部门店" style="width:100%" allow-clear show-search :field-names="{label:'mendianmingcheng',value:'id'}" :options="storeOptions" @change="fetchList" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="4">
          <div class="filter-label">督导</div>
          <a-select v-model:value="filters.supervisorName" placeholder="全部督导" style="width:100%" allow-clear :options="supervisorOptions" @change="fetchList" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">是否完成调货</div>
          <a-select v-model:value="filters.status" placeholder="全部" style="width:100%" allow-clear @change="fetchList">
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="pending_confirm">待确认</a-select-option>
            <a-select-option value="completed">已完成</a-select-option>
            <a-select-option value="rejected">已拒绝</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">是否还货</div>
          <a-select v-model:value="filters.handoff" placeholder="全部" style="width:100%" allow-clear @change="fetchList">
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="pending_return">待还</a-select-option>
            <a-select-option value="returned">已还</a-select-option>
          </a-select>
        </a-col>
      </a-row>
      <a-row :gutter="[16, 16]" style="margin-top:12px">
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">关键词</div>
          <a-input v-model:value="filters.keyword" placeholder="编号/门店/物品" style="width:100%" allow-clear @press-enter="fetchList" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">日期范围</div>
          <a-range-picker v-model:value="dateRange" style="width:100%" @change="onDateChange" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="5" style="display:flex;align-items:flex-end;justify-content:flex-end;gap:8px">
          <a-button @click="resetFilters">重置</a-button>
          <a-button type="primary" @click="fetchList">查询</a-button>
        </a-col>
      </a-row>
    </a-card>

    <!-- 表格 -->
    <a-card :bordered="false">
      <a-table :columns="cols" :data-source="list" :loading="loading" :pagination="pagination" row-key="id" size="middle" :scroll="{ x: 1100 }" @change="handleTable">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-if="column.key === 'completed'">
            <a-tag :color="record.status === 'completed' ? 'green' : record.status === 'cancelled' || record.status === 'rejected' ? 'default' : 'orange'">{{ record.status === 'completed' ? '已完成' : record.status === 'cancelled' ? '已取消' : record.status === 'rejected' ? '已拒绝' : '进行中' }}</a-tag>
          </template>
          <template v-if="column.key === 'return'">
            <a-tag :color="record.handoff === 'returned' ? 'green' : 'orange'">{{ record.handoff === 'returned' ? '已还' : '待还' }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a @click="openDrawer(record)">查看</a>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 详情抽屉 -->
    <a-drawer v-model:open="drawerOpen" :width="480" placement="right" :closable="false">
      <template v-if="cur">
        <div class="d-head">
          <div class="d-head-top">
            <a-tag :color="statusColor(cur.status)">{{ statusLabel(cur.status) }}</a-tag>
            <span class="d-close" @click="drawerOpen = false">✕</span>
          </div>
          <div class="d-title" style="margin-top:12px">{{ curTitle }}</div>
          <div class="d-biz-code">{{ cur.bizCode || '--' }}</div>
        </div>
        <a-divider />

        <a-tabs v-model:activeKey="detailTabKey" size="small">
          <a-tab-pane key="transfer" tab="调拨详情">
            <div class="info-block subtle">
              <strong>台账说明</strong>
              <p style="margin:4px 0 0;color:#66706A;font-size:13px">{{ statusNote(cur.status) }}</p>
            </div>
            <div class="info-card">
              <div class="info-card-title">调货信息</div>
              <div class="info-row"><span class="info-label">调出门店</span><span class="info-value">{{ cur.fromStoreName || '--' }}</span></div>
              <div class="info-row"><span class="info-label">调入门店</span><span class="info-value">{{ cur.toStoreName || '--' }}</span></div>
              <div class="info-row"><span class="info-label">总数量</span><span class="info-value">{{ cur.totalQty }}</span></div>
              <div class="info-row"><span class="info-label">调货原因</span><span class="info-value">{{ cur.remark || '--' }}</span></div>
              <div class="info-row"><span class="info-label">发起人</span><span class="info-value">{{ cur.createdByName || cur.createdBy || '--' }}</span></div>
              <div class="info-row"><span class="info-label">更新时间</span><span class="info-value">{{ cur.updatedAt || cur.createdAt || '--' }}</span></div>
            </div>
            <div class="info-card" v-if="curItems && curItems.length">
              <div class="info-card-title">物料明细</div>
              <div class="info-row" v-for="item in curItems" :key="item.id" style="border-bottom:1px solid #EEF1EF;padding:8px 0;display:flex;justify-content:space-between;gap:12px">
                <span class="info-label" style="flex:1;width:auto;min-width:0">调拨物料：{{ item.materialName }}</span>
                <span class="info-value" style="flex:0 0 auto">{{ item.transferQty }}{{ item.unit || '件' }}</span>
              </div>
              <div class="info-row" style="padding:10px 0 0;display:flex;justify-content:space-between;font-weight:600">
                <span>总金额</span>
                <span style="color:#2F8F57">¥{{ totalTransferAmount }}</span>
              </div>
            </div>
            <div class="info-block">
              <strong>流程记录</strong>
              <div class="steps" style="margin-top:12px">
                <div class="step-row" v-for="(s, i) in curSteps" :key="i">
                  <span class="step-dot" :class="{ on: i <= curStepIdx }">{{ i + 1 }}</span>
                  <span class="step-text" :class="{ on: i <= curStepIdx }">{{ s }}</span>
                </div>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="return" tab="还货详情">
            <template v-if="curItems && curItems.length">
              <div class="info-card" v-for="item in returnItems" :key="item.id">
                <div class="info-card-title" style="display:flex;justify-content:space-between;align-items:center">
                  <span>{{ item.materialName }}</span>
                  <a-tag :color="(item.returnedQty || 0) >= (item.transferQty || 0) ? 'green' : 'orange'">{{ (item.returnedQty || 0) >= (item.transferQty || 0) ? '已还清' : '待归还' }}</a-tag>
                </div>
                <div class="info-row"><span class="info-label">调拨</span><span class="info-value">{{ item.transferQty }}{{ item.unit || '件' }}{{ item.unitPrice ? ' · ' : '' }}<a-tag v-if="item.unitPrice" color="default">单价：¥{{ item.unitPrice }}</a-tag></span></div>
                <div class="info-row"><span class="info-label">已还</span><span class="info-value">{{ item.goodsQty > 0 ? `${item.goodsQty}${item.unit}` : '' }}{{ item.goodsQty > 0 && item.returnAmount > 0 ? ' + ' : '' }}{{ item.returnAmount > 0 ? `¥${item.returnAmount}` : '' }}{{ !item.goodsQty && !item.returnAmount ? '--' : '' }}</span></div>
                <!-- 归还明细 -->
                <div v-if="item.records && item.records.length" style="margin-top:12px;border-top:1px solid #EEF1EF;padding-top:8px">
                  <div v-for="(r, ri) in item.records" :key="ri" style="display:flex;justify-content:space-between;align-items:center;padding:4rpx 0;font-size:13px">
                    <span style="color:#374151">{{ r.returnType === 'goods' ? `还货 ${r.returnQty}${item.unit}` : `还钱 ¥${r.returnAmount || 0}` }}</span>
                    <span style="color:#98A19C">{{ r.handlerName || '' }} · {{ r.createdAt ? r.createdAt.substring(0,16) : '' }}</span>
                  </div>
                </div>
                <div v-else style="margin-top:8px;text-align:center;color:#98A19C;font-size:13px">暂无归还记录</div>
              </div>
            </template>
            <div v-else class="info-block" style="text-align:center;color:#98A19C">暂无物料信息</div>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { getTransferOrders, getTransferOrderDetail, exportTransferOrders } from '../../api/transfer'
import { getStores } from '../../api/store'
import { getSupervisorOptions } from '../../api/supervisor'
import dayjs from 'dayjs'

const loading = ref(false)
const list = ref<any[]>([])
const storeOptions = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })
const filters = reactive({ fromStoreId: '', toStoreId: '', supervisorName: '', status: '', handoff: '', keyword: '', startDate: '', endDate: '' })
const supervisorOptions = ref<{ label: string; value: string }[]>([])
const dateRange = ref<any>(null)
const drawerOpen = ref(false)
const cur = ref<any>(null)
const curItems = ref<any[]>([])
const totalTransferAmount = computed(() => {
  return curItems.value.reduce((sum, item) => {
    return sum + (item.transferQty || 0) * (item.unitPrice || 0)
  }, 0).toFixed(2)
})
const returnItems = computed(() => {
  return curItems.value.map(item => {
    const itemRecords = (curReturnRecords.value || []).filter((r: any) => r.itemId === item.id)
    const goodsQty = itemRecords.filter((r: any) => r.returnType === 'goods').reduce((s: number, r: any) => s + (r.returnQty || 0), 0)
    const moneyAmount = itemRecords.filter((r: any) => r.returnType === 'money').reduce((s: number, r: any) => s + (r.returnAmount || 0), 0)
    const moneyQty = (item.unitPrice && item.unitPrice > 0) ? moneyAmount / item.unitPrice : 0
    return { ...item, returnedQty: goodsQty + moneyQty, goodsQty, returnAmount: moneyAmount, records: itemRecords }
  })
})
function getItemRecords(itemId: number) {
  return (curReturnRecords.value || []).filter((r: any) => r.itemId === itemId)
}
const curLoading = ref(false)
const detailTabKey = ref('transfer')
const curReturnRecords = ref<any[]>([])

const cols = [
  { title: '调出门店', dataIndex: 'fromStoreName', width: 160, ellipsis: true, fixed: 'left' },
  { title: '调入门店', dataIndex: 'toStoreName', width: 160, ellipsis: true, fixed: 'left' },
  { title: '督导', dataIndex: 'supervisorName', width: 100, ellipsis: true },
  { title: '物品', dataIndex: 'itemNames', width: 200, ellipsis: true },
  { title: '是否完成调货', key: 'completed', width: 100 },
  { title: '是否还货', key: 'return', width: 80 },
  { title: '发起人', dataIndex: 'createdByName', width: 100, ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt', width: 150 },
  { title: '操作', key: 'action', width: 60, fixed: 'right' },
]

const curTitle = computed(() => {
  if (!cur.value) return ''
  return `${cur.value.fromStoreName} → ${cur.value.toStoreName}`
})

const curSteps = computed(() => {
  if (!cur.value) return []
  const s = cur.value.status
  const steps = [`${cur.value.toStoreName}发起调入申请`]
  if (s === 'cancelled') { steps.push('已取消', '调货单结束'); return steps }
  if (s === 'rejected') { steps.push(`${cur.value.fromStoreName}已拒绝`, '调货单结束'); return steps }
  if (s === 'pending_confirm') { steps.push(`等待${cur.value.fromStoreName}确认`); return steps }
  steps.push(`${cur.value.fromStoreName}确认可调出`)
  if (s === 'confirmed') { steps.push('等待发货交接'); return steps }
  if (s === 'pending_ship') { steps.push(`等待${cur.value.toStoreName}收货确认`); return steps }
  steps.push(`${cur.value.toStoreName}确认收货`)
  return steps
})

const curStepIdx = computed(() => {
  const s = cur.value?.status
  if (!s || s === 'pending_confirm' || s === 'cancelled' || s === 'rejected') return 0
  if (s === 'confirmed') return 1
  if (s === 'pending_ship') return 2
  return curSteps.value.length - 1
})

function statusColor(s: string) {
  const map: Record<string, string> = { pending_confirm: 'orange', confirmed: 'orange', pending_ship: 'green', pending_receive: 'green', completed: 'green', returned: 'green', cancelled: 'default', rejected: 'red' }
  return map[s] || 'default'
}
function statusLabel(s: string) {
  const map: Record<string, string> = { pending_confirm: '待确认', confirmed: '待交接', pending_ship: '待收货', pending_receive: '已收货', completed: '已完成', returned: '已归还', cancelled: '已取消', rejected: '已拒绝' }
  return map[s] || s
}
function statusNote(s: string) {
  const map: Record<string, string> = {
    pending_confirm: '等待调出方确认是否可调出。',
    confirmed: '调出方已确认，等待发货交接。',
    pending_ship: '已发货，等待调入方确认收货。',
    pending_receive: '调入方已确认收货，流程完成。',
    completed: '调货流程已完成。',
    cancelled: '该调货单已取消。',
    rejected: '调出方已拒绝该调货申请。',
  }
  return map[s] || '总部仅查看台账，不干预门店调货。'
}

function onDateChange(dates: any) {
  if (dates && dates.length === 2) {
    filters.startDate = dayjs(dates[0]).format('YYYY-MM-DD')
    filters.endDate = dayjs(dates[1]).format('YYYY-MM-DD')
  } else {
    filters.startDate = ''; filters.endDate = ''
  }
  fetchList()
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await getTransferOrders({
      fromStoreId: filters.fromStoreId || undefined,
      toStoreId: filters.toStoreId || undefined,
      supervisorName: filters.supervisorName || undefined,
      status: filters.status || undefined,
      handoff: filters.handoff || undefined,
      keyword: filters.keyword || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
      pageNum: pagination.current, pageSize: pagination.pageSize,
    })
    list.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally { loading.value = false }
}

async function loadStores() {
  try { const res: any = await getStores(); storeOptions.value = res.data || res || [] } catch { /* */ }
}

function resetFilters() {
  filters.fromStoreId = ''; filters.toStoreId = ''; filters.supervisorName = ''; filters.status = ''; filters.handoff = ''; filters.keyword = ''; filters.startDate = ''; filters.endDate = ''
  dateRange.value = null
  fetchList()
}

function handleTable(p: any) { pagination.current = p.current; fetchList() }

async function openDrawer(record: any) {
  cur.value = record
  drawerOpen.value = true
  detailTabKey.value = 'transfer'
  curLoading.value = true
  try {
    const res: any = await getTransferOrderDetail(record.id)
    curItems.value = res.data?.items || []
    curReturnRecords.value = res.data?.returnRecords || []
  } catch { curItems.value = []; curReturnRecords.value = [] }
  finally { curLoading.value = false }
}

async function handleExport() {
  try {
    const res: any = await exportTransferOrders({
      fromStoreId: filters.fromStoreId || undefined,
      toStoreId: filters.toStoreId || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
    })
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `调货台账_${dayjs().format('YYYY-MM-DD')}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch { /* ignore */ }
}

async function fetchSupervisors() {
  try { const res: any = await getSupervisorOptions(); supervisorOptions.value = res.data || [] } catch { /* */ }
}

onMounted(() => { loadStores(); fetchSupervisors(); fetchList() })
</script>

<style scoped>
.transfer-page { padding: 0 }
.page-title-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px }
.page-title { font-size: 22px; font-weight: 700; color: #1F2421; margin: 0 }
.page-subtitle { font-size: 13px; color: #66706A; margin: 4px 0 0 }
.filter-card { margin-bottom: 12px }
.filter-label { font-size: 13px; color: #66706A; margin-bottom: 4px }
.d-head { margin-bottom: 8px }
.d-head-top { display: flex; justify-content: space-between; align-items: center }
.d-close { font-size: 22px; color: #1F2421; cursor: pointer; line-height: 1; padding: 4px }
.d-title { font-size: 20px; font-weight: 700; color: #1F2421 }
.d-biz-code { font-size: 13px; color: #98A19C; margin-top: 4px }
.info-block { padding: 16px; border: 1px solid #E8ECE9; border-radius: 8px; margin-bottom: 12px }
.info-block.subtle { background: #F7F8F6 }
.info-card { background: #fff; border: 1px solid #E8E8E8; border-radius: 12px; padding: 20px 16px; margin-bottom: 12px }
.info-card-title { font-size: 16px; font-weight: 600; color: #1F2421; margin-bottom: 16px }
.info-row { display: flex; padding: 6px 0 }
.info-label { width: 80px; flex-shrink: 0; font-size: 14px; color: #98A19C }
.info-value { flex: 1; font-size: 14px; color: #1F2421 }
.steps { display: flex; flex-direction: column; gap: 10px }
.step-row { display: flex; align-items: center; gap: 10px }
.step-dot { width: 24px; height: 24px; border-radius: 50%; background: #E8ECE9; color: #98A19C; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0 }
.step-dot.on { background: #E7F4EB; color: #2F8F57 }
.step-text { font-size: 13px; color: #98A19C }
.step-text.on { color: #1F2421; font-weight: 500 }
</style>
