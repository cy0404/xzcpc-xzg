<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { fetchTaskDetail, searchTaskMaterial } from '@/api/task'
import { addZone, deleteZone, sortZones, updateZoneName } from '@/api/zone'
import { materialDisplayName } from '@/utils/formatter'
import Skeleton from '@/components/Skeleton.vue'
import EmptyState from '@/components/EmptyState.vue'

const taskId = ref('')
const loading = ref(true)
const detail = ref<any>({})
const showAdd = ref(false)
const zoneName = ref('')
const saving = ref(false)
const showSaveConfirm = ref(false)
const showDeleteConfirm = ref(false) // 批量删除确认

// === 物料搜索 ===
const searchKey = ref('')
const searchLoading = ref(false)
const searchResults = ref<any[]>([])
const showSearchPicker = ref(false)
const isNavigating = ref(false)
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
let searchSeq = 0

// === 语音搜索 ===
const voiceLoading = ref(false)
const recording = ref(false)
const voiceRecognizing = ref(false)
const voiceCooldown = ref(false)
let voiceManager: any = null
let voiceManagerReady = false
const voiceCancelled = ref(false)
const touchStartY = ref(0)
let longPressTimer: any = null
let cooldownSafetyTimer: any = null

function startCooldownLock() {
  voiceCooldown.value = true
  clearTimeout(cooldownSafetyTimer)
  cooldownSafetyTimer = setTimeout(() => {
    voiceCooldown.value = false
    voiceRecognizing.value = false
  }, 1000)
}

function clearCooldownLock() {
  voiceCooldown.value = false
  voiceRecognizing.value = false
  clearTimeout(cooldownSafetyTimer)
}

function getVoiceManager() {
  if (voiceManager) return voiceManager
  // #ifdef MP-WEIXIN
  try {
    // @ts-ignore
    voiceManager = requirePlugin('WechatSI').getRecordRecognitionManager()
    voiceManagerReady = true
  } catch { voiceManagerReady = false }
  // #endif
  return voiceManager
}

function cleanupVoice() {
  recording.value = false
  voiceLoading.value = false
  // voiceRecognizing / voiceCooldown 不在此处重置，由 onStop/onError 回调统一重置
}

function startVoice(e: any) {
  e?.preventDefault?.()
  if (voiceLoading.value || recording.value || voiceRecognizing.value || voiceCooldown.value) return
  voiceCancelled.value = false
  const t = e.touches?.[0]
  if (t) touchStartY.value = t.clientY
  clearTimeout(longPressTimer)
  longPressTimer = setTimeout(() => {
    longPressTimer = null
    voiceLoading.value = true
    const mgr = getVoiceManager()
    if (!mgr || !voiceManagerReady) {
      voiceLoading.value = false
      uni.showToast({ title: '语音插件未加载', icon: 'none' })
      return
    }
    mgr.onStart = () => { recording.value = true; voiceLoading.value = false }
    mgr.onStop = (res: any) => {
      clearCooldownLock()
      if (res.result && !voiceCancelled.value) {
        searchKey.value = res.result
        doSearch(res.result, 'confirm')
      }
    }
    mgr.onError = (res: any) => {
      clearCooldownLock()
      recording.value = false
      voiceLoading.value = false
      uni.showToast({ title: res.msg || '识别失败', icon: 'none' })
    }
    try { mgr.start({ duration: 30000, lang: 'zh_CN' }) } catch { cleanupVoice() }
  }, 80)
}

function onVoiceMove(e: any) {
  if (!recording.value) return
  const touch = e.touches[0]
  voiceCancelled.value = (touchStartY.value - touch.clientY) > 65
}

function cancelVoice() {
  if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; voiceLoading.value = false; return }
  if (!recording.value) return
  recording.value = false
  startCooldownLock()
  if (voiceManager) { try { voiceManager.stop() } catch { cleanupVoice() } }
}

function stopVoice(e: any) {
  e?.preventDefault?.()
  if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; voiceLoading.value = false; return }
  if (!recording.value || !voiceManager) return
  if (voiceCancelled.value) {
    recording.value = false
    startCooldownLock()
    try { voiceManager.stop() } catch { cleanupVoice() }
    uni.showToast({ title: '已取消', icon: 'none' })
  } else {
    // 立即关闭浮层，显示"识别中"状态，提升松手响应体感
    recording.value = false
    voiceRecognizing.value = true
    try { voiceManager.stop() } catch { cleanupVoice() }
  }
}

function goToMaterial(item: any) {
  if (isNavigating.value) return
  isNavigating.value = true
  if (searchDebounceTimer) { clearTimeout(searchDebounceTimer); searchDebounceTimer = null }
  searchSeq++
  searchLoading.value = false
  const zn = encodeURIComponent(item.zoneName || '')
  const sn = encodeURIComponent(detail.value?.storeName || '')
  const mid = encodeURIComponent(item.materialId || '')
  const mn = encodeURIComponent(item.materialName || '')
  showSearchPicker.value = false
  searchResults.value = []
  uni.navigateTo({
    url: `/pages/task/zone-entry/index?taskId=${taskId.value}&zoneId=${item.taskZoneId}&zoneName=${zn}&storeName=${sn}&focusMaterialId=${mid}&focusMaterialName=${mn}&fromSearch=1`,
  })
}

async function doSearch(keyword: string, mode: 'input' | 'confirm' = 'confirm') {
  if (isNavigating.value) return
  const kw = (keyword || '').trim()
  if (!kw) {
    searchResults.value = []
    showSearchPicker.value = false
    if (mode === 'confirm') uni.showToast({ title: '请输入物料名称', icon: 'none' })
    return
  }
  const seq = ++searchSeq
  searchLoading.value = true
  try {
    const raw: any = await searchTaskMaterial(Number(taskId.value), kw)
    if (seq !== searchSeq || isNavigating.value) return
    const list = Array.isArray(raw) ? raw : raw ? [raw] : []
    if (list.length === 0) {
      searchResults.value = []
      showSearchPicker.value = false
      if (mode === 'confirm') uni.showToast({ title: '未找到该物料', icon: 'none' })
    } else if (list.length === 1) {
      goToMaterial(list[0])
    } else {
      searchResults.value = list
      showSearchPicker.value = true
    }
  } catch (e: any) {
    if (seq !== searchSeq || isNavigating.value) return
    console.error('[detail] search error:', e)
    if (mode === 'confirm') uni.showToast({ title: e?.message || '搜索失败', icon: 'none' })
  } finally {
    if (seq === searchSeq && !isNavigating.value) searchLoading.value = false
  }
}

function onSearchInput() {
  if (isNavigating.value) return
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  const kw = searchKey.value.trim()
  if (!kw) {
    searchResults.value = []
    showSearchPicker.value = false
    return
  }
  searchDebounceTimer = setTimeout(() => doSearch(kw, 'input'), 400)
}

function onSearchConfirm() {
  doSearch(searchKey.value, 'confirm')
}

watch(searchKey, (val) => {
  if (!val.trim()) {
    searchResults.value = []
    showSearchPicker.value = false
  }
})

// 模式
const sortMode = ref(false)
const editValue = ref('')
const editingIdx = ref(-1)
const editingZoneNameId = ref<number | null>(null)
const editingZoneName = ref('')
const listKey = ref(0)
const selected = ref<Set<number>>(new Set())

// 编辑缓存
const editList = ref<any[]>([])
const deletedIds = ref<number[]>([])  // 待删除的ID列表

const taskStatus = computed(() => {
  const s = detail.value?.status
  if (s === 'submitted') return { text: '已提交', cls: 'st-done' }
  if (s === 'in_progress') return { text: '进行中', cls: 'st-active' }
  return { text: '未开始', cls: 'st-pending' }
})
const zones = computed(() => detail.value?.zones || [])
const displayList = computed(() => sortMode.value ? editList.value : (detail.value?.zones || []))
const totalMaterials = computed(() => zones.value.reduce((s: number, z: any) => s + (z.total || 0), 0))
const enteredMaterials = computed(() => zones.value.reduce((s: number, z: any) => s + (z.entered || 0), 0))
const remaining = computed(() => Math.max(totalMaterials.value - enteredMaterials.value, 0))
const zoneCount = computed(() => detail.value?.totalZones || zones.value.length)
const isSubmitted = computed(() => detail.value?.status === 'submitted')
const progressPct = computed(() => {
  if (!totalMaterials.value) return 0
  return Math.round((enteredMaterials.value / totalMaterials.value) * 100)
})
const hasChanges = computed(() => {
  if (!sortMode.value) return false
  const a = editList.value.map((z: any) => z.taskZoneId).join(',')
  const b = zones.value.map((z: any) => z.taskZoneId).join(',')
  return a !== b || deletedIds.value.length > 0
})

onLoad((q: any) => { taskId.value = q?.taskId || ''; getVoiceManager() })
onShow(() => {
  isNavigating.value = false
  if (taskId.value) loadDetail()
})

async function loadDetail() {
  loading.value = true
  try { detail.value = (await fetchTaskDetail(taskId.value)) || {} }
  finally { loading.value = false }
}

function goZoneEntry(zone: any) {
  const zn = encodeURIComponent(zone.zoneName || '')
  const sn = encodeURIComponent(detail.value?.storeName || '')
  uni.navigateTo({ url: `/pages/task/zone-entry/index?taskId=${taskId.value}&zoneId=${zone.taskZoneId}&zoneName=${zn}&storeName=${sn}` })
}
function goSummary() { uni.navigateTo({ url: `/pages/task/summary/index?taskId=${taskId.value}` }) }
function fmtDeadline(d: string) { if (!d) return '--'; return d.replace('T', ' ').substring(0, 16) }
function zoneProgress(z: any) { if (!z.total) return 0; return Math.round(((z.entered || 0) / z.total) * 100) }

async function doAddZone() {
  if (saving.value) return
  if (!zoneName.value.trim()) { uni.showToast({ title: '请输入分区名称', icon: 'none' }); return }
  saving.value = true
  try { await addZone(Number(taskId.value), zoneName.value.trim()); zoneName.value = ''; showAdd.value = false; loadDetail() }
  finally { saving.value = false }
}

// 排序操作（操作编辑缓存）
function startEdit(idx: number) { editingIdx.value = idx; editValue.value = String(idx + 1) }
async function confirmEdit() {
  const from = editingIdx.value; if (from < 0) return
  let to = parseInt(editValue.value) - 1
  if (isNaN(to) || to < 0) to = 0
  if (to >= editList.value.length) to = editList.value.length - 1
  editingIdx.value = -1
  if (to === from) return
  const list = editList.value
  const [moved] = list.splice(from, 1); list.splice(to, 0, moved)
  listKey.value++
}
function cancelEdit() { editingIdx.value = -1 }
function startEditZoneName(zone: any) { editingZoneNameId.value = zone.taskZoneId; editingZoneName.value = zone.zoneName || '' }
async function confirmZoneName() {
  const id = editingZoneNameId.value
  if (id == null) return
  const name = editingZoneName.value.trim()
  if (!name) { uni.showToast({ title: '分区名称不能为空', icon: 'none' }); return }
  try {
    await updateZoneName(Number(taskId.value), id, name)
    const zone = (sortMode.value ? editList.value : detail.value.zones).find((z: any) => z.taskZoneId === id)
    if (zone) zone.zoneName = name
    listKey.value++
  } catch { /* error toast from api */ }
  editingZoneNameId.value = null
}
function moveZone(from: number, dir: -1 | 1) {
  const to = from + dir
  if (to < 0 || to >= editList.value.length) return
  const list = editList.value
  const [moved] = list.splice(from, 1); list.splice(to, 0, moved)
  listKey.value++
}

// 批量删除（操作编辑缓存）
function toggleSelect(zone: any) {
  const s = new Set(selected.value)
  if (s.has(zone.taskZoneId)) s.delete(zone.taskZoneId); else s.add(zone.taskZoneId)
  selected.value = s
}
function batchDelete() {
  if (selected.value.size === 0) { uni.showToast({ title: '请先选择分区', icon: 'none' }); return }
  showDeleteConfirm.value = true
}
function confirmDelete() {
  const ids = selected.value
  deletedIds.value.push(...ids)
  editList.value = editList.value.filter((z: any) => !ids.has(z.taskZoneId))
  selected.value = new Set()
  listKey.value++
  showDeleteConfirm.value = false
}
async function saveChanges() {
  if (saving.value) return
  saving.value = true
  try {
    // 删除
    for (const id of deletedIds.value) { await deleteZone(Number(taskId.value), id) }
    deletedIds.value = []
    // 排序
    const ids = editList.value.map((z: any) => z.taskZoneId)
    await sortZones(Number(taskId.value), ids)
  } catch { uni.showToast({ title: '保存失败', icon: 'none' }); throw null }
  finally { saving.value = false }
}

// 进入 / 退出编辑模式
function enterEdit() {
  editList.value = zones.value.map((z: any) => ({ ...z }))
  selected.value = new Set()
  deletedIds.value = []
  sortMode.value = true
}
function onCancelEdit() {
  if (hasChanges.value) { showSaveConfirm.value = true }
  else { exitEdit() }
}
async function onSaveEdit() {
  try { await saveChanges(); exitEdit() } catch {}
}
function exitEdit() {
  sortMode.value = false
  editList.value = []
  deletedIds.value = []
  loadDetail()
}
function discardAndExit() {
  showSaveConfirm.value = false; exitEdit()
}
async function saveAndExit() {
  showSaveConfirm.value = false
  try { await saveChanges(); exitEdit() } catch {}
}

</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="6" />
    <template v-else>
      <view class="card info-card">
        <text class="info-badge" :class="taskStatus.cls">{{ taskStatus.text }}</text>
        <text class="info-title">{{ detail.taskName }}</text>
        <view class="info-rows">
          <view class="ir"><text class="irk">盘点门店</text><text class="irv">{{ detail.storeName }}</text></view>
          <view class="ir"><text class="irk">盘点周期</text><text class="irv">{{ detail.taskMonth }}</text></view>
          <view class="ir"><text class="irk">截止时间</text><text class="irv danger">{{ fmtDeadline(detail.deadline) }}</text></view>
        </view>
      </view>
      <view class="card progress-card">
        <view class="pc-top"><text class="pc-label">总体录入进度</text><view class="pc-nums"><text class="pc-big">{{ enteredMaterials }}</text><text class="pc-total">/{{ totalMaterials }}</text></view></view>
        <view class="bar-wrap"><view class="bar-fill" :style="{width:progressPct+'%'}"></view></view>
        <view class="pc-stats">
          <view class="pcs"><text class="pcs-label">分区数</text><text class="pcs-val">{{ zoneCount }}</text></view>
          <view class="pcs"><text class="pcs-label">物料总数</text><text class="pcs-val">{{ totalMaterials }}</text></view>
          <view class="pcs"><text class="pcs-label">已录入</text><text class="pcs-val green">{{ enteredMaterials }}</text></view>
          <view class="pcs"><text class="pcs-label">未录入</text><text class="pcs-val danger">{{ remaining }}</text></view>
        </view>
      </view>
      <!-- 物料搜索框 -->
      <view class="search-bar">
        <view class="sb-input-wrap">
          <text class="sb-search-icon">🔍</text>
          <input class="sb-input" type="text" v-model="searchKey" placeholder="搜索物料，跳转分区" confirm-type="search" @input="onSearchInput" @confirm="onSearchConfirm" />
          <text v-if="searchKey" class="sb-clear" @click.stop="searchKey=''">✕ 清空</text>
        </view>
      </view>

      <view class="section">
        <view class="section-top">
          <view><text class="section-title">分区列表</text><text class="refresh-icon" @click="loadDetail" v-if="!sortMode">↻</text><text class="section-sub" v-if="sortMode" style="color:#E58A2D">点击序号或箭头排序 · 点击分区名更改名称</text><text class="section-sub" v-else>点击卡片进入盘点</text></view>
        </view>
        <view v-if="displayList.length" class="zone-list">
          <view v-for="(zone, idx) in displayList" :key="zone.taskZoneId + '-v' + listKey" class="zone-card"
            :class="{done: zone.entered && zone.total && zone.entered >= zone.total}">
            <!-- 编辑模式 -->
            <view v-if="sortMode" class="zc-check" @click.stop="toggleSelect(zone)">
              <view class="check-box" :class="{on: selected.has(zone.taskZoneId)}">
                <text v-if="selected.has(zone.taskZoneId)">✓</text>
              </view>
            </view>
            <view class="zc-order" v-if="sortMode">
              <template v-if="editingIdx === idx">
                <input class="order-input" v-model="editValue" type="number" :maxlength="4" :focus="true" @blur="confirmEdit" @confirm="confirmEdit" />
              </template>
              <template v-else>
                <view class="order-btn" @click.stop="startEdit(idx)"><text class="order-num">{{ idx + 1 }}</text></view>
              </template>
            </view>
            <view class="zc-content" @click="sortMode ? null : goZoneEntry(zone)">
              <view class="zc-icon" :class="{done: zone.entered && zone.total && zone.entered >= zone.total, prog: (zone.entered||0) > 0 && (!zone.total || zone.entered < zone.total)}">
                <text v-if="zone.entered && zone.total && zone.entered >= zone.total">✓</text>
                <text v-else-if="(zone.entered||0) > 0">✎</text>
                <text v-else>⌛</text>
              </view>
              <view class="zc-info">
                <view class="zc-top-row">
                  <template v-if="sortMode && editingZoneNameId === zone.taskZoneId">
                    <input class="zone-name-input" v-model="editingZoneName" :focus="true" @blur="confirmZoneName" @confirm="confirmZoneName" />
                  </template>
                  <text v-else class="zc-name" @click.stop="sortMode ? startEditZoneName(zone) : null">{{ zone.zoneName }}</text>
                  <text class="zc-count">{{ zone.entered || 0 }}/{{ zone.total || 0 }}</text>
                </view>
                <view class="zc-bar-wrap" v-if="(zone.entered||0) > 0 && (!zone.total || zone.entered < zone.total)"><view class="zc-bar-fill" :style="{width:zoneProgress(zone)+'%'}"></view></view>
                <text class="zc-bar-text" v-else-if="zone.entered && zone.total && zone.entered >= zone.total">已完成</text>
                <text class="zc-bar-text" v-else>未开始</text>
              </view>
            </view>
            <view class="zc-arrows" v-if="sortMode && editingIdx < 0">
              <view class="arrow-btn" :class="{disabled: idx === 0}" @click.stop="moveZone(idx, -1)">^</view>
              <view class="arrow-btn" :class="{disabled: idx === displayList.length - 1}" @click.stop="moveZone(idx, 1)">v</view>
            </view>
          </view>
        </view>
        <EmptyState v-else text="暂无分区，请添加分区后开始盘点" />
      </view>

    <!-- 底部栏 -->
    <view v-if="!isSubmitted" class="bottom-bar" :class="{edit:sortMode}">
      <template v-if="sortMode">
        <view class="bb-back" @click="onCancelEdit">取消</view>
        <view class="bb-confirm" @click="onSaveEdit">{{ saving ? '保存中...' : '保存' }}</view>
        <view class="bb-del" @click="batchDelete" :class="{disabled: selected.size === 0}">删除 ({{ selected.size }})</view>
      </template>
      <template v-else>
        <view class="bb-summary" @click="goSummary">查看汇总</view>
        <view class="bb-voice" :class="{ recording }" @touchstart="startVoice" @touchmove="onVoiceMove" @touchend="stopVoice">
          <view class="bb-mic">
            <view class="bb-mic-body"></view>
            <view class="bb-mic-bracket"></view>
            <view class="bb-mic-stand"></view>
          </view>
          <text>{{ voiceLoading ? '准备中...' : voiceRecognizing ? '识别中...' : recording ? '松开停止' : '语音搜索' }}</text>
        </view>
      </template>
    </view>

    <!-- 添加分区弹窗 -->
    <view v-if="showAdd" class="mask" @click="showAdd = false">
      <view class="sheet" @click.stop><view class="sh"></view>
        <view class="sheet-head"><text class="st">添加分区</text><text class="sx" @click="showAdd=false">✕</text></view>
        <view class="sheet-body"><text class="sl">分区名称</text><input class="si" v-model="zoneName" placeholder="请输入分区名称" /><text class="shint">新增分区排在最后</text></view>
        <view class="sheet-btns"><view class="sb-cancel" @click="showAdd=false">取消</view><view class="sb-confirm" @click="doAddZone">{{ saving ? '添加中...' : '确认添加' }}</view></view>
      </view>
    </view>

    <!-- 返回确认弹窗 -->
    <view v-if="showSaveConfirm" class="mask" @click="showSaveConfirm = false">
      <view class="sheet" @click.stop><view class="sh"></view>
        <view class="sheet-head"><text class="st">提示</text></view>
        <view class="sheet-body"><text class="del-msg">是否保存当前修改</text></view>
        <view class="sheet-btns">
          <view class="sb-cancel" @click="discardAndExit">不保存</view>
          <view class="sb-confirm" @click="saveAndExit">{{ saving ? '保存中...' : '保存' }}</view>
        </view>
      </view>
    </view>

    <!-- 删除确认弹窗 -->
    <view v-if="showDeleteConfirm" class="mask" @click="showDeleteConfirm = false">
      <view class="sheet" @click.stop><view class="sh"></view>
        <view class="sheet-head"><text class="st">确认删除</text></view>
        <view class="sheet-body"><text class="del-msg">确定删除 {{ selected.size }} 个分区吗？</text></view>
        <view class="sheet-btns">
          <view class="sb-cancel" @click="showDeleteConfirm = false">取消</view>
          <view class="sb-danger" @click="confirmDelete">{{ saving ? '删除中...' : '确认删除' }}</view>
        </view>
      </view>
    </view>

    <!-- 搜索结果选择 -->
    <view v-if="showSearchPicker" class="mask" @click="showSearchPicker = false">
      <view class="sheet search-sheet" @click.stop>
        <view class="sh"></view>
        <view class="sheet-head"><text class="st">选择物料</text><text class="sx" @click="showSearchPicker=false">✕</text></view>
        <view class="search-results">
          <view class="search-results-inner">
            <view v-for="(item, idx) in searchResults" :key="item.materialId + '-' + item.taskZoneId + '-' + idx" class="sr-item" @click="goToMaterial(item)">
              <text class="sri-name">{{ materialDisplayName(item) }}</text>
              <text class="sri-zone">{{ item.zoneName }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 录音浮层（含加载中状态） -->
    <view v-if="recording || voiceLoading" class="voice-overlay" :class="{ cancelling: voiceCancelled }" @touchmove.stop.prevent="onVoiceMove" @touchend.stop.prevent="stopVoice" @click="cancelVoice">
      <view v-if="voiceLoading" class="vw-loading-spinner"></view>
      <view v-else-if="!voiceCancelled" class="vw-mic">
        <view class="vw-mic-body"></view>
        <view class="vw-mic-bracket"></view>
        <view class="vw-mic-stand"></view>
      </view>
      <view v-else class="vw-cancel">
        <text class="vw-cancel-icon">✕</text>
      </view>
      <text class="vw-text">{{ voiceLoading ? '请稍候...' : voiceCancelled ? '松开取消' : '正在聆听...' }}</text>
      <view class="vw-bars" v-if="!voiceCancelled && !voiceLoading">
        <view class="vw-bar" v-for="i in 20" :key="i" :style="{animationDelay: (i*0.08)+'s'}"></view>
      </view>
      <text class="vw-hint">↑ 上滑取消</text>
    </view>
  </template>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$ps2:#247847;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 220rpx}
.card{border-radius:20rpx;padding:28rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}
.info-card{background:$s;position:relative;overflow:hidden;margin-bottom:24rpx}
.info-badge{position:absolute;top:0;right:0;padding:12rpx 28rpx;font-size:24rpx;font-weight:600;border-radius:0 0 0 16rpx}
.st-active{background:$ps;color:$p}.st-done{background:#EEF1EF;color:$t2}.st-pending{background:#FAFBF9;color:$t3}
.info-title{display:block;margin-top:4rpx;font-size:40rpx;font-weight:700;color:$t1;padding-right:140rpx}
.info-rows{margin-top:28rpx;display:flex;flex-direction:column;gap:16rpx}
.ir{display:flex;justify-content:space-between}.irk{font-size:26rpx;color:$t2}.irv{font-size:28rpx;font-weight:600;color:$t1}.irv.danger{color:$d}
.progress-card{background:#F1F8F3;margin-bottom:32rpx}
.pc-top{display:flex;justify-content:space-between;align-items:flex-end}.pc-label{font-size:28rpx;color:$t2}
.pc-big{font-size:64rpx;font-weight:800;color:$p;line-height:1}.pc-total{font-size:28rpx;color:$t2}
.bar-wrap{height:14rpx;border-radius:999rpx;background:$s;margin-top:20rpx;overflow:hidden}
.bar-fill{height:100%;border-radius:999rpx;background:$p;transition:width .3s}
.pc-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:8rpx;margin-top:28rpx;text-align:center}
.pcs-label{display:block;font-size:24rpx;color:$t2}.pcs-val{display:block;margin-top:8rpx;font-size:40rpx;font-weight:800;color:$t1}.pcs-val.green{color:$p}.pcs-val.danger{color:$d}
/* 物料搜索框 */
.search-bar{display:flex;align-items:center;gap:16rpx;margin-bottom:24rpx}
.sb-input-wrap{flex:1;display:flex;align-items:center;background:$s;border-radius:16rpx;border:2rpx solid $b;padding:0 20rpx;height:80rpx}
.sb-search-icon{font-size:28rpx;margin-right:12rpx;flex-shrink:0}
.sb-input{flex:1;font-size:28rpx;color:$t1}
.sb-clear{font-size:28rpx;color:$t3;padding:8rpx}.sb-go{font-size:26rpx;color:$p;font-weight:600;padding:8rpx 16rpx;flex-shrink:0}
.sb-btn{width:80rpx;height:80rpx;border-radius:16rpx;background:$ps2;display:flex;align-items:center;justify-content:center;font-size:32rpx;flex-shrink:0}
.sb-btn.recording{background:$d}
.sb-searching{font-size:26rpx;color:$t2;flex-shrink:0}
.search-sheet{max-height:70vh;display:flex;flex-direction:column}
.search-results{flex:1;max-height:50vh;overflow-y:auto;-webkit-overflow-scrolling:touch}
.search-results-inner{padding:0 32rpx calc(env(safe-area-inset-bottom) + 24rpx);box-sizing:border-box}
.sr-item{display:flex;align-items:center;gap:24rpx;padding:24rpx 0;border-bottom:2rpx solid #EEF1EF;box-sizing:border-box;width:100%}
.sri-name{flex:1;min-width:0;font-size:30rpx;font-weight:600;color:$t1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sri-zone{flex-shrink:0;font-size:26rpx;color:$t2;text-align:right;white-space:nowrap;max-width:240rpx;overflow:hidden;text-overflow:ellipsis}

.section{margin-top:8rpx}
.section-top{display:flex;align-items:flex-start;justify-content:space-between;gap:16rpx;margin-bottom:20rpx}
.section-title{display:inline;font-size:36rpx;font-weight:700;color:$t1}.section-sub{display:block;margin-top:8rpx;font-size:26rpx;color:$t2}
.refresh-icon{display:inline-block;margin-left:12rpx;font-size:36rpx;color:$p;vertical-align:middle}
.section-actions{display:flex;align-items:center;gap:16rpx;flex-shrink:0}
.edit-mode-btn{font-size:26rpx;font-weight:600;color:$p;white-space:nowrap;flex-shrink:0}
.add-zone-btn{display:inline-flex;align-items:center;gap:8rpx;padding:16rpx 24rpx;border-radius:16rpx;background:$ps2;color:#fff;font-size:26rpx;font-weight:600;white-space:nowrap;flex-shrink:0;box-shadow:0 2rpx 8rpx rgba(31,36,33,.04)}
.zone-list{display:flex;flex-direction:column;gap:16rpx}
.zone-card{display:flex;align-items:center;gap:26rpx;padding:24rpx 20rpx;background:$s;border-radius:20rpx;border:2rpx solid $b;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04)}
.zone-card.done{background:#FAFBF9}
.zc-check{flex-shrink:0;display:flex;align-items:center}
.check-box{width:44rpx;height:44rpx;border-radius:8rpx;border:2rpx solid $b;display:flex;align-items:center;justify-content:center;font-size:28rpx;color:#fff}
.check-box.on{background:$p;border-color:$p}
.zc-order{width:100rpx;flex-shrink:0;display:flex;align-items:center;justify-content:center}
.order-num{width:80rpx;height:56rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700;color:$t1;border:2rpx solid $b;border-radius:10rpx;background:#FAFBF9}
.order-input{width:80rpx;height:64rpx;text-align:center;font-size:30rpx;font-weight:700;border:2rpx solid $b;border-radius:12rpx;color:$t1;background:#FAFBF9}
.zc-order{width:72rpx;flex-shrink:0;display:flex;align-items:center;justify-content:center}
.zc-content{flex:1;display:flex;align-items:center;gap:16rpx;min-width:0}
.zc-icon{width:72rpx;height:72rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:32rpx;color:#B0B0B0;background:#F2F2F2;flex-shrink:0}
.zc-icon.done{background:$ps;color:$p}.zc-icon.prog{background:$ps2;color:#fff}
.zc-info{flex:1;min-width:0}
.zc-top-row{display:flex;align-items:center;justify-content:space-between;gap:12rpx}
.zc-name{font-size:30rpx;font-weight:700;color:$t1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.zone-name-input{flex:1;min-width:0;font-size:30rpx;font-weight:700;color:$t1;padding:4rpx 8rpx;border:2rpx solid $p;border-radius:8rpx;background:#fff}
.zc-count{font-size:26rpx;color:$t2;flex-shrink:0}
.zc-bar-wrap{height:8rpx;border-radius:999rpx;background:#EEF1EF;margin-top:12rpx;overflow:hidden}
.zc-bar-fill{height:100%;border-radius:999rpx;background:$p}
.zc-bar-text{font-size:24rpx;color:#B0B0B0}
.zc-arrows{display:flex;flex-direction:column;gap:4rpx;flex-shrink:0}
.arrow-btn{width:48rpx;height:40rpx;border-radius:8rpx;background:#FAFBF9;border:1rpx solid $b;display:flex;align-items:center;justify-content:center;font-size:20rpx;color:$t2}
.arrow-btn.disabled{opacity:.3}
.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:grid;grid-template-columns:1fr 1fr;gap:24rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 16rpx);background:#fff;border-top:2rpx solid #EEF1EF;box-shadow:0 -4rpx 16rpx rgba(31,36,33,.04)}
.bottom-bar.edit{display:flex}.bottom-bar.edit .bb-back{flex:1}.bottom-bar.edit .bb-del{flex:1}
.bb-summary{height:88rpx;border-radius:16rpx;border:2rpx solid $b;background:$s;color:$ps2;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:600}
.bb-voice{height:88rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;gap:8rpx;font-size:26rpx;font-weight:600}.bb-voice.recording{background:$d}
.bb-mic{width:28rpx;height:38rpx;display:flex;flex-direction:column;align-items:center;flex-shrink:0;margin-top:4rpx}
.bb-mic-body{width:18rpx;height:22rpx;border:2rpx solid #fff;border-radius:11rpx;box-sizing:border-box}
.bb-mic-bracket{width:20rpx;height:4rpx;border:0 solid #fff;border-bottom-width:2rpx;border-left-width:2rpx;border-right-width:2rpx;border-radius:0 0 11rpx 11rpx;margin-top:-2rpx}
.bb-mic-stand{width:2rpx;height:5rpx;background:#fff;border-radius:1rpx;margin-top:0}
.bb-back{height:88rpx;border-radius:16rpx;border:2rpx solid $b;background:$s;color:$t1;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.bb-confirm{flex:2;height:88rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}
.bb-del{height:88rpx;border-radius:16rpx;background:$d;color:#fff;display:flex;align-items:center;justify-content:center;gap:8rpx;font-size:28rpx;font-weight:600}
.bb-del.disabled{opacity:.5}
.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;max-height:85vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column}
.sh{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:20rpx auto}
.sheet-head{display:flex;justify-content:center;padding:8rpx 32rpx 20rpx;position:relative}.st{font-size:36rpx;font-weight:700;color:$t1}.sx{position:absolute;right:32rpx;font-size:40rpx;color:$t2;padding:8rpx}
.sheet-body{padding:0 32rpx 24rpx;display:flex;flex-direction:column;gap:16rpx}
.sl{font-size:28rpx;font-weight:600;color:$t1}.si{padding:24rpx;border:2rpx solid transparent;border-radius:12rpx;font-size:28rpx;background:#FAFBF9}.shint{font-size:24rpx;color:$t3;display:flex;align-items:center;gap:6rpx}
.del-msg{padding:0;font-size:28rpx;color:$t2;line-height:1.5}
.sheet-btns{display:grid;grid-template-columns:1fr 1fr;gap:24rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);border-top:2rpx solid #EEF1EF}
.sb-cancel{height:96rpx;border-radius:999rpx;background:#FAFBF9;color:$t1;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.sb-confirm{height:96rpx;border-radius:999rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.sb-danger{height:96rpx;border-radius:999rpx;background:$d;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}

/* 录音浮层 */
.voice-overlay{position:fixed;inset:0;z-index:200;display:flex;flex-direction:column;align-items:center;justify-content:center;background:rgba(31,36,33,.85)}.voice-overlay.cancelling{background:rgba(224,90,71,.85)}.voice-overlay.cancelling .vw-bar{background:#E05A47}
.vw-loading-spinner{width:60rpx;height:60rpx;border:4rpx solid rgba(255,255,255,.2);border-top-color:rgba(255,255,255,.9);border-radius:50%;animation:spin .8s linear infinite;margin-bottom:24rpx}@keyframes spin{to{transform:rotate(360deg)}}
.vw-mic{width:70rpx;height:96rpx;display:flex;flex-direction:column;align-items:center;margin-bottom:20rpx}.vw-mic-body{width:44rpx;height:52rpx;border:6rpx solid rgba(255,255,255,.9);border-radius:26rpx;box-sizing:border-box}.vw-mic-bracket{width:52rpx;height:8rpx;border:0 solid rgba(255,255,255,.9);border-bottom-width:6rpx;border-left-width:6rpx;border-right-width:6rpx;border-radius:0 0 26rpx 26rpx;margin-top:-3rpx}.vw-mic-stand{width:6rpx;height:16rpx;background:rgba(255,255,255,.9);border-radius:3rpx;margin-top:0}
.vw-cancel{width:120rpx;height:120rpx;border-radius:50%;border:4rpx solid rgba(255,255,255,.8);display:flex;align-items:center;justify-content:center;margin-bottom:20rpx}.vw-cancel-icon{font-size:56rpx;color:rgba(255,255,255,.8);font-weight:300}
.vw-text{font-size:28rpx;color:rgba(255,255,255,.7);margin-bottom:28rpx}
.vw-bars{display:flex;align-items:center;gap:6rpx;height:80rpx}
.vw-bar{width:6rpx;border-radius:3rpx;background:$p;animation:waveBar .6s ease-in-out infinite alternate}
@keyframes waveBar{0%{height:12rpx}100%{height:80rpx}}
.vw-hint{position:absolute;bottom:80rpx;font-size:24rpx;color:rgba(255,255,255,.35)}
</style>