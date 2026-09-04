<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createTransfer } from '@/api/transfer'
import { useUserStore } from '@/store/user'
import { useTransferStore } from '@/store/transfer'
import { fetchStores } from '@/api/store'
import { topUpSubscribeOnce } from '@/utils/subscribe'

const userStore = useUserStore()
const transferStore = useTransferStore()

interface StoreOption { storeId: string; storeName: string }

// 调入地默认当前门店
const toStoreId = ref(userStore.storeId)
const toStoreName = ref(userStore.storeName)

const fromStoreId = ref('')
const fromStoreName = ref('')
const fromStoreSearch = ref('')
const toStoreSearch = ref('')

const allStores = ref<StoreOption[]>([])
const remark = ref('')
const saving = ref(false)

const showToSheet = ref(false)
const showFromSheet = ref(false)

// Delete picker
const showDelSheet = ref(false)
const delMaterialId = ref('')

const filteredFromStores = computed(() => {
  if (!fromStoreSearch.value) return allStores.value
  const kw = fromStoreSearch.value.toLowerCase()
  return allStores.value.filter(s => s.storeName.toLowerCase().includes(kw))
})
const filteredToStores = computed(() => {
  if (!toStoreSearch.value) return allStores.value
  const kw = toStoreSearch.value.toLowerCase()
  return allStores.value.filter(s => s.storeName.toLowerCase().includes(kw))
})

// Items grouped by materialId (as array for v-for compatibility)
const groupedItems = computed(() => {
  const map: Record<string, { mid: string; name: string; units: { unit: string; qty: number; price: number }[] }> = {}
  for (const i of transferStore.selectedItems) {
    if (!map[i.materialId]) map[i.materialId] = { mid: i.materialId, name: i.materialName, units: [] }
    map[i.materialId].units.push({ unit: i.selectedUnit, qty: i.qty, price: (i.unitPrices[i.selectedUnit] || 0) * i.qty })
  }
  return Object.values(map)
})

const canSubmit = computed(() =>
  toStoreId.value && fromStoreId.value && transferStore.selectedItems.length > 0
)

onLoad(async () => {
  try {
    const data = await fetchStores()
    allStores.value = (data || [])
  } catch { /* empty */ }
})

function openFromSheet() { fromStoreSearch.value = ''; showFromSheet.value = true }
function openToSheet() { toStoreSearch.value = ''; showToSheet.value = true }
function selectToStore(store: StoreOption) { toStoreId.value = store.storeId; toStoreName.value = store.storeName; showToSheet.value = false }
function selectFromStore(store: StoreOption) { fromStoreId.value = store.storeId; fromStoreName.value = store.storeName; showFromSheet.value = false }

function goSelectItems() { uni.navigateTo({ url: '/pages/transfer/item-select/index' }) }

function openDelPicker(materialId: string) {
  delMaterialId.value = materialId
  showDelSheet.value = true
}
function deleteUnit(unit: string) {
  transferStore.removeItemByUnit(delMaterialId.value, unit)
  showDelSheet.value = false
}
function deleteAll(materialId: string) {
  transferStore.removeItem(materialId)
}

function itemUnitText(materialId: string): string {
  const units = transferStore.selectedItems.filter(i => i.materialId === materialId)
  return units.map(i => i.qty + i.selectedUnit).join(' ')
}

function itemAmount(materialId: string): string {
  const items = transferStore.selectedItems.filter(i => i.materialId === materialId)
  const total = items.reduce((s, i) => s + (i.unitPrices[i.selectedUnit] || 0) * i.qty, 0)
  return total > 0 ? '¥' + total.toFixed(2) : ''
}

async function submit() {
  if (saving.value || !canSubmit.value) return
  topUpSubscribeOnce() // 调货发起（tap 内）→ 订阅授权充值（对端店长将收到待确认提醒）
  // 校验：调出地或调入地必须有一个是当前门店
  if (fromStoreId.value !== userStore.storeId && toStoreId.value !== userStore.storeId) {
    uni.showToast({ title: '调出地或调入地必须有一个是当前门店', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await createTransfer({
      fromStoreId: fromStoreId.value,
      fromStoreName: fromStoreName.value,
      toStoreId: toStoreId.value,
      toStoreName: toStoreName.value,
      remark: remark.value,
      items: transferStore.getFormItems(),
    })
    transferStore.clearItems()
    uni.showToast({ title: '调货申请已提交', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch { /* handled */ }
  finally { saving.value = false }
}
</script>

<template>
  <view class="page">
    <view class="form-card">
      <view class="form-row"><text class="fr-label">调拨类型</text><text class="fr-value">店间调拨</text></view>
      <view class="form-row tap" @click="openFromSheet()">
        <text class="fr-label">调出地 <text class="req">*</text></text>
        <view class="fr-picker">
          <text :class="{ placeholder: !fromStoreName }">{{ fromStoreName || '请选择门店' }}</text>
          <text class="fr-arrow">›</text>
        </view>
      </view>
      <view class="form-row tap" @click="openToSheet()">
        <text class="fr-label">调入地 <text class="req">*</text></text>
        <view class="fr-picker">
          <text :class="{ placeholder: !toStoreName }">{{ toStoreName || '请选择门店' }}</text>
          <text class="fr-arrow">›</text>
        </view>
      </view>
    </view>

    <view class="form-card">
      <text class="fc-title">单据备注</text>
      <textarea v-model="remark" class="remark-input" placeholder="请填写调拨原因或说明..." :maxlength="100" />
      <text class="remark-count">{{ remark.length }}/100</text>
    </view>

    <view class="form-card">
      <view class="fc-header">
        <text class="fc-title">调拨物料（{{ transferStore.selectedItems.length }}）</text>
        <text class="fc-add" @click="goSelectItems">＋ 添加</text>
      </view>
      <view v-if="transferStore.selectedItems.length" class="item-list">
        <view v-for="g in groupedItems" :key="g.mid" class="item-row">
          <view class="item-info">
            <text class="item-name">{{ g.name }}</text>
            <text class="item-sub">{{ itemUnitText(g.mid) }}</text>
          </view>
          <text v-if="itemAmount(g.mid)" class="item-amt">{{ itemAmount(g.mid) }}</text>
          <view class="item-remove" @click="g.units.length > 1 ? openDelPicker(g.mid) : deleteAll(g.mid)">
            <text>✕</text>
          </view>
        </view>
      </view>
      <view v-else class="empty-items" @click="goSelectItems">
        <text class="empty-icon">📦</text>
        <text class="empty-text">暂无物料，请点击添加</text>
      </view>
    </view>

    <view class="bottom-bar">
      <view class="bb-info">
        <text class="bb-count">已选 {{ transferStore.totalCount }} 物料</text>
        <text v-if="transferStore.totalAmount > 0" class="bb-amount">¥{{ transferStore.totalAmount.toFixed(2) }}</text>
      </view>
      <button class="bb-submit" :disabled="!canSubmit || saving" @click="submit">
        {{ saving ? '提交中...' : '提交申请' }}
      </button>
    </view>

    <!-- 调入地 picker -->
    <view v-if="showToSheet" class="mask" @click="showToSheet = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head"><text class="sh-title">选择调入地</text><text class="sh-close" @click="showToSheet = false">✕</text></view>
        <view class="sheet-search"><input v-model="toStoreSearch" class="sheet-search-input" placeholder="搜索门店名称" /></view>
        <view v-if="filteredToStores.length" class="sheet-list">
          <view v-for="s in filteredToStores" :key="s.storeId" class="sheet-item tap" @click="selectToStore(s)"><text class="si-name">{{ s.storeName }}</text><text class="si-arrow">›</text></view>
        </view>
        <text v-else class="sheet-empty">无匹配门店</text>
      </view>
    </view>

    <!-- 调出地 picker -->
    <view v-if="showFromSheet" class="mask" @click="showFromSheet = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head"><text class="sh-title">选择调出地</text><text class="sh-close" @click="showFromSheet = false">✕</text></view>
        <view class="sheet-search"><input v-model="fromStoreSearch" class="sheet-search-input" placeholder="搜索门店名称" /></view>
        <view v-if="filteredFromStores.length" class="sheet-list">
          <view v-for="s in filteredFromStores" :key="s.storeId" class="sheet-item tap" @click="selectFromStore(s)"><text class="si-name">{{ s.storeName }}</text><text class="si-arrow">›</text></view>
        </view>
        <text v-else class="sheet-empty">无匹配门店</text>
      </view>
    </view>

    <!-- 删除单位选择 -->
    <view v-if="showDelSheet" class="mask" @click="showDelSheet = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head"><text class="sh-title">选择删除</text><text class="sh-close" @click="showDelSheet = false">✕</text></view>
        <view class="sheet-list">
          <view v-for="item in transferStore.selectedItems.filter(i => i.materialId === delMaterialId)" :key="item.selectedUnit" class="sheet-item tap" @click="deleteUnit(item.selectedUnit)">
            <text class="si-name">{{ item.qty }} {{ item.selectedUnit }}</text>
            <text class="si-arrow">🗑</text>
          </view>
          <view class="sheet-item tap del-all" @click="deleteAll(delMaterialId); showDelSheet = false">
            <text class="si-name" style="color:#E05A47">全部删除</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 200rpx}

.form-card{background:$s;border-radius:20rpx;border:2rpx solid $b;padding:0;margin-bottom:24rpx;overflow:hidden}
.form-row{display:flex;justify-content:space-between;align-items:center;padding:28rpx;min-height:100rpx}
.form-row + .form-row{border-top:1px solid #EEF1EF}
.fr-label{font-size:26rpx;color:$t3;flex-shrink:0}
.fr-value{font-size:30rpx;font-weight:500;color:$t1}
.fr-picker{display:flex;align-items:center;gap:8rpx;font-size:30rpx;font-weight:500;color:$t1}
.fr-picker .placeholder{color:$t3;font-weight:400}
.fr-arrow{font-size:36rpx;color:#8C9691}
.req{color:$d}

.fc-title{display:block;font-size:34rpx;font-weight:700;color:$t1;padding:28rpx 28rpx 0}
.fc-header{display:flex;justify-content:space-between;align-items:center;padding:28rpx}
.fc-add{font-size:26rpx;color:$p;font-weight:500}
.remark-input{width:100%;min-height:180rpx;padding:20rpx 28rpx;font-size:28rpx;color:$t1;box-sizing:border-box;background:$s}
.remark-count{display:block;text-align:right;padding:8rpx 28rpx 20rpx;font-size:22rpx;color:$t3}

.item-list{padding:0 28rpx 20rpx}
.item-row{display:flex;align-items:center;padding:20rpx 0;border-bottom:1px solid #EEF1EF}
.item-row:last-child{border-bottom:0}
.item-info{flex:1;min-width:0}
.item-name{display:block;font-size:28rpx;font-weight:600;color:$t1}
.item-sub{display:block;margin-top:4rpx;font-size:24rpx;color:$t2}
.item-amt{font-size:28rpx;font-weight:600;color:$d;margin:0 12rpx;flex-shrink:0}
.item-remove{width:60rpx;height:60rpx;border-radius:50%;background:#FAFBF9;display:flex;align-items:center;justify-content:center;font-size:24rpx;color:$t3;flex-shrink:0}

.empty-items{display:flex;flex-direction:column;align-items:center;justify-content:center;padding:48rpx 28rpx 40rpx}
.empty-icon{font-size:64rpx;opacity:.4}
.empty-text{display:block;margin-top:12rpx;font-size:26rpx;color:$t3}

.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:flex;align-items:center;gap:20rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:rgba(255,255,255,.95);border-top:1px solid $b}
.bb-info{flex:1}
.bb-count{display:block;font-size:24rpx;color:$t3}
.bb-amount{display:block;margin-top:4rpx;font-size:40rpx;font-weight:800;color:$d}
.bb-submit{width:280rpx;height:88rpx;border-radius:16rpx;background:#247847;color:#fff;font-size:30rpx;font-weight:600;border:0;display:flex;align-items:center;justify-content:center}
.bb-submit[disabled]{background:$b;color:#B8C0BC}

.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;border-radius:32rpx 32rpx 0 0;background:$s;padding:28rpx 32rpx calc(env(safe-area-inset-bottom) + 28rpx);max-height:70vh;overflow-y:auto}
.sheet-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:24rpx}
.sh-title{font-size:34rpx;font-weight:700;color:$t1}
.sh-close{font-size:40rpx;color:$t2;padding:8rpx}
.sheet-item{display:flex;justify-content:space-between;align-items:center;padding:24rpx;border:2rpx solid $b;border-radius:12rpx;margin-bottom:12rpx}
.si-name{font-size:30rpx;font-weight:600;color:$t1}
.si-arrow{font-size:36rpx;color:#8C9691}
.sheet-empty{display:block;text-align:center;padding:40rpx;font-size:28rpx;color:$t3}
.sheet-search{padding:0 0 20rpx}
.sheet-search-input{width:100%;height:80rpx;border:2rpx solid $b;border-radius:12rpx;padding:0 20rpx;font-size:28rpx;color:$t1;background:#FAFBF9;box-sizing:border-box}
.del-all{border-color:$d;background:#FFF4F2}
</style>
