<script setup lang="ts">
import { ref, watch } from 'vue'
import { replyIssue, storeConfirm, uploadIssueMedia } from '@/api/issue'
import { useUserStore } from '@/store/user'

const props = defineProps<{
  visible: boolean
  issueId: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'success'): void
}>()

const userStore = useUserStore()
const replyText = ref('')
const selectedFiles = ref<string[]>([])
const sending = ref(false)
const previewImgUrl = ref('')
const showImgPreview = ref(false)

watch(() => props.visible, (v) => {
  if (v) {
    replyText.value = ''
    selectedFiles.value = []
  }
})

function chooseFile(type: 'image' | 'video') {
  if (type === 'video') {
    uni.chooseVideo({ maxDuration: 60, compressed: false }).then((res: any) => {
      const path = res?.tempFilePath
      if (!path) return
      selectedFiles.value.push(path)
    }).catch(() => {})
  } else {
    uni.chooseImage({ count: 6, sizeType: ['compressed'] }).then((res: any) => {
      (res?.tempFilePaths || []).forEach((p: string) => selectedFiles.value.push(p))
    }).catch(() => {})
  }
}

function removeFile(idx: number) {
  selectedFiles.value.splice(idx, 1)
}

function isVideo(path: string): boolean {
  return /\.(mp4|mov|avi|mkv)($|\?)/i.test(path)
}

function showImg(url: string) {
  previewImgUrl.value = url
  showImgPreview.value = true
}

function playVideo(url: string) {
  if (url.startsWith('http://tmp/') || url.startsWith('wxfile://')) {
    uni.previewMedia({ sources: [{ url, type: 'video' }] })
    return
  }
  uni.navigateTo({ url: `/pages/issue/video/index?url=${encodeURIComponent(url)}` })
}

async function handleConfirm() {
  if (sending.value) return
  if (!replyText.value.trim() && selectedFiles.value.length === 0) {
    uni.showToast({ title: '请填写未解决原因', icon: 'none' }); return
  }
  sending.value = true
  try {
    // Step 1: 门店确认未解决
    await storeConfirm(props.issueId, 'reject')

    // Step 2: 回复未解决原因
    if (replyText.value.trim() || selectedFiles.value.length) {
      let urls = ''
      if (selectedFiles.value.length) {
        uni.showLoading({ title: '上传中' })
        const uploadedUrls: string[] = []
        for (const fp of selectedFiles.value) {
          try {
            const data: any = await uploadIssueMedia([fp], userStore.chatId || '')
            const list: string[] = data?.urls || (data?.url ? [data.url] : [])
            uploadedUrls.push(...list)
          } catch {
            uni.hideLoading()
            uni.showToast({ title: '上传失败', icon: 'none' })
            return
          }
        }
        urls = uploadedUrls.join(',')
      }
      uni.showLoading({ title: '发送中' })
      await replyIssue(props.issueId, replyText.value.trim(), urls)
    }

    uni.hideLoading()
    uni.showToast({ title: '操作成功', icon: 'success' })
    emit('success')
    emit('close')
  } catch { uni.hideLoading() }
  finally { sending.value = false }
}

function handleCancel() {
  emit('close')
}
</script>

<template>
  <view v-if="visible" class="mask" @click="handleCancel">
    <view class="sheet" @click.stop>
      <view class="sheet-scroll">
        <view class="sheet-head">
          <text class="sh-title">未解决原因</text>
          <text class="sh-close" @click="handleCancel">✕</text>
        </view>

        <text class="sh-label">解决原因</text>
        <textarea v-model="replyText" class="sh-input" placeholder="请描述问题未解决的原因..." :maxlength="500" />

        <view class="upload-row">
          <button class="upload-btn" :disabled="sending" @click="chooseFile('image')">
            <text class="upload-btn-text">📷 图片</text>
          </button>
          <button class="upload-btn" :disabled="sending" @click="chooseFile('video')">
            <text class="upload-btn-text">🎬 视频</text>
          </button>
          <text class="upload-hint">视频≤50M</text>
        </view>

        <view v-if="selectedFiles.length" class="media-list">
          <view v-for="(path, idx) in selectedFiles" :key="idx" class="media-item">
            <image v-if="!isVideo(path)" :src="path" class="media-thumb" mode="aspectFill" @click="showImg(path)" />
            <view v-if="isVideo(path)" class="media-thumb tlr-video" @click="playVideo(path)">
              <text class="tlr-play">▶</text>
            </view>
            <text class="media-del" @click="removeFile(idx)">✕</text>
          </view>
        </view>
      </view>

      <view class="sheet-footer">
        <button class="sh-btn sh-cancel" @click="handleCancel">取消</button>
        <button class="sh-btn sh-confirm" :disabled="sending" @click="handleConfirm">确认</button>
      </view>
    </view>

    <!-- 图片全屏预览 -->
    <view v-if="showImgPreview" class="img-overlay" @click="showImgPreview = false">
      <image :src="previewImgUrl" class="img-full" mode="aspectFit" @click.stop />
      <view class="img-close" @click="showImgPreview = false">✕</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$s: #fff; $p: #2F8F57; $t1: #1F2421; $t2: #66706A; $t3: #98A19C; $b: #E8ECE9; $d: #E05A47; $sub: #FAFBF9;

.mask {
  position: fixed; inset: 0; z-index: 200;
  display: flex; align-items: flex-end;
  background: rgba(31,36,33,.4);
}

.sheet {
  width: 100%; max-height: 80vh;
  border-radius: 32rpx 32rpx 0 0; background: $s;
  display: flex; flex-direction: column;
}

.sheet-scroll {
  flex: 1; overflow-y: auto;
  padding: 32rpx 32rpx 0;
}

.sheet-head {
  display: flex; justify-content: center; align-items: center;
  margin-bottom: 24rpx;
}
.sh-title { font-size: 34rpx; font-weight: 700; color: $t1; }
.sh-close { position: absolute; right: 32rpx; font-size: 36rpx; color: $t2; padding: 8rpx; }

.sh-label { display: block; font-size: 26rpx; font-weight: 600; color: $t1; margin-bottom: 12rpx; }

.sh-input {
  width: 100%; box-sizing: border-box; min-height: 160rpx;
  border: 2rpx solid $b; border-radius: 16rpx; background: $sub;
  padding: 20rpx; font-size: 28rpx; color: $t1;
}

.upload-row {
  display: flex; gap: 16rpx; align-items: center; margin-top: 20rpx;
}
.upload-btn {
  height: 64rpx; padding: 0 28rpx; border-radius: 999rpx;
  border: 2rpx solid $b; background: $s; display: flex; align-items: center;
}
.upload-btn[disabled] { opacity: .6; }
.upload-btn-text { font-size: 24rpx; }
.upload-hint { font-size: 22rpx; color: $t3; flex: 1; }

.media-list { display: flex; flex-direction: column; gap: 12rpx; margin-top: 16rpx; }
.media-item { display: flex; align-items: center; gap: 12rpx; background: $sub; border-radius: 12rpx; padding: 10rpx 16rpx; }
.media-thumb { width: 80rpx; height: 80rpx; border-radius: 8rpx; flex-shrink: 0; }
.tlr-video { background: linear-gradient(135deg,#3A3F3C,#1F2421); display: flex; align-items: center; justify-content: center; }
.tlr-play { font-size: 44rpx; color: #fff; opacity: .9; }
.media-del { font-size: 28rpx; color: $d; padding: 4rpx 8rpx; flex-shrink: 0; }

.sheet-footer {
  display: flex; gap: 20rpx;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  flex-shrink: 0;
}
.sh-btn { flex: 1; height: 88rpx; border-radius: 16rpx; font-size: 30rpx; font-weight: 600; border: 0; display: flex; align-items: center; justify-content: center; }
.sh-cancel { border: 2rpx solid $b; background: $s; color: $t2; }
.sh-confirm { background: $p; color: #fff; }
.sh-confirm[disabled] { opacity: .6; }

.img-overlay { position: fixed; inset: 0; z-index: 300; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,.92); }
.img-full { width: 100%; height: 100%; }
.img-close { position: fixed; top: 80rpx; right: 32rpx; z-index: 10; width: 64rpx; height: 64rpx; border-radius: 999rpx; background: rgba(0,0,0,.5); color: #fff; font-size: 32rpx; display: flex; align-items: center; justify-content: center; }
</style>
