<template>
  <div class="diff-page">
    <PageModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">盘点差异处理</h1>
        <p class="page-subtitle">按盘点单维度展示差异，差异率超过阈值({{ thresholdPercent }})的物料计入大差异</p>
      </div>
      <div class="title-actions">
        <a-button @click="showConfigModal = true">阈值设置</a-button>
      </div>
    </div>

    <!-- 筛选 -->
    <a-card class="filter-card" :bordered="false">
      <div class="filter-row">
        <a-col :xs="24" :sm="12" :md="6" style="flex:none;width:220px">
          <div class="filter-label">门店</div>
          <a-select v-model:value="filters.storeIds" mode="multiple" placeholder="全部门店" style="width:100%" allow-clear show-search option-filter-prop="label" :loading="storesLoading" :max-tag-count="5">
            <a-select-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.mendianmingcheng">{{ s.mendianmingcheng }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6" style="flex:none;width:180px">
          <div class="filter-label">督导</div>
          <a-select v-model:value="filters.supervisorName" placeholder="全部督导" style="width:100%" allow-clear :options="supervisorOptions" />
        </a-col>
        <div class="filter-actions">
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" @click="handleSearch">查询</a-button>
        </div>
      </div>
    </a-card>

    <!-- Tab 切换 -->
    <a-tabs v-model:activeKey="activeTab" @change="onTabChange" style="margin-bottom:8px">
      <a-tab-pane key="list" tab="差异列表" />
      <a-tab-pane key="chart" tab="统计图表" />
    </a-tabs>

    <!-- 指标卡片 -->
    <a-row :gutter="12" class="metric-row" v-if="activeTab === 'list'">
      <a-col :xs="12" :md="8"><a-card :bordered="false" class="metric-card"><a-statistic title="有差异盘点单" :value="metrics.taskCount" value-style="font-size:28px;font-weight:700;color:#1F2421" /></a-card></a-col>
      <a-col :xs="12" :md="8"><a-card :bordered="false" class="metric-card"><a-statistic title="大差异物料总数" :value="metrics.largeDiffCount" value-style="font-size:28px;font-weight:700;color:#E05A47" /></a-card></a-col>
      <a-col :xs="12" :md="8"><a-card :bordered="false" class="metric-card"><a-statistic title="差异阈值" :value="thresholdPercent" value-style="font-size:28px;font-weight:700;color:#356d91" /></a-card></a-col>
    </a-row>

    <!-- 表格 -->
    <a-card :bordered="false" v-if="activeTab === 'list'">
      <a-table :columns="columns" :data-source="list" :loading="loading" :pagination="pagination" row-key="taskId" size="middle" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'storeName'"><div class="strong-text">{{ record.storeName }}</div></template>
          <template v-if="column.key === 'supervisorName'">{{ record.supervisorName || '--' }}</template>
          <template v-if="column.key === 'taskMonth'">{{ record.taskMonth || '--' }}</template>
          <template v-if="column.key === 'materialCount'">{{ record.materialCount || 0 }}</template>
          <template v-if="column.key === 'largeDiffCount'"><span :class="record.largeDiffCount > 0 ? 'diff-large' : 'diff-normal'">{{ record.largeDiffCount || 0 }}</span></template>
          <template v-if="column.key === 'action'"><a-tag class="action-tag action-view-tag" @click="goDetail(record)">处理</a-tag></template>
        </template>
      </a-table>
    </a-card>

    <!-- 统计图表 Tab -->
    <a-card :bordered="false" v-if="activeTab === 'chart'" :loading="chartLoading">
      <template #title>
        <a-radio-group v-model:value="chartDimension" button-style="solid" size="small" style="margin-right:16px">
          <a-radio-button value="store">门店维度</a-radio-button>
          <a-radio-button value="material">物料维度</a-radio-button>
        </a-radio-group>
      </template>

      <!-- 门店维度（不变） -->
      <template v-if="chartDimension === 'store'">
        <div v-if="chartData.length === 0" style="text-align:center;padding:40px;color:#98A19C">暂无数据</div>
        <div v-else class="chart-scroll" style="max-height:calc(100vh - 320px);overflow-y:auto">
          <div v-for="group in chartData" :key="group.supervisorName" style="margin-bottom:20px">
            <div class="chart-group-title">{{ group.supervisorName || '未分配' }} <span class="chart-group-note">({{ group.stores.length }} 家门店，大差异物料 {{ group.largeDiffCount }} 项)</span></div>
            <div class="bar-list">
              <div v-for="store in group.stores" :key="store.storeName" class="bar-row">
                <span class="bar-label">{{ store.storeName }}</span>
                <span class="bar-track"><span class="bar-fill" :style="{ width: pct(store.largeDiffCount, maxMat) }" /></span>
                <strong style="white-space:nowrap;font-size:12px">总盘 {{ store.materialCount }} · <span style="color:#E05A47">大差异 {{ store.largeDiffCount }}</span></strong>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 物料维度（新） -->
      <template v-if="chartDimension === 'material'">
        <div style="margin-bottom:12px;display:flex;align-items:center;gap:12px">
          <span style="font-size:13px;color:#66706A">盘点月份</span>
          <a-select v-model:value="materialMonth" style="width:160px" size="small" :loading="matLoading" @change="fetchMaterialChart">
            <a-select-option v-for="m in materialMonths" :key="m" :value="m">{{ m }}</a-select-option>
          </a-select>
        </div>

        <a-spin :spinning="matLoading">
          <div v-if="materialData.length === 0 && !matLoading" style="text-align:center;padding:40px;color:#98A19C">暂无数据</div>
          <template v-else>
            <!-- 柱状图：Top 15 物料 -->
            <div class="chart-group-title">大差异物料 Top {{ Math.min(15, materialData.length) }}</div>
            <div class="bar-list" style="margin-bottom:16px">
              <div v-for="m in topMaterials" :key="m.material_id" class="bar-row">
                <span class="bar-label" :title="m.material_name + (m.spec ? ' / ' + m.spec : '')">{{ m.material_name }}<span v-if="m.spec" style="color:#98A19C;font-size:11px"> / {{ m.spec }}</span></span>
                <span class="bar-track">
                  <span class="bar-fill" :style="{ width: pct(m.store_count, maxAbsDiff) }" />
                </span>
                <strong style="white-space:nowrap">{{ m.store_count }} 店</strong>
              </div>
            </div>

            <!-- 完整表格 -->
            <a-table :columns="matColumns" :data-source="materialData" :pagination="{ pageSize: 20, showSizeChanger: false, showTotal: (t:number) => '共 ' + t + ' 项' }" row-key="material_id" size="small">
              <template #avgDiffRateTitle>
                <span>差异率 <a-tooltip title="差异率 = |本月盘点数 - 理论剩余| / |理论剩余|；此处为各门店大差异条目的平均值" overlay-style="max-width:300px"><span style="color:#98A19C;cursor:help;font-size:12px">?</span></a-tooltip></span>
              </template>
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'materialName'">
                  <div class="strong-text">{{ record.material_name }}</div>
                  <div class="sub-text" v-if="record.spec">{{ record.spec }}</div>
                </template>
                <template v-else-if="column.key === 'unit'">{{ record.unit || '--' }}</template>
                <template v-else-if="column.key === 'totalAbsDiffQty'">
                  <span :class="Number(record.total_diff_qty) >= 0 ? 'amount-positive' : 'amount-negative'">{{ fmtSigned(record.total_diff_qty) }}</span>
                </template>
                <template v-else-if="column.key === 'storeCount'">{{ record.store_count }}</template>
                <template v-else-if="column.key === 'avgDiffRate'">{{ rateFmt(record.avg_diff_rate) }}</template>
                <template v-else-if="column.key === 'action'">
                  <a-tag class="action-tag action-view-tag" @click="showMatDetail(record)">详情</a-tag>
                </template>
              </template>
            </a-table>
          </template>
        </a-spin>
      </template>
    </a-card>

    <!-- 物料门店明细弹窗 -->
    <a-modal v-model:open="matDetailOpen" :title="'「' + matDetailRow?.material_name + '」各门店差异'" :footer="null" width="700" :destroy-on-close="true">
      <a-table v-if="matDetailRow" :columns="storeDetailColumns" :data-source="matDetailRow.storeDetails" :pagination="false" row-key="store_id" size="small" :scroll="{ y: 400 }">
        <template #bodyCell="{ column, record: sd }">
          <template v-if="column.key === 'storeName'">{{ sd.store_name || '--' }}</template>
          <template v-else-if="column.key === 'theoreticalQty'">{{ fmt(sd.theoretical_qty) }}</template>
          <template v-else-if="column.key === 'actualQty'">{{ fmt(sd.actual_qty) }}</template>
          <template v-else-if="column.key === 'diffQty'">
            <span :class="Number(sd.diff_qty) >= 0 ? 'amount-positive' : 'amount-negative'">{{ fmtSigned(sd.diff_qty) }}</span>
          </template>
          <template v-else-if="column.key === 'diffRate'">{{ rateFmt(sd.diff_rate) }}</template>
          <template v-else-if="column.key === 'isLarge'">
            <a-tag v-if="sd.is_large === 1" color="error">大差异</a-tag>
            <span v-else style="color:#98A19C">--</span>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- 阈值设置弹窗 -->
    <a-modal v-model:open="showConfigModal" title="差异阈值设置" :destroy-on-close="true" @ok="saveConfig">
      <a-form layout="vertical">
        <a-form-item label="差异阈值（%）" help="差异率 = abs(本月盘点数 - 理论剩余) / 理论剩余，超过此阈值即标记为大差异">
          <a-input-number v-model:value="configRate" :min="1" :max="100" :step="1" style="width:100%" addon-after="%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import { getDiffTasks, getDiffConfig, updateDiffConfig, getDiffMaterials } from '../../api/task'
import { getStores } from '../../api/store'
import { getSupervisorOptions } from '../../api/supervisor'

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })
const metrics = reactive({ taskCount: 0, largeDiffCount: 0 })
const thresholdPercent = ref('50%')
const activeTab = ref('list')
const showConfigModal = ref(false)
const configRate = ref<number>(50)
const chartLoading = ref(false)
const chartData = ref<any[]>([])

const storesLoading = ref(false)
const stores = ref<any[]>([])
const supervisorOptions = ref<{ label: string; value: string }[]>([])
const filters = reactive({ storeIds: [] as string[], supervisorName: '' as string })

const columns = [
  { title: '门店', key: 'storeName', ellipsis: true, width: 160 },
  { title: '督导', key: 'supervisorName', width: 100 },
  { title: '盘点月份', key: 'taskMonth', width: 120 },
  { title: '已盘物料数', key: 'materialCount', align: 'right' as const, width: 110 },
  { title: '有差异物料数', key: 'largeDiffCount', align: 'right' as const, width: 130 },
  { title: '操作', key: 'action', width: 80 },
]

function formatDate(v: string | null) { if (!v) return '--'; return v.replace('T', ' ').substring(0, 16) }

async function fetchList() {
  loading.value = true
  try {
    const params: any = { pageNum: pagination.current, pageSize: pagination.pageSize }
    if (filters.storeIds.length) params.storeIds = filters.storeIds.join(',')
    if (filters.supervisorName) params.supervisorName = filters.supervisorName
    const res: any = await getDiffTasks(params)
    const data = res.data
    list.value = data.records || []
    pagination.total = data.total || 0
    metrics.taskCount = data.total || 0
    metrics.largeDiffCount = list.value.reduce((s: number, d: any) => s + (d.largeDiffCount || 0), 0)
  } finally { loading.value = false }
}

async function loadConfig() {
  try {
    const res: any = await getDiffConfig()
    thresholdPercent.value = res.data?.thresholdPercent || '50%'
    configRate.value = Math.round((res.data?.thresholdRate || 0.5) * 100)
  } catch { /* ignore */ }
}

async function saveConfig() {
  try {
    await updateDiffConfig(configRate.value / 100)
    message.success('阈值已更新')
    showConfigModal.value = false
    loadConfig()
    fetchList()
    setTimeout(() => { fetchChart(); fetchMaterialChart() }, 500)
  } catch { message.error('更新失败') }
}

function handleTableChange(p: any) { pagination.current = p.current; fetchList() }
function handleSearch() { pagination.current = 1; fetchList(); fetchChart(); fetchMaterialChart() }
function handleReset() { filters.storeIds = []; filters.supervisorName = ''; handleSearch() }
function onTabChange(key: string) { if (key === 'chart') { fetchChart(); fetchMaterialChart() } }
function goDetail(record: any) { router.push(`/tasks/${record.taskId}/differences`) }

async function fetchStores() {
  storesLoading.value = true
  try { const res: any = await getStores(); stores.value = res.data || [] }
  finally { storesLoading.value = false }
}
async function fetchSupervisors() {
  try { const res: any = await getSupervisorOptions(); supervisorOptions.value = res.data || [] }
  catch { /* ignore */ }
}

const maxMat = computed(() => {
  const allVals = chartData.value.flatMap((d: any) => d.stores.map((s: any) => s.largeDiffCount || 0))
  return Math.max(1, ...allVals)
})
function pct(v: number, max: number) { return max === 0 ? '0%' : ((v / max) * 100).toFixed(1) + '%' }

// ---- 物料维度 ----
const chartDimension = ref<'store' | 'material'>('store')
const materialMonth = ref<string>('')
const materialMonths = ref<string[]>([])
const materialData = ref<any[]>([])
const matLoading = ref(false)
const matDetailOpen = ref(false)
const matDetailRow = ref<any>(null)
const topMaterials = computed(() => materialData.value.slice(0, 15))
const maxAbsDiff = computed(() => {
  const vals = materialData.value.map((m: any) => Number(m.store_count) || 0)
  return Math.max(1, ...vals)
})

function fmt(v: any) { if (v == null) return '0'; return Number(v).toFixed(2).replace(/\.?0+$/, '') }
function fmtSigned(v: any) { if (v == null) return '0'; const n = Number(v); return (n >= 0 ? '+' : '') + fmt(n) }
function rateFmt(v: any) { if (v == null || Number(v) === 0) return '0.00%'; return (Number(v) * 100).toFixed(2) + '%' }

const matColumns = [
  { title: '物料名称', key: 'materialName', width: 180, ellipsis: true },
  { title: '单位', key: 'unit', width: 60, align: 'center' as const },
  { title: '差异合计', key: 'totalAbsDiffQty', width: 110, align: 'right' as const },
  { title: '涉及门店数', key: 'storeCount', width: 100, align: 'center' as const },
  { title: '差异率', key: 'avgDiffRate', width: 100, align: 'center' as const, slots: { title: 'avgDiffRateTitle' } },
  { title: '操作', key: 'action', width: 70, align: 'center' as const },
]

const storeDetailColumns = [
  { title: '门店', key: 'storeName', width: 140, ellipsis: true },
  { title: '理论剩余', key: 'theoreticalQty', width: 110, align: 'right' as const },
  { title: '本月盘点', key: 'actualQty', width: 110, align: 'right' as const },
  { title: '差异', key: 'diffQty', width: 100, align: 'right' as const },
  { title: '差异率', key: 'diffRate', width: 90, align: 'center' as const },
  { title: '大差异', key: 'isLarge', width: 80, align: 'center' as const },
]

function showMatDetail(record: any) { matDetailRow.value = record; matDetailOpen.value = true }

async function fetchMaterialChart() {
  matLoading.value = true
  try {
    const params: any = {}
    if (filters.storeIds.length) params.storeIds = filters.storeIds.join(',')
    if (filters.supervisorName) params.supervisorName = filters.supervisorName
    if (materialMonth.value) params.taskMonth = materialMonth.value
    const res: any = await getDiffMaterials(params)
    materialMonths.value = res.data?.months || []
    // 自动选中最新月份并重新查询
    if (!materialMonth.value && materialMonths.value.length) {
      materialMonth.value = materialMonths.value[0]
      // 重新带月份查询数据
      const params2: any = { taskMonth: materialMonth.value }
      if (filters.storeIds.length) params2.storeIds = filters.storeIds.join(',')
      if (filters.supervisorName) params2.supervisorName = filters.supervisorName
      const res2: any = await getDiffMaterials(params2)
      materialData.value = res2.data?.materials || []
    } else {
      materialData.value = res.data?.materials || []
    }
  } catch { /* ignore */ }
  finally { matLoading.value = false }
}

async function fetchChart() {
  chartLoading.value = true
  try {
    const params: any = { pageNum: 1, pageSize: 1000 }
    if (filters.storeIds.length) params.storeIds = filters.storeIds.join(',')
    if (filters.supervisorName) params.supervisorName = filters.supervisorName
    const res: any = await getDiffTasks(params)
    const records = res.data?.records || []
    const agg: Record<string, any> = {}
    for (const r of records) {
      const sv = r.supervisorName || '未分配'
      if (!agg[sv]) agg[sv] = { supervisorName: sv, largeDiffCount: 0, stores: {} as Record<string, any> }
      agg[sv].largeDiffCount += r.largeDiffCount || 0
      const sn = r.storeName || r.storeId || '未知'
      if (!agg[sv].stores[sn]) agg[sv].stores[sn] = { storeName: sn, largeDiffCount: 0, materialCount: 0, totalDiffCount: 0 }
      agg[sv].stores[sn].largeDiffCount += r.largeDiffCount || 0
      agg[sv].stores[sn].materialCount += r.materialCount || 0
      agg[sv].stores[sn].totalDiffCount += r.totalDiffCount || 0
    }
    chartData.value = Object.values(agg).map((g: any) => ({
      ...g, stores: Object.values(g.stores).sort((a: any, b: any) => b.largeDiffCount - a.largeDiffCount)
    })).sort((a: any, b: any) => b.largeDiffCount - a.largeDiffCount)
  } catch { /* ignore */ }
  finally { chartLoading.value = false }
}

onMounted(() => { loadConfig(); fetchStores(); fetchSupervisors(); fetchList(); fetchChart() })
</script>

<style scoped>
.diff-page { padding: 0 }
.page-title-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px }
.page-title { font-size: 22px; font-weight: 700; color: #1F2421; margin: 0 }
.page-subtitle { font-size: 13px; color: #66706A; margin: 4px 0 0 }
.title-actions { display: flex; gap: 8px }
.metric-row { margin-bottom: 12px }
.metric-card { min-height: 92px }
.filter-card { margin-bottom: 8px }
.filter-label { font-size: 13px; color: #66706A; margin-bottom: 4px }
.filter-row { display: flex; align-items: flex-end; gap: 16px; flex-wrap: wrap }
.filter-actions { display: flex; gap: 8px; margin-left: auto }
.strong-text { font-weight: 600; color: #1F2421 }
.diff-large { color: #E05A47; font-weight: 700 }
.diff-normal { color: #2F8F57 }
.action-tag { cursor: pointer; border-radius: 4px; font-size: 12px; padding: 0 7px; line-height: 20px }
.action-view-tag { color: #0d7a3d; background: rgba(13,122,61,0.1); border: 1px solid rgba(13,122,61,0.2) }
.chart-group-title { font-size: 14px; font-weight: 600; color: #1F2421; margin-bottom: 8px }
.chart-group-note { font-weight: 400; font-size: 12px; color: #98A19C }
.bar-list { display: grid; gap: 6px; margin-bottom: 8px }
.bar-row { display: flex; align-items: center; gap: 10px }
.bar-label { width: 160px; font-size: 13px; color: #1F2421; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.bar-track { flex: 1; height: 18px; background: #F0F2F0; border-radius: 6px; overflow: hidden; min-width: 60px }
.bar-fill { display: block; height: 100%; background: var(--primary, #0d7a3d); border-radius: 6px; transition: width .3s; min-width: 4px }
.chart-group-title { font-size: 14px; font-weight: 600; color: #1F2421; margin-bottom: 8px } .chart-group-note { font-weight: 400; font-size: 12px; color: #98A19C } .bar-list { display: grid; gap: 6px; margin-bottom: 8px } .bar-row { display: flex; align-items: center; gap: 10px } .bar-label { width: 160px; font-size: 13px; color: #1F2421; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap } .bar-track { flex: 1; height: 18px; background: #F0F2F0; border-radius: 6px; overflow: hidden; min-width: 60px } .bar-fill { display: block; height: 100%; background: var(--primary, #0d7a3d); border-radius: 6px; transition: width .3s; min-width: 4px } .amount-positive { color: #2F8F57; font-weight: 700 } .amount-negative { color: #E05A47; font-weight: 700 } .sub-text { font-size: 12px; color: #98A19C; margin-top: 2px }
</style>
