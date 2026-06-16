<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchZoneMaterials, saveZone, itemSave } from '@/api/zone'
import { fetchCandidates, addMaterial, removeMaterial, sortMaterials } from '@/api/material'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'

const taskId = ref(0)
const zoneId = ref(0)
const zoneName = ref('')
const storeName = ref('')
const taskName = ref('')
const loading = ref(true)
const saving = ref(false)
const materials = ref<any[]>([])
const searchKey = ref('')
const filterTab = ref<'pending'|'all'|'done'>('pending')
const showPending = ref<boolean|undefined>(undefined) // 全部tab下待盘区域折叠状态
const showDone = ref<boolean|undefined>(undefined)   // 全部tab下已盘区域折叠状态

const drawerItem = ref<any>(null)
const drawerUnitInputs = ref<Record<string,string>>({})

const editMode = ref(false)
const editList = ref<any[]>([])
const selected = ref<Set<string>>(new Set())
const deletedIds = ref<Set<string>>(new Set())
const listKey = ref(0)
const editingIdx = ref(-1)
const editValue = ref('')

onLoad((options: any) => {
  taskId.value = Number(options.taskId); zoneId.value = Number(options.zoneId)
  zoneName.value = options.zoneName ? decodeURIComponent(options.zoneName) : ''
  storeName.value = options.storeName ? decodeURIComponent(options.storeName) : ''
  taskName.value = options.taskName ? decodeURIComponent(options.taskName) : ''
  uni.setNavigationBarTitle({ title: '物料盘点' })
  drawerItem.value = null; addSearchVisible.value = false; showEditConfirm.value = false; editMode.value = false
  loadMaterials()
})

async function loadMaterials() {
  loading.value = true
  drawerItem.value = null; addSearchVisible.value = false; showEditConfirm.value = false
  try { materials.value = ((await fetchZoneMaterials(taskId.value, zoneId.value)) as any[]) || [] }
  finally { loading.value = false }
}

const pending = computed(() => materials.value.filter(m => !m.inputStatus || m.inputStatus !== 'entered'))
const completed = computed(() => materials.value.filter(m => m.inputStatus === 'entered'))
const pendingCount = computed(() => pending.value.length)
const doneCount = computed(() => completed.value.length)
const totalCount = computed(() => materials.value.length)
const progressPercent = computed(() => totalCount.value ? Math.round(doneCount.value / totalCount.value * 100) : 0)

const searchFilter = (list: any[]) => {
  if (!searchKey.value.trim()) return list
  const kw = searchKey.value.trim().toLowerCase()
  return list.filter(m => (m.materialName||'').toLowerCase().includes(kw) || (m.spec||'').toLowerCase().includes(kw))
}
const filteredPending = computed(() => searchFilter(pending.value))
const filteredCompleted = computed(() => searchFilter(completed.value))

const filtered = computed(() => {
  let list = filterTab.value === 'done' ? completed.value : filterTab.value === 'pending' ? pending.value : materials.value
  return searchFilter(list)
})

const displayList = computed(() => editMode.value ? editList.value : filtered.value)
const hasRule = (m: any) => m.inventoryRule?.ruleStatus === 'maintained'
const isMultiUnit = (m: any) => hasRule(m) && (m.inventoryRule?.units?.length || 0) > 1

watch(filterTab, (val) => {
  if (val !== 'all') {
    showPending.value = undefined
    showDone.value = undefined
  } else {
    if (showPending.value === undefined) showPending.value = true  // 默认展开
    if (showDone.value === undefined) showDone.value = false       // 默认收起
  }
})

function breakdownText(m: any) {
  if (!m.inputStatus || m.inputStatus !== 'entered') return ''
  if (m.inputQty == null) return ''
  if (isMultiUnit(m)) {
    const stored = uni.getStorageSync(`ui_${taskId.value}_${zoneId.value}_${m.materialId}`)
    if (stored) {
      const parts: string[] = []
      for (const [u, v] of Object.entries(stored)) {
        if (v && Number(v) > 0) parts.push(`${v}${u}`)
      }
      return parts.length ? `多单位：${parts.join(' + ')}` : `多单位`
    }
    return `多单位 · 称重`
  }
  const qty = Number(m.inputQty)
  return isNaN(qty) ? '' : `${qty} ${m.unit || ''}`
}

function openDrawer(m: any) {
  drawerItem.value = m; drawerUnitInputs.value = {}
  const bUnit = m.inventoryRule?.baseUnit || m.unit || ''
  const stored = uni.getStorageSync(`ui_${taskId.value}_${zoneId.value}_${m.materialId}`)
  if (stored) drawerUnitInputs.value = { ...stored }
  else if (m.inputQty != null && m.inputOriginalUnit) drawerUnitInputs.value[m.inputOriginalUnit] = String(m.inputOriginalQty || m.inputQty)
  else if (bUnit) drawerUnitInputs.value[bUnit] = ''
}
function closeDrawer() { drawerItem.value = null }
function baseUnit(m: any) { return m.inventoryRule?.baseUnit || m.unit || '' }
function ruleUnits(m: any) { return m.inventoryRule?.units?.map((u:any)=>u.unitName) || (m.unit?[m.unit]:[]) }

const drawerTotal = computed(() => {
  const m = drawerItem.value; if (!m) return '0'; const b = baseUnit(m); let t = 0
  for (const [u, v] of Object.entries(drawerUnitInputs.value)) { const n = parseFloat(v||'0'); if (!n) continue; t += u===b ? n : (()=>{const c=m.inventoryRule?.conversions?.find((x:any)=>x.fromUnit===u); return c?n*(Number(c.toQuantity)/Number(c.fromQuantity)):0})() }
  return t>0?t.toFixed(2):'0'
})

async function confirmDrawer() {
  if (saving.value) return
  const m = drawerItem.value; if (!m) return
  let t=0; let h=false; const b=baseUnit(m)
  for(const [u,v] of Object.entries(drawerUnitInputs.value)){const n=parseFloat(v||'0'); if(!n)continue;h=true;t+=u===b?n:(()=>{const c=m.inventoryRule?.conversions?.find((x:any)=>x.fromUnit===u);return c?n*(Number(c.toQuantity)/Number(c.fromQuantity)):0})()}
  if(!h){uni.showToast({title:'请输入数量',icon:'none'});return}
  try{await itemSave(taskId.value,zoneId.value,{materialId:m.materialId,qty:t,inputMode:'unit',originalQty:t,originalUnit:b,unitInputs:JSON.stringify(drawerUnitInputs.value)}); m.inputQty=t; m.inputStatus='entered'; uni.setStorageSync(`ui_${taskId.value}_${zoneId.value}_${m.materialId}`,{...drawerUnitInputs.value}); closeDrawer()}catch{}
}

async function quickSave(m: any, val: string) {
  const q=parseFloat(val); if(isNaN(q)||q<0)return
  try{await itemSave(taskId.value,zoneId.value,{materialId:m.materialId,qty:q,inputMode:'unit',originalQty:q,originalUnit:m.unit||''}); m.inputQty=q; m.inputQtyRaw=val; m.inputStatus='entered'}catch{}
}

async function handleSave() {
  if (saving.value) return
  saving.value=true
  try{await saveZone(taskId.value,zoneId.value,[]); uni.showToast({title:'保存成功',icon:'success'}); setTimeout(()=>uni.navigateBack(),800)}
  finally{saving.value=false}
}

/* 编辑模式 */
function enterEdit() { if (loading.value) { uni.showToast({ title: '数据加载中，请稍后', icon: 'none' }); return }; editList.value = materials.value.map((m:any)=>({...m})); selected.value = new Set(); deletedIds.value = new Set(); editingIdx.value = -1; editMode.value = true }
async function doSaveEdit() {
  if (saving.value) return
  saving.value = true
  try {
    const ids = editList.value.map((m:any)=>m.taskZoneMaterialId);
    console.log('[doSaveEdit] editList 长度:', editList.value.length, 'ids:', JSON.stringify(ids));
    console.log('[doSaveEdit] 首个物料样例:', JSON.stringify(editList.value[0]));
    await sortMaterials(taskId.value, zoneId.value, ids);
    console.log('[doSaveEdit] 待删除物料数:', deletedIds.value.size);
    for (const id of deletedIds.value) { await removeMaterial(taskId.value, zoneId.value, String(id)) }
  }
  catch { uni.showToast({ title: '保存失败', icon: 'none' }); throw null }
  finally { saving.value = false }
}
const showEditConfirm = ref(false)
async function onCancelEdit() { const orderChanged = editList.value.length === materials.value.length && editList.value.some((m: any, i: number) => m.taskZoneMaterialId !== materials.value[i]?.taskZoneMaterialId); if (editList.value.length !== materials.value.length || selected.value.size > 0 || deletedIds.value.size > 0 || orderChanged) { showEditConfirm.value = true } else { await exitEdit() } }
async function exitEdit() { editMode.value = false; editList.value = []; selected.value = new Set(); deletedIds.value = new Set(); await loadMaterials() }
async function saveAndExit() { showEditConfirm.value = false; try { await doSaveEdit(); await exitEdit() } catch {} }
async function onSaveEdit() { try { await doSaveEdit(); await exitEdit() } catch {} }
async function discardAndExit() { showEditConfirm.value = false; await exitEdit() }
function deleteSelected() {
  if (selected.value.size === 0) return
  uni.showModal({ title: '确认删除', content: `确定删除 ${selected.value.size} 项物料吗？`, success: (r: any) => { if (r.confirm) { for (const id of selected.value) deletedIds.value.add(id); editList.value = editList.value.filter((m: any) => !selected.value.has(m.materialId)); selected.value = new Set(); listKey.value++ } } })
}
function toggleSelect(m: any) { const s = new Set(selected.value); if (s.has(m.materialId)) s.delete(m.materialId); else s.add(m.materialId); selected.value = s }
function moveItem(from: number, dir: -1|1) { const to=from+dir; if(to<0||to>=editList.value.length)return; const l=editList.value; const [mv]=l.splice(from,1); l.splice(to,0,mv); listKey.value++ }
function startEdit(idx: number) { editingIdx.value = idx; editValue.value = String(idx + 1) }
function confirmEdit() {
  const from = editingIdx.value; if (from < 0) return
  let to = parseInt(editValue.value) - 1
  if (isNaN(to) || to < 0) to = 0
  if (to >= editList.value.length) to = editList.value.length - 1
  editingIdx.value = -1
  if (to === from) return
  const l = editList.value; const [mv] = l.splice(from, 1); l.splice(to, 0, mv)
  listKey.value++
}

function goBack() { uni.navigateBack() }
function goScan() { uni.navigateTo({ url: '/pages/inventory/scan/index' }) }

const addSearchVisible = ref(false); const addSearchKey = ref(''); const addSearchResults = ref<any[]>([]); const addSearchLoading = ref(false); let addSearchTimer:any=null; const addSearchAddings = ref(new Set<string>())
function openAddSearch() { addSearchKey.value=''; addSearchResults.value=[]; addSearchVisible.value=true; doAddSearch() }
function onAddSearchInput() { if(addSearchTimer)clearTimeout(addSearchTimer); addSearchTimer=setTimeout(doAddSearch,300) }
async function doAddSearch() { addSearchLoading.value=true; try{addSearchResults.value=((await fetchCandidates(taskId.value,zoneId.value,addSearchKey.value))as any[])||[]} finally{addSearchLoading.value=false} }
async function handleAddSearch(item:any) { if(item.inCurrentZone||addSearchAddings.value.has(item.materialId))return; addSearchAddings.value.add(item.materialId); addSearchAddings.value=new Set(addSearchAddings.value); try{await addMaterial(taskId.value,zoneId.value,item.materialId); uni.showToast({title:'添加成功',icon:'success'}); item.inCurrentZone=true; loadMaterials()} finally{addSearchAddings.value.delete(item.materialId);addSearchAddings.value=new Set(addSearchAddings.value)} }

const extResults = ref<any[]>([]); const extLoading = ref(false); let extTimer:any=null; const extAddings = ref(new Set<string>())
// 检查全部物料（不限当前 tab）是否有匹配
const allMatchCount = computed(() => {
  if (!searchKey.value.trim()) return -1
  const kw = searchKey.value.trim().toLowerCase()
  return materials.value.filter(m => (m.materialName||'').toLowerCase().includes(kw) || (m.spec||'').toLowerCase().includes(kw)).length
})
watch([searchKey], () => {
  if (extTimer) clearTimeout(extTimer)
  const kw = searchKey.value.trim()
  if (!kw) { extResults.value = []; extLoading.value = false; return }
  extLoading.value = true
  extTimer = setTimeout(async () => {
    try {
      const list = (await fetchCandidates(taskId.value, zoneId.value, kw)) as any[]
      extResults.value = Array.isArray(list) ? list : []
    } catch { extResults.value = [] }
    finally { extLoading.value = false }
  }, 500)
})
async function handleExtAdd(item:any) { if(extAddings.value.has(item.materialId))return; extAddings.value.add(item.materialId); extAddings.value=new Set(extAddings.value); try{await addMaterial(taskId.value,zoneId.value,item.materialId); uni.showToast({title:'添加成功',icon:'success'}); extResults.value=extResults.value.filter((m:any)=>m.materialId!==item.materialId); loadMaterials()} finally{extAddings.value.delete(item.materialId);extAddings.value=new Set(extAddings.value)} }
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else>
      <view class="progress-card">
        <view class="pc-top">
          <view class="pc-left">
            <text class="pc-store">{{ storeName||'' }}{{ storeName?'  ':'' }}{{ zoneName }}</text>
            <text class="pc-task">{{ taskName||'月盘任务' }}</text>
            <text class="pc-hint">{{ pendingCount > 0 ? `还有 ${pendingCount} 项物料未填写，建议今天收尾。` : '当前分区待盘物料已处理完成。' }}</text>
          </view>
          <text class="pc-badge">{{ doneCount }} / {{ totalCount }} 已录入</text>
        </view>
        <view class="pc-bar"><view class="pc-fill" :style="{width:progressPercent+'%'}"></view></view>
        <view class="pc-stats">
          <view class="pcs"><text class="pcsl">待盘</text><text class="pcsv">{{ pendingCount }} 项</text></view>
          <view class="pcs"><text class="pcsl">已盘</text><text class="pcsv">{{ doneCount }} 项</text></view>
        </view>
      </view>

      <view class="search-row" v-if="!editMode">
        <view class="search-box">
          <input class="si-input" type="text" v-model="searchKey" placeholder="🔍 搜索物料" />
          <text v-if="searchKey" class="si-clear" @click.stop="searchKey=''">✕</text>
        </view>
        <view class="scan-btn" @click="goScan">📷 扫码</view>
      </view>
      <view class="search-row" v-else>
        <text class="edit-tip">点击序号或箭头排序，勾选删除</text>
      </view>

      <!-- 筛选标签栏 -->
      <view class="pills" v-if="!editMode">
        <view class="pill" :class="{on:filterTab==='pending'}" @click="filterTab='pending'">待盘 {{ pendingCount }}</view>
        <view class="pill" :class="{on:filterTab==='done'}" @click="filterTab='done'">已盘 {{ doneCount }}</view>
        <view class="pill" :class="{on:filterTab==='all'}" @click="filterTab='all'">全部 {{ totalCount }}</view>
      </view>

      <!-- ======== 待盘物料区（pending / all 时显示） ======== -->
      <template v-if="!editMode && (filterTab==='pending'||filterTab==='all')">
        <view class="section-head">
          <view class="sh-left" @click="filterTab==='all'?showPending=!showPending:null">
            <text v-if="filterTab==='all'" class="sh-arrow" :class="{folded:showPending===false}">▼</text>
            <text class="sh-title">待盘物料（{{ pendingCount }}）</text>
          </view>
          <view class="sh-actions">
            <view class="sh-edit" @click="enterEdit">编辑</view>
            <view class="sh-add" @click="openAddSearch">+添加</view>
          </view>
        </view>
        <view v-if="(filterTab==='pending'||showPending!==false) && filteredPending.length" class="material-list">
          <view v-for="m in filteredPending" :key="m.materialId" class="m-row" @click="isMultiUnit(m) ? openDrawer(m) : null">
            <view class="mr-icon" :class="{green:isMultiUnit(m), gray:!isMultiUnit(m)}">
              <text>{{ isMultiUnit(m)?'📦':'📝' }}</text>
            </view>
            <view class="mr-info">
              <text class="mr-name">{{ m.materialName }}</text>
              <text class="mr-desc">{{ isMultiUnit(m)?'多单位 · 称重':'按实际数量填写' }}</text>
            </view>
            <view v-if="!isMultiUnit(m)" class="mr-input-wrap" @click.stop>
              <input class="mr-input" type="digit" placeholder="0" :value="m.inputQtyRaw||''" @blur="(e:any)=>quickSave(m,e.detail.value)" />
              <text class="mr-unit">{{ m.inventoryUnit||m.inventory_unit||m.unit||'' }}</text>
            </view>
            <view v-else class="mr-arrow-wrap" @click.stop="openDrawer(m)">
              <text class="mr-arrow-label">{{ m.inputQty ? m.inputQty+' '+(m.unit||baseUnit(m)) : '未录入' }}</text>
              <text class="mr-arrow">›</text>
            </view>
          </view>
        </view>
        <EmptyState v-else-if="filterTab==='pending'||showPending!==false" text="暂无待盘物料" />
      </template>

      <!-- ======== 已盘物料区（done / all 时显示） ======== -->
      <template v-if="!editMode && (filterTab==='done'||filterTab==='all')">
        <view class="section-head done-head">
          <view class="sh-left" @click="filterTab==='all'?showDone=!showDone:null">
            <text v-if="filterTab==='all'" class="sh-arrow" :class="{folded:showDone===false}">▼</text>
            <text class="sh-title">已盘物料（{{ doneCount }}）</text>
          </view>
        </view>
        <view v-if="(filterTab==='done'||showDone!==false) && filteredCompleted.length" class="material-list done-list">
          <view v-for="m in filteredCompleted" :key="m.materialId" class="m-row done">
            <view class="mr-icon check">✓</view>
            <view class="mr-info">
              <text class="mr-name done-name">{{ m.materialName }}</text>
              <text class="mr-desc done-desc">{{ breakdownText(m) }}</text>
            </view>
            <view class="mr-done">
              <text class="mr-val">{{ m.inputQty||'--' }} {{ m.unit || baseUnit(m) }}</text>
              <text class="mr-edit" @click.stop="openDrawer(m)">修改</text>
            </view>
          </view>
        </view>
        <EmptyState v-else-if="filterTab==='done'" text="暂无已盘物料" />
      </template>

      <!-- 编辑模式列表 -->
      <template v-if="editMode">
        <view v-if="displayList.length" class="material-list">
          <view v-for="(m, idx) in displayList" :key="(m.materialId||m.id)+'-v'+listKey" class="m-row" :class="{done:m.inputStatus==='entered'}">
            <view class="m-check" @click.stop="toggleSelect(m)">
              <view class="check-box" :class="{on:selected.has(m.materialId)}"><text v-if="selected.has(m.materialId)">✓</text></view>
            </view>
            <view class="m-order">
              <template v-if="editingIdx === idx">
                <input class="order-input" v-model="editValue" type="number" :maxlength="4" :focus="true"
                  @blur="confirmEdit" @confirm="confirmEdit" />
              </template>
              <template v-else>
                <view class="order-btn" @click.stop="startEdit(idx)">
                  <text class="order-num">{{ idx + 1 }}</text>
                </view>
              </template>
            </view>
            <view class="mr-info">
              <text class="mr-name">{{ m.materialName }}</text>
              <text class="mr-desc">{{ isMultiUnit(m)?'多单位':'按实际数量填写' }}</text>
            </view>
            <view class="m-arrows">
              <view class="arr-btn" :class="{dis:idx===0}" @click.stop="moveItem(idx,-1)">^</view>
              <view class="arr-btn" :class="{dis:idx===displayList.length-1}" @click.stop="moveItem(idx,1)">v</view>
            </view>
          </view>
        </view>
        <EmptyState v-else text="暂无物料，点击上方添加" />
      </template>

      <!-- 搜索物料库 -->
      <view v-if="!editMode && extLoading" class="ext-hint">搜索物料库中...</view>
      <template v-if="!editMode && !extLoading && searchKey && extResults.length">
        <view class="ext-section">
          <view class="ext-header" v-if="allMatchCount===0">
            <view class="ext-empty-icon">
              <text class="ext-empty-search">🔍</text>
              <text class="ext-empty-x">✕</text>
            </view>
            <text class="ext-empty-title">当前分区未找到相关物料</text>
            <text class="ext-empty-sub">可能该物料被放在其他分区，或尚未加入当前分区</text>
          </view>
          <view class="ext-header" v-else-if="allMatchCount>0">
          <view class="ext-empty-icon">
              <text class="ext-empty-search">🔍</text>
              <text class="ext-empty-x">✕</text>
            </view>
            <text class="ext-hint-title">可加入以下物料到此分区</text>
          </view>
          <view class="ext-list">
            <view v-for="m in extResults" :key="m.materialId" class="ext-row" :class="{added:m.inCurrentZone}">
              <view class="ext-info">
                <text class="ext-name">{{ m.materialName }}</text>
                <text class="ext-meta">{{ m.spec||'--' }} · {{ '盘点单位：'+(m.inventoryUnit||m.inventory_unit||m.unit||'--') }}</text>
              </view>
              <text class="ext-add" v-if="m.inCurrentZone">已加入</text>
              <view class="ext-add" v-else @click="handleExtAdd(m)">{{ extAddings.has(m.materialId)?'添加中...':'加入当前分区' }}</view>
            </view>
          </view>
        </view>
      </template>
      <view v-if="!editMode && !extLoading && searchKey && extResults.length===0" class="ext-hint">此物料不存在</view>
    </template>

    <view class="bottom-bar" v-if="!editMode">
      <view class="bb-back" @click="goBack">返回任务</view>
      <view class="bb-confirm" @click="handleSave">{{ saving?'保存中...':'保存本分区' }}</view>
    </view>
    <view class="bottom-bar" v-else>
      <view class="bb-back" @click="onCancelEdit">取消</view>
      <view class="bb-confirm" @click="onSaveEdit">{{ saving?'保存中...':'保存' }}</view>
      <view class="bb-del" :class="{disabled:selected.size===0}" @click="deleteSelected">删除 ({{ selected.size }})</view>
    </view>

    <view v-if="drawerItem" class="mask" @click="closeDrawer">
      <view class="drawer" @click.stop>
        <view class="dh"></view>
        <view class="drawer-head">
          <text class="dht">{{ drawerItem.materialName }}</text>
          <text class="dhx" @click="closeDrawer">✕</text>
        </view>
        <view class="drawer-body">
          <view class="df-grid">
            <view v-for="u in ruleUnits(drawerItem)" :key="u" class="df-item">
              <text class="dfl">{{ u }}</text>
              <input class="dfi" type="digit" v-model="drawerUnitInputs[u]" :placeholder="u" />
            </view>
          </view>
          <view class="drawer-total">折算 ≈ {{ drawerTotal }} {{ baseUnit(drawerItem) }}</view>
        </view>
        <view class="drawer-act"><view class="da-confirm" @click="confirmDrawer">确认录入</view></view>
      </view>
    </view>

    <view v-if="addSearchVisible" class="mask" @click="addSearchVisible=false">
      <view class="sheet" @click.stop>
        <view class="sh"></view>
        <view class="sheet-head">
          <text class="st">添加物料</text>
          <text class="sx" @click="addSearchVisible=false">✕</text>
        </view>
        <view class="sheet-search"><input class="si" v-model="addSearchKey" placeholder="搜索物料名称" @input="onAddSearchInput" /></view>
        <view class="search-results">
          <view v-if="addSearchLoading" class="sr-loading">搜索中...</view>
          <view v-else-if="addSearchResults.length===0" class="sr-empty">未找到物料</view>
          <view v-else class="sr-list">
            <view v-for="m in addSearchResults" :key="m.materialId" class="sr-item" :class="{added:m.inCurrentZone}" @click="handleAddSearch(m)">
              <view class="sri-info"><text class="sri-name">{{ m.materialName }}</text><text class="sri-meta">{{ m.spec||'--' }}  ·  {{ '盘点单位：'+(m.inventoryUnit||m.inventory_unit||m.unit||'--') }}</text></view>
              <text class="sri-btn" v-if="m.inCurrentZone">已在当前分区</text>
              <text class="sri-btn add" v-else>{{ addSearchAddings.has(m.materialId)?'...':'加入当前分区' }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="showEditConfirm" class="mask" @click="showEditConfirm=false">
      <view class="sheet" @click.stop>
        <view class="sh"></view>
        <view class="sheet-head"><text class="st">提示</text></view>
        <view class="sheet-body"><text class="del-msg">是否保存当前修改</text></view>
        <view class="sheet-btns">
          <view class="sb-cancel" @click="discardAndExit">不保存</view>
          <view class="sb-confirm" @click="saveAndExit">{{ saving?'保存中...':'保存' }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$ps2:#247847;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 200rpx}
.progress-card{background:$s;border-radius:20rpx;padding:28rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:24rpx}
.pc-top{display:flex;justify-content:space-between;gap:16rpx;align-items:flex-start}
.pc-store{display:block;font-size:26rpx;font-weight:600;color:$p}
.pc-task{display:block;margin-top:6rpx;font-size:34rpx;font-weight:700;color:$t1}
.pc-hint{display:block;margin-top:6rpx;font-size:24rpx;color:$t2}
.pc-badge{padding:10rpx 20rpx;border-radius:999rpx;background:$ps;color:$p;font-size:22rpx;font-weight:600;white-space:nowrap}
.pc-bar{height:14rpx;border-radius:999rpx;background:#FAFBF9;margin-top:20rpx;overflow:hidden}
.pc-fill{height:100%;border-radius:999rpx;background:$p}
.pc-stats{display:grid;grid-template-columns:repeat(2,1fr);margin-top:20rpx;padding:16rpx;background:#FAFBF9;border-radius:12rpx;text-align:center}
.pcsl{font-size:22rpx;color:$t3}.pcsv{display:block;margin-top:4rpx;font-size:36rpx;font-weight:800;color:$t1}

.search-row{display:flex;gap:12rpx;margin-bottom:16rpx;align-items:center}
.search-box{width:400rpx;display:flex;align-items:center;gap:12rpx;height:80rpx;padding:0 20rpx;border:2rpx solid $b;border-radius:12rpx;background:$s}
.si-input{flex:1;height:80rpx;font-size:26rpx;padding-left:8rpx}.si-clear{font-size:28rpx;color:$t3;padding:8rpx;flex-shrink:0}
.scan-btn{flex:1;height:80rpx;padding:0 28rpx;border-radius:12rpx;background:$ps2;color:#fff;display:flex;align-items:center;justify-content:center;gap:8rpx;font-size:26rpx;font-weight:600;white-space:nowrap}
.scan-btn{height:80rpx;padding:0 28rpx;border-radius:12rpx;background:$ps2;color:#fff;display:flex;align-items:center;gap:8rpx;font-size:26rpx;font-weight:600;white-space:nowrap}
.edit-tip{flex:1;font-size:26rpx;color:$p;font-weight:500}

.pills{display:flex;align-items:center;gap:16rpx;margin-bottom:20rpx}
.pill{padding:14rpx 28rpx;border-radius:999rpx;font-size:26rpx;border:2rpx solid $b;background:$s;color:$t2}.pill.on{background:$p;color:#fff;border-color:$p}

.section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:16rpx;padding:0 4rpx}
.sh-left{display:flex;align-items:center;gap:4rpx}
.sh-title{font-size:32rpx;font-weight:700;color:$t1}
.sh-actions{display:flex;align-items:center;gap:12rpx;flex-shrink:0}
.sh-edit{padding:12rpx 24rpx;border-radius:12rpx;border:2rpx solid $b;background:$s;color:$t1;font-size:26rpx;font-weight:600;white-space:nowrap}
.sh-add{padding:12rpx 24rpx;border-radius:12rpx;background:$p;color:#fff;font-size:26rpx;font-weight:600;white-space:nowrap}
.sh-arrow{font-size:28rpx;color:$t3;transition:transform .25s;margin-right:8rpx;flex-shrink:0}.sh-arrow.folded{transform:rotate(-90deg)}
.done-head{margin-top:32rpx;cursor:pointer}

.material-list{display:flex;flex-direction:column;border-radius:16rpx;overflow:hidden;border:2rpx solid $b;background:$s;margin-bottom:8rpx}
.material-list.done-list{background:transparent;border-color:transparent}
.m-row{display:flex;align-items:center;gap:16rpx;padding:24rpx;background:$s;border-bottom:2rpx solid #EEF1EF;min-height:96rpx}
.m-row:last-child{border-bottom:0}.m-row.done{background:$s;border-bottom:2rpx solid #EEF1EF;min-height:68rpx;padding:20rpx 24rpx}
.m-check{flex-shrink:0}.check-box{width:40rpx;height:40rpx;border-radius:8rpx;border:2rpx solid $b;display:flex;align-items:center;justify-content:center;font-size:26rpx;color:#fff}.check-box.on{background:$p;border-color:$p}
.m-arrows{display:flex;flex-direction:column;gap:2rpx;flex-shrink:0}.arr-btn{width:40rpx;height:34rpx;border-radius:6rpx;background:#FAFBF9;border:1rpx solid $b;display:flex;align-items:center;justify-content:center;font-size:18rpx;color:$t2}.arr-btn.dis{opacity:.3}
.m-order{width:64rpx;flex-shrink:0;display:flex;align-items:center;justify-content:center}.order-num{width:52rpx;height:52rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:700;color:$p;border:2rpx solid $p;border-radius:10rpx;background:$ps}.order-input{width:56rpx;height:56rpx;text-align:center;font-size:28rpx;font-weight:700;border:2rpx solid $p;border-radius:10rpx;color:$p;background:$ps}
.mr-icon{width:72rpx;height:72rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:32rpx;flex-shrink:0}.mr-icon.green{background:$ps;color:$p}.mr-icon.gray{background:#FAFBF9;color:$t3}.mr-icon.check{background:$ps;color:$p;width:56rpx;height:56rpx;font-size:24rpx;font-weight:700}
.mr-info{flex:1;min-width:0}.mr-name{display:block;font-size:30rpx;font-weight:700;color:$t1}.mr-name.done-name{font-size:28rpx;font-weight:500}
.mr-desc{display:block;margin-top:4rpx;font-size:24rpx;color:$t3}.mr-desc.done-desc{font-size:22rpx}
.mr-done{text-align:right;flex-shrink:0}.mr-val{display:block;font-size:26rpx;font-weight:600;color:$t1}.mr-edit{display:block;margin-top:4rpx;font-size:22rpx;color:$p;padding:8rpx 0}
.mr-input-wrap{display:flex;align-items:center;gap:8rpx;flex-shrink:0}
.mr-input{width:120rpx;height:72rpx;text-align:center;font-size:30rpx;font-weight:600;border:2rpx solid $b;border-radius:12rpx;background:#FAFBF9}
.mr-unit{font-size:24rpx;color:$t2;white-space:nowrap}
.mr-arrow-wrap{display:flex;align-items:center;gap:6rpx;flex-shrink:0}
.mr-arrow-label{font-size:22rpx;color:$d}
.mr-arrow{font-size:36rpx;color:$t3}

.ext-hint{text-align:center;padding:40rpx 0;font-size:26rpx;color:$t2}
.ext-section{margin-top:16rpx}
.ext-header{display:flex;flex-direction:column;align-items:center;padding:80rpx 0 32rpx}
.ext-hint-title{display:block;font-size:28rpx;font-weight:600;color:$t1;padding:24rpx 0 16rpx}
.ext-empty-icon{position:relative;width:160rpx;height:160rpx;border-radius:50%;background:#F0F1F3;display:flex;align-items:center;justify-content:center;margin-bottom:28rpx}
.ext-empty-search{font-size:64rpx;opacity:.5}
.ext-empty-x{position:absolute;bottom:20rpx;right:32rpx;font-size:32rpx;color:#E05A47;font-weight:700}
.ext-empty-title{font-size:30rpx;color:#4A4A4A;font-weight:500;text-align:center;margin-bottom:12rpx}
.ext-empty-sub{font-size:26rpx;color:#8C8C8C;text-align:center;line-height:1.5;max-width:480rpx}
.ext-list{display:flex;flex-direction:column;gap:16rpx;margin-top:16rpx}
.ext-row{display:flex;align-items:center;justify-content:space-between;padding:24rpx;background:$s;border-radius:16rpx;border:2rpx solid $b}
.ext-info{flex:1}.ext-name{font-size:28rpx;font-weight:600;color:$t1}.ext-meta{display:block;margin-top:4rpx;font-size:24rpx;color:$t2}
.ext-row.added{opacity:.6}.ext-row.added .ext-add{background:$b;color:$t2}
.ext-add{padding:12rpx 24rpx;border-radius:999rpx;background:$p;color:#fff;font-size:24rpx;font-weight:600;white-space:nowrap}

.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:flex;gap:20rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:$s;border-top:2rpx solid #EEF1EF}
.bb-back{flex:1;height:88rpx;border-radius:999rpx;border:2rpx solid $b;background:$s;color:$t1;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.bb-confirm{flex:2;height:88rpx;border-radius:999rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}
.bb-del{flex:1;height:88rpx;border-radius:999rpx;background:$d;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}.bb-del.disabled{opacity:.5}

.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.drawer{width:100%;max-height:85vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column}
.dh{width:80rpx;height:6rpx;border-radius:999rpx;background:$b;margin:16rpx auto}
.drawer-head{display:flex;justify-content:center;padding:8rpx 32rpx 16rpx;position:relative}.dht{font-size:34rpx;font-weight:700;color:$t1}.dhx{position:absolute;right:32rpx;font-size:40rpx;color:$t2}
.drawer-body{padding:0 32rpx 24rpx;display:flex;flex-direction:column;gap:20rpx}
.df-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx}.df-item{display:flex;flex-direction:column;gap:10rpx}
.dfl{font-size:28rpx;font-weight:600;color:$t1}.dfi{padding:24rpx;border:2rpx solid $b;border-radius:12rpx;font-size:32rpx;font-weight:600;text-align:center;background:#FAFBF9}
.drawer-total{text-align:center;padding:16rpx;background:$ps;border-radius:12rpx;color:$p;font-size:28rpx;font-weight:600}
.drawer-act{padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);border-top:2rpx solid #EEF1EF}
.da-confirm{height:96rpx;border-radius:999rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700}

.sheet{width:100%;max-height:80vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column}
.search-results{flex:1;overflow:auto;padding:0 32rpx 24rpx;min-height:0}
.sh{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:20rpx auto;flex-shrink:0}
.sheet-head{display:flex;justify-content:center;padding:8rpx 32rpx 20rpx;position:relative;flex-shrink:0}.st{font-size:36rpx;font-weight:700;color:$t1}.sx{position:absolute;right:32rpx;font-size:40rpx;color:$t2;padding:8rpx}
.sheet-search{padding:0 32rpx 20rpx;flex-shrink:0}.si{height:80rpx;padding:0 24rpx;border:2rpx solid transparent;border-radius:12rpx;font-size:28rpx;background:#FAFBF9;width:100%;box-sizing:border-box}
.sr-loading,.sr-empty{text-align:center;padding:60rpx 0;font-size:26rpx;color:$t3}
.sr-list{display:flex;flex-direction:column;gap:12rpx}
.sr-item{display:flex;align-items:center;justify-content:space-between;padding:20rpx 24rpx;background:#FAFBFC;border-radius:12rpx}.sr-item.added{opacity:.6}
.sri-info{flex:1}.sri-name{font-size:28rpx;font-weight:500;color:$t1}.sri-meta{display:block;margin-top:4rpx;font-size:22rpx;color:$t3}
.sri-btn{padding:12rpx 20rpx;border-radius:999rpx;font-size:22rpx;font-weight:500;flex-shrink:0;white-space:nowrap}.sri-btn.add{background:$p;color:#fff}.sri-btn:not(.add){background:#EDEFF2;color:#8C9691}
.del-msg{padding:0;font-size:28rpx;color:$t2;line-height:1.5}
.sheet-btns{display:grid;grid-template-columns:1fr 1fr;gap:24rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);border-top:2rpx solid #EEF1EF}
.sb-cancel{height:96rpx;border-radius:999rpx;background:#FAFBF9;color:$t1;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.sb-confirm{height:96rpx;border-radius:999rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
</style>