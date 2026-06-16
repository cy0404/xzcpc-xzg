<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { fetchExpenseDetail, deleteExpense, type ExpenseRecord } from '@/api/expense'
import Skeleton from '@/components/Skeleton.vue'

const expenseId = ref('')
const loading = ref(true)
const detail = ref<ExpenseRecord | null>(null)
const showDelete = ref(false)
const deleting = ref(false)

async function doDelete() {
  if (deleting.value) return
  deleting.value = true
  try { await deleteExpense(expenseId.value); uni.showToast({ title: '已删除', icon: 'success' }); setTimeout(() => uni.navigateBack(), 600) }
  finally { deleting.value = false; showDelete.value = false }
}

onLoad((q: any) => { expenseId.value = q?.expenseId || '' })
onShow(() => { if (expenseId.value) loadDetail() })

async function loadDetail() {
  loading.value = true
  try { detail.value = await fetchExpenseDetail(expenseId.value) as any }
  finally { loading.value = false }
}
function goEdit() { uni.navigateTo({ url: `/pages/expense/form/index?expenseId=${expenseId.value}` }) }
function fmtMoney(n: number) { return Number(n || 0).toFixed(2) }
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else-if="detail">
      <!-- 金额头部 -->
      <view class="hero-card">
        <text class="hero-badge">已登记</text>
        <text class="hero-amount">¥{{ fmtMoney(detail.amount) }}</text>
        <text class="hero-type">{{ detail.typeName }}</text>
      </view>

      <!-- 支出信息 -->
      <view class="card">
        <text class="card-title">支出信息</text>
        <view class="row"><text class="rk">类型</text><text class="rv">{{ detail.typeName }}</text></view>
        <view class="row"><text class="rk">日期</text><text class="rv">{{ detail.occurredDate }}</text></view>
        <view class="row"><text class="rk">说明</text><text class="rv">{{ detail.remark || '--' }}</text></view>
        <view class="row"><text class="rk">经手人</text><text class="rv">{{ detail.handlerName }}</text></view>
      </view>

      <!-- 凭证 -->
      <view v-if="detail.voucherUrl" class="card">
        <text class="card-title">凭证照片</text>
        <image :src="detail.voucherUrl" class="voucher-photo" mode="widthFix" @error="detail.voucherUrl = ''" />
      </view>

      <!-- 登记信息 -->
      <view class="card">
        <text class="card-title">登记信息</text>
        <view class="row"><text class="rk">登记人</text><text class="rv">{{ detail.handlerName }}</text></view>
        <view class="row last"><text class="rk">时间</text><text class="rv">{{ detail.createdAt || '--' }}</text></view>
      </view>

      <view class="tip">如需修改或删除，请联系门店负责人</view>
    </template>

    <view class="bottom-bar">
      <view class="bb-edit" @click="goEdit">编辑记录</view>
      <view class="bb-del" @click="showDelete = true">删除记录</view>
    </view>

    <!-- 删除确认 -->
    <view v-if="showDelete" class="mask" @click="showDelete = false">
      <view class="sheet" @click.stop>
        <view class="sh"></view>
        <text class="st">确认删除</text>
        <text class="sd">删除后该支出记录将无法恢复</text>
        <view class="sa">
          <view class="sab cancel" @click="showDelete = false">取消</view>
          <view class="sab danger" @click="doDelete">{{ deleting ? '删除中...' : '确认删除' }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 200rpx}
.hero-card{background:linear-gradient(135deg,$p,#247847);border-radius:24rpx;padding:40rpx 32rpx;color:#fff;text-align:center;margin-bottom:24rpx}
.hero-badge{display:inline-block;padding:6rpx 20rpx;border-radius:999rpx;background:rgba(255,255,255,.2);font-size:22rpx;margin-bottom:16rpx}
.hero-amount{display:block;font-size:72rpx;font-weight:800}.hero-type{display:block;margin-top:12rpx;font-size:28rpx;opacity:.8}
.card{background:$s;border-radius:20rpx;padding:28rpx;border:2rpx solid $b;margin-bottom:24rpx}
.card-title{display:block;font-size:26rpx;font-weight:600;color:$t3;margin-bottom:12rpx}
.row{display:flex;justify-content:space-between;padding:18rpx 0;border-bottom:2rpx solid #EEF1EF}.row.last{border:0}.rk{font-size:28rpx;color:$t2}.rv{font-size:28rpx;color:$t1}
.voucher-photo{width:100%;border-radius:12rpx;margin-top:8rpx}
.tip{padding:20rpx;text-align:center;font-size:24rpx;color:$t3}
.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:flex;gap:20rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:$s;border-top:2rpx solid #EEF1EF}
.bb-edit{flex:1;height:88rpx;border-radius:16rpx;border:2rpx solid $b;color:$p;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.bb-del{flex:1;height:88rpx;border-radius:16rpx;border:2rpx solid $d;color:$d;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;border-radius:32rpx 32rpx 0 0;background:$s;padding:32rpx 32rpx calc(env(safe-area-inset-bottom) + 32rpx);text-align:center}
.sh{width:80rpx;height:6rpx;border-radius:999rpx;background:$b;margin:0 auto 24rpx}
.st{display:block;font-size:34rpx;font-weight:700;color:$t1;margin-bottom:12rpx}.sd{display:block;font-size:28rpx;color:$t2;margin-bottom:32rpx}
.sa{display:flex;gap:20rpx}.sab{flex:1;height:88rpx;border-radius:99rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}.sab.cancel{background:#FAFBF9;color:$t1}.sab.danger{background:$d;color:#fff}
</style>
