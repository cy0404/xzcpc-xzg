<template>
  <div class="feedback-page">
    <div class="page-title-row">
      <div>
        <h1 class="page-title">评价管理</h1>
        <p class="page-subtitle">顾客/公众扫码提交的评价反馈，支持按类型、门店、状态、日期筛选与导出；后续将接入美团、小红书等平台差评。</p>
      </div>
      <a-button type="primary" @click="exportFeedback" :loading="exporting">导出 Excel</a-button>
    </div>

    <!-- 筛选 -->
    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 12]">
        <a-col :xs="12" :md="6">
          <div class="filter-label">渠道</div>
          <a-select v-model:value="filters.channel" placeholder="全部渠道" style="width:100%" allow-clear @change="fetchList">
            <a-select-option v-for="o in channelOptions" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">反馈类型</div>
          <a-select v-model:value="filters.feedbackType" placeholder="全部类型" style="width:100%" allow-clear @change="fetchList">
            <a-select-option v-for="t in typeOptions" :key="t" :value="t">{{ t }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">门店</div>
          <a-input v-model:value="filters.storeName" placeholder="门店名称" style="width:100%" allow-clear @press-enter="fetchList" />
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">处理状态</div>
          <a-select v-model:value="filters.status" placeholder="全部状态" style="width:100%" allow-clear @change="fetchList">
            <a-select-option v-for="o in statusOptions" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">关键词</div>
          <a-input v-model:value="filters.keyword" placeholder="反馈内容" style="width:100%" allow-clear @press-enter="fetchList" />
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">日期范围</div>
          <a-range-picker v-model:value="dateRange" style="width:100%" @change="onDateChange" />
        </a-col>
        <a-col :xs="12" :md="6" style="display:flex;align-items:flex-end;gap:8px">
          <a-button @click="resetFilters">重置</a-button>
          <a-button type="primary" @click="fetchList">查询</a-button>
        </a-col>
      </a-row>
    </a-card>

    <!-- 表格 -->
    <a-card :bordered="false">
      <a-table :columns="cols" :data-source="list" :loading="loading" :pagination="pagination" row-key="id" size="middle" :scroll="{ x: 1000 }" @change="handleTable">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'channel'">
            <a-tag style="border-radius:12px" :style="channelStyle(record.channel)">{{ channelText(record.channel) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'feedbackType'">
            <a-tag color="#2F8F57" style="border-radius:12px" :style="{ background: '#2F8F5718', borderColor: '#2F8F5740', color: '#2F8F57' }">{{ record.feedbackType || '--' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag style="border-radius:12px" :style="statusStyle(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'images'">
            <span v-if="imageList(record).length">
              <a-image v-for="(img, i) in imageList(record).slice(0, 3)" :key="i" :src="img" :width="32" :height="32" style="border-radius:6px;object-fit:cover;margin-right:4px" />
            </span>
            <span v-else style="color:#98A19C">--</span>
          </template>
          <template v-else-if="column.key === 'action'">
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
            <a-tag color="#2F8F57" style="border-radius:12px" :style="{ background: '#2F8F5718', borderColor: '#2F8F5740', color: '#2F8F57' }">{{ cur.feedbackType || '--' }}</a-tag>
            <a-tag style="border-radius:12px" :style="statusStyle(cur.status)">{{ statusText(cur.status) }}</a-tag>
            <span class="d-close" @click="drawerOpen = false">✕</span>
          </div>
          <div class="d-id" style="margin-top:8px">#{{ cur.id }}</div>
        </div>
        <a-divider />

        <div class="info-card">
          <div class="info-card-title">基本信息</div>
          <div class="info-row"><span class="info-label">门店</span><span class="info-value">{{ cur.storeName || '--' }}</span></div>
          <div class="info-row"><span class="info-label">手机号</span><span class="info-value">{{ cur.phone || '--' }}</span></div>
          <div class="info-row"><span class="info-label">提交时间</span><span class="info-value">{{ cur.createdAt || '--' }}</span></div>
        </div>

        <div class="info-card">
          <div class="info-card-title">处理进度</div>
          <div class="info-row">
            <span class="info-label">处理状态</span>
            <span class="info-value" style="flex:1">
              <a-select v-model:value="editStatus" style="width:150px" :options="statusOptions" />
            </span>
          </div>
          <div class="info-row" style="align-items:flex-start">
            <span class="info-label">处理说明</span>
            <a-textarea v-model:value="editNote" :rows="3" maxlength="500" placeholder="仅门店/总部可见，顾客不可见" style="flex:1" />
          </div>
          <div class="info-row" v-if="cur.processingAt"><span class="info-label">开始处理</span><span class="info-value">{{ cur.processingAt }}</span></div>
          <div class="info-row" v-if="cur.processedAt"><span class="info-label">完成时间</span><span class="info-value">{{ cur.processedAt }}</span></div>
          <div class="info-row" v-if="cur.processedName"><span class="info-label">处理人</span><span class="info-value">{{ cur.processedName }}</span></div>
          <div class="info-row" v-if="evidenceList.length" style="align-items:flex-start">
            <span class="info-label">处理凭证</span>
            <span class="info-value" style="display:flex;flex-wrap:wrap;gap:6px">
              <a-image v-for="(ev, i) in evidenceList" :key="i" :src="ev" :width="64" :height="64" style="border-radius:6px;object-fit:cover" />
            </span>
          </div>
          <a-button type="primary" style="margin-top:8px" :loading="saving" @click="saveStatus">保存处理进度</a-button>
        </div>

        <div class="info-card">
          <div class="info-card-title">反馈内容</div>
          <p style="margin:0;font-size:14px;color:#1F2421;line-height:22px;white-space:pre-wrap">{{ cur.content }}</p>
        </div>

        <div class="info-card" v-if="curImages.length">
          <div class="info-card-title">图片（{{ curImages.length }}）</div>
          <div class="img-grid">
            <a-image v-for="(img, i) in curImages" :key="i" :src="img" :width="88" :height="88" style="border-radius:8px;object-fit:cover" />
          </div>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getFeedbackOptions, getFeedbackList, getFeedbackDetail, updateFeedbackStatus } from '../../api/feedback'
import dayjs from 'dayjs'

const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const filters = reactive({ channel: '', feedbackType: '', storeName: '', status: '', keyword: '', startDate: '', endDate: '' })
const typeOptions = ref<string[]>([])

/** 渠道选项：scan=扫码反馈（现有）；meituan/xiaohongshu 为未来接入平台差评预留 */
const CHANNEL_MAP: Record<string, { text: string; color: string; bg: string; border: string }> = {
  scan: { text: '扫码反馈', color: '#2F8F57', bg: '#E7F4EB', border: '#B7DCC2' },
  meituan: { text: '美团', color: '#FFB800', bg: '#FFF8E6', border: '#FFE3A3' },
  xiaohongshu: { text: '小红书', color: '#E8574F', bg: '#FDEBEA', border: '#F5C0BC' },
}
const channelOptions = Object.entries(CHANNEL_MAP).map(([value, m]) => ({ value, label: m.text }))
function channelText(c?: string) {
  return (CHANNEL_MAP[c || ''] || { text: c || '--' }).text
}
function channelStyle(c?: string) {
  const m = CHANNEL_MAP[c || ''] || { text: c || '--', color: '#66706A', bg: '#F0F2F1', border: '#D9DEDB' }
  return { background: m.bg, borderColor: m.border, color: m.color }
}
const dateRange = ref<any>(null)
const drawerOpen = ref(false)
const cur = ref<any>(null)
const editStatus = ref<string>('pending')
const editNote = ref('')

const statusOptions = [
  { value: 'pending', label: '待处理' },
  { value: 'processing', label: '处理中' },
  { value: 'done', label: '已处理' },
  { value: 'closed', label: '已关闭' },
]
const STATUS_STYLE: Record<string, { text: string; color: string; bg: string; border: string }> = {
  pending: { text: '待处理', color: '#D46B08', bg: '#FFF7E6', border: '#FFD591' },
  processing: { text: '处理中', color: '#1677FF', bg: '#E6F4FF', border: '#91CAFF' },
  done: { text: '已处理', color: '#389E0D', bg: '#F6FFED', border: '#B7EB8F' },
  closed: { text: '已关闭', color: '#66706A', bg: '#F0F2F1', border: '#D9DEDB' },
}
function statusText(s?: string) {
  return (STATUS_STYLE[s || ''] || { text: s || '--' }).text
}
function statusStyle(s?: string) {
  const m = STATUS_STYLE[s || ''] || { color: '#66706A', bg: '#F0F2F1', border: '#D9DEDB' }
  return { background: m.bg, borderColor: m.border, color: m.color }
}

const cols = [
  { title: '渠道', key: 'channel', width: 90 },
  { title: '反馈类型', key: 'feedbackType', width: 100 },
  { title: '门店', dataIndex: 'storeName', width: 130, ellipsis: true, fixed: 'left' },
  { title: '手机号', dataIndex: 'phone', width: 120 },
  { title: '状态', key: 'status', width: 90 },
  { title: '反馈内容', dataIndex: 'content', ellipsis: true },
  { title: '图片', key: 'images', width: 100 },
  { title: '提交时间', dataIndex: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 60, fixed: 'right' },
]

function imgUrl(url: string): string {
  if (!url) return ''
  return url.replace('http://162.14.122.80:30260', 'https://www.xzcpc-9pd.top')
}
function imageList(record: any): string[] {
  return record.images ? String(record.images).split(',').filter(Boolean).map(imgUrl) : []
}
const curImages = computed(() => cur.value ? imageList(cur.value) : [])
const evidenceList = computed(() => cur.value?.evidence ? String(cur.value.evidence).split(',').filter(Boolean).map(imgUrl) : [])

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
    const res: any = await getFeedbackList({
      channel: filters.channel || undefined,
      feedbackType: filters.feedbackType || undefined,
      storeName: filters.storeName || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
      pageNum: pagination.current, pageSize: pagination.pageSize,
    })
    list.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally { loading.value = false }
}

function resetFilters() {
  filters.channel = ''; filters.feedbackType = ''; filters.storeName = ''; filters.status = ''; filters.keyword = ''; filters.startDate = ''; filters.endDate = ''
  dateRange.value = null
  fetchList()
}

function handleTable(p: any) { pagination.current = p.current; fetchList() }

async function openDrawer(record: any) {
  cur.value = record
  editStatus.value = record.status || 'pending'
  editNote.value = record.processNote || ''
  drawerOpen.value = true
  try {
    const res: any = await getFeedbackDetail(record.id)
    if (res.data) {
      cur.value = res.data
      editStatus.value = res.data.status || 'pending'
      editNote.value = res.data.processNote || ''
    }
  } catch { /* keep row data */ }
}

async function saveStatus() {
  saving.value = true
  try {
    await updateFeedbackStatus(cur.value.id, { status: editStatus.value, processNote: editNote.value })
    message.success('处理进度已保存')
    drawerOpen.value = false
    fetchList()
  } catch { message.error('保存失败') }
  finally { saving.value = false }
}

async function exportFeedback() {
  exporting.value = true
  try {
    const params = new URLSearchParams()
    if (filters.channel) params.append('channel', filters.channel)
    if (filters.feedbackType) params.append('feedbackType', filters.feedbackType)
    if (filters.storeName) params.append('storeName', filters.storeName)
    if (filters.status) params.append('status', filters.status)
    if (filters.keyword) params.append('keyword', filters.keyword)
    if (filters.startDate) params.append('startDate', filters.startDate)
    if (filters.endDate) params.append('endDate', filters.endDate)
    const token = localStorage.getItem('admin_token')
    const resp = await fetch(`/api/admin/feedback/export?${params.toString()}`, {
      headers: { Authorization: `Bearer ${token || ''}` }
    })
    if (!resp.ok) { message.error('导出失败'); return }
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = '评价管理.xlsx'; a.click()
    URL.revokeObjectURL(url)
  } catch { message.error('导出失败') }
  finally { exporting.value = false }
}

async function fetchTypeOptions() {
  try {
    const res: any = await getFeedbackOptions()
    typeOptions.value = res.data || []
  } catch { typeOptions.value = ['门店服务', '饮品品质', '其他'] }
}

onMounted(() => { fetchTypeOptions(); fetchList() })
</script>

<style scoped>
.feedback-page { padding: 0 }
.page-title-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px }
.page-title { font-size: 22px; font-weight: 700; color: #1F2421; margin: 0 }
.page-subtitle { font-size: 13px; color: #66706A; margin: 4px 0 0 }
.filter-card { margin-bottom: 12px }
.filter-label { font-size: 13px; color: #66706A; margin-bottom: 4px }
.d-head { margin-bottom: 8px }
.d-head-top { display: flex; justify-content: space-between; align-items: center; gap: 8px }
.d-close { font-size: 22px; color: #1F2421; cursor: pointer; line-height: 1; padding: 4px; margin-left: auto }
.d-id { font-size: 13px; color: #98A19C; margin-top: 4px }
.info-card { background: #fff; border: 1px solid #E8E8E8; border-radius: 12px; padding: 20px 16px; margin-bottom: 12px }
.info-card-title { font-size: 16px; font-weight: 600; color: #1F2421; margin-bottom: 16px }
.info-row { display: flex; padding: 6px 0 }
.info-label { width: 80px; flex-shrink: 0; font-size: 14px; color: #98A19C }
.info-value { flex: 1; font-size: 14px; color: #1F2421 }
.img-grid { display: flex; flex-wrap: wrap; gap: 8px }
</style>
