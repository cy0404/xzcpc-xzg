<template>
  <div class="visit-page">
    <div class="page-title-row">
      <div>
        <h1 class="page-title">督导拜访</h1>
        <p class="page-subtitle">跟进经营辅导拜访、店长确认、行动计划拆分和督导审核，形成门店经营改善闭环。</p>
      </div>
      <a-button type="primary" size="large" @click="$router.push('/supervisor/create')">+ 创建拜访单</a-button>
    </div>

    <!-- Overview stat cards -->
    <section class="metric-grid" aria-label="拜访概览">
      <article class="metric-card">
        <p class="metric-label">待店长确认</p>
        <p class="metric-value">{{ stats.pendingConfirm }}</p>
        <p class="metric-note">拜访单已提交，等待小程序确认</p>
      </article>
      <article class="metric-card">
        <p class="metric-label">异议待处理</p>
        <p class="metric-value">{{ stats.objection }}</p>
        <p class="metric-note">需要督导调整记录或重新沟通</p>
      </article>
      <article class="metric-card">
        <p class="metric-label">跟进中任务</p>
        <p class="metric-value">{{ stats.inProgressTasks }}</p>
        <p class="metric-note">确认后拆分给门店执行</p>
      </article>
      <article class="metric-card">
        <p class="metric-label">逾期任务</p>
        <p class="metric-value" :class="{ 'metric-value--warn': stats.overdueTasks > 0 }">{{ stats.overdueTasks }}</p>
        <p class="metric-note">暂不提醒，仅展示逾期状态</p>
      </article>
    </section>

    <!-- Filter -->
    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 12]">
        <a-col :xs="12" :md="6">
          <div class="filter-label">门店</div>
          <a-select v-model:value="filters.storeId" placeholder="全部门店" style="width:100%" allow-clear show-search
            :field-names="{label:'mendianmingcheng',value:'id'}" :options="storeOptions" @change="handleSearch" />
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">督导</div>
          <a-select v-model:value="filters.supervisorName" placeholder="全部督导" style="width:100%" allow-clear show-search
            :options="supervisorOptions" @change="handleSearch" />
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">拜访状态</div>
          <a-select v-model:value="filters.visitStatus" placeholder="全部状态" style="width:100%" allow-clear @change="handleSearch">
            <a-select-option value="draft">草稿</a-select-option>
            <a-select-option value="pending_confirm">待确认</a-select-option>
            <a-select-option value="objection">异议处理中</a-select-option>
            <a-select-option value="in_progress">跟进中</a-select-option>
            <a-select-option value="completed">已完成</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">确认状态</div>
          <a-select v-model:value="filters.confirmStatus" placeholder="全部确认状态" style="width:100%" allow-clear @change="handleSearch">
            <a-select-option value="unsent">未发送</a-select-option>
            <a-select-option value="pending">待确认</a-select-option>
            <a-select-option value="confirmed">已确认</a-select-option>
            <a-select-option value="objected">已提出异议</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">逾期任务</div>
          <a-select v-model:value="filters.hasOverdue" placeholder="不限" style="width:100%" allow-clear @change="handleSearch">
            <a-select-option :value="true">仅看有逾期的</a-select-option>
            <a-select-option :value="false">无逾期</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">拜访日期</div>
          <a-range-picker v-model:value="dateRange" style="width:100%" @change="onDateChange" />
        </a-col>
        <a-col :xs="12" :md="6">
          <div class="filter-label">关键词</div>
          <a-input v-model:value="filters.keyword" placeholder="拜访编号/门店" style="width:100%" allow-clear @press-enter="handleSearch" />
        </a-col>
        <a-col :xs="12" :md="3" style="display:flex;align-items:flex-end;gap:8px">
          <a-button @click="resetFilters">重置</a-button>
          <a-button type="primary" @click="handleSearch">查询</a-button>
        </a-col>
      </a-row>
    </a-card>

    <!-- Table -->
    <a-card :bordered="false">
      <a-table :columns="cols" :data-source="list" :loading="loading" :pagination="pagination"
        row-key="id" size="middle" :scroll="{ x: 1050 }" @change="handleTable">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'visitNo'">
            <div class="visit-cell">
              <strong>{{ record.visitNo }}</strong>
              <span>{{ record.improvementFocus || record.lastIssueReview || '暂无摘要' }}</span>
            </div>
          </template>
          <template v-if="column.key === 'visitStatus'">
            <span class="badge" :class="visitBadgeCls(record.visitStatus)">{{ visitStatusLabel(record.visitStatus) }}</span>
          </template>
          <template v-if="column.key === 'confirmStatus'">
            <span class="badge" :class="confirmBadgeCls(record.confirmStatus)">{{ confirmStatusLabel(record.confirmStatus) }}</span>
          </template>
          <template v-if="column.key === 'progress'">
            <template v-if="record.actionCount">
              <div class="progress-line">
                <p>{{ record.completedCount || 0 }}/{{ record.actionCount }} 完成<span v-if="record.overdueCount" class="overdue-mark"> · {{ record.overdueCount }} 逾期</span></p>
                <div class="progress-track"><div class="progress-fill" :style="{ width: progressPct(record) + '%' }"></div></div>
              </div>
            </template>
            <template v-else-if="record.visitStatus === 'pending_confirm'">
              <span class="progress-pending">{{ record.actionCount || 0 }} 项待生成</span>
            </template>
            <template v-else-if="record.visitStatus === 'objection'">
              <span class="progress-pending">暂未拆分任务</span>
            </template>
            <span v-else class="progress-pending">--</span>
          </template>
          <template v-if="column.key === 'action'">
            <a @click="$router.push(`/supervisor/${record.id}/edit`)" v-if="record.visitStatus === 'draft'">编辑</a>
            <a @click="openDrawer(record)" v-else-if="record.visitStatus === 'pending_confirm'">查看</a>
            <a @click="openDrawer(record)" v-else-if="record.visitStatus === 'objection'">查看异议</a>
            <a @click="openDrawer(record)" v-else-if="record.visitStatus === 'in_progress'">审核</a>
            <a @click="openDrawer(record)" v-else-if="record.visitStatus === 'completed'">详情</a>
            <a @click="openDrawer(record)" v-else>查看</a>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- Detail drawer -->
    <a-drawer v-model:open="drawerOpen" :width="560" placement="right" :closable="false" class="visit-drawer">
      <template v-if="cur">
        <div class="drawer-wrap">
        <!-- Header -->
        <div class="drawer-header">
          <div>
            <span class="badge" :class="visitBadgeCls(cur.visitStatus)">{{ visitStatusLabel(cur.visitStatus) }}</span>
            <h2 class="drawer-title">{{ cur.visitNo }} · {{ cur.storeName }}</h2>
          </div>
          <a-button class="drawer-close" @click="drawerOpen = false">关闭</a-button>
        </div>

        <div class="drawer-body">
          <!-- Summary panel -->
          <section class="info-panel subtle">
            <h3>本次拜访结论</h3>
            <div class="summary-grid">
              <div class="summary-item"><span>确认对象</span><strong v-html="confirmPersonHtml"></strong></div>
              <div class="summary-item" v-if="bizData"><span>上月 GMV</span><strong>{{ bizData.gmv || '--' }}</strong></div>
              <div class="summary-item" v-if="bizData"><span>目标达成率</span><strong>{{ bizData.targetRate || '--' }}</strong></div>
              <div class="summary-item" v-if="bizData"><span>最近评级</span><strong>{{ bizData.recentRatings ? bizData.recentRatings.map((r:any)=>r.rating).join(' / ') : '--' }}</strong></div>
            </div>
          </section>

          <!-- Objection -->
          <section class="info-panel subtle objection-panel" v-if="cur.visitStatus === 'objection' && cur.objectionReason">
            <h3>异议说明</h3>
            <p class="objection-text">{{ cur.objectionReason }}</p>
          </section>

          <!-- Communication records -->
          <section class="info-panel" v-if="cur.lastIssueReview || cur.currentFocus || cur.improvementFocus || cur.storeFeedback">
            <h3>沟通记录结构</h3>
            <div class="task-list">
              <article class="task-item" v-if="cur.lastIssueReview">
                <header><strong>回顾上次访店问题与行动追踪</strong></header>
                <p>{{ cur.lastIssueReview }}</p>
              </article>
              <article class="task-item" v-if="cur.currentFocus">
                <header><strong>门店当下重点关注</strong></header>
                <p>{{ cur.currentFocus }}</p>
              </article>
              <article class="task-item" v-if="cur.improvementFocus">
                <header><strong>本月提升重点沟通记录</strong></header>
                <p>{{ cur.improvementFocus }}</p>
              </article>
              <article class="task-item" v-if="cur.storeFeedback">
                <header><strong>门店 & 加盟商反馈与所需支持</strong></header>
                <p>{{ cur.storeFeedback }}</p>
              </article>
            </div>
          </section>

          <!-- Actions -->
          <section class="info-panel" v-if="actions.length">
            <h3>行动计划</h3>
            <div class="task-list">
              <article class="task-item" v-for="a in actions" :key="a.id">
                <header>
                  <strong>{{ a.actionName }}</strong>
                  <span class="badge" :class="actionBadgeCls(a.taskStatus)">{{ actionStatusLabel(a.taskStatus) }}</span>
                </header>
                <div class="action-rows">
                  <div class="action-row"><span class="ar-label">目标值</span><span class="ar-value">{{ a.targetValue || '--' }}</span></div>
                  <div class="action-row"><span class="ar-label">具体动作</span><span class="ar-value">{{ a.specificAction || '--' }}</span></div>
                  <div class="action-row"><span class="ar-label">追踪时间</span><span class="ar-value">{{ a.trackingTime || '--' }}<span v-if="isLateSubmit(a)" class="overdue-mark">（逾期提交）</span></span></div>
                  <div class="action-row"><span class="ar-label">负责人</span><span class="ar-value">{{ confirmPersonLabel(a.responsibleRole) }}</span></div>
                </div>
                <!-- Complete feedback -->
                <div v-if="a.completeNote" class="feedback-block">
                  <strong>完成反馈</strong>
                  <div class="feedback-text">{{ a.completeNote }}</div>
                  <div v-if="actionImages(a).length" class="feedback-img-row">
                    <a-image v-for="(img, i) in actionImages(a)" :key="i" :src="img" :width="64" :height="64" style="border-radius:6px;object-fit:cover;cursor:pointer" />
                  </div>
                  <div class="feedback-meta">提交时间：{{ a.submittedAt || '--' }}</div>
                </div>
              </article>
            </div>
          </section>

          <!-- Review section -->
          <section class="info-panel subtle" v-if="hasReviewableActions">
            <h3>督导审核</h3>
            <p class="review-text">门店已提交完成反馈，图片为选填。本页用于督导确认完成质量，必要时可退回补充。</p>
          </section>
        </div>

        <!-- Footer: review actions -->
        <div class="drawer-footer" v-if="hasReviewableActions">
          <a-button @click="bulkReject">退回补充</a-button>
          <a-button type="primary" @click="bulkApprove">审核通过</a-button>
        </div>

        <!-- Footer: pending confirm → can recall -->
        <div class="drawer-footer" v-if="cur.visitStatus === 'pending_confirm'">
          <a-button @click="$router.push(`/supervisor/${cur.id}/edit`)">退回补充</a-button>
        </div>

        <!-- Footer: objection → can modify -->
        <div class="drawer-footer" v-if="cur.visitStatus === 'objection'">
          <a-button type="primary" @click="$router.push(`/supervisor/${cur.id}/edit`)">修改</a-button>
        </div>
        </div>
      </template>
    </a-drawer>

    <!-- Reject modal -->
    <a-modal v-model:open="rejectModal.open" title="审核退回" @ok="confirmReject" ok-text="确认退回" cancel-text="取消">
      <a-textarea v-model:value="rejectModal.reason" placeholder="退回原因，门店可见" :rows="3" style="margin-bottom:8px" />
      <a-date-picker v-model:value="rejectModal.newDate" placeholder="调整追踪时间（可选）" style="width:100%" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { getSupervisorVisits, getSupervisorVisitDetail, approveAction, rejectAction, getSupervisorStores, getSupervisorOptions } from '../../api/supervisor'

const loading = ref(false)
const list = ref<any[]>([])
const storeOptions = ref<any[]>([])
const supervisorOptions = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const filters = reactive({
  storeId: '', supervisorName: '', visitStatus: '', confirmStatus: '',
  hasOverdue: undefined as any, keyword: '', visitDateStart: '', visitDateEnd: ''
})
const dateRange = ref<any>(null)

const drawerOpen = ref(false)
const cur = ref<any>(null)
const bizData = ref<any>(null)
const actions = ref<any[]>([])

const rejectModal = reactive({ open: false, actionId: 0, reason: '', newDate: null as any })

// Stats
const stats = reactive({ pendingConfirm: 0, objection: 0, inProgressTasks: 0, overdueTasks: 0 })

// Columns
const cols = [
  { title: '拜访单', key: 'visitNo', width: '18%', fixed: 'left' as const },
  { title: '门店', dataIndex: 'storeName', width: '14%', ellipsis: true },
  { title: '督导', dataIndex: 'supervisorName', width: '12%' },
  { title: '拜访日期', dataIndex: 'visitDate', width: '12%' },
  { title: '拜访状态', key: 'visitStatus', width: '12%' },
  { title: '确认状态', key: 'confirmStatus', width: '12%' },
  { title: '任务进度', key: 'progress', width: '14%' },
  { title: '操作', key: 'action', width: '12%', fixed: 'right' as const },
]

const hasReviewableActions = computed(() => actions.value.some((a: any) => a.taskStatus === 'pending_review'))
const confirmPersonHtml = computed(() => {
  const parts = []
  if (cur.value?.confirmManagerName) parts.push(`${cur.value.confirmManagerName}<em style="font-style:normal;font-size:11px;padding:1px 5px;border-radius:4px;font-weight:500;background:#E7F4EB;color:#2F8F57;margin-left:4px">店长</em>`)
  if (cur.value?.confirmOwnerName) parts.push(`${cur.value.confirmOwnerName}<em style="font-style:normal;font-size:11px;padding:1px 5px;border-radius:4px;font-weight:500;background:#FFF3E0;color:#E58A2D;margin-left:4px">老板</em>`)
  if (!parts.length) return confirmPersonLabel(cur.value?.confirmPersonType || '')
  return parts.join(' / ')
})
// Badge classes matching prototype
function visitBadgeCls(s: string) {
  return ({ draft: 'default', pending_confirm: 'info', objection: 'warning', in_progress: 'in-progress', completed: 'default' } as any)[s] || 'default'
}
function confirmBadgeCls(s: string) {
  return ({ unsent: 'default', pending: 'warning', confirmed: 'primary', objected: 'danger' } as any)[s] || 'default'
}
function actionBadgeCls(s: string) {
  return ({ pending: 'default', overdue: 'warning', pending_review: 'primary', returned: 'danger', completed: 'default' } as any)[s] || 'default'
}

function visitStatusLabel(s: string) {
  return ({ draft: '草稿', pending_confirm: '待确认', objection: '异议处理中', in_progress: '跟进中', completed: '已完成' } as any)[s] || s
}
function confirmStatusLabel(s: string) {
  return ({ unsent: '未发送', pending: '小程序待确认', confirmed: '已确认', objected: '已提出异议' } as any)[s] || s
}
function actionStatusLabel(s: string) {
  return ({ pending: '待执行', overdue: '逾期', pending_review: '待审核', returned: '已退回', completed: '已完成' } as any)[s] || s
}
function confirmPersonLabel(t: string) {
  return ({ store_manager: '店长', owner: '加盟商老板', both: '店长 / 加盟商老板' } as any)[t] || t || '--'
}
function actionImages(a: any): string[] {
  if (!a.completeImages) return []
  return String(a.completeImages).split(',').filter(Boolean).map(imgUrl)
}
function imgUrl(url: string): string {
  if (!url) return ''
  return url.replace('http://162.14.122.80:30260', 'https://www.xzcpc-9pd.top')
}
function isLateSubmit(a: any) {
  if (!a.submittedAt || !a.trackingTime) return false
  return a.submittedAt.substring(0, 10) > a.trackingTime
}
function progressPct(r: any) {
  if (!r.actionCount) return 0
  return Math.round((r.completedCount || 0) / r.actionCount * 100)
}

function onDateChange(dates: any) {
  if (dates && dates.length === 2) {
    filters.visitDateStart = dayjs(dates[0]).format('YYYY-MM-DD')
    filters.visitDateEnd = dayjs(dates[1]).format('YYYY-MM-DD')
  } else {
    filters.visitDateStart = ''; filters.visitDateEnd = ''
  }
  handleSearch()
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await getSupervisorVisits({
      storeId: filters.storeId || undefined,
      supervisorName: filters.supervisorName || undefined,
      visitStatus: filters.visitStatus || undefined,
      confirmStatus: filters.confirmStatus || undefined,
      hasOverdue: filters.hasOverdue,
      visitDateStart: filters.visitDateStart || undefined,
      visitDateEnd: filters.visitDateEnd || undefined,
      keyword: filters.keyword || undefined,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })
    list.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally { loading.value = false }
}

async function fetchStats() {
  try {
    // Fetch all visits to compute stats (lightweight summary)
    const res: any = await getSupervisorVisits({ pageNum: 1, pageSize: 1 })
    const allRes: any = await getSupervisorVisits({ pageNum: 1, pageSize: 9999 })
    const records = allRes.data?.records || []
    stats.pendingConfirm = records.filter((r: any) => r.visitStatus === 'pending_confirm').length
    stats.objection = records.filter((r: any) => r.visitStatus === 'objection').length
    stats.inProgressTasks = records.reduce((s: number, r: any) => s + ((r.actionCount || 0) - (r.completedCount || 0)), 0)
    stats.overdueTasks = records.reduce((s: number, r: any) => s + (r.overdueCount || 0), 0)
  } catch { /* ignore */ }
}

function handleSearch() { pagination.current = 1; fetchList() }

async function loadStores() {
  try {
    const [storesRes, optionsRes] = await Promise.all([
      getSupervisorStores(),
      getSupervisorOptions(),
    ])
    storeOptions.value = (storesRes as any).data || storesRes || []
    supervisorOptions.value = (optionsRes as any).data || optionsRes || []
  } catch { /* */ }
}

function resetFilters() {
  filters.storeId = ''; filters.supervisorName = ''; filters.visitStatus = ''
  filters.confirmStatus = ''; filters.hasOverdue = undefined; filters.keyword = ''
  filters.visitDateStart = ''; filters.visitDateEnd = ''
  dateRange.value = null
  handleSearch()
}

function handleTable(p: any) { pagination.current = p.current; fetchList() }

async function openDrawer(record: any) {
  cur.value = record
  drawerOpen.value = true
  bizData.value = null
  actions.value = []
  try {
    const res: any = await getSupervisorVisitDetail(record.id)
    if (res.data) {
      const d = res.data
      cur.value = d.visit || d
      bizData.value = d.bizData?.bizData || d.bizData || null
      actions.value = d.actions || []
      historyVisits.value = d.historyVisits || []
    }
  } catch { /* keep row data */ }
}

async function handleApprove(actionId: number) {
  try {
    await approveAction(actionId)
    message.success('已通过')
    fetchList()
    openDrawer(cur.value)
  } catch (e: any) { message.error(e.message || '操作失败') }
}

function showReject(a: any) {
  rejectModal.actionId = a.id
  rejectModal.reason = ''
  rejectModal.newDate = null
  rejectModal.open = true
}

function bulkApprove() {
  const pending = actions.value.filter((a: any) => a.taskStatus === 'pending_review')
  if (!pending.length) return
  // Review first pending action
  handleApprove(pending[0].id)
}

function bulkReject() {
  const pending = actions.value.filter((a: any) => a.taskStatus === 'pending_review')
  if (pending.length) showReject(pending[0])
}

async function confirmReject() {
  try {
    const newDate = rejectModal.newDate ? dayjs(rejectModal.newDate).format('YYYY-MM-DD') : undefined
    await rejectAction(rejectModal.actionId, rejectModal.reason || undefined, newDate)
    rejectModal.open = false
    message.success('已退回')
    fetchList()
    openDrawer(cur.value)
  } catch (e: any) { message.error(e.message || '操作失败') }
}

onMounted(() => { loadStores(); fetchList(); fetchStats() })
</script>

<style scoped>
.visit-page { padding: 0 }

/* Title */
.page-title-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; gap: 16px }
.page-title { font-size: 22px; font-weight: 700; color: #1F2421; margin: 0 }
.page-subtitle { font-size: 13px; color: #66706A; margin: 4px 0 0 }

/* Metric cards */
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px }
.metric-card { background: #fff; border: 1px solid #E8ECE9; border-radius: 12px; padding: 20px }
.metric-label { font-size: 13px; color: #66706A; margin: 0 0 8px }
.metric-value { font-size: 32px; font-weight: 700; color: #1F2421; margin: 0 0 6px }
.metric-value--warn { color: #E05A47 }
.metric-note { font-size: 12px; color: #98A19C; margin: 0 }

/* Badge */
.badge { display: inline-flex; align-items: center; min-height: 24px; padding: 0 9px; border-radius: 999px; font-size: 12px; line-height: 18px; font-weight: 600; white-space: nowrap }
.badge.primary { color: #2F8F57; background: #E7F4EB }
.badge.warning { color: #9a4f1f; background: #fff8ee }
.badge.danger { color: #b74132; background: #fff4f2 }
.badge.info { color: #435f7c; background: #eef4fa }
.badge.in-progress { color: #2F6FCF; background: #E3F0FF }
.badge.default { color: #66706A; background: #F1F2F0 }

/* Visit cell */
.visit-cell { display: grid; gap: 4px; min-width: 0 }
.visit-cell strong { overflow: hidden; color: #1F2421; font-size: 13px; line-height: 20px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap }
.visit-cell span { overflow: hidden; color: #98A19C; font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap }

/* Progress */
.progress-line { display: grid; gap: 6px; min-width: 0 }
.progress-line p { margin: 0; color: #1F2421; font-size: 13px; line-height: 20px; font-weight: 600 }
.progress-track { height: 6px; overflow: hidden; border-radius: 999px; background: #edf0ee }
.progress-fill { height: 100%; border-radius: inherit; background: #2F8F57; transition: width .3s }
.progress-pending { color: #98A19C; font-size: 13px }
.overdue-mark { color: #E05A47; font-weight: 400 }

/* Filter */
.filter-card { margin-bottom: 12px }
.filter-label { font-size: 13px; color: #66706A; margin-bottom: 4px }

/* Drawer */
:deep(.visit-drawer .ant-drawer-body) { display: flex; flex-direction: column; height: 100%; padding: 0; overflow: hidden }
.drawer-wrap { display: flex; flex-direction: column; height: 100% }
.drawer-header { flex-shrink: 0; padding: 24px 8px 0; display: flex; align-items: flex-start; justify-content: space-between; gap: 16px }
.drawer-title { margin: 8px 0 0; font-size: 20px; line-height: 28px; font-weight: 600; color: #1F2421 }
.drawer-close { color: #66706A }
.drawer-body { flex: 1; overflow: auto; display: grid; gap: 16px; padding: 16px 8px 24px }

/* Info panels */
.info-panel { padding: 16px; border: 1px solid #E8ECE9; border-radius: 8px; background: #fff }
.info-panel.subtle { background: #F7F8F6 }
.info-panel h3 { margin: 0 0 12px; font-size: 14px; line-height: 22px; font-weight: 600; color: #1F2421 }
.objction-panel { background: #FFF4F2; border-color: #FADBD8 }
.objction-panel h3 { color: #E05A47 }
.objection-text { margin: 0; color: #b74132; font-size: 13px; line-height: 20px }

/* Summary grid */
.summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px }
.summary-item { padding: 12px; border: 1px solid #E8ECE9; border-radius: 8px; background: #fbfcfb }
.summary-item span { display: block; color: #98A19C; font-size: 12px; line-height: 18px }
.summary-item strong { display: block; margin-top: 6px; color: #1F2421; font-size: 14px; line-height: 22px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }

/* Task list */
.task-list { display: grid; gap: 10px }
.task-item { display: grid; gap: 8px; padding: 12px; border: 1px solid #E8ECE9; border-radius: 8px; background: #fbfcfb }
.task-item header { display: flex; align-items: center; justify-content: space-between; gap: 12px }
.task-item strong { color: #1F2421; font-size: 14px; line-height: 22px }
.task-item p { margin: 0; color: #66706A; font-size: 13px; line-height: 20px }
.action-rows { display: grid; gap: 4px; margin-top: 8px }
.action-row { display: flex }
.ar-label { width: 64px; flex-shrink: 0; font-size: 13px; color: #98A19C }
.ar-value { font-size: 13px; color: #66706A; flex: 1 }
.overdue-mark { color: #E05A47; font-size: 12px }
.feedback-block { margin-top: 8px; padding: 10px 12px; background: #fff; border-radius: 6px; border: 1px solid #E8ECE9 }
.feedback-block strong { font-size: 13px; color: #1F2421 }
.feedback-text { font-size: 13px; color: #66706A; line-height: 20px; margin-top: 4px }
.feedback-img-row { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap }
.feedback-meta { color: #98A19C; font-size: 12px; margin-top: 6px }
.review-text { margin: 0; color: #66706A; font-size: 13px; line-height: 20px }
.role-tag { font-size: 11px; padding: 1px 6px; border-radius: 4px; font-weight: 500; background: #E7F4EB; color: #2F8F57; margin-left: 4px; white-space: nowrap }
.role-tag.owner { background: #FFF3E0; color: #E58A2D }

/* Drawer footer */
.drawer-footer { flex-shrink: 0; display: flex; justify-content: flex-end; gap: 10px; padding: 8px 0; margin: 0 8px; border-top: 1px solid #E8ECE9; background: #fff }

@media (max-width: 900px) {
  .metric-grid { grid-template-columns: repeat(2, 1fr) }
}
</style>
