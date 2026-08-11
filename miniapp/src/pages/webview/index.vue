<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchIssueByExternalId } from '@/api/issue'

const src = ref('')

onLoad((q: any) => {
  const raw = q?.url ? decodeURIComponent(q.url) : ''
  src.value = raw
})

/** 接收 H5 表单 postMessage 回传，查 externalId → 跳详情页 */
function onMessage(e: any) {
  console.log('[webview] @message event fired:', JSON.stringify(e?.detail))
  const list = e?.detail?.data || []
  console.log('[webview] data list length:', list.length)
  if (!list.length) return
  const last = list[list.length - 1]
  console.log('[webview] last item:', JSON.stringify(last))
  if (last?.type !== 'issue-submitted' || !last.id) {
    console.log('[webview] skip: type or id missing')
    return
  }
  const externalId = Number(last.id)
  // 轮询等待 callback 写入（最多等 3 秒）
  pollAndNavigate(externalId, 0)
}

function pollAndNavigate(externalId: number, attempts: number) {
  fetchIssueByExternalId(externalId).then((issue) => {
    uni.showToast({ title: '提交成功', icon: 'success' })
    uni.redirectTo({ url: `/pages/issue/detail/index?id=${issue.id}` })
  }).catch(() => {
    if (attempts < 6) {
      setTimeout(() => pollAndNavigate(externalId, attempts + 1), 500)
    } else {
      uni.showToast({ title: '已提交，处理中可能稍有延迟', icon: 'none' })
      uni.navigateBack()
    }
  })
}
</script>

<template>
  <web-view v-if="src" :src="src" @message="onMessage" />
</template>
