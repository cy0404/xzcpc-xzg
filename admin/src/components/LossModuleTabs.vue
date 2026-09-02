<template>
  <div class="module-tabs">
    <div class="tabs-left">
      <button
        type="button"
        class="module-tab"
        :class="{ active: activeKey === 'list' }"
        @click="go('/loss')"
      >
        报损列表
      </button>
      <button
        type="button"
        class="module-tab"
        :class="{ active: activeKey === 'dashboard' }"
        @click="go('/loss/dashboard')"
      >
        报损统计
      </button>
      <button
        type="button"
        class="module-tab"
        :class="{ active: activeKey === 'standard' }"
        @click="go('/loss/standard')"
      >
        验收标准
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const activeKey = computed(() => {
  if (route.path.startsWith('/loss/dashboard')) return 'dashboard'
  if (route.path.startsWith('/loss/standard')) return 'standard'
  return 'list'
})

function go(path: string) {
  // 仅同路径时跳过；不能按前缀判断，否则 /loss/dashboard 上点"报损列表"(/loss) 会被误判为已在该页
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
</style>
