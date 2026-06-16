<template>
  <div class="template-page">
    <PageModuleTabs />

    <div class="template-layout">
      <!-- 左侧：模板列表 -->
      <aside class="template-sidebar">
        <a-button type="primary" block class="btn-new-template" @click="showAddModal">
          <template #icon><PlusOutlined /></template>
          新建模板
        </a-button>

        <a-input-search
          v-model:value="keyword"
          placeholder="搜索模板名称..."
          allow-clear
          class="template-search"
          @search="handleSearch"
        />

        <a-spin :spinning="loading">
          <div v-if="dataSource.length === 0 && !loading" class="list-empty">
            <a-empty description="暂无模板" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
          </div>
          <div
            v-for="item in dataSource"
            :key="item.templateId"
            class="template-card"
            :class="{ selected: item.templateId === selectedTemplateId }"
            @click="selectTemplate(item)"
          >
            <div class="template-card-name">{{ item.templateName }}</div>
            <div class="template-card-meta">
              最后更新 {{ formatDate(item.updatedAt) }}
            </div>
            <a-tag :color="getStatusColor(item.status)">
              {{ getStatusText(item.status) }}
            </a-tag>
          </div>
        </a-spin>

        <div v-if="pagination.total > pagination.pageSize" class="list-pagination">
          <a-pagination
            v-model:current="pagination.current"
            :total="pagination.total"
            :page-size="pagination.pageSize"
            size="small"
            simple
            @change="onPageChange"
          />
        </div>
      </aside>

      <!-- 右侧：模板编辑区 -->
      <main class="template-editor">
        <div v-if="!selectedTemplate" class="editor-placeholder">
          <FileTextOutlined class="placeholder-icon" />
          <p>请从左侧选择一个模板，或点击「新建模板」</p>
        </div>

        <template v-else>
          <div class="editor-header">
            <div class="editor-title-row">
              <template v-if="editingTitle">
                <a-input
                  v-model:value="titleDraft"
                  class="title-input"
                  :maxlength="50"
                  @pressEnter="saveTitleLocal"
                />
                <a-button type="link" @click="saveTitleLocal">保存</a-button>
                <a-button type="link" @click="cancelTitleEdit">取消</a-button>
              </template>
              <template v-else>
                <h2 class="editor-title">{{ selectedTemplate.templateName }}</h2>
                <EditOutlined v-if="!isTemplateEnabled" class="title-edit-icon" @click="startTitleEdit" />
              </template>
            </div>
            <p class="editor-desc">
              配置盘点时的分区及物料顺序，智能推荐基于历史盘点路径优化。
            </p>
            <div class="editor-search-row">
              <div class="material-locate-search" :class="{ focused: materialLocateFocused }">
                <SearchOutlined class="locate-search-icon" />
                <input
                  v-model="materialLocateKeyword"
                  class="locate-search-input"
                  placeholder="搜索物料名称，快速定位到所在分区..."
                  @focus="materialLocateFocused = true"
                  @blur="handleLocateBlur"
                />
                <CloseCircleFilled
                  v-if="materialLocateKeyword"
                  class="locate-clear-icon"
                  @click="materialLocateKeyword = ''"
                />
              </div>
              <!-- 搜索结果下拉 -->
              <div
                v-if="materialLocateFocused && materialLocateKeyword.trim()"
                class="locate-dropdown"
              >
                <div
                  v-for="r in materialLocateResults"
                  :key="r.materialId + '-' + r.zoneId"
                  class="locate-dropdown-item"
                  @mousedown.prevent="locateMaterial(r)"
                >
                  <span class="locate-mat-name">{{ r.materialName }}</span>
                  <span v-if="r.spec" class="locate-mat-spec">{{ r.spec }}</span>
                  <a-tag class="locate-zone-tag">{{ r.zoneName }}</a-tag>
                </div>
                <div v-if="materialLocateResults.length === 0" class="locate-dropdown-empty">
                  未找到匹配的物料
                </div>
              </div>
            </div>
            <div class="editor-actions">
              <a-popconfirm title="确认删除此模板？模板下的分区和物料将被一并清除。" ok-text="确认删除" cancel-text="取消" ok-type="danger" @confirm="handleDeleteTemplate">
                <a-button danger :loading="deleteLoading">删除</a-button>
              </a-popconfirm>
              <a-popconfirm
                v-if="selectedTemplate.status === 1"
                title="确认停用此模板？"
                ok-text="确认停用"
                cancel-text="取消"
                @confirm="handleDisable"
              >
                <a-button :style="{ borderColor: '#fa8c16', color: '#fa8c16' }" :loading="publishLoading">停用</a-button>
              </a-popconfirm>
              <a-popconfirm
                v-if="selectedTemplate.status !== 1 && !dirty"
                title="确认启用此模板？"
                ok-text="确认启用"
                cancel-text="取消"
                @confirm="handleEnable"
              >
                <a-button
                  :style="{ borderColor: '#16a34a', color: '#16a34a' }"
                  :loading="publishLoading"
                >
                  启用
                </a-button>
              </a-popconfirm>
              <a-tooltip
                :title="selectedTemplate.status === 1 ? '需要先停用模板才能编辑' : ''"
              >
                <a-button
                  type="primary"
                  :disabled="selectedTemplate.status === 1"
                  :loading="saveTitleLoading"
                  @click="saveAllChanges"
                >
                  保存
                </a-button>
              </a-tooltip>
            </div>
            <div v-if="isTemplateEnabled" class="enabled-warning">
              启用中的模板不可修改，如需修改，请先停用当前模板再操作。
            </div>
          </div>

          <a-spin :spinning="zonesLoading">
            <div
              v-for="(zone, idx) in sortedZones"
              :key="zone.zoneId"
              class="zone-card"
              :class="{ 'zone-card--dragging': dragZoneIdx === idx, 'zone-card--dragover': dragOverZoneIdx === idx }"
              :draggable="!isTemplateEnabled"
              @dragstart="isTemplateEnabled ? null : onZoneDragStart(idx, $event)"
              @dragover.prevent="isTemplateEnabled ? null : onZoneDragOver(idx)"
              @dragleave="isTemplateEnabled ? null : onZoneDragLeave"
              @drop="isTemplateEnabled ? null : onZoneDrop(idx)"
              @dragend="isTemplateEnabled ? null : onZoneDragEnd"
            >
              <div class="zone-card-header">
                <span
                  class="zone-collapse-icon"
                  :class="{ collapsed: collapsedZoneIds.has(zone.zoneId) }"
                  @click.stop="toggleCollapse(zone.zoneId)"
                >
                  <RightOutlined />
                </span>
                <HolderOutlined class="drag-icon" />
                <span class="zone-card-title">{{ zone.zoneName }}</span>
                <a-tag class="zone-count-tag">
                  {{ zone.materialCount ?? zone.materials?.length ?? 0 }} 项物料
                </a-tag>
                <a-button
                  size="small"
                  class="zone-add-material"
                  :disabled="isTemplateEnabled"
                  @click="showAddMaterialModal(zone)"
                >
                  添加物料
                </a-button>
                <a-dropdown v-if="!isTemplateEnabled">
                  <EllipsisOutlined class="zone-more" />
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="showEditZoneModal(zone)">编辑分区</a-menu-item>
                      <a-menu-item danger @click="confirmDeleteZone(zone)">删除分区</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </div>

              <div v-if="!collapsedZoneIds.has(zone.zoneId) && zone.materials?.length" class="zone-material-list">
                <div
                  v-for="(mat, matIdx) in zone.materials"
                  :key="mat.materialId"
                  class="material-item"
                  :class="{
                    'material-item--dragging': dragMatZoneId === zone.zoneId && dragMatIdx === matIdx,
                    'material-item--dragover': dragOverMatZoneId === zone.zoneId && dragOverMatIdx === matIdx,
                    'material-item--highlight': highlightMatId === mat.materialId,
                  }"
                  :data-mat-id="mat.materialId"
                  :draggable="!isTemplateEnabled"
                  @dragstart="isTemplateEnabled ? null : onMatDragStart(zone.zoneId, matIdx, $event)"
                  @dragover.prevent="isTemplateEnabled ? null : onMatDragOver(zone.zoneId, matIdx)"
                  @dragleave="isTemplateEnabled ? null : onMatDragLeave"
                  @drop="isTemplateEnabled ? null : onMatDrop(zone.zoneId, matIdx)"
                  @dragend="isTemplateEnabled ? null : onMatDragEnd"
                >
                  <HolderOutlined class="drag-icon material-drag" />
                  <div class="material-icon-wrap">
                    <InboxOutlined />
                  </div>
                  <div class="material-info">
                    <div class="material-name">{{ mat.materialName }}</div>
                    <div v-if="mat.spec" class="material-spec-line">{{ mat.spec }}</div>
                  </div>
                  <div v-if="mat.inventoryUnit || mat.unit" class="material-inventory-unit">
                    盘点单位：{{ mat.inventoryUnit || mat.unit }}
                  </div>
                  <a-popconfirm v-if="!isTemplateEnabled" title="确认移除此物料？" @confirm="handleRemoveMaterialLocal(zone, mat)">
                    <DeleteOutlined class="material-delete" />
                  </a-popconfirm>
                </div>
              </div>
              <div v-else-if="!collapsedZoneIds.has(zone.zoneId)" class="zone-empty">暂无物料，点击「添加物料」</div>
            </div>
          </a-spin>

          <button type="button" class="btn-new-zone" :disabled="isTemplateEnabled" @click="showAddZoneModal">
            <PlusOutlined />
            新建分区
          </button>
        </template>
      </main>
    </div>

    <!-- 新建模板弹窗 -->
    <a-modal
      v-model:open="templateModalVisible"
      title="新建模板"
      :footer="null"
      width="480px"
      destroy-on-close
      @cancel="resetTemplateModal"
    >
      <div class="modal-field">
        <label class="modal-label" :class="{ error: nameValidateStatus === 'error' }">
          模板名称
        </label>
        <a-input
          v-model:value="templateForm.templateName"
          placeholder="请输入模板名称"
          :maxlength="50"
          :status="nameValidateStatus === 'error' ? 'error' : nameValidateStatus === 'success' ? '' : undefined"
          @blur="validateTemplateName"
          @input="onTemplateNameInput"
        >
          <template v-if="nameValidateStatus === 'success'" #suffix>
            <CheckCircleFilled style="color: #22c55e" />
          </template>
          <template v-if="nameValidateStatus === 'error'" #suffix>
            <ExclamationCircleFilled style="color: #ef4444" />
          </template>
        </a-input>
        <p v-if="nameValidateStatus === 'success'" class="field-hint success">名称可用</p>
        <p v-else-if="nameValidateStatus === 'error'" class="field-hint error">
          ⚠️ 名称已存在或包含特殊字符，仅支持中英文、数字及下划线。
        </p>
        <p v-else class="field-hint">支持中英文、数字及下划线，最长 50 个字符。</p>
      </div>
      <div class="modal-footer">
        <a-button @click="templateModalVisible = false">取消</a-button>
        <a-button
          type="primary"
          :loading="templateSubmitLoading"
          :disabled="!canSubmitTemplate"
          @click="handleTemplateSubmit"
        >
          确认创建
        </a-button>
      </div>
    </a-modal>

    <!-- 添加分区弹窗 -->
    <a-modal
      v-model:open="zoneModalVisible"
      :title="editingZoneId ? '编辑分区' : '添加分区'"
      :ok-text="editingZoneId ? '确认' : '确认添加'"
      cancel-text="取消"
      :confirm-loading="zoneSubmitLoading"
      @ok="handleZoneSubmit"
      @cancel="resetZoneForm"
    >
      <a-form ref="zoneFormRef" :model="zoneForm" :rules="zoneFormRules" layout="vertical">
        <a-form-item label="分区名称" name="zoneName" required>
          <a-input
            v-model:value="zoneForm.zoneName"
            placeholder="请输入分区名称，例如：后仓货架3"
            :maxlength="50"
          />
        </a-form-item>
        <a-form-item label="排序值（可选）" name="sortNo">
          <a-input-number
            v-model:value="zoneForm.sortNo"
            placeholder="请输入数字，数值越小越靠前"
            style="width: 100%"
            :min="0"
          />
        </a-form-item>
      </a-form>
      <div v-if="!editingZoneId" class="zone-hint">
        <BulbOutlined />
        <span>
          智能建议：根据历史数据，建议将高频动销商品的存放区域分配较小的排序值，以提升盘点效率。
        </span>
      </div>
    </a-modal>

    <!-- 添加物料弹窗 -->
    <a-modal
      v-model:open="materialModalVisible"
      title="添加物料到分区"
      width="560px"
      :footer="null"
      destroy-on-close
      @cancel="resetMaterialSearch"
    >
      <a-tag color="green" class="current-zone-tag">
        当前分区：{{ currentZoneName }}
      </a-tag>

      <a-input
        v-model:value="materialKeyword"
        placeholder="搜索物料名称或规格"
        class="material-search"
        enter-button
        @search="searchMaterials"
      />

      <div class="material-chips">
        <button
          v-for="chip in visibleChips"
          :key="chip.key"
          type="button"
          class="chip"
          :class="{ active: activeChip === chip.key }"
          @click="activeChip = chip.key"
        >
          {{ chip.label }}
        </button>
        <a
          v-if="materialChips.length > 5"
          class="chip-expand"
          @click="showAllChips = !showAllChips"
        >
          {{ showAllChips ? '收起' : '展开' }}
          <UpOutlined v-if="showAllChips" />
          <DownOutlined v-else />
        </a>
      </div>

      <a-spin :spinning="searchMaterialLoading">
        <div class="material-picker-list">
          <div
            v-for="item in filteredSearchMaterials"
            :key="item.materialId"
            class="picker-item"
          >
            <div class="picker-icon"><InboxOutlined /></div>
            <div class="picker-info">
              <div class="picker-name">
                {{ item.materialName }}
                <span v-if="item.category" class="picker-type-tag">{{ item.category }}</span>
              </div>
              <div class="picker-spec">
                <span class="spec-tag" v-if="item.spec">{{ item.spec }}</span>
                <span v-else>-</span>
                &nbsp;&nbsp;&nbsp;&nbsp;
                <span class="picker-unit">盘点单位: {{ item.inventoryUnit || item.unit || '-' }}</span>
              </div>
            </div>
            <a-button
              v-if="!item._added"
              type="primary"
              size="small"
              @click="handleAddMaterialLocal(item)"
            >
              加入
            </a-button>
            <span v-else class="picker-added">
              <CheckOutlined /> 已在当前分区
            </span>
          </div>
          <a-empty
            v-if="!searchMaterialLoading && filteredSearchMaterials.length === 0"
            :description="materialKeyword ? '未找到匹配物料' : '暂无物料'"
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
          />
        </div>
      </a-spin>

      <div class="material-modal-footer">
        <a-button @click="materialModalVisible = false">完成</a-button>
      </div>
    </a-modal>

    <!-- 未保存更改确认弹窗 -->
    <a-modal
      v-model:open="confirmModalVisible"
      title="未保存的更改"
      :footer="null"
      width="420px"
      destroy-on-close
    >
      <p style="margin-bottom: 24px; font-size: 14px; color: #4b5563;">
        您修改的模板还未保存，是否关闭操作？
      </p>
      <div style="display: flex; justify-content: flex-end; gap: 12px;">
        <a-button danger @click="handleDiscardAndLeave">确认关闭</a-button>
        <a-button type="primary" @click="handleSaveAndLeave">保存</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Empty, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  FileTextOutlined,
  EditOutlined,
  HolderOutlined,
  EllipsisOutlined,
  InboxOutlined,
  DeleteOutlined,
  CheckCircleFilled,
  ExclamationCircleFilled,
  BulbOutlined,
  CheckOutlined,
  DownOutlined,
  UpOutlined,
  RightOutlined,
  SearchOutlined,
  CloseCircleFilled,
} from '@ant-design/icons-vue'
import PageModuleTabs from '../../components/PageModuleTabs.vue'
import api from '../../api/index'
import {
  getTemplates,
  addTemplate,
  updateTemplate,
  deleteTemplate,
  setTemplateStatus,
  getTemplateDetail,
  getTemplateZones,
  addTemplateZone,
  updateTemplateZone,
  deleteTemplateZone,
  addZoneMaterial,
  deleteZoneMaterial,
  updateZoneSort,
  updateZoneMaterialSort,
} from '../../api/template'

interface ZoneItem {
  zoneId: number
  zoneName: string
  sortNo?: number | null
  materialCount?: number
  materials?: ZoneMaterial[]
}

interface ZoneMaterial {
  materialId: string
  materialName: string
  spec?: string
  unit?: string
  inventoryUnit?: string
  leibie?: string
  parentCategory?: string
  category?: string
  sortNo?: number | null
}

interface MaterialItem {
  materialId: string
  materialName: string
  spec?: string
  unit?: string
  inventoryUnit?: string
  leibie?: string
  parentCategory?: string
  category?: string
  _added?: boolean
}

const keyword = ref('')
const loading = ref(false)
const zonesLoading = ref(false)
const dataSource = ref<any[]>([])
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })

const selectedTemplateId = ref<number | null>(null)
const selectedTemplate = ref<any>(null)
const zones = ref<ZoneItem[]>([])

/** 已折叠的分区 ID 集合 */
const collapsedZoneIds = ref(new Set<number>())

const sortedZones = computed(() =>
  [...zones.value].sort((a, b) => {
    if (a.sortNo != null && b.sortNo != null) return a.sortNo - b.sortNo
    if (a.sortNo != null) return -1
    if (b.sortNo != null) return 1
    return 0
  }),
)

const isTemplateEnabled = computed(() => selectedTemplate.value?.status === 1)

// ========== 草稿模式状态 ==========
const dirty = ref(false)
const tempZoneIdCounter = ref(-1)

/** 加载时的原始物料快照，保存时直接对比，避免保存时逐个调 API */
const originalZoneMatIds = ref<Map<number, Set<string>>>(new Map())
const confirmModalVisible = ref(false)
const pendingTargetRecord = ref<any>(null)

/** 是否有未保存的更改 */
const hasUnsavedChanges = computed(() => dirty.value)

/** 记录哪些分区被修改过（新增/编辑/删除/物料变更），保存时只同步这些分区 */
const changedZoneIds = ref(new Set<number>())

function markDirty() {
  dirty.value = true
}

function markZoneDirty(zoneId: number) {
  markDirty()
  changedZoneIds.value.add(zoneId)
  changedZoneIds.value = new Set(changedZoneIds.value)
}

// ========== 分区拖拽排序 ==========
const dragZoneIdx = ref<number | null>(null)
const dragOverZoneIdx = ref<number | null>(null)

function onZoneDragStart(idx: number, e: DragEvent) {
  dragZoneIdx.value = idx
  e.dataTransfer!.effectAllowed = 'move'
}

function onZoneDragOver(idx: number) {
  if (dragZoneIdx.value === null || dragZoneIdx.value === idx) return
  const list = [...zones.value].sort((a, b) => {
    if (a.sortNo != null && b.sortNo != null) return a.sortNo - b.sortNo
    if (a.sortNo != null) return -1
    if (b.sortNo != null) return 1
    return 0
  })
  const [moved] = list.splice(dragZoneIdx.value, 1)
  list.splice(idx, 0, moved)
  list.forEach((z, i) => { z.sortNo = i + 1 })
  zones.value = list
  dragZoneIdx.value = idx
  dragOverZoneIdx.value = idx
}

function onZoneDragLeave() {
  dragOverZoneIdx.value = null
}

function onZoneDrop(_idx: number) {
  if (dragZoneIdx.value === null) return
  dragZoneIdx.value = null
  dragOverZoneIdx.value = null
  // 拖拽排序影响所有分区，全部标记为脏
  zones.value.forEach(z => changedZoneIds.value.add(z.zoneId))
  changedZoneIds.value = new Set(changedZoneIds.value)
  markDirty()
}

function onZoneDragEnd() {
  dragZoneIdx.value = null
  dragOverZoneIdx.value = null
}

// ========== 物料拖拽排序 ==========
const dragMatZoneId = ref<number | null>(null)
const dragMatIdx = ref<number | null>(null)
const dragOverMatZoneId = ref<number | null>(null)
const dragOverMatIdx = ref<number | null>(null)

function onMatDragStart(zoneId: number, matIdx: number, e: DragEvent) {
  dragMatZoneId.value = zoneId
  dragMatIdx.value = matIdx
  e.dataTransfer!.effectAllowed = 'move'
}

function onMatDragOver(zoneId: number, matIdx: number) {
  if (dragMatZoneId.value === null || dragMatIdx.value === null) return
  if (dragMatZoneId.value !== zoneId || dragMatIdx.value === matIdx) return
  const zone = zones.value.find(z => z.zoneId === zoneId)
  if (!zone?.materials) return
  const list = [...zone.materials]
  const [moved] = list.splice(dragMatIdx.value, 1)
  list.splice(matIdx, 0, moved)
  zone.materials = list
  dragMatIdx.value = matIdx
  dragOverMatZoneId.value = zoneId
  dragOverMatIdx.value = matIdx
}

function onMatDragLeave() {
  dragOverMatZoneId.value = null
  dragOverMatIdx.value = null
}

function onMatDrop(zoneId: number, _matIdx: number) {
  if (dragMatZoneId.value === null || dragMatIdx.value === null) return
  if (dragMatZoneId.value !== zoneId) {
    dragMatZoneId.value = null
    dragMatIdx.value = null
    dragOverMatZoneId.value = null
    dragOverMatIdx.value = null
    return
  }
  dragMatZoneId.value = null
  dragMatIdx.value = null
  dragOverMatZoneId.value = null
  dragOverMatIdx.value = null
  markZoneDirty(zoneId)
}

function onMatDragEnd() {
  dragMatZoneId.value = null
  dragMatIdx.value = null
  dragOverMatZoneId.value = null
  dragOverMatIdx.value = null
}

const editingTitle = ref(false)
const titleDraft = ref('')
const saveTitleLoading = ref(false)
const publishLoading = ref(false)
const deleteLoading = ref(false)

const templateModalVisible = ref(false)
const templateSubmitLoading = ref(false)
const templateForm = reactive({ templateName: '' })
const nameValidateStatus = ref<'success' | 'error' | ''>('')

const zoneModalVisible = ref(false)
const zoneSubmitLoading = ref(false)
const editingZoneId = ref<number | null>(null)
const zoneFormRef = ref<any>(null)
const zoneForm = reactive({ zoneName: '', sortNo: undefined as number | undefined })
const zoneFormRules = {
  zoneName: [
    { required: true, message: '请输入分区名称', trigger: 'blur' },
    { max: 50, message: '分区名称不能超过 50 个字符', trigger: 'blur' },
  ],
}

const materialModalVisible = ref(false)
const materialKeyword = ref('')
const allMaterials = ref<MaterialItem[]>([])
const searchMaterialLoading = ref(false)
const currentMaterialZoneId = ref<number | null>(null)
const currentZoneName = ref('')
const currentZoneMaterials = ref<ZoneMaterial[]>([])
const activeChip = ref('all')
const showAllChips = ref(false)

// ========== 物料搜索定位 ==========

const materialLocateKeyword = ref('')
const materialLocateFocused = ref(false)
const highlightMatId = ref<string | null>(null)

/** 跨所有分区搜索物料 */
const materialLocateResults = computed(() => {
  const kw = materialLocateKeyword.value.trim()
  if (!kw) return []
  const results: { zoneId: number; zoneName: string; materialId: string; materialName: string; spec?: string }[] = []
  for (const zone of zones.value) {
    if (!zone.materials) continue
    for (const mat of zone.materials) {
      if (mat.materialName && mat.materialName.includes(kw)) {
        results.push({
          zoneId: zone.zoneId,
          zoneName: zone.zoneName,
          materialId: mat.materialId,
          materialName: mat.materialName,
          spec: mat.spec,
        })
      }
    }
  }
  return results.slice(0, 20)
})

const visibleChips = computed(() => {
  if (showAllChips.value || materialChips.value.length <= 5) return materialChips.value
  return materialChips.value.slice(0, 5)
})

const materialChips = computed(() => {
  const set = new Set<string>()
  allMaterials.value.forEach((m) => m.category && set.add(m.category))
  return [{ key: 'all', label: '全部' }, ...Array.from(set).map((cat) => ({ key: cat, label: cat }))]
})

const canSubmitTemplate = computed(
  () =>
    templateForm.templateName.trim().length > 0 && nameValidateStatus.value !== 'error',
)

const filteredSearchMaterials = computed(() => {
  let list = allMaterials.value
  if (activeChip.value !== 'all') {
    list = list.filter((m) => m.category === activeChip.value)
  }
  const kw = materialKeyword.value.trim()
  if (kw) {
    list = list.filter((m) =>
      (m.materialName && m.materialName.includes(kw)) ||
      (m.spec && m.spec.includes(kw)),
    )
  }
  return list
})

function getStatusText(status: number): string {
  if (status === 1) return '启用中'
  if (status === 0) return '已停用'
  if (status === 2) return '草稿'
  return '未知'
}

function getStatusColor(status: number): string {
  if (status === 1) return 'green'
  if (status === 0) return 'red'
  return 'default'
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getTemplates({
      keyword: keyword.value,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })
    dataSource.value = res.data?.records || []
    pagination.total = res.data?.total || 0

    if (!selectedTemplateId.value && dataSource.value.length > 0) {
      selectTemplate(dataSource.value[0])
    }
  } catch (e: any) {
    message.error(e.message || '获取模板列表失败')
  } finally {
    loading.value = false
  }
}

/** 加载分区及其物料 */
async function loadZonesWithMaterials() {
  if (!selectedTemplateId.value) return
  zonesLoading.value = true
  try {
    const res: any = await getTemplateZones(selectedTemplateId.value)
    const zoneList: ZoneItem[] = res.data || []
    // 并行加载每个分区的物料
    await Promise.all(zoneList.map(async (zone) => {
      try {
        const matRes: any = await api.get(`/templates/${selectedTemplateId.value}/zones/${zone.zoneId}/materials`)
        const mats: ZoneMaterial[] = (matRes.data || []).map((m: any) => ({
          materialId: String(m.materialId),
          materialName: m.materialName || '',
          spec: m.spec || '',
          inventoryUnit: m.inventoryUnit || '',
          unit: m.unit || '',
          parentCategory: m.parentCategory || '',
          category: m.category || m.leibie || '',
          sortNo: m.sortNo,
        }))
        mats.sort((a, b) => {
          if (a.sortNo != null && b.sortNo != null) return a.sortNo - b.sortNo
          if (a.sortNo != null) return -1
          if (b.sortNo != null) return 1
          return 0
        })
        zone.materials = mats
      } catch {
        zone.materials = []
      }
    }))
    zones.value = zoneList
    // 保存原始物料快照（zoneId → materialId 集合），保存时直接对比，无需再查服务器
    const snap = new Map<number, Set<string>>()
    zoneList.forEach(z => {
      snap.set(z.zoneId, new Set((z.materials || []).map(m => String(m.materialId))))
    })
    originalZoneMatIds.value = snap
    // 默认全部收缩
    collapsedZoneIds.value = new Set(zoneList.map(z => z.zoneId))
  } catch (e: any) {
    message.error(e.message || '获取分区列表失败')
  } finally {
    zonesLoading.value = false
  }
}

async function selectTemplate(record: any) {
  // 有未保存更改时，弹窗确认
  if (hasUnsavedChanges.value && record.templateId !== selectedTemplateId.value) {
    pendingTargetRecord.value = record
    confirmModalVisible.value = true
    return
  }
  await switchToTemplate(record)
}

async function switchToTemplate(record: any) {
  pendingTargetRecord.value = null
  selectedTemplateId.value = record.templateId
  try {
    const detailRes: any = await getTemplateDetail(record.templateId)
    selectedTemplate.value = detailRes.data
  } catch (e: any) {
    message.error(e.message || '获取模板详情失败')
  }
  editingTitle.value = false
  dirty.value = false
  await loadZonesWithMaterials()
}

async function reloadZones() {
  await loadZonesWithMaterials()
}

// ========== 导航守卫：丢弃更改 ==========
async function handleDiscardAndLeave() {
  confirmModalVisible.value = false
  dirty.value = false
  if (pendingTargetRecord.value) {
    await switchToTemplate(pendingTargetRecord.value)
  }
}

// ========== 导航守卫：保存后离开 ==========
async function handleSaveAndLeave() {
  confirmModalVisible.value = false
  await saveAllChanges()
  if (pendingTargetRecord.value) {
    await switchToTemplate(pendingTargetRecord.value)
  }
}

function handleSearch() {
  pagination.current = 1
  fetchData()
}

function onPageChange(page: number) {
  pagination.current = page
  fetchData()
}

function startTitleEdit() {
  titleDraft.value = selectedTemplate.value?.templateName || ''
  editingTitle.value = true
}

function cancelTitleEdit() {
  titleDraft.value = ''
  editingTitle.value = false
}

/** 本地保存模板名称（不入库） */
function saveTitleLocal() {
  const name = (editingTitle.value ? titleDraft.value : selectedTemplate.value?.templateName)?.trim()
  if (!name) {
    message.warning('模板名称不能为空')
    return
  }
  if (!NAME_REGEX.test(name)) {
    message.warning('模板名称仅支持中英文、数字及下划线')
    return
  }
  if (name !== selectedTemplate.value?.templateName) {
    selectedTemplate.value.templateName = name
    markDirty()
  }
  editingTitle.value = false
}

/** 「保存」按钮 → 持久化到数据库 */
async function saveAllChanges() {
  if (!selectedTemplateId.value) return
  saveTitleLoading.value = true
  try {
    const tid = selectedTemplateId.value

    // 1. 保存模板名称 + 获取服务器分区列表（并行）
    const [, serverZonesRes] = await Promise.all([
      selectedTemplate.value?.templateName
        ? updateTemplate(tid, { templateName: selectedTemplate.value.templateName })
        : Promise.resolve(),
      getTemplateZones(tid),
    ])
    const serverZoneIds: number[] = (serverZonesRes.data || []).map((z: any) => z.zoneId)
    const localZoneIds = new Set(zones.value.filter(z => z.zoneId > 0).map(z => z.zoneId))

    // 2. 并行删除服务器有但本地没有的分区
    await Promise.all(
      serverZoneIds
        .filter(sid => !localZoneIds.has(sid))
        .map(sid => deleteTemplateZone(tid, sid)),
    )

    // 3. 逐个处理变动的分区（串行，避免上百并发请求打爆连接池）
    const changedIds = changedZoneIds.value
    for (const zone of zones.value) {
      const isChanged = changedIds.has(zone.zoneId)
      if (zone.zoneId < 0) {
        // 新增分区 → 获取真实 ID → 批量添加物料
        const addRes: any = await addTemplateZone(tid, {
          zoneName: zone.zoneName,
          sortNo: zone.sortNo,
        })
        const realZoneId = addRes.data?.id || addRes.data?.zoneId
        if (!realZoneId) {
          throw new Error(`分区「${zone.zoneName}」创建成功但未能获取ID，请刷新页面重试`)
        }
        zone.zoneId = realZoneId
        if (zone.materials?.length) {
          await Promise.all(zone.materials.map(mat =>
            addZoneMaterial(tid, realZoneId, {
              materialId: mat.materialId,
              materialName: mat.materialName || '',
              spec: mat.spec || '',
              inventoryUnit: mat.inventoryUnit || '',
            }),
          ))
        }
      } else if (serverZoneIds.includes(zone.zoneId)) {
        if (isChanged) {
          const origIds = originalZoneMatIds.value.get(zone.zoneId) || new Set<string>()
          const localMats = zone.materials || []
          const localMatIds = new Set(localMats.map(m => String(m.materialId)))
          const toDelete = [...origIds].filter(id => !localMatIds.has(id))
          const toAdd = localMats.filter(m => !origIds.has(String(m.materialId)))

          // 先更新分区
          await updateTemplateZone(tid, zone.zoneId, {
            zoneName: zone.zoneName,
            sortNo: zone.sortNo,
          })
          // 再批量增删物料（同分区内并行）
          if (toDelete.length || toAdd.length) {
            await Promise.all([
              ...toDelete.map(id => deleteZoneMaterial(tid, zone.zoneId, id)),
              ...toAdd.map(lm => addZoneMaterial(tid, zone.zoneId, {
                materialId: lm.materialId,
                materialName: lm.materialName || '',
                spec: lm.spec || '',
                unit: lm.unit || '',
                inventoryUnit: lm.inventoryUnit || '',
              })),
            ])
          }
        }
        // 未变动的分区完全跳过
      }
    }

    // 4. 只对变动分区保存排序
    const sortPromises: Promise<any>[] = []
    if (changedIds.size > 0) {
      const sortedIds = zones.value.filter(z => z.zoneId > 0).map((z, i) => ({
        zoneId: z.zoneId,
        sortNo: z.sortNo ?? i + 1,
      }))
      if (sortedIds.length > 0) {
        sortPromises.push(updateZoneSort(tid, sortedIds).catch(() => {}))
      }
    }
    for (const zone of zones.value) {
      if (zone.zoneId > 0 && zone.materials?.length && changedIds.has(zone.zoneId)) {
        sortPromises.push(
          updateZoneMaterialSort(tid, zone.zoneId,
            zone.materials.map((m, i) => ({ materialId: m.materialId, sortNo: i + 1 })),
          ).catch(() => {}),
        )
      }
    }
    await Promise.all(sortPromises)

    dirty.value = false
    changedZoneIds.value = new Set()

    // 5. 并发刷新列表和分区
    await Promise.all([
      fetchData().catch(() => {}),
      loadZonesWithMaterials().catch(() => {}),
    ])

    message.success('模板保存成功')
    // 如果模板当前非启用状态，询问是否启用（默认不启用，避免误操作）
    if (selectedTemplate.value?.status !== 1) {
      await new Promise<void>((resolve) => {
        Modal.confirm({
          title: '保存成功',
          content: '模板已保存，是否立即启用该模板？启用后模板不可编辑。',
          okText: '启用',
          cancelText: '暂不启用',
          okButtonProps: { type: 'default' },
          cancelButtonProps: { type: 'primary' },
          onOk: async () => {
            await handleEnable()
            resolve()
          },
          onCancel: () => {
            resolve()
          },
        })
      })
    }
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    saveTitleLoading.value = false
  }
}

async function handleDisable() {
  if (!selectedTemplateId.value) return
  publishLoading.value = true
  try {
    await setTemplateStatus(selectedTemplateId.value, 0)
    message.success('模板已停用')
    const detailRes: any = await getTemplateDetail(selectedTemplateId.value)
    selectedTemplate.value = detailRes.data
    await fetchData()
  } catch (e: any) {
    message.error(e.message || '操作失败')
  } finally {
    publishLoading.value = false
  }
}

async function handleEnable() {
  if (!selectedTemplateId.value) return
  publishLoading.value = true
  try {
    await setTemplateStatus(selectedTemplateId.value, 1)
    message.success('模板已启用')
    const detailRes: any = await getTemplateDetail(selectedTemplateId.value)
    selectedTemplate.value = detailRes.data
    await fetchData()
  } catch (e: any) {
    message.error(e.message || '操作失败')
  } finally {
    publishLoading.value = false
  }
}

async function handleDeleteTemplate() {
  if (!selectedTemplateId.value) return
  deleteLoading.value = true
  try {
    await deleteTemplate(selectedTemplateId.value)
    message.success('模板已删除')
    selectedTemplateId.value = null
    selectedTemplate.value = null
    zones.value = []
    dirty.value = false
    await fetchData()
  } catch (e: any) {
    message.error(e.message || '删除失败')
  } finally {
    deleteLoading.value = false
  }
}

function showAddModal() {
  templateForm.templateName = ''
  nameValidateStatus.value = ''
  templateModalVisible.value = true
}

function resetTemplateModal() {
  templateForm.templateName = ''
  nameValidateStatus.value = ''
}

function onTemplateNameInput() {
  if (nameValidateStatus.value === 'error') {
    validateTemplateName()
  }
}

let validateTimer: ReturnType<typeof setTimeout> | null = null

const NAME_REGEX = /^[一-龥a-zA-Z0-9_]+$/

function validateTemplateName() {
  const name = templateForm.templateName.trim()
  if (!name) {
    nameValidateStatus.value = ''
    return
  }
  if (!NAME_REGEX.test(name)) {
    nameValidateStatus.value = 'error'
    return
  }
  if (validateTimer) clearTimeout(validateTimer)
  validateTimer = setTimeout(async () => {
    try {
      const res: any = await getTemplates({ keyword: name, pageSize: 1 })
      const list = res.data?.records || []
      const dup = list.some((t: any) => t.templateName === name)
      nameValidateStatus.value = dup ? 'error' : 'success'
    } catch {
      nameValidateStatus.value = ''
    }
  }, 300)
}

async function handleTemplateSubmit() {
  const name = templateForm.templateName.trim()
  if (!name) {
    message.warning('请输入模板名称')
    return
  }
  if (!NAME_REGEX.test(name)) {
    message.warning('模板名称仅支持中英文、数字及下划线')
    return
  }

  templateSubmitLoading.value = true
  try {
    const res: any = await getTemplates({ keyword: name, pageSize: 1 })
    const list = res.data?.records || []
    const dup = list.some((t: any) => t.templateName === name)
    if (dup) {
      nameValidateStatus.value = 'error'
      message.warning('模板名称已存在，请使用其他名称')
      return
    }
    await addTemplate({ templateName: name })
    message.success('创建成功')
    templateModalVisible.value = false
    resetTemplateModal()
    await fetchData()
  } catch (e: any) {
    message.error(e.message || '创建失败')
  } finally {
    templateSubmitLoading.value = false
  }
}

/** 切换分区折叠/展开 */
function toggleCollapse(zoneId: number) {
  const next = new Set(collapsedZoneIds.value)
  if (next.has(zoneId)) {
    next.delete(zoneId)
  } else {
    next.add(zoneId)
  }
  collapsedZoneIds.value = next
}

/** 搜索物料后定位：展开对应分区、滚动到物料、高亮闪烁 */
function locateMaterial(result: { zoneId: number; materialId: string }) {
  // 展开该分区
  const next = new Set(collapsedZoneIds.value)
  next.delete(result.zoneId)
  collapsedZoneIds.value = next

  // 高亮物料
  highlightMatId.value = result.materialId

  // 滚动到该物料元素
  setTimeout(() => {
    const el = document.querySelector(`[data-mat-id="${result.materialId}"]`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  }, 150)

  // 2 秒后取消高亮
  setTimeout(() => {
    highlightMatId.value = null
  }, 2500)

  // 清除搜索
  materialLocateKeyword.value = ''
  materialLocateFocused.value = false
}

/** 延迟失焦，让 mousedown 先触发 */
function handleLocateBlur() {
  setTimeout(() => {
    materialLocateFocused.value = false
  }, 200)
}

// ========== 分区操作（本地缓存，不入库） ==========

function showAddZoneModal() {
  editingZoneId.value = null
  zoneForm.zoneName = ''
  zoneForm.sortNo = undefined
  zoneModalVisible.value = true
}

function showEditZoneModal(zone: ZoneItem) {
  editingZoneId.value = zone.zoneId
  zoneForm.zoneName = zone.zoneName
  zoneForm.sortNo = zone.sortNo ?? undefined
  zoneModalVisible.value = true
}

function confirmDeleteZone(zone: ZoneItem) {
  Modal.confirm({
    title: '确认删除此分区？',
    content: '分区内物料将被同步移除。',
    okText: '删除',
    okType: 'danger',
    onOk: () => handleDeleteZoneLocal(zone),
  })
}

async function handleZoneSubmit() {
  try {
    await zoneFormRef.value?.validate()
    zoneSubmitLoading.value = true

    if (editingZoneId.value) {
      // 编辑已有分区
      const zone = zones.value.find(z => z.zoneId === editingZoneId.value)
      if (zone) {
        zone.zoneName = zoneForm.zoneName
        zone.sortNo = zoneForm.sortNo
        markZoneDirty(zone.zoneId)
      }
    } else {
      // 新增分区（临时 ID）
      const newZone: ZoneItem = {
        zoneId: tempZoneIdCounter.value--,
        zoneName: zoneForm.zoneName,
        sortNo: zoneForm.sortNo,
        materials: [],
      }
      zones.value.push(newZone)
      markZoneDirty(newZone.zoneId)
    }
    zoneModalVisible.value = false
    resetZoneForm()
  } catch (e: any) {
    if (e.errorFields) return
    message.error(e.message || '操作失败')
  } finally {
    zoneSubmitLoading.value = false
  }
}

/** 本地删除分区 */
function handleDeleteZoneLocal(zone: ZoneItem) {
  zones.value = zones.value.filter(z => z.zoneId !== zone.zoneId)
  markZoneDirty(zone.zoneId)
}

function resetZoneForm() {
  zoneForm.zoneName = ''
  zoneForm.sortNo = undefined
  editingZoneId.value = null
  zoneFormRef.value?.clearValidate()
}

// ========== 物料操作（本地缓存，不入库） ==========

function showAddMaterialModal(zone: ZoneItem) {
  currentMaterialZoneId.value = zone.zoneId
  currentZoneName.value = zone.zoneName
  currentZoneMaterials.value = zone.materials || []
  materialKeyword.value = ''
  activeChip.value = 'all'
  showAllChips.value = false
  materialModalVisible.value = true
  fetchAllMaterials()
}

async function fetchAllMaterials() {
  searchMaterialLoading.value = true
  try {
    const res: any = await api.get('/materials', { params: { keyword: '', pageSize: 9999 } })
    const list: MaterialItem[] = res.data?.records || res.data || []
    const existingIds = new Set(currentZoneMaterials.value.map((m) => String(m.materialId)))
    allMaterials.value = list.map((item) => ({
      ...item,
      materialId: String(item.materialId),
      _added: existingIds.has(String(item.materialId)),
    }))
  } catch (e: any) {
    message.error(e.message || '拉取物料失败')
  } finally {
    searchMaterialLoading.value = false
  }
}

function searchMaterials() {
  // 本地过滤，computed 自动响应
}

/** 本地添加物料到分区 */
function handleAddMaterialLocal(item: MaterialItem) {
  if (currentMaterialZoneId.value === null) return
  const zone = zones.value.find(z => z.zoneId === currentMaterialZoneId.value)
  if (!zone) return
  if (!zone.materials) zone.materials = []
  const alreadyExists = zone.materials.some(m => String(m.materialId) === String(item.materialId))
  if (alreadyExists) {
    message.warning('该物料已在当前分区中')
    return
  }
  zone.materials.push({
    materialId: String(item.materialId),
    materialName: item.materialName,
    spec: item.spec,
    leibie: item.leibie,
    category: item.category,
    inventoryUnit: item.inventoryUnit,
    unit: item.unit,
  })
  item._added = true
  zone.materialCount = (zone.materialCount ?? 0) + 1
  markZoneDirty(zone.zoneId)
}

/** 本地从分区移除物料 */
function handleRemoveMaterialLocal(zone: ZoneItem, material: ZoneMaterial) {
  const z = zones.value.find(z => z.zoneId === zone.zoneId)
  if (!z || !z.materials) return
  z.materials = z.materials.filter(m => String(m.materialId) !== String(material.materialId))
  z.materialCount = Math.max((z.materialCount ?? 0) - 1, 0)
  markZoneDirty(zone.zoneId)
}

function resetMaterialSearch() {
  materialKeyword.value = ''
  allMaterials.value = []
  activeChip.value = 'all'
  showAllChips.value = false
  currentMaterialZoneId.value = null
  currentZoneName.value = ''
}

onMounted(fetchData)
</script>

<style scoped>
.template-page {
  max-width: 1400px;
}

.template-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.template-sidebar {
  flex: 0 0 300px;
  background: #fff;
  border-radius: var(--radius, 8px);
  box-shadow: var(--card-shadow);
  padding: 16px;
  max-height: calc(100vh - 160px);
  overflow-y: auto;
}

.btn-new-template {
  margin-bottom: 12px;
  height: 40px;
  font-weight: 500;
}

.template-search {
  margin-bottom: 12px;
}

.template-card {
  padding: 14px 14px 14px 16px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  position: relative;
}

.template-card:hover {
  border-color: #b7dfc9;
}

.template-card.selected {
  border-color: var(--primary, #0d7a3d);
  border-left-width: 4px;
  background: var(--primary-bg, #e8f5e9);
  box-shadow: 0 2px 8px rgba(13, 122, 61, 0.12);
}

.template-card-name {
  font-weight: 600;
  font-size: 14px;
  color: #1f2937;
  margin-bottom: 6px;
  padding-right: 72px;
}

.template-card-meta {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 8px;
}

.template-card :deep(.ant-tag) {
  position: absolute;
  top: 14px;
  right: 12px;
}

.list-empty {
  padding: 24px 0;
}

.list-pagination {
  margin-top: 12px;
  text-align: center;
}

.template-editor {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: var(--radius, 8px);
  box-shadow: var(--card-shadow);
  padding: 24px;
  min-height: 520px;
}

.editor-placeholder {
  text-align: center;
  padding: 80px 20px;
  color: #9ca3af;
}

.placeholder-icon {
  font-size: 48px;
  color: #d9d9d9;
  margin-bottom: 16px;
}

.editor-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.editor-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.editor-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}

.title-edit-icon {
  color: #9ca3af;
  cursor: pointer;
  font-size: 16px;
}

.title-edit-icon:hover {
  color: var(--primary, #0d7a3d);
}

.title-input {
  max-width: 360px;
}

.editor-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

.enabled-warning {
  margin-top: 8px;
  margin-bottom: -8px;
  font-size: 12px;
  color: #d4a017;
  text-align: right;
}

.editor-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.zone-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  margin-bottom: 16px;
  overflow: hidden;
  cursor: grab;
}

.zone-card--dragging {
  opacity: 0.4;
}

.zone-card--dragover {
  border-color: var(--primary, #0d7a3d);
  box-shadow: 0 2px 8px rgba(13, 122, 61, 0.15);
}

.zone-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.zone-collapse-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  font-size: 11px;
  color: #9ca3af;
  cursor: pointer;
  border-radius: 4px;
  transition: transform 0.25s ease, color 0.2s;
  user-select: none;
}

.zone-collapse-icon:hover {
  color: #0d7a3d;
  background: rgba(13, 122, 61, 0.06);
}

.zone-collapse-icon.collapsed {
  transform: rotate(0deg);
}

/* 展开状态：箭头旋转 90° */
.zone-collapse-icon:not(.collapsed) {
  transform: rotate(90deg);
}

.drag-icon {
  color: #d1d5db;
  cursor: grab;
}

.zone-card-title {
  font-weight: 600;
  font-size: 15px;
}

.zone-count-tag {
  margin: 0;
}

.zone-add-material {
  margin-left: auto;
  flex-shrink: 0;
  background: rgba(13, 122, 61, 0.1);
  border-color: rgba(13, 122, 61, 0.2);
  color: #0D7A3D;
}
.zone-add-material:hover {
  border-color: #0D7A3D;
  color: #0D7A3D;
}

.zone-more {
  font-size: 18px;
  color: #9ca3af;
  cursor: pointer;
  padding: 4px;
}

.zone-material-list {
  padding: 4px 0;
}

.material-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
}

.material-item:last-child {
  border-bottom: none;
}

.material-drag {
  font-size: 12px;
}

.material-icon-wrap {
  width: 40px;
  height: 40px;
  background: #f3f4f6;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 18px;
}

.material-info {
  flex: 1;
  min-width: 0;
}

.material-name {
  font-weight: 500;
  font-size: 14px;
  color: #1f2937;
  line-height: 1.4;
}

.material-spec-line {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
  line-height: 1.4;
}

.material-inventory-unit {
  font-size: 12px;
  color: #9ca3af;
  flex-shrink: 0;
  white-space: nowrap;
  margin-left: auto;
}

.material-delete {
  flex-shrink: 0;
  color: #ef4444;
  cursor: pointer;
  font-size: 16px;
  padding: 8px;
}

.material-delete:hover {
  color: #dc2626;
}

.material-item--dragging {
  opacity: 0.7;
  background: #f0faf4;
}

.material-item--dragover {
  border-top: 2px solid var(--primary, #0d7a3d);
}

.zone-empty {
  padding: 24px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.btn-new-zone:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.btn-new-zone {
  width: 100%;
  margin-top: 8px;
  padding: 14px;
  border: 2px dashed #d1d5db;
  border-radius: 8px;
  background: transparent;
  color: #6b7280;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: border-color 0.2s, color 0.2s;
}

.btn-new-zone:hover {
  border-color: var(--primary, #0d7a3d);
  color: var(--primary, #0d7a3d);
}

.modal-field {
  margin-bottom: 24px;
}

.modal-label {
  display: block;
  font-weight: 500;
  margin-bottom: 8px;
}

.modal-label.error {
  color: #ef4444;
}

.field-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #9ca3af;
}

.field-hint.success {
  color: #22c55e;
}

.field-hint.error {
  color: #ef4444;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.zone-hint {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  color: #047857;
  font-size: 13px;
  line-height: 1.5;
  margin-top: 8px;
}

.current-zone-tag {
  margin-bottom: 12px;
}

.material-search {
  margin-bottom: 12px;
}

.material-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.chip {
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 20px;
  padding: 4px 14px;
  font-size: 13px;
  cursor: pointer;
  color: #4b5563;
}

.chip.active {
  background: var(--primary, #0d7a3d);
  border-color: var(--primary, #0d7a3d);
  color: #fff;
}

.chip-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #6b7280;
  cursor: pointer;
  font-size: 12px;
}

.chip-expand {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #1677ff;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
}
.chip-expand:hover {
  color: #4096ff;
}

.chip-toggle:hover {
  color: var(--primary, #0d7a3d);
}

.material-picker-list {
  max-height: 360px;
  overflow-y: auto;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  margin-bottom: 10px;
}

.picker-icon {
  width: 44px;
  height: 44px;
  background: #f3f4f6;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 20px;
}

.picker-info {
  flex: 1;
  min-width: 0;
}

.picker-name {
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.picker-type-tag {
  display: inline-block;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 400;
  line-height: 18px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 4px;
}

.picker-spec,
.picker-unit {
  font-size: 12px;
  color: #9ca3af;
}

.spec-tag {
  display: inline-block;
  background: #f0f0f0;
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 12px;
  color: #666;
}

.picker-added {
  font-size: 12px;
  color: #9ca3af;
  white-space: nowrap;
}

/* ========== 物料搜索定位 ========== */

.editor-search-row {
  position: relative;
  margin-bottom: 16px;
}

.material-locate-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.material-locate-search.focused {
  border-color: #0d7a3d;
  box-shadow: 0 0 0 2px rgba(13, 122, 61, 0.1);
}

.locate-search-icon {
  font-size: 15px;
  color: #9ca3af;
  flex-shrink: 0;
}

.locate-search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  color: #1f2937;
  background: transparent;
}

.locate-search-input::placeholder {
  color: #c0c8c2;
}

.locate-clear-icon {
  font-size: 14px;
  color: #9ca3af;
  cursor: pointer;
  flex-shrink: 0;
}

.locate-clear-icon:hover {
  color: #6b7280;
}

/* 搜索结果下拉 */
.locate-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  max-height: 300px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  z-index: 100;
}

.locate-dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.locate-dropdown-item:hover {
  background-color: #f0faf4;
}

.locate-dropdown-item + .locate-dropdown-item {
  border-top: 1px solid #f5f5f5;
}

.locate-mat-name {
  font-weight: 500;
  font-size: 13px;
  color: #1f2937;
}

.locate-mat-spec {
  font-size: 12px;
  color: #9ca3af;
  flex: 1;
}

.locate-zone-tag {
  flex-shrink: 0;
}

.locate-dropdown-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}

/* 物料高亮闪烁 */
.material-item--highlight {
  animation: material-highlight-pulse 0.6s ease-in-out 3;
  background: #ecfdf5 !important;
  border-radius: 6px;
}

@keyframes material-highlight-pulse {
  0%, 100% { background: #ecfdf5; }
  50% { background: #a7f3d0; }
}

.material-modal-footer {
  margin-top: 16px;
  text-align: right;
}
</style>
