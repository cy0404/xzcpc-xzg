<script setup lang="ts">
defineProps<{
  show: boolean
  title?: string
  content?: string
  confirmText?: string
  cancelText?: string
}>()

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()
</script>

<template>
  <wd-message-box
    :visible="show"
    :title="title || '提示'"
    :content="content || '确认执行此操作？'"
    :confirm-text="confirmText || '确认'"
    :cancel-text="cancelText || '取消'"
    @confirm="emit('confirm')"
    @cancel="emit('cancel')"
  />
</template>
