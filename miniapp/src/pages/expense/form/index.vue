<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createExpense, updateExpense, fetchExpenseDetail, fetchExpenseItems, fetchExpenseTypes, uploadVoucher } from '@/api/expense'
import { fetchMaterialsByCategory } from '@/api/material'
import { useUserStore } from '@/store/user'

const SELF_PURCHASE = '自购食材'
const MATERIAL_PARENT_CATEGORY = '自购食材成本'

const userStore = useUserStore()
const isEdit = ref(false)
const editId = ref('')
const types = ref<any[]>([])
const typeId = ref('')
const typeName = ref('请选择支出类型')
const typeDesc = ref('')
const amount = ref('')
const description = ref('')
const occurredDate = ref(new Date().toISOString().slice(0,10))
const handlerName = ref(userStore.employeeName || '')
const showTypes = ref(false)
const saving = ref(false)
const voucherPath = ref('')
const amountFocus = ref(true)

const isSelfPurchase = computed(() => typeName.value === SELF_PURCHASE)

// 多明细（一次登记最多 10 条）：自购食材=物料型（名称+重量kg+单价），其他类型=名称+金额
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
const items = ref<MatItem[]>([])          // 自购食材：物料明细
const plainItems = ref<PlainItem[]>([])   // 其他类型：名称+金额明细
const MAX_ITEMS = 10

function itemAmount(it: MatItem): number {
  const w = parseFloat(it.weight)
  const p = parseFloat(it.unitPrice)
  if (!(w > 0) || !(p > 0)) return 0
  return Math.round(w * p * 100) / 100
}
function plainItemAmount(it: PlainItem): number {
  const a = parseFloat(it.amount)
  return a > 0 ? Math.round(a * 100) / 100 : 0
}
const totalAmount = computed(() => {
  if (isSelfPurchase.value) {
    const sum = items.value.reduce((s, it) => s + itemAmount(it), 0)
    return sum > 0 ? sum.toFixed(2) : ''
  }
  const sum = plainItems.value.reduce((s, it) => s + plainItemAmount(it), 0)
  return sum > 0 ? sum.toFixed(2) : ''
})
watch(totalAmount, (val) => {
  const hasItems = isSelfPurchase.value ? items.value.length > 0 : plainItems.value.length > 0
  if (val && hasItems) amount.value = val
})
const amountDisabled = computed(() => {
  return isSelfPurchase.value ? items.value.length > 0 : plainItems.value.length > 0
})

// 自购食材物料：添加/编辑抽屉
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

// 其他类型明细：添加/编辑抽屉
const showPlainDrawer = ref(false)
const pEditingIndex = ref(-1)
const pDrawerName = ref('')
const pDrawerAmount = ref('')
const pDrawerTotal = computed(() => {
  const a = parseFloat(pDrawerAmount.value)
  return a > 0 ? a.toFixed(2) : ''
})

const showMatSearch = ref(false)
const matSearchKey = ref('')
const matSearchFocus = ref(false)
const matResults = ref<any[]>([])
const matLoading = ref(false)
let matTimer: any = null

onLoad(async (q: any) => {
  types.value = await fetchExpenseTypes()
  if (q?.expenseId) {
    isEdit.value = true
    editId.value = q.expenseId
    const d = await fetchExpenseDetail(q.expenseId) as any
    if (d) {
      typeId.value = d.typeId || ''
      typeName.value = d.typeName || '请选择支出类型'
      // 兜底：如果 typeId 为空但 typeName 能匹配，自动补齐
      if (!typeId.value && typeName.value !== '请选择支出类型') {
        const matched = types.value.find((t: any) => t.name === typeName.value)
        if (matched) typeId.value = matched.typeId
      }
      amount.value = String(d.amount || '')
      description.value = d.remark || ''
      occurredDate.value = d.occurredDate || ''
      handlerName.value = d.handlerName || ''
      const vu = d.voucherUrl || ''
      voucherPath.value = (vu.startsWith('http') && !vu.includes('127.0.0.1') && !vu.includes('__tmp__') && !vu.includes('://tmp/') && !vu.includes('localhost')) ? vu : ''
      const matched = types.value.find((t: any) => t.typeId === d.typeId)
      if (matched) typeDesc.value = matched.description || ''
      // 回填多明细：自购食材=物料型，其他类型=名称+金额
      try {
        const its = await fetchExpenseItems(q.expenseId)
        if (Array.isArray(its) && its.length) {
          if (d.typeName === SELF_PURCHASE) {
            items.value = its.map((mat: any) => ({
              materialId: mat.materialId || '',
              materialName: mat.name,
              parentCategory: mat.parentCategory || '',
              category: mat.category || '',
              weight: mat.qty != null ? String(mat.qty) : '',
              unitPrice: mat.unitPrice != null ? String(mat.unitPrice) : '',
            }))
          } else {
            plainItems.value = its.map((it: any) => ({
              name: it.name || '',
              amount: it.amount != null ? String(it.amount) : '',
            }))
          }
        }
      } catch { /* ignore */ }
    }
  }
})

function selectType(item: any) {
  typeId.value = item.typeId
  typeName.value = item.name
  typeDesc.value = item.description || ''
  showTypes.value = false
  if (item.name === SELF_PURCHASE) { plainItems.value = [] } else { items.value = [] }
}

function openMatSearch() {
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
  if (editingIndex.value === -1 && items.value.some(i => i.materialId && i.materialId === (m.id || m.materialId))) {
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
  const it = items.value[idx]
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
  if (editingIndex.value >= 0) { items.value[editingIndex.value] = item } else { items.value.push(item) }
  closeDrawer()
}
function removeItem(idx: number) { items.value.splice(idx, 1) }
function openPlainAddDrawer() {
  pDrawerName.value = ''; pDrawerAmount.value = ''; pEditingIndex.value = -1; showPlainDrawer.value = true
}
function openPlainEditDrawer(idx: number) {
  const it = plainItems.value[idx]
  pDrawerName.value = it.name; pDrawerAmount.value = it.amount; pEditingIndex.value = idx; showPlainDrawer.value = true
}
function closePlainDrawer() { showPlainDrawer.value = false; pEditingIndex.value = -1 }
function confirmPlainDrawer() {
  const name = pDrawerName.value.trim()
  if (!name) { uni.showToast({ title: '请填写明细名称', icon: 'none' }); return }
  if (!(parseFloat(pDrawerAmount.value) > 0)) { uni.showToast({ title: '请填写明细金额', icon: 'none' }); return }
  const item: PlainItem = { name, amount: pDrawerAmount.value }
  if (pEditingIndex.value >= 0) { plainItems.value[pEditingIndex.value] = item } else { plainItems.value.push(item) }
  closePlainDrawer()
}
function removePlainItem(idx: number) { plainItems.value.splice(idx, 1) }

function onDateChange(e: any) { occurredDate.value = e.detail.value }
function chooseVoucher() {
  uni.chooseImage({
    count: 1,
    success: (res: any) => { voucherPath.value = res.tempFilePaths[0] },
    fail: (err: any) => { uni.showToast({ title: err.errMsg || '失败', icon: 'none', duration: 3000 }) }
  })
}

async function submit() {
  if (saving.value) return
  if (!typeId.value) { uni.showToast({ title: '请选择支出类型', icon: 'none' }); return }
  if (isSelfPurchase.value) {
    if (!items.value.length) { uni.showToast({ title: '请添加物料', icon: 'none' }); return }
    if (items.value.length > MAX_ITEMS) { uni.showToast({ title: `最多添加${MAX_ITEMS}种物料`, icon: 'none' }); return }
    for (const it of items.value) {
      if (!(parseFloat(it.weight) > 0)) { uni.showToast({ title: '请填写物料重量', icon: 'none' }); return }
      if (!(parseFloat(it.unitPrice) > 0)) { uni.showToast({ title: '请填写物料单价', icon: 'none' }); return }
    }
  } else if (plainItems.value.length) {
    if (plainItems.value.length > MAX_ITEMS) { uni.showToast({ title: `最多添加${MAX_ITEMS}项明细`, icon: 'none' }); return }
    for (const it of plainItems.value) {
      if (!it.name.trim()) { uni.showToast({ title: '请填写明细名称', icon: 'none' }); return }
      if (!(parseFloat(it.amount) > 0)) { uni.showToast({ title: '请填写明细金额', icon: 'none' }); return }
    }
  }
  const amt = parseFloat(amount.value)
  if (!amt || amt <= 0) { uni.showToast({ title: '请输入金额', icon: 'none' }); return }
  saving.value = true
  try {
    const isTempPath = voucherPath.value && (
      !voucherPath.value.startsWith('http') || voucherPath.value.includes('://tmp/') ||
      voucherPath.value.includes('__tmp__') || voucherPath.value.includes('127.0.0.1') || voucherPath.value.includes('localhost')
    )
    let uploadedUrl = ''
    if (voucherPath.value && isTempPath) {
      try { const res = await uploadVoucher(voucherPath.value); uploadedUrl = res.url }
      catch { uni.showToast({ title: '凭证上传失败', icon: 'none' }); saving.value = false; return }
    } else { uploadedUrl = voucherPath.value }
    const data: Record<string, any> = {
      typeId: typeId.value, amount: amt, occurredDate: occurredDate.value,
      handlerName: handlerName.value || '--', voucherUrl: uploadedUrl || undefined,
      remark: description.value || undefined
    }
    if (isSelfPurchase.value) {
      data.items = items.value.map(it => ({
        materialId: it.materialId || undefined,
        materialName: it.materialName,
        parentCategory: it.parentCategory || undefined,
        category: it.category || undefined,
        weight: parseFloat(it.weight),
        unitPrice: parseFloat(it.unitPrice),
      }))
    } else if (plainItems.value.length) {
      data.amountItems = plainItems.value.map(it => ({ name: it.name.trim(), amount: parseFloat(it.amount) }))
    }
    if (isEdit.value) { await updateExpense(editId.value, data) }
    else { await createExpense(data) }
    uni.showToast({ title: isEdit.value ? '修改成功' : '登记成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } finally { saving.value = false }
}

function matDisplayName(item: any) { return item.yuancailiaomingcheng || item.materialName || '' }
function matDisplayCategory(item: any) { return item.leibie2 || item.category || '' }
</script>

<template>
  <view class="page">
    <!-- 金额输入 -->
    <view class="card amount-card">
      <text class="amount-label">支出金额</text>
      <view class="amount-row">
        <text class="amount-symbol">¥</text>
        <input class="amount-num" type="digit" v-model="amount" placeholder="0.00" :focus="amountFocus" :disabled="amountDisabled" @blur="amountFocus=false" />
      </view>
    </view>

    <!-- 自购食材提示 -->
    <view v-if="isSelfPurchase" class="sp-tip">
      <text class="sp-tip-icon">⚠️</text>
      <text class="sp-tip-text">请仅填写门店自行购买的物料，已通过企迈采购的物料无需登记</text>
    </view>

    <!-- 支出信息 -->
    <view class="card">
      <view class="section-title">
        <text class="section-icon">📄</text>
        <text>支出信息</text>
      </view>
      <view class="form-body">
        <!-- 支出类型 -->
        <view class="field" @click="showTypes=true">
          <view class="field-text">
            <text class="field-label">支出类型 <text class="required">*</text></text>
            <text class="field-value" :class="{placeholder:!typeId}">{{ typeName }}</text>
          </view>
          <text class="field-arrow">›</text>
        </view>

        <!-- 自购食材物料明细 -->
        <view v-if="isSelfPurchase" class="mat-section">
          <view class="mat-title">
            <text>📦 物料明细</text>
            <text class="mat-count">{{ items.length }}/{{ MAX_ITEMS }}</text>
          </view>
          <view v-if="items.length === 0" class="mat-empty">请添加物料</view>
          <view v-for="(it, idx) in items" :key="idx" class="mat-item" @click="openEditDrawer(idx)">
            <view class="mi-left">
              <text class="mi-name">{{ it.materialName }}</text>
              <text v-if="it.category" class="mi-meta">{{ it.category }}</text>
            </view>
            <view class="mi-right">
              <text class="mi-amount">¥{{ itemAmount(it).toFixed(2) }}</text>
              <text class="mi-meta">{{ it.weight }}kg × ¥{{ it.unitPrice }}</text>
            </view>
            <text class="mi-del" @click.stop="removeItem(idx)">✕</text>
          </view>
          <view v-if="items.length < MAX_ITEMS" class="add-material-btn" @click="openMatSearch">
            <text class="add-icon">＋</text><text class="add-text">添加物料（{{ items.length }}/{{ MAX_ITEMS }}）</text>
          </view>
          <view v-else class="add-material-hint">最多添加 {{ MAX_ITEMS }} 种物料</view>
        </view>

        <!-- 其他类型支出明细 -->
        <view v-else class="mat-section">
          <view class="mat-title">
            <text>💰 支出明细</text>
            <text class="mat-count">{{ plainItems.length }}/{{ MAX_ITEMS }}</text>
          </view>
          <view v-if="plainItems.length === 0" class="mat-empty">请添加明细（也可直接填写上方金额）</view>
          <view v-for="(it, idx) in plainItems" :key="idx" class="mat-item" @click="openPlainEditDrawer(idx)">
            <view class="mi-left">
              <text class="mi-name">{{ it.name }}</text>
            </view>
            <view class="mi-right">
              <text class="mi-amount">¥{{ plainItemAmount(it).toFixed(2) }}</text>
            </view>
            <text class="mi-del" @click.stop="removePlainItem(idx)">✕</text>
          </view>
          <view v-if="plainItems.length < MAX_ITEMS" class="add-material-btn" @click="openPlainAddDrawer">
            <text class="add-icon">＋</text><text class="add-text">添加明细（{{ plainItems.length }}/{{ MAX_ITEMS }}）</text>
          </view>
          <view v-else class="add-material-hint">最多添加 {{ MAX_ITEMS }} 项明细</view>
        </view>

        <!-- 日期 -->
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

    <!-- 凭证 -->
    <view class="card">
      <view class="section-title">
        <text class="section-icon">📷</text>
        <text>支出凭证</text>
      </view>
      <text class="card-sub">请上传发票、收据、付款截图或现场照片</text>
      <view class="voucher-area" @click="chooseVoucher">
        <image v-if="voucherPath" :src="voucherPath" class="voucher-img" mode="widthFix" @error="voucherPath = ''" />
        <view v-if="voucherPath" class="voucher-overlay"><text>📷 重新上传</text></view>
        <view v-else class="voucher-empty">
          <text class="voucher-icon">📷</text>
          <text class="voucher-text">上传凭证</text>
        </view>
      </view>
    </view>

    <!-- 底部按钮 -->
    <view class="bottom">
      <text class="bottom-hint">提交后可在支出详情页查看和修改</text>
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
          <text class="sheet-sub">选择后会自动填入表单</text>
        </view>
        <view class="sheet-close" @click="showTypes=false">✕</view>
      </view>
      <scroll-view scroll-y class="sheet-list">
        <view class="type-grid">
          <view v-for="item in types" :key="item.typeId" class="type-btn" :class="{on:typeId===item.typeId}" @click="selectType(item)">
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
.page{min-height:100vh;background:$bg;padding:24rpx 24rpx 220rpx}

// 卡片
.card{background:$s;border-radius:24rpx;padding:24rpx;border:2rpx solid $b;box-shadow:0 4rpx 24rpx rgba(31,36,33,.04);margin-bottom:20rpx}
.section-title{display:flex;align-items:center;gap:8rpx;font-size:28rpx;font-weight:700;color:$t1;margin-bottom:16rpx}
.section-icon{font-size:28rpx}
.card-sub{display:block;font-size:22rpx;color:$t2;margin-bottom:16rpx}
.form-body{display:flex;flex-direction:column;gap:12rpx}

// 金额
.amount-card{display:flex;flex-direction:column;align-items:center;padding:32rpx 24rpx;border-color:$ps}
.amount-label{font-size:24rpx;font-weight:600;color:$p;margin-bottom:12rpx}
.amount-row{display:flex;align-items:baseline;justify-content:center;gap:4rpx}
.amount-symbol{font-size:48rpx;font-weight:700;color:$p;line-height:1}
.amount-num{width:340rpx;height:80rpx;font-size:60rpx;font-weight:800;color:$t1;text-align:center;line-height:80rpx;caret-color:$p;background:transparent}
.amount-num::placeholder{color:$t3}

// 自购食材提示条
.sp-tip{display:flex;align-items:center;gap:12rpx;background:#FDF3E7;border:2rpx solid #F5D9B3;border-radius:16rpx;padding:18rpx 24rpx;margin-bottom:20rpx}
.sp-tip-icon{font-size:28rpx;flex-shrink:0}
.sp-tip-text{font-size:24rpx;color:#E58A2D;line-height:1.4}

// 表单字段
.field{display:flex;align-items:center;justify-content:space-between;background:#F7F8F6;border-radius:16rpx;padding:20rpx}
.field:active{background:#EEF1EF}
.field-col{flex-direction:column;align-items:stretch;gap:8rpx}
.field-text{flex:1;min-width:0}
.field-label{font-size:24rpx;color:$t2}.required{color:#E05A47}
.field-value{display:block;font-size:28rpx;color:$t1;margin-top:4rpx}.field-value.placeholder{color:$t3}
.field-arrow{font-size:32rpx;color:$p;flex-shrink:0}
.field-input{width:100%;font-size:28rpx;color:$t1;background:transparent;height:48rpx;line-height:48rpx}.field-input::placeholder{color:$t3}
.field-textarea{width:100%;font-size:28rpx;color:$t1;background:transparent;height:100rpx}.field-textarea::placeholder{color:$t3}

// 物料/支出明细
.mat-section{margin-top:8rpx;padding:20rpx;background:#FAFBF9;border-radius:16rpx;display:flex;flex-direction:column;gap:14rpx}
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
.mat-placeholder{font-size:28rpx;color:$t3}
.mat-input-val{font-size:28rpx;color:$t1}
.mat-num{flex:1;min-width:0;font-size:28rpx;color:$t1;text-align:right;background:transparent;height:80rpx;line-height:80rpx}
.mat-unit,.mat-symbol{font-size:24rpx;color:$t2;flex-shrink:0}
.mat-row{display:flex;gap:14rpx}
.mat-half{flex:1}
.mat-total{text-align:right;font-size:28rpx;font-weight:700;color:$p;padding-top:4rpx}
.dr-body{padding:0 32rpx 8rpx;display:flex;flex-direction:column;gap:16rpx}
.dr-name{font-size:30rpx;font-weight:700;color:$t1;padding:4rpx 0}
.dr-total{margin-top:4rpx}

// 凭证
.voucher-area{border:2rpx dashed $b;border-radius:16rpx;overflow:hidden;position:relative}
.voucher-img{width:100%;display:block}
.voucher-overlay{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.4);color:#fff;font-size:26rpx;opacity:0;transition:opacity .2s}
.voucher-area:active .voucher-overlay{opacity:1}
.voucher-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;padding:48rpx 0}
.voucher-icon{font-size:48rpx;margin-bottom:12rpx}
.voucher-text{font-size:26rpx;color:$t3}

// 底部按钮
.bottom{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:16rpx 24rpx calc(env(safe-area-inset-bottom) + 24rpx);background:rgba(255,255,255,.9);backdrop-filter:blur(20rpx);border-radius:32rpx 32rpx 0 0;box-shadow:0 -10rpx 40rpx rgba(0,0,0,.04);display:flex;flex-direction:column;align-items:center}
.bottom-hint{font-size:22rpx;color:$t3;margin-bottom:12rpx}
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
