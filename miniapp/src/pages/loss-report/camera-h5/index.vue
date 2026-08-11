<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const src = ref('')

onLoad((q: any) => {
  src.value = q?.url ? decodeURIComponent(q.url) : ''
})

function onMessage(e: any) {
  const list = e?.detail?.data || []
  if (!list.length) return
  const last = list[list.length - 1]
  if (last?.type === 'loss-submitted') {
    uni.showToast({ title: '提交成功', icon: 'success' })
    setTimeout(function() { uni.reLaunch({ url: '/pages/loss-report/list/index' }) }, 500)
  }
  if (last?.type === 'video-recorded' && last.url) {
    const pages = getCurrentPages()
    const prev = pages[pages.length - 2] as any
    if (prev && prev.$vm && prev.$vm.onCameraRecorded) {
      prev.$vm.onCameraRecorded(last.url, last.size || 0, last.thumb || '')
    }
    uni.navigateBack()
  }
}
</script>

<template>
  <web-view v-if="src" :src="src" @message="onMessage" />
</template>
