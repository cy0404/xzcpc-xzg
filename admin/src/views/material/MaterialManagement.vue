<template>
  <div class="material-page">
    <PageModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">物料管理</h1>
        <p class="page-subtitle">维护基础单位、可盘点单位、单位换算关系、称重换算和盘点单价</p>
      </div>
    </div>

    <a-card class="filter-card" :bordered="false">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">物料分类</div>
          <a-select v-model:value="filters.category" allow-clear placeholder="全部分类" style="width: 100%">
            <a-select-option v-for="item in categoryOptions" :key="item" :value="item">
              {{ item }}
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">基础盘点单位</div>
          <a-select v-model:value="filters.baseUnit" allow-clear placeholder="全部单位" style="width: 100%">
            <a-select-option v-for="item in baseUnitOptions" :key="item" :value="item">
              {{ item }}
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">换算类型</div>
          <a-select v-model:value="filters.conversionType" allow-clear placeholder="全部类型" style="width: 100%">
            <a-select-option value="maintained">已维护</a-select-option>
            <a-select-option value="pending">未维护</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="filter-label">物料名称</div>
          <a-input v-model:value="filters.keyword" allow-clear placeholder="输入物料名称搜索" @pressEnter="handleSearch" />
        </a-col>
      </a-row>
      <div class="filter-actions">
        <a-button @click="handleReset">重置</a-button>
        <a-button class="btn-query" @click="handleSearch">查询</a-button>
      </div>
    </a-card>

    <a-card class="table-card" :bordered="false">
      <a-table
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="tablePagination"
        row-key="materialId"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'materialName'">
            <div class="material-name">{{ record.materialName || '--' }}</div>
          </template>

          <template v-else-if="column.key === 'baseUnit'">
            <span v-if="record.baseUnit" class="base-unit-tag">{{ record.baseUnit }}</span>
            <a-tag v-else color="default">未维护</a-tag>
          </template>

          <template v-else-if="column.key === 'conversions'">
            <div v-if="hasConversions(record)" class="conversion-tags">
              <span
                v-for="(item, idx) in allConversionItems(record)"
                :key="idx"
                class="conversion-tag"
              >{{ item.text }}</span>
            </div>
            <a-tag v-else color="default">未维护</a-tag>
          </template>

          <template v-else-if="column.key === 'unitPrice'">
            <template v-if="record.unitPrice != null && Number(record.unitPrice) !== 0">
              <span class="money">{{ formatUnitPrice(record) }}</span>
            </template>
            <a-tag v-else color="default">未维护</a-tag>
          </template>

          <template v-else-if="column.key === 'updatedAt'">
            <span class="date-text">{{ formatDate(record.updatedAt) }}</span>
          </template>

          <template v-else-if="column.key === 'action'">
            <span class="action-link" @click="openEdit(record)">编辑</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="editVisible"
      width="760px"
      title="编辑物料盘点规则"
      :confirm-loading="saving"
      @ok="handleSave"
    >
      <div v-if="currentRecord" class="rule-form">
        <!-- 顶部物料信息（只读） -->
        <div class="info-grid">
          <div class="info-item">
            <div class="info-label">物料名称</div>
            <div class="info-value readonly">{{ currentRecord.materialName }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">分类</div>
            <div class="info-value readonly">{{ currentRecord.category || '--' }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">规格</div>
            <div class="info-value readonly">{{ currentRecord.spec || '--' }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">基础盘点单位</div>
            <div class="info-value readonly">{{ form.baseUnit || currentRecord.baseUnit || '--' }}</div>
          </div>
        </div>

        <!-- 可编辑字段 -->
        <a-row :gutter="[16, 16]">
          <a-col :span="12">
            <div class="field-label">基础盘点单位</div>
            <a-input v-model:value="form.baseUnit" placeholder="如：根、包、千克" />
            <div class="field-tip">单价按基础单位维护，系统按关系链自动换算</div>
          </a-col>
          <a-col :span="12">
            <div class="field-label">盘点单价</div>
            <a-input-number v-model:value="form.unitPrice" :min="0" style="width: 100%" />
            <div class="field-tip">单价按基础单位维护</div>
          </a-col>
        </a-row>

        <!-- 单位换算关系 -->
        <div class="rule-section">
          <div class="section-head">
            <h3>单位换算关系</h3>
            <span class="section-add-btn" @click="addConversion">添加关系</span>
          </div>
          <div class="section-tip">设定其他单位与基础盘点单位的换算比例，例如 1 箱 = 100 根。</div>
          <div v-for="(item, index) in form.conversions" :key="index" class="conversion-card">
            <a-input-number v-model:value="item.fromQuantity" :min="0" class="conv-num" />
            <a-input v-model:value="item.fromUnit" placeholder="单位" class="conv-unit" />
            <span class="conv-eq">=</span>
            <a-input-number v-model:value="item.toQuantity" :min="0" class="conv-num" />
            <span class="conv-unit readonly">{{ form.baseUnit || '--' }}</span>
            <span class="delete-text" @click="removeConversion(index)">删除</span>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import {
  getMaterialRules,
  getBaseUnits,
  saveMaterialRule,
  type MaterialRuleRecord,
  type MaterialUnitConversion,
} from '../../api/materialRule'
import { getCategories } from '../../api/material'

const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const records = ref<MaterialRuleRecord[]>([])
const currentRecord = ref<MaterialRuleRecord | null>(null)
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const filters = reactive({
  keyword: '',
  category: undefined as string | undefined,
  baseUnit: undefined as string | undefined,
  conversionType: undefined as string | undefined,
})

const form = reactive({
  baseUnit: '',
  unitPrice: 0,
  units: [] as { unitName: string }[],
  conversions: [] as MaterialUnitConversion[],
})

/** 可盘点单位：基础单位 + 所有换算关系的 fromUnit */
const computedUnits = computed(() => {
  const set = new Set<string>()
  if (form.baseUnit) set.add(form.baseUnit)
  form.conversions.forEach(c => {
    if (c.fromUnit?.trim()) set.add(c.fromUnit.trim())
  })
  return [...set]
})

const columns = [
  { title: '物料名称', dataIndex: 'materialName', key: 'materialName', width: 160 },
  { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
  { title: '规格', dataIndex: 'spec', key: 'spec', width: 120 },
  { title: '基础单位', dataIndex: 'baseUnit', key: 'baseUnit', width: 110 },
  { title: '换算关系', dataIndex: 'conversions', key: 'conversions' },
  { title: '盘点单价', dataIndex: 'unitPrice', key: 'unitPrice', width: 130, align: 'right' as const },
  { title: '最近更新', dataIndex: 'updatedAt', key: 'updatedAt', width: 120 },
  { title: '操作', key: 'action', width: 80 },
]

const categoryOptions = ref<string[]>([])
const baseUnitOptions = ref<string[]>([])
const tablePagination = computed(() => ({
  current: pagination.current,
  pageSize: pagination.pageSize,
  total: pagination.total,
  showTotal: (total: number) => `共 ${total} 个物料`,
}))

function formatDate(value?: string) {
  if (!value) return '--'
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}-${match[2]}-${match[3]}` : '--'
}

function hasConversions(record: MaterialRuleRecord) {
  return (record.conversions?.length || 0) + (record.weightConversions?.length || 0) > 0
}

function allConversionItems(record: MaterialRuleRecord): { text: string }[] {
  const unitItems = (record.conversions || []).map(
    item => ({ text: `${item.fromQuantity}${item.fromUnit} = ${item.toQuantity}${item.toUnit}` }),
  )
  const weightItems = (record.weightConversions || []).map(
    item => ({ text: `${item.weightQuantity}${item.weightUnit} = ${item.countQuantity}${item.countUnit}` }),
  )
  return [...unitItems, ...weightItems]
}

function formatUnitPrice(record: MaterialRuleRecord) {
  if (record.unitPrice == null || Number(record.unitPrice) === 0) return '未维护'
  const price = String(Number(record.unitPrice))
  const unit = record.baseUnit || ''
  return unit ? `¥${price} / ${unit}` : `¥${price}`
}

async function fetchData() {
  loading.value = true
  try {
    const res = (await getMaterialRules({
      keyword: filters.keyword,
      category: filters.category,
      baseUnit: filters.baseUnit,
      conversionType: filters.conversionType,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })) as any
    records.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function fetchBaseUnits() {
  try {
    const res = (await getBaseUnits()) as any
    baseUnitOptions.value = res.data || []
  } catch {
    // 基础单位加载失败不影响主流程
  }
}

async function fetchCategories() {
  try {
    const res = (await getCategories()) as any
    categoryOptions.value = res.data || []
  } catch {
    // 分类加载失败不影响主流程
  }
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  filters.keyword = ''
  filters.category = undefined
  filters.baseUnit = undefined
  filters.conversionType = undefined
  handleSearch()
}

function handleTableChange(pager: any) {
  pagination.current = pager.current || 1
  pagination.pageSize = pager.pageSize || 10
  fetchData()
}

function openEdit(record: MaterialRuleRecord) {
  currentRecord.value = record
  form.baseUnit = record.baseUnit || ''
  form.unitPrice = Number(record.unitPrice || 0)
  form.conversions = (record.conversions || []).map(item => ({
    ...item,
    toUnit: record.baseUnit || '',
  }))
  editVisible.value = true
}

function addConversion() {
  form.conversions.push({ fromQuantity: 1, fromUnit: '', toQuantity: 1, toUnit: form.baseUnit })
}

function removeConversion(index: number) {
  form.conversions.splice(index, 1)
}

async function handleSave() {
  if (!currentRecord.value) return
  if (!form.baseUnit.trim()) {
    message.warning('请填写基础盘点单位')
    return
  }
  saving.value = true
  try {
    await saveMaterialRule(currentRecord.value.materialId, {
      baseUnit: form.baseUnit.trim(),
      unitPrice: Number(form.unitPrice || 0),
      units: computedUnits.value.map(u => ({ unitName: u })),
      conversions: form.conversions,
      weightConversions: [],
    })
    message.success('物料盘点规则已保存')
    editVisible.value = false
    fetchData()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchData()
  fetchCategories()
  fetchBaseUnits()
})
</script>

<style scoped>
.material-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f2421;
}

.page-subtitle {
  margin: 6px 0 0;
  color: #66706a;
}

.filter-card,
.table-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.filter-label {
  margin-bottom: 8px;
  color: #66706a;
  font-size: 13px;
  font-weight: 500;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.btn-query {
  color: #fff;
  background: var(--primary, #0d7a3d);
  border-color: var(--primary, #0d7a3d);
}

.material-name {
  font-weight: 600;
  color: #1f2421;
}

/* 基础单位标签 — 浅绿色胶囊 */
.base-unit-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  line-height: 20px;
  color: #0d7a3d;
  background: #e6f7ec;
  border-radius: 10px;
  white-space: nowrap;
}

/* 换算关系标签容器 */
.conversion-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 换算关系单个标签 — 灰色圆角 */
.conversion-tag {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  line-height: 20px;
  color: #4b5563;
  background: #f3f4f6;
  border-radius: 8px;
  white-space: nowrap;
}

.money,
.date-text {
  font-variant-numeric: tabular-nums;
}

.money {
  color: #4b5851;
}

/* 操作链接 — 绿色文字 */
.action-link {
  color: #0d7a3d;
  cursor: pointer;
  font-size: 13px;
}

.action-link:hover {
  color: #0a5f2e;
  text-decoration: underline;
}

/* ===== 弹窗内样式 ===== */

/* 顶部物料信息 — 2×2 只读网格 */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  padding: 16px;
  background: #fafbfa;
  border-radius: 8px;
  margin-bottom: 20px;
}

.info-item {
  display: grid;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: #88948d;
}

.info-value {
  font-size: 14px;
  font-weight: 600;
  color: #1f2421;
  padding: 6px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;

  &.readonly {
    background: #f3f5f4;
    color: #58665e;
  }
}

/* 可编辑字段 */
.field-label {
  margin-bottom: 6px;
  color: #4b5851;
  font-size: 13px;
  font-weight: 500;
}

.field-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #a0aba5;
}

/* 区域通用 */
.rule-section {
  display: grid;
  gap: 8px;
  margin-top: 20px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2421;
}

.section-add-btn {
  display: inline-block;
  padding: 3px 14px;
  font-size: 13px;
  color: #0d7a3d;
  background: #e6f7ec;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;

  &:hover {
    background: #d2f0d8;
  }
}

.section-tip {
  font-size: 12px;
  color: #a0aba5;
  line-height: 1.6;
}

.delete-text {
  font-size: 13px;
  color: #9ca3af;
  cursor: pointer;
  user-select: none;

  &:hover {
    color: #ef4444;
  }
}

/* 换算关系 & 称重换算 — 卡片行 */
.conversion-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #eef1f0;
  border-radius: 8px;
}

.conv-num {
  width: 80px;
  flex-shrink: 0;
}

.conv-unit {
  flex: 1;
  min-width: 60px;
}

.conv-eq {
  flex-shrink: 0;
  color: #9ca3af;
  font-weight: 600;
  font-size: 16px;
}

.conv-unit.readonly {
  display: flex;
  align-items: center;
  padding: 4px 11px;
  color: #58665e;
  background: #f3f5f4;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 14px;
  white-space: nowrap;
}

</style>
