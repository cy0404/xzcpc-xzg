<template>
  <a-config-provider :locale="zhCN">
    <a-layout class="app-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      class="app-sider"
      collapsible
      theme="light"
      width="220"
    >
      <div
        style="display: flex; align-items: center; gap: 12px; padding: 24px 20px 28px;"
      >
        <div
          style="width: 40px; height: 40px; flex-shrink: 0; border-radius: 10px; background: linear-gradient(145deg, #b8c9b0 0%, #7a9a72 55%, #5c7a59 100%); display: flex; align-items: center; justify-content: center; box-shadow: inset 0 1px 0 rgba(255,255,255,0.35);"
        >
          <span style="width: 14px; height: 14px; border-radius: 50%; background: rgba(255,255,255,0.55); box-shadow: 0 1px 3px rgba(0,0,0,0.12);" />
        </div>
        <div v-show="!collapsed" style="display: flex; flex-direction: column; line-height: 1.25;">
          <span style="font-size: 17px; font-weight: 700; color: #000; letter-spacing: 0.5px;">象掌柜总部</span>
          <span style="font-size: 12px; color: #000; margin-top: 2px;">管理后台</span>
        </div>
      </div>

      <a-menu
        v-model:selectedKeys="menuKeys"
        class="app-menu"
        mode="inline"
        theme="light"
        @click="handleMenuClick"
      >
        <a-menu-item v-if="canSeeTask" key="tasks">
          <ContainerOutlined />
          <span>盘点管理</span>
        </a-menu-item>
        <a-menu-item v-if="canSeeExpense" key="expense">
          <PayCircleOutlined />
          <span>支出管理</span>
        </a-menu-item>
        <a-menu-item v-if="canSeePeople" key="people">
          <UserOutlined />
          <span>人员管理</span>
        </a-menu-item>
        <a-menu-item v-if="canSeeSettings" key="settings">
          <SettingOutlined />
          <span>系统设置</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>

      <a-layout-content class="content-wrapper">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
  </a-config-provider>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import zhCN from 'ant-design-vue/locale/zh_CN'
import {
  ContainerOutlined,
  SettingOutlined,
  PayCircleOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { authRevision, hasAdminAccess, hasRole } from './utils/auth'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const canAccessAdmin = computed(() => {
  authRevision.value
  return hasAdminAccess()
})

/** 角色菜单可见性 */
const canSeeTask = computed(() => {
  authRevision.value
  return canAccessAdmin.value && (hasRole('headquarters_admin') || hasRole('operation_admin'))
})
const canSeeExpense = computed(() => {
  authRevision.value
  return canAccessAdmin.value && (hasRole('headquarters_admin') || hasRole('finance_admin'))
})
const canSeePeople = computed(() => {
  authRevision.value
  return canAccessAdmin.value && (hasRole('headquarters_admin') || hasRole('hr_admin'))
})
const canSeeSettings = computed(() => {
  authRevision.value
  return canAccessAdmin.value && hasRole('headquarters_admin')
})

const menuKeys = computed(() =>
  route.path.startsWith('/tasks') || route.path.startsWith('/templates') || route.path.startsWith('/materials')
    ? ['tasks']
    : route.path.startsWith('/expense')
      ? ['expense']
    : route.path.startsWith('/people')
      ? ['people']
    : route.path.startsWith('/settings')
      ? ['settings']
    : ['tasks'],
)

watch(
  () => route.path,
  () => {
    /* 路由变化时菜单高亮由 computed 处理 */
  },
)

function handleMenuClick({ key }: { key: string }) {
  if (key === 'tasks') router.push('/tasks')
  if (key === 'expense') router.push('/expense')
  if (key === 'people') router.push('/people')
  if (key === 'settings') router.push('/settings')
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.content-wrapper {
  margin: 0;
  padding: 24px 28px 32px;
  min-height: 100vh;
}
</style>
