<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else>
      <!-- 待确认拜访单 -->
      <section v-if="pendingVisits.length" class="section">
        <text class="sec-title">待确认拜访单</text>
        <view class="card-list">
          <view v-for="v in pendingVisits" :key="v.id" class="card" @click="goDetail(v.id)">
            <view class="card-head">
              <text class="card-title">{{ v.visitNo }}</text>
              <text class="tag pending">待确认</text>
            </view>
            <text class="card-sub">{{ v.storeName }} · {{ v.supervisorName }} · {{ v.visitDate }}</text>
            <text class="card-arrow">查看详情 →</text>
          </view>
        </view>
      </section>

      <!-- 待处理任务 -->
      <section v-if="pendingActions.length" class="section">
        <text class="sec-title">待处理拜访任务</text>
        <view class="card-list">
          <view v-for="a in pendingActions" :key="a.id" class="card" @click="goAction(a.id)">
            <view class="card-head">
              <text class="card-title">{{ a.actionName }}</text>
              <text class="tag" :class="statusCls(a.taskStatus)">{{ statusLabel(a.taskStatus) }}</text>
            </view>
            <text class="card-sub">{{ a.storeName || '' }} · 追踪时间 {{ a.trackingTime }}</text>
            <text class="card-arrow">查看详情 →</text>
          </view>
        </view>
      </section>

      <EmptyState v-if="!pendingVisits.length && !pendingActions.length" text="暂无待处理事项" />
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchPendingVisits, fetchMyActions, type SupervisorVisit, type SupervisorVisitAction } from '@/api/supervisor-visit'
import Skeleton from '@/components/Skeleton.vue'
import EmptyState from '@/components/EmptyState.vue'

const loading = ref(true)
const pendingVisits = ref<SupervisorVisit[]>([])
const pendingActions = ref<SupervisorVisitAction[]>([])

const statusCls = (s: string) =>
  ({ pending: '', overdue: 'overdue', returned: 'overdue', pending_review: 'review' } as any)[s] || ''
const statusLabel = (s: string) =>
  ({ pending: '待执行', overdue: '已逾期', returned: '已退回', pending_review: '审核中' } as any)[s] || s

async function load() {
  loading.value = true
  try {
    pendingVisits.value = (await fetchPendingVisits()) || []
    const res: any = await fetchMyActions(undefined, 1, 50)
    pendingActions.value = (res?.records || []).filter((a: SupervisorVisitAction) =>
      ['pending', 'overdue', 'returned'].includes(a.taskStatus))
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function goDetail(id: number) { uni.navigateTo({ url: `/pages/supervisor-visit/detail/index?id=${id}` }) }
function goAction(id: number) { uni.navigateTo({ url: `/pages/supervisor-visit/action-detail/index?id=${id}` }) }

onShow(() => load())
</script>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$w:#E58A2D;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}
.section{margin-top:32rpx}
.sec-title{font-size:34rpx;font-weight:700;color:$t1;margin-bottom:20rpx;display:block}
.card-list{display:flex;flex-direction:column;gap:16rpx}
.card{background:$s;border-radius:16rpx;padding:24rpx 28rpx;border:2rpx solid $b;position:relative}
.card-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:8rpx}
.card-title{font-size:28rpx;font-weight:600;color:$t1}
.card-sub{font-size:24rpx;color:$t2;display:block}
.card-arrow{font-size:24rpx;color:$p;position:absolute;right:28rpx;bottom:24rpx}
.tag{font-size:20rpx;padding:4rpx 12rpx;border-radius:8rpx;font-weight:600}
.tag.pending{background:#FFF3E0;color:$w}
.tag.overdue{background:#FFF4F2;color:$d}
.tag.review{background:#FFF3E0;color:$w}
</style>
