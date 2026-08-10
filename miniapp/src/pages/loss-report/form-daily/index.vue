<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { createDailyLoss, updateDailyLoss, getLossDetail, searchMaterials, getContainers, uploadLossImage } from '@/api/loss-report'

const userStore = useUserStore()
const editId = ref(0)
const isEdit = computed(() => editId.value > 0)

// ---- Material Items ----
interface MaterialItem {
  materialId: string
  materialName: string
  spec: string
  category: string
  baseUnit: string
  unitPrice: number | null
  grossWeight: string
  containerId: number | null
  containerName: string
  containerWeight: number
  netWeight: number
}

const items = ref<MaterialItem[]>([])
const reason = ref('过期')
const remark = ref('')
const dailyReasons = ['过期', '破损', '制作损耗', '其他']
const containers = ref<any[]>([])

function getNetWeight(gross: string, tare: number): number {
  const g = parseFloat(gross) || 0
  return Math.round((g - tare) * 10) / 10
}
function getItemAmount(item: MaterialItem): number {
  if (!item.unitPrice || item.netWeight <= 0) return 0
  return item.unitPrice * item.netWeight
}

// ---- Media ----
interface MediaItem { url: string; uploading: boolean; failed: boolean }
const mediaList = ref<MediaItem[]>([])
const uploadedUrls = computed(() => mediaList.value.filter(i => !i.uploading && !i.failed).map(i => i.url))
const submitting = ref(false)

// ---- Search Sheet ----
const showSearch = ref(false)
const searchKey = ref('')
const searchResults = ref<any[]>([])
const searchLoading = ref(false)
let searchTimer: any = null

// ---- Add/Edit Drawer ----
const showDrawer = ref(false)
const editingIndex = ref(-1)
const drawerMaterial = ref<any>(null)
const drawerGross = ref('')
const drawerContainerId = ref<number | null>(null)
const drawerNetWeight = computed(() => getNetWeight(drawerGross.value, getTare(drawerContainerId.value)))
const drawerAmount = computed(() => {
  const price = drawerMaterial.value?.unitPrice || 0
  if (!price || drawerNetWeight.value <= 0) return 0
  return price * drawerNetWeight.value
})

function fmtPrice(p: number): string {
  return '¥' + p.toFixed(4).replace(/0+$/, '').replace(/\.$/, '')
}
function getTare(cid: number | null): number {
  if (cid == null) return 0
  const c = containers.value.find((c: any) => c.id === cid)
  return c ? (c.tareWeight || 0) : 0
}
function getContainer(cid: number | null): any {
  return containers.value.find((c: any) => c.id === cid)
}

// ---- Totals ----
const totalAmount = computed(() => items.value.reduce((sum, item) => sum + getItemAmount(item), 0))
const totalCount = computed(() => items.value.length)

onLoad((options: any) => {
  loadContainers()
  if (options?.editId) {
    editId.value = Number(options.editId)
    loadEditData()
  }
})

async function loadEditData() {
  try {
    const r: any = await getLossDetail(editId.value)
    reason.value = r.reason || '过期'
    remark.value = r.remark || ''
    if (r.voucherUrl) {
      mediaList.value = r.voucherUrl.split(',').filter(Boolean).map((url: string) => ({
        url, uploading: false, failed: false
      }))
    }
    // 多物料明细
    if (r.items?.length) {
      items.value = r.items.map((item: any) => ({
        materialId: item.materialId,
        materialName: item.materialName,
        spec: item.spec || '',
        category: '',
        baseUnit: item.lossObject === 'semi_finished' ? 'g' : (item.inputUnit || ''),
        unitPrice: item.unitPrice || null,
        grossWeight: item.grossWeight != null ? String(item.grossWeight) : '',
        containerId: item.containerId || null,
        containerName: item.containerName || '',
        containerWeight: item.containerWeight || 0,
        netWeight: item.netWeight || 0,
      }))
    } else if (r.materialName) {
      // 存量扁平单物料回退
      items.value = [{
        materialId: r.materialId || '',
        materialName: r.materialName,
        spec: r.spec || '',
        category: '',
        baseUnit: r.lossObject === 'semi_finished' ? 'g' : (r.inputUnit || ''),
        unitPrice: r.unitPrice || null,
        grossWeight: r.grossWeight != null ? String(r.grossWeight) : '',
        containerId: r.containerId || null,
        containerName: r.containerName || '',
        containerWeight: r.containerWeight || 0,
        netWeight: r.netWeight || 0,
      }]
    }
  } catch { uni.showToast({ title: '加载失败', icon: 'none' }) }
}

async function loadContainers() {
  try { containers.value = (await getContainers()) || [] } catch { containers.value = [] }
}

// ---- Search ----
function openSearch() { showSearch.value = true; searchKey.value = ''; searchResults.value = []; loadDefaultMaterials() }
function closeSearch() { showSearch.value = false }

function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = searchKey.value.trim()
  if (!kw) { loadDefaultMaterials(); return }
  searchTimer = setTimeout(async () => {
    searchLoading.value = true
    try { const res: any = await searchMaterials(kw); searchResults.value = res.list || [] } catch { searchResults.value = [] }
    finally { searchLoading.value = false }
  }, 300)
}
async function loadDefaultMaterials() {
  searchLoading.value = true
  try { const res: any = await searchMaterials(''); searchResults.value = res.list || [] } catch { searchResults.value = [] }
  finally { searchLoading.value = false }
}

function openAddDrawer(m: any) {
  if (items.value.some(i => i.materialId === m.materialId && editingIndex.value === -1)) {
    uni.showToast({ title: '该物料已添加', icon: 'none' }); return
  }
  drawerMaterial.value = m; editingIndex.value = -1
  const def = containers.value.find((c: any) => c.alias === '去皮' || c.name === '去皮' || c.tareWeight === 0)
  drawerContainerId.value = def ? def.id : null; drawerGross.value = ''
  showSearch.value = false; showDrawer.value = true
}
function openEditDrawer(idx: number) {
  const item = items.value[idx]
  drawerMaterial.value = { materialId: item.materialId, materialName: item.materialName, spec: item.spec, category: item.category, baseUnit: item.baseUnit, unitPrice: item.unitPrice }
  editingIndex.value = idx; drawerContainerId.value = item.containerId; drawerGross.value = item.grossWeight
  showDrawer.value = true
}
function closeDrawer() { showDrawer.value = false; drawerMaterial.value = null; editingIndex.value = -1 }

function confirmDrawer() {
  const gross = parseFloat(drawerGross.value)
  if (!(gross > 0)) { uni.showToast({ title: '请填写含容器重量', icon: 'none' }); return }
  if (!drawerContainerId.value) { uni.showToast({ title: '请选择容器', icon: 'none' }); return }
  if (drawerNetWeight.value <= 0) { uni.showToast({ title: '净重必须大于0', icon: 'none' }); return }
  const m = drawerMaterial.value!
  const c = getContainer(drawerContainerId.value)
  const item: MaterialItem = {
    materialId: m.materialId, materialName: m.materialName, spec: m.spec || '', category: m.category || '',
    baseUnit: m.baseUnit || '', unitPrice: m.unitPrice || null,
    grossWeight: drawerGross.value, containerId: drawerContainerId.value,
    containerName: c ? (c.alias || c.name) : '', containerWeight: c ? (c.tareWeight || 0) : 0,
    netWeight: drawerNetWeight.value,
  }
  if (editingIndex.value >= 0) { items.value[editingIndex.value] = item } else { items.value.push(item) }
  closeDrawer()
}
function removeItem(idx: number) { items.value.splice(idx, 1) }

// ---- Media ----
function chooseImage() {
  const remaining = 20 - mediaList.value.length
  if (remaining <= 0) { uni.showToast({ title: '最多20张图片', icon: 'none' }); return }
  uni.chooseImage({ count: Math.min(remaining, 9), sizeType: ['compressed'], success: (res) => {
    for (const p of res.tempFilePaths) {
      if (mediaList.value.length >= 20) break
      const item: MediaItem = { url: p, uploading: true, failed: false }; const idx = mediaList.value.length
      mediaList.value.push(item)
      uploadLossImage(p, userStore.storeName).then(url => { mediaList.value.splice(idx, 1, { url, uploading: false, failed: false }) }).catch(() => { mediaList.value.splice(idx, 1, { ...item, uploading: false, failed: true }) })
    }
  }})
}
function removeMedia(idx: number) { mediaList.value.splice(idx, 1) }
function retryMedia(item: MediaItem, idx: number) {
  mediaList.value.splice(idx, 1, { ...item, uploading: true, failed: false })
  uploadLossImage(item.url, userStore.storeName).then(url => { mediaList.value.splice(idx, 1, { url, uploading: false, failed: false }) }).catch(() => { mediaList.value.splice(idx, 1, { ...item, uploading: false, failed: true }) })
}
function previewImage(url: string) { uni.previewImage({ urls: [url], current: url }) }

// ---- Submit ----
async function submitLoss() {
  if (submitting.value) return
  if (items.value.length === 0) { uni.showToast({ title: '请添加报损物料', icon: 'none' }); return }
  if (!reason.value) { uni.showToast({ title: '请选择报损原因', icon: 'none' }); return }
  if (mediaList.value.length === 0) { uni.showToast({ title: '请上传现场凭证', icon: 'none' }); return }
  if (mediaList.value.some(m => m.failed)) { uni.showToast({ title: '有图片上传失败，请重试或删除', icon: 'none' }); return }
  if (mediaList.value.some(m => m.uploading)) { uni.showToast({ title: '图片上传中，请稍候', icon: 'none' }); return }
  submitting.value = true
  try {
    const payload = { reason: reason.value, remark: remark.value, voucherUrl: uploadedUrls.value.join(','),
      items: items.value.map(item => ({ materialId: item.materialId, materialName: item.materialName, spec: item.spec, lossObject: 'semi_finished', grossWeight: parseFloat(item.grossWeight), containerId: item.containerId })),
    }
    if (isEdit.value) { await updateDailyLoss(editId.value, payload) }
    else { await createDailyLoss(payload) }
    uni.showToast({ title: isEdit.value ? '修改成功' : '提交成功', icon: 'success' }); setTimeout(() => uni.navigateBack(), 800)
  } catch { uni.showToast({ title: '提交失败', icon: 'none' }) }
  finally { submitting.value = false }
}
</script>

<template>
  <view class="page">
    <view class="header"><text class="header-title">{{ isEdit ? '编辑报损' : '新增报损' }}</text></view>
    <view class="body">
      <!-- 报损商品 -->
      <view class="card">
        <text class="card-label">报损商品 <text class="req">*</text></text>
        <view v-if="items.length === 0" class="empty-hint">请添加报损物料</view>
        <view v-for="(item, idx) in items" :key="item.materialId" class="material-card" @click="openEditDrawer(idx)">
          <view class="mc-left">
            <text class="mc-name">{{ item.materialName }}</text>
            <text v-if="item.spec" class="mc-spec">{{ item.spec }}</text>
          </view>
          <view class="mc-right">
            <text class="mc-net">净重 {{ item.netWeight }}g</text>
            <text v-if="item.unitPrice && item.netWeight > 0" class="mc-amount">¥{{ getItemAmount(item).toFixed(2) }}</text>
          </view>
          <text class="mc-del" @click.stop="removeItem(idx)">✕</text>
        </view>
        <view class="add-material-btn" @click="openSearch" v-if="items.length < 20"><text class="add-icon">＋</text><text class="add-text">添加物料（{{ items.length }}/20）</text></view>
        <view v-else class="add-material-hint">最多添加 20 种物料</view>
      </view>

      <!-- 报损原因 -->
      <view class="card">
        <text class="card-label">报损原因 <text class="req">*</text></text>
        <scroll-view scroll-x enhanced show-scrollbar="false">
          <view class="reason-pills"><view v-for="r in dailyReasons" :key="r" class="rp-pill" :class="{ on: reason === r }" @click="reason = r">{{ r }}</view></view>
        </scroll-view>
      </view>

      <!-- 备注 -->
      <view class="card">
        <text class="card-label">备注</text>
        <textarea class="fta" v-model="remark" placeholder="补充描述，有助于更好的处理问题" />
      </view>

      <!-- 图片上传 -->
      <view class="card">
        <text class="card-label">上传图片 <text class="req">*</text></text>
        <text class="photo-count">已上传 {{ uploadedUrls.length }}/20</text>
        <view class="photo-grid">
          <view class="photo-upload" @click="chooseImage" v-if="mediaList.length < 20"><text class="pu-icon">📷</text><text class="pu-txt">上传图片</text></view>
          <view v-for="(item, idx) in mediaList" :key="idx" class="photo-thumb" @click="!item.failed && !item.uploading && previewImage(item.url)">
            <image :src="item.url" mode="aspectFill" class="pt-img" />
            <view v-if="item.uploading" class="pt-loading"><text>⋯</text></view>
            <view v-if="item.failed" class="pt-retry" @click.stop="retryMedia(item, idx)"><text>↻</text></view>
            <text class="pt-del" @click.stop="removeMedia(idx)">✕</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Bottom Bar -->
    <view class="bottom-bar">
      <view class="bb-left"><text class="bb-amount-label">报损金额</text><text class="bb-amount">¥{{ totalAmount.toFixed(2) }}</text></view>
      <view class="bb-mid"><text class="bb-qty">共{{ totalCount }}种物料</text></view>
      <view class="bb-btn" @click="submitLoss">{{ submitting ? '提交中...' : (isEdit ? '提交修改' : '确认') }}</view>
    </view>

    <!-- Search Sheet -->
    <view v-if="showSearch" class="sheet-mask" @click="closeSearch">
      <view class="search-sheet" @click.stop>
        <view class="ss-head"><text class="ss-title">添加物料</text><text class="ss-close" @click="closeSearch">✕</text></view>
        <view class="ss-body">
          <input class="ss-search" v-model="searchKey" placeholder="搜索物料名称" @input="onSearchInput" />
          <scroll-view scroll-y class="ss-list">
            <view v-if="searchLoading" class="ss-loading">搜索中...</view>
            <view v-for="m in searchResults" :key="m.materialId" class="ss-item" @click="openAddDrawer(m)">
              <view class="ss-item-head"><text class="ss-name">{{ m.materialName }}</text><text v-if="m.category" class="ss-category">{{ m.category }}</text></view>
              <text v-if="m.spec" class="ss-spec">{{ m.spec }}</text>
            </view>
            <view v-if="!searchLoading && searchResults.length === 0 && searchKey" class="ss-empty">暂无匹配物料</view>
          </scroll-view>
        </view>
      </view>
    </view>

    <!-- Add/Edit Drawer -->
    <view v-if="showDrawer" class="sheet-mask" @click="closeDrawer">
      <view class="drawer" @click.stop>
        <view class="dr-handle"></view>
        <view class="dr-head">
          <view class="dr-name-row">
            <text class="dr-name">{{ drawerMaterial?.materialName }}</text>
            <text v-if="drawerMaterial?.category" class="dr-category">{{ drawerMaterial.category }}</text>
          </view>
          <text v-if="drawerMaterial?.spec" class="dr-spec">{{ drawerMaterial?.spec }}</text>
          <text v-if="drawerMaterial?.unitPrice" class="dr-price">{{ fmtPrice(drawerMaterial.unitPrice) }}/{{ drawerMaterial.baseUnit }}</text>
        </view>

        <!-- 容器选择 -->
        <view class="dr-section">
          <text class="dr-label">容器（去皮）</text>
          <scroll-view scroll-x enhanced show-scrollbar="false" class="dr-container-scroll">
            <view class="dr-container-pills">
              <view v-for="c in containers" :key="c.id" class="rc-pill" :class="{ on: drawerContainerId === c.id }" @click="drawerContainerId = c.id">{{ c.alias || c.name }}</view>
            </view>
          </scroll-view>
          <view v-if="getContainer(drawerContainerId)" class="dr-container-detail">
            <view class="cd-icon" @click="getContainer(drawerContainerId).image && previewImage(getContainer(drawerContainerId).image)">
              <image v-if="getContainer(drawerContainerId).image" :src="getContainer(drawerContainerId).image" mode="aspectFill" class="cd-img" />
              <text v-else class="cd-placeholder">📦</text>
            </view>
            <view class="cd-info">
              <text class="cd-name">{{ getContainer(drawerContainerId).alias || getContainer(drawerContainerId).name }}</text>
              <text class="cd-desc">{{ getContainer(drawerContainerId).name }} · 约{{ getContainer(drawerContainerId).tareWeight }}g</text>
            </view>
          </view>
        </view>

        <!-- 含容器重量 + 净重 -->
        <view class="dr-section">
          <text class="dr-label">含容器重量</text>
          <view class="dr-gross-row">
            <view class="dr-gross-input">
              <input class="fii" v-model="drawerGross" type="digit" placeholder="0" focus />
              <text class="unit-suffix">g</text>
            </view>
            <text v-if="parseFloat(drawerGross) > 0" class="dr-net-inline">净重 {{ drawerNetWeight }}g</text>
          </view>
          <!-- 计算公式 -->
          <view v-if="parseFloat(drawerGross) > 0" class="dr-formula">
            净重 {{ drawerNetWeight }}g = 含容器重量 {{ parseFloat(drawerGross) || 0 }}g - {{ getContainer(drawerContainerId)?.alias || getContainer(drawerContainerId)?.name || '容器' }} {{ getTare(drawerContainerId) }}g
          </view>
        </view>

        <!-- 金额 -->
        <view v-if="drawerAmount > 0" class="dr-amount-row">
          <text class="dr-amount-label">金额</text>
          <text class="dr-amount-val">¥{{ drawerAmount.toFixed(2) }}</text>
        </view>

        <view class="dr-btn" @click="confirmDrawer">{{ editingIndex >= 0 ? '确认修改' : '确认添加' }}</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$s:#fff;$bg:#F7F8F6;$w:#E58A2D;$d:#E05A47;
.req{color:$d;margin-left:4rpx}
.page{min-height:100vh;background:$bg;padding-bottom:140rpx}
.header{padding:24rpx 32rpx;background:$s}.header-title{font-size:36rpx;font-weight:700;color:$t1}
.body{padding:24rpx;display:flex;flex-direction:column;gap:20rpx}
.card{background:$s;border-radius:16rpx;padding:24rpx;border:1px solid $b}
.card-label{display:block;font-size:28rpx;color:$t1;font-weight:600;margin-bottom:20rpx}
.card-divider{height:2rpx;background:$b;margin:12rpx 0 4rpx}

// Material list
.empty-hint{font-size:26rpx;color:$t3;padding:20rpx 0}
.material-card{display:flex;align-items:center;gap:12rpx;padding:20rpx 0;border-bottom:1px solid #F5F6F4}
.material-card:first-of-type{padding-top:0}
.mc-left{flex:1;min-width:0;display:flex;flex-direction:column;gap:2rpx}
.mc-name{font-size:28rpx;font-weight:600;color:$t1}
.mc-spec{font-size:22rpx;color:$t3}
.mc-right{display:flex;flex-direction:column;align-items:flex-end;gap:4rpx;flex-shrink:0}
.mc-net{font-size:24rpx;font-weight:600;color:$p}
.mc-amount{font-size:26rpx;font-weight:700;color:$w}
.mc-del{font-size:36rpx;color:$t3;padding:8rpx;flex-shrink:0}

.add-material-btn{display:flex;align-items:center;justify-content:center;gap:8rpx;padding:20rpx 0 4rpx;color:$p;font-size:26rpx}.add-material-hint{text-align:center;padding:20rpx 0 4rpx;font-size:24rpx;color:$t3}
.add-icon{font-size:28rpx}.add-text{font-weight:500}
.fta{width:100%;height:110rpx;padding:12rpx 16rpx;border:1px solid $b;border-radius:12rpx;font-size:26rpx;background:#FAFBF9;box-sizing:border-box}

// Photo
.photo-count{display:block;font-size:22rpx;color:$t3;margin-bottom:8rpx}
.photo-grid{display:flex;flex-wrap:wrap;gap:12rpx}
.photo-upload{width:160rpx;height:160rpx;border-radius:12rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8rpx;background:#F5F5F5;border:1px dashed $b}
.pu-icon{font-size:48rpx;opacity:.6}.pu-txt{font-size:22rpx;color:$t3}
.photo-thumb{width:160rpx;height:160rpx;border-radius:12rpx;overflow:hidden;position:relative}.pt-img{width:100%;height:100%}
.pt-loading{position:absolute;inset:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;color:#fff;font-size:40rpx}
.pt-retry{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.4);color:#fff;font-size:48rpx}
.pt-del{position:absolute;top:6rpx;right:6rpx;width:40rpx;height:40rpx;border-radius:50%;background:rgba(0,0,0,.5);color:#fff;display:flex;align-items:center;justify-content:center;font-size:24rpx;z-index:1}

// Bottom Bar
.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:flex;align-items:center;gap:16rpx;padding:16rpx 24rpx calc(env(safe-area-inset-bottom) + 16rpx);background:$s;box-shadow:0 -4rpx 20rpx rgba(0,0,0,.06)}
.bb-left{display:flex;flex-direction:column;gap:2rpx}.bb-amount-label{font-size:22rpx;color:$t2}.bb-amount{font-size:34rpx;font-weight:800;color:$w}
.bb-mid{flex:1}.bb-qty{font-size:24rpx;color:$w}
.bb-btn{flex-shrink:0;padding:0 72rpx;height:80rpx;border-radius:48rpx;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700;background:$p;color:#fff}

// Reason pills
.reason-pills{display:flex;gap:12rpx;white-space:nowrap}
.rp-pill{flex-shrink:0;padding:10rpx 28rpx;border-radius:999rpx;font-size:26rpx;background:$s;color:$t2;border:1.5px solid $b}
.rp-pill.on{background:$ps;color:$p;border-color:$p}

// Search Sheet
.sheet-mask{position:fixed;inset:0;z-index:200;background:rgba(31,36,33,.4);display:flex;align-items:flex-end}
.search-sheet{width:100%;max-height:80vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column}
.ss-head{display:flex;align-items:center;justify-content:space-between;padding:24rpx 32rpx;border-bottom:1px solid $b}
.ss-title{font-size:30rpx;font-weight:700;color:$t1}.ss-close{font-size:40rpx;color:$t3;padding:8rpx}
.ss-body{padding:24rpx 32rpx;flex:1;overflow:hidden;display:flex;flex-direction:column;gap:16rpx}
.ss-search{width:100%;height:80rpx;padding:0 20rpx;border:1px solid $b;border-radius:12rpx;font-size:28rpx;background:#FAFBF9;box-sizing:border-box}
.ss-list{flex:1;max-height:500rpx}
.ss-item{padding:20rpx 0;border-bottom:1px solid #F5F6F4;display:flex;flex-direction:column;gap:4rpx}
.ss-item-head{display:flex;align-items:center;gap:12rpx}.ss-name{font-size:28rpx;font-weight:500;color:$t1}.ss-spec{font-size:24rpx;color:$t3}
.ss-category{flex-shrink:0;padding:2rpx 14rpx;border-radius:999rpx;font-size:20rpx;color:$p;background:$ps}
.ss-loading,.ss-empty{text-align:center;padding:40rpx;font-size:26rpx;color:$t3}

// Drawer
.drawer{width:100%;max-height:85vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column;padding-bottom:env(safe-area-inset-bottom);overflow-y:auto}
.dr-handle{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:20rpx auto}
.dr-head{padding:0 32rpx 24rpx;text-align:center}
.dr-name-row{display:flex;align-items:center;justify-content:center;gap:12rpx}
.dr-name{font-size:32rpx;font-weight:700;color:$t1}
.dr-spec{display:block;font-size:24rpx;color:$t3;margin-top:4rpx}
.dr-category{flex-shrink:0;padding:4rpx 16rpx;border-radius:999rpx;font-size:22rpx;color:$p;background:$ps}
.dr-price{display:block;font-size:24rpx;color:$w;font-weight:500;margin-top:4rpx}
.dr-section{padding:0 32rpx;margin-bottom:24rpx}
.dr-label{display:block;font-size:26rpx;color:$t2;margin-bottom:12rpx}

// Container pills (text capsules)
.dr-container-scroll{white-space:nowrap}
.dr-container-pills{display:flex;gap:10rpx;padding-right:24rpx}
.rc-pill{flex-shrink:0;padding:14rpx 28rpx;border-radius:999rpx;border:1.5px solid $b;font-size:26rpx;color:$t2;background:$s}
.rc-pill.on{border-color:$p;color:$p;background:$ps}
// Container detail card
.dr-container-detail{display:flex;align-items:center;gap:16rpx;margin-top:16rpx;padding:16rpx;border-radius:14rpx;background:#FAFBF9;border:1px solid $b}
.cd-icon{width:88rpx;height:88rpx;border-radius:16rpx;background:$s;display:flex;align-items:center;justify-content:center;flex-shrink:0;border:1px solid $b}
.cd-img{width:88rpx;height:88rpx;border-radius:16rpx}.cd-placeholder{font-size:44rpx}
.cd-info{flex:1}.cd-name{display:block;font-size:28rpx;font-weight:600;color:$t1}.cd-desc{display:block;font-size:24rpx;color:$t3;margin-top:4rpx}

// Weight row
.dr-gross-row{display:flex;align-items:center;gap:16rpx}
.dr-gross-input{flex:1;position:relative}
.fii{width:100%;height:80rpx;padding:0 20rpx;border:1px solid $b;border-radius:12rpx;font-size:28rpx;background:#FAFBF9;box-sizing:border-box}
.unit-suffix{position:absolute;right:20rpx;top:50%;transform:translateY(-50%);font-size:26rpx;color:$t2}
.dr-net-inline{font-size:26rpx;font-weight:600;color:$p;white-space:nowrap}
.dr-formula{padding:12rpx 16rpx;margin-top:10rpx;border-radius:10rpx;background:$ps;font-size:22rpx;color:$p;line-height:1.5}

// Amount (orange, same as unit price)
.dr-amount-row{display:flex;justify-content:space-between;align-items:center;padding:16rpx 32rpx;margin:0 32rpx 24rpx;background:#FFF8EE;border-radius:12rpx}
.dr-amount-label{font-size:26rpx;color:$w}
.dr-amount-val{font-size:30rpx;font-weight:700;color:$w}

.dr-btn{margin:0 32rpx 32rpx;height:88rpx;border-radius:16rpx;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700;background:$p;color:#fff}
</style>
