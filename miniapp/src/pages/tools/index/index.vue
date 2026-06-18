<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const rawToolGroups = [
  {
    title: '门店作业',
    items: [
      {
        title: '盘点任务',
        desc: '查看总部下发的月盘任务',
        icon: '盘',
        url: '/pages/task/list/index',
      },
      {
        title: '支出记录',
        desc: '记录门店日常支出',
        icon: '支',
        url: '/pages/expense/list/index',
      },
    ],
  },
  {
    title: '门店管理',
    items: [
      {
        title: '员工管理',
        desc: '维护门店员工信息',
        icon: '员',
        url: '/pages/staff/list/index',
        permission: 'staff:manage',
      },
    ],
  },
]

const toolGroups = computed(() => rawToolGroups
  .map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.permission || userStore.permissions.includes(item.permission)),
  }))
  .filter((group) => group.items.length > 0))

onShow(() => {
  if (!userStore.token) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (!userStore.bound) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})

function goTool(item: any) {
  if (item.type === 'switchTab') {
    uni.switchTab({ url: item.url })
    return
  }
  uni.navigateTo({ url: item.url })
}
</script>

<template>
  <view class="tools-page">
    <view class="page-header">
      <text class="page-title">工具</text>
      <text class="page-sub">常用业务入口集中管理</text>
    </view>

    <view v-for="group in toolGroups" :key="group.title" class="tool-section">
      <text class="group-title">{{ group.title }}</text>
      <view class="tool-list">
        <view v-for="item in group.items" :key="item.title" class="tool-card" @click="goTool(item)">
          <view class="tool-icon">{{ item.icon }}</view>
          <view class="tool-content">
            <text class="tool-title">{{ item.title }}</text>
            <text class="tool-desc">{{ item.desc }}</text>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.tools-page {
  min-height: 100vh;
  padding: 36rpx 24rpx 48rpx;
  background: #F4F5F7;
}

.page-header {
  padding: 8rpx 8rpx 24rpx;
}

.page-title {
  display: block;
  font-size: 44rpx;
  font-weight: 800;
  color: #1A1A1A;
}

.page-sub {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #8C8C8C;
}

.tool-section {
  margin-top: 28rpx;
}

.group-title {
  display: block;
  margin: 0 8rpx 16rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #4A4A4A;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.tool-card {
  display: flex;
  align-items: center;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.tool-icon {
  width: 76rpx;
  height: 76rpx;
  border-radius: 22rpx;
  background: #E8F5EE;
  color: #00734A;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 700;
}

.tool-content {
  flex: 1;
  margin-left: 22rpx;
}

.tool-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1A1A1A;
}

.tool-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8C8C8C;
}

.arrow {
  color: #B0B0B0;
  font-size: 44rpx;
}
</style>
