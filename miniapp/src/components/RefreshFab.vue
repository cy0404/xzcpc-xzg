<template>
  <view
    :class="inline ? 'refresh-inline' : 'refresh-fab'"
    @click="handleRefresh"
  >
    <text :class="{ spinning: loading }" :style="inline ? { fontSize: '38rpx', color: '#00734A' } : { fontSize: '44rpx', color: '#00734A' }">&#8635;</text>
    <text v-if="inline && text" class="refresh-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{ loading?: boolean; inline?: boolean; text?: string }>(), { inline: false })
const emit = defineEmits<{ refresh: [] }>()

function handleRefresh() {
  if (!props.loading) emit('refresh')
}
</script>

<style scoped>
/* 浮动模式 */
.refresh-fab {
  position: fixed;
  right: 32rpx;
  bottom: 260rpx;
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

/* 行内模式 */
.refresh-inline {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #E8F5EE;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.refresh-text {
  font-size: 24rpx;
  color: #00734A;
  margin-left: 8rpx;
}

.spinning {
  animation: spin 0.6s linear infinite;
  display: inline-block;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
