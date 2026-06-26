<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchTaskSummary, submitTask, fetchUnenteredMaterials } from '@/api/task'
import BrandDialog from '@/components/BrandDialog.vue'
import { materialDisplayName } from '@/utils/formatter'

const taskId = ref(0)
const loading = ref(true)
const submitting = ref(false)
const summary = ref<any>(null)
const confirmVisible = ref(false)
const unenteredNames = ref('')
const unenteredLoading = ref(false)

onLoad((options: any) => {
  taskId.value = Number(options.taskId)
  loadSummary()
})

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await fetchTaskSummary(taskId.value)
  } finally {
    loading.value = false
  }
}

const allDone = computed(() => {
  if (!summary.value) return false
  return (
    summary.value.savedZones === summary.value.totalZones &&
    summary.value.enteredMaterials === summary.value.totalMaterials
  )
})

const unfinishedZones = computed(() => {
  if (!summary.value) return 0
  return (summary.value.totalZones || 0) - (summary.value.savedZones || 0)
})

const unenteredMaterials = computed(() => {
  if (!summary.value) return 0
  return (summary.value.totalMaterials || 0) - (summary.value.enteredMaterials || 0)
})

async function handleSubmit() {
  if (submitting.value) return
  confirmVisible.value = false
  submitting.value = true
  try {
    await submitTask(taskId.value)
    uni.redirectTo({ url: `/pages/task/result/index?taskId=${taskId.value}&justSubmitted=1` })
  } catch {
    submitting.value = false
  }
}

async function onTapSubmit() {
  if (submitting.value) return
  if (allDone.value) {
    confirmVisible.value = true
  } else {
    unenteredLoading.value = true
    try {
      const list: any[] = await fetchUnenteredMaterials(taskId.value) || []
      unenteredNames.value = list.map((m: any) => materialDisplayName(m)).join('、')
    } finally {
      unenteredLoading.value = false
    }
    confirmVisible.value = true
  }
}

function formatQty(v: any) {
  const n = Number(v)
  return Number.isInteger(n) ? n.toString() : n.toFixed(2).replace(/\.?0+$/, '')
}

function unitBreakdownText(item: any) {
  if (!item.unitBreakdown || !item.unitBreakdown.length) return ''
  return item.unitBreakdown
    .filter((bd: any) => bd && Number(bd.qty) > 0)
    .map((bd: any) => {
      const prefix = bd.isWeight ? '净重' : ''
      return `${prefix}${formatQty(bd.qty)}${bd.unit || ''}`
    })
    .join(' · ')
}

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="page">
    <!-- 内容滚动区 -->
    <scroll-view class="scroll-body" scroll-y enhanced :show-scrollbar="false">
      <view class="content" v-if="!loading && summary">
        <!-- 任务完成情况 -->
        <view class="status-card">
          <view class="status-top">
            <text class="status-title">任务完成情况</text>
            <view class="status-check" :class="allDone ? 'done' : 'pending'">
              <text class="check-icon">{{ allDone ? '✓' : '!' }}</text>
            </view>
          </view>
          <view class="stat-grid">
            <view class="stat-cell">
              <text class="stat-label">分区总数</text>
              <text class="stat-value">{{ summary.totalZones }}</text>
            </view>
            <view class="stat-cell">
              <text class="stat-label">已完成分区</text>
              <text class="stat-value green">{{ summary.savedZones }}</text>
            </view>
            <view class="stat-cell">
              <text class="stat-label">未完成分区</text>
              <text class="stat-value" :class="unfinishedZones > 0 ? 'red' : ''">
                {{ unfinishedZones }}
              </text>
            </view>
            <view class="stat-cell">
              <text class="stat-label">物料总数</text>
              <text class="stat-value">{{ summary.totalMaterials }}</text>
            </view>
            <view class="stat-cell">
              <text class="stat-label">已录入物料</text>
              <text class="stat-value green">{{ summary.enteredMaterials }}</text>
            </view>
            <view class="stat-cell">
              <text class="stat-label">未录入物料</text>
              <text class="stat-value" :class="unenteredMaterials > 0 ? 'red' : ''">
                {{ unenteredMaterials }}
              </text>
            </view>
          </view>
        </view>

        <!-- 物料汇总表 -->
        <view class="material-card">
          <text class="card-title">物料汇总表</text>

          <view class="table-header">
            <text class="col-name">物料名称</text>
            <text class="col-qty">汇总数量</text>
          </view>

          <view
            v-if="summary.materialSummary && summary.materialSummary.length > 0"
            class="table-body"
          >
            <view
              v-for="(item, idx) in summary.materialSummary"
              :key="item.materialId || idx"
              class="table-row"
            >
              <view class="row-left">
                <text class="row-name">{{ materialDisplayName(item) }}</text>
                <view class="row-sub-line">
                  <text class="zone-tag">{{ item.zoneCount || 0 }}个分区</text>
                  <text v-if="item.isMultiUnit && item.unitBreakdown" class="ub-text">{{ unitBreakdownText(item) }}</text>
                  <text v-else class="ub-text">{{ formatQty(item.totalQty) }} {{ item.baseUnit || item.unit || '' }}</text>
                </view>
              </view>
              <view class="row-right">
                <text class="row-qty">{{ item.totalQty }}</text>
                <text class="row-unit"> {{ item.baseUnit || '' }}</text>
              </view>
            </view>
          </view>
          <view v-else class="empty-text">暂无物料数据</view>
        </view>
      </view>

      <!-- 加载中 -->
      <view v-if="loading" class="loading-wrap">
        <view v-for="i in 5" :key="i" class="skeleton-row">
          <view class="skel skel-short" />
          <view class="skel skel-long" />
        </view>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar safe-bottom">
      <view class="bar-btn outline" @click="goBack">
        <text>返回继续录入</text>
      </view>
      <view
        class="bar-btn primary"
        :class="{ disabled: submitting }"
        @click="onTapSubmit"
      >
        <text>{{ unenteredLoading ? '加载中...' : submitting ? '提交中...' : '提交任务' }}</text>
      </view>
    </view>

    <!-- 提交确认弹窗 -->
    <BrandDialog
      v-model:show="confirmVisible"
      icon="info"
      title="确认提交"
      confirm-text="确认提交"
      cancel-text="取消"
      @confirm="handleSubmit"
      @cancel="confirmVisible = false"
    >
      <template v-if="unenteredNames">
        <text>以下物料未盘点，确认后默认写为 0：</text>
        <text class="unentered-names">{{ unenteredNames }}</text>
      </template>
      <template v-else>
        提交后将<text class="warn-text">无法继续修改</text>。
      </template>
    </BrandDialog>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7F8F6;
  display: flex;
  flex-direction: column;
}

/* 滚动区 */
.scroll-body {
  flex: 1;
}

.content {
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

/* 任务完成情况卡片 */
.status-card {
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.status-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.status-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #1F2421;
}

.status-check {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .check-icon {
    font-size: 24px;
    color: #fff;
    font-weight: 700;
  }

  &.done {
    background: #2F8F57;
  }
  &.pending {
    background: #F39B27;
  }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32rpx 12rpx;
  text-align: center;
}

.stat-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-label {
  font-size: 24rpx;
  color: #66706A;
}

.stat-value {
  margin-top: 8rpx;
  font-size: 40rpx;
  font-weight: 800;
  color: #1F2421;

  &.green {
    color: #2F8F57;
  }
  &.red {
    color: #66706A;
  }
}

/* 物料汇总卡片 */
.material-card {
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.card-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: #1F2421;
  margin-bottom: 28rpx;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FAFBF9;
  padding: 16rpx 24rpx;
  border-radius: 16rpx;
}

.col-name,
.col-qty {
  font-size: 26rpx;
  color: #66706A;
}

.col-qty {
  text-align: right;
}

.table-body {
  margin-top: 4px;
  padding: 0 16rpx;
}

.table-row {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 20rpx 0;
  border-bottom: 1px solid #EEF1EF;

  &:last-child {
    border-bottom: none;
  }
}

.row-left {
  min-width: 0;
}

.row-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2421;
}

.row-sub-line {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 6rpx;
}

.zone-tag {
  display: inline-block;
  padding: 2rpx 14rpx;
  font-size: 22rpx;
  line-height: 38rpx;
  border-radius: 100rpx;
  color: #66706A;
  background: #EEF1EF;
  flex-shrink: 0;
}

.ub-text {
  font-size: 24rpx;
  color: #66706A;
}

.row-qty {
  font-size: 36rpx;
  font-weight: 700;
  color: #1F2421;
}

.row-unit {
  font-size: 24rpx;
  color: #98A19C;
  margin-left: 4rpx;
}

/* 底部操作栏 */
.bottom-bar {
  position: sticky;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 50;
  display: grid;
  grid-template-columns: 1fr 1.45fr;
  gap: 12px;
  padding: 12px 16px calc(16px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.95);
  border-top: 1px solid #EEF1EF;
  box-shadow: 0 -8px 24px rgba(31, 36, 33, 0.06);
}

.bar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;

  &.outline {
    background: #FFFFFF;
    color: #247847;
  }

  &.primary {
    background: #2F8F57;
    color: #fff;
    box-shadow: 0 10px 24px rgba(47, 143, 87, 0.22);
  }

  &.disabled {
    background: #B5C9BF;
    box-shadow: none;
  }
}

/* 空状态 */
.empty-text {
  text-align: center;
  padding: 48rpx 0;
  font-size: 26rpx;
  color: #98A19C;
}

/* 骨架屏 */
.loading-wrap {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.skeleton-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 32rpx;
  background: #fff;
  border-radius: 48rpx;
}

.skel {
  height: 28rpx;
  border-radius: 8rpx;
  background: linear-gradient(90deg, #E8ECE9 25%, #F7F8F6 50%, #E8ECE9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skel-short { width: 40%; }
.skel-long { width: 75%; }

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.warn-text {
  color: #E84B61;
  font-weight: 600;
}

.unentered-names {
  display: block;
  margin-top: 16rpx;
  padding: 20rpx;
  background: #FFF4F2;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: #66706A;
  line-height: 1.6;
  max-height: 300rpx;
  overflow-y: auto;
}
</style>
