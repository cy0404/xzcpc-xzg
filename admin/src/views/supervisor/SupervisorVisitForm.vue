<template>
  <div class="visit-form-page">
    <!-- Hero -->
    <div class="hero">
      <div>
        <h1 class="page-title">创建经营辅导拜访单</h1>
        <p class="page-subtitle">督导带电脑到店，基于固定展示的上月经营数据和历史拜访记录，与店长或加盟商现场沟通并填写。</p>
      </div>
      <a-button class="back-btn" @click="$router.push('/supervisor')">返回台账</a-button>
    </div>

    <!-- Main layout: form + side panel -->
    <div class="form-layout">
      <!-- Left: main form -->
      <div class="form-stack">
        <!-- Base info -->
        <section class="card form-card">
          <h2>基础信息</h2>
          <div class="form-grid">
            <div class="form-field">
              <label>拜访门店</label>
              <a-select v-model:value="form.storeId" placeholder="选择门店" style="width:100%;height:38px"
                show-search :field-names="{label:'mendianmingcheng',value:'id'}"
                :options="storeOptions" @change="onStoreChange" />
            </div>
            <div class="form-field">
              <label>督导</label>
              <a-select v-model:value="form.supervisorName" placeholder="选择督导" style="width:100%;height:38px" :options="supervisorOptions" />
            </div>
            <div class="form-field">
              <label>拜访日期</label>
              <a-date-picker v-model:value="form.visitDate" style="width:100%;height:38px" placeholder="选择日期" />
            </div>
            <div class="form-field">
              <label>店长确认人</label>
              <a-select v-model:value="form.confirmManager" placeholder="不选择" style="width:100%;height:38px" allow-clear
                :options="managerOptions" />
            </div>
            <div class="form-field">
              <label>加盟商老板确认人</label>
              <a-select v-model:value="form.confirmOwner" placeholder="不选择" style="width:100%;height:38px" allow-clear
                :options="ownerOptions" />
            </div>
          </div>
        </section>

        <!-- Store data -->
        <section class="card form-card" v-if="bizData">
          <h2>上个月经营数据</h2>
          <div class="data-grid">
            <div class="data-item"><span>GMV</span><strong>{{ bizData.gmv || '--' }}</strong><small>上月</small></div>
            <div class="data-item"><span>目标达成率</span><strong>{{ bizData.targetRate || '--' }}</strong><small>目标值</small></div>
            <div class="data-item"><span>实收率</span><strong>{{ bizData.realCollectionRate || '--' }}</strong><small>退款与抹零后</small></div>
            <div class="data-item"><span>客单价</span><strong>{{ bizData.avgOrderPrice || '--' }}</strong><small>环比</small></div>
            <div class="data-item"><span>EBITDA</span><strong>{{ bizData.ebitda || '--' }}</strong><small>利润表现</small></div>
            <div class="data-item"><span>人效</span><strong>{{ bizData.staffEfficiency || '--' }}</strong><small>人/日销售额</small></div>
            <div class="data-item"><span>QSC 分数</span><strong>{{ bizData.qscScore || '--' }}</strong><small>最近一次评分</small></div>
            <div class="data-item"><span>差评率</span><strong>{{ bizData.negativeRate || '--' }}</strong><small>优秀线参照</small></div>
          </div>
        </section>

        <!-- Communication records -->
        <section class="card form-card">
          <h2>沟通记录</h2>
          <div class="form-stack">
            <div class="form-field">
              <label>回顾上次访店问题与行动追踪</label>
              <a-textarea v-model:value="form.lastIssueReview" placeholder="记录上次问题回顾和行动项完成情况" :rows="3" class="form-textarea" />
            </div>
            <div class="form-field">
              <label>门店当下重点关注（店长分享）</label>
              <a-textarea v-model:value="form.currentFocus" placeholder="记录店长现场分享的当前关注点" :rows="3" class="form-textarea" />
            </div>
            <div class="form-field">
              <label>本月提升重点沟通记录</label>
              <a-textarea v-model:value="form.improvementFocus" placeholder="记录本月经营改善方向和沟通结论" :rows="3" class="form-textarea" />
            </div>
            <div class="form-field">
              <label>门店 & 加盟商反馈与所需支持</label>
              <a-textarea v-model:value="form.storeFeedback" placeholder="记录门店希望的运营/供应链/培训支持" :rows="3" class="form-textarea" />
            </div>
          </div>
        </section>

        <!-- Action plan -->
        <section class="card form-card">
          <div class="action-plan-header">
            <div>
              <h2>行动计划</h2>
              <span class="action-plan-count">已添加 {{ form.actions.length }} 项行动计划</span>
            </div>
            <a-button class="btn-success" @click="openActionModal()">新增行动项</a-button>
          </div>
          <div class="action-list" v-if="form.actions.length">
            <article class="action-card" v-for="(a, i) in form.actions" :key="i">
              <div class="action-card-header">
                <div>
                  <h3 class="action-card-title">{{ a.actionName || '未命名' }}</h3>
                  <div class="action-card-meta">
                    <span class="action-chip">目标：{{ a.targetValue || '--' }}</span>
                    <span class="action-chip">追踪：{{ a.trackingTime ? dayjs(a.trackingTime).format('YYYY-MM-DD') : '--' }}</span>
                    <span class="action-chip">负责人：{{ a.responsibleRole === 'owner' ? '加盟商老板' : '店长' }}</span>
                  </div>
                </div>
                <div class="action-card-actions">
                  <a-button type="link" size="small" @click="openActionModal(i)">编辑</a-button>
                  <a-button type="link" size="small" danger @click="removeAction(i)">删除</a-button>
                </div>
              </div>
              <p class="action-card-desc">{{ a.specificAction || '暂无描述' }}</p>
            </article>
          </div>
          <div v-else style="color:#98A19C;font-size:13px;padding:12px 0">暂无行动项，点击上方按钮新增。</div>
        </section>

      </div>

      <!-- Right: side panel -->
      <aside class="side-panel" aria-label="拜访辅助信息">
        <section class="card form-card">
          <h2>最近 3 次门店评级</h2>
          <div v-if="ratings.length">
            <div class="rating-row" v-for="r in ratings" :key="r.month">
              <strong>{{ r.month }}</strong>
              <strong>{{ r.rating }}</strong>
            </div>
          </div>
          <div v-else style="color:#98A19C;font-size:13px">选择门店后加载</div>
        </section>

        <section class="card form-card">
          <div class="side-title-row">
            <h2>历史拜访记录</h2>
            <a @click="$router.push('/supervisor')">查看全部</a>
          </div>
          <div class="history-list" v-if="historyVisits.length">
            <div class="history-row" v-for="h in filteredHistory" :key="h.id" @click="openHistoryDrawer(h)">
              <strong>{{ fmtShortDate(h.visitDate) }}</strong>
              <span>{{ h.improvementFocus || '暂无记录' }}</span>
              <span class="history-state">{{ historyState(h) }}</span>
              <span class="history-arrow">›</span>
            </div>
          </div>
          <div v-else style="color:#98A19C;font-size:13px">暂无历史记录</div>
        </section>
      </aside>
    </div>

    <!-- History detail drawer -->
    <a-drawer v-model:open="historyDrawerOpen" :width="520" placement="right" :closable="false">
      <template v-if="historyCur">
        <div class="drawer-header">
          <div>
            <span class="badge" :class="historyBadgeCls(historyCur.visitStatus)">{{ historyStatusLabel(historyCur.visitStatus) }}</span>
            <h2 class="drawer-title">{{ historyCur.visitNo }} · {{ historyCur.storeName }}</h2>
          </div>
          <a-button type="text" class="drawer-close" @click="historyDrawerOpen = false">关闭</a-button>
        </div>
        <div class="drawer-body">
          <section class="info-panel subtle">
            <h3>拜访信息</h3>
            <div class="info-row"><span class="info-label">门店</span><span class="info-value">{{ historyCur.storeName }}</span></div>
            <div class="info-row"><span class="info-label">督导</span><span class="info-value">{{ historyCur.supervisorName }}</span></div>
            <div class="info-row"><span class="info-label">日期</span><span class="info-value">{{ historyCur.visitDate }}</span></div>
          </section>
          <section class="info-panel" v-if="historyCur.lastIssueReview || historyCur.currentFocus || historyCur.improvementFocus || historyCur.storeFeedback">
            <h3>沟通记录</h3>
            <div v-if="historyCur.lastIssueReview" class="comm-snippet"><strong>回顾上次访店</strong><p>{{ historyCur.lastIssueReview }}</p></div>
            <div v-if="historyCur.currentFocus" class="comm-snippet"><strong>门店当下重点关注</strong><p>{{ historyCur.currentFocus }}</p></div>
            <div v-if="historyCur.improvementFocus" class="comm-snippet"><strong>本月提升重点</strong><p>{{ historyCur.improvementFocus }}</p></div>
            <div v-if="historyCur.storeFeedback" class="comm-snippet"><strong>门店反馈与所需支持</strong><p>{{ historyCur.storeFeedback }}</p></div>
          </section>
          <section class="info-panel" v-if="historyActions.length">
            <h3>行动计划</h3>
            <div class="task-list">
              <article class="task-item" v-for="a in historyActions" :key="a.id">
                <header><strong>{{ a.actionName }}</strong><span class="badge" :class="historyActionCls(a.taskStatus)">{{ historyActionLabel(a.taskStatus) }}</span></header>
                <p>{{ a.specificAction || '--' }} · 追踪时间：{{ a.trackingTime || '--' }}</p>
              </article>
            </div>
          </section>
        </div>
      </template>
    </a-drawer>

    <!-- Action modal -->
    <a-modal v-model:open="actionModal.open" :title="actionModal.isEdit ? '编辑行动项' : '新增行动项'" width="680px" @ok="saveAction" ok-text="保存行动项" cancel-text="取消">
      <div class="modal-grid">
        <div class="form-field">
          <label>任务名称</label>
          <a-input v-model:value="actionModal.form.actionName" placeholder="如：提升套餐推荐率" />
        </div>
        <div class="form-field">
          <label>目标值</label>
          <a-input v-model:value="actionModal.form.targetValue" placeholder="如：客单价 32 元" />
        </div>
        <div class="form-field">
          <label>追踪时间</label>
          <a-date-picker v-model:value="actionModal.form.trackingTime" style="width:100%" placeholder="选择日期" />
        </div>
        <div class="form-field">
          <label>负责人</label>
          <a-select v-model:value="actionModal.form.responsibleRole" style="width:100%">
            <a-select-option value="store_manager">店长</a-select-option>
            <a-select-option value="owner">加盟商老板</a-select-option>
          </a-select>
        </div>
      </div>
      <div class="form-field" style="margin-top:14px">
        <label>具体动作</label>
        <a-textarea v-model:value="actionModal.form.specificAction" placeholder="门店需要执行的具体动作" :rows="3" />
      </div>
    </a-modal>

    <!-- Fixed footer -->
    <div class="create-footer">
      <a-button size="large" @click="$router.push('/supervisor')">取消</a-button>
      <a-button v-if="!isEdit || curVisitStatus === 'draft'" size="large" style="margin-left:12px" @click="handleSave('draft')" :loading="saving">保存草稿</a-button>
      <a-button type="primary" size="large" style="margin-left:12px" @click="handleSave('submit')" :loading="submitting">{{ isEdit && curVisitStatus !== 'draft' ? '重新提交' : '提交确认' }}</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  createSupervisorVisit, updateSupervisorVisit, getSupervisorVisitDetail,
  submitSupervisorVisit, handleObjection, getStoreBizData, getStoreEmployees,
  getSupervisorStores, getSupervisorOptions
} from '../../api/supervisor'

const route = useRoute()
const router = useRouter()
const isEdit = !!route.params.id
const visitId = Number(route.params.id) || 0

const storeOptions = ref<any[]>([])
const bizData = ref<any>(null)
const ratings = ref<any[]>([])
const historyVisits = ref<any[]>([])
const historyDrawerOpen = ref(false)
const historyCur = ref<any>(null)
const historyActions = ref<any[]>([])
const filteredHistory = computed(() => historyVisits.value.filter((h: any) => h.id !== visitId))
const managerOptions = ref<any[]>([])
const ownerOptions = ref<any[]>([])
const supervisorOptions = ref<any[]>([])
const saving = ref(false)
const submitting = ref(false)
const curVisitStatus = ref('')

const form = reactive({
  storeId: '',
  supervisorName: '',
  visitDate: null as any,
  confirmManager: null as any,
  confirmOwner: null as any,
  lastIssueReview: '',
  currentFocus: '',
  improvementFocus: '',
  storeFeedback: '',
  actions: [] as any[],
})

const actionModal = reactive({
  open: false, isEdit: false, editIndex: -1,
  form: { actionName: '', targetValue: '', specificAction: '', trackingTime: null as any, responsibleRole: 'store_manager' },
})

function fmtShortDate(d: string) { return d ? d.substring(5) : '--' }
function historyState(h: any) {
  if (h.visitStatus === 'completed') return '已闭环'
  const overdue = h.overdueCount || 0
  return overdue > 0 ? `${overdue} 逾期` : '跟进中'
}

function openActionModal(index?: number) {
  if (index !== undefined) {
    actionModal.isEdit = true
    actionModal.editIndex = index
    const a = form.actions[index]
    actionModal.form = { ...a, trackingTime: a.trackingTime || null }
  } else {
    actionModal.isEdit = false
    actionModal.editIndex = -1
    actionModal.form = { actionName: '', targetValue: '', specificAction: '', trackingTime: null, responsibleRole: 'store_manager' }
  }
  actionModal.open = true
}

function saveAction() {
  const item = {
    actionName: actionModal.form.actionName,
    targetValue: actionModal.form.targetValue,
    specificAction: actionModal.form.specificAction,
    trackingTime: actionModal.form.trackingTime,
    responsibleRole: actionModal.form.responsibleRole,
  }
  if (actionModal.isEdit) {
    form.actions[actionModal.editIndex] = item
  } else {
    form.actions.push(item)
  }
  actionModal.open = false
}

function removeAction(index: number) { form.actions.splice(index, 1) }

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

async function onStoreChange(storeId: string) {
  if (!storeId) {
    bizData.value = null; ratings.value = []; historyVisits.value = []
    managerOptions.value = []; ownerOptions.value = []
    form.confirmManager = null; form.confirmOwner = null
    return
  }
  // 加载员工
  try {
    const empRes: any = await getStoreEmployees(storeId)
    const emps = empRes.data || empRes || []
    managerOptions.value = emps
      .filter((e: any) => e.role && (e.role.includes('店长') || e.role.includes('经理')))
      .map((e: any) => ({ label: e.name, value: e.openid }))
    ownerOptions.value = emps
      .filter((e: any) => e.role && e.role.includes('老板'))
      .map((e: any) => ({ label: e.name, value: e.openid }))
  } catch { managerOptions.value = []; ownerOptions.value = [] }

  // 加载经营数据
  try {
    const res: any = await getStoreBizData(storeId)
    const data = res.data || res
    bizData.value = data.bizData || null
    ratings.value = bizData.value?.recentRatings || []
    historyVisits.value = data.historyVisits || []
  } catch { bizData.value = null; ratings.value = []; historyVisits.value = [] }
}

async function loadVisit() {
  try {
    const res: any = await getSupervisorVisitDetail(visitId)
    const d = res.data?.visit || res.data
    if (!d) return
    curVisitStatus.value = d.visitStatus || ''
    form.storeId = d.storeId || ''
    form.supervisorName = d.supervisorName || ''
    form.visitDate = d.visitDate ? dayjs(d.visitDate) : null
    // 编辑时：confirmPersonType 确定需不需要回填，具体人选等员工列表加载后匹配
    if (d.confirmPersonType === 'store_manager' || d.confirmPersonType === 'both') {
      form.confirmManager = d.confirmManagerOpenid || null
    }
    if (d.confirmPersonType === 'owner' || d.confirmPersonType === 'both') {
      form.confirmOwner = d.confirmOwnerOpenid || null
    }
    form.lastIssueReview = d.lastIssueReview || ''
    form.currentFocus = d.currentFocus || ''
    form.improvementFocus = d.improvementFocus || ''
    form.storeFeedback = d.storeFeedback || ''
    if (form.storeId) onStoreChange(form.storeId)
    const actions = res.data?.actions || []
    form.actions = actions.map((a: any) => ({
      actionName: a.actionName || '', targetValue: a.targetValue || '',
      specificAction: a.specificAction || '',
      trackingTime: a.trackingTime ? dayjs(a.trackingTime) : null,
      responsibleRole: a.responsibleRole || 'store_manager',
    }))
  } catch { message.error('加载拜访单失败') }
}

function buildConfirmPersonType() {
  const hasManager = !!form.confirmManager
  const hasOwner = !!form.confirmOwner
  if (hasManager && hasOwner) return 'both'
  if (hasOwner) return 'owner'
  if (hasManager) return 'store_manager'
  return 'store_manager' // default
}

function buildPayload() {
  return {
    storeId: form.storeId,
    supervisorName: form.supervisorName,
    visitDate: form.visitDate ? dayjs(form.visitDate).format('YYYY-MM-DD') : null,
    confirmPersonType: buildConfirmPersonType(),
    confirmManagerOpenid: form.confirmManager || null,
    confirmManagerName: form.confirmManager
      ? (managerOptions.value.find((o: any) => o.value === form.confirmManager)?.label || null)
      : null,
    confirmOwnerOpenid: form.confirmOwner || null,
    confirmOwnerName: form.confirmOwner
      ? (ownerOptions.value.find((o: any) => o.value === form.confirmOwner)?.label || null)
      : null,
    lastIssueReview: form.lastIssueReview,
    currentFocus: form.currentFocus,
    improvementFocus: form.improvementFocus,
    storeFeedback: form.storeFeedback,
    actions: form.actions.map((a: any) => ({
      actionName: a.actionName,
      targetValue: a.targetValue,
      specificAction: a.specificAction,
      trackingTime: a.trackingTime ? dayjs(a.trackingTime).format('YYYY-MM-DD') : null,
      responsibleRole: a.responsibleRole,
    })),
  }
}

async function handleSave(mode: 'draft' | 'submit') {
  if (!form.storeId) { message.warning('请选择门店'); return }
  if (!form.visitDate) { message.warning('请选择拜访日期'); return }

  const payload = buildPayload()

  if (mode === 'draft') {
    saving.value = true
    try {
      if (isEdit) {
        await updateSupervisorVisit(visitId, payload)
        message.success('已保存')
      } else {
        await createSupervisorVisit(payload)
        message.success('已保存草稿')
        router.push('/supervisor')
      }
    } catch (e: any) { message.error(e.message || '保存失败') }
    finally { saving.value = false }
  } else {
    submitting.value = true
    try {
      let id = visitId
      if (isEdit) {
        // 异议状态走 handleObjection，其他状态先保存再提交
        if (curVisitStatus === 'objection') {
          await handleObjection(id, payload)
        } else {
          await updateSupervisorVisit(id, payload)
        }
      } else {
        const res: any = await createSupervisorVisit(payload)
        id = res.data?.id || id
      }
      await submitSupervisorVisit(id)
      message.success('已提交确认')
      router.push('/supervisor')
    } catch (e: any) { message.error(e.message || '提交失败') }
    finally { submitting.value = false }
  }
}

async function openHistoryDrawer(h: any) {
  historyCur.value = h
  historyDrawerOpen.value = true
  try {
    const res: any = await getSupervisorVisitDetail(h.id)
    if (res.data) {
      const d = res.data
      historyCur.value = d.visit || d
      historyActions.value = d.actions || []
    }
  } catch { /* keep basic info */ }
}

function historyBadgeCls(s: string) { return ({ draft: 'default', pending_confirm: 'info', objection: 'warning', in_progress: 'primary', completed: 'default' } as any)[s] || 'default' }
function historyStatusLabel(s: string) { return ({ draft: '草稿', pending_confirm: '待确认', objection: '异议处理中', in_progress: '跟进中', completed: '已完成' } as any)[s] || s }
function historyActionCls(s: string) { return ({ pending: 'default', overdue: 'warning', pending_review: 'primary', returned: 'danger', completed: 'default' } as any)[s] || 'default' }
function historyActionLabel(s: string) { return ({ pending: '待执行', overdue: '已逾期', pending_review: '待审核', returned: '已退回', completed: '已完成' } as any)[s] || s }

onMounted(() => {
  loadStores()
  if (isEdit) loadVisit()
})
</script>

<style scoped>
.visit-form-page { padding-bottom: 100px }

/* Hero */
.hero { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 4px }
.page-title { font-size: 22px; font-weight: 700; color: #1F2421; margin: 0 }
.page-subtitle { font-size: 13px; color: #66706A; margin: 4px 0 0; max-width: 560px }
.back-btn { flex-shrink: 0 }

/* Layout */
.form-layout { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 20px; align-items: start }
.form-stack { display: grid; gap: 16px }

/* Card */
.card { background: #fff; border: 1px solid #E8ECE9; border-radius: 12px }
.form-card { padding: 18px }
.form-card h2 { margin: 0 0 14px; color: #1F2421; font-size: 16px; line-height: 24px; font-weight: 600 }

/* Form fields */
.form-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px }
.form-field { display: grid; gap: 6px }
.form-field label { color: #66706A; font-size: 12px; line-height: 18px }
.form-textarea { border-radius: 8px }
:deep(.form-textarea textarea) { background: #F7F8F6; border-color: #E8ECE9; border-radius: 8px }

/* Data grid (store stats) */
.data-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px }
.data-item { min-height: 84px; padding: 12px; border: 1px solid #E8ECE9; border-radius: 8px; background: #fbfcfb }
.data-item span { display: block; color: #98A19C; font-size: 12px; line-height: 18px }
.data-item strong { display: block; margin-top: 8px; color: #1F2421; font-size: 20px; line-height: 28px; font-weight: 700 }
.data-item small { display: block; margin-top: 4px; color: #66706A; font-size: 12px; line-height: 18px }

/* Action plan */
.action-plan-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px }
.action-plan-header h2 { margin-bottom: 0 }
.action-plan-count { color: #98A19C; font-size: 12px; line-height: 18px; white-space: nowrap }
.btn-success { color: #2F8F57; border-color: #2F8F57 }
.action-list { display: grid; gap: 12px }
.action-card { padding: 14px; border: 1px solid #E8ECE9; border-radius: 8px; background: #fbfcfb }
.action-card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px }
.action-card-title { margin: 0; color: #1F2421; font-size: 15px; line-height: 23px; font-weight: 600 }
.action-card-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px }
.action-chip { display: inline-flex; align-items: center; min-height: 26px; padding: 0 9px; border-radius: 999px; color: #66706A; background: #fff; border: 1px solid #E8ECE9; font-size: 12px; line-height: 18px; font-weight: 600 }
.action-card-desc { margin: 10px 0 0; color: #66706A; font-size: 13px; line-height: 21px }
.action-card-actions { display: flex; align-items: center; gap: 12px; white-space: nowrap; flex-shrink: 0 }

/* Fixed footer */
.create-footer { position: fixed; left: 0; right: 0; bottom: 0; background: #fff; border-top: 1px solid #E8ECE9; padding: 16px 24px; display: flex; justify-content: flex-end; z-index: 100 }

/* Side panel */
.side-panel { position: sticky; top: 24px; display: grid; gap: 16px }
.side-title-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0 0 14px }
.side-title-row h2 { margin-bottom: 0 }

/* Rating */
.rating-row { display: grid; grid-template-columns: 92px minmax(0, 1fr); gap: 12px; align-items: center; padding: 11px 0; border-bottom: 1px solid #E8ECE9; color: #66706A; font-size: 13px; line-height: 20px }
.rating-row:last-child { border-bottom: 0 }
.rating-row strong { color: #1F2421 }
.rating-row strong:last-child { justify-self: end }

/* History */
.history-list { display: grid; gap: 4px }
.history-row { display: grid; grid-template-columns: 72px minmax(0, 1fr) 72px 18px; gap: 12px; align-items: center; min-height: 54px; padding: 10px 8px; margin: 0 -8px; border-radius: 6px; color: #66706A; font-size: 13px; line-height: 20px; transition: background-color .16s }
.history-row:hover { background: #F7F8F6 }
.history-row strong { color: #1F2421 }
.history-state { color: #1F2421; font-weight: 600; white-space: nowrap }
.history-arrow { color: #98A19C; font-size: 16px; text-align: right }

/* Modal */
.modal-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px }

/* Drawer (same as list page) */
.drawer-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px }
.drawer-title { margin: 8px 0 0; font-size: 20px; line-height: 28px; font-weight: 600; color: #1F2421 }
.drawer-close { color: #66706A; font-size: 14px }
.drawer-body { display: grid; gap: 16px }
.info-panel { padding: 16px; border: 1px solid #E8ECE9; border-radius: 8px; background: #fff }
.info-panel.subtle { background: #F7F8F6 }
.info-panel h3 { margin: 0 0 12px; font-size: 14px; line-height: 22px; font-weight: 600; color: #1F2421 }
.info-row { display: flex; padding: 6px 0 }
.info-label { width: 80px; flex-shrink: 0; font-size: 14px; color: #98A19C }
.info-value { flex: 1; font-size: 14px; color: #1F2421 }
.badge { display: inline-flex; align-items: center; min-height: 24px; padding: 0 9px; border-radius: 999px; font-size: 12px; line-height: 18px; font-weight: 600; white-space: nowrap }
.badge.primary { color: #2F8F57; background: #E7F4EB }
.badge.warning { color: #9a4f1f; background: #fff8ee }
.badge.danger { color: #b74132; background: #fff4f2 }
.badge.info { color: #435f7c; background: #eef4fa }
.badge.default { color: #66706A; background: #F1F2F0 }
.task-list { display: grid; gap: 10px }
.task-item { display: grid; gap: 8px; padding: 12px; border: 1px solid #E8ECE9; border-radius: 8px; background: #fbfcfb }
.task-item header { display: flex; align-items: center; justify-content: space-between; gap: 12px }
.task-item strong { color: #1F2421; font-size: 14px; line-height: 22px }
.task-item p { margin: 0; color: #66706A; font-size: 13px; line-height: 20px }
.comm-snippet { margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid #E8ECE9 }
.comm-snippet:last-child { border-bottom: none; margin-bottom: 0; padding-bottom: 0 }
.comm-snippet strong { color: #98A19C; font-size: 12px; display: block; margin-bottom: 4px }
.comm-snippet p { color: #1F2421; font-size: 13px; margin: 0; white-space: pre-wrap }

@media (max-width: 960px) {
  .form-layout { grid-template-columns: 1fr }
  .side-panel { position: static }
  .form-grid { grid-template-columns: repeat(2, 1fr) }
  .data-grid { grid-template-columns: repeat(2, 1fr) }
}
</style>
