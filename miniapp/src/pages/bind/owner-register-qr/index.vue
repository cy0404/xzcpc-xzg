<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { BASE_URL } from '@/utils/constants'

const bindCode = ref('')
const loading = ref(true)

onLoad((query: any) => {
  bindCode.value = query?.bindCode || query?.scene || ''
})
const submitting = ref(false)
const showDrawer = ref(false)
const stores = ref<any[]>([])
const selectedIds = ref<string[]>([])
const form = ref({ name: '', phone: '', role: '老板' })
const agreed = ref(false)

const storeKeyword = ref('')

const filteredStores = computed(() => {
  const kw = storeKeyword.value.trim().toLowerCase()
  if (!kw) return stores.value
  return stores.value.filter(s => s.storeName.toLowerCase().includes(kw))
})

const selectedNames = computed(() =>
  stores.value.filter(s => selectedIds.value.includes(s.storeId)).map(s => s.storeName)
)

async function fetchStores() {
  try {
    const res = await uni.request({ url: BASE_URL + '/auth/owner/stores' })
    if (res.data?.code === 200) stores.value = res.data.data || []
  } finally { loading.value = false }
}

function toggleStore(storeId: string) {
  const idx = selectedIds.value.indexOf(storeId)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(storeId)
}

function buildSubmitResultContent(results: any[]) {
  const boundNames = results.filter((r: any) => r.status === '已绑定').map((r: any) => r.storeName)
  const failedItems = results.filter((r: any) => r.status === '失败')
  const unboundNames = results.filter((r: any) => r.status === '未关联').map((r: any) => r.storeName)
  const lines: string[] = []
  if (boundNames.length) {
    lines.push('已绑定：' + boundNames.join('、'))
  }
  if (failedItems.length) {
    lines.push('老板已被占用：' + failedItems.map((r: any) => r.storeName).join('、'))
    lines.push('请联系总部处理上述门店')
  }
  if (unboundNames.length) {
    lines.push('待总部核验：' + unboundNames.join('、'))
  }
  return { boundNames, failedItems, unboundNames, content: lines.join('\n') }
}

function showSubmitResultModal(results: any[]) {
  const { boundNames, failedItems, unboundNames, content } = buildSubmitResultContent(results)
  const allBound = results.every((r: any) => r.status === '已绑定')
  const allFailed = failedItems.length === results.length

  let title = '登记结果'
  if (allBound) title = '绑定成功'
  else if (allFailed) title = '无法绑定'

  const finalContent = allBound
    ? content + '\n请登录后开始使用。'
    : content

  uni.showModal({
    title,
    content: finalContent,
    showCancel: false,
    confirmText: '知道了',
    success() {
      if (allBound) {
        uni.reLaunch({ url: '/pages/login/index' })
        return
      }
      if (allFailed) return
      const params = 'state=pending' +
          '&bindCode=' + encodeURIComponent(bindCode.value) +
          '&name=' + encodeURIComponent(form.value.name.trim()) +
          '&phone=' + encodeURIComponent(form.value.phone.trim()) +
          '&storeCount=' + boundNames.length +
          '&storeNames=' + encodeURIComponent(boundNames.join(','))
      uni.redirectTo({ url: '/pages/bind/owner-register-status?' + params })
    },
  })
}

async function submit() {
  if (!form.value.name.trim()) { uni.showToast({ title: '请填写姓名', icon: 'none' }); return }
  if (!form.value.phone.trim() || !/^1[3-9]\d{9}$/.test(form.value.phone)) {
    uni.showToast({ title: '请填写正确的手机号', icon: 'none' }); return
  }
  if (selectedIds.value.length === 0) { uni.showToast({ title: '请选择门店', icon: 'none' }); return }
  if (!agreed.value || submitting.value) return
  submitting.value = true
  try {
    const loginRes: any = await uni.login()
    const res = await uni.request({
      url: BASE_URL + '/auth/owner/register',
      method: 'POST',
      data: { code: loginRes.code, name: form.value.name.trim(), phone: form.value.phone.trim(), role: form.value.role, bindCode: bindCode.value, storeIds: selectedIds.value },
    })
    if (res.data?.code === 200) {
      const results = res.data.data || []
      showSubmitResultModal(results)
    } else {
      uni.showToast({ title: res.data?.message || '提交失败', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '提交失败，请重试', icon: 'none' })
  } finally { submitting.value = false }
}

onMounted(fetchStores)
</script>

<template>
  <view class="page">
    <view class="hero">
      <text class="hero-title">管理员微信登记</text>
      <text class="hero-desc">请选择门店并填写信息，系统将自动匹配。</text>
    </view>

    <!-- 门店选择 -->
    <view class="card">
      <text class="card-title">选择门店（可多选）</text>
      <view class="picker-row" @click="showDrawer = true">
        <text v-if="selectedIds.length === 0" class="picker-placeholder">请选择门店</text>
        <view v-else class="picker-tags">
          <text v-for="name in selectedNames" :key="name" class="tag">{{ name }}</text>
        </view>
        <text class="picker-arrow">›</text>
      </view>
    </view>

    <!-- 个人信息 -->
    <view class="card">
      <text class="card-title">个人信息</text>
      <view class="field-row">
        <text class="label"><text class="req">*</text> 姓名</text>
        <input class="input" v-model="form.name" placeholder="请输入真实姓名" placeholder-style="text-align:center" />
      </view>
      <view class="field-row">
        <text class="label"><text class="req">*</text> 手机号</text>
        <input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="请输入手机号" placeholder-style="text-align:center" />
      </view>
      <view class="field-row">
        <text class="label"><text class="req">*</text> 角色</text>
        <view class="role-picker">
          <text class="role-opt" :class="{ on: form.role === '老板' }" @click="form.role = '老板'">老板</text>
          <text class="role-opt" :class="{ on: form.role === '店长' }" @click="form.role = '店长'">店长</text>
        </view>
      </view>
      <view class="hint">
        <text>💡 提示：若</text>
        <text class="hint-highlight">老板兼任店长</text>
        <text>，角色请选「老板」。</text>
      </view>
    </view>

    <view class="bottom">
      <view class="agreement" @click="agreed = !agreed">
        <view class="agree-check" :class="{ on: agreed }">
          <text v-if="agreed">✓</text>
        </view>
        <text class="agree-text">
          已阅读并同意
          <text class="link" @click.stop="uni.navigateTo({ url: '/pages/agreement/service' })">《用户服务协议》</text>
          和
          <text class="link" @click.stop="uni.navigateTo({ url: '/pages/agreement/privacy' })">《隐私政策》</text>
        </text>
      </view>
      <view class="btn" :class="{ disabled: !agreed }" @click="submit">{{ submitting ? '提交中...' : '提交' }}</view>
    </view>

    <!-- 门店选择抽屉 -->
    <view v-if="showDrawer" class="mask" @click="showDrawer = false; storeKeyword = ''">
      <view class="drawer" @click.stop>
        <view class="dh"></view>
        <view class="drawer-head">
          <text class="dht">选择门店</text>
          <text class="dhx" @click="showDrawer = false">✕</text>
        </view>
        <view class="drawer-search">
          <input class="search-input" v-model="storeKeyword" placeholder="搜索门店名称" placeholder-style="color:#98A19C" />
        </view>
        <scroll-view scroll-y class="drawer-body">
          <view
            v-for="s in filteredStores" :key="s.storeId"
            class="store-item"
            :class="{ on: selectedIds.includes(s.storeId) }"
            @click="toggleStore(s.storeId)"
          >
            <view class="check-box" :class="{ checked: selectedIds.includes(s.storeId) }">
              <text v-if="selectedIds.includes(s.storeId)">✓</text>
            </view>
            <text class="store-name">{{ s.storeName }}</text>
          </view>
        </scroll-view>
        <view class="drawer-foot">
          <text>已选 {{ selectedIds.length }} 家门店</text>
          <view class="confirm-btn" @click="showDrawer = false">确定</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;

.page{min-height:100vh;background:$bg;padding:40rpx 32rpx 200rpx}
.hero{text-align:center;margin-bottom:40rpx}
.hero-title{font-size:40rpx;font-weight:700;color:$t1;display:block}
.hero-desc{font-size:28rpx;color:$t2;margin-top:12rpx;display:block;line-height:1.5}

.card{background:$s;border-radius:20rpx;padding:28rpx;border:2rpx solid $b;margin-bottom:24rpx}
.card-title{font-size:28rpx;font-weight:600;color:$t1;margin-bottom:16rpx;display:block}

/* 选择器入口 */
.picker-row{display:flex;align-items:center;justify-content:space-between;min-height:72rpx;padding:16rpx 0}
.picker-placeholder{font-size:28rpx;color:$t3}
.picker-tags{flex:1;display:flex;flex-wrap:wrap;gap:8rpx}
.tag{padding:8rpx 18rpx;border-radius:999rpx;background:$ps;color:$p;font-size:24rpx;font-weight:500}
.picker-arrow{font-size:40rpx;color:$t3;margin-left:8rpx}

.field-row{display:flex;align-items:center;justify-content:space-between;gap:16rpx;padding:20rpx 0;border-bottom:2rpx solid #EEF1EF}
.field-row:last-child{border-bottom:0}
.label{font-size:28rpx;color:$t1;flex-shrink:0}
.req{color:#E05A47}
.input{flex:1;height:80rpx;background:#FAFBFC;border-radius:12rpx;padding:0 20rpx;font-size:28rpx;text-align:center;box-sizing:border-box}
.role-picker{display:flex;gap:16rpx}
.role-opt{padding:10rpx 28rpx;border-radius:999rpx;border:2rpx solid $b;font-size:26rpx;color:$t2}.role-opt.on{background:$ps;border-color:$p;color:$p;font-weight:600}
.hint{margin-top:16rpx;font-size:24rpx;color:$t3;line-height:1.8}
.hint-highlight{color:$p;font-weight:500}

.bottom{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:0 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:$s;border-top:2rpx solid #EEF1EF}
.agreement{display:flex;align-items:flex-start;gap:12rpx;padding:20rpx 0}
.agree-check{width:36rpx;height:36rpx;border-radius:6rpx;border:2rpx solid $b;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-top:2rpx}.agree-check.on{background:$p;border-color:$p;color:#fff;font-size:22rpx;font-weight:700}
.agree-text{font-size:24rpx;color:$t2;line-height:1.6}
.link{color:$p}
.btn{width:100%;height:96rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700}
.btn.disabled{opacity:.4}

/* 抽屉 */
.mask{position:fixed;inset:0;z-index:200;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.drawer{width:100%;max-height:80vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column}
.dh{width:80rpx;height:6rpx;border-radius:999rpx;background:$b;margin:16rpx auto}
.drawer-head{display:flex;justify-content:center;padding:8rpx 32rpx 16rpx;position:relative}
.dht{font-size:34rpx;font-weight:700;color:$t1}
.dhx{position:absolute;right:32rpx;font-size:40rpx;color:$t2}
.drawer-search{padding:0 32rpx 16rpx}
.search-input{height:80rpx;background:#FAFBFC;border-radius:12rpx;padding:0 20rpx;font-size:28rpx;width:100%;box-sizing:border-box}
.drawer-body{flex:1;max-height:50vh;padding:0 32rpx}
.store-item{display:flex;align-items:center;gap:16rpx;padding:24rpx 16rpx;margin-bottom:8rpx;border-radius:12rpx;background:#FAFBFC}
.store-item.on{background:$ps}
.check-box{width:40rpx;height:40rpx;border-radius:8rpx;border:2rpx solid $b;display:flex;align-items:center;justify-content:center;flex-shrink:0}.check-box.checked{background:$p;border-color:$p;color:#fff;font-size:24rpx;font-weight:700}
.store-name{font-size:28rpx;color:$t1}
.drawer-foot{display:flex;align-items:center;justify-content:space-between;padding:16rpx 32rpx calc(env(safe-area-inset-bottom) + 16rpx);border-top:2rpx solid #EEF1EF;font-size:26rpx;color:$t2}
.confirm-btn{padding:14rpx 40rpx;border-radius:999rpx;background:$p;color:#fff;font-size:28rpx;font-weight:600}
</style>
