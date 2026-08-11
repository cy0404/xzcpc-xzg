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
      <div style="display: flex; align-items: center; gap: 12px; padding: 24px 20px 28px;">
        <div style="width: 40px; height: 40px; flex-shrink: 0; border-radius: 10px; background: linear-gradient(145deg, #b8c9b0 0%, #7a9a72 55%, #5c7a59 100%); display: flex; align-items: center; justify-content: center; box-shadow: inset 0 1px 0 rgba(255,255,255,0.35);">
          <span style="width: 14px; height: 14px; border-radius: 50%; background: rgba(255,255,255,0.55); box-shadow: 0 1px 3px rgba(0,0,0,0.12);" />
        </div>
        <div v-show="!collapsed" style="display: flex; flex-direction: column; line-height: 1.25;">
          <span style="font-size: 17px; font-weight: 700; color: #000; letter-spacing: 0.5px;">象掌柜总部</span>
          <span style="font-size: 12px; color: #000; margin-top: 2px;">运营管理后台</span>
        </div>
      </div>

      <div class="nav-menu">
        <!-- 门店运营 -->
        <div class="nav-group">
          <p class="nav-group-title" v-show="!collapsed">门店运营</p>
          <div class="nav-group-items">
            <div class="nav-link" :class="{ active: menuKeyMap.tasks }" v-if="canSeeTask" @click="go('/tasks')">
              <ContainerOutlined /><span>盘点管理</span>
            </div>
            <div class="nav-link" :class="{ active: menuKeyMap.issue }" v-if="canSeeTask" @click="go('/issue')">
              <QuestionCircleOutlined /><span>问题处理</span>
            </div>
            <div class="nav-link" :class="{ active: menuKeyMap.loss }" v-if="canSeeTask" @click="go('/loss')">
              <ExclamationCircleOutlined /><span>报损管理</span>
            </div>
          </div>
        </div>

        <!-- 协同流转 -->
        <div class="nav-group" v-if="canSeeTask">
          <p class="nav-group-title" v-show="!collapsed">协同流转</p>
          <div class="nav-group-items">
            <!-- 物流信息暂隐藏 -->
            <!-- <div class="nav-link" :class="{ active: menuKeyMap.logistics }" @click="go('/placeholder?title=物流信息')">
              <SendOutlined /><span>物流信息</span>
            </div> -->
            <div class="nav-link" :class="{ active: menuKeyMap.transfer }" @click="go('/transfer')">
              <SwapOutlined /><span>调货台账</span>
            </div>
          </div>
        </div>

        <!-- 经营支撑 -->
        <div class="nav-group">
          <p class="nav-group-title" v-show="!collapsed">经营支撑</p>
          <div class="nav-group-items">
            <div class="nav-link" :class="{ active: menuKeyMap.supervisor }" v-if="canSeeTask" @click="go('/supervisor')">
              <EnvironmentOutlined /><span>督导拜访</span>
            </div>
            <div class="nav-link" :class="{ active: menuKeyMap.expense }" v-if="canSeeExpense" @click="go('/expense')">
              <PayCircleOutlined /><span>支出管理</span>
            </div>
            <div class="nav-link" :class="{ active: menuKeyMap.people }" v-if="canSeePeople" @click="go('/people')">
              <UserOutlined /><span>人员管理</span>
            </div>
          </div>
        </div>

        <!-- 系统 -->
        <div class="nav-group" v-if="canSeeSettings">
          <p class="nav-group-title" v-show="!collapsed">系统</p>
          <div class="nav-group-items">
            <div class="nav-link" :class="{ active: menuKeyMap.settings }" @click="go('/settings')">
              <SettingOutlined /><span>系统设置</span>
            </div>
            <div class="nav-link" :class="{ active: menuKeyMap.logs }" v-if="canSeeLogs" @click="go('/logs/operation')">
              <FileTextOutlined /><span>日志管理</span>
            </div>
          </div>
        </div>
      </div>
    </a-layout-sider>

    <a-layout :style="{ marginLeft: siderWidth }">
      <a-layout-content class="content-wrapper">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
  </a-config-provider>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import zhCN from 'ant-design-vue/locale/zh_CN'
import {
  ContainerOutlined, ExclamationCircleOutlined, QuestionCircleOutlined,
  SendOutlined, SwapOutlined, EnvironmentOutlined,
  SettingOutlined, PayCircleOutlined, UserOutlined, FileTextOutlined,
} from '@ant-design/icons-vue'
import { authRevision, hasAdminAccess, hasRole } from './utils/auth'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const siderWidth = computed(() => collapsed.value ? '80px' : '220px')

const canAccessAdmin = computed(() => { authRevision.value; return hasAdminAccess() })
const isSupervisor = computed(() => { authRevision.value; return hasRole('supervisor_admin') })
const canSeeTask = computed(() => { authRevision.value; return canAccessAdmin.value && (hasRole('headquarters_admin') || hasRole('operation_admin') || isSupervisor.value) })
const canSeeExpense = computed(() => { authRevision.value; return canAccessAdmin.value && (hasRole('headquarters_admin') || hasRole('finance_admin') || isSupervisor.value) })
const canSeePeople = computed(() => { authRevision.value; return canAccessAdmin.value && (hasRole('headquarters_admin') || hasRole('hr_admin') || isSupervisor.value) })
const canSeeLogs = computed(() => { authRevision.value; return canAccessAdmin.value && hasRole('headquarters_admin') })
const canSeeSettings = computed(() => { authRevision.value; return canAccessAdmin.value && hasRole('headquarters_admin') })

const menuKeyMap = computed(() => {
  const p = route.path
  return {
    tasks: p.startsWith('/tasks') || p.startsWith('/templates') || p.startsWith('/materials'),
    issue: p === '/issue',
    loss: p.startsWith('/loss'),
    logistics: p === '/logistics',
    transfer: p === '/transfer',
    supervisor: p === '/supervisor',
    expense: p.startsWith('/expense'),
    people: p.startsWith('/people'),
    settings: p.startsWith('/settings'),
    logs: p.startsWith('/logs'),
  }
})

function go(path: string) { router.push(path) }
</script>

<style>
.app-layout { min-height: 100vh }
.app-sider { position: fixed !important; left: 0; top: 0; bottom: 0; height: 100vh; overflow-y: auto; z-index: 10 }
.content-wrapper { padding: 24px 28px 32px; min-height: 100vh }

/* ---- 自定义导航菜单 ---- */
.nav-menu { padding: 0 12px 60px }
.nav-group { margin-bottom: 16px }
.nav-group-title { padding: 8px 12px 6px; margin: 0; font-size: 12px; font-weight: 500; color: #98A19C; letter-spacing: 0.5px }
.nav-group-items { display: grid; gap: 4px }
.nav-link { display: flex; align-items: center; gap: 10px; height: 40px; padding: 0 12px; border-radius: 8px; color: #66706A; font-size: 14px; font-weight: 500; cursor: pointer; transition: background .15s, color .15s }
.nav-link:hover { background: #F7F8F6; color: #1F2421 }
.nav-link.active { background: #E7F4EB; color: #2F8F57; font-weight: 600 }
.nav-link .anticon { font-size: 18px }
.nav-sub-link { padding-left: 28px !important; font-size: 13px }
</style>
