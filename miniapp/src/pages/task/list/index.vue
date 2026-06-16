<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { useTaskStore } from '@/store/task'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'

const userStore = useUserStore()
const taskStore = useTaskStore()
const loading = ref(true)

const firstTask = computed(() => taskStore.currentTasks[0])
const taskProgress = computed(() => {
  const t = firstTask.value
  if (!t?.totalMaterials) return 0
  return Math.round(((t.enteredMaterials || 0) / t.totalMaterials) * 100)
})
const taskRemaining = computed(() => {
  const t = firstTask.value
  if (!t?.totalMaterials) return 0
  return Math.max((t.totalMaterials || 0) - (t.enteredMaterials || 0), 0)
})

onShow(async () => {
  if (!userStore.token || !userStore.bound) return
  loading.value = true
  try { await taskStore.fetchTaskList() }
  finally { loading.value = false }
})

function goDetail(task: any) { uni.navigateTo({ url: `/pages/task/detail/index?taskId=${task.taskId}` }) }
function goResult(task: any) { uni.navigateTo({ url: `/pages/task/result/index?taskId=${task.taskId}` }) }
function goContinue() { if (firstTask.value) goDetail(firstTask.value) }
function nowMonth() { const d = new Date(); return `${d.getFullYear()}年${d.getMonth()+1}月` }
function fmtDeadline(d: string) { if (!d) return '--'; return d.replace('T',' ').substring(0,16) }
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else>
      <view class="overview-card">
        <view class="ov-top">
          <view><text class="ov-label">当前门店</text><text class="ov-store">{{ userStore.storeName }}</text></view>
          <text class="ov-month">📅 {{ nowMonth() }}</text>
        </view>
        <text class="ov-desc">{{ firstTask ? `本月盘点任务正在进行中，还有 ${taskRemaining} 项物料待录入。` : '暂无进行中的任务' }}</text>
        <view class="ov-stats">
          <view class="ov-stat"><text class="osl">盘点进度</text><text class="osv green">{{ firstTask ? `${firstTask.enteredMaterials||0}/${firstTask.totalMaterials||0}` : '--' }}</text></view>
          <view class="ov-stat"><text class="osl">区域数量</text><text class="osv">{{ firstTask?.zoneCount||0 }} 区</text></view>
          <view class="ov-stat"><text class="osl">待处理</text><text class="osv">{{ taskRemaining }} 项</text></view>
        </view>
      </view>

      <view class="section">
        <text class="section-title">当前任务</text>
        <view v-if="firstTask" class="task-card" @click="goDetail(firstTask)">
          <view class="task-top">
            <view class="task-icon">📦</view>
            <view class="task-head">
              <text class="task-status">● 进行中</text>
              <text class="task-name">{{ firstTask.taskName }}</text>
            </view>
            <text class="task-arrow">›</text>
          </view>
          <view class="task-grid">
            <view><text class="tgk">盘点周期</text><text class="tgv">{{ firstTask.taskMonth }}</text></view>
            <view><text class="tgk">截止时间</text><text class="tgv danger">{{ fmtDeadline(firstTask.deadline) }}</text></view>
            <view><text class="tgk">区域数量</text><text class="tgv">{{ firstTask.zoneCount }}</text></view>
            <view><text class="tgk">物料数量</text><text class="tgv">{{ firstTask.totalMaterials }}</text></view>
          </view>
          <view class="progress-wrap">
            <view class="pw-top"><text>盘点进度</text><text>{{ firstTask.enteredMaterials||0 }}/{{ firstTask.totalMaterials||0 }}</text></view>
            <view class="progress-bar"><view class="progress-fill" :style="{width:taskProgress+'%'}"></view></view>
          </view>
        </view>
        <EmptyState v-else text="暂无进行中的任务" />
      </view>

      <view v-if="taskStore.historyTasks.length" class="section">
        <text class="section-title">历史任务</text>
        <view v-for="t in taskStore.historyTasks" :key="t.taskId" class="hist-card" @click="goResult(t)">
          <view><text class="hc-name">{{ t.taskName }}</text><text class="hc-meta">{{ t.taskMonth }} · {{ t.totalMaterials }} SKU</text></view>
          <text class="hc-link">查看结果 ›</text>
        </view>
      </view>
    </template>

    <view v-if="firstTask" class="fab-bar"><view class="fab-btn" @click="goContinue">继续盘点</view></view>
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
.task-head{flex:1}.task-status{display:inline-flex;padding:6rpx 16rpx;border-radius:999rpx;background:$ps;color:$p;font-size:22rpx;font-weight:600}.task-name{display:block;margin-top:12rpx;font-size:32rpx;font-weight:700;color:$t1}.task-arrow{font-size:48rpx;color:#8C9691}
.task-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx 32rpx;margin-top:24rpx;padding:20rpx;background:#FAFBF9;border-radius:16rpx}
.tgk{font-size:24rpx;color:$t3}.tgv{display:block;margin-top:6rpx;font-size:28rpx;font-weight:600;color:$t1}.tgv.danger{color:$d}
.progress-wrap{margin-top:32rpx}.pw-top{display:flex;justify-content:space-between;font-size:26rpx;color:$t2;margin-bottom:12rpx}
.progress-bar{height:14rpx;border-radius:999rpx;background:#EEF1EF;overflow:hidden}.progress-fill{height:100%;border-radius:999rpx;background:$p}
.hist-card{display:flex;align-items:center;justify-content:space-between;padding:24rpx 28rpx;background:$s;border-radius:20rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:16rpx}
.hc-name{font-size:28rpx;font-weight:600;color:$t1}.hc-meta{display:block;margin-top:6rpx;font-size:24rpx;color:$t2}.hc-link{font-size:26rpx;color:$p;font-weight:600}
.fab-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);background:linear-gradient(to top,#fff,transparent)}
.fab-btn{width:100%;height:96rpx;border-radius:999rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700;box-shadow:0 8rpx 24rpx rgba(47,143,87,.3)}
</style>
