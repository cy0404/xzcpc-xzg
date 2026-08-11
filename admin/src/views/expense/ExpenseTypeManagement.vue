<template>
  <div class="expense-page">
    <ExpenseModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">支出项目管理</h1>
        <p class="page-subtitle">管理支出分类。一级分类名称自由填写。</p>
      </div>
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新增支出分类
      </a-button>
    </div>

    <a-card :bordered="false">
      <a-table :columns="columns" :data-source="typeList" :pagination="pagination" :loading="loading" row-key="typeId">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'firstTypeName'">
            <a-tag color="blue">{{ record.firstTypeName || '--' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'disabled' ? 'default' : 'green'">{{ record.status === 'disabled' ? '停用' : '启用' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ record.updatedAt ? record.updatedAt.substring(0, 10) : '--' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除？" @confirm="handleDelete(record)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </template>
          <template v-else>
            {{ record[column.dataIndex] || '--' }}
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="editing ? '编辑支出分类' : '新增支出分类'" :confirm-loading="saving" @ok="handleSave">
      <a-form layout="vertical" :model="form">
        <a-form-item label="所属分类" required>
          <a-auto-complete
            v-model:value="form.firstTypeName"
            :options="firstTypeOptions"
            placeholder="选择或输入所属分类"
            style="width:100%"
          />
        </a-form-item>
        <a-form-item label="项目名称" required>
          <a-input v-model:value="form.name" placeholder="如：门店月租" />
        </a-form-item>
        <a-form-item label="适用示例">
          <a-textarea v-model:value="form.description" placeholder="选填" :rows="2" />
        </a-form-item>
        <a-form-item label="状态" required>
          <a-radio-group v-model:value="form.status">
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
import api from '../../api/index'

const typeList = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editing = ref<any>(null)
const form = reactive({ firstTypeName: '', name: '', description: '', status: 'enabled' })

/** 从已有数据中提取去重的所属分类作为下拉建议 */
const firstTypeOptions = computed(() => {
  const names = [...new Set(typeList.value.map((t: any) => t.firstTypeName).filter(Boolean))] as string[]
  return names.map(n => ({ value: n, label: n }))
})

const columns = [
  { title: '项目名称', dataIndex: 'name', key: 'name' },
  { title: '所属分类', key: 'firstTypeName', width: 140 },
  { title: '适用示例', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'status', width: 80 },
  { title: '最近更新', key: 'updatedAt', width: 110 },
  { title: '操作', key: 'action', width: 140 },
]

const pagination = computed(() => ({ pageSize: 20, total: typeList.value.length }))

async function fetchList() {
  loading.value = true
  try {
    const res = (await api.get('/expense-types')) as any
    const list = res.data || res || []
    typeList.value = Array.isArray(list) ? list : []
  } finally { loading.value = false }
}

function openCreate() {
  editing.value = null
  form.firstTypeName = ''; form.name = ''; form.description = ''; form.status = 'enabled'
  modalOpen.value = true
}

function openEdit(row: any) {
  editing.value = row
  form.firstTypeName = row.firstTypeName || ''
  form.name = row.name
  form.description = row.description || ''
  form.status = row.status || 'enabled'
  modalOpen.value = true
}

async function handleSave() {
  if (!form.firstTypeName.trim()) { message.warning('请输入所属分类'); return }
  if (!form.name.trim()) { message.warning('请输入项目名称'); return }
  saving.value = true
  try {
    if (editing.value) {
      await api.put(`/expense-types/${editing.value.typeId}`, { ...form })
      message.success('保存成功')
    } else {
      await api.post('/expense-types', { ...form })
      message.success('新增成功')
    }
    modalOpen.value = false
    fetchList()
  } finally { saving.value = false }
}

async function handleDelete(row: any) {
  try { await api.delete(`/expense-types/${row.typeId}`) } catch { /* */ }
  typeList.value = typeList.value.filter(t => t.typeId !== row.typeId)
  message.success('已删除')
}

onMounted(() => { fetchList() })
</script>

<style scoped>
.expense-page { max-width: 1280px; }
.page-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 24px; font-weight: 600; color: #111827; }
.page-subtitle { margin: 6px 0 0; color: #6b7280; }
</style>
