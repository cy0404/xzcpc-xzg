<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getLossStandard } from '@/api/loss-report'

const materialId = ref('')
const activeTab = ref<'fruit' | 'video'>('fruit')
const loading = ref(true)
const fruitCheck = ref<any>(null)
const videoUpload = ref<any>(null)

onLoad((options: any) => {
  materialId.value = options.materialId || ''
  fetchData()
})

async function fetchData() {
  if (!materialId.value) return
  loading.value = true
  try {
    const res: any = await getLossStandard(materialId.value)
    if (res.data) {
      fruitCheck.value = res.data.fruitCheck || null
      videoUpload.value = res.data.videoUpload || null
      if (!fruitCheck.value && videoUpload.value) activeTab.value = 'video'
    }
  } catch { /* 静默失败 */ }
  finally { loading.value = false }
}

function previewImages(urls: string[]) {
  if (urls.length) uni.previewImage({ urls, current: urls[0] })
}
</script>

<template>
  <view class="page">
    <!-- Tab -->
    <view class="tab-row">
      <view class="tab-item" :class="{ on: activeTab === 'fruit' }" @click="activeTab = 'fruit'">
        水果验收标准
      </view>
      <view class="tab-item" :class="{ on: activeTab === 'video' }" @click="activeTab = 'video'">
        视频上传标准
      </view>
    </view>

    <view class="content">
      <!-- 水果验收标准 -->
      <template v-if="activeTab === 'fruit'">
        <template v-if="fruitCheck">
          <text class="std-title" v-if="fruitCheck.title">{{ fruitCheck.title }}</text>
          <view class="img-grid" v-if="fruitCheck.mediaUrls && fruitCheck.mediaUrls.length">
            <image v-for="(url, idx) in fruitCheck.mediaUrls" :key="idx" :src="url" mode="aspectFill" class="img-item" @click="previewImages(fruitCheck.mediaUrls)" />
          </view>
          <text class="std-desc" v-if="fruitCheck.description">{{ fruitCheck.description }}</text>
          <view v-if="!fruitCheck.description && (!fruitCheck.mediaUrls || !fruitCheck.mediaUrls.length)" class="empty">暂无数据</view>
        </template>
        <view v-else class="empty">暂无数据</view>
      </template>

      <!-- 视频上传标准 -->
      <template v-if="activeTab === 'video'">
        <template v-if="videoUpload">
          <text class="std-title" v-if="videoUpload.title">{{ videoUpload.title }}</text>
          <view class="img-grid" v-if="videoUpload.mediaUrls && videoUpload.mediaUrls.length">
            <image v-for="(url, idx) in videoUpload.mediaUrls" :key="idx" :src="url" mode="aspectFill" class="img-item" @click="previewImages(videoUpload.mediaUrls)" />
          </view>
          <text class="std-desc" v-if="videoUpload.description">{{ videoUpload.description }}</text>
          <view v-if="!videoUpload.description && (!videoUpload.mediaUrls || !videoUpload.mediaUrls.length)" class="empty">暂无数据</view>
        </template>
        <view v-else class="empty">暂无数据</view>
      </template>
    </view>

    <view v-if="loading" class="loading-hint">加载中...</view>
  </view>
</template>

<style lang="scss" scoped>
$p: #2F8F57; $ps: #E7F4EB; $t1: #1F2421; $t2: #66706A; $t3: #98A19C; $b: #E8ECE9; $bg: #F7F8F6; $s: #fff;

.page { min-height: 100vh; background: $bg; }

.tab-row { display: flex; margin: 24rpx 24rpx 0; background: $s; border-radius: 16rpx; overflow: hidden; border: 1px solid $b; }
.tab-item { flex: 1; text-align: center; padding: 20rpx 0; font-size: 28rpx; color: $t2; background: $s; }
.tab-item.on { background: $p; color: #fff; font-weight: 600; }

.content { padding: 24rpx; }
.std-title { display: block; font-size: 32rpx; font-weight: 700; color: $t1; margin-bottom: 16rpx; }
.img-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8rpx; margin-bottom: 20rpx; }
.img-item { width: 100%; aspect-ratio: 1; border-radius: 12rpx; border: 1px solid $b; background: #FAFBF9; }
.std-desc { display: block; font-size: 28rpx; color: $t2; line-height: 1.8; white-space: pre-wrap; background: $s; border-radius: 12rpx; padding: 24rpx; }
.empty { text-align: center; padding: 80rpx 0; font-size: 26rpx; color: $t3; }
.loading-hint { text-align: center; padding: 40rpx; font-size: 24rpx; color: $t3; }
</style>
