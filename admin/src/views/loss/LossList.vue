<template>
  <div class="loss-page">
    <LossModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">报损管理</h1>
        <p class="page-subtitle">查看全门店日常报损与到货验收报损，总部不做逐条审批。</p>
      </div>
      <a-button type="primary" :loading="exporting" @click="exportLoss">导出报损记录</a-button>
    </div>

    <!-- 筛选 -->
    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">门店</div>
          <a-select v-model:value="filters.storeId" placeholder="全部门店" style="width:100%" allow-clear show-search :filter-option="(input:string, option:any) => (option.mendianmingcheng||'').includes(input)" :field-names="{label:'mendianmingcheng',value:'id'}" :options="storeOptions" @change="fetchList" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">督导</div>
          <a-select v-model:value="filters.supervisorName" placeholder="全部督导" style="width:100%" allow-clear :options="supervisorOptions" @change="fetchList" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">报损类型</div>
          <a-select v-model:value="filters.lossType" placeholder="全部" style="width:100%" allow-clear @change="fetchList">
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="daily">日常报损</a-select-option>
            <a-select-option value="arrival">到货验收</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="4">
          <div class="filter-label">状态</div>
          <a-select v-model:value="filters.status" placeholder="全部" style="width:100%" allow-clear @change="fetchList">
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="pending_approval">待店长审批</a-select-option>
            <a-select-option value="pending">待厂家确认</a-select-option>
            <a-select-option value="registered">已登记</a-select-option>
            <a-select-option value="confirmed_resend">已确认补发</a-select-option>
            <a-select-option value="received">已收到货</a-select-option>
            <a-select-option value="not_received">未收到货</a-select-option>
            <a-select-option value="completed">已录入</a-select-option>
            <a-select-option value="rejected">已拒绝</a-select-option>
            <a-select-option value="closed">已关闭</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="5">
          <div class="filter-label">日期范围</div>
          <a-range-picker v-model:value="dateRange" style="width:100%" @change="fetchList" />
        </a-col>
      </a-row>
      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:8px">
        <a-button @click="resetFilters">重置</a-button>
        <a-button type="primary" @click="fetchList">查询</a-button>
      </div>
    </a-card>

    <!-- 表格 -->
    <a-card :bordered="false">
      <a-table :columns="cols" :data-source="list" :loading="loading" :pagination="pagination" row-key="id" size="middle" @change="handleTable">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'lossType'">
            <a-tag :color="record.lossType === 'arrival' ? 'orange' : 'default'">{{ record.lossType === 'arrival' ? '到货验收' : '日常报损' }}</a-tag>
          </template>
          <template v-if="column.key === 'materialName'">
            {{ record.itemNames || record.materialName || '--' }}
          </template>
          <template v-if="column.key === 'qty'">
            <template v-if="record.itemCount && record.itemCount > 0">共 {{ record.itemCount }} 种</template>
            <template v-else>{{ record.lossObject === 'semi_finished' ? (record.netWeight ? `净重 ${record.netWeight}g` : '--') : (record.inputQty ? `${record.inputQty} ${record.inputUnit||''}` : (record.baseQty ? `${record.baseQty} ${record.baseUnit||''}` : '--')) }}</template>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status, record.lossType)">{{ statusLabel(record.status, record.lossType) }}</a-tag>
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
            <a-tag :color="statusColor(cur.status, cur.lossType)">{{ statusLabel(cur.status, cur.lossType) }}</a-tag>
            <span class="d-close" @click="drawerOpen = false">✕</span>
          </div>
          <div class="d-head-info" style="margin-top:12px">
            <div class="d-material-name">{{ cur.itemNames || cur.materialName }}</div>
            <div class="d-biz-code">{{ cur.bizCode || '--' }}</div>
          </div>
        </div>
        <a-divider />
        <div class="info-block subtle">
          <strong v-if="cur.lossType === 'arrival'">厂家确认结果</strong>
          <strong v-else>处理结果</strong>
          <p style="margin:4px 0 0;color:#66706A;font-size:13px">
            <template v-if="cur.lossType === 'daily'">已记录，无需后续处理。</template>
            <template v-else>{{ cur.status === 'pending' ? '等待厂家确认中' : cur.status === 'registered' ? '已登记，待次月汇总发券。' : cur.status === 'confirmed_resend' ? '厂家已确认补发' : cur.status === 'rejected' ? '厂家已拒绝' : '已关闭，无需后续处理。' }}</template>
          </p>
        </div>
        <div class="info-card">
          <div class="info-card-title">报损信息</div>
          <div class="info-row"><span class="info-label">门店</span><span class="info-value">{{ cur.storeName || '--' }}</span></div>
          <div class="info-row"><span class="info-label">报损类型</span><span class="info-value">{{ cur.lossType === 'arrival' ? '到货验收' : '日常报损' }}</span></div>
          <template v-if="cur.items && cur.items.length > 0">
            <div class="info-row"><span class="info-label">报损明细</span><span class="info-value">{{ cur.items.length }} 种物料</span></div>
            <div v-for="item in cur.items" :key="item.id" class="info-row" style="padding-left:12px;font-size:13px">
              <span class="info-label">{{ item.materialName }}</span>
              <span class="info-value">净重 {{ item.netWeight }}g<template v-if="item.totalAmount"> · ¥{{ item.totalAmount }}</template></span>
            </div>
          </template>
          <template v-else>
            <div class="info-row"><span class="info-label">物品</span><span class="info-value">{{ cur.materialName }}</span></div>
            <div class="info-row"><span class="info-label">数量/重量</span><span class="info-value">{{ cur.netWeight ? `净重 ${cur.netWeight}g` : (cur.inputQty ? `${cur.inputQty} ${cur.inputUnit||''}` : (cur.baseQty ? `${cur.baseQty} ${cur.baseUnit||''}` : '--')) }}</span></div>
          </template>
          <div class="info-row"><span class="info-label">报损原因</span><span class="info-value">{{ cur.reason || '--' }}</span></div>
          <div class="info-row"><span class="info-label">登记人</span><span class="info-value">{{ cur.handlerName || '--' }}</span></div>
          <div class="info-row"><span class="info-label">提交时间</span><span class="info-value">{{ cur.createdAt || '--' }}</span></div>
        </div>
        <div class="info-block" v-if="cur.items && cur.items.length > 0">
          <strong>计算公式</strong>
          <p v-for="item in cur.items" :key="item.id" style="margin:4px 0 0;color:#66706A;font-size:13px">
            <template v-if="item.containerName">{{ item.materialName }}: 净重 {{ item.netWeight }}g = 含容器重量 {{ item.grossWeight }}g - {{ item.containerName }} {{ item.containerWeight }}g</template>
            <template v-else>{{ item.materialName }}: 净重 {{ item.netWeight }}g</template>
          </p>
        </div>
        <div class="info-block" v-else-if="cur.containerName">
          <strong>计算公式</strong>
          <p style="margin:4px 0 0;color:#66706A;font-size:13px">净重 {{ cur.netWeight }}g = 含容器重量 {{ cur.grossWeight }}g - {{ cur.containerName }} {{ cur.containerWeight }}g</p>
        </div>
        <div class="info-block" v-if="cur.remark">
          <strong>备注</strong>
          <p style="margin:4px 0 0;color:#66706A;font-size:13px">{{ cur.remark }}</p>
        </div>
        <div class="info-block" v-if="lossMedia.length">
          <strong>附件</strong>
          <a-image-preview-group>
            <div class="img-grid">
              <template v-for="(item, i) in lossMedia" :key="i">
                <a-image v-if="!item.isVideo" :src="item.url" style="width:100%;border-radius:6px" />
                <div v-else class="video-thumb" @click="openVideoPreview(item.url)">
                  <video :src="item.url" preload="metadata" muted playsinline class="video-thumb-el" />
                  <div class="video-play-overlay">▶</div>
                </div>
              </template>
            </div>
          </a-image-preview-group>
        </div>
      </template>
    </a-drawer>

    <!-- 视频预览弹窗 -->
    <a-modal v-model:open="videoPreviewOpen" :footer="null" width="720px" destroy-on-close @cancel="videoPreviewOpen = false">
      <video v-if="videoPreviewUrl" :src="videoPreviewUrl" controls autoplay style="width:100%;max-height:70vh;border-radius:8px;background:#000" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { getLossManageList, exportLossReport } from '../../api/loss'
import { getStores } from '../../api/store'
import { getSupervisorOptions } from '../../api/supervisor'
import LossModuleTabs from '../../components/LossModuleTabs.vue'
import dayjs from 'dayjs'

const loading = ref(false)
const list = ref<any[]>([])
const storeOptions = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })
const filters = reactive({ storeId: '', supervisorName: '', lossType: '', status: '', startDate: '', endDate: '' })
const supervisorOptions = ref<{ label: string; value: string }[]>([])
const dateRange = ref<any>(null)
const drawerOpen = ref(false)
const cur = ref<any>(null)
function isVideoUrl(url: string) { return /\.(mp4|mov|avi|mkv|webm)($|\?)/i.test(url) }
const lossMedia = computed(() => {
  if (!cur.value?.voucherUrl) return []
  return cur.value.voucherUrl.split(',').filter(Boolean).map(url => ({ url, isVideo: isVideoUrl(url) }))
})
const lossImages = computed(() => lossMedia.value.filter(m => !m.isVideo).map(m => m.url))
const lossVideos = computed(() => lossMedia.value.filter(m => m.isVideo).map(m => m.url))
const videoPreviewOpen = ref(false)
const videoPreviewUrl = ref('')
function openVideoPreview(url: string) { videoPreviewUrl.value = url; videoPreviewOpen.value = true }

const cols = [
  { title: '门店', dataIndex: 'storeName', width: 160, ellipsis: true },
  { title: '督导', dataIndex: 'supervisorName', width: 100, ellipsis: true },
  { title: '类型', key: 'lossType', width: 80 },
  { title: '物品', key: 'materialName', ellipsis: true },
  { title: '数量/重量', key: 'qty', width: 100 },
  { title: '原因', dataIndex: 'reason', width: 80, ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '时间', dataIndex: 'createdAt', width: 120 },
  { title: '操作', key: 'action', width: 60 },
]

function statusColor(s: string, lossType?: string) {
  if (s === 'pending_approval') return 'orange'
  if (s === 'pending') return 'orange'
  if (s === 'registered') return 'blue'
  if (s === 'confirmed_resend') return 'blue'
  if (s === 'received') return 'green'
  if (s === 'not_received') return 'red'
  if (s === 'completed') return 'green'
  if (s === 'rejected') return 'red'
  if (s === 'closed') return 'default'
  return 'default'
}
function statusLabel(s: string, lossType?: string) {
  if (s === 'pending_approval') return '待店长审批'
  if (s === 'pending') return '待厂家确认'
  if (s === 'registered') return '已登记'
  if (s === 'confirmed_resend') return '已确认补发'
  if (s === 'received') return '已收到货'
  if (s === 'not_received') return '未收到货'
  if (s === 'completed') return '已录入'
  if (s === 'rejected') return '已拒绝'
  if (s === 'closed') return '已关闭'
  return s
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
    if (dateRange.value && dateRange.value.length === 2) {
      filters.startDate = dayjs(dateRange.value[0]).format('YYYY-MM-DD')
      filters.endDate = dayjs(dateRange.value[1]).format('YYYY-MM-DD')
    } else {
      filters.startDate = ''; filters.endDate = ''
    }
    const res: any = await getLossManageList({
      storeId: filters.storeId || undefined,
      supervisorName: filters.supervisorName || undefined,
      lossType: filters.lossType || undefined,
      status: filters.status || undefined,
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

function resetFilters() { filters.storeId = ''; filters.supervisorName = ''; filters.lossType = ''; filters.status = ''; dateRange.value = null; fetchList() }

const exporting = ref(false)

async function exportLoss() {
  exporting.value = true
  try {
    const res: any = await exportLossReport({
      storeId: filters.storeId || undefined,
      supervisorName: filters.supervisorName || undefined,
      lossType: filters.lossType || undefined,
      status: filters.status || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
    })
    const blob = res instanceof Blob ? res : new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `报损记录_${dayjs().format('YYYY-MM-DD')}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    // 延迟释放 URL：立即 revoke 可能赶在浏览器开始下载前销毁，导致静默无反应
    setTimeout(() => URL.revokeObjectURL(url), 2000)
  } catch (e: any) {
    message.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function handleTable(p: any) { pagination.current = p.current; fetchList() }
function openDrawer(record: any) { cur.value = record; drawerOpen.value = true }

async function fetchSupervisors() {
  try { const res: any = await getSupervisorOptions(); supervisorOptions.value = res.data || [] } catch { /* */ }
}

onMounted(() => { loadStores(); fetchSupervisors(); fetchList() })
</script>

<style scoped>
.loss-page { padding: 0 }
.page-title-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px }
.page-title { font-size: 22px; font-weight: 700; color: #1F2421; margin: 0 }
.page-subtitle { font-size: 13px; color: #66706A; margin: 4px 0 0 }
.filter-card { margin-bottom: 12px }
.filter-label { font-size: 13px; color: #66706A; margin-bottom: 4px }
.d-head { margin-bottom: 8px }
.d-head-top { display: flex; justify-content: space-between; align-items: center }
.d-close { font-size: 22px; color: #1F2421; cursor: pointer; line-height: 1; padding: 4px }
.d-material-name { font-size: 20px; font-weight: 700; color: #1F2421 }
.d-biz-code { font-size: 13px; color: #98A19C; margin-top: 4px }
.info-block { padding: 16px; border: 1px solid #E8ECE9; border-radius: 8px; margin-bottom: 12px }
.info-block.subtle { background: #F7F8F6 }

/* 报损信息卡片风格 */
.info-card { background: #fff; border: 1px solid #E8E8E8; border-radius: 12px; padding: 20px 16px; margin-bottom: 12px }
.info-card-title { font-size: 16px; font-weight: 600; color: #1F2421; margin-bottom: 16px }
.info-row { display: flex; padding: 6px 0 }
.info-label { width: 80px; flex-shrink: 0; font-size: 14px; color: #98A19C }
.info-value { flex: 1; font-size: 14px; color: #1F2421 }
.img-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 8px }
.video-thumb { position: relative; border-radius: 6px; overflow: hidden; cursor: pointer; aspect-ratio: 1; background: #000 }
.video-thumb-el { width: 100%; height: 100%; object-fit: cover; display: block }
.video-play-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 32px; color: #fff; background: rgba(0,0,0,.25); pointer-events: none }
</style>
