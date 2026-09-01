<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createExpenseBatch, updateExpense, fetchExpenseDetail, fetchExpenseItems, fetchExpenseTypes, uploadVoucher } from '@/api/expense'
import { fetchMaterialsByCategory } from '@/api/material'
import { useUserStore } from '@/store/user'

const SELF_PURCHASE = '自购食材'
const MATERIAL_PARENT_CATEGORY = '自购食材成本'
const MAX_GROUPS = 10
const MAX_ITEMS = 10
const MAX_VOUCHERS = 9

const userStore = useUserStore()
const isEdit = ref(false)
const editId = ref('')
const types = ref<any[]>([])
const occurredDate = ref(new Date().toISOString().slice(0, 10))
const description = ref('')
const handlerName = ref(userStore.employeeName || '')
const saving = ref(false)

// 类型组：每组一个支出类型 + 该组明细/金额；整单共享日期/说明/凭证
interface MatItem {
  materialId: string
  materialName: string
  parentCategory: string
  category: string
  weight: string
  unitPrice: string
}
interface PlainItem {
  name: string
  amount: string
}
interface TypeGroup {
  typeId: string
  typeName: string
  typeDesc: string
  items: MatItem[]        // 自购食材：物料明细
  plainItems: PlainItem[] // 其他类型：名称+金额明细
  manualAmount: string    // 无明细时手填金额
}
const groups = ref<TypeGroup[]>([])

function newGroup(): TypeGroup {
  return { typeId: '', typeName: '请选择支出类型', typeDesc: '', items: [], plainItems: [], manualAmount: '' }
}
function round2(n: number) { return Math.round(n * 100) / 100 }
function itemAmount(it: MatItem): number {
  const w = parseFloat(it.weight)
  const p = parseFloat(it.unitPrice)
  if (!(w > 0) || !(p > 0)) return 0
  return round2(w * p)
}
function plainItemAmount(it: PlainItem): number {
  const a = parseFloat(it.amount)
  return a > 0 ? round2(a) : 0
}
/** 组金额：自购=Σ物料；其他=Σ明细或手填金额 */
function groupAmount(g: TypeGroup): number {
  if (g.typeName === SELF_PURCHASE) {
    return round2(g.items.reduce((s, it) => s + itemAmount(it), 0))
  }
  if (g.plainItems.length) {
    return round2(g.plainItems.reduce((s, it) => s + plainItemAmount(it), 0))
  }
  const m = parseFloat(g.manualAmount)
  return m > 0 ? round2(m) : 0
}
const totalAmount = computed(() => round2(groups.value.reduce((s, g) => s + groupAmount(g), 0)))

// 凭证（多张，最多9张）
const vouchers = ref<string[]>([])
function isRemoteUrl(u: string) {
  return u.startsWith('http') && !u.includes('://tmp/') && !u.includes('__tmp__') && !u.includes('127.0.0.1') && !u.includes('localhost')
}
function chooseVouchers() {
  const rest = MAX_VOUCHERS - vouchers.value.length
  if (rest <= 0) { uni.showToast({ title: `最多上传${MAX_VOUCHERS}张凭证`, icon: 'none' }); return }
  uni.chooseImage({
    count: rest,
    success: (res: any) => { vouchers.value.push(...res.tempFilePaths) },
    fail: (err: any) => { uni.showToast({ title: err.errMsg || '失败', icon: 'none', duration: 3000 }) }
  })
}
function removeVoucher(idx: number) { vouchers.value.splice(idx, 1) }

onLoad(async (q: any) => {
  types.value = await fetchExpenseTypes()
  if (q?.expenseId) {
    isEdit.value = true
    editId.value = q.expenseId
    const d = await fetchExpenseDetail(q.expenseId) as any
    if (d) {
      const g = newGroup()
      g.typeId = d.typeId || ''
      g.typeName = d.typeName || '请选择支出类型'
      // 兜底：如果 typeId 为空但 typeName 能匹配，自动补齐
      if (!g.typeId && g.typeName !== '请选择支出类型') {
        const matched = types.value.find((t: any) => t.name === g.typeName)
        if (matched) g.typeId = matched.typeId
      }
      const matched = types.value.find((t: any) => t.typeId === g.typeId)
      if (matched) g.typeDesc = matched.description || ''
      description.value = d.remark || ''
      occurredDate.value = d.occurredDate || ''
      handlerName.value = d.handlerName || ''
      // 多张凭证回填：子表优先，兜底主表单值
      const urls = (d.voucherUrls && d.voucherUrls.length) ? d.voucherUrls : (d.voucherUrl ? [d.voucherUrl] : [])
      vouchers.value = (Array.isArray(urls) ? urls : []).filter((u: string) => isRemoteUrl(u))
      // 回填明细：自购食材=物料型，其他类型=名称+金额；无明细则手填金额
      try {
        const its = await fetchExpenseItems(q.expenseId)
        if (Array.isArray(its) && its.length) {
          if (d.typeName === SELF_PURCHASE) {
            g.items = its.map((mat: any) => ({
              materialId: mat.materialId || '',
              materialName: mat.name,
              parentCategory: mat.parentCategory || '',
              category: mat.category || '',
              weight: mat.qty != null ? String(mat.qty) : '',
              unitPrice: mat.unitPrice != null ? String(mat.unitPrice) : '',
            }))
          } else {
            g.plainItems = its.map((it: any) => ({
              name: it.name || '',
              amount: it.amount != null ? String(it.amount) : '',
            }))
          }
        } else if (d.typeName !== SELF_PURCHASE) {
          g.manualAmount = String(d.amount != null ? d.amount : '')
        }
      } catch { /* ignore */ }
      groups.value = [g]
    }
  } else {
    groups.value = [newGroup()]
  }
})

// ---- 类型组操作 ----
const showTypes = ref(false)
const activeGroupIdx = ref(0)
function openTypePicker(idx: number) {
  if (isEdit.value && idx !== 0) return
  activeGroupIdx.value = idx
  showTypes.value = true
}
function selectType(item: any) {
  const g = groups.value[activeGroupIdx.value]
  g.typeId = item.typeId
  g.typeName = item.name
  g.typeDesc = item.description || ''
  if (item.name === SELF_PURCHASE) { g.plainItems = []; g.manualAmount = '' } else { g.items = []; g.manualAmount = '' }
  showTypes.value = false
}
function addGroup() {
  if (groups.value.length >= MAX_GROUPS) { uni.showToast({ title: `最多添加${MAX_GROUPS}个支出类型`, icon: 'none' }); return }
  groups.value.push(newGroup())
}
function removeGroup(idx: number) {
  if (isEdit.value) return
  if (groups.value.length <= 1) { uni.showToast({ title: '至少保留一个支出类型', icon: 'none' }); return }
  groups.value.splice(idx, 1)
}

// ---- 自购食材物料：添加/编辑抽屉 ----
const showMatSearch = ref(false)
const matSearchKey = ref('')
const matSearchFocus = ref(false)
const matResults = ref<any[]>([])
const matLoading = ref(false)
const matGroupIdx = ref(0)
let matTimer: any = null

const showDrawer = ref(false)
const editingIndex = ref(-1)
const drawerItem = ref<MatItem | null>(null)
const drawerWeight = ref('')
const drawerUnitPrice = ref('')
const drawerAmount = computed(() => {
  const w = parseFloat(drawerWeight.value)
  const p = parseFloat(drawerUnitPrice.value)
  if (!(w > 0) || !(p > 0)) return ''
  return (w * p).toFixed(2)
})

function openMatSearch(idx: number) {
  matGroupIdx.value = idx
  showMatSearch.value = true; matSearchKey.value = ''; matResults.value = []; matSearchFocus.value = true
  doMatSearch()
}
function closeMatSearch() { showMatSearch.value = false; matSearchFocus.value = false }
function onMatSearchInput() { if (matTimer) clearTimeout(matTimer); matTimer = setTimeout(() => doMatSearch(), 150) }
async function doMatSearch() {
  matLoading.value = true
  try { matResults.value = await fetchMaterialsByCategory(MATERIAL_PARENT_CATEGORY, matSearchKey.value) }
  finally { matLoading.value = false }
}
function openAddDrawer(m: any) {
  const g = groups.value[matGroupIdx.value]
  if (editingIndex.value === -1 && g.items.some(i => i.materialId && i.materialId === (m.id || m.materialId))) {
    uni.showToast({ title: '该物料已添加', icon: 'none' }); return
  }
  drawerItem.value = {
    materialId: m.id || m.materialId || '',
    materialName: matDisplayName(m),
    parentCategory: m.leibie || m.parentCategory || '',
    category: m.leibie2 || m.category || '',
    weight: '', unitPrice: '',
  }
  drawerWeight.value = ''; drawerUnitPrice.value = ''
  editingIndex.value = -1
  showMatSearch.value = false; showDrawer.value = true
}
function selectOther() {
  drawerItem.value = { materialId: '', materialName: '其他', parentCategory: MATERIAL_PARENT_CATEGORY, category: SELF_PURCHASE, weight: '', unitPrice: '' }
  drawerWeight.value = ''; drawerUnitPrice.value = ''
  editingIndex.value = -1
  showMatSearch.value = false; showDrawer.value = true
}
function openEditDrawer(idx: number) {
  const g = groups.value[matGroupIdx.value]
  const it = g.items[idx]
  drawerItem.value = { ...it }
  drawerWeight.value = it.weight; drawerUnitPrice.value = it.unitPrice
  editingIndex.value = idx
  showDrawer.value = true
}
function closeDrawer() { showDrawer.value = false; drawerItem.value = null; editingIndex.value = -1 }
function confirmDrawer() {
  if (!(parseFloat(drawerWeight.value) > 0)) { uni.showToast({ title: '请填写重量', icon: 'none' }); return }
  if (!(parseFloat(drawerUnitPrice.value) > 0)) { uni.showToast({ title: '请填写单价', icon: 'none' }); return }
  const item: MatItem = { ...drawerItem.value!, weight: drawerWeight.value, unitPrice: drawerUnitPrice.value }
  const g = groups.value[matGroupIdx.value]
  if (editingIndex.value >= 0) { g.items[editingIndex.value] = item } else { g.items.push(item) }
  closeDrawer()
}
function removeItem(idx: number) { groups.value[matGroupIdx.value].items.splice(idx, 1) }

// ---- 其他类型明细：添加/编辑抽屉 ----
const showPlainDrawer = ref(false)
const pEditingIndex = ref(-1)
const pGroupIdx = ref(0)
const pDrawerName = ref('')
const pDrawerAmount = ref('')
const pDrawerTotal = computed(() => {
  const a = parseFloat(pDrawerAmount.value)
  return a > 0 ? a.toFixed(2) : ''
})

function openPlainAddDrawer(idx: number) {
  pGroupIdx.value = idx
  pDrawerName.value = ''; pDrawerAmount.value = ''; pEditingIndex.value = -1; showPlainDrawer.value = true
}
function openPlainEditDrawer(idx: number, pIdx: number) {
  pGroupIdx.value = idx
  const it = groups.value[idx].plainItems[pIdx]
  pDrawerName.value = it.name; pDrawerAmount.value = it.amount; pEditingIndex.value = pIdx; showPlainDrawer.value = true
}
function closePlainDrawer() { showPlainDrawer.value = false; pEditingIndex.value = -1 }
function confirmPlainDrawer() {
  const name = pDrawerName.value.trim()
  if (!name) { uni.showToast({ title: '请填写明细名称', icon: 'none' }); return }
  if (!(parseFloat(pDrawerAmount.value) > 0)) { uni.showToast({ title: '请填写明细金额', icon: 'none' }); return }
  const item: PlainItem = { name, amount: pDrawerAmount.value }
  const g = groups.value[pGroupIdx.value]
  if (pEditingIndex.value >= 0) { g.plainItems[pEditingIndex.value] = item } else { g.plainItems.push(item) }
  closePlainDrawer()
}
function removePlainItem(idx: number, pIdx: number) { groups.value[idx].plainItems.splice(pIdx, 1) }

function onDateChange(e: any) { occurredDate.value = e.detail.value }

async function submit() {
  if (saving.value) return
  const filled = groups.value.filter(g => g.typeId)
  if (!filled.length) { uni.showToast({ title: '请至少选择一个支出类型', icon: 'none' }); return }
  if (isEdit.value && filled.length > 1) { uni.showToast({ title: '编辑模式仅支持一个支出类型', icon: 'none' }); return }
  // 每组校验
  for (const g of filled) {
    if (g.typeName === SELF_PURCHASE) {
      if (!g.items.length) { uni.showToast({ title: '请添加物料', icon: 'none' }); return }
      if (g.items.length > MAX_ITEMS) { uni.showToast({ title: `最多添加${MAX_ITEMS}种物料`, icon: 'none' }); return }
      for (const it of g.items) {
        if (!(parseFloat(it.weight) > 0)) { uni.showToast({ title: '请填写物料重量', icon: 'none' }); return }
        if (!(parseFloat(it.unitPrice) > 0)) { uni.showToast({ title: '请填写物料单价', icon: 'none' }); return }
      }
    } else if (g.plainItems.length) {
      if (g.plainItems.length > MAX_ITEMS) { uni.showToast({ title: `最多添加${MAX_ITEMS}项明细`, icon: 'none' }); return }
      for (const it of g.plainItems) {
        if (!it.name.trim()) { uni.showToast({ title: '请填写明细名称', icon: 'none' }); return }
        if (!(parseFloat(it.amount) > 0)) { uni.showToast({ title: '请填写明细金额', icon: 'none' }); return }
      }
    } else {
      if (!(parseFloat(g.manualAmount) > 0)) { uni.showToast({ title: `请填写【${g.typeName}】金额`, icon: 'none' }); return }
    }
  }
  saving.value = true
  try {
    // 上传所有本地凭证
    const urls: string[] = []
    for (const v of vouchers.value) {
      if (isRemoteUrl(v)) { urls.push(v); continue }
      try { const res = await uploadVoucher(v); urls.push(res.url) }
      catch { uni.showToast({ title: '凭证上传失败', icon: 'none' }); saving.value = false; return }
    }
    if (isEdit.value) {
      const g = filled[0]
      const data: Record<string, any> = {
        typeId: g.typeId, amount: groupAmount(g), occurredDate: occurredDate.value,
        handlerName: handlerName.value || '--', voucherUrls: urls, remark: description.value || undefined
      }
      if (g.typeName === SELF_PURCHASE) {
        data.items = g.items.map(it => ({
          materialId: it.materialId || undefined,
          materialName: it.materialName,
          parentCategory: it.parentCategory || undefined,
          category: it.category || undefined,
          weight: parseFloat(it.weight),
          unitPrice: parseFloat(it.unitPrice),
        }))
      } else if (g.plainItems.length) {
        data.amountItems = g.plainItems.map(it => ({ name: it.name.trim(), amount: parseFloat(it.amount) }))
      }
      await updateExpense(editId.value, data)
    } else {
      await createExpenseBatch({
        occurredDate: occurredDate.value,
        handlerName: handlerName.value || '--',
        voucherUrls: urls,
        remark: description.value || undefined,
        records: filled.map(g => {
          const rec: Record<string, any> = { typeId: g.typeId, amount: groupAmount(g) }
          if (g.typeName === SELF_PURCHASE) {
            rec.items = g.items.map(it => ({
              materialId: it.materialId || undefined,
              materialName: it.materialName,
              parentCategory: it.parentCategory || undefined,
              category: it.category || undefined,
              weight: parseFloat(it.weight),
              unitPrice: parseFloat(it.unitPrice),
            }))
          } else if (g.plainItems.length) {
            rec.amountItems = g.plainItems.map(it => ({ name: it.name.trim(), amount: parseFloat(it.amount) }))
          }
          return rec
        })
      })
    }
    uni.showToast({ title: isEdit.value ? '修改成功' : '登记成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } finally { saving.value = false }
}

function matDisplayName(item: any) { return item.yuancailiaomingcheng || item.materialName || '' }
function matDisplayCategory(item: any) { return item.leibie2 || item.category || '' }
</script>

<template>
  <view class="page">
    <!-- 编辑模式提示 -->
    <view v-if="isEdit" class="sp-tip edit-tip">
      <text class="sp-tip-icon">✏️</text>
      <text class="sp-tip-text">编辑模式：仅支持修改一个支出类型</text>
    </view>

    <!-- 自购食材提示 -->
    <view v-if="groups.some(g => g.typeName === SELF_PURCHASE)" class="sp-tip">
      <text class="sp-tip-icon">⚠️</text>
      <text class="sp-tip-text">请仅填写门店自行购买的物料，已通过企迈采购的物料无需登记</text>
    </view>

    <!-- 类型组卡片（每组一个支出类型） -->
    <view v-for="(g, idx) in groups" :key="idx" class="card">
      <view class="group-head">
        <view class="field group-type-field" @click="openTypePicker(idx)">
          <view class="field-text">
            <text class="field-label">类型 {{ idx + 1 }} <text class="required">*</text></text>
            <text class="field-value" :class="{placeholder:!g.typeId}">{{ g.typeName }}</text>
            <text v-if="g.typeDesc" class="group-desc">{{ g.typeDesc }}</text>
          </view>
          <text class="field-arrow">›</text>
        </view>
        <view v-if="!isEdit && groups.length > 1" class="group-del" @click="removeGroup(idx)">✕</view>
      </view>

      <!-- 自购食材物料明细 -->
      <view v-if="g.typeName === SELF_PURCHASE" class="mat-section">
        <view class="mat-title">
          <text>📦 物料明细</text>
          <text class="mat-count">{{ g.items.length }}/{{ MAX_ITEMS }}</text>
        </view>
        <view v-if="g.items.length === 0" class="mat-empty">请添加物料</view>
        <view v-for="(it, i) in g.items" :key="i" class="mat-item" @click="openEditDrawer(i)">
          <view class="mi-left">
            <text class="mi-name">{{ it.materialName }}</text>
            <text v-if="it.category" class="mi-meta">{{ it.category }}</text>
          </view>
          <view class="mi-right">
            <text class="mi-amount">¥{{ itemAmount(it).toFixed(2) }}</text>
            <text class="mi-meta">{{ it.weight }}kg × ¥{{ it.unitPrice }}</text>
          </view>
          <text class="mi-del" @click.stop="removeItem(i)">✕</text>
        </view>
        <view v-if="g.items.length < MAX_ITEMS" class="add-material-btn" @click="openMatSearch(idx)">
          <text class="add-icon">＋</text><text class="add-text">添加物料（{{ g.items.length }}/{{ MAX_ITEMS }}）</text>
        </view>
        <view v-else class="add-material-hint">最多添加 {{ MAX_ITEMS }} 种物料</view>
      </view>

      <!-- 其他类型支出明细 -->
      <view v-else-if="g.typeId" class="mat-section">
        <view class="mat-title">
          <text>💰 支出明细</text>
          <text class="mat-count">{{ g.plainItems.length }}/{{ MAX_ITEMS }}</text>
        </view>
        <view v-if="g.plainItems.length === 0" class="mat-empty">可添加明细，或直接填写下方金额</view>
        <view v-for="(it, i) in g.plainItems" :key="i" class="mat-item" @click="openPlainEditDrawer(idx, i)">
          <view class="mi-left">
            <text class="mi-name">{{ it.name }}</text>
          </view>
          <view class="mi-right">
            <text class="mi-amount">¥{{ plainItemAmount(it).toFixed(2) }}</text>
          </view>
          <text class="mi-del" @click.stop="removePlainItem(idx, i)">✕</text>
        </view>
        <view v-if="g.plainItems.length < MAX_ITEMS" class="add-material-btn" @click="openPlainAddDrawer(idx)">
          <text class="add-icon">＋</text><text class="add-text">添加明细（{{ g.plainItems.length }}/{{ MAX_ITEMS }}）</text>
        </view>
        <view v-else class="add-material-hint">最多添加 {{ MAX_ITEMS }} 项明细</view>
        <!-- 无明细时手填金额 -->
        <view v-if="g.plainItems.length === 0" class="mat-field">
          <text class="mat-label">金额</text>
          <view class="mat-input-box mat-input-row">
            <text class="mat-symbol">¥</text>
            <input class="mat-num" type="digit" v-model="g.manualAmount" placeholder="请输入金额" />
          </view>
        </view>
      </view>

      <!-- 组小计 -->
      <view v-if="g.typeId" class="group-total">
        <text class="gt-label">本组小计</text>
        <text class="gt-amount">¥{{ groupAmount(g).toFixed(2) }}</text>
      </view>
    </view>

    <!-- 添加支出类型 -->
    <view v-if="!isEdit && groups.length < MAX_GROUPS" class="add-group-btn" @click="addGroup">
      <text class="add-icon">＋</text><text class="add-text">添加支出类型（{{ groups.length }}/{{ MAX_GROUPS }}）</text>
    </view>

    <!-- 整单信息 -->
    <view class="card">
      <view class="section-title">
        <text class="section-icon">📄</text>
        <text>登记信息</text>
      </view>
      <view class="form-body">
        <!-- 日期（整单一个） -->
        <picker mode="date" :value="occurredDate" @change="onDateChange">
          <view class="field">
            <view class="field-text">
              <text class="field-label">支出日期 <text class="required">*</text></text>
              <text class="field-value">{{ occurredDate }}</text>
            </view>
            <text class="field-arrow">›</text>
          </view>
        </picker>

        <!-- 说明 -->
        <view class="field field-col">
          <text class="field-label">支出说明</text>
          <input class="field-input" v-model="description" placeholder="请简要说明支出用途" />
        </view>
      </view>
    </view>

    <!-- 凭证（多张，最多9张） -->
    <view class="card">
      <view class="section-title">
        <text class="section-icon">📷</text>
        <text>支出凭证</text>
        <text class="voucher-count">{{ vouchers.length }}/{{ MAX_VOUCHERS }}</text>
      </view>
      <text class="card-sub">请上传发票、收据、付款截图或现场照片，可多张</text>
      <view class="voucher-grid">
        <view v-for="(v, vi) in vouchers" :key="vi" class="voucher-item">
          <image :src="v" class="voucher-img" mode="aspectFill" @error="removeVoucher(vi)" />
          <view class="voucher-del" @click.stop="removeVoucher(vi)">✕</view>
        </view>
        <view v-if="vouchers.length < MAX_VOUCHERS" class="voucher-add" @click="chooseVouchers">
          <text class="voucher-icon">📷</text>
          <text class="voucher-text">上传</text>
        </view>
      </view>
    </view>

    <!-- 底部：合计 + 提交 -->
    <view class="bottom">
      <view class="bottom-total">
        <text class="bt-label">合计金额</text>
        <text class="bt-amount">¥{{ totalAmount.toFixed(2) }}</text>
      </view>
      <view class="btn" @click="submit">{{ saving ? '提交中...' : (isEdit ? '保存修改' : '提交登记') }}</view>
    </view>
  </view>

  <!-- 支出类型选择弹窗 -->
  <view v-if="showTypes" class="mask" @click="showTypes=false">
    <view class="sheet" @click.stop>
      <view class="sh"></view>
      <view class="sheet-head">
        <view>
          <text class="sheet-title">选择支出类型</text>
          <text class="sheet-sub">{{ isEdit ? '编辑模式仅修改当前类型' : '可一次登记多个支出类型' }}</text>
        </view>
        <view class="sheet-close" @click="showTypes=false">✕</view>
      </view>
      <scroll-view scroll-y class="sheet-list">
        <view class="type-grid">
          <view v-for="item in types" :key="item.typeId" class="type-btn" :class="{on:groups[activeGroupIdx]?.typeId===item.typeId}" @click="selectType(item)">
            <text class="type-btn-name">{{ item.name }}</text>
            <text v-if="item.description" class="type-btn-desc">{{ item.description }}</text>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>

  <!-- 物料搜索弹窗 -->
  <view v-if="showMatSearch" class="mask" @click="closeMatSearch">
    <view class="sheet" @click.stop>
      <view class="sh"></view>
      <view class="sheet-head">
        <text class="sheet-title">选择物料</text>
        <view class="sheet-close" @click="closeMatSearch">✕</view>
      </view>
      <view class="search-box">
        <input class="search-input" v-model="matSearchKey" placeholder="搜索物料名称" :focus="matSearchFocus" @input="onMatSearchInput" />
      </view>
      <scroll-view scroll-y class="sheet-list">
        <view v-if="matLoading" class="search-status">搜索中...</view>
        <view v-else-if="matResults.length===0 && !matSearchKey" class="search-status">输入关键词搜索</view>
        <view v-else-if="matResults.length===0" class="search-status">暂无匹配物料</view>
        <view v-for="item in matResults" :key="item.id||item.materialId" class="search-item" @click="openAddDrawer(item)">
          <view class="si-info">
            <text class="si-name">{{ matDisplayName(item) }}</text>
            <text class="si-meta">{{ matDisplayCategory(item) }}</text>
          </view>
        </view>
        <view class="search-item other-item" @click="selectOther()">
          <view class="si-info"><text class="si-name">其他</text></view>
        </view>
      </scroll-view>
    </view>
  </view>

  <!-- 物料编辑抽屉（自购食材） -->
  <view v-if="showDrawer" class="mask" @click="closeDrawer">
    <view class="sheet" @click.stop>
      <view class="sh"></view>
      <view class="sheet-head">
        <text class="sheet-title">{{ editingIndex >= 0 ? '编辑物料' : '添加物料' }}</text>
        <view class="sheet-close" @click="closeDrawer">✕</view>
      </view>
      <view class="dr-body">
        <view class="dr-name">{{ drawerItem?.materialName || '' }}</view>
        <view class="mat-field">
          <text class="mat-label">重量（kg）</text>
          <view class="mat-input-box mat-input-row">
            <input class="mat-num" type="digit" v-model="drawerWeight" placeholder="请输入重量" />
            <text class="mat-unit">kg</text>
          </view>
        </view>
        <view class="mat-field">
          <text class="mat-label">单价（元）</text>
          <view class="mat-input-box mat-input-row">
            <text class="mat-symbol">¥</text>
            <input class="mat-num" type="digit" v-model="drawerUnitPrice" placeholder="请输入单价" />
          </view>
        </view>
        <view class="mat-total dr-total">小计：¥{{ drawerAmount || '0.00' }}</view>
        <view class="btn" @click="confirmDrawer">确定</view>
      </view>
    </view>
  </view>

  <!-- 明细编辑抽屉（其他类型） -->
  <view v-if="showPlainDrawer" class="mask" @click="closePlainDrawer">
    <view class="sheet" @click.stop>
      <view class="sh"></view>
      <view class="sheet-head">
        <text class="sheet-title">{{ pEditingIndex >= 0 ? '编辑明细' : '添加明细' }}</text>
        <view class="sheet-close" @click="closePlainDrawer">✕</view>
      </view>
      <view class="dr-body">
        <view class="mat-field">
          <text class="mat-label">明细名称</text>
          <view class="mat-input-box">
            <input class="mat-input-val" v-model="pDrawerName" placeholder="请输入明细名称" />
          </view>
        </view>
        <view class="mat-field">
          <text class="mat-label">明细金额</text>
          <view class="mat-input-box mat-input-row">
            <text class="mat-symbol">¥</text>
            <input class="mat-num" type="digit" v-model="pDrawerAmount" placeholder="请输入金额" />
          </view>
        </view>
        <view class="mat-total dr-total">小计：¥{{ pDrawerTotal || '0.00' }}</view>
        <view class="btn" @click="confirmPlainDrawer">确定</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;
.page{min-height:100vh;background:$bg;padding:24rpx 24rpx 260rpx}

// 卡片
.card{background:$s;border-radius:24rpx;padding:24rpx;border:2rpx solid $b;box-shadow:0 4rpx 24rpx rgba(31,36,33,.04);margin-bottom:20rpx}
.section-title{display:flex;align-items:center;gap:8rpx;font-size:28rpx;font-weight:700;color:$t1;margin-bottom:16rpx}
.section-icon{font-size:28rpx}
.card-sub{display:block;font-size:22rpx;color:$t2;margin-bottom:16rpx}
.form-body{display:flex;flex-direction:column;gap:12rpx}

// 自购食材提示条
.sp-tip{display:flex;align-items:center;gap:12rpx;background:#FDF3E7;border:2rpx solid #F5D9B3;border-radius:16rpx;padding:18rpx 24rpx;margin-bottom:20rpx}
.sp-tip-icon{font-size:28rpx;flex-shrink:0}
.sp-tip-text{font-size:24rpx;color:#E58A2D;line-height:1.4}
.edit-tip{background:$ps;border-color:$p}
.edit-tip .sp-tip-text{color:$p}

// 类型组
.group-head{display:flex;align-items:center;gap:12rpx}
.group-type-field{flex:1;min-width:0}
.group-desc{display:block;font-size:22rpx;color:#E58A2D;margin-top:4rpx}
.group-del{flex-shrink:0;width:56rpx;height:56rpx;border-radius:50%;background:#F7F8F6;display:flex;align-items:center;justify-content:center;font-size:24rpx;color:$t3}
.group-del:active{background:#FDECEA;color:#E05A47}
.group-total{display:flex;align-items:center;justify-content:space-between;margin-top:16rpx;padding:16rpx 20rpx;background:$ps;border-radius:12rpx}
.gt-label{font-size:24rpx;color:$p;font-weight:600}
.gt-amount{font-size:32rpx;font-weight:800;color:$p}
.add-group-btn{display:flex;align-items:center;justify-content:center;gap:8rpx;padding:24rpx;border-radius:20rpx;border:2rpx dashed $p;color:$p;font-size:28rpx;font-weight:600;background:$ps;margin-bottom:20rpx}
.add-group-btn:active{opacity:.8}

// 表单字段
.field{display:flex;align-items:center;justify-content:space-between;background:#F7F8F6;border-radius:16rpx;padding:20rpx}
.field:active{background:#EEF1EF}
.field-col{flex-direction:column;align-items:stretch;gap:8rpx}
.field-text{flex:1;min-width:0}
.field-label{font-size:24rpx;color:$t2}.required{color:#E05A47}
.field-value{display:block;font-size:28rpx;color:$t1;margin-top:4rpx}.field-value.placeholder{color:$t3}
.field-arrow{font-size:32rpx;color:$p;flex-shrink:0}
.field-input{width:100%;font-size:28rpx;color:$t1;background:transparent;height:48rpx;line-height:48rpx}.field-input::placeholder{color:$t3}

// 物料/支出明细
.mat-section{margin-top:16rpx;padding:20rpx;background:#FAFBF9;border-radius:16rpx;display:flex;flex-direction:column;gap:14rpx}
.mat-title{display:flex;align-items:center;justify-content:space-between;gap:8rpx;font-size:26rpx;font-weight:600;color:$p;margin-bottom:4rpx}
.mat-count{font-size:22rpx;color:$t3;font-weight:400}
.mat-empty{padding:24rpx 0;text-align:center;font-size:24rpx;color:$t3;background:$s;border-radius:12rpx}
.mat-item{display:flex;align-items:center;justify-content:space-between;gap:12rpx;background:$s;border-radius:12rpx;padding:16rpx 20rpx;border:1rpx solid #EEF1EF}
.mat-item:active{background:#F7F8F6}
.mi-left{flex:1;min-width:0;display:flex;flex-direction:column;gap:4rpx}
.mi-name{font-size:28rpx;color:$t1;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.mi-meta{font-size:22rpx;color:$t3}
.mi-right{flex-shrink:0;display:flex;flex-direction:column;align-items:flex-end;gap:4rpx}
.mi-amount{font-size:26rpx;font-weight:700;color:$p}
.mi-del{flex-shrink:0;width:48rpx;height:48rpx;display:flex;align-items:center;justify-content:center;font-size:22rpx;color:$t3;background:#F7F8F6;border-radius:50%}
.mi-del:active{background:#EEF1EF;color:#E05A47}
.add-material-btn{display:flex;align-items:center;justify-content:center;gap:8rpx;padding:20rpx;border-radius:12rpx;border:2rpx dashed $p;color:$p;font-size:26rpx;font-weight:600;background:$ps}
.add-material-btn:active{opacity:.8}
.add-icon{font-size:28rpx;line-height:1}
.add-material-hint{text-align:center;font-size:22rpx;color:$t3;padding:12rpx 0}
.mat-field{display:flex;flex-direction:column;gap:8rpx}
.mat-label{font-size:26rpx;color:$t1}
.mat-input-box{background:$s;border-radius:12rpx;padding:0 20rpx;height:80rpx;display:flex;align-items:center}
.mat-input-row{gap:6rpx}
.mat-input-val{font-size:28rpx;color:$t1}
.mat-num{flex:1;min-width:0;font-size:28rpx;color:$t1;text-align:right;background:transparent;height:80rpx;line-height:80rpx}
.mat-unit,.mat-symbol{font-size:24rpx;color:$t2;flex-shrink:0}
.mat-total{text-align:right;font-size:28rpx;font-weight:700;color:$p;padding-top:4rpx}
.dr-body{padding:0 32rpx 8rpx;display:flex;flex-direction:column;gap:16rpx}
.dr-name{font-size:30rpx;font-weight:700;color:$t1;padding:4rpx 0}
.dr-total{margin-top:4rpx}

// 凭证（多张 grid）
.voucher-count{font-size:22rpx;color:$t3;font-weight:400;margin-left:auto}
.voucher-grid{display:flex;flex-wrap:wrap;gap:16rpx}
.voucher-item{position:relative;width:calc((100% - 32rpx) / 3);height:180rpx;border-radius:16rpx;overflow:hidden;background:#F7F8F6}
.voucher-img{width:100%;height:100%}
.voucher-del{position:absolute;top:0;right:0;width:44rpx;height:44rpx;display:flex;align-items:center;justify-content:center;background:rgba(31,36,33,.55);color:#fff;font-size:22rpx;border-radius:0 0 0 12rpx}
.voucher-add{width:calc((100% - 32rpx) / 3);height:180rpx;border:2rpx dashed $b;border-radius:16rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8rpx;background:#FAFBF9;box-sizing:border-box}
.voucher-add:active{background:#EEF1EF}
.voucher-icon{font-size:40rpx}
.voucher-text{font-size:22rpx;color:$t3}

// 底部（合计 + 提交）
.bottom{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:16rpx 24rpx calc(env(safe-area-inset-bottom) + 24rpx);background:rgba(255,255,255,.95);backdrop-filter:blur(20rpx);border-radius:32rpx 32rpx 0 0;box-shadow:0 -10rpx 40rpx rgba(0,0,0,.04);display:flex;flex-direction:column;align-items:center}
.bottom-total{display:flex;align-items:baseline;justify-content:center;gap:12rpx;margin-bottom:16rpx}
.bt-label{font-size:24rpx;color:$t2}
.bt-amount{font-size:44rpx;font-weight:800;color:$p}
.btn{width:100%;height:96rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700}
.btn:active{opacity:.9;transform:scale(.98)}

// 弹窗
.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.32)}
.sh{width:80rpx;height:6rpx;border-radius:999rpx;background:$b;margin:8rpx auto 16rpx;flex-shrink:0}
.sheet-head{display:flex;align-items:flex-start;justify-content:space-between;padding:0 32rpx;margin-bottom:16rpx}
.sheet-title{display:block;font-size:32rpx;font-weight:700;color:$t1}
.sheet-sub{display:block;font-size:22rpx;color:$t3;margin-top:4rpx}
.sheet-close{width:64rpx;height:64rpx;border-radius:50%;background:#F7F8F6;display:flex;align-items:center;justify-content:center;font-size:28rpx;color:$t3;flex-shrink:0}
.sheet{width:100%;max-height:75vh;border-radius:32rpx 32rpx 0 0;background:$s;padding:16rpx 0 calc(env(safe-area-inset-bottom) + 16rpx);display:flex;flex-direction:column;box-shadow:0 -8rpx 24rpx rgba(31,36,33,.1);overflow:hidden}
.sheet-list{flex:1;overflow-y:auto;padding:0 24rpx;max-height:55vh;box-sizing:border-box}
.type-grid{display:flex;flex-wrap:wrap;gap:20rpx}
.type-btn{width:calc((100% - 20rpx) / 2);padding:20rpx 18rpx;border-radius:16rpx;background:#FAFBF9;border:1rpx solid $b;display:flex;flex-direction:column;box-sizing:border-box}
.type-btn-name{font-size:28rpx;color:$t1;font-weight:600}
.type-btn-desc{font-size:22rpx;color:#E58A2D;margin-top:4rpx;line-height:1.3}
.type-btn.on{background:$ps;border-color:$p}.type-btn.on .type-btn-name{color:$p}

// 物料搜索弹窗
.search-box{padding:0 24rpx;margin-bottom:12rpx}
.search-input{width:100%;height:80rpx;background:#FAFBF9;border-radius:16rpx;padding:0 24rpx;font-size:28rpx;color:$t1;box-sizing:border-box}
.search-status{padding:40rpx 0;text-align:center;font-size:26rpx;color:$t3}
.search-item{padding:24rpx 20rpx;border-bottom:2rpx solid #EEF1EF}
.search-item:last-child{border-bottom:0}
.si-info{flex:1;min-width:0}
.si-name{display:block;font-size:28rpx;color:$t1;font-weight:500}
.si-meta{display:block;font-size:24rpx;color:$t3;margin-top:4rpx}
.other-item{border-top:2rpx solid $b;margin-top:8rpx}
</style>
