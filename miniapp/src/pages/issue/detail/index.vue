<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchIssueDetail, fetchIssueRecords, replyIssue, acceptIssue, storeConfirm, uploadIssueMedia, type Issue } from '@/api/issue'
import { useUserStore } from '@/store/user'
import { formatDateTime } from '@/utils/formatter'
import Skeleton from '@/components/Skeleton.vue'
import IssueRejectSheet from '@/components/IssueRejectSheet.vue'

const userStore = useUserStore()
const loading = ref(true)
const issue = ref<Issue | null>(null)
const acting = ref(false)

// 未解决原因弹窗
const showRejectSheet = ref(false)

// 外部处理记录
const recordsLoading = ref(false)
const records = ref<any[]>([])
const solutionPhotos = ref<string[]>([])

/** 合并 records + replies 为统一时间线,按时间排序 */
const timelineItems = computed(() => {
  const items: any[] = []
  records.value.forEach((r: any) => items.push({ kind: 'record', time: r.time, author: r.author, text: r.text, photos: r.photos }))
  replies.value.forEach((r: any) => {
    let media = r.mediaUrls
    if (Array.isArray(media)) media = media.join(',')
    items.push({ kind: 'reply', time: r.repliedAt, author: r.repliedBy, text: r.replyText, mediaUrls: media || '' })
  })
  items.sort((a, b) => (a.time || '').localeCompare(b.time || ''))
  return items
})
const replies = ref<any[]>([])

// 验收 + 回复
const showAcceptSheet = ref(false)
const acceptRemark = ref('')
const replyText = ref('')
// 选中的待上传文件（本地路径）
const selectedFiles = ref<string[]>([])
const replySending = ref(false)
const previewImgUrl = ref('')
const showImgPreview = ref(false)

let issueId = 0

onLoad((q: any) => {
  issueId = Number(q?.id || 0)
  load()
})

onPullDownRefresh(async () => {
  await load()
  await loadRecords()
  uni.stopPullDownRefresh()
})

async function load() {
  loading.value = true
  try {
    issue.value = await fetchIssueDetail(issueId)
  } catch { /* handled */ }
  finally { loading.value = false }
  if (issue.value?.xiangmuId) {
    loadRecords()
  }
}

async function loadRecords() {
  recordsLoading.value = true
  try {
    const data: any = await fetchIssueRecords(issueId)
    records.value = data?.records || []
    solutionPhotos.value = data?.solutionPhotos || []
    replies.value = data?.replies || []
  } catch { /* ignore */ }
  finally { recordsLoading.value = false }
}

function urgencyLabel(u: string): string {
  return ({ urgent: '非常紧急', normal: '紧急', low: '一般' } as Record<string, string>)[u] || u
}
function statusLabel(s: string): string {
  return ({
    PENDING_CONFIRMATION: '待处理', IN_PROGRESS: '处理中', PENDING_CONTACT: '未联系上', PENDING_ACCEPTANCE: '待验收',
    RESOLVED: '已解决', CLOSED: '已解决', OVERDUE: '逾期',
  } as Record<string, string>)[s] || s
}
function statusClass(s: string): string {
  return ({
    PENDING_CONFIRMATION: 'st-gray', IN_PROGRESS: 'st-warn', PENDING_CONTACT: 'st-danger', PENDING_ACCEPTANCE: 'st-warn',
    RESOLVED: 'st-primary', CLOSED: 'st-primary', OVERDUE: 'st-danger',
  } as Record<string, string>)[s] || 'st-gray'
}
function severityClass(s: string): string {
  if (!s) return 'sv-default'
  if (s.includes('严重')) return 'sv-danger'
  if (s.includes('较大')) return 'sv-warn'
  if (s.includes('一般')) return 'sv-normal'
  if (s.includes('轻微')) return 'sv-light'
  return 'sv-default'
}

function previewSolutionPhoto(url: string) {
  uni.previewImage({ urls: solutionPhotos.value, current: url })
}
function showImg(url: string) {
  previewImgUrl.value = url
  showImgPreview.value = true
}
function playVideo(url: string) {
  // 本地视频用 previewMedia
  if (url.startsWith('http://tmp/') || url.startsWith('wxfile://')) {
    uni.previewMedia({ sources: [{ url, type: 'video' }] })
    return
  }
  // 远程视频跳独立页
  uni.navigateTo({ url: `/pages/issue/video/index?url=${encodeURIComponent(url)}` })
}

async function confirmAccept() {
  if (acting.value || !issue.value) return
  acting.value = true
  try {
    await acceptIssue(issue.value.id, acceptRemark.value)
    showAcceptSheet.value = false
    uni.showToast({ title: '已验收', icon: 'success' })
    await load()
  } catch { /* handled */ }
  finally { acting.value = false }
}

async function handleStoreConfirm(action: 'accept' | 'reject') {
  if (acting.value || !issue.value) return
  if (action === 'reject') {
    showRejectSheet.value = true
    return
  }
  // accept 保持原有确认弹窗
  const res: any = await new Promise(resolve => {
    uni.showModal({ title: '确认已解决', content: '确定问题已解决吗？', success: r => resolve(r.confirm) })
  })
  if (!res) return
  acting.value = true
  try {
    await storeConfirm(issue.value.id, action)
    uni.showToast({ title: '操作成功', icon: 'success' })
    await load()
  } catch { /* handled */ }
  finally { acting.value = false }
}

function onRejectSuccess() {
  load()
}

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

function fileName(path: string): string {
  return path.split('/').pop() || path
}

function isVideo(path: string): boolean {
  return /\.(mp4|mov|avi|mkv)($|\?)/i.test(path)
}

async function sendReply() {
  if (replySending.value) return
  if (!replyText.value.trim() && !selectedFiles.value.length) return
  replySending.value = true
  try {
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
    await replyIssue(issueId, replyText.value.trim(), urls)
    replyText.value = ''
    selectedFiles.value = []
    uni.hideLoading()
    uni.showToast({ title: '回复已发送', icon: 'success' })
    await loadRecords()
  } catch { uni.hideLoading() }
  finally { replySending.value = false }
}
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />

    <template v-else-if="issue">
      <!-- 状态卡 -->
      <view class="card status-card">
        <view class="sc-top">
          <text class="sc-status" :class="statusClass(issue.status)">{{ statusLabel(issue.status) }}</text>
          <text class="sc-time">更新于 {{ formatDateTime(issue.updatedAt) }}</text>
        </view>
      </view>

      <!-- 问题信息 -->
      <view class="card">
        <text class="ct">问题信息</text>
        <view class="row"><text class="rl">问题编号</text><text class="rv">{{ issue.bizCode }}</text></view>
        <view v-if="issue.storeName" class="row"><text class="rl">门店</text><text class="rv">{{ issue.storeName }}</text></view>
        <view v-if="issue.title" class="row"><text class="rl">标题</text><text class="rv">{{ issue.title }}</text></view>
        <view v-if="issue.issueType" class="row"><text class="rl">问题类型</text><text class="rv">{{ issue.issueType }}{{ issue.subType ? ' · ' + issue.subType : '' }}</text></view>
        <view v-if="issue.urgency" class="row"><text class="rl">严重程度</text><text class="rv"><text class="severity-tag" :class="severityClass(issue.urgency)">{{ issue.urgency }}</text></text></view>
        <view v-if="issue.processedBy" class="row"><text class="rl">处理人</text><text class="rv">{{ issue.processedBy }}</text></view>
        <view v-if="issue.replyText" class="row"><text class="rl">解决原因</text><text class="rv">{{ issue.replyText }}</text></view>
        <view class="row"><text class="rl">上报时间</text><text class="rv">{{ formatDateTime(issue.createdAt) }}</text></view>
      </view>

      <!-- 处理记录时间线 -->
      <view v-if="issue.xiangmuId" class="card">
        <text class="ct">处理记录</text>
        <view v-if="recordsLoading" style="padding:32rpx 0"><text style="color:#98A19C;font-size:26rpx">加载中...</text></view>
        <view v-else-if="timelineItems.length" class="timeline">
          <view v-for="(item, i) in timelineItems" :key="'tl' + i" class="tl-item">
            <view class="tl-dot" :class="{ on: i === timelineItems.length - 1, reply: item.kind === 'reply' }" />
            <template v-if="i < timelineItems.length - 1"><view class="tl-line" /></template>
            <view class="tl-body" :class="{ 'tl-reply-body': item.kind === 'reply' }">
              <text class="tl-text">{{ item.text }}</text>
              <view v-if="item.photos" class="tlr-media">
                <template v-for="(p, pi) in (Array.isArray(item.photos) ? item.photos : item.photos.split(',').filter(Boolean))" :key="'p'+pi">
                  <image v-if="!isVideo(p)" :src="p" class="tlr-img" @click.stop="showImg(p)" />
                  <view v-else class="tlr-video" @click.stop="playVideo(p)"><text class="tlr-play">▶</text></view>
                </template>
              </view>
              <view v-if="item.kind === 'reply' && item.mediaUrls" class="tlr-media">
                <image v-for="(m, mi) in item.mediaUrls.split(',').filter(Boolean).filter((x: string) => !isVideo(x))" :key="'img'+mi" :src="m" class="tlr-img" @click.stop="showImg(m)" />
                <view v-for="(m, mi) in item.mediaUrls.split(',').filter(Boolean).filter((x: string) => isVideo(x))" :key="'vid'+mi" class="tlr-video" @click.stop="playVideo(m)">
                  <text class="tlr-play">▶</text>
                </view>
              </view>
              <view class="tl-meta">
                <text v-if="item.kind === 'reply'" class="tl-tag">门店回复</text>
                <text class="tl-author">{{ item.author }}</text>
                <text class="tl-time">{{ item.time }}</text>
              </view>
            </view>
          </view>
        </view>
        <text v-else style="color:#98A19C;font-size:26rpx;padding:16rpx 0;display:block">暂无处理记录</text>
      </view>

      <!-- 方案照片 -->
      <view v-if="solutionPhotos.length" class="card">
        <text class="ct">处理照片</text>
        <view class="imgs">
          <image v-for="(img, idx) in solutionPhotos" :key="'sp'+idx" :src="img" class="img" mode="aspectFill" @click="previewSolutionPhoto(img)" />
        </view>
      </view>

      <!-- 回复处理人（已关闭不可回复） -->
      <view v-if="issue.xiangmuId && issue.status !== 'CLOSED' && issue.status !== 'PENDING_ACCEPTANCE'" class="card">
        <text class="ct">回复处理人</text>
        <textarea v-model="replyText" class="fta" placeholder="输入回复内容..." :maxlength="500" />
        <view style="display:flex;gap:20rpx;margin-top:16rpx">
          <button class="upload-btn" :disabled="replySending" @click="chooseFile('image')">
            <text style="font-size:24rpx">📷 图片</text>
          </button>
          <button class="upload-btn" :disabled="replySending" @click="chooseFile('video')">
            <text style="font-size:24rpx">🎬 视频</text>
          </button>
          <text style="font-size:22rpx;color:#98A19C;align-self:center;flex:1">视频≤50M</text>
          <button class="reply-btn" :disabled="replySending || (!replyText.trim() && !selectedFiles.length)" @click="sendReply">发送</button>
        </view>
        <view v-if="selectedFiles.length" class="media-list">
          <view v-for="(path, idx) in selectedFiles" :key="idx" class="media-item">
            <image v-if="!isVideo(path)" :src="path" class="media-thumb" mode="aspectFill" @click="showImg(path)" />
            <view v-if="isVideo(path)" class="media-thumb tlr-video" @click="playVideo(path)"><text class="tlr-play">▶</text></view>
            <text class="media-del" @click="removeFile(idx)">✕</text>
          </view>
        </view>
      </view>

    <!-- 图片浮层 -->
    <view v-if="showImgPreview" class="img-overlay" @click="showImgPreview = false">
      <image :src="previewImgUrl" class="img-full" mode="aspectFit" @click.stop />
      <view class="img-close" @click="showImgPreview = false">✕</view>
    </view>

<!-- 底部确认（PENDING_ACCEPTANCE） -->
      <view v-if="issue.status === 'PENDING_ACCEPTANCE'" class="fab">
        <view style="display:flex;gap:20rpx">
          <button class="fab-btn" style="flex:1;background:#fff;color:#E05A47;border:2rpx solid #E05A47" @click="handleStoreConfirm('reject')">未解决</button>
          <button class="fab-btn" style="flex:2" @click="handleStoreConfirm('accept')">已解决</button>
        </view>
      </view>
    </template>

    <!-- 验收弹窗 -->
    <view v-if="showAcceptSheet" class="mask" @click="showAcceptSheet = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <view>
            <text class="sh-title">验收问题</text>
            <text class="sh-sub">如果问题已解决，可点击「确认已解决」</text>
          </view>
          <text class="sh-close" @click="showAcceptSheet = false">✕</text>
        </view>
        <text class="sh-label">验收备注</text>
        <textarea v-model="acceptRemark" class="sh-input" placeholder="可填写验收说明或补充信息" :maxlength="200" />
        <view class="sh-actions">
          <button class="sh-btn sh-cancel" @click="showAcceptSheet = false">取消</button>
          <button class="sh-btn sh-confirm" :disabled="acting" @click="confirmAccept">确认已解决</button>
        </view>
      </view>
    </view>
    <!-- 未解决原因弹窗 -->
    <IssueRejectSheet
      :visible="showRejectSheet"
      :issue-id="issue?.id || 0"
      @close="showRejectSheet = false"
      @success="onRejectSuccess"
    />
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$w:#E58A2D;$d:#E05A47;$so:#FFF8EE;$sr:#FFF4F2;$sub:#FAFBF9;$sg:#F1F8F3;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}
.card{background:$s;border-radius:20rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);padding:32rpx;margin-bottom:24rpx}
.ct{display:block;font-size:30rpx;font-weight:700;color:$t1;margin-bottom:8rpx}

.status-card .sc-top{display:flex;align-items:center;gap:16rpx}
.sc-status{padding:6rpx 22rpx;border-radius:999rpx;font-size:24rpx;font-weight:600}
.sc-status.st-warn{background:$so;color:$w}
.sc-status.st-primary{background:$ps;color:$p}
.sc-status.st-danger{background:$sr;color:$d}

/* Severity tag */
.severity-tag{display:inline-block;padding:4rpx 18rpx;border-radius:999rpx;font-size:24rpx;font-weight:600}
.sv-danger{background:$sr;color:$d}
.sv-warn{background:$so;color:$w}
.sv-normal{background:#EEF4FA;color:#435F7C}
.sv-light{background:$sub;color:$t2}
.sv-default{background:$sub;color:$t2}
.sc-status.st-gray{background:$sub;color:$t2}
.sc-time{font-size:24rpx;color:$t3}

.row{display:flex;padding:14rpx 0;border-top:2rpx solid #F2F4F1}
.row:first-of-type{border-top:0}
.rl{width:150rpx;flex-shrink:0;font-size:26rpx;color:$t3}
.rv{flex:1;font-size:28rpx;color:$t1;line-height:40rpx;word-break:break-all}

.imgs{margin-top:16rpx;display:flex;flex-wrap:wrap;gap:16rpx}
.img{width:160rpx;height:160rpx;border-radius:12rpx;border:2rpx solid $b;object-fit:contain;background:#EEF1EF}

/* Timeline */
.timeline{padding:8rpx 0}
.tl-item{display:flex;position:relative;padding-bottom:32rpx}
.tl-dot{width:20rpx;height:20rpx;border-radius:999rpx;background:$b;flex-shrink:0;margin-top:6rpx}
.tl-dot.on{background:$p}
.tl-dot.reply{background:$w}
.tl-line{position:absolute;left:9rpx;top:26rpx;width:2rpx;bottom:0;background:#EEF1EF}
.tl-body{flex:1;margin-left:24rpx;min-width:0}
.tl-reply-body{background:$sg;border-radius:12rpx;padding:16rpx 20rpx}
.tl-text{display:block;font-size:28rpx;color:$t1;line-height:42rpx}
.tl-meta{display:flex;align-items:center;gap:12rpx;margin-top:8rpx}
.tl-tag{padding:2rpx 14rpx;border-radius:999rpx;background:$ps;color:$p;font-size:20rpx;font-weight:600}
.tl-author{font-size:24rpx;color:$t2}
.tl-time{font-size:22rpx;color:$t3}
.tlr-media{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:12rpx}
.tlr-img{width:100rpx;height:100rpx;border-radius:10rpx;border:2rpx solid $b;object-fit:contain;background:#EEF1EF}
.tlr-video{width:100rpx;height:100rpx;border-radius:10rpx;background:linear-gradient(135deg,#3A3F3C,#1F2421);display:flex;align-items:center;justify-content:center;position:relative;overflow:hidden}
.tlr-play{font-size:44rpx;color:#fff;opacity:.9}

/* Reply input */
.fta{margin-top:16rpx;width:100%;box-sizing:border-box;min-height:140rpx;border:2rpx solid $b;border-radius:16rpx;background:$sub;padding:20rpx 24rpx;font-size:28rpx;color:$t1}
.reply-btn{height:72rpx;padding:0 48rpx;border-radius:999rpx;background:$p;color:#fff;font-size:28rpx;font-weight:600;border:0;display:flex;align-items:center}
.reply-btn[disabled]{opacity:.6}

.fab{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:linear-gradient(to top,#fff 60%,transparent)}
.fab-btn{width:100%;height:96rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:600;border:0}

.mask{position:fixed;inset:0;z-index:200;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;border-radius:32rpx 32rpx 0 0;background:$s;padding:32rpx 32rpx calc(env(safe-area-inset-bottom) + 32rpx)}
.sheet-head{display:flex;justify-content:space-between;align-items:flex-start;gap:16rpx}
.sh-title{display:block;font-size:34rpx;font-weight:700;color:$t1}
.sh-sub{display:block;margin-top:6rpx;font-size:24rpx;color:$t2}
.sh-close{font-size:36rpx;color:$t2;padding:8rpx}
.sh-label{display:block;margin-top:28rpx;font-size:26rpx;font-weight:600;color:$t1}
.sh-input{margin-top:16rpx;width:100%;min-height:160rpx;box-sizing:border-box;border:2rpx solid $b;border-radius:16rpx;background:$sub;padding:20rpx;font-size:28rpx;color:$t1}
.sh-actions{display:grid;grid-template-columns:1fr 1fr;gap:20rpx;margin-top:28rpx}
.sh-btn{height:88rpx;border-radius:16rpx;font-size:30rpx;font-weight:600;border:0;display:flex;align-items:center;justify-content:center}
.sh-cancel{border:2rpx solid $b;background:$s;color:$t2}
.sh-confirm{background:$p;color:#fff}

/* Upload */
.upload-btn{height:72rpx;padding:0 32rpx;border-radius:999rpx;border:2rpx solid $b;background:$s;color:$t2;font-size:26rpx;display:flex;align-items:center;gap:8rpx}
.upload-btn[disabled]{opacity:.6}
.media-list{margin-top:16rpx;display:flex;flex-direction:column;gap:12rpx}
.media-item{display:flex;align-items:center;gap:12rpx;background:$sub;border-radius:12rpx;padding:10rpx 16rpx}
.media-thumb{width:80rpx;height:80rpx;border-radius:8rpx;flex-shrink:0}
.media-name{font-size:24rpx;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.media-del{font-size:28rpx;color:$d;padding:4rpx 8rpx;flex-shrink:0}

.img-overlay{position:fixed;inset:0;z-index:300;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.92)}
.img-full{width:100%;height:100%}
.img-close{position:fixed;top:80rpx;right:32rpx;z-index:10;width:64rpx;height:64rpx;border-radius:999rpx;background:rgba(0,0,0,.5);color:#fff;font-size:32rpx;display:flex;align-items:center;justify-content:center}

</style>
