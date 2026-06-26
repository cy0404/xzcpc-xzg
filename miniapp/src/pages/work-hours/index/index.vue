<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import {
  fetchWorkHours,
  createWorkHours,
  updateWorkHours,
  deleteWorkHours,
  type WorkHoursRecord,
} from '@/api/workHours'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'

const userStore = useUserStore()
const loading = ref(true)
const saving = ref(false)
const records = ref<WorkHoursRecord[]>([])
const editId = ref('')
const yearMonth = ref(currentYearMonth())
const hours = ref('')

const canManage = computed(() => userStore.role === '老板' || userStore.role === '店长')
const isEdit = computed(() => !!editId.value)
const pickerValue = computed(() => `${yearMonth.value}-01`)

function currentYearMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function resetForm() {
  editId.value = ''
  yearMonth.value = currentYearMonth()
  hours.value = ''
}

onShow(async () => {
  if (!canManage.value) {
    uni.showToast({ title: '仅老板、店长可录入工时', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 600)
    return
  }
  await loadList()
})

async function loadList() {
  loading.value = true
  try {
    records.value = (await fetchWorkHours()) || []
  } finally {
    loading.value = false
  }
}

function onMonthChange(e: any) {
  const v = e.detail.value || ''
  yearMonth.value = v.length >= 7 ? v.slice(0, 7) : v
}

function startEdit(item: WorkHoursRecord) {
  editId.value = item.recordId
  yearMonth.value = item.recordTime
  hours.value = formatHours(item.hours)
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

function formatHours(n: number | string) {
  const v = Number(n)
  return isNaN(v) ? '' : v.toFixed(2)
}

function fmtDisplayHours(n: number | string) {
  const v = Number(n)
  return isNaN(v) ? '--' : v.toFixed(2)
}

function fmtTime(s: string) {
  if (!s) return '--'
  return s.replace('T', ' ').slice(0, 16)
}

async function submit() {
  if (saving.value) return
  const h = parseFloat(hours.value)
  if (isNaN(h) || h < 0) {
    uni.showToast({ title: '请输入有效工时', icon: 'none' })
    return
  }
  if (!isEdit.value && h <= 0) {
    uni.showToast({ title: '工时须大于 0', icon: 'none' })
    return
  }
  const rounded = Math.round(h * 100) / 100
  const payload = { recordTime: yearMonth.value, hours: rounded }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateWorkHours(editId.value, payload)
      uni.showToast({ title: '修改成功', icon: 'success' })
    } else {
      await createWorkHours(payload)
      uni.showToast({ title: '录入成功', icon: 'success' })
    }
    resetForm()
    await loadList()
  } finally {
    saving.value = false
  }
}

function confirmDelete() {
  if (!editId.value || saving.value) return
  uni.showModal({
    title: '确认删除',
    content: `确定删除 ${yearMonth.value} 的工时记录吗？`,
    success: async (res) => {
      if (!res.confirm) return
      saving.value = true
      try {
        await deleteWorkHours(editId.value)
        uni.showToast({ title: '已删除', icon: 'success' })
        resetForm()
        await loadList()
      } finally {
        saving.value = false
      }
    },
  })
}
</script>

<template>
  <view class="page">
    <view class="form-card">
      <text class="form-title">{{ isEdit ? '修改工时' : '录入工时' }}</text>
      <text class="form-sub">请录入所有员工本月总工时</text>

      <view class="form-row">
        <text class="fr-label">年月</text>
        <picker mode="date" fields="month" :value="pickerValue" @change="onMonthChange">
          <view class="fr-picker">
            <text>{{ yearMonth }}</text>
            <text class="fr-arrow">›</text>
          </view>
        </picker>
      </view>

      <view class="form-row">
        <text class="fr-label">工时（小时）</text>
        <input class="fr-input" type="digit" v-model="hours" placeholder="请输入工时" />
      </view>

      <view class="form-actions">
        <view v-if="isEdit" class="btn ghost" @click="resetForm">取消编辑</view>
        <view v-if="isEdit" class="btn danger" @click="confirmDelete">删除</view>
        <view class="btn primary" :class="{ full: !isEdit }" @click="submit">
          {{ saving ? '提交中...' : (isEdit ? '确认修改' : '确认录入') }}
        </view>
      </view>
    </view>

    <view class="section">
      <text class="sec-title">历史工时</text>
      <text class="sec-hint">点击任意记录即可修改或删除</text>
      <Skeleton v-if="loading" :rows="4" />
      <template v-else-if="records.length">
        <view
          v-for="item in records"
          :key="item.recordId"
          class="hist-card"
          :class="{ active: editId === item.recordId }"
          @click="startEdit(item)"
        >
          <view class="hc-top">
            <text class="hc-month">{{ item.recordTime }}</text>
            <text class="hc-hours">{{ fmtDisplayHours(item.hours) }} 小时</text>
          </view>
          <view class="hc-meta">
            <text>{{ item.employeeName || '--' }}</text>
            <text>{{ fmtTime(item.createdAt) }}</text>
          </view>
        </view>
      </template>
      <EmptyState v-else text="暂无工时记录" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 48rpx}
.form-card{padding:28rpx;border-radius:20rpx;background:$s;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}
.form-title{display:block;font-size:36rpx;font-weight:700;color:$t1}
.form-sub{display:block;margin-top:8rpx;font-size:26rpx;color:$t2;margin-bottom:28rpx}
.form-row{display:flex;align-items:center;justify-content:space-between;gap:24rpx;padding:24rpx 0;border-bottom:2rpx solid #EEF1EF}
.form-row:last-of-type{border-bottom:none}
.fr-label{font-size:28rpx;color:$t2;flex-shrink:0}
.fr-picker{flex:1;display:flex;align-items:center;justify-content:flex-end;gap:8rpx;font-size:30rpx;font-weight:600;color:$t1}
.fr-arrow{font-size:36rpx;color:$t3}
.fr-input{flex:1;text-align:right;font-size:32rpx;font-weight:600;color:$t1}
.form-actions{display:flex;gap:16rpx;margin-top:28rpx;flex-wrap:wrap}
.btn{height:88rpx;border-radius:16rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600;flex:1;min-width:0}
.btn.primary{background:$p;color:#fff}.btn.primary.full{flex:1 1 100%}
.btn.ghost{background:#FAFBF9;color:$t1;border:2rpx solid $b}
.btn.danger{background:#FFF4F2;color:$d;border:2rpx solid #F5B8B2}
.section{margin-top:40rpx}
.sec-title{display:block;font-size:34rpx;font-weight:700;color:$t1;margin-bottom:8rpx}
.sec-hint{display:block;font-size:24rpx;color:$t3;margin-bottom:20rpx}
.hist-card{padding:24rpx;border-radius:16rpx;background:$s;border:2rpx solid $b;margin-bottom:16rpx}
.hist-card.active{border-color:$p;background:$ps}
.hc-top{display:flex;align-items:center;justify-content:space-between;gap:16rpx}
.hc-month{font-size:32rpx;font-weight:700;color:$t1}
.hc-hours{font-size:30rpx;font-weight:700;color:$p}
.hc-meta{display:flex;justify-content:space-between;margin-top:12rpx;font-size:24rpx;color:$t3}
</style>
