<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  text?: string
  type?: 'empty' | 'error' | 'network' | 'search'
  subText?: string
}>()

const iconChar = computed(() => {
  if (props.type === 'error') return '\u26A0'
  if (props.type === 'network') return '\u{1F310}'
  if (props.type === 'search') return '\u{1F50D}'
  return '\u{1F4C4}'
})
</script>

<template>
  <view class="empty-state">
    <view class="empty-icon-wrap">
      <text class="empty-icon">{{ iconChar }}</text>
    </view>
    <text class="empty-text">{{ text || '暂无数据' }}</text>
    <text v-if="subText" class="empty-sub">{{ subText }}</text>
    <slot />
  </view>
</template>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 40rpx;
}

.empty-icon-wrap {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: #F0F1F3;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28rpx;
}

.empty-icon {
  font-size: 72rpx;
  opacity: 0.6;
}

.empty-text {
  font-size: 30rpx;
  color: #4A4A4A;
  text-align: center;
  margin-bottom: 12rpx;
  font-weight: 500;
}

.empty-sub {
  font-size: 26rpx;
  color: #8C8C8C;
  text-align: center;
  line-height: 1.5;
  max-width: 480rpx;
}
</style>
