<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { scanMaterial, addMaterial } from '@/api/material'
import { itemSave } from '@/api/zone'
import { request } from '@/utils/request'

const taskId = ref(0)
const zoneId = ref(0)
const zoneName = ref('')
const pageState = ref<'scanning'|'found'|'notFound'|'alreadyInZone'|'success'|'error'>('scanning')
const qmCode = ref('')
const material = ref<any>(null)
const adding = ref(false)
const errorMsg = ref('')
const unitInputs = ref<Record<string,string>>({})
const drawerTotal = ref('0')

onLoad((query: any) => {
  taskId.value = Number(query?.taskId||0)
  zoneId.value = Number(query?.zoneId||0)
  zoneName.value = query?.zoneName ? decodeURIComponent(query.zoneName) : ''
})
onMounted(() => startScan())

async function startScan() {
  pageState.value = 'scanning'
  try {
    const res = await uni.scanCode({ onlyFromCamera: true, scanType: ['qrCode','barCode'] })
    qmCode.value = res.result
    await lookupMaterial(res.result)
  } catch (err: any) {
    if (err?.errMsg?.includes('cancel')) goBack()
    else { pageState.value='error'; errorMsg.value='扫码失败，请重试' }
  }
}

async function lookupMaterial(code: string) {
  pageState.value = 'scanning'
  try {
    const data: any = await scanMaterial(taskId.value, zoneId.value, code)
    material.value = data
    if (data.inCurrentZone) { pageState.value = 'alreadyInZone'; return }
    try {
      const rule: any = await request({ url: `/tasks/${taskId.value}/zones/${zoneId.value}/materials/scan-rule`, data: { materialId: data.materialId }, showLoading: false, silent: true })
      if (rule) material.value.inventoryRule = rule
    } catch { /* ignore */ }
    unitInputs.value = {}
    drawerTotal.value = '0'
    const b = baseUnit()
    if (b) unitInputs.value[b] = ''
    pageState.value = 'found'
  } catch (err: any) {
    pageState.value = err?.code===404 ? 'notFound' : 'error'
    if (pageState.value==='error') errorMsg.value = err?.message||'查询失败'
  }
}

function isMultiUnit() { return material.value?.inventoryRule?.units?.length > 1 }
function baseUnit() { return material.value?.inventoryRule?.baseUnit || material.value?.unit || '' }
function ruleUnits() { return material.value?.inventoryRule?.units?.map((u:any)=>u.unitName) || [material.value?.unit||''] }

function onUnitInput(u: string, v: string) {
  unitInputs.value = { ...unitInputs.value, [u]: v }
  let t = 0; const b = baseUnit()
  for (const [k,val] of Object.entries(unitInputs.value)) {
    const n = parseFloat(val||'0'); if (!n) continue
    if (k===b) t+=n
    else { const c = (material.value?.inventoryRule?.conversions||[]).find((x:any)=>x.fromUnit===k); t+=c?n*(Number(c.toQuantity)/Number(c.fromQuantity)):0 }
  }
  drawerTotal.value = t>0?t.toFixed(2):'0'
}

async function handleAdd() {
  if (adding.value) return
  if (isMultiUnit()) {
    if (!Object.values(unitInputs.value).some(v=>parseFloat(v||'0')>0)) { uni.showToast({title:'请输入数量',icon:'none'}); return }
  } else {
    const q = parseFloat(unitInputs.value[baseUnit()]||'0')
    if (isNaN(q)||q<0) { uni.showToast({title:'请输入有效数量',icon:'none'}); return }
  }
  adding.value = true
  try {
    await addMaterial(taskId.value, zoneId.value, material.value.materialId)
    const qty = parseFloat(isMultiUnit() ? drawerTotal.value : (unitInputs.value[baseUnit()]||'0'))
    await itemSave(taskId.value, zoneId.value, { materialId: material.value.materialId, qty, inputMode:'unit', originalQty:qty, originalUnit:baseUnit(), unitInputs: isMultiUnit()?JSON.stringify(unitInputs.value):undefined })
    pageState.value = 'success'
    uni.showToast({title:'添加成功',icon:'success'})
  } catch { pageState.value='found' }
  finally { adding.value = false }
}

function continueScan() { unitInputs.value={}; material.value=null; qmCode.value=''; startScan() }
function goBack() { uni.navigateBack() }
</script>

<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" @click="goBack">← 返回</view>
      <text class="nav-title">扫码盘点</text>
      <view class="nav-placeholder"></view>
    </view>
    <view v-if="pageState==='scanning'" class="scan-area">
      <view class="scan-frame">
        <view class="scan-corner tl"></view><view class="scan-corner tr"></view>
        <view class="scan-corner bl"></view><view class="scan-corner br"></view>
        <view class="scan-line"></view>
      </view>
      <text class="scan-hint">将二维码对准扫描框</text>
      <text class="scan-sub">{{ zoneName }}</text>
    </view>
    <view v-else-if="pageState==='found'" class="result-area">
      <view class="zone-tag">🏪 {{ zoneName }}</view>
      <view class="card">
        <text class="mat-name">{{ material?.materialName }}</text>
        <text class="mat-spec" v-if="material?.spec">规格：{{ material.spec }}</text>
        <text class="mat-code">编码：{{ material?.qmCode }}</text>
        <view class="divider"></view>
        <template v-if="isMultiUnit()">
          <view class="unit-list">
            <view v-for="u in ruleUnits()" :key="u" class="unit-row">
              <text class="unit-label">{{ u }}</text>
              <input class="unit-input" type="digit" :value="unitInputs[u]||''" @input="(e:any)=>onUnitInput(u,e.detail.value||'')" :placeholder="u" />
            </view>
          </view>
          <view class="total-row"><text class="total-label">汇总</text><text class="total-val">{{ drawerTotal }} {{ baseUnit() }}</text></view>
        </template>
        <template v-else>
          <view class="single-row">
            <text class="unit-label">{{ baseUnit() }}</text>
            <input class="unit-input large" type="digit" :value="unitInputs[baseUnit()]||''" @input="(e:any)=>onUnitInput(baseUnit(),e.detail.value)" :placeholder="baseUnit()" :focus="true" />
          </view>
        </template>
        <view class="btn-primary" :class="{loading:adding}" @click="handleAdd">{{ adding?'添加中...':'添加至当前分区' }}</view>
        <view class="btn-secondary" @click="continueScan">重新扫码</view>
      </view>
    </view>
    <view v-else-if="pageState==='success'" class="result-area">
      <view class="status-icon success">✓</view>
      <text class="status-title">添加成功</text>
      <text class="status-desc">{{ material?.materialName }}</text>
      <view class="btn-primary" @click="continueScan">继续扫码</view>
      <view class="btn-secondary" @click="goBack">返回分区</view>
    </view>
    <view v-else-if="pageState==='alreadyInZone'" class="result-area">
      <view class="status-icon info">ℹ️</view>
      <text class="status-title">该物料已在当前分区中</text>
      <view class="card readonly"><text class="mat-name">{{ material?.materialName }}</text></view>
      <view class="btn-primary" @click="continueScan">继续扫码</view>
      <view class="btn-secondary" @click="goBack">返回分区</view>
    </view>
    <view v-else-if="pageState==='notFound'" class="result-area">
      <view class="status-icon warn">⚠️</view>
      <text class="status-title">未找到该编码对应的物料</text>
      <text class="status-desc">编码：{{ qmCode }}</text>
      <view class="btn-primary" @click="continueScan">重新扫码</view>
      <view class="btn-secondary" @click="goBack">返回分区</view>
    </view>
    <view v-else-if="pageState==='error'" class="result-area">
      <view class="status-icon error">✕</view>
      <text class="status-title">操作失败</text>
      <text class="status-desc">{{ errorMsg }}</text>
      <view class="btn-primary" @click="continueScan">重新扫码</view>
      <view class="btn-secondary" @click="goBack">返回分区</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#1F2421;$s:#2A302C;$p:#2F8F57;$t1:#F0F2F0;$t2:#98A19C;$t3:#66706A;$w:#E58A2D;$d:#E05A47;
.page{min-height:100vh;background:$bg;display:flex;flex-direction:column}
.nav-bar{display:flex;align-items:center;justify-content:space-between;padding:24rpx 32rpx}
.nav-back{font-size:28rpx;color:$p}
.nav-title{font-size:32rpx;font-weight:600;color:$t1}
.nav-placeholder{width:80rpx}
.scan-area{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:40rpx}
.scan-frame{width:420rpx;height:420rpx;position:relative}
.scan-corner{position:absolute;width:40rpx;height:40rpx;border-color:$p;border-style:solid}
.tl{top:0;left:0;border-width:6rpx 0 0 6rpx;border-radius:12rpx 0 0 0}
.tr{top:0;right:0;border-width:6rpx 6rpx 0 0;border-radius:0 12rpx 0 0}
.bl{bottom:0;left:0;border-width:0 0 6rpx 6rpx;border-radius:0 0 0 12rpx}
.br{bottom:0;right:0;border-width:0 6rpx 6rpx 0;border-radius:0 0 12rpx 0}
.scan-line{position:absolute;left:8rpx;top:8rpx;width:404rpx;height:4rpx;background:linear-gradient(90deg,transparent,$p,transparent);animation:scanLine 2s ease-in-out infinite}
@keyframes scanLine{0%,100%{top:8rpx}50%{top:408rpx}}
.scan-hint{margin-top:48rpx;font-size:28rpx;color:$t2}
.scan-sub{margin-top:12rpx;font-size:24rpx;color:$t3}
.result-area{flex:1;display:flex;flex-direction:column;align-items:center;padding:56rpx 40rpx}
.status-icon{width:140rpx;height:140rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:64rpx;margin-bottom:32rpx}
.status-icon.success{background:rgba($p,.15);color:$p}
.status-icon.info{background:rgba($w,.15);color:$w}
.status-icon.warn{background:rgba($w,.15);color:$w}
.status-icon.error{background:rgba($d,.15);color:$d}
.status-title{font-size:36rpx;font-weight:700;color:$t1;text-align:center;margin-bottom:12rpx}
.status-desc{font-size:26rpx;color:$t2;text-align:center;margin-bottom:48rpx}
.zone-tag{font-size:24rpx;color:$p;background:rgba($p,.12);padding:8rpx 24rpx;border-radius:999rpx;margin-bottom:24rpx}
.card{width:100%;background:$s;border-radius:24rpx;padding:32rpx;margin-bottom:24rpx}
.card.readonly{opacity:.85}
.mat-name{display:block;font-size:32rpx;font-weight:700;color:$t1;margin-bottom:4rpx}
.mat-spec{display:block;font-size:26rpx;color:$t2;margin-bottom:4rpx}
.mat-code{display:block;font-size:24rpx;color:$t3;margin-bottom:16rpx}
.divider{height:2rpx;background:rgba($t3,.2);margin:16rpx 0}
.unit-list{display:flex;flex-direction:column;gap:16rpx}
.unit-row,.single-row{display:flex;align-items:center;justify-content:space-between}
.unit-label{font-size:28rpx;color:$t2}
.unit-input{width:200rpx;height:72rpx;background:rgba($bg,.6);border:2rpx solid rgba($t3,.3);border-radius:12rpx;text-align:center;font-size:28rpx;color:$t1}
.unit-input.large{width:280rpx;font-size:32rpx}
.total-row{display:flex;align-items:center;justify-content:space-between;margin-top:16rpx;padding-top:16rpx;border-top:2rpx solid rgba($t3,.2)}
.total-label{font-size:28rpx;font-weight:600;color:$t1}
.total-val{font-size:28rpx;font-weight:700;color:$p}
.btn-primary{width:100%;height:88rpx;border-radius:20rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:600;margin-bottom:16rpx}
.btn-primary.loading{opacity:.6}
.btn-secondary{font-size:28rpx;color:$t2;padding:8rpx 0}
</style>
