<template>
  <div class="diff-detail-page">
    <PageModuleTabs />

    <div class="result-header">
      <a-button type="text" class="back-btn" @click="$router.back()">
        <ArrowLeftOutlined />
      </a-button>
      <h1 class="result-title">盘点差异明细</h1>
    </div>

    <!-- 任务信息 -->
    <a-card class="info-card" :bordered="false" :loading="loading">
      <a-row :gutter="16">
        <a-col :span="6">
          <div class="info-item"><span class="info-label">门店</span><span class="info-value">{{ taskInfo.storeName || '--' }}</span></div>
        </a-col>
        <a-col :span="6">
          <div class="info-item"><span class="info-label">督导</span><span class="info-value">{{ taskInfo.supervisorName || '--' }}</span></div>
        </a-col>
        <a-col :span="6">
          <div class="info-item"><span class="info-label">盘点月份</span><span class="info-value">{{ taskInfo.taskMonth || '--' }}</span></div>
        </a-col>
        <a-col :span="6">
          <div class="info-item"><span class="info-label">提交时间</span><span class="info-value">{{ formatDate(taskInfo.submittedAt) }}</span></div>
        </a-col>
      </a-row>
    </a-card>

    <!-- 操作栏 -->
    <a-card :bordered="false" style="margin-bottom:12px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <div>
          <a-tag color="orange">大差异: {{ largeCount }} 项</a-tag>
          <a-tag color="default">总差异项: {{ differences.length }} 项</a-tag>
          <a-input v-model:value="searchKeyword" placeholder="搜索物料名称" allow-clear size="small" style="width:200px;margin-left:12px" />
        </div>
        <div>
          <a-button type="primary" :loading="calculating" @click="doCalculate">重新计算</a-button>
        </div>
      </div>
    </a-card>

    <!-- 差异明细表格 -->
    <a-card :bordered="false" :loading="loading">
      <a-table
        :columns="columns"
        :data-source="filteredDifferences"
        :pagination="false"
        row-key="id"
        size="middle"
        :scroll="{ y: 'calc(100vh - 380px)' }"
        :row-class-name="rowClass"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'materialName'">
            <div class="strong-text">{{ record.materialName }}</div>
            <div class="sub-text" v-if="record.spec">{{ record.spec }}</div>
          </template>
          <template v-else-if="column.key === 'unit'">{{ record.unit || '--' }}</template>
          <template v-else-if="column.key === 'theoreticalQty'"><strong>{{ fmt(record.theoreticalQty) }}</strong></template>
          <template v-else-if="column.key === 'actualQty'">
            <span v-if="editingId === record.id">
              <a-input-number v-model:value="editQty" :step="0.01" style="width:100px" size="small" />
            </span>
            <template v-else>
              <strong>{{ fmt(record.actualQty) }}</strong>
              <div v-if="record.unitBreakdown" style="margin-top:2px"><a-tooltip title="录入明细"><a-tag color="processing">{{ fmtBreakdown(record.unitBreakdown) }}</a-tag></a-tooltip></div>
            </template>
          </template>
          <template v-else-if="column.key === 'diffQty'">
            <span :class="getDiffClass(record.diffQty)">{{ fmtSigned(record.diffQty) }}</span>
          </template>
          <template v-else-if="column.key === 'diffRate'">
            <span :class="record.isLarge === 1 ? 'diff-large' : ''">{{ rateFmt(record.diffRate) }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <template v-if="editingId === record.id">
              <a-space>
                <a-button size="small" type="primary" @click="saveEdit(record)">保存</a-button>
                <a-button size="small" @click="cancelEdit">取消</a-button>
              </a-space>
            </template>
            <template v-else>
              <a-tag class="action-tag action-view-tag" @click="showDetail(record)">查看</a-tag>
              <a-tag class="action-tag action-edit-tag" @click="startEdit(record)">修改</a-tag>
            </template>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 详情弹窗 -->
    <a-modal v-model:open="detailOpen" title="差异详情" :footer="null" width="640" :destroy-on-close="true">
      <template v-if="detailRow">
        <a-descriptions :column="2" size="small" bordered :colon="false">
          <a-descriptions-item label="物料">{{ detailRow.materialName }}</a-descriptions-item>
          <a-descriptions-item label="单位">{{ detailRow.unit || '--' }}</a-descriptions-item>
          <a-descriptions-item label="上月剩余">{{ fmt(detailRow.lastMonthQty) }}</a-descriptions-item>
          <a-descriptions-item label="采购">{{ fmt(detailRow.purchaseQty) }}</a-descriptions-item>
          <a-descriptions-item label="订货">{{ fmt(detailRow.orderQty) }}</a-descriptions-item>
          <a-descriptions-item label="调货净值">{{ fmtSigned(detailRow.transferNetQty) }}</a-descriptions-item>
          <a-descriptions-item label="还货">{{ fmtSigned(detailRow.returnQty) }}</a-descriptions-item>
          <a-descriptions-item label="报损">{{ fmt(detailRow.lossQty) }}</a-descriptions-item>
          <a-descriptions-item label="自购">{{ fmt(detailRow.selfPurchaseQty) }}</a-descriptions-item>
          <a-descriptions-item label="消耗">{{ fmt(detailRow.consumptionQty) }}</a-descriptions-item>
        </a-descriptions>
        <div class="formula-text">理论剩余 = 上月剩余 + 采购 + 订货 + 调货净值 + 还货净值 - 报损 + 自购 - 消耗</div>
        <div class="formula-text">差异率 = |差异| / |理论剩余|</div>
        <a-divider />
        <a-row :gutter="8">
          <a-col :span="8"><div class="stat-box"><div class="stat-label">理论剩余</div><div class="stat-value">{{ fmt(detailRow.theoreticalQty) }}</div></div></a-col>
          <a-col :span="8"><div class="stat-box"><div class="stat-label">本月盘点</div><div class="stat-value">{{ fmt(detailRow.actualQty) }}<a-tooltip title="录入明细"><a-tag v-if="detailRow.unitBreakdown" color="processing" style="margin-left:6px">{{ fmtBreakdown(detailRow.unitBreakdown) }}</a-tag></a-tooltip></div></div></a-col>
          <a-col :span="8"><div class="stat-box"><div class="stat-label">差异</div><div class="stat-value" :class="getDiffClass(detailRow.diffQty)">{{ fmtSigned(detailRow.diffQty) }} ({{ rateFmt(detailRow.diffRate) }})</div></div></a-col>
        </a-row>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import { getDiffTaskDetail, triggerDiffCalc, modifyAdjustedQty } from '../../api/task'

const route = useRoute()
const taskId = Number(route.params.taskId)
const loading = ref(false)
const calculating = ref(false)
const taskInfo = ref<any>({})
const differences = ref<any[]>([])
const largeCount = ref(0)
const searchKeyword = ref('')
const filteredDifferences = computed(() => {
  if (!searchKeyword.value) return differences.value
  const kw = searchKeyword.value.toLowerCase()
  return differences.value.filter((d: any) => (d.materialName || '').toLowerCase().includes(kw))
})

const editingId = ref<number | null>(null)
const editQty = ref<number>(0)
const detailOpen = ref(false)
const detailRow = ref<any>(null)

const columns = [
  { title: '物料', key: 'materialName', width: 160, fixed: 'left' as const, align: 'center' as const },
  { title: '单位', key: 'unit', width: 60, align: 'center' as const },
  { title: '理论剩余', key: 'theoreticalQty', width: 110, align: 'center' as const },
  { title: '本月盘点', key: 'actualQty', width: 120, align: 'center' as const },
  { title: '差异', key: 'diffQty', width: 100, align: 'center' as const },
  { title: '差异率', key: 'diffRate', width: 90, align: 'center' as const },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const, align: 'center' as const },
]

function fmt(v: any) { if (v == null) return '0'; return Number(v).toFixed(2).replace(/\.?0+$/, '') }
function fmtSigned(v: any) { if (v == null) return '0'; const n = Number(v); return (n >= 0 ? '+' : '') + fmt(n) }
function rateFmt(v: any) { if (v == null || Number(v) === 0) return '0.00%'; return (Number(v) * 100).toFixed(2) + '%' }
function getDiffClass(v: any) { return v != null && Number(v) >= 0 ? 'amount-positive' : 'amount-negative' }
function formatDate(v: string | null) { if (!v) return '--'; return v.replace('T', ' ').substring(0, 16) }
function rowClass(record: any) { return record.isLarge === 1 ? 'row-large-diff' : '' }
function fmtBreakdown(s: string | null) {
  if (!s) return '--'
  try {
    const obj = JSON.parse(s)
    if (typeof obj === 'object' && !Array.isArray(obj)) {
      return Object.entries(obj).filter(([, v]) => v !== '0' && v !== 0 && v !== '').map(([u, q]) => `${q}${u}`).join('·')
    }
  } catch { /* ignore */ }
  return s
}

function showDetail(row: any) { detailRow.value = row; detailOpen.value = true }

async function fetchDetail() {
  loading.value = true
  try {
    const res: any = await getDiffTaskDetail(taskId)
    taskInfo.value = res.data?.taskInfo || {}
    differences.value = res.data?.differences || []
    largeCount.value = res.data?.largeCount || 0
    if (differences.value.length === 0) {
      await triggerDiffCalc(taskId)
      const res2: any = await getDiffTaskDetail(taskId)
      taskInfo.value = res2.data?.taskInfo || {}
      differences.value = res2.data?.differences || []
      largeCount.value = res2.data?.largeCount || 0
    }
  } catch { message.error('加载差异明细失败') }
  finally { loading.value = false }
}

async function doCalculate() {
  calculating.value = true
  try { await triggerDiffCalc(taskId); message.success('差异计算完成'); fetchDetail() }
  catch { message.error('计算失败') }
  finally { calculating.value = false }
}

function startEdit(record: any) { editingId.value = record.id; editQty.value = Number(record.actualQty) || 0 }
function cancelEdit() { editingId.value = null }

async function saveEdit(record: any) {
  try { await modifyAdjustedQty(record.id, editQty.value); message.success('修改成功'); editingId.value = null; fetchDetail() }
  catch { message.error('修改失败') }
}

onMounted(() => fetchDetail())
</script>

<style scoped>
.diff-detail-page { padding: 0 }
.result-header { display: flex; align-items: baseline; gap: 8px; margin-bottom: 16px }
.back-btn { padding: 0; font-size: 18px }
.result-title { font-size: 22px; font-weight: 700; color: #1F2421; margin: 0 }
.info-card { margin-bottom: 12px }
.info-item { display: flex; flex-direction: column }
.info-label { font-size: 12px; color: #98A19C }
.info-value { font-size: 16px; font-weight: 600; color: #1F2421; margin-top: 2px }
.strong-text { font-weight: 600; color: #1F2421 }
.unit-text { font-weight: 400; color: #98A19C; font-size: 12px }
.sub-text { font-size: 12px; color: #98A19C; margin-top: 2px }
.amount-positive { color: #2F8F57; font-weight: 700 }
:deep(.ant-table-tbody td) { text-align: center !important }
.amount-negative { color: #E05A47; font-weight: 700 }
.diff-large { color: #E05A47; font-weight: 700 }
:deep(.row-large-diff) { background: #FFF5F5 }
:deep(.row-large-diff:hover) { background: #FFE8E8 !important }
.action-tag { cursor: pointer; border-radius: 4px; font-size: 12px; padding: 0 7px; line-height: 20px }
.action-view-tag { color: #0d7a3d; background: rgba(13,122,61,0.1); border: 1px solid rgba(13,122,61,0.2) }
.action-edit-tag { color: #356d91; background: rgba(53,109,145,0.1); border: 1px solid rgba(53,109,145,0.2) }
.stat-box { text-align: center; padding: 8px; background: #F7F8F6; border-radius: 6px }
.stat-label { font-size: 12px; color: #98A19C }
.stat-value { font-size: 16px; font-weight: 700; color: #1F2421; margin-top: 4px }
.formula-text { font-size: 12px; color: #98A19C; margin-bottom: 8px }
</style>
