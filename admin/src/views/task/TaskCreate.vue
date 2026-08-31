<template>
  <div class="task-create-page">
    <PageModuleTabs />

    <div class="create-header">
      <a-button type="text" class="back-btn" @click="$router.push('/tasks')">
        <ArrowLeftOutlined />
      </a-button>
      <h1 class="create-title">{{ form.taskType === 'weekly' ? '创建周盘任务' : '创建月盘任务' }}</h1>
    </div>

    <a-row :gutter="24" class="create-body">
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" class="config-card">
          <div class="section-title">
            <span class="section-bar" />
            基础配置
          </div>

          <a-form layout="vertical" :model="form" class="create-form">
            <a-form-item label="任务类型" required>
              <a-radio-group v-model:value="form.taskType" @change="onTaskTypeChange">
                <a-radio-button value="monthly">月盘</a-radio-button>
                <a-radio-button value="weekly">周盘</a-radio-button>
              </a-radio-group>
            </a-form-item>

            <a-form-item label="任务名称" required>
              <a-input v-model:value="form.taskName" :placeholder="taskNamePlaceholder" />
            </a-form-item>

            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item v-if="form.taskType === 'monthly'" label="盘点月份" required>
                  <a-month-picker
                    v-model:value="taskMonth"
                    placeholder="选择月份"
                    style="width: 100%"
                    format="MMMM YYYY"
                  />
                </a-form-item>
                <a-form-item v-else label="盘点周" required>
                  <a-date-picker
                    v-model:value="taskWeek"
                    picker="week"
                    placeholder="选择周"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
            </a-row>

            <a-form-item required>
              <template #label>
                门店范围
                <a-checkbox
                  v-model:checked="allStoresSelected"
                  :indeterminate="indeterminate"
                  style="margin-left: 12px; font-weight: 400;"
                >
                  全选
                </a-checkbox>
              </template>
              <a-select
                v-model:value="form.storeIds"
                placeholder="请选择门店"
                mode="multiple"
                :loading="storesLoading"
                show-search
                option-filter-prop="label"
                :max-tag-count="3"
              >
                <a-select-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.mendianmingcheng">
                  {{ s.mendianmingcheng }}
                </a-select-option>
              </a-select>
            </a-form-item>

            <a-form-item label="应用模板" required>
              <a-select
                v-model:value="form.templateId"
                placeholder="请选择模板"
                @change="onTemplateChange"
              >
                <a-select-option v-for="t in templates" :key="t.id" :value="t.id">
                  {{ t.templateName }}
                </a-select-option>
              </a-select>
            </a-form-item>

            <a-form-item v-if="form.taskType === 'monthly'" label="截止时间" required>
              <a-date-picker
                v-model:value="deadline"
                show-time
                format="MM/DD/YYYY, HH:mm"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择截止时间"
                style="width: 100%"
              />
              <p class="field-tip">建议设置在盘点当月最后一天营业结束前</p>
            </a-form-item>

            <a-form-item v-else label="截止时间" required>
              <a-table
                :columns="weeklyDeadlineColumns"
                :data-source="weeklyDeadlines"
                :pagination="false"
                size="small"
                row-key="storeId"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'dayText'">
                    <span v-if="record.unconfigured" class="unconfigured-tag">未配置</span>
                    <span v-else>{{ record.dayText }}</span>
                  </template>
                  <template v-else-if="column.key === 'deadlineText'">
                    <span v-if="record.unconfigured" class="unconfigured-tip">请先到门店订货周期配置设置</span>
                    <template v-else>
                      <div class="deadline-line">{{ record.deadlineText }}</div>
                      <div v-if="record.multiDay" class="multi-day-tip">仅首个订货日创建，其余由自动生成覆盖</div>
                    </template>
                  </template>
                </template>
              </a-table>
              <p class="field-tip">
                按各门店订货周期自动计算截止时间（订货日前一天 9:00 生成，截止 = 订货日当天 05:00），无需手动填写。
                <router-link to="/stores/weekly-config" class="config-link">门店订货周期配置</router-link>
              </p>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" class="preview-card">
          <div class="section-title preview-title">
            <EyeOutlined />
            模板预览
          </div>

          <template v-if="preview">
            <h3 class="preview-name">{{ preview.templateName }}</h3>
            <div class="preview-stats">
              <div class="stat-card">
                <div class="stat-value">{{ preview.zoneCount ?? preview.zones?.length ?? 0 }}</div>
                <div class="stat-label">分区数</div>
              </div>
              <div class="stat-card">
                <div class="stat-value">{{ preview.materialCount ?? totalMaterials }}</div>
                <div class="stat-label">物料数</div>
              </div>
            </div>

            <div class="preview-zones-label">包含分区结构</div>
            <div class="zone-preview-list">
              <div v-for="(zone, idx) in preview.zones" :key="zone.id" class="zone-preview-item">
                <component :is="zoneIcons[idx % zoneIcons.length]" class="zone-preview-icon" />
                <span>{{ zone.zoneName }}</span>
              </div>
            </div>
          </template>
          <a-empty v-else description="请先选择模板" />
        </a-card>
      </a-col>
    </a-row>

    <div class="create-footer">
      <a-button size="large" @click="$router.push('/tasks')">取消</a-button>
      <a-button
        type="primary"
        size="large"
        :loading="submitting"
        @click="handleSubmit"
      >
        <template #icon><CheckOutlined /></template>
        创建任务
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import {
  ArrowLeftOutlined,
  EyeOutlined,
  CheckOutlined,
  CloudOutlined,
  AppstoreOutlined,
  InboxOutlined,
  GiftOutlined,
  DatabaseOutlined,
  LayoutOutlined,
} from '@ant-design/icons-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import { getTemplates, getTemplateDetail, getTemplateZones } from '../../api/template'
import { createTask } from '../../api/task'
import { getStores } from '../../api/store'

const router = useRouter()
const zoneIcons = [CloudOutlined, AppstoreOutlined, InboxOutlined, GiftOutlined, DatabaseOutlined, LayoutOutlined]

const form = reactive({
  taskName: '',
  taskType: 'monthly' as 'monthly' | 'weekly',
  storeIds: [] as string[],
  templateId: null as number | null,
})
const taskMonth = ref<Dayjs | null>(null)
const taskWeek = ref<Dayjs | null>(null)
const deadline = ref<Dayjs | null>(null)
const submitting = ref(false)

const WEEK_DAY_TEXT: Record<number, string> = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
}

const templates = ref<any[]>([])
const preview = ref<any>(null)
const stores = ref<any[]>([])
const storesLoading = ref(false)

const totalMaterials = computed(() => {
  if (!preview.value?.zones) return 0
  return preview.value.zones.reduce(
    (sum: number, z: any) => sum + (z.materials?.length || z.materialCount || 0),
    0,
  )
})

// ===== 周盘工具 =====

/** 归一化为当周周一（ISO 周起始，兼容 picker 返回周日/周一两种行为） */
function weekMonday(value: Dayjs): Dayjs {
  return value.subtract((value.day() + 6) % 7, 'day')
}

/** 由周一推导 ISO 周标签：2026-W34 */
function isoWeekLabel(value: Dayjs): string {
  const thursday = weekMonday(value).add(3, 'day')
  const weekNum = Math.floor(thursday.diff(thursday.startOf('year'), 'day') / 7) + 1
  return `${thursday.year()}-W${String(weekNum).padStart(2, '0')}`
}

/** 展示用：2026年第34周 */
function isoWeekText(value: Dayjs): string {
  const thursday = weekMonday(value).add(3, 'day')
  const weekNum = Math.floor(thursday.diff(thursday.startOf('year'), 'day') / 7) + 1
  return `${thursday.year()}年第${weekNum}周`
}

const taskNamePlaceholder = computed(() =>
  form.taskType === 'weekly' ? '例如：2026年第34周周盘' : '例如：2026年8月月盘',
)

/** 任务名称为空时自动填充默认名 */
function fillDefaultTaskName() {
  if (form.taskName.trim()) return
  if (form.taskType === 'monthly' && taskMonth.value) {
    form.taskName = `${taskMonth.value.format('YYYY年M月')}月盘`
  } else if (form.taskType === 'weekly' && taskWeek.value) {
    form.taskName = `${isoWeekText(taskWeek.value)}周盘`
  }
}

watch([taskMonth, taskWeek], () => fillDefaultTaskName())

// 周盘截止时间预览表（按订货周期：每个订货日前一天为盘点日，各生成一次周盘任务）
const weeklyDeadlineColumns = [
  { title: '门店', dataIndex: 'storeName', key: 'storeName' },
  { title: '订货日', dataIndex: 'dayText', key: 'dayText', width: 140 },
  { title: '盘点截止', dataIndex: 'deadlineText', key: 'deadlineText', width: 220 },
]

const weeklyDeadlines = computed(() => {
  if (!taskWeek.value) return []
  const monday = weekMonday(taskWeek.value)
  const selected = stores.value.filter((s: any) =>
    form.storeIds.map(String).includes(String(s.id)),
  )
  return selected.map((s: any) => {
    // orderDays 来自 store_order_cycle（如 "1,4"），空 = 未配置
    const orderDays = (s.orderDays ? String(s.orderDays).split(',') : [])
      .map(Number)
      .filter((d: number) => d >= 1 && d <= 7)
    if (!orderDays.length) {
      return {
        storeId: s.id,
        storeName: s.mendianmingcheng,
        dayText: '未配置',
        deadlineText: '',
        unconfigured: true,
      }
    }
    // 盘点日 = 订货日 - 1（订货日周一 → 盘点日上周日）；截止 = 订货日当天 05:00
    // 手动创建只按首个订货日生成（其余订货日由每日自动生成覆盖），与后端 batchCreate orderDays.get(0) 保持一致
    const firstDay = orderDays[0]
    const deadline = monday.add(firstDay - 1, 'day').hour(5).minute(0).format('YYYY-MM-DD HH:mm')
    return {
      storeId: s.id,
      storeName: s.mendianmingcheng,
      dayText: orderDays.map((d: number) => WEEK_DAY_TEXT[d]).join('、'),
      deadlineText: deadline,
      multiDay: orderDays.length > 1,
      unconfigured: false,
    }
  })
})

const allStoresSelected = computed({
  get: () => stores.value.length > 0 && form.storeIds.length === stores.value.length,
  set: (val: boolean) => {
    form.storeIds = val ? stores.value.map(s => s.id) : []
  },
})

const indeterminate = computed(() => {
  return form.storeIds.length > 0 && form.storeIds.length < stores.value.length
})

async function fetchTemplates() {
  try {
    const res = (await getTemplates({
      pageSize: 100,
      pageNum: 1,
      templateType: form.taskType,
    })) as any
    templates.value = (res.data?.records || []).filter((t: any) => t.status === 1)
    if (templates.value.length > 0) {
      form.templateId = templates.value[0].id
      await onTemplateChange(templates.value[0].id)
    }
  } catch (e: any) {
    message.error('获取模板列表失败：' + (e.message || ''))
  }
}

/** 切换任务类型：重新按类型加载模板，重置模板选择 */
async function onTaskTypeChange() {
  form.templateId = null
  preview.value = null
  await fetchTemplates()
}

async function fetchStores() {
  storesLoading.value = true
  try {
    const res = (await getStores()) as any
    stores.value = res.data || []
  } catch (e: any) {
    message.error('获取门店列表失败：' + (e.message || ''))
  } finally {
    storesLoading.value = false
  }
}

async function onTemplateChange(templateId: number) {
  preview.value = null
  try {
    const [detailRes, zonesRes] = await Promise.all([
      getTemplateDetail(templateId) as any,
      getTemplateZones(templateId) as any,
    ])
    const zones = zonesRes.data || []
    const materialCount = zones.reduce(
      (s: number, z: any) => s + (z.materialCount ?? z.materials?.length ?? 0),
      0,
    )
    preview.value = {
      templateName: detailRes.data?.templateName || '模板',
      zones,
      zoneCount: zones.length,
      materialCount,
    }
  } catch (e: any) {
    message.error('获取模板预览失败：' + (e.message || ''))
  }
}

async function handleSubmit() {
  if (!form.taskName.trim()) {
    fillDefaultTaskName()
    if (!form.taskName.trim()) {
      message.warning('请输入任务名称')
      return
    }
  }
  if (!form.storeIds || form.storeIds.length === 0) {
    message.warning('请选择门店范围')
    return
  }
  if (!form.templateId) {
    message.warning('请选择应用模板')
    return
  }
  if (form.taskType === 'monthly') {
    if (!taskMonth.value) {
      message.warning('请选择盘点月份')
      return
    }
    if (!deadline.value) {
      message.warning('请选择截止时间')
      return
    }
  } else {
    if (!taskWeek.value) {
      message.warning('请选择盘点周')
      return
    }
    const unconfigured = weeklyDeadlines.value.filter((d: any) => d.unconfigured)
    if (unconfigured.length > 0) {
      message.warning(
        `以下门店未配置订货周期：${unconfigured.map((d: any) => d.storeName).join('、')}，请先到门店订货周期配置设置`,
      )
      return
    }
  }

  const payload: any = {
    taskName: form.taskName.trim(),
    taskType: form.taskType,
    storeIds: form.storeIds,
    templateId: form.templateId,
  }
  if (form.taskType === 'monthly') {
    payload.taskMonth = taskMonth.value!.format('YYYY-MM')
    payload.deadline = deadline.value
  } else {
    payload.taskWeek = isoWeekLabel(taskWeek.value!)
  }

  submitting.value = true
  try {
    await createTask(payload)
    message.success(`已为 ${form.storeIds.length} 个门店创建任务`)
    router.push('/tasks')
  } catch (e: any) {
    message.error(e.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchTemplates()
  fetchStores()
})
</script>

<style scoped>
.task-create-page {
  max-width: 1200px;
  padding-bottom: 88px;
}

.create-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.back-btn {
  font-size: 18px;
  color: #374151;
}

.create-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.config-card,
.preview-card {
  border-radius: var(--radius, 8px);
  min-height: 420px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
  color: #1f2937;
}

.section-bar {
  width: 4px;
  height: 18px;
  background: var(--primary, #0d7a3d);
  border-radius: 2px;
}

.preview-title {
  gap: 8px;
  color: var(--primary, #0d7a3d);
}

.field-tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: #9ca3af;
}

.unconfigured-tag {
  display: inline-block;
  padding: 0 6px;
  font-size: 12px;
  color: #cf1322;
  background: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 4px;
}

.config-link {
  color: var(--primary, #0d7a3d);
  margin-left: 4px;
}

.unconfigured-tip {
  font-size: 12px;
  color: #cf1322;
}

.deadline-line {
  font-size: 12px;
  line-height: 1.7;
  white-space: nowrap;
}

.multi-day-tip {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
}

.preview-name {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
}

.preview-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.preview-stats .stat-card {
  flex: 1;
  text-align: center;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.preview-stats .stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--primary, #0d7a3d);
}

.preview-stats .stat-label {
  font-size: 13px;
  color: #6b7280;
  margin-top: 4px;
}

.preview-zones-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 12px;
}

.zone-preview-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.zone-preview-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: #f9fafb;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  font-size: 14px;
}

.zone-preview-icon {
  font-size: 18px;
  color: var(--primary, #0d7a3d);
}

.create-footer {
  position: fixed;
  bottom: 0;
  left: 220px;
  right: 0;
  background: #fff;
  border-top: 1px solid #f0f0f0;
  padding: 16px 28px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  z-index: 10;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
}

:deep(.ant-layout-sider-collapsed) ~ .ant-layout .create-footer {
  left: 80px;
}
</style>
