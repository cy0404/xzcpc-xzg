<script setup lang="ts">
defineProps<{
  rows?: number
}>()
</script>

<template>
  <view class="skeleton">
    <view v-for="i in (rows || 3)" :key="i" class="skeleton-row">
      <view class="skeleton-line skeleton-short" />
      <view class="skeleton-line skeleton-long" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.skeleton {
  padding: 32rpx;
}

.skeleton-row {
  margin-bottom: 40rpx;
}

.skeleton-line {
  height: 28rpx;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 37%, #f0f0f0 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
  border-radius: 6rpx;
  margin-bottom: 16rpx;
}

.skeleton-short {
  width: 50%;
}

.skeleton-long {
  width: 90%;
}

@keyframes skeleton-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
</style>
