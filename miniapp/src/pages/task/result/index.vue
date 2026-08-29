<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchTaskResult } from '@/api/task'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import MaterialIcon from '@/components/MaterialIcon.vue'
import { formatDateTime } from '@/utils/formatter'

const taskId = ref(0)
const loading = ref(true)
const result = ref<any>(null)

onLoad((options: any) => {
  taskId.value = Number(options.taskId)
  if (options.justSubmitted === '1') {
    uni.showToast({ title: '提交成功', icon: 'success' })
  }
  loadResult()
})

async function loadResult() {
  loading.value = true
  try {
    result.value = await fetchTaskResult(taskId.value)
  } finally {
    loading.value = false
  }
}

function goTaskList() {
  uni.reLaunch({ url: '/pages/task/list/index' })
}

function formatUnitInputs(mat: any) {
  try {
    if (!mat.unitInputs) return ''
    const obj = typeof mat.unitInputs === 'string' ? JSON.parse(mat.unitInputs) : mat.unitInputs
    return Object.entries(obj)
      .filter(([, v]) => v && Number(v) > 0)
      .map(([u, v]) => `${v}${u}`)
      .join(' · ')
  } catch {
    return ''
  }
}
</script>

<template>
  <view class="result-page">
    <Skeleton v-if="loading" :rows="5" />

    <template v-else-if="result">
      <!-- 顶部任务信息 -->
      <view class="info-card">
        <view class="info-top">
          <text class="task-name">{{ result.taskName }}</text>
          <StatusBadge type="success" text="已提交" dot />
        </view>
        <view class="meta-row">
          <text class="meta-icon">&#128340;</text>
          <text class="meta-text">提交时间：{{ formatDateTime(result.submittedAt) }}</text>
        </view>
        <view class="meta-row">
          <text class="meta-icon">&#128100;</text>
          <text class="meta-text">提交人：{{ result.submittedBy || '--' }}</text>
        </view>
        <view v-if="result.totalAmount > 0" class="meta-row">
          <text class="meta-icon">&#128176;</text>
          <text class="meta-text amount-highlight">盘点金额：¥{{ result.totalAmount.toFixed(2) }}</text>
        </view>
      </view>

      <!-- 物料汇总 -->
      <view class="summary-view">
        <view class="summary-card">
          <text class="summary-card-title">物料汇总（跨分区合并）</text>
          <view v-for="item in result.summary" :key="item.materialId" class="summary-row">
            <MaterialIcon :name="item.materialName" :size="70" />
            <view class="row-left">
              <text class="row-name">{{ item.materialName }}</text>
                          </view>
            <view class="row-mid">
              <text v-if="formatUnitInputs(item)" class="row-multi">{{ formatUnitInputs(item) }}</text>
              <text class="row-zone">分区数：{{ item.zoneCount }}</text>
              <text v-if="item.remark" class="row-remark danger-text">备注：{{ item.remark }}</text>
            </view>
            <view class="row-right">
              <text class="row-qty">{{ item.totalQuantity }}</text>
              <text class="row-unit">{{ item.unit || '' }}</text>
            </view>
          </view>
        </view>
      </view>
    </template>

    <EmptyState v-else text="暂无结果数据" />

    <!-- 底部 -->
    <view class="action-bar safe-bottom">
      <view class="primary-action-btn" @click="goTaskList">
        <text class="btn-icon">&#9776;</text>
        <text>返回任务列表</text>
      </view>
    </view>

    <RefreshFab :loading="loading" inline @refresh="loadResult" />
  </view>
</template>

<style lang="scss" scoped>
.result-page {
  min-height: 100vh;
  padding: 24rpx 24rpx 200rpx;
  background: #F4F5F7;
}

.info-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.info-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.task-name {
  font-size: 36rpx;
  font-weight: 700;
  color: #1A1A1A;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 8rpx;
}

.meta-icon {
  font-size: 24rpx;
  color: #8C8C8C;
}

.meta-text {
  font-size: 26rpx;
  color: #4A4A4A;
}
.amount-highlight {
  font-size: 28rpx;
  font-weight: 700;
  color: #E65C2E;
}

.danger-text { color: #E84B61; }

.summary-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.summary-card-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1A1A1A;
  margin-bottom: 20rpx;
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #F4F5F7;

  &:last-child {
    border-bottom: none;
  }
}

.row-left {
  flex: 1;
  min-width: 0;
}

.row-name {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: #1A1A1A;
}

.row-sub {
  font-size: 22rpx;
  color: #8C8C8C;
  display: block;
  margin-top: 4rpx;
}

.row-mid {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4rpx;
}

.row-multi {
  font-size: 20rpx;
  color: #6B7280;
  background: #F3F4F6;
  padding: 4rpx 10rpx;
  border-radius: 6rpx;
  max-width: 260rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.zm-detail {
  max-width: 280rpx;
  color: #00734A;
  font-size: 22rpx;
  margin-top: 4rpx;
  background: #F0FAF3;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-zone {
  font-size: 22rpx;
  color: #00734A;
  background: #F0FAF3;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.row-remark {
  font-size: 20rpx;
  max-width: 160rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-right {
  display: flex;
  align-items: baseline;
  flex-shrink: 0;
}

.row-qty {
  font-size: 32rpx;
  font-weight: 700;
  color: #1A1A1A;
}

.row-unit {
  font-size: 22rpx;
  color: #8C8C8C;
  margin-left: 4rpx;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 32rpx;
  display: flex;
  justify-content: center;
}

.primary-action-btn {
  height: 96rpx;
  border-radius: 999rpx;
  background: #00734A;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 480rpx;
  padding: 0 64rpx;
  color: #fff;
  font-size: 32rpx;
  font-weight: 600;
  box-shadow: 0 12rpx 28rpx rgba(0, 115, 74, 0.3);
}

.btn-icon {
  font-size: 30rpx;
}
</style>
