<template>
  <div class="task-create-page">
    <PageModuleTabs />

    <div class="create-header">
      <a-button type="text" class="back-btn" @click="$router.push('/tasks')">
        <ArrowLeftOutlined />
      </a-button>
      <h1 class="create-title">创建月盘任务</h1>
    </div>

    <a-row :gutter="24" class="create-body">
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" class="config-card">
          <div class="section-title">
            <span class="section-bar" />
            基础配置
          </div>

          <a-form layout="vertical" :model="form" class="create-form">
            <a-form-item label="任务名称" required>
              <a-input v-model:value="form.taskName" placeholder="例如：2026年4月常规月盘任务" />
            </a-form-item>

            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="盘点月份" required>
                  <a-month-picker
                    v-model:value="taskMonth"
                    placeholder="选择月份"
                    style="width: 100%"
                    format="MMMM YYYY"
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

            <a-form-item label="截止时间" required>
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
import { ref, reactive, computed, onMounted } from 'vue'
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
  storeIds: [] as string[],
  templateId: null as number | null,
})
const taskMonth = ref<Dayjs | null>(null)
const deadline = ref<Dayjs | null>(null)
const submitting = ref(false)

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
    const res = (await getTemplates({ pageSize: 100, pageNum: 1 })) as any
    templates.value = (res.data?.records || []).filter((t: any) => t.status === 1)
    if (templates.value.length > 0) {
      form.templateId = templates.value[0].id
      await onTemplateChange(templates.value[0].id)
    }
  } catch (e: any) {
    message.error('获取模板列表失败：' + (e.message || ''))
  }
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
    message.warning('请输入任务名称')
    return
  }
  if (!taskMonth.value) {
    message.warning('请选择盘点月份')
    return
  }
  if (!form.storeIds || form.storeIds.length === 0) {
    message.warning('请选择门店范围')
    return
  }
  if (!form.templateId) {
    message.warning('请选择应用模板')
    return
  }
  if (!deadline.value) {
    message.warning('请选择截止时间')
    return
  }

  submitting.value = true
  try {
    await createTask({
      taskName: form.taskName.trim(),
      taskMonth: taskMonth.value.format('YYYY-MM'),
      storeIds: form.storeIds,
      templateId: form.templateId,
      deadline: deadline.value,
    })
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
