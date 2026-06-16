<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  valueColor?: string
}>()
</script>

<template>
  <view class="stat-card">
    <text class="stat-label">{{ label }}</text>
    <text class="stat-value" :style="{ color: valueColor }">{{ value }}</text>
  </view>
</template>

<style lang="scss" scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  padding: 12rpx 0;
}

.stat-label {
  font-size: 24rpx;
  color: #8C8C8C;
  margin-bottom: 8rpx;
}

.stat-value {
  font-size: 40rpx;
  font-weight: 700;
  color: #1A1A1A;
  line-height: 1.2;
}
</style>
