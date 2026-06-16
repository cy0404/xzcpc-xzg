<template>
  <div class="expense-page">
    <ExpenseModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">支出项目管理</h1>
        <p class="page-subtitle">管理二级支出项目。一级分类由数据库预设。</p>
      </div>
      <a-button type="primary" @click="openItemCreate">
        <template #icon><PlusOutlined /></template>
        新增支出项目
      </a-button>
    </div>

    <a-card :bordered="false">
      <a-table :columns="itemColumns" :data-source="items" :pagination="itemPagination" :loading="itemLoading" row-key="itemId">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'typeName'">
            <a-tag color="blue">{{ record.typeName || record.typeId }}</a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'disabled' ? 'default' : 'green'">{{ record.status === 'disabled' ? '停用' : '启用' }}</a-tag>
          </template>
          <template v-if="column.key === 'updatedAt'">
            {{ record.updatedAt ? record.updatedAt.substring(0, 10) : '--' }}
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openItemEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除？" @confirm="handleDeleteItem(record)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="itemModalOpen" :title="editingItem ? '编辑支出项目' : '新增支出项目'" :confirm-loading="itemSaving" @ok="handleItemSave">
      <a-form layout="vertical" :model="itemForm">
        <a-form-item label="所属一级分类" required>
          <a-select v-model:value="itemForm.typeId" placeholder="选择一级分类" style="width:100%">
            <a-select-option v-for="t in types" :key="t.typeId" :value="t.typeId">{{ t.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="项目名称" required>
          <a-input v-model:value="itemForm.name" placeholder="如：门店月租" />
        </a-form-item>
        <a-form-item label="适用示例">
          <a-textarea v-model:value="itemForm.description" placeholder="选填" :rows="2" />
        </a-form-item>
        <a-form-item label="状态" required>
          <a-radio-group v-model:value="itemForm.status">
            <a-radio value="enabled">启用</a-radio>
            <a-radio value="disabled">停用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ExpenseModuleTabs from '../../components/ExpenseModuleTabs.vue'
import { getExpenseTypes } from '../../api/expense'
import api from '../../api/index'

const types = ref<any[]>([])
const items = ref<any[]>([])
const itemLoading = ref(false)
const itemSaving = ref(false)
const itemModalOpen = ref(false)
const editingItem = ref<any>(null)
const itemForm = reactive({ typeId: '', name: '', description: '', status: 'enabled' })

const itemColumns = [
  { title: '项目名称', dataIndex: 'name', key: 'name' },
  { title: '所属分类', key: 'typeName', width: 120 },
  { title: '适用示例', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'status', width: 80 },
  { title: '最近更新', key: 'updatedAt', width: 110 },
  { title: '操作', key: 'action', width: 140 },
]

const itemPagination = computed(() => ({ pageSize: 20, total: items.value.length }))

async function fetchTypes() {
  try {
    const res = (await getExpenseTypes()) as any
    const records = res.data || res || []
    types.value = Array.isArray(records) ? records : []
  } catch { types.value = [] }
}

async function fetchItems() {
  itemLoading.value = true
  try {
    const res = (await api.get('/expense-types/items')) as any
    const list = res.data || res || []
    items.value = Array.isArray(list) ? list : []
  } finally { itemLoading.value = false }
}

function openItemCreate() {
  editingItem.value = null
  itemForm.typeId = ''; itemForm.name = ''; itemForm.description = ''; itemForm.status = 'enabled'
  itemModalOpen.value = true
}

function openItemEdit(row: any) {
  editingItem.value = row
  itemForm.typeId = row.typeId; itemForm.name = row.name
  itemForm.description = row.description || ''; itemForm.status = row.status || 'enabled'
  itemModalOpen.value = true
}

async function handleItemSave() {
  if (!itemForm.typeId) { message.warning('请选择一级分类'); return }
  if (!itemForm.name.trim()) { message.warning('请输入项目名称'); return }
  itemSaving.value = true
  try {
    if (editingItem.value) {
      await api.put(`/expense-types/items/${editingItem.value.itemId}`, { ...itemForm })
      message.success('保存成功')
    } else {
      await api.post('/expense-types/items', { ...itemForm })
      message.success('新增成功')
    }
    itemModalOpen.value = false
    fetchItems()
  } finally { itemSaving.value = false }
}

async function handleDeleteItem(row: any) {
  try { await api.delete(`/expense-types/items/${row.itemId}`) } catch { /* */ }
  items.value = items.value.filter(i => i.itemId !== row.itemId)
  message.success('已删除')
}

onMounted(async () => { await fetchTypes(); await fetchItems() })
</script>

<style scoped>
.expense-page { max-width: 1280px; }
.page-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 24px; font-weight: 600; color: #111827; }
.page-subtitle { margin: 6px 0 0; color: #6b7280; }
</style>
