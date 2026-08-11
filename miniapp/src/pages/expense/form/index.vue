<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createExpense, updateExpense, fetchExpenseDetail, fetchExpenseMaterial, fetchExpenseTypes, uploadVoucher } from '@/api/expense'
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
const selectedMaterial = ref<any>(null)
const isOtherMaterial = ref(false)
const matWeight = ref('')
const matUnitPrice = ref('')

const matTotalAmount = computed(() => {
  const w = parseFloat(matWeight.value)
  const p = parseFloat(matUnitPrice.value)
  if (!w || w <= 0 || !p || p <= 0) return ''
  return (w * p).toFixed(2)
})

watch(matTotalAmount, (val) => {
  if (val && isSelfPurchase.value) amount.value = val
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
      // 自购食材回填物料信息
      if (d.typeName === SELF_PURCHASE) {
        try {
          const mat = await fetchExpenseMaterial(q.expenseId)
          if (mat) {
            if (mat.materialId) {
              selectedMaterial.value = { id: mat.materialId, materialId: mat.materialId, yuancailiaomingcheng: mat.materialName, materialName: mat.materialName, leibie: mat.parentCategory, parentCategory: mat.parentCategory, leibie2: mat.category, category: mat.category }
            } else {
              isOtherMaterial.value = true
            }
            matWeight.value = mat.purchaseQty ? String(mat.purchaseQty) : ''
            matUnitPrice.value = mat.unitPrice ? String(mat.unitPrice) : ''
          }
        } catch { /* ignore */ }
      }
    }
  }
})

function selectType(item: any) {
  typeId.value = item.typeId
  typeName.value = item.name
  typeDesc.value = item.description || ''
  showTypes.value = false
  if (item.name !== SELF_PURCHASE) {
    selectedMaterial.value = null; isOtherMaterial.value = false; matWeight.value = ''; matUnitPrice.value = ''
  }
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
function selectMaterial(item: any) { selectedMaterial.value = item; isOtherMaterial.value = false; showMatSearch.value = false }
function selectOther() { selectedMaterial.value = null; isOtherMaterial.value = true; showMatSearch.value = false }

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
      if (!selectedMaterial.value && !isOtherMaterial.value) { uni.showToast({ title: '请选择物料', icon: 'none' }); saving.value = false; return }
      if (!matWeight.value || parseFloat(matWeight.value) <= 0) { uni.showToast({ title: '请输入重量', icon: 'none' }); saving.value = false; return }
      if (!matUnitPrice.value || parseFloat(matUnitPrice.value) <= 0) { uni.showToast({ title: '请输入单价', icon: 'none' }); saving.value = false; return }
      if (isOtherMaterial.value) {
        data.materialId = ''
        data.materialName = '其他'
        data.parentCategory = MATERIAL_PARENT_CATEGORY
        data.category = SELF_PURCHASE
      } else {
        data.materialId = selectedMaterial.value.id || selectedMaterial.value.materialId || ''
        data.materialName = selectedMaterial.value.yuancailiaomingcheng || selectedMaterial.value.materialName
        data.parentCategory = selectedMaterial.value.leibie || selectedMaterial.value.parentCategory || ''
        data.category = selectedMaterial.value.leibie2 || selectedMaterial.value.category || ''
      }
      data.materialUnit = 'kg'
      data.weight = parseFloat(matWeight.value)
      data.unitPrice = parseFloat(matUnitPrice.value)
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
        <input class="amount-num" type="digit" v-model="amount" placeholder="0.00" :focus="amountFocus" @blur="amountFocus=false" />
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
          </view>
          <!-- 物料名称 -->
          <view class="mat-field" @click="openMatSearch">
            <text class="mat-label">物料名称 <text class="required">*</text></text>
            <view class="mat-input-box">
              <text v-if="!selectedMaterial && !isOtherMaterial" class="mat-placeholder">搜索物料名称</text>
              <text v-else class="mat-input-val">{{ selectedMaterial ? matDisplayName(selectedMaterial) : '其他' }}</text>
            </view>
          </view>
          <!-- 重量 + 单价 -->
          <view class="mat-row">
            <view class="mat-field mat-half">
              <text class="mat-label">重量 <text class="required">*</text></text>
              <view class="mat-input-box mat-input-row">
                <input class="mat-num" type="digit" v-model="matWeight" placeholder="0" />
                <text class="mat-unit">kg</text>
              </view>
            </view>
            <view class="mat-field mat-half">
              <text class="mat-label">单价 <text class="required">*</text></text>
              <view class="mat-input-box mat-input-row">
                <text class="mat-symbol">¥</text>
                <input class="mat-num" type="digit" v-model="matUnitPrice" placeholder="0.00" />
              </view>
            </view>
          </view>
          <text v-if="matTotalAmount" class="mat-total">合计：¥{{ matTotalAmount }}</text>
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
        <view v-for="item in matResults" :key="item.id||item.materialId" class="search-item" @click="selectMaterial(item)">
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

// 物料明细
.mat-section{margin-top:8rpx;padding:20rpx;background:#FAFBF9;border-radius:16rpx;display:flex;flex-direction:column;gap:14rpx}
.mat-title{font-size:26rpx;font-weight:600;color:$p;margin-bottom:4rpx}
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
