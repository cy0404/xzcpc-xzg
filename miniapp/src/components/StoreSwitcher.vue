<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/store/user'
import { fetchMyStores, switchStore } from '@/api/auth'

const userStore = useUserStore()

const visible = ref(false)
const stores = ref<any[]>([])
const switching = ref(false)

const emit = defineEmits<{
  (e: 'switched'): void
}>()

async function open() {
  try {
    stores.value = (await fetchMyStores()) || []
  } catch {
    stores.value = []
  }
  visible.value = true
}

function close() {
  visible.value = false
}

async function handleSelect(store: any) {
  if (switching.value) return
  switching.value = true
  try {
    const data: any = await switchStore(store.storeId)
    if (data) {
      // 更新 token（switchStore 返回了新 token，必须替换）
      if (data.token) {
        uni.setStorageSync('token', data.token)
        userStore.token = data.token
      }
      userStore.storeId = data.storeId || ''
      userStore.storeName = data.storeName || ''
      userStore.role = data.role || ''
      userStore.permissions = Array.isArray(data.permissions) ? data.permissions : []
      userStore.storeCount = data.storeCount || 1
      // 持久化到本地缓存
      uni.setStorageSync('userInfo', {
        storeId: userStore.storeId,
        storeName: userStore.storeName,
        employeeId: userStore.employeeId,
        employeeName: userStore.employeeName,
        role: userStore.role,
        permissions: userStore.permissions,
        storeCount: userStore.storeCount,
        bound: userStore.bound,
      })
    }
    visible.value = false
    emit('switched')
  } catch {
    // handled by request interceptor
  } finally {
    switching.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <view v-if="visible" class="mask" @click="close">
    <view class="sheet" @click.stop>
      <view class="sh"></view>
      <view class="sheet-head">
        <text class="st">切换门店</text>
        <text class="sx" @click="close">✕</text>
      </view>
      <view class="store-list">
        <view
          v-for="store in stores"
          :key="store.storeId"
          class="store-item"
          :class="{ active: store.storeId === userStore.storeId }"
          @click="handleSelect(store)"
        >
          <view class="store-info">
            <text class="store-name">{{ store.storeName }}</text>
            <text v-if="store.employeeName" class="store-role">{{ store.role || '--' }}</text>
          </view>
          <text v-if="store.storeId === userStore.storeId" class="check">✓</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg: #F7F8F6;
$s: #fff;
$p: #2F8F57;
$t1: #1F2421;
$t2: #66706A;
$t3: #98A19C;
$b: #E8ECE9;

.mask {
  position: fixed; inset: 0; z-index: 200;
  display: flex; align-items: flex-end;
  background: rgba(31, 36, 33, .4);
}
.sheet {
  width: 100%; max-height: 70vh;
  border-radius: 32rpx 32rpx 0 0; background: $s;
  display: flex; flex-direction: column;
}
.sh {
  width: 96rpx; height: 6rpx; border-radius: 999rpx;
  background: $b; margin: 20rpx auto; flex-shrink: 0;
}
.sheet-head {
  display: flex; justify-content: center;
  padding: 8rpx 32rpx 24rpx; position: relative; flex-shrink: 0;
}
.st { font-size: 36rpx; font-weight: 700; color: $t1; }
.sx { position: absolute; right: 32rpx; font-size: 40rpx; color: $t2; padding: 8rpx; }
.store-list {
  padding: 0 32rpx calc(env(safe-area-inset-bottom) + 32rpx);
  display: flex; flex-direction: column; gap: 12rpx;
  max-height: 50vh; overflow-y: auto;
}
.store-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 28rpx 24rpx; background: #FAFBFC; border-radius: 16rpx;
  border: 2rpx solid transparent;
}
.store-item.active {
  background: #F1F8F3; border-color: $p;
}
.store-info { flex: 1; min-width: 0; }
.store-name { font-size: 30rpx; font-weight: 600; color: $t1; }
.store-role { display: block; margin-top: 6rpx; font-size: 24rpx; color: $t3; }
.check { font-size: 32rpx; color: $p; font-weight: 700; }
</style>
