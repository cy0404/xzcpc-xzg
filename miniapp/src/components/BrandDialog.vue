<script setup lang="ts">
defineProps<{
  show: boolean
  title?: string
  content?: string
  icon?: 'info' | 'warning' | 'danger'
  confirmText?: string
  cancelText?: string
  confirmType?: 'primary' | 'danger'
  showTip?: boolean
  tipText?: string
}>()

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
  (e: 'update:show', value: boolean): void
}>()

function onCancel() {
  emit('cancel')
  emit('update:show', false)
}

function onConfirm() {
  emit('confirm')
}
</script>

<template>
  <wd-popup
    :model-value="show"
    custom-class="brand-dialog-popup"
    custom-style="background: transparent;"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => $emit('update:show', v)"
  >
    <view class="dialog-wrap">
      <view class="dialog-card">
        <view class="dialog-icon" :class="`icon-${icon || 'info'}`">
          <text class="icon-char">
            <template v-if="icon === 'danger'">&#10006;</template>
            <template v-else-if="icon === 'warning'">!</template>
            <template v-else>i</template>
          </text>
        </view>
        <text class="dialog-title">{{ title || '提示' }}</text>
        <text class="dialog-content">
          <slot>{{ content }}</slot>
        </text>

        <view v-if="showTip" class="dialog-tip">
          <text class="tip-icon">&#9432;</text>
          <text class="tip-text">{{ tipText }}</text>
        </view>

        <view class="dialog-actions">
          <view class="dialog-btn cancel" @click="onCancel">
            <text>{{ cancelText || '取消' }}</text>
          </view>
          <view
            class="dialog-btn confirm"
            :class="confirmType === 'danger' ? 'btn-danger' : 'btn-primary'"
            @click="onConfirm"
          >
            <text>{{ confirmText || '确认' }}</text>
          </view>
        </view>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.dialog-wrap {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 64rpx;
  box-sizing: border-box;
}

.dialog-card {
  width: 100%;
  background: #fff;
  border-radius: 24rpx;
  padding: 56rpx 40rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 12rpx 48rpx rgba(0, 0, 0, 0.12);
}

.dialog-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
  font-size: 48rpx;
  font-weight: 700;

  &.icon-info {
    background: #E8F5EE;
    color: #00734A;
  }

  &.icon-danger {
    background: #FCE6EA;
    color: #E84B61;
  }

  &.icon-warning {
    background: #FFF4E0;
    color: #F39B27;
  }
}

.icon-char {
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1;
}

.dialog-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 16rpx;
}

.dialog-content {
  font-size: 28rpx;
  color: #4A4A4A;
  text-align: center;
  line-height: 1.5;
  margin-bottom: 36rpx;
}

.dialog-tip {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 20rpx;
  background: #F5F6F8;
  border-radius: 12rpx;
  margin-bottom: 36rpx;
  width: 100%;
  box-sizing: border-box;
}

.tip-icon {
  font-size: 26rpx;
  color: #8C8C8C;
  flex-shrink: 0;
}

.tip-text {
  font-size: 24rpx;
  color: #4A4A4A;
  line-height: 1.5;
}

.dialog-actions {
  display: flex;
  gap: 24rpx;
  width: 100%;
}

.dialog-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 500;

  &.cancel {
    background: #F0F1F3;
    color: #4A4A4A;
  }

  &.btn-primary {
    background: #00734A;
    color: #fff;
  }

  &.btn-danger {
    background: #E84B61;
    color: #fff;
  }
}
</style>
