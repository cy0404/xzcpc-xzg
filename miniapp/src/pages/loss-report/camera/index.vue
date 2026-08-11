<script setup lang="ts">
import { ref, onMounted } from 'vue'

const recording = ref(false)
const previewing = ref(false)
const recordTime = ref(0)
const maxTime = 600 // 最长10分钟
const videoPath = ref('')
const videoSize = ref(0)
let cameraCtx: any = null
let timer: any = null

onMounted(() => {
  cameraCtx = uni.createCameraContext()
})

function startRecord() {
  recording.value = true
  recordTime.value = 0
  timer = setInterval(() => {
    recordTime.value++
    if (recordTime.value >= maxTime) stopRecord()
  }, 1000)
  cameraCtx.startRecord({ timeout: maxTime, success: () => {} })
}

function stopRecord() {
  if (!recording.value) return
  clearInterval(timer)
  recording.value = false
  cameraCtx.stopRecord({
    success: (res: any) => {
      videoPath.value = res.tempVideoPath
      videoSize.value = res.size || 0
      previewing.value = true
    },
    fail: (err: any) => {
      uni.showToast({ title: '录制失败', icon: 'none' })
    },
  })
}

function confirmVideo() {
  const pages = getCurrentPages()
  const prev = pages[pages.length - 2] as any
  if (prev && prev.$vm && prev.$vm.onCameraRecorded) {
    prev.$vm.onCameraRecorded(videoPath.value, videoSize.value, '')
  }
  uni.navigateBack()
}

function reRecord() {
  previewing.value = false
  videoPath.value = ''
}

function close() {
  if (recording.value) { stopRecord(); return }
  uni.navigateBack()
}

function fmtTime(s: number) {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}
</script>

<template>
  <view class="page">
    <!-- 录制模式 -->
    <template v-if="!previewing">
      <camera device-position="back" flash="off" class="camera" />

      <view class="top-bar">
        <text class="top-btn" @click="close">✕</text>
        <view v-if="recording" class="rec-dot"></view>
        <text class="timer">{{ fmtTime(recordTime) }} / {{ fmtTime(maxTime) }}</text>
      </view>

      <view class="bottom-bar">
        <view v-if="!recording" class="record-btn" @click="startRecord">
          <view class="record-btn-inner"></view>
        </view>
        <view v-else class="record-btn" @click="stopRecord">
          <view class="record-btn-inner stop"></view>
        </view>
      </view>

      <text class="tip">最长{{ maxTime / 60 }}分钟</text>
    </template>

    <!-- 预览模式 -->
    <template v-else>
      <video v-if="videoPath" :src="videoPath" class="preview-video" autoplay loop muted />

      <view class="top-bar">
        <text class="top-btn" @click="reRecord">✕</text>
      </view>

      <view class="preview-bar">
        <text class="preview-btn" @click="reRecord">重拍</text>
        <text class="preview-btn confirm" @click="confirmVideo">使用视频</text>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.page { position: relative; width: 100vw; height: 100vh; background: #000; }
.camera { position: absolute; inset: 0; width: 100%; height: 100%; }

.top-bar { position: absolute; top: 0; left: 0; right: 0; display: flex; align-items: center; padding: 40rpx 32rpx; padding-top: calc(env(safe-area-inset-top) + 40rpx); z-index: 10; }
.top-btn { font-size: 36rpx; color: #fff; padding: 8rpx; }
.rec-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #E05A47; margin-left: 16rpx; animation: blink 1s infinite; }
@keyframes blink { 0%,100% { opacity: 1 } 50% { opacity: 0.3 } }
.timer { font-size: 30rpx; color: #fff; font-weight: 700; margin-left: auto; }

.bottom-bar { position: absolute; bottom: 0; left: 0; right: 0; display: flex; justify-content: center; padding-bottom: calc(env(safe-area-inset-bottom) + 100rpx); z-index: 10; }
.record-btn { width: 140rpx; height: 140rpx; border-radius: 50%; border: 6rpx solid #fff; display: flex; align-items: center; justify-content: center; }
.record-btn-inner { width: 100rpx; height: 100rpx; border-radius: 50%; background: #E05A47; }
.record-btn-inner.stop { width: 50rpx; height: 50rpx; border-radius: 8rpx; }
.tip { position: absolute; bottom: calc(env(safe-area-inset-bottom) + 40rpx); left: 0; right: 0; text-align: center; font-size: 24rpx; color: rgba(255,255,255,.5); }

.preview-video { position: absolute; inset: 0; width: 100%; height: 100%; z-index: 5; }
.preview-bar { position: absolute; bottom: 0; left: 0; right: 0; display: flex; justify-content: space-between; padding: 32rpx 48rpx; padding-bottom: calc(env(safe-area-inset-bottom) + 40rpx); z-index: 10; }
.preview-btn { font-size: 32rpx; color: #fff; padding: 16rpx 32rpx; }
.preview-btn.confirm { font-weight: 700; }
</style>
