<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { searchTransferMaterials, type MaterialSearchItem } from '@/api/transfer'
import { useTransferStore } from '@/store/transfer'
import DrawerNumericKeyboard from '@/components/DrawerNumericKeyboard.vue'

const store = useTransferStore()

const keyword = ref('')
const materials = ref<MaterialSearchItem[]>([])
const loading = ref(false)

// Entry drawer
const showDrawer = ref(false)
const drawerItem = ref<MaterialSearchItem | null>(null)
const drawerUnit = ref('')
const drawerQty = ref('')
const drawerPrice = ref('')
const drawerUnitInputs = ref<Record<string, string>>({})

// Cart drawer
const showCart = ref(false)

onLoad(() => { loadMaterials() })

async function loadMaterials() {
  loading.value = true
  try {
    const data = await searchTransferMaterials(keyword.value)
    materials.value = data.list || []
  } finally { loading.value = false }
}

let searchTimer: any = null
function onKeywordInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadMaterials, 300)
}

// --- Entry drawer ---
function openDrawer(item: MaterialSearchItem) {
  drawerItem.value = item
  drawerUnit.value = item.units[0] || item.baseUnit || ''
  drawerQty.value = ''
  drawerPrice.value = ''
  // 回填已录入的数量
  drawerUnitInputs.value = {}
  const existing = store.selectedItems.filter(i => i.materialId === item.materialId)
  for (const u of item.units) {
    const exist = existing.find(e => e.selectedUnit === u)
    drawerUnitInputs.value[u] = exist ? String(exist.qty) : ''
  }
  updateDrawerSummary()
  showDrawer.value = true
}

function onKeyboardInput(val: string) {
  drawerUnitInputs.value = { ...drawerUnitInputs.value, [drawerUnit.value]: val }
  updateDrawerSummary()
}

function selectUnit(u: string) {
  drawerUnit.value = u
  updateDrawerSummary()
}

function updateDrawerSummary() {
  const item = drawerItem.value
  if (!item) return
  let totalBaseQty = 0
  const unitFactors: Record<string, number> = {}
  for (const u of item.units) {
    if (u === item.baseUnit) { unitFactors[u] = 1; continue }
    const bp = item.unitPrices?.[item.baseUnit] || 0
    const up = item.unitPrices?.[u] || 0
    unitFactors[u] = bp > 0 ? up / bp : 1
  }
  for (const [u, val] of Object.entries(drawerUnitInputs.value)) {
    const q = parseFloat(val) || 0
    totalBaseQty += q * (unitFactors[u] || 1)
  }
  drawerQty.value = totalBaseQty > 0 ? fmtQty(totalBaseQty) + ' ' + item.baseUnit : '0 ' + item.baseUnit
  const unitPrice = item.unitPrices?.[item.baseUnit]
  drawerPrice.value = unitPrice && totalBaseQty > 0 ? (unitPrice * totalBaseQty).toFixed(2) : ''
}

function fmtQty(n: number) {
  if (!n) return '0'
  return n.toFixed(3).replace(/.?0+$/, '').replace(/(\..*?)0+$/, '$1')
}

function confirmAdd() {
  const item = drawerItem.value
  if (!item) return
  let totalBaseQty = 0
  const entries: { unit: string; qty: number }[] = []
  for (const u of item.units) {
    const q = parseFloat(drawerUnitInputs.value[u] || '0') || 0
    if (q > 0) entries.push({ unit: u, qty: q })
  }
  const unitFactors: Record<string, number> = {}
  for (const u of item.units) {
    if (u === item.baseUnit) { unitFactors[u] = 1; continue }
    const bp = item.unitPrices?.[item.baseUnit] || 0
    const up = item.unitPrices?.[u] || 0
    unitFactors[u] = bp > 0 ? up / bp : 1
  }
  for (const e of entries) totalBaseQty += e.qty * (unitFactors[e.unit] || 1)
  if (totalBaseQty <= 0) {
    uni.showToast({ title: '请输入数量', icon: 'none' })
    return
  }
  // 先清空该物料已有记录，再重新写入
  store.removeItem(item.materialId)
  for (const e of entries) {
    store.addItem({ materialId: item.materialId, materialName: item.materialName, qmCode: item.qmCode, spec: item.spec, baseUnit: item.baseUnit, unitPrices: item.unitPrices || {}, selectedUnit: e.unit })
    const added = store.selectedItems.find(i => i.materialId === item.materialId && i.selectedUnit === e.unit)
    if (added) added.qty = e.qty
  }
  closeDrawer()
}

function closeDrawer() {
  showDrawer.value = false
  drawerItem.value = null
  drawerUnit.value = ''
  drawerQty.value = ''
  drawerPrice.value = ''
  drawerUnitInputs.value = {}
}

// --- Material list helpers ---
function itemQtyDetail(materialId: string): string {
  return store.selectedItems.filter(i => i.materialId === materialId).map(i => i.qty + i.selectedUnit).join(' ')
}

function itemAmount(materialId: string): string {
  const items = store.selectedItems.filter(i => i.materialId === materialId)
  const total = items.reduce((s, i) => s + (i.unitPrices[i.selectedUnit] || 0) * i.qty, 0)
  return total > 0 ? '¥' + total.toFixed(2) : ''
}

// --- Cart drawer ---
function openCart() { showCart.value = true }
function closeCart() { showCart.value = false }
function removeCartItem(materialId: string) { store.removeItem(materialId) }
function removeCartUnit(materialId: string, unit: string) { store.removeItemByUnit(materialId, unit) }
function done() { uni.navigateBack() }
</script>

<template>
  <view class="page">
    <view class="search-bar">
      <view class="search-inner">
        <text class="search-icon">🔍</text>
        <input v-model="keyword" class="search-input" placeholder="搜索物料名" @input="onKeywordInput" />
      </view>
    </view>

    <scroll-view scroll-y class="mat-list">
      <view v-if="loading" class="mat-loading"><text>加载中...</text></view>
      <view v-else-if="materials.length === 0 && !keyword" class="mat-empty"><text>输入关键词搜索物料</text></view>
      <view v-else-if="materials.length === 0" class="mat-empty"><text>暂无物料</text></view>
      <view v-for="item in materials" :key="item.materialId" class="mat-card" @click="openDrawer(item)">
        <view class="mc-info">
          <text class="mc-name">{{ item.materialName }}</text>
          <text v-if="item.spec" class="mc-spec">{{ item.spec }}</text>
        </view>
        <view v-if="itemQtyDetail(item.materialId)" class="mc-right">
          <view class="mc-r-row">
            <view class="mc-r-info">
              <text class="mc-qty-detail">{{ itemQtyDetail(item.materialId) }}</text>
              <text v-if="itemAmount(item.materialId)" class="mc-amt">{{ itemAmount(item.materialId) }}</text>
            </view>
            <text class="mc-del" @click.stop="removeCartItem(item.materialId)">✕</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view class="bb-icon" @click="openCart"><text>🛒</text></view>
      <view class="bb-info">
        <text class="bb-label">已选 {{ store.totalCount }} 品项</text>
        <text v-if="store.totalAmount > 0" class="bb-amount">¥{{ store.totalAmount.toFixed(2) }}</text>
      </view>
      <button class="bb-submit" @click="done">选好了</button>
    </view>

    <!-- Entry Drawer -->
    <view v-if="showDrawer" class="mask" @click="closeDrawer">
      <view class="drawer" @click.stop>
        <view class="dr-handle"></view>
        <view class="dr-head">
          <text class="dr-name">{{ drawerItem?.materialName }}</text>
          <text v-if="drawerItem?.spec" class="dr-spec">{{ drawerItem.spec }}</text>
        </view>
        <view class="dr-grid">
          <view v-for="info in (drawerItem?.unitInfos || [])" :key="info.unit" class="dr-cell" :class="{ active: drawerUnit === info.unit }" @click="selectUnit(info.unit)">
            <view class="drc-top">
              <text class="drc-unit">{{ info.unit }}</text>
              <text class="drc-qty">{{ drawerUnitInputs[info.unit] || '0' }}</text>
            </view>
            <text class="drc-hint">{{ info.hint }}</text>
          </view>
        </view>
        <view class="dr-summary" v-if="drawerPrice">
          <text class="drs-label">合计</text>
          <text class="drs-val">¥{{ drawerPrice }}</text>
        </view>
        <DrawerNumericKeyboard :model-value="drawerUnitInputs[drawerUnit] || ''" confirm-text="确认录入" @update:model-value="onKeyboardInput" @confirm="confirmAdd" />
      </view>
    </view>

    <!-- Cart Drawer -->
    <view v-if="showCart" class="mask" @click="closeCart">
      <view class="cart-drawer" @click.stop>
        <view class="cart-handle"></view>
        <view class="cart-head">
          <text class="cart-title">已选物料</text>
          <text class="cart-close" @click="closeCart">✕</text>
        </view>
        <view class="cart-list">
          <view v-if="store.selectedItems.length === 0" class="cart-empty">暂无物料</view>
          <view v-for="item in store.selectedItems" :key="item.materialId + '_' + item.selectedUnit" class="cart-item">
            <view class="cart-item-info">
              <text class="cart-item-name">{{ item.materialName }}</text>
              <text class="cart-item-detail">{{ item.qty }} {{ item.selectedUnit }}</text>
            </view>
            <view class="cart-item-right">
              <text class="cart-item-amt">¥{{ ((item.unitPrices[item.selectedUnit] || 0) * item.qty).toFixed(2) }}</text>
              <text class="cart-item-del" @click="removeCartUnit(item.materialId, item.selectedUnit)">✕</text>
            </view>
          </view>
        </view>
        <view class="cart-foot">
          <text class="cart-foot-label">合计</text>
          <text class="cart-foot-val">¥{{ store.totalAmount.toFixed(2) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$so:#FAFBF9;
.page{display:flex;flex-direction:column;height:100vh;background:$bg}

.search-bar{padding:20rpx 32rpx;background:$bg;flex-shrink:0}
.search-inner{display:flex;align-items:center;height:88rpx;border:2rpx solid $b;border-radius:16rpx;background:$so;padding:0 24rpx;box-sizing:border-box}
.search-icon{font-size:32rpx;margin-right:16rpx;flex-shrink:0}
.search-input{flex:1;min-width:0;height:88rpx;font-size:28rpx;color:$t1;background:transparent}

.mat-list{flex:1;padding:0 32rpx;box-sizing:border-box;overflow:hidden}
.mat-loading,.mat-empty{display:flex;justify-content:center;padding:60rpx 0;font-size:26rpx;color:$t3}
.mat-card{display:flex;align-items:center;background:$s;border-radius:16rpx;border:2rpx solid $b;padding:20rpx 24rpx;margin-bottom:12rpx;box-sizing:border-box}
.mc-info{flex:1;min-width:0}
.mc-name{font-size:28rpx;font-weight:500;color:$t1;display:block}
.mc-spec{font-size:22rpx;color:$t3;margin-top:4rpx}
.mc-right{margin-left:12rpx;flex-shrink:0}
.mc-r-row{display:flex;align-items:center;gap:12rpx}
.mc-r-info{text-align:right}
.mc-qty-detail{display:block;font-size:26rpx;font-weight:600;color:$t1}
.mc-amt{display:block;margin-top:4rpx;font-size:24rpx;color:$d}
.mc-del{font-size:28rpx;color:$t3;flex-shrink:0}

.bottom-bar{display:flex;align-items:center;gap:20rpx;padding:16rpx 32rpx calc(env(safe-area-inset-bottom) + 16rpx);background:$s;border-top:1px solid $b;flex-shrink:0}
.bb-icon{width:80rpx;height:80rpx;border-radius:50%;background:$ps;display:flex;align-items:center;justify-content:center;flex-shrink:0;font-size:40rpx}
.bb-info{flex:1;min-width:0}
.bb-label{display:block;font-size:24rpx;color:$t3}
.bb-amount{display:block;margin-top:4rpx;font-size:44rpx;font-weight:800;color:$d}
.bb-submit{width:240rpx;height:88rpx;border-radius:16rpx;background:$p;color:#fff;font-size:30rpx;font-weight:600;border:0}

/* Entry Drawer */
.mask{position:fixed;inset:0;z-index:200;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.drawer{width:100%;background:$s;border-radius:32rpx 32rpx 0 0;display:flex;flex-direction:column}
.dr-handle{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:16rpx auto;flex-shrink:0}
.dr-head{padding:4rpx 32rpx 20rpx;flex-shrink:0}
.dr-name{font-size:34rpx;font-weight:700;color:$t1;display:block}
.dr-spec{font-size:26rpx;color:$t2;margin-top:4rpx}
.dr-grid{display:grid;grid-template-columns:1fr 1fr;gap:16rpx;padding:0 32rpx 16rpx;flex-shrink:0}
.dr-cell{padding:20rpx 16rpx;border-radius:16rpx;border:2rpx solid $b;background:$s}
.dr-cell.active{background:$ps;border-color:$p}
.drc-top{display:flex;justify-content:space-between;align-items:baseline}
.drc-unit{font-size:30rpx;font-weight:700;color:$t1}
.drc-qty{font-size:28rpx;font-weight:700;color:$p}
.drc-hint{display:block;margin-top:6rpx;font-size:22rpx;color:$t2}
.dr-summary{display:flex;justify-content:space-between;align-items:center;padding:16rpx 32rpx;background:$ps;flex-shrink:0}
.drs-label{font-size:28rpx;font-weight:600;color:$t1}
.drs-val{font-size:32rpx;font-weight:800;color:$p}

/* Cart Drawer */
.cart-drawer{width:100%;max-height:60vh;background:$s;border-radius:32rpx 32rpx 0 0;display:flex;flex-direction:column}
.cart-handle{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:16rpx auto;flex-shrink:0}
.cart-head{display:flex;justify-content:space-between;align-items:center;padding:8rpx 32rpx 20rpx;flex-shrink:0}
.cart-title{font-size:34rpx;font-weight:700;color:$t1}
.cart-close{font-size:40rpx;color:$t2;padding:8rpx}
.cart-list{flex:1;padding:0 32rpx;max-height:400rpx;overflow-y:auto}
.cart-empty{text-align:center;padding:60rpx 0;font-size:26rpx;color:$t3}
.cart-item{display:flex;align-items:center;padding:20rpx 0;border-bottom:1px solid #EEF1EF}
.cart-item-info{flex:1;min-width:0}
.cart-item-name{display:block;font-size:28rpx;font-weight:500;color:$t1}
.cart-item-detail{display:block;margin-top:4rpx;font-size:24rpx;color:$t2}
.cart-item-right{display:flex;align-items:center;gap:16rpx;flex-shrink:0}
.cart-item-amt{font-size:28rpx;font-weight:600;color:$d}
.cart-item-del{width:56rpx;height:56rpx;border-radius:50%;background:transparent;display:flex;align-items:center;justify-content:center;font-size:36rpx;color:$t3;flex-shrink:0}
.cart-foot{display:flex;justify-content:space-between;align-items:center;padding:16rpx 32rpx calc(env(safe-area-inset-bottom) + 16rpx);border-top:1px solid $b;flex-shrink:0}
.cart-foot-label{font-size:28rpx;font-weight:600;color:$t1}
.cart-foot-val{font-size:40rpx;font-weight:800;color:$d}
</style>
