<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { checkApplicationStatus } from '@/api/staff'

const applicationId = ref('')
const name = ref('')
const storeName = ref('')
const now = ref('')
const status = ref('pending')
const rejectReason = ref('')
const refreshing = ref(false)

onLoad((query: any) => {
  applicationId.value = query?.applicationId || ''
  name.value = decodeURIComponent(query?.name || '')
  storeName.value = decodeURIComponent(query?.storeName || '')
  now.value = new Date().toLocaleString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  // 进入页面时自动拉取最新审批状态，避免退出重进后看到过期的"待审批"假状态
  refreshStatus()
})

async function refreshStatus() {
  if (!applicationId.value || refreshing.value) return
  refreshing.value = true
  try {
    // 获取 wxCode 用于身份校验
    let wxCode = ''
    try {
      const loginRes: any = await uni.login()
      wxCode = loginRes.code || ''
    } catch { /* ignore */ }
    const data: any = await checkApplicationStatus(applicationId.value, wxCode)
    if (data?.found) {
      status.value = data.status || 'pending'
      rejectReason.value = data.rejectReason || ''
    }
  } catch {
    // 静默失败
  } finally {
    refreshing.value = false
  }
}

function goLogin() {
  uni.reLaunch({ url: '/pages/login/index' })
}

function goReapply() {
  uni.redirectTo({
    url: `/pages/staff/register/index?applicationId=${applicationId.value}&storeId=&storeName=${encodeURIComponent(storeName.value)}`,
  })
}
</script>

<template>
  <view class="page">
    <!-- ===== 待审批 ===== -->
    <template v-if="status === 'pending'">
      <view class="success">
        <view class="icon">⏳</view>
        <text class="st">登记申请已提交</text>
        <text class="sd">门店负责人审批通过后，你就可以使用象掌柜中的相关工具。</text>
      </view>

      <view class="card">
        <view class="r"><text class="k">门店</text><text class="v">{{ storeName }}</text></view>
        <view class="r"><text class="k">姓名</text><text class="v">{{ name }}</text></view>
        <view class="r"><text class="k">提交时间</text><text class="v">{{ now }}</text></view>
        <view class="r last"><text class="k">当前状态</text><text class="v tag-wait">等待审批</text></view>
      </view>

      <view class="steps">
        <view class="step done">
          <view class="dot">✓</view>
          <view><text class="sl">填写员工信息</text><text class="ss">已完成</text></view>
        </view>
        <view class="step active">
          <view class="dot dot-active"></view>
          <view><text class="sl on">门店负责人审批</text><text class="ss">进行中</text></view>
        </view>
        <view class="step">
          <view class="dot dot-pending"></view>
          <view><text class="sl">自动匹配岗位功能</text><text class="ss">等待审批通过</text></view>
        </view>
      </view>
    </template>

    <!-- ===== 审批通过 ===== -->
    <template v-else-if="status === 'approved'">
      <view class="success">
        <view class="icon">✅</view>
        <text class="st">审批已通过！</text>
        <text class="sd">你现在可以登录并使用象掌柜了。</text>
      </view>

      <view class="card">
        <view class="r"><text class="k">门店</text><text class="v">{{ storeName }}</text></view>
        <view class="r"><text class="k">姓名</text><text class="v">{{ name }}</text></view>
        <view class="r last"><text class="k">状态</text><text class="v tag-done">已通过</text></view>
      </view>

      <view class="steps">
        <view class="step done"><view class="dot">✓</view><view><text class="sl">填写员工信息</text><text class="ss">已完成</text></view></view>
        <view class="step done"><view class="dot">✓</view><view><text class="sl">门店负责人审批</text><text class="ss">已通过</text></view></view>
        <view class="step done"><view class="dot">✓</view><view><text class="sl">岗位功能已开通</text><text class="ss">已完成</text></view></view>
      </view>
    </template>

    <!-- ===== 审批拒绝 ===== -->
    <template v-else-if="status === 'rejected'">
      <view class="success">
        <view class="icon">❌</view>
        <text class="st">审核未通过</text>
        <text class="sd">如需了解原因，请联系门店负责人。</text>
      </view>

      <view class="card" v-if="rejectReason">
        <view class="r last"><text class="k">未通过原因</text><text class="v tag-deny">{{ rejectReason }}</text></view>
      </view>

      <view class="card" v-else>
        <view class="r last"><text class="k">状态</text><text class="v tag-deny">未通过</text></view>
      </view>
    </template>

    <!-- 手动刷新（仅 pending 时显示） -->
    <view v-if="status === 'pending'" class="polling-hint">
      <text class="polling-text">审批通过后，登录时会自动匹配岗位</text>
      <view class="btn-refresh" :class="{ loading: refreshing }" @click="refreshStatus">
        <text>{{ refreshing ? '刷新中...' : '刷新状态' }}</text>
      </view>
    </view>

    <view class="actions">
      <view v-if="status === 'approved'" class="btn btn-primary" @click="goLogin">去登录</view>
      <view v-else-if="status === 'rejected'" class="btn btn-primary" @click="goReapply">重新申请</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$w:#E58A2D;$d:#E05A47;
.page{min-height:100vh;background:$bg;padding:60rpx 32rpx 80rpx;display:flex;flex-direction:column;align-items:center}
.success{display:flex;flex-direction:column;align-items:center;text-align:center;margin-bottom:40rpx}.icon{font-size:80rpx;margin-bottom:24rpx}.st{font-size:36rpx;font-weight:700;color:$t1}.sd{font-size:26rpx;color:$t2;margin-top:12rpx;max-width:400rpx}
.card{width:100%;background:$s;border-radius:20rpx;padding:28rpx;border:2rpx solid $b;margin-bottom:32rpx}
.r{display:flex;justify-content:space-between;padding:20rpx 0;border-bottom:2rpx solid #EEF1EF}.r.last{border:0}.k{color:$t2;font-size:28rpx}.v{color:$t1;font-size:28rpx}.tag-wait{color:$w;font-weight:600}.tag-done{color:$p;font-weight:600}.tag-deny{color:$d;font-weight:600}

.steps{width:100%;background:$s;border-radius:20rpx;padding:32rpx 28rpx;border:2rpx solid $b;margin-bottom:32rpx;display:flex;flex-direction:column;gap:28rpx}
.step{display:flex;align-items:flex-start;gap:20rpx}.dot{width:48rpx;height:48rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-top:4rpx}.step.done .dot{background:$p;color:#fff;font-size:24rpx}.dot-active{background:$s;border:4rpx solid $p;position:relative}.dot-active::after{content:'';position:absolute;width:16rpx;height:16rpx;border-radius:50%;background:$p}.dot-pending{background:$s;border:4rpx solid $b}
.sl{display:block;font-size:28rpx;font-weight:600;color:$t1}.sl.on{color:$p}.ss{display:block;font-size:24rpx;color:$t2;margin-top:4rpx}

.polling-hint{padding:16rpx 0;text-align:center;margin-bottom:32rpx}.polling-text{font-size:24rpx;color:$t3;display:block;margin-bottom:20rpx}
.btn-refresh{display:inline-flex;align-items:center;justify-content:center;padding:18rpx 48rpx;border-radius:999rpx;border:2rpx solid $p;color:$p;font-size:28rpx;font-weight:500}.btn-refresh.loading{opacity:.6}

.actions{width:100%}.btn{width:100%;height:88rpx;border-radius:999rpx;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700}.btn-primary{background:$p;color:#fff}.btn-outline{background:$s;border:2rpx solid $b;color:$t1}
</style>
