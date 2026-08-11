<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else-if="action">
      <!-- Status card -->
      <view class="card">
        <view class="card-head">
          <view class="min-w-0">
            <text class="source-text">来源：{{ action.visitNo || '--' }} · {{ action.storeName || '--' }}</text>
            <text class="task-name">{{ action.actionName }}</text>
          </view>
          <text class="tag" :class="badgeCls">{{ badgeLabel }}</text>
        </view>
        <view class="state-panel" :class="panelCls" v-if="!hidePanel">
          <text class="state-title">{{ panelTitle }}</text>
          <text class="state-note">{{ panelNote }}</text>
        </view>
      </view>

      <!-- Task content -->
      <view class="card">
        <text class="card-title">任务内容</text>
        <view class="content-rows">
          <view class="content-row"><text class="cl">目标值</text><text class="cv">{{ action.targetValue || '--' }}</text></view>
          <view class="content-row"><text class="cl">具体动作</text><text class="cv">{{ action.specificAction || '--' }}</text></view>
          <view class="content-row"><text class="cl">追踪时间</text><text class="cv" :class="{ overdue: action.taskStatus === 'overdue' }">{{ action.trackingTime || '--' }}</text></view>
          <view class="content-row"><text class="cl">负责人</text><text class="cv">{{ ownerLabel }}</text></view>
        </view>
      </view>

      <!-- Return reason -->
      <view class="card return-card" v-if="action.taskStatus === 'returned' && action.returnReason">
        <text class="card-title return-title">督导退回说明</text>
        <text class="return-text">{{ action.returnReason }}</text>
      </view>

      <!-- Feedback panel (editable when pending/overdue/returned) -->
      <view class="card" v-if="canEdit">
        <text class="card-title">完成反馈</text>
        <textarea class="feedback-input" v-model="note" placeholder="填写实际执行动作、目标达成情况和需要督导关注的问题。" :maxlength="500" />
        <view class="upload-area" @click="uploadImg">
          <text class="upload-icon">📷</text>
          <text class="upload-text">上传图片</text>
          <text class="upload-tip">选填，可补充现场照片、截图或记录</text>
          <view v-if="images.length" class="img-preview-row">
            <image v-for="(url, i) in images" :key="i" :src="url" mode="aspectFill" class="preview-img" @click.stop />
          </view>
        </view>
      </view>

      <!-- Submitted feedback (readonly) -->
      <view class="card" v-else-if="action.completeNote">
        <text class="card-title">完成反馈</text>
        <text class="feedback-text">{{ action.completeNote }}</text>
        <view v-if="imgList.length" class="img-row">
          <image v-for="(url, i) in imgList" :key="i" :src="url" mode="aspectFill" class="fb-img" @click="previewImg(url)" />
        </view>
        <text class="time-text">提交时间：{{ action.submittedAt || '--' }}</text>
      </view>

      <!-- Review result -->
      <view class="card" v-if="action.reviewResult">
        <text class="card-title">审核结果</text>
        <text class="review-result" :class="action.reviewResult === 'approved' ? 'approved' : 'returned'">
          {{ action.reviewResult === 'approved' ? '✓ 督导审核通过' : '✕ 督导审核退回' }}
        </text>
      </view>

      <!-- Visit record link -->
      <view class="card">
        <text class="card-title">拜访记录</text>
        <view class="visit-link" @click="goVisit">
          <view class="visit-link-icon"><text>📋</text></view>
          <view class="visit-link-body">
            <text class="visit-link-title">{{ action.visitNo || '--' }} · {{ action.storeName || '--' }}经营辅导拜访</text>
          </view>
          <text class="visit-link-arrow">›</text>
        </view>
      </view>
    </template>
    <EmptyState v-else-if="!loading" text="任务不存在" />

    <!-- Bottom footer -->
    <view class="bottom-bar" v-if="action && canEdit">
      <button class="btn primary" @click="handleSubmit" :disabled="acting">{{ submitLabel }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchActionDetail, completeAction, type SupervisorVisitAction } from '@/api/supervisor-visit'
import { BASE_URL } from '@/utils/constants'
import Skeleton from '@/components/Skeleton.vue'
import EmptyState from '@/components/EmptyState.vue'

const loading = ref(true)
const acting = ref(false)
const action = ref<SupervisorVisitAction | null>(null)
const note = ref('')
const images = ref<string[]>([])

let actionId = 0

const stateConfig: any = {
  pending:   { badge: '待处理', badgeCls: 'tag-pending', panelCls: 'panel-green', panelTitle: '需要按时跟进', panelNote: '请在追踪时间前提交完成反馈，图片为选填。', submit: '提交完成反馈', hidePanel: false },
  overdue:   { badge: '已逾期', badgeCls: 'tag-overdue', panelCls: '', panelTitle: '', panelNote: '', submit: '提交完成反馈', hidePanel: true },
  returned:  { badge: '已退回', badgeCls: 'tag-returned', panelCls: 'panel-orange', panelTitle: '需要补充反馈', panelNote: '督导已退回，请根据说明补充后再次提交。', submit: '重新提交反馈', hidePanel: false },
  pending_review: { badge: '待督导审核', badgeCls: 'tag-review', panelCls: 'panel-orange', panelTitle: '已提交反馈', panelNote: '等待督导审核，审核通过后任务完成。', submit: '', hidePanel: false },
  completed: { badge: '已完成', badgeCls: 'tag-done', panelCls: 'panel-green', panelTitle: '任务已完成', panelNote: '该行动项已通过督导审核。', submit: '', hidePanel: false },
}

const cfg = computed(() => stateConfig[action.value?.taskStatus || 'pending'] || stateConfig.pending)
const badgeLabel = computed(() => cfg.value.badge)
const badgeCls = computed(() => cfg.value.badgeCls)
const panelCls = computed(() => cfg.value.panelCls)
const panelTitle = computed(() => cfg.value.panelTitle)
const panelNote = computed(() => cfg.value.panelNote)
const submitLabel = computed(() => cfg.value.submit)
const hidePanel = computed(() => cfg.value.hidePanel)
const canEdit = computed(() => action.value && ['pending', 'overdue', 'returned'].includes(action.value.taskStatus))
const ownerLabel = computed(() => action.value?.responsibleRole === 'owner' ? '加盟商老板' : '店长')
const imgList = computed(() => action.value?.completeImages ? action.value.completeImages.split(',').filter(Boolean) : [])

onLoad((q: any) => { actionId = Number(q?.id || 0); load() })

async function load() {
  loading.value = true
  try {
    const res: any = await fetchActionDetail(actionId)
    if (res) action.value = res
  } finally { loading.value = false }
}

function goVisit() {
  if (action.value?.visitId) {
    uni.navigateTo({ url: `/pages/supervisor-visit/detail/index?id=${action.value.visitId}` })
  }
}

function previewImg(url: string) {
  uni.previewImage({ urls: imgList.value, current: url })
}

function uploadImg() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success(res: any) {
      const filePath = res.tempFilePaths[0]
      const baseUrl = (BASE_URL as string).replace(/\/api\/mp\/?$/, '')
      uni.uploadFile({
        url: `${baseUrl}/api/mp/upload/voucher`,
        filePath,
        name: 'file',
        header: { Authorization: `Bearer ${uni.getStorageSync('token')}` },
        success: (upRes: any) => {
          try {
            const d = JSON.parse(upRes.data)
            if (d.code === 200) {
              images.value.push(d.data?.url || d.data)
            } else {
              uni.showToast({ title: '上传失败', icon: 'none' })
            }
          } catch { uni.showToast({ title: '上传失败', icon: 'none' }) }
        },
        fail: () => uni.showToast({ title: '上传失败', icon: 'none' })
      })
    }
  })
}

async function handleSubmit() {
  if (acting.value) return
  if (!note.value.trim()) { uni.showToast({ title: '请填写完成说明', icon: 'none' }); return }
  acting.value = true
  try {
    const imgStr = images.value.join(',')
    await completeAction(actionId, note.value.trim(), imgStr)
    uni.showToast({ title: '已提交', icon: 'success' })
    load()
  } catch { /* handled */ }
  finally { acting.value = false }
}
</script>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$w:#E58A2D;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}
.card{background:$s;border-radius:16rpx;padding:28rpx;margin-bottom:20rpx;border:2rpx solid $b}
.card-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16rpx}
.min-w-0{min-width:0;flex:1}
.source-text{font-size:24rpx;color:$t3;line-height:36rpx}
.task-name{display:block;margin-top:8rpx;font-size:34rpx;font-weight:700;color:$t1;line-height:44rpx}
.tag{font-size:22rpx;padding:4rpx 16rpx;border-radius:999rpx;font-weight:600;flex-shrink:0}
.tag-pending{background:#E7F4EB;color:$p}
.tag-overdue{background:#FFF4F2;color:$d}
.tag-returned{background:#FFF8EE;color:$w}
.tag-review{background:#FFF8EE;color:$w}
.tag-done{background:#E7F4EB;color:$p}
.state-panel{margin-top:20rpx;border-radius:12rpx;padding:16rpx 20rpx}
.panel-green{background:#F1F8F3}
.panel-orange{background:#FFF8EE}
.state-title{display:block;font-size:28rpx;font-weight:600;color:$t1}
.state-note{display:block;margin-top:8rpx;font-size:26rpx;color:$t2;line-height:40rpx}
.card-title{font-size:30rpx;font-weight:700;color:$t1;margin-bottom:20rpx;display:block}
.content-rows{display:grid;gap:0}
.content-row{display:flex;padding:16rpx 0;border-bottom:2rpx solid $b}
.content-row:last-child{border-bottom:none}
.cl{width:160rpx;font-size:26rpx;color:$t3;flex-shrink:0}
.cv{font-size:28rpx;color:$t1;font-weight:500;flex:1}
.cv.overdue{color:$d}
.return-card{background:#FFF4F2;border-color:#FADBD8}
.return-title{color:$d}
.return-text{font-size:26rpx;color:$t2;line-height:40rpx}
.feedback-input{width:100%;min-height:200rpx;background:$bg;border:2rpx solid $b;border-radius:12rpx;padding:20rpx;font-size:26rpx;box-sizing:border-box}
.upload-area{margin-top:20rpx;border:2rpx dashed $b;border-radius:12rpx;background:$bg;padding:32rpx;text-align:center}
.upload-icon{font-size:44rpx;display:block}
.upload-text{display:block;margin-top:8rpx;font-size:28rpx;font-weight:600;color:$t1}
.upload-tip{display:block;margin-top:4rpx;font-size:24rpx;color:$t3}
.img-preview-row{display:flex;gap:12rpx;margin-top:16rpx}
.preview-img{width:120rpx;height:120rpx;border-radius:12rpx}
.feedback-text{font-size:26rpx;color:$t1;line-height:40rpx}
.time-text{display:block;margin-top:16rpx;font-size:24rpx;color:$t3}
.img-row{display:flex;gap:12rpx;margin-top:16rpx;flex-wrap:wrap}
.fb-img{width:160rpx;height:160rpx;border-radius:12rpx}
.review-result{font-size:28rpx;font-weight:600;line-height:40rpx}
.review-result.approved{color:$p}
.review-result.returned{color:$d}
.visit-link{display:flex;align-items:center;gap:16rpx;padding:16rpx;background:$bg;border:2rpx solid $b;border-radius:12rpx}
.visit-link-icon{width:80rpx;height:80rpx;border-radius:50%;background:#E7F4EB;display:flex;align-items:center;justify-content:center;flex-shrink:0;font-size:36rpx}
.visit-link-body{flex:1;min-width:0}
.visit-link-title{font-size:26rpx;font-weight:600;color:$t1;line-height:38rpx}
.visit-link-arrow{font-size:32rpx;color:$t3;flex-shrink:0}
.bottom-bar{position:fixed;left:0;right:0;bottom:0;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:linear-gradient(to top,#fff 60%,transparent)}
.btn{width:100%;height:96rpx;border-radius:16rpx;font-weight:600;font-size:30rpx;display:flex;align-items:center;justify-content:center;border:none}
.btn.primary{background:$p;color:#fff}
</style>
