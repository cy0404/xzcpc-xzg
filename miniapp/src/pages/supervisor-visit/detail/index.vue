<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="6" />
    <template v-else-if="visit">
      <!-- 头部状态卡片 -->
      <view class="card">
        <view class="status-row">
          <text class="tag" :class="statusCls">{{ statusLabel }}</text>
          <text class="visit-no">{{ visit.visitNo || '--' }}</text>
        </view>
        <view class="info-grid">
          <view class="info-item"><text class="info-label">门店</text><text class="info-value">{{ visit.storeName || '--' }}</text></view>
          <view class="info-item"><text class="info-label">督导</text><text class="info-value">{{ visit.supervisorName || '--' }}</text></view>
          <view class="info-item"><text class="info-label">拜访日期</text><text class="info-value">{{ visit.visitDate || '--' }}</text></view>
          <view class="info-item"><text class="info-label">确认人</text><view class="info-value-row">
            <text v-if="visit.confirmManagerName">{{ visit.confirmManagerName }}</text>
            <text v-if="visit.confirmManagerName" class="role-tag">店长</text>
            <text v-if="visit.confirmManagerName && visit.confirmOwnerName"> / </text>
            <text v-if="visit.confirmOwnerName">{{ visit.confirmOwnerName }}</text>
            <text v-if="visit.confirmOwnerName" class="role-tag owner">老板</text>
            <text v-if="!visit.confirmManagerName && !visit.confirmOwnerName">{{ confirmPersonLabel }}</text>
          </view></view>
        </view>
      </view>

      <!-- 异议说明 -->
      <view class="card warn" v-if="visit.visitStatus === 'objection' && visit.objectionReason">
        <text class="card-title" style="color:#E05A47">异议说明</text>
        <text class="obj-text">{{ visit.objectionReason }}</text>
      </view>

      <!-- 经营数据 -->
      <view class="card" v-if="bizData">
        <text class="card-title">门店经营数据（上月）</text>
        <view class="biz-grid">
          <view class="biz-item"><text class="biz-label">GMV</text><text class="biz-value">{{ bizData.gmv || '--' }}</text></view>
          <view class="biz-item"><text class="biz-label">目标达成率</text><text class="biz-value">{{ bizData.targetRate || '--' }}</text></view>
          <view class="biz-item"><text class="biz-label">实收率</text><text class="biz-value">{{ bizData.realCollectionRate || '--' }}</text></view>
          <view class="biz-item"><text class="biz-label">客单价</text><text class="biz-value">{{ bizData.avgOrderPrice || '--' }}</text></view>
          <view class="biz-item"><text class="biz-label">EBITDA</text><text class="biz-value">{{ bizData.ebitda || '--' }}</text></view>
          <view class="biz-item"><text class="biz-label">人效</text><text class="biz-value">{{ bizData.staffEfficiency || '--' }}</text></view>
          <view class="biz-item"><text class="biz-label">QSC分数</text><text class="biz-value">{{ bizData.qscScore || '--' }}</text></view>
          <view class="biz-item"><text class="biz-label">差评率</text><text class="biz-value">{{ bizData.negativeRate || '--' }}</text></view>
        </view>
      </view>

      <!-- 沟通记录 -->
      <view class="card" v-if="visit.lastIssueReview || visit.currentFocus || visit.improvementFocus || visit.storeFeedback">
        <text class="card-title">沟通记录</text>
        <view class="comm-block" v-if="visit.lastIssueReview">
          <text class="comm-label">回顾上次访店</text>
          <text class="comm-text">{{ visit.lastIssueReview }}</text>
        </view>
        <view class="comm-block" v-if="visit.currentFocus">
          <text class="comm-label">门店当下重点关注</text>
          <text class="comm-text">{{ visit.currentFocus }}</text>
        </view>
        <view class="comm-block" v-if="visit.improvementFocus">
          <text class="comm-label">本月提升重点</text>
          <text class="comm-text">{{ visit.improvementFocus }}</text>
        </view>
        <view class="comm-block" v-if="visit.storeFeedback">
          <text class="comm-label">门店反馈与所需支持</text>
          <text class="comm-text">{{ visit.storeFeedback }}</text>
        </view>
      </view>

      <!-- 历史拜访 -->
      <view class="card" v-if="historyVisits.length">
        <text class="card-title">最近历史拜访</text>
        <view class="history-item" v-for="h in historyVisits" :key="h.id">
          <text class="history-date">{{ fmtShort(h.visitDate) }}</text>
          <text class="history-summary">{{ h.improvementFocus || h.lastIssueReview || '暂无记录' }}</text>
          <text class="history-state">{{ h.visitStatus === 'completed' ? '已闭环' : h.visitStatus === 'in_progress' ? '跟进中' : '' }}</text>
        </view>
      </view>

      <!-- 行动计划 -->
      <view class="card" v-if="actions.length">
        <text class="card-title">行动计划 ({{ actions.length }})</text>
        <view class="action-item" v-for="a in actions" :key="a.id">
          <view class="action-header">
            <text class="action-name">{{ a.actionName }}</text>
            <text class="tag small" :class="actionCls(a.taskStatus)">{{ actionLabel(a.taskStatus) }}</text>
          </view>
          <view class="action-row"><text class="al">目标值</text><text class="av">{{ a.targetValue || '--' }}</text></view>
          <view class="action-row"><text class="al">具体动作</text><text class="av">{{ a.specificAction || '--' }}</text></view>
          <view class="action-row"><text class="al">追踪时间</text><text class="av" :class="{ overdue: a.taskStatus === 'overdue' }">{{ a.trackingTime || '--' }}</text></view>
        </view>
      </view>
    </template>
    <EmptyState v-else-if="!loading" text="拜访单不存在" />

    <!-- 底部操作栏 -->
    <view class="bottom-bar" v-if="visit && visit.visitStatus === 'pending_confirm'">
      <button class="btn danger" @click="handleObject" :disabled="acting">提出异议</button>
      <button class="btn primary" @click="handleConfirm" :disabled="acting">确认拜访</button>
    </view>

    <!-- 异议弹窗 -->
    <view class="mask" v-if="showObjSheet" @click="showObjSheet = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">提出异议</text>
        <textarea class="sheet-input" v-model="objReason" placeholder="请填写异议说明（必填）" :maxlength="500" />
        <view class="sheet-btns">
          <button class="btn cancel" @click="showObjSheet = false">取消</button>
          <button class="btn primary" @click="submitObject" :loading="acting">提交异议</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchVisitDetail, confirmVisit, objectVisit, type SupervisorVisit, type SupervisorVisitAction } from '@/api/supervisor-visit'
import { useUserStore } from '@/store/user'
import Skeleton from '@/components/Skeleton.vue'
import EmptyState from '@/components/EmptyState.vue'

const userStore = useUserStore()
const loading = ref(true)
const acting = ref(false)
const visit = ref<SupervisorVisit | null>(null)
const actions = ref<SupervisorVisitAction[]>([])
const bizData = ref<any>(null)
const historyVisits = ref<SupervisorVisit[]>([])

const showObjSheet = ref(false)
const objReason = ref('')

let visitId = 0

const statusMap: any = { draft: ['草稿', 'draft'], pending_confirm: ['待确认', 'pending'], objection: ['异议待处理', 'obj'], in_progress: ['跟进中', 'progress'], completed: ['已完成', 'done'] }
const statusLabel = computed(() => visit.value ? (statusMap[visit.value.visitStatus] || ['--', ''])[0] : '--')
const statusCls = computed(() => visit.value ? (statusMap[visit.value.visitStatus] || ['', ''])[1] : '')
const confirmPersonLabel = computed(() => {
  const t = visit.value?.confirmPersonType
  return { store_manager: '店长', owner: '老板', both: '店长+老板' }[t || ''] || t || '--'
})
const actionCls = (s: string) => ({ pending: '', overdue: 'overdue', pending_review: 'review', returned: 'overdue', completed: 'done' })[s] || ''
const actionLabel = (s: string) => ({ pending: '待执行', overdue: '已逾期', pending_review: '待审核', returned: '已退回', completed: '已完成' })[s] || s

onLoad((q: any) => { visitId = Number(q?.id || 0); load() })

async function load() {
  loading.value = true
  try {
    const res: any = await fetchVisitDetail(visitId)
    if (res) {
      visit.value = res.visit || res
      actions.value = res.actions || []
      bizData.value = res.bizData?.bizData || res.bizData || null
      historyVisits.value = (res.historyVisits || []).filter((h: any) => h.id !== visitId)
    }
  } finally { loading.value = false }
}

async function handleConfirm() {
  if (acting.value) return
  const ok = await new Promise<boolean>(resolve =>
    uni.showModal({ title: '确认拜访', content: '确认本次拜访记录和行动计划？确认后将生成待处理任务。', success: r => resolve(r.confirm) })
  )
  if (!ok) return
  acting.value = true
  try {
    await confirmVisit(visitId)
    uni.showToast({ title: '已确认', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch { /* handled */ } finally { acting.value = false }
}

function fmtShort(d?: string) { return d ? d.substring(5) : '--' }

function handleObject() { objReason.value = ''; showObjSheet.value = true }

async function submitObject() {
  if (acting.value) return
  if (!objReason.value.trim()) { uni.showToast({ title: '请填写异议说明', icon: 'none' }); return }
  acting.value = true
  try {
    await objectVisit(visitId, objReason.value.trim())
    uni.showToast({ title: '已提交异议', icon: 'success' })
    showObjSheet.value = false
    load()
  } catch { /* handled */ } finally { acting.value = false }
}
</script>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$w:#E58A2D;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}
.card{background:$s;border-radius:20rpx;padding:28rpx;margin-bottom:24rpx;border:2rpx solid $b}
.card.warn{background:#FFF4F2;border-color:#FADBD8}
.card-title{font-size:30rpx;font-weight:700;color:$t1;margin-bottom:20rpx;display:block}
.status-row{display:flex;align-items:center;justify-content:space-between;margin-bottom:20rpx}
.visit-no{font-size:24rpx;color:$t3}
.tag{font-size:22rpx;padding:4rpx 16rpx;border-radius:12rpx;font-weight:600}
.tag.draft{background:#F0F0F0;color:$t2}
.tag.pending{background:#FFF3E0;color:$w}
.tag.obj{background:#FFF4F2;color:$d}
.tag.progress{background:#E3F0FF;color:#2F6FCF}
.tag.done{background:#E8F5E9;color:$p}
.tag.overdue{background:#FFF4F2;color:$d}
.tag.review{background:#FFF3E0;color:$w}
.tag.small{font-size:20rpx;padding:2rpx 12rpx}
.info-grid{display:grid;grid-template-columns:1fr 1fr;gap:12rpx}
.info-item{display:flex;flex-direction:column}
.info-label{font-size:24rpx;color:$t3;margin-bottom:4rpx}
.info-value{font-size:28rpx;color:$t1;font-weight:500}
.info-value-row{display:flex;align-items:center;flex-wrap:wrap;font-size:28rpx;color:$t1;font-weight:500}
.role-tag{font-size:20rpx;padding:2rpx 10rpx;border-radius:6rpx;font-weight:500;background:#E7F4EB;color:#2F8F57;margin-left:6rpx}
.role-tag.owner{background:#FFF3E0;color:#E58A2D}
.obj-text{font-size:26rpx;color:$d;line-height:40rpx;margin-top:8rpx}
.biz-grid{display:grid;grid-template-columns:1fr 1fr;gap:8rpx}
.biz-item{display:flex;justify-content:space-between;padding:14rpx 16rpx;background:$bg;border-radius:12rpx}
.biz-label{font-size:24rpx;color:$t2}
.biz-value{font-size:26rpx;font-weight:600;color:$t1}
.comm-block{margin-bottom:20rpx;padding-bottom:20rpx;border-bottom:2rpx solid $b}
.comm-block:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0}
.comm-label{font-size:24rpx;color:$t3;margin-bottom:8rpx;display:block}
.comm-text{font-size:26rpx;color:$t1;line-height:40rpx}
.action-item{padding:16rpx 0;border-bottom:2rpx solid $b}
.action-item:last-child{border-bottom:none;padding-bottom:0}
.action-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:8rpx}
.action-name{font-size:28rpx;font-weight:600;color:$t1}
.action-row{display:flex;padding:4rpx 0}
.al{width:140rpx;font-size:24rpx;color:$t3;flex-shrink:0}
.av{font-size:26rpx;color:$t1;flex:1}
.av.overdue{color:$d}
.history-item{display:flex;align-items:center;padding:14rpx 0;border-bottom:2rpx solid $b;gap:16rpx}
.history-item:last-child{border-bottom:none}
.history-date{font-size:26rpx;font-weight:600;color:$t1;width:90rpx;flex-shrink:0}
.history-summary{font-size:24rpx;color:$t2;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.history-state{font-size:22rpx;color:$t1;font-weight:600;flex-shrink:0}
.bottom-bar{position:fixed;left:0;right:0;bottom:0;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:linear-gradient(to top,#fff 60%,transparent);display:grid;grid-template-columns:1fr 1fr;gap:24rpx}
.btn{width:100%;height:96rpx;border-radius:16rpx;font-weight:600;font-size:30rpx;display:flex;align-items:center;justify-content:center;border:none}
.btn.primary{background:$p;color:#fff}
.btn.danger{border:2rpx solid $d;background:#fff;color:$d}
.btn.cancel{background:$bg;color:$t2;border:2rpx solid $b}
.mask{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.4);z-index:999;display:flex;align-items:flex-end}
.sheet{width:100%;background:#fff;border-radius:24rpx 24rpx 0 0;padding:32rpx;padding-bottom:calc(env(safe-area-inset-bottom) + 32rpx)}
.sheet-title{font-size:32rpx;font-weight:700;color:$t1;margin-bottom:20rpx;display:block}
.sheet-input{width:100%;height:200rpx;background:$bg;border-radius:12rpx;padding:20rpx;font-size:28rpx;box-sizing:border-box}
.sheet-btns{display:grid;grid-template-columns:1fr 1fr;gap:24rpx;margin-top:24rpx}
</style>
