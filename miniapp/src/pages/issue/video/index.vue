<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const src = ref('')

onLoad((q: any) => {
  const raw = q?.url ? decodeURIComponent(q.url) : ''
  src.value = raw.startsWith('https://') ? encodeURI(raw) : raw
})

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view style="background:#000;width:100vw;height:100vh">
    <view style="position:fixed;top:0;left:0;right:0;z-index:10;padding:24rpx 32rpx;background:rgba(0,0,0,.6)" @click="goBack">
      <text style="font-size:30rpx;color:#fff">‹ 返回</text>
    </view>
    <video v-if="src" :src="src" style="width:100%;height:100%" autoplay controls show-center-play-btn enable-progress-gesture playsinline webkit-playsinline />
  </view>
</template>
