<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const src = ref('')

onLoad((q: any) => {
  src.value = q?.url ? decodeURIComponent(q.url) : ''
  // 可选动态标题（智能订货等 H5 复用本页时传入；不传则保持 pages.json 默认标题）
  if (q?.title) {
    uni.setNavigationBarTitle({ title: decodeURIComponent(q.title) })
  }
})
</script>

<template>
  <web-view v-if="src" :src="src" />
</template>
