<template>
  <div class="store-weekly-config-page">
    <PageModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">门店订货周期配置</h1>
        <p class="page-subtitle">为各门店设置每周固定订货日（可多选）与暂停开关：订货日前一天自动生成周盘任务，暂停的门店自动跳过</p>
      </div>
      <div class="header-actions">
        <a-button size="large" @click="$router.push('/tasks')">返回盘点列表</a-button>
        <a-button type="primary" size="large" :loading="saving" @click="handleSave">
          <template #icon><SaveOutlined /></template>
          保存配置
        </a-button>
      </div>
    </div>

    <a-card :bordered="false" class="config-card">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'storeType'">
            <a-tag :color="isDirect(record) ? 'green' : 'default'">
              {{ isDirect(record) ? '直营' : '加盟' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'days'">
            <a-checkbox-group
              v-model:value="orderDaysMap[record.id]"
              class="days-group"
            >
              <a-checkbox v-for="(text, d) in WEEK_DAY_OPTIONS" :key="d" :value="Number(d)" :disabled="!isDirect(record)">
                {{ text }}
              </a-checkbox>
            </a-checkbox-group>
            <div v-if="!orderDaysMap[record.id]?.length" class="days-empty">
              {{ isDirect(record) ? '未选择 = 不参与周盘' : '加盟店，不可配置' }}
            </div>
          </template>
          <template v-else-if="column.key === 'paused'">
            <a-switch
              :checked="pausedMap[record.id] === 1"
              :disabled="!isDirect(record) || !orderDaysMap[record.id]?.length"
              checked-children="已暂停"
              un-checked-children="参与"
              @change="(v: boolean) => (pausedMap[record.id] = v ? 1 : 0)"
            />
          </template>
          <template v-else-if="column.key === 'status'">
            <span v-if="!isDirect(record)" class="status-none">加盟店，不参与周盘</span>
            <span v-else-if="orderDaysMap[record.id]?.length" class="status-ok">
              每周 {{ formatDays(orderDaysMap[record.id]) }} 订货
              <span v-if="pausedMap[record.id] === 1" class="status-paused">（已暂停）</span>
            </span>
            <span v-else class="status-none">未配置（不参与周盘）</span>
          </template>
        </template>
      </a-table>

      <div class="config-tip">
        <BulbOutlined />
        <span>
          周盘仅直营店参与：加盟店不可配置订货周期。每个订货日的前一天 9:00 自动生成周盘任务（如订货日为周一 → 上周日盘点），
          盘点截止 = 订货日当天 05:00；门店完成周盘提交后，自动生成智能订货单。一周多个订货日的门店，每周生成多个周盘任务。
        </span>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SaveOutlined, BulbOutlined } from '@ant-design/icons-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import { getStores, updateOrderCycle } from '../../api/store'

const WEEK_DAY_OPTIONS: Record<number, string> = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
}

const loading = ref(false)
const saving = ref(false)
const dataSource = ref<any[]>([])
const orderDaysMap = reactive<Record<string, number[]>>({})
const pausedMap = reactive<Record<string, number>>({})

const columns = [
  { title: '门店', dataIndex: 'mendianmingcheng', key: 'storeName', width: 260, ellipsis: true },
  { title: '类型', key: 'storeType', width: 80, align: 'center' as const },
  { title: '订货日（可多选）', key: 'days', width: 300 },
  { title: '暂停周盘', key: 'paused', width: 120 },
  { title: '当前状态', key: 'status', width: 240 },
]

/** 直营店可配置订货周期，加盟店不参与周盘 */
function isDirect(record: any): boolean {
  return record.storeType === 'direct'
}

function formatDays(days: number[] | undefined) {
  if (!days?.length) return ''
  return [...days]
    .sort((a, b) => a - b)
    .map((d) => WEEK_DAY_OPTIONS[d] || '')
    .join('、')
}

async function fetchStores() {
  loading.value = true
  try {
    const res = (await getStores()) as any
    dataSource.value = res.data || []
    for (const s of dataSource.value) {
      // orderDays 来自 store_order_cycle（如 "1,4"），空 = 未配置
      orderDaysMap[s.id] = s.orderDays
        ? String(s.orderDays).split(',').map(Number).filter((d: number) => d >= 1 && d <= 7)
        : []
      pausedMap[s.id] = s.weeklyPaused ?? 0
    }
  } catch (e: any) {
    message.error('获取门店列表失败：' + (e.message || ''))
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const payload: Record<string, { orderDays: string | null; paused: number }> = {}
  for (const s of dataSource.value) {
    const days = [...(orderDaysMap[s.id] ?? [])].sort((a, b) => a - b)
    payload[s.id] = {
      // 空数组 → null：清空配置（不参与周盘）
      orderDays: days.length ? days.join(',') : null,
      paused: pausedMap[s.id] ?? 0,
    }
  }
  saving.value = true
  try {
    await updateOrderCycle(payload)
    message.success('订货周期配置已保存')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(fetchStores)
</script>

<style scoped>
.store-weekly-config-page {
  max-width: 1180px;
}

.page-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  gap: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 14px;
  color: #6b7280;
}

.header-actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.config-card {
  border-radius: var(--radius, 8px);
}

.days-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0 10px;
  row-gap: 6px;
}

.days-empty {
  margin-top: 4px;
  font-size: 12px;
  color: #9ca3af;
}

.status-ok {
  color: #047857;
  font-size: 13px;
}

.status-paused {
  color: #d97706;
}

.status-none {
  color: #9ca3af;
  font-size: 13px;
}

.config-tip {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding: 12px 14px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  color: #047857;
  font-size: 13px;
  line-height: 1.6;
}
</style>
