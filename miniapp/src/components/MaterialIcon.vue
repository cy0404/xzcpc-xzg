<script setup lang="ts">
defineProps<{
  name: string
  size?: number
}>()

// 统一使用文档 emoji 作为物料占位图标
const icon = '\u{1F4C4}'
</script>

<template>
  <view class="material-icon" :style="{ width: (size || 80) + 'rpx', height: (size || 80) + 'rpx' }">
    <text class="emoji" :style="{ fontSize: ((size || 80) * 0.5) + 'rpx' }">{{ icon }}</text>
  </view>
</template>

<style lang="scss" scoped>
.material-icon {
  background: #E8F5EE;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.emoji {
  line-height: 1;
}
</style>
