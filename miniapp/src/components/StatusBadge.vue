<script setup lang="ts">
defineProps<{
  type?: 'success' | 'progress' | 'danger' | 'warning' | 'gray' | 'primary'
  text: string
  dot?: boolean
}>()
</script>

<template>
  <view class="status-badge" :class="`badge-${type || 'gray'}`">
    <view v-if="dot" class="badge-dot" />
    <text>{{ text }}</text>
  </view>
</template>

<style lang="scss" scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 6rpx 18rpx;
  border-radius: 28rpx;
  font-size: 22rpx;
  font-weight: 500;
  line-height: 1.4;
}

.badge-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: currentColor;
  margin-right: 8rpx;
}

.badge-success {
  background: #E8F5EE;
  color: #00734A;
}

.badge-progress {
  background: #FCEAEE;
  color: #C24563;
}

.badge-danger {
  background: #FCE6EA;
  color: #E84B61;
}

.badge-warning {
  background: #FFF4E0;
  color: #F39B27;
}

.badge-gray {
  background: #F0F1F3;
  color: #909399;
}

.badge-primary {
  background: #00734A;
  color: #fff;
}
</style>
