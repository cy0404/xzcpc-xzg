<template>
  <div class="no-permission-page">
    <a-empty description="暂无权限">
      <template #image>
        <LockOutlined class="empty-icon" />
      </template>
      <p class="empty-tip">当前账号暂无象掌柜后台访问权限，请联系总部管理员开通。</p>
    </a-empty>
  </div>
</template>

<script setup lang="ts">
import { LockOutlined } from '@ant-design/icons-vue'
</script>

<style scoped>
.no-permission-page {
  min-height: calc(100vh - 56px);
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 12px;
}

.empty-icon {
  font-size: 56px;
  color: #cbd5e1;
}

.empty-tip {
  color: #6b7280;
  margin: 8px 0 0;
}
</style>
