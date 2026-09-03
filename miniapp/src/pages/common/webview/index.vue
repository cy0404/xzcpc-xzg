<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { topUpSubscribeOnce } from '@/utils/subscribe'

const src = ref('')

onLoad((q: any) => {
  src.value = q?.url ? decodeURIComponent(q.url) : ''
  // 进入 H5 前补一次订阅授权（H5 内无法调 requestSubscribeMessage，智能订货确认/付款等动作的授权点放容器页）
  topUpSubscribeOnce()
  // 可选动态标题（智能订货等 H5 复用本页时传入；不传则保持 pages.json 默认标题）
  if (q?.title) {
    uni.setNavigationBarTitle({ title: decodeURIComponent(q.title) })
  }
})

// H5 提交/删除通知：支出登记页提交成功、详情页删除成功 → 提示并返回
// H5 登录过期：收到 relogin → 清 token 并回登录页（web-view 内 H5 无法直接 reLaunch，统一由容器执行）
function onMessage(e: any) {
  const list: any[] = e?.detail?.data || []
  const relogin = list.find((d: any) => d?.type === 'relogin')
  if (relogin) {
    uni.removeStorageSync('token')
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  const msg = list.find((d: any) => d?.type === 'expense-submitted')
  if (msg) {
    const tip = msg.deleted ? '已删除' : (msg.isEdit ? '修改成功' : '登记成功')
    uni.showToast({ title: tip, icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  }
}
</script>

<template>
  <web-view v-if="src" :src="src" @message="onMessage" />
</template>
