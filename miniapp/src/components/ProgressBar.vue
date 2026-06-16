<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  current: number
  total: number
  color?: string
  height?: number
}>()

const percent = computed(() => {
  if (!props.total) return 0
  return Math.min(100, Math.round((props.current / props.total) * 100))
})
</script>

<template>
  <view class="progress-wrap" :style="{ height: (height || 12) + 'rpx' }">
    <view
      class="progress-inner"
      :style="{
        width: percent + '%',
        background: color || 'linear-gradient(90deg, #07B85D 0%, #00734A 100%)',
      }"
    />
  </view>
</template>

<style lang="scss" scoped>
.progress-wrap {
  width: 100%;
  background: #EDEFF2;
  border-radius: 999rpx;
  overflow: hidden;
}

.progress-inner {
  height: 100%;
  border-radius: 999rpx;
  transition: width 0.3s ease;
}
</style>
