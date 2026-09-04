<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { useTaskStore } from '@/store/task'
import { request } from '@/utils/request'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'
import { topUpSubscribeOnce, ensureBackHome } from '@/utils/subscribe'

const userStore = useUserStore()
const taskStore = useTaskStore()
const loading = ref(true)
const entering = ref(false)
// 页面任意点击（首次）→ 订阅授权充值：任务列表是高频页面，正常查看即攒额度
const pageTopUpDone = ref(false)
function onPageTap() {
  if (pageTopUpDone.value) return
  pageTopUpDone.value = true
  topUpSubscribeOnce()
}

const currentTasks = computed(() => taskStore.currentTasks || [])
const taskCount = computed(() => taskStore.currentTasks?.length || 0)
const totalMaterials = computed(() => taskStore.currentTasks.reduce((s: number, t: any) => s + (t.totalMaterials || 0), 0))
const totalEntered = computed(() => taskStore.currentTasks.reduce((s: number, t: any) => s + (t.enteredMaterials || 0), 0))
const totalRemaining = computed(() => Math.max(totalMaterials.value - totalEntered.value, 0))
const totalProgress = computed(() => totalMaterials.value ? Math.round(totalEntered.value / totalMaterials.value * 100) : 0)

// 订阅消息落地页：冷启动直达时垫首页，让"返回"回首页而不是退出小程序
onLoad(() => { ensureBackHome() })

onShow(async () => {
  if (!userStore.token || !userStore.bound) return
  loading.value = true
  try { await taskStore.fetchTaskList() }
  finally { loading.value = false }
})

async function enterZone(task: any) {
  if (entering.value) return
  uni.navigateTo({
    url: `/pages/task/zone-entry/index?taskId=${task.taskId}&taskName=${encodeURIComponent(task.taskName)}&storeName=${encodeURIComponent(userStore.storeName)}`
  })
}

function goResult(task: any) { uni.navigateTo({ url: `/pages/task/result/index?taskId=${task.taskId}` }) }
function goHistoryTask(task: any) {
  if (task.status === 'overdue') {
    uni.showToast({ title: '任务已过期，无法查看', icon: 'none' })
    return
  }
  goResult(task)
}
function enterCurrentTask() {
  const first = currentTasks.value[0]
  if (first) enterZone(first)
}
function nowMonth() { const d = new Date(); return `${d.getFullYear()}年${d.getMonth()+1}月` }
function fmtDeadline(d: string) { if (!d) return '--'; return d.replace('T',' ').substring(0,16) }
function taskProgress(t: any) { if (!t?.totalMaterials) return 0; return Math.round((t.enteredMaterials / t.totalMaterials) * 100) }
/** 盘点周期展示：周盘显示 2026年第34周，月盘显示 2026-08 */
function taskPeriod(t: any) {
  if (t.taskType === 'weekly') {
    const m = /^(\d{4})-W(\d{2})$/.exec(t.taskWeek || '')
    return m ? `${m[1]}年第${Number(m[2])}周` : (t.taskWeek || t.taskMonth || '--')
  }
  return t.taskMonth || '--'
}
</script>

<template>
  <view class="page" @click="onPageTap">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else>
      <view class="overview-card">
        <view class="ov-top">
          <view><text class="ov-label">当前门店</text><text class="ov-store">{{ userStore.storeName }}</text></view>
          <text class="ov-month">📅 {{ nowMonth() }}</text>
        </view>
        <text class="ov-desc">{{ taskCount > 0 ? `${taskCount} 个盘点任务进行中，还有 ${totalRemaining} 项物料待录入。` : '暂无进行中的任务' }}</text>
        <view class="ov-stats">
          <view class="ov-stat"><text class="osl">任务数</text><text class="osv green">{{ taskCount }}</text></view>
          <view class="ov-stat"><text class="osl">已录入</text><text class="osv">{{ totalEntered }}/{{ totalMaterials }}</text></view>
          <view class="ov-stat"><text class="osl">待处理</text><text class="osv">{{ totalRemaining }} 项</text></view>
        </view>
        <view class="total-progress" v-if="taskCount > 0">
          <view class="tp-bar"><view class="tp-fill" :style="{width:totalProgress+'%'}"></view></view>
        </view>
      </view>

      <view class="section">
        <text class="section-title">当前任务</text>
        <view v-if="currentTasks.length" class="task-list">
          <view v-for="t in currentTasks" :key="t.taskId" class="task-card" @click="enterZone(t)">
            <view class="task-top">
              <view class="task-icon">📦</view>
              <view class="task-head">
                <view class="task-badges">
                  <text class="task-status" :class="t.status === 'not_started' ? 'ts-pending' : 'ts-active'">{{ t.status === 'not_started' ? '○ 未开始' : '● 进行中' }}</text>
                  <text class="task-type" :class="t.taskType === 'weekly' ? 'tt-weekly' : 'tt-monthly'">{{ t.taskType === 'weekly' ? '周盘' : '月盘' }}</text>
                </view>
                <text class="task-name">{{ t.taskName }}</text>
              </view>
              <text class="task-arrow">›</text>
            </view>
            <view class="task-grid">
              <view><text class="tgk">盘点周期</text><text class="tgv">{{ taskPeriod(t) }}</text></view>
              <view><text class="tgk">截止时间</text><text class="tgv danger">{{ fmtDeadline(t.deadline) }}</text></view>
              <view><text class="tgk">区域数量</text><text class="tgv">{{ t.zoneCount || 0 }}</text></view>
              <view><text class="tgk">物料数量</text><text class="tgv">{{ t.totalMaterials || 0 }}</text></view>
            </view>
            <view class="progress-wrap">
              <view class="pw-top"><text>盘点进度</text><text>{{ t.enteredMaterials||0 }}/{{ t.totalMaterials||0 }}</text></view>
              <view class="progress-bar"><view class="progress-fill" :style="{width:taskProgress(t)+'%'}"></view></view>
            </view>
            <view class="task-enter">进入盘点 ›</view>
          </view>
        </view>
        <EmptyState v-else text="暂无进行中的任务" />
      </view>

      <view v-if="taskStore.historyTasks.length" class="section">
        <text class="section-title">历史任务</text>
        <view v-for="t in taskStore.historyTasks" :key="t.taskId" class="hist-card" :class="{ 'hist-overdue': t.status === 'overdue' }" @click="goHistoryTask(t)">
          <view><text class="hc-name">{{ t.taskName }}</text><text class="hc-meta">{{ taskPeriod(t) }} · {{ t.totalMaterials }} SKU</text></view>
          <text class="hc-overdue" v-if="t.status === 'overdue'">已过期</text>
          <text class="hc-link" v-else>查看结果 ›</text>
        </view>
      </view>
    </template>

    <view class="fab-bar" v-if="taskCount > 0">
      <view class="fab-btn" @click="enterCurrentTask">进入当前盘点任务</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 180rpx}
.overview-card{background:$s;border-radius:24rpx;padding:28rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:32rpx}
.ov-top{display:flex;justify-content:space-between;gap:16rpx}.ov-label{font-size:24rpx;color:$t2}.ov-store{display:block;margin-top:8rpx;font-size:34rpx;font-weight:700;color:$t1}
.ov-month{padding:10rpx 20rpx;border-radius:999rpx;background:#FAFBF9;font-size:24rpx;color:$t2;white-space:nowrap;height:fit-content}
.ov-desc{display:block;margin-top:20rpx;font-size:26rpx;color:$t2;line-height:1.5}
.ov-stats{display:grid;grid-template-columns:repeat(3,1fr);margin-top:24rpx;padding:20rpx;background:#FAFBF9;border-radius:16rpx;text-align:center}
.osl{font-size:24rpx;color:$t3}.osv{display:block;margin-top:6rpx;font-size:40rpx;font-weight:800;color:$t1}.osv.green{color:$p}
.section{margin-top:40rpx}.section-title{display:block;font-size:34rpx;font-weight:700;color:$t1;margin-bottom:20rpx}
.task-card{background:$s;border-radius:24rpx;padding:28rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}
.task-top{display:flex;align-items:flex-start;gap:20rpx}.task-icon{width:80rpx;height:80rpx;border-radius:16rpx;background:$ps;display:flex;align-items:center;justify-content:center;font-size:40rpx;flex-shrink:0}
.task-head{flex:1}.task-badges{display:flex;align-items:center;gap:12rpx}.task-status{display:inline-flex;padding:6rpx 16rpx;border-radius:999rpx;background:$ps;color:$p;font-size:22rpx;font-weight:600}.task-status.ts-pending{background:#FAFBF9;color:$t3}.task-type{display:inline-flex;padding:6rpx 16rpx;border-radius:999rpx;font-size:22rpx;font-weight:600}.tt-monthly{background:#FAFBF9;color:$t3;border:2rpx solid $b}.tt-weekly{background:#FFF7E6;color:#D48806;border:2rpx solid #FFE7BA}.task-name{display:block;margin-top:12rpx;font-size:32rpx;font-weight:700;color:$t1}.task-arrow{font-size:48rpx;color:#8C9691}
.task-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx 32rpx;margin-top:24rpx;padding:20rpx;background:#FAFBF9;border-radius:16rpx}
.tgk{font-size:24rpx;color:$t3}.tgv{display:block;margin-top:6rpx;font-size:28rpx;font-weight:600;color:$t1}.tgv.danger{color:$d}
.progress-wrap{margin-top:32rpx}.pw-top{display:flex;justify-content:space-between;font-size:26rpx;color:$t2;margin-bottom:12rpx}
.progress-bar{height:14rpx;border-radius:999rpx;background:#EEF1EF;overflow:hidden}.progress-fill{height:100%;border-radius:999rpx;background:$p}
.task-list{display:flex;flex-direction:column;gap:20rpx}
.task-enter{text-align:right;margin-top:20rpx;font-size:26rpx;color:$p;font-weight:600}
.total-progress{margin-top:20rpx}.tp-bar{height:8rpx;border-radius:999rpx;background:#EEF1EF;overflow:hidden}.tp-fill{height:100%;border-radius:999rpx;background:$p}
.hist-card{display:flex;align-items:center;justify-content:space-between;padding:24rpx 28rpx;background:$s;border-radius:20rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:16rpx}
.hc-name{font-size:28rpx;font-weight:600;color:$t1}.hc-meta{display:block;margin-top:6rpx;font-size:24rpx;color:$t2}.hc-link{font-size:26rpx;color:$p;font-weight:600}
.hc-overdue{font-size:26rpx;color:$d;font-weight:600}
.hist-overdue{opacity:.55;background:#F5F5F5}
.fab-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:linear-gradient(to top,#fff,transparent)}
.fab-btn{width:100%;height:96rpx;border-radius:999rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700;box-shadow:0 8rpx 24rpx rgba(47,143,87,.3)}
</style>
