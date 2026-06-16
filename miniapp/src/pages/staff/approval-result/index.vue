<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const action = ref('')
const name = ref('')
const storeName = ref('')
const role = ref('')

onLoad((query: any) => {
  action.value = query?.action || ''
  name.value = decodeURIComponent(query?.name || '')
  storeName.value = decodeURIComponent(query?.storeName || '')
  role.value = query?.role || ''
})

function goBack() {
  uni.navigateBack()
}
</script>

<template>
  <view class="page">
    <!-- 审批通过 -->
    <template v-if="action === 'approve'">
      <view class="hero approve">
        <view class="hero-icon">✅</view>
        <text class="hero-title">审批通过</text>
        <text class="hero-desc">{{ name }} 已加入 {{ storeName }}</text>
      </view>

      <view class="card">
        <view class="row"><text class="k">姓名</text><text class="v">{{ name }}</text></view>
        <view class="row"><text class="k">门店</text><text class="v">{{ storeName }}</text></view>
        <view class="row last"><text class="k">岗位</text><text class="v">{{ role }}</text></view>
      </view>

      <view class="tip">
        <text>系统已根据岗位自动开通对应功能。员工可使用手机号登录小程序。</text>
      </view>
    </template>

    <!-- 驳回 -->
    <template v-else>
      <view class="hero reject">
        <view class="hero-icon">📝</view>
        <text class="hero-title">已驳回</text>
        <text class="hero-desc">{{ name }} 的登记申请已退回修改</text>
      </view>

      <view class="card">
        <view class="row"><text class="k">姓名</text><text class="v">{{ name }}</text></view>
        <view class="row last"><text class="k">门店</text><text class="v">{{ storeName }}</text></view>
      </view>

      <view class="tip">
        <text>员工会收到驳回提醒，重新修改信息后可再次提交。</text>
      </view>
    </template>

    <view class="actions">
      <view class="btn" @click="goBack">返回</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;

.page{min-height:100vh;background:$bg;padding:60rpx 32rpx 80rpx;display:flex;flex-direction:column;align-items:center}

.hero{display:flex;flex-direction:column;align-items:center;text-align:center;margin-bottom:40rpx}
.hero-icon{font-size:96rpx;margin-bottom:24rpx}
.hero-title{font-size:40rpx;font-weight:700;color:$t1}
.hero-desc{font-size:28rpx;color:$t2;margin-top:12rpx}

.card{width:100%;background:$s;border-radius:20rpx;padding:28rpx;border:2rpx solid $b;margin-bottom:24rpx}
.row{display:flex;justify-content:space-between;padding:20rpx 0;border-bottom:2rpx solid #EEF1EF}.row.last{border:0}
.k{color:$t2;font-size:28rpx}.v{color:$t1;font-size:28rpx;font-weight:600}

.tip{padding:20rpx;border-radius:12rpx;background:#FAFBF9;color:$t2;font-size:26rpx;text-align:center;margin-bottom:40rpx;line-height:1.6}

.actions{width:100%}.btn{width:100%;height:88rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700}
</style>
