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
const tabIndex = ref<0 | 1>(0)

onLoad((options: any) => {
  taskId.value = Number(options.taskId)
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

function isAbnormal(mat: any) {
  return Number(mat.quantity) === 0 && mat.remark
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
      </view>

      <!-- 切换 Tab -->
      <view class="tab-card">
        <view
          class="tab-item"
          :class="{ active: tabIndex === 0 }"
          @click="tabIndex = 0"
        >
          <text class="tab-icon">&#9783;</text>
          <text>按分区看</text>
        </view>
        <view
          class="tab-item"
          :class="{ active: tabIndex === 1 }"
          @click="tabIndex = 1"
        >
          <text class="tab-icon">&#9776;</text>
          <text>按物料汇总看</text>
        </view>
      </view>

      <!-- 分区视图 -->
      <view v-if="tabIndex === 0" class="zone-view">
        <view v-for="zone in result.zones" :key="zone.zoneId" class="zone-card">
          <view class="zone-card-head">
            <view class="zone-head-icon">&#128229;</view>
            <text class="zone-head-name">{{ zone.zoneName }}</text>
          </view>
          <view
            v-for="mat in zone.materials"
            :key="mat.materialId"
            class="zone-material"
            :class="{ 'is-abnormal': isAbnormal(mat) }"
          >
            <MaterialIcon :name="mat.materialName" :size="80" />
            <view class="zone-material-info">
              <text class="zm-name">{{ mat.materialName }}</text>
              <text class="zm-spec">{{ mat.spec || '--' }}</text>
              <text v-if="formatUnitInputs(mat)" class="zm-detail">{{ formatUnitInputs(mat) }}</text>
            </view>
            <view class="zone-material-qty">
              <text class="zm-qty" :class="{ 'danger-text': isAbnormal(mat) }">{{ mat.quantity }}</text>
              <text class="zm-unit">{{ mat.baseUnit || mat.unit || '' }}</text>
              <text v-if="!mat.remark" class="zm-remark">备注：无</text>
              <text v-else class="zm-remark danger-text">备注：{{ mat.remark }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 物料汇总视图 -->
      <view v-else class="summary-view">
        <view class="summary-card">
          <text class="summary-card-title">物料汇总（跨分区合并）</text>
          <view v-for="item in result.summary" :key="item.materialId" class="summary-row">
            <MaterialIcon :name="item.materialName" :size="70" />
            <view class="row-left">
              <text class="row-name">{{ item.materialName }}</text>
              <text class="row-sub">{{ item.spec || '--' }}</text>
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

.tab-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 8rpx;
  margin-bottom: 20rpx;
  display: flex;
  gap: 8rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.tab-item {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border-radius: 14rpx;
  font-size: 28rpx;
  color: #8C8C8C;

  &.active {
    background: #F0FAF3;
    color: #00734A;
    font-weight: 600;
  }
}

.tab-icon {
  font-size: 26rpx;
}

.zone-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.03);
}

.zone-card-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 20rpx;
  background: #F0F1F3;
  border-radius: 14rpx;
  margin-bottom: 16rpx;
}

.zone-head-icon {
  font-size: 28rpx;
  color: #00734A;
}

.zone-head-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.zone-material {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 16rpx;
  border-radius: 16rpx;

  &.is-abnormal {
    background: #FFF6F7;
  }
}

.zone-material-info {
  flex: 1;
  min-width: 0;
}

.zm-name {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: #1A1A1A;
  margin-bottom: 4rpx;
}

.zm-spec {
  font-size: 22rpx;
  color: #8C8C8C;
}

.zone-material-qty {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.zm-origin {
  max-width: 240rpx;
  margin-bottom: 6rpx;
  color: #8C8C8C;
  font-size: 20rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zm-qty {
  font-size: 36rpx;
  font-weight: 700;
  color: #1A1A1A;
  line-height: 1.1;
}

.zm-unit {
  font-size: 22rpx;
  color: #8C8C8C;
  margin-left: 4rpx;
}

.zm-remark {
  font-size: 20rpx;
  color: #B0B0B0;
  margin-top: 6rpx;
  max-width: 240rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.danger-text {
  color: #E84B61;
}

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
