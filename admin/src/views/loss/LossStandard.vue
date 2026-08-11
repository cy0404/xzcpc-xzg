<template>
  <div class="page">
    <LossModuleTabs />

    <div class="page-title-row">
      <div>
        <h1 class="page-title">验收标准管理</h1>
        <p class="page-subtitle">配置物料的水果验收标准和视频上传标准，门店报损时将展示给店长。</p>
      </div>
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新增标准
      </a-button>
    </div>

    <a-card :bordered="false">
      <a-table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'standardType'">
            <a-tag :color="record.standardType === 'fruit_check' ? 'green' : 'blue'">
              {{ record.standardType === 'fruit_check' ? '水果验收标准' : '视频上传标准' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除？" @confirm="handleDelete(record)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑标准' : '新增标准'"
      :confirm-loading="saving"
      @ok="handleSave"
      width="560px"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="物料">
          <a-select
            v-model:value="form.materialId"
            placeholder="搜索物料名称"
            show-search
            :filter-option="(input: string, option: any) => (option.label || '').includes(input)"
            :options="materialOptions"
            :field-names="{ label: 'materialName', value: 'materialId' }"
            @change="onMaterialChange"
          />
        </a-form-item>
        <a-form-item label="物料分类" v-if="form.standardType === 'video_upload'">
          <a-input v-model:value="form.materialCategory" placeholder="手动输入分类名，或先选物料自动填入" />
        </a-form-item>
        <a-form-item label="标准类型" required>
          <a-select v-model:value="form.standardType" placeholder="请选择">
            <a-select-option value="fruit_check">水果验收标准</a-select-option>
            <a-select-option value="video_upload">视频上传标准</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="标准标题">
          <a-input v-model:value="form.title" placeholder="标准标题（选填）" />
        </a-form-item>
        <a-form-item label="文字说明">
          <a-textarea v-model:value="form.description" :rows="4" placeholder="文字说明" />
        </a-form-item>
        <a-form-item label="媒体图片">
          <a-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            accept="image/*"
            list-type="picture-card"
            :file-list="uploadFileList"
            @change="onUploadChange"
          >
            <div v-if="uploadFileList.length < 10">
              <plus-outlined />
              <div style="margin-top:8px">上传</div>
            </div>
          </a-upload>
          <div v-if="uploadedUrls.length" style="margin-top:8px">
            <a-tag v-for="(url, idx) in uploadedUrls.split(',').filter(Boolean)" :key="idx" color="green" style="max-width:200px;overflow:hidden;text-overflow:ellipsis">{{ url }}</a-tag>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import LossModuleTabs from '../../components/LossModuleTabs.vue'
import { getStandards, createStandard, updateStandard, deleteStandard } from '../../api/loss'
import api from '../../api/index'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const modalOpen = ref(false)
const editing = ref<any>(null)
const form = reactive({
  materialId: '',
  materialName: '',
  materialCategory: '',
  standardType: '',
  title: '',
  description: '',
  mediaUrls: '',
})

const pagination = reactive({ current: 1, pageSize: 20, total: 0 })

const materialOptions = ref<any[]>([])

async function loadMaterials() {
  if (materialOptions.value.length > 0) return
  try {
    const res: any = await api.get('/materials', { params: { pageSize: 200 } })
    const records = res?.data?.records || res?.records || []
    materialOptions.value = records
  } catch { materialOptions.value = [] }
}

function onMaterialChange(materialId: string) {
  const m = materialOptions.value.find((o: any) => o.materialId === materialId)
  if (m) {
    form.materialName = m.materialName || ''
    form.materialCategory = m.category || ''
  }
}

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('admin_token')}`,
}))
const uploadFileList = ref<any[]>([])
const uploadedUrls = ref('')

function onUploadChange(info: any) {
  uploadFileList.value = info.fileList.filter((f: any) => f.status === 'done' || f.status === 'uploading')
  if (info.file.status === 'done') {
    const url = info.file.response?.data?.url || info.file.response?.url
    if (url) {
      const urls = uploadedUrls.value ? uploadedUrls.value.split(',').filter(Boolean) : []
      urls.push(url)
      uploadedUrls.value = urls.join(',')
    }
    message.success('上传成功')
  } else if (info.file.status === 'error') {
    message.error('上传失败')
  }
}

const columns = [
  { title: '物料ID', dataIndex: 'materialId', key: 'materialId' },
  { title: '物料分类', dataIndex: 'materialCategory', key: 'materialCategory' },
  { title: '标准类型', key: 'standardType' },
  { title: '标题', dataIndex: 'title', key: 'title' },
  { title: '操作', key: 'action', width: 140 },
]

async function fetchList() {
  loading.value = true
  try {
    const res: any = await getStandards({ pageNum: pagination.current, pageSize: pagination.pageSize })
    list.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally { loading.value = false }
}

function onTableChange(p: any) {
  pagination.current = p.current
  pagination.pageSize = p.pageSize
  fetchList()
}

function resetForm() {
  Object.assign(form, {
    materialId: '',
    materialName: '',
    materialCategory: '',
    standardType: '',
    title: '',
    description: '',
    mediaUrls: '',
  })
  materialOptions.value = []
  uploadedUrls.value = ''
  uploadFileList.value = []
}

function openCreate() {
  resetForm()
  editing.value = null
  modalOpen.value = true
  loadMaterials()
}

function openEdit(record: any) {
  Object.assign(form, {
    materialId: record.materialId || '',
    materialName: record.materialName || '',
    materialCategory: record.materialCategory || '',
    standardType: record.standardType || '',
    title: record.title || '',
    description: record.description || '',
    mediaUrls: record.mediaUrls || '',
  })
  // 预填物料选择器
  if (record.materialId) {
    materialOptions.value = [{ materialId: record.materialId, materialName: record.materialName || record.materialId, category: record.materialCategory }]
  }
  uploadedUrls.value = record.mediaUrls || ''
  uploadFileList.value = (record.mediaUrls || '').split(',').filter(Boolean).map((url: string, idx: number) => ({
    uid: `-${idx}-${Date.now()}`,
    name: `image-${idx}`,
    url,
    status: 'done',
  }))
  editing.value = record
  modalOpen.value = true
  loadMaterials()
}

async function handleSave() {
  if (!form.standardType) { message.warning('请选择标准类型'); return }
  saving.value = true
  try {
    if (editing.value) {
      await updateStandard(editing.value.id, { ...form, mediaUrls: uploadedUrls.value })
      message.success('已更新')
    } else {
      await createStandard({ ...form, mediaUrls: uploadedUrls.value })
      message.success('已创建')
    }
    modalOpen.value = false
    fetchList()
  } catch { /* api layer handles error toast */ }
  finally { saving.value = false }
}

async function handleDelete(record: any) {
  await deleteStandard(record.id)
  message.success('已删除')
  fetchList()
}

onMounted(() => fetchList())
</script>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; }
.page-title-row { display: flex; justify-content: space-between; align-items: flex-start; margin: 20px 0; }
.page-title { font-size: 22px; font-weight: 700; color: #1F2421; margin: 0; }
.page-subtitle { color: #66706A; font-size: 14px; margin-top: 4px; }
</style>
