<template>
  <div class="module-tabs">
    <div class="tabs-left">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="module-tab"
        :class="{ active: activeKey === tab.key }"
        @click="go(tab.path)"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="tabs-right">
      <a-input-search placeholder="Search..." allow-clear class="tabs-search" />
      <a-avatar size="small" class="tabs-avatar">默认</a-avatar>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const tabs = [
  { key: 'list', label: '支出明细', path: '/expense' },
  { key: 'dashboard', label: '支出统计', path: '/expense/dashboard' },
  { key: 'types', label: '支出类型', path: '/expense/types' },
]

const activeKey = computed(() => {
  if (route.path.startsWith('/expense/dashboard')) return 'dashboard'
  if (route.path.startsWith('/expense/types')) return 'types'
  return 'list'
})

function go(path: string) {
  if (route.path !== path) router.push(path)
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

.tabs-search {
  width: 240px;
}

.tabs-avatar {
  background: var(--primary, #0d7a3d);
  cursor: default;
}
</style>
