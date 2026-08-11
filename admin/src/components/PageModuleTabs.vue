<template>
  <div class="module-tabs">
    <div class="tabs-left">
      <button type="button" class="module-tab" :class="{ active: activeKey === 'tasks' }" @click="go('/tasks')">
        盘点列表
      </button>
      <button type="button" class="module-tab" :class="{ active: activeKey === 'differences' }" @click="go('/tasks/differences')">
        盘点处理
      </button>
      <button type="button" class="module-tab" :class="{ active: activeKey === 'templates' }" @click="go('/templates')">
        盘点模板
      </button>
      <button type="button" class="module-tab" :class="{ active: activeKey === 'materials' }" @click="go('/materials')">
        物料管理
      </button>
    </div>
    <div class="tabs-right">
      <a-avatar size="small" class="tabs-avatar">默认</a-avatar>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const activeKey = computed(() =>
  route.path.includes('/differences') ? 'differences'
    : route.path.startsWith('/templates') ? 'templates'
    : route.path.startsWith('/materials') ? 'materials'
    : 'tasks',
)

function go(path: string) {
  if (route.path !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
.module-tabs {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0;
}

.tabs-left {
  display: flex;
  gap: 32px;
}

.tabs-right {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 8px;
}

.module-tab {
  background: none;
  border: none;
  padding: 0 0 12px;
  font-size: 15px;
  font-weight: 500;
  color: #6b7280;
  cursor: pointer;
  position: relative;
  margin-bottom: -1px;
}

.module-tab.active {
  color: var(--primary, #0d7a3d);
  font-weight: 600;
}

.module-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--primary, #0d7a3d);
  border-radius: 2px 2px 0 0;
}

.module-tab:hover:not(.active) {
  color: #374151;
}

.tabs-avatar {
  background: var(--primary, #0d7a3d);
  cursor: default;
}
</style>
