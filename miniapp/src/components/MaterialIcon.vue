<script setup lang="ts">
defineProps<{
  name: string
  size?: number
  imageUrl?: string
}>()

// 统一使用文档 emoji 作为物料占位图标（有图片时显示真实图片）
const icon = '\u{1F4C4}'
</script>

<template>
  <view class="material-icon" :style="{ width: (size || 80) + 'rpx', height: (size || 80) + 'rpx' }">
    <image v-if="imageUrl" class="mat-img" :src="imageUrl" mode="aspectFill" />
    <text v-else class="emoji" :style="{ fontSize: ((size || 80) * 0.5) + 'rpx' }">{{ icon }}</text>
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
  overflow: hidden;
}

.mat-img {
  width: 100%;
  height: 100%;
}

.emoji {
  line-height: 1;
}
</style>
