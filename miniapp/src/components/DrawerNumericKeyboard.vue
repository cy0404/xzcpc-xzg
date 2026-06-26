<script setup lang="ts">
const props = withDefaults(defineProps<{
  modelValue: string
  confirmText?: string
  loading?: boolean
  disabled?: boolean
}>(), {
  confirmText: '确认',
  loading: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [val: string]
  confirm: []
}>()

function setValue(val: string) {
  emit('update:modelValue', val)
}

function onKey(key: string) {
  if (props.disabled) return
  const cur = props.modelValue || ''
  if (key === 'del') {
    setValue(cur.slice(0, -1))
    return
  }
  if (key === '.') {
    if (cur.includes('.')) return
    setValue(cur ? `${cur}.` : '0.')
    return
  }
  if (cur === '0') {
    setValue(key)
    return
  }
  setValue(cur + key)
}

function onConfirm() {
  if (props.disabled || props.loading) return
  emit('confirm')
}
</script>

<template>
  <view class="kbd">
    <view class="kbd-grid">
      <view class="kbd-key" @click="onKey('1')">1</view>
      <view class="kbd-key" @click="onKey('2')">2</view>
      <view class="kbd-key" @click="onKey('3')">3</view>
      <view class="kbd-key kbd-del" @click="onKey('del')">⌫</view>

      <view class="kbd-key" @click="onKey('4')">4</view>
      <view class="kbd-key" @click="onKey('5')">5</view>
      <view class="kbd-key" @click="onKey('6')">6</view>
      <view class="kbd-confirm" :class="{ disabled: disabled || loading }" @click="onConfirm">
        {{ loading ? '...' : confirmText }}
      </view>

      <view class="kbd-key" @click="onKey('7')">7</view>
      <view class="kbd-key" @click="onKey('8')">8</view>
      <view class="kbd-key" @click="onKey('9')">9</view>

      <view class="kbd-key kbd-dot" @click="onKey('.')">.</view>
      <view class="kbd-key" @click="onKey('0')">0</view>
      <view class="kbd-key kbd-placeholder" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
$border: #E8ECE9;
$p: #2F8F57;

.kbd {
  flex-shrink: 0;
  border-top: 2rpx solid #EEF1EF;
  background: #FAFBF9;
  padding-bottom: env(safe-area-inset-bottom);
}

.kbd-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(4, 96rpx);
}

.kbd-key {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  font-weight: 500;
  color: #1F2421;
  background: #fff;
  border-right: 2rpx solid $border;
  border-bottom: 2rpx solid $border;
  box-sizing: border-box;

  &:active {
    background: #EEF1EF;
  }
}

.kbd-del {
  font-size: 36rpx;
  color: #66706A;
}

.kbd-dot {
  font-size: 44rpx;
  font-weight: 700;
}

.kbd-placeholder {
  pointer-events: none;
  background: #fff;
}

.kbd-confirm {
  grid-row: 2 / span 3;
  grid-column: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $p;
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  border-bottom: 2rpx solid $border;
  box-sizing: border-box;

  &:active {
    background: #247847;
  }

  &.disabled {
    opacity: 0.55;
  }
}
</style>
