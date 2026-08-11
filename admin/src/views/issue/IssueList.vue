<template>
  <div class="issue-page">
    <div class="page-title-row">
      <div>
        <h1 class="page-title">问题台账</h1>
        <p class="page-subtitle">门店上报问题台账。总部仅查看，派单与处理由象目经理系统完成，不在此干预。</p>
      </div>
    </div>

    <!-- 筛选 -->
    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 12]">
        <a-col :xs="12" :md="6">
          <div class="filter-label">关键词</div>
          <a-input v-model:value="filters.keyword" placeholder="标题/门店" style="width:100%" allow-clear @press-enter="fetchList" />
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">督导</div>
          <a-select v-model:value="filters.supervisorName" placeholder="全部督导" style="width:100%" allow-clear :options="supervisorOptions" @change="fetchList" />
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">问题类型</div>
          <a-select v-model:value="filters.issueType" placeholder="全部类型" style="width:100%" allow-clear @change="fetchList">
            <a-select-option v-for="t in issueTypes" :key="t" :value="t">{{ t }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">紧急程度</div>
          <a-select v-model:value="filters.urgency" placeholder="全部" style="width:100%" allow-clear @change="fetchList">
            <a-select-option value="严重-影响营业或有客诉">严重-影响营业或有客诉</a-select-option>
            <a-select-option value="一般-影响效率和体验">一般-影响效率和体验</a-select-option>
            <a-select-option value="轻微-期望优化">轻微-期望优化</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">来源</div>
          <a-select v-model:value="filters.source" placeholder="全部来源" style="width:100%" allow-clear @change="fetchList">
            <a-select-option value="MINI_PROGRAM">小程序</a-select-option>
            <a-select-option value="FEISHU_GROUP">飞书群</a-select-option>
            <a-select-option value="HQ">总部</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">状态</div>
          <a-select v-model:value="filters.status" placeholder="全部状态" style="width:100%" allow-clear @change="fetchList">
            <a-select-option value="PENDING_CONFIRMATION">待确认</a-select-option>
            <a-select-option value="PENDING_CONFIRMATION">待确认</a-select-option>
            <a-select-option value="IN_PROGRESS">处理中</a-select-option>
            <a-select-option value="PENDING_CONTACT">待联系</a-select-option>
            <a-select-option value="PENDING_ACCEPTANCE">待验收</a-select-option>
            <a-select-option value="RESOLVED">已解决</a-select-option>
            <a-select-option value="CLOSED">已关闭</a-select-option>
            <a-select-option value="OVERDUE">逾期</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">日期范围</div>
          <a-range-picker v-model:value="dateRange" style="width:100%" @change="onDateChange" />
        </a-col>
        <a-col :xs="12" :md="3" style="display:flex;align-items:flex-end;gap:8px">
          <a-button @click="resetFilters">重置</a-button>
          <a-button type="primary" @click="fetchList">查询</a-button>
        </a-col>
      </a-row>
    </a-card>

    <!-- 表格 -->
    <a-card :bordered="false">
      <a-table :columns="cols" :data-source="list" :loading="loading" :pagination="pagination" row-key="id" size="middle" :scroll="{ x: 700 }" @change="handleTable">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'urgency'">
            <a-tag :color="urgencyColor(record.urgency)" style="border-radius:12px" :style="{ background: urgencyColor(record.urgency) + '18', borderColor: urgencyColor(record.urgency) + '40', color: urgencyColor(record.urgency) }">{{ record.urgency || '--' }}</a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-if="column.key === 'source'">
            <a-tag v-if="record.source" :color="sourceColor(record.source)" style="border-radius:12px" :style="{ background: sourceColor(record.source) + '18', borderColor: sourceColor(record.source) + '40', color: sourceColor(record.source) }">{{ sourceLabel(record.source) }}</a-tag>
            <span v-else style="color:#98A19C">--</span>
          </template>
          <template v-if="column.key === 'xiangmuId'">
            <span>{{ record.xiangmuId || '--' }}</span>
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
          <div class="d-biz-code" style="margin-top:8px">{{ cur.bizCode || '--' }}</div>
        </div>
        <a-divider />

        <div class="info-block subtle" v-if="cur.processResult">
          <strong>处理结果</strong>
          <p style="margin:4px 0 0;color:#1F2421;font-size:13px">{{ cur.processResult }}</p>
        </div>

        <div class="info-card">
          <div class="info-card-title">基本信息</div>
          <div class="info-row"><span class="info-label">问题编号</span><span class="info-value">{{ cur.bizCode || '--' }}</span></div>
          <div class="info-row"><span class="info-label">门店信息</span><span class="info-value">{{ cur.storeName || '--' }}</span></div>
          <div class="info-row"><span class="info-label">标题</span><span class="info-value">{{ cur.title || '--' }}</span></div>
          <div class="info-row"><span class="info-label">问题类型</span><span class="info-value">{{ cur.issueType || '--' }}{{ cur.subType ? ` · ${cur.subType}` : '' }}</span></div>
          <div class="info-row"><span class="info-label">严重程度</span><a-tag :color="urgencyColor(cur.urgency)" style="border-radius:12px" :style="{ background: urgencyColor(cur.urgency) + '18', borderColor: urgencyColor(cur.urgency) + '40', color: urgencyColor(cur.urgency) }">{{ cur.urgency || '--' }}</a-tag></div>
          <div class="info-row"><span class="info-label">来源</span><span class="info-value"><a-tag v-if="cur.source" :color="sourceColor(cur.source)" style="border-radius:12px" :style="{ background: sourceColor(cur.source) + '18', borderColor: sourceColor(cur.source) + '40', color: sourceColor(cur.source) }">{{ sourceLabel(cur.source) }}</a-tag><span v-else>--</span></span></div>
          <div class="info-row"><span class="info-label">处理人</span><span class="info-value">{{ cur.contactName || '--' }}</span></div>
          <div class="info-row"><span class="info-label">提交时间</span><span class="info-value">{{ cur.createdAt || '--' }}</span></div>
        </div>

        <div class="info-card" v-if="curImages.length">
          <div class="info-card-title">附件</div>
          <div class="img-grid">
            <a-image v-for="(img, i) in curImages" :key="i" :src="img" :width="88" :height="88" style="border-radius:8px;object-fit:cover" />
          </div>
        </div>

        <!-- 处理记录 -->
        <div class="info-block" v-if="cur.xiangmuId">
          <strong>处理记录</strong>
          <div v-if="recordsLoading" style="padding:16px 0;color:#98A19C;font-size:13px">加载中...</div>
          <div v-else>
            <div v-if="curRecords.length" class="records-list" style="margin-top:8px">
              <div v-for="(r, i) in curRecords" :key="i" class="rec-item">
                <div class="rec-dot" :class="{ on: i === curRecords.length - 1 }" />
                <div class="rec-line" v-if="i < curRecords.length - 1" />
                <div class="rec-body">
                  <div class="rec-text">{{ r.text }}</div>
                  <div class="rec-meta">{{ r.author }} · {{ r.time }}</div>
                  <div v-if="r.photos && r.photos.length" style="display:flex;gap:6px;margin-top:8px;flex-wrap:wrap">
                    <a-image v-for="(p, pi) in r.photos" :key="pi" :src="imgUrl(p)" :width="64" :height="64" style="border-radius:6px;object-fit:cover" />
                  </div>
                  <!-- 门店回复 -->
                  <div v-for="rp in curReplies.filter((x: any) => x.recordIndex === i)" :key="'rp'+rp.id" class="rec-reply">
                    <span>{{ rp.replyText }}</span>
                    <div v-if="rp.mediaUrls" style="display:flex;gap:6px;margin-top:6px;flex-wrap:wrap">
                      <a-image v-for="(m, mi) in rp.mediaUrls.split(',').filter(Boolean)" :key="mi" :src="imgUrl(m)" :width="48" :height="48" style="border-radius:4px;object-fit:cover" />
                    </div>
                    <div class="rec-meta" style="margin-top:4px">{{ rp.repliedBy }} · {{ rp.repliedAt }}</div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else style="padding:8px 0;color:#98A19C;font-size:13px">暂无处理记录</div>
            <!-- 方案照片 -->
            <div v-if="curSolutionPhotos.length" style="margin-top:12px">
              <div style="font-size:13px;color:#98A19C;margin-bottom:6px">方案照片</div>
              <div style="display:flex;gap:6px;flex-wrap:wrap">
                <a-image v-for="(img, ii) in curSolutionPhotos" :key="ii" :src="img" :width="88" :height="88" style="border-radius:8px;object-fit:cover" />
              </div>
            </div>
          </div>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { getIssueList, getIssueDetail } from '../../api/issue'
import { getSupervisorOptions } from '../../api/supervisor'
import dayjs from 'dayjs'

const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const filters = reactive({ supervisorName: '', issueType: '', urgency: '', status: '', source: '', keyword: '', startDate: '', endDate: '' })
const supervisorOptions = ref<{ label: string; value: string }[]>([])
const dateRange = ref<any>(null)
const drawerOpen = ref(false)
const cur = ref<any>(null)
const recordsLoading = ref(false)
const curRecords = ref<any[]>([])
const curReplies = ref<any[]>([])
const curSolutionPhotos = ref<any[]>([])

const issueTypes = ['设备问题', '物料问题', '物流问题', '系统问题', '经营异常', '其他']

const cols = [
  { title: '门店', dataIndex: 'storeName', width: 130, ellipsis: true, fixed: 'left' },
  { title: '督导', dataIndex: 'supervisorName', width: 100, ellipsis: true },
  { title: '类型', dataIndex: 'issueType', width: 90 },
  { title: '紧急程度', key: 'urgency', width: 170 },
  { title: '标题', dataIndex: 'title', ellipsis: true },
  { title: '状态', key: 'status', width: 80 },
  { title: '来源', key: 'source', width: 80 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 150 },
  { title: '操作', key: 'action', width: 60, fixed: 'right' },
]

const curImages = computed(() => (cur.value?.images ? String(cur.value.images).split(',').filter(Boolean).map(imgUrl) : []))

function imgUrl(url: string): string {
  if (!url) return ''
  // 内网 IP → 生产域名
  return url.replace('http://162.14.122.80:30260', 'https://www.xzcpc-9pd.top')
}

function sourceLabel(s: string) {
  if (!s) return '--'
  if (s === 'wxapp' || s === 'WXAPP' || s === 'MINI_PROGRAM') return '小程序'
  if (s === 'FEISHU_GROUP') return '飞书群'
  if (s === 'HQ') return '总部'
  return s
}
function sourceColor(s: string) {
  if (!s) return 'default'
  if (s === 'wxapp' || s === 'WXAPP' || s === 'MINI_PROGRAM') return '#2F8F57'
  if (s === 'FEISHU_GROUP') return '#4A90D9'
  if (s === 'HQ') return '#E58A2D'
  return 'default'
}
function urgencyColor(u: string) {
  if (!u) return 'default'
  if (u.includes('严重')) return '#E05A47'
  if (u.includes('较大')) return '#E58A2D'
  if (u.includes('一般')) return '#D7B36A'
  if (u.includes('轻微')) return '#8C8C8C'
  return 'default'
}
function statusColor(s: string) {
  return ({ PENDING_CONFIRMATION: 'default', IN_PROGRESS: 'orange', PENDING_CONTACT: 'red', PENDING_ACCEPTANCE: 'orange', RESOLVED: 'green', CLOSED: 'default', OVERDUE: 'red' } as Record<string, string>)[s] || 'default'
}
function statusLabel(s: string) {
  return ({ PENDING_CONFIRMATION: '待确认', IN_PROGRESS: '处理中', PENDING_CONTACT: '待联系', PENDING_ACCEPTANCE: '待验收', RESOLVED: '已解决', CLOSED: '已关闭', OVERDUE: '逾期' } as Record<string, string>)[s] || s
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
    const res: any = await getIssueList({
      supervisorName: filters.supervisorName || undefined,
      issueType: filters.issueType || undefined,
      urgency: filters.urgency || undefined,
      status: filters.status || undefined,
      source: filters.source || undefined,
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
  filters.supervisorName = ''; filters.issueType = ''; filters.urgency = ''; filters.status = ''; filters.source = ''; filters.keyword = ''; filters.startDate = ''; filters.endDate = ''
  dateRange.value = null
  fetchList()
}

function handleTable(p: any) { pagination.current = p.current; fetchList() }

async function openDrawer(record: any) {
  cur.value = record
  drawerOpen.value = true
  try {
    const res: any = await getIssueDetail(record.id)
    if (res.data) cur.value = res.data
  } catch { /* keep row data */ }
  loadRecords()
}

async function loadRecords() {
  if (!cur.value?.xiangmuId) return
  recordsLoading.value = true
  try {
    const res: any = await getIssueDetail(cur.value.id, true)
    const data = res?.data || {}
    curRecords.value = data.records || []
    curReplies.value = data.replies || []
    curSolutionPhotos.value = (data.solutionPhotos || []).map(imgUrl)
  } catch { curRecords.value = []; curReplies.value = []; curSolutionPhotos.value = [] }
  finally { recordsLoading.value = false }
}

async function fetchSupervisors() {
  try { const res: any = await getSupervisorOptions(); supervisorOptions.value = res.data || [] } catch { /* */ }
}

onMounted(() => { fetchSupervisors(); fetchList() })
</script>

<style scoped>
.issue-page { padding: 0 }
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
.img-grid { display: flex; flex-wrap: wrap; gap: 8px }

.records-list { padding: 4px 0 }
.rec-item { display: flex; position: relative; padding-bottom: 20px }
.rec-dot { width: 10px; height: 10px; border-radius: 50%; background: #E8ECE9; flex-shrink: 0; margin-top: 4px }
.rec-dot.on { background: #2F8F57 }
.rec-line { position: absolute; left: 4px; top: 14px; width: 2px; bottom: 6px; background: #EEF1EF }
.rec-body { flex: 1; margin-left: 14px; min-width: 0 }
.rec-text { font-size: 13px; color: #1F2421; line-height: 20px }
.rec-meta { font-size: 12px; color: #98A19C; margin-top: 2px }
.rec-reply { margin-top: 10px; background: #F1F8F3; border-radius: 8px; padding: 10px 14px; font-size: 13px; color: #1F2421 }
</style>
