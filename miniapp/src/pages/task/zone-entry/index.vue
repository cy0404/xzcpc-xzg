<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { fetchZoneMaterials, saveZone, itemSave } from '@/api/zone'
import { fetchCandidates, addMaterial, sortMaterials } from '@/api/material'
import { request } from '@/utils/request'
import { materialDisplayName } from '@/utils/formatter'
import EmptyState from '@/components/EmptyState.vue'
import Skeleton from '@/components/Skeleton.vue'
import DrawerNumericKeyboard from '@/components/DrawerNumericKeyboard.vue'

const taskId = ref(0)
const zoneId = ref(0)
const zoneName = ref('')
const storeName = ref('')
const taskName = ref('')
const loading = ref(true)
const saving = ref(false)
const materials = ref<any[]>([])
const searchKey = ref('')
const showPending = ref(true)
const showDone = ref(true)
const focusMaterialId = ref('')
const focusMaterialName = ref('')
const locateMode = ref(false)
const silentInitialLoad = ref(false)

const drawerItem = ref<any>(null)
const drawerUnitInputs = ref<Record<string,string>>({})
const drawerIsEdit = ref(false)
const drawerTab = ref<'add' | 'overwrite'>('add')
const existingUnitInputs = ref<Record<string,string>>({})

const editMode = ref(false)
const editList = ref<any[]>([])
const listKey = ref(0)
const editingIdx = ref(-1)
const editValue = ref('')

const drawerActiveUnit = ref('')
const drawerWeightUnit = ref('g')  // g / kg 切换
const parsedVoiceQty = ref<number | null>(null)
const parsedVoiceUnit = ref('')
const voiceHintText = ref('')  // 模糊匹配时的提示文字
const extDrawerActiveUnit = ref('')
const showFinishConfirm = ref(false)
const finishConfirmNames = ref('')

const recording = ref(false)
const voiceLoading = ref(false)
const voiceRecognizing = ref(false)
const voiceCooldown = ref(false)
let voiceManager: any = null
let voiceManagerReady = false
const voiceCancelled = ref(false)

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
        locateMode.value = false
        focusMaterialId.value = ''
        const parsed = parseVoiceInput(res.result)
        if (parsed) {
          parsedVoiceQty.value = parsed.qty
          parsedVoiceUnit.value = parsed.unit
          // 如果解析出的物料名与当前 searchKey 相同，watch 不会触发 → 手动打版本号强制触发
          if (searchKey.value === parsed.name) voiceSearchVersion.value++
          searchKey.value = parsed.name
        } else {
          parsedVoiceQty.value = null
          parsedVoiceUnit.value = ''
          if (searchKey.value === res.result) voiceSearchVersion.value++
          searchKey.value = res.result
        }
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

const touchStartY = ref(0)
let longPressTimer: any = null
let cooldownSafetyTimer: any = null

/** 启动冷却锁 + 1 秒兜底超时，防止插件异常不回调导致永久卡死 */
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

onLoad(async (options: any) => {
  taskId.value = Number(options.taskId)
  zoneId.value = options.zoneId ? Number(options.zoneId) : 0
  zoneName.value = options.zoneName ? decodeURIComponent(options.zoneName) : ''
  storeName.value = options.storeName ? decodeURIComponent(options.storeName) : ''
  taskName.value = options.taskName ? decodeURIComponent(options.taskName) : ''
  focusMaterialId.value = options.focusMaterialId ? decodeURIComponent(options.focusMaterialId) : ''
  focusMaterialName.value = options.focusMaterialName ? decodeURIComponent(options.focusMaterialName) : ''
  silentInitialLoad.value = options.fromSearch === '1'
  if (focusMaterialName.value) searchKey.value = focusMaterialName.value
  if (silentInitialLoad.value) loading.value = false
  uni.setNavigationBarTitle({ title: '物料盘点' })
  resetDrawer(); addSearchVisible.value = false; showEditConfirm.value = false; editMode.value = false
  // 如果没传 zoneId，自动取第一个分区
  if (!zoneId.value) {
    try {
      const detail: any = await request({ url: `/tasks/${taskId.value}`, showLoading: true })
      const zones = detail?.zones || []
      if (zones.length === 0) { uni.showToast({ title: '暂无分区', icon: 'none' }); loading.value = false; return }
      zoneId.value = zones[0].taskZoneId || zones[0].id
      zoneName.value = zones[0].zoneName || ''
    } catch { uni.showToast({ title: '加载失败', icon: 'none' }); loading.value = false; return }
  }
  loadMaterials()
  // 提前初始化语音插件，避免首次按住时 ~2s 的初始化延迟
  getVoiceManager()
})

onUnload(() => {
  clearTimeout(longPressTimer)
  clearTimeout(cooldownSafetyTimer)
  clearTimeout(addSearchTimer)
  clearTimeout(extTimer)
})

async function loadMaterials() {
  const quiet = silentInitialLoad.value
  silentInitialLoad.value = false
  if (!quiet) loading.value = true
  resetDrawer(); showEditConfirm.value = false
  try {
    materials.value = ((await fetchZoneMaterials(taskId.value, zoneId.value, { showLoading: !quiet })) as any[]) || []
  }
  finally { loading.value = false; applyLocate() }
}

function applyLocate() {
  if (!focusMaterialId.value || editMode.value) return
  const m = materials.value.find(x => x.materialId === focusMaterialId.value)
  if (!m) {
    uni.showToast({ title: '该分区未找到该物料', icon: 'none' })
    focusMaterialId.value = ''
    focusMaterialName.value = ''
    locateMode.value = false
    return
  }
  locateMode.value = true
  if (focusMaterialName.value) searchKey.value = focusMaterialName.value
  if (m.inputStatus === 'entered') {
    showPending.value = false
    showDone.value = true
  } else {
    showPending.value = true
    showDone.value = false
  }
}

const pending = computed(() => materials.value.filter(m => !m.inputStatus || m.inputStatus !== 'entered'))
const completed = computed(() => materials.value.filter(m => m.inputStatus === 'entered'))
const pendingCount = computed(() => pending.value.length)
const doneCount = computed(() => completed.value.length)
const totalCount = computed(() => materials.value.length)
const progressPercent = computed(() => totalCount.value ? Math.round(doneCount.value / totalCount.value * 100) : 0)

const isAirportStore = computed(() => (storeName.value || '').includes('机场'))

const airportFilter = (list: any[]) => {
  if (isAirportStore.value) return list
  return list.filter(m => !(m.materialName||'').includes('机场'))
}

const searchFilter = (list: any[]) => {
  let filtered = airportFilter(list)
  if (locateMode.value && focusMaterialId.value && !editMode.value) {
    return filtered.filter(m => m.materialId === focusMaterialId.value)
  }
  if (!searchKey.value.trim()) return filtered
  const kw = searchKey.value.trim().toLowerCase()
  return filtered.filter(m => (m.materialName || '').toLowerCase().includes(kw))
}
const filteredPending = computed(() => searchFilter(airportFilter(pending.value)))
const filteredCompleted = computed(() => searchFilter(airportFilter(completed.value)))

const displayList = computed(() => editMode.value ? editList.value : materials.value)
const hasRule = (m: any) => m.inventoryRule?.ruleStatus === 'maintained'
const isMultiUnit = (m: any) => hasRule(m) && (m.inventoryRule?.units?.length || 0) > 1
/** 判断物料是否有称重类型换算：优先 weightConversions，回退 conversionSnapshot */
const hasWeightUnit = (m: any) => {
  if ((m?.inventoryRule?.weightConversions || []).length > 0) return true
  try {
    const snap = m?.conversionSnapshot
    if (!snap) return false
    const arr = typeof snap === 'string' ? JSON.parse(snap) : snap
    return (arr || []).some((c: any) => c.conversionType === 'weight')
  } catch { return false }
}
/** 判断某个单位是否为称重类型 */
const isWeightUnit = (m: any, u: string) => u === 'g' && hasWeightUnit(m)

function breakdownText(m: any) { console.log("bd", m.materialName, "ui:", m.unitInputs, "multi:", isMultiUnit(m))
  if (!m.inputStatus || m.inputStatus !== 'entered') return ''
  if (m.inputQty == null) return ''
  if (isMultiUnit(m)) {
    let stored = uni.getStorageSync(`ui_${taskId.value}_${zoneId.value}_${m.materialId}`)
    if (!stored && m.unitInputs) { try { stored = JSON.parse(m.unitInputs) } catch {} }
    if (stored) {
      const parts: string[] = []
      for (const [u, v] of Object.entries(stored)) {
        if (v && Number(v) > 0) parts.push(`${v}${u}`)
      }
      return parts.length ? `${parts.join(' + ')}` : `--`
    }
    return `--`
  }
  const qty = Number(m.inputQty)
  return isNaN(qty) ? '' : `${qty} ${m.unit || ''}`
}

function getStoredUnitInputs(m: any): Record<string, string> {
  const stored = uni.getStorageSync(`ui_${taskId.value}_${zoneId.value}_${m.materialId}`)
  if (stored && typeof stored === 'object') return { ...stored }
  if (m.unitInputs) {
    try { return { ...JSON.parse(m.unitInputs) } } catch { /* ignore */ }
  }
  const b = baseUnit(m)
  const u = m.inputOriginalUnit || m.unit || b
  if (m.inputQty != null && m.inputQty !== '') return { [u]: String(m.inputOriginalQty ?? m.inputQty) }
  return {}
}

function unitToBaseFactor(m: any, u: string) {
  const b = baseUnit(m)
  if (u === b) return 1
  if (u === 'kg') {
    const gFactor = unitToBaseFactor(m, 'g')
    return gFactor ? gFactor * 1000 : 0
  }
  // 单位换算（从 inventoryRule.conversions）
  const c = m.inventoryRule?.conversions?.find((x: any) => x.fromUnit === u)
  if (c) return Number(c.toQuantity) / Number(c.fromQuantity)
  // 称重换算（优先 weightConversions，回退 conversionSnapshot）
  const w = m.inventoryRule?.weightConversions?.find((x: any) => x.weightUnit === u)
  if (w) return Number(w.countQuantity) / Number(w.weightQuantity)
  try {
    const snap = m?.conversionSnapshot
    if (snap) {
      const arr = typeof snap === 'string' ? JSON.parse(snap) : snap
      const ws = (arr || []).find((x: any) => x.conversionType === 'weight' && x.fromUnit === u)
      if (ws) return Number(ws.toQuantity) / Number(ws.fromQuantity)
    }
  } catch {}
  return 0
}

function fmtFactor(f: number) {
  if (!f || f === 1) return '1'
  return f.toFixed(4).replace(/\.?0+$/, '')
}

function conversionHint(m: any, u: string) {
  const b = baseUnit(m)
  if (u === b) return ''
  const f = unitToBaseFactor(m, u)
  if (!f) return ''
  return `1${u}≈${fmtFactor(f)}${b}`
}

function extConversionHint(m: any, u: string) {
  const b = m.baseUnit || m.unit || ''
  if (!b || u === b) return ''
  const c = (m.conversions || []).find((x: any) => x.fromUnit === u)
  if (!c) return ''
  const f = Number(c.toQuantity) / Number(c.fromQuantity)
  if (!f) return ''
  return `1${u}=${fmtFactor(f)}${b}`
}

function sumInputsToBase(m: any, inputs: Record<string, string>) {
  const b = baseUnit(m)
  let t = 0
  for (const [u, v] of Object.entries(inputs)) {
    const n = parseFloat(v || '0')
    if (!n) continue
    t += u === b ? n : n * unitToBaseFactor(m, u)
  }
  return t
}

/** 盘点单位为计数单位且含 g 录入时，仅对 g 换算部分四舍五入 */
const G_ROUND_COUNT_UNITS = new Set(['个', '张', '根', '支', '片', '副'])

function isGRoundCountBase(m: any) {
  return G_ROUND_COUNT_UNITS.has(baseUnit(m))
}

function hasGInput(inputs: Record<string, string>) {
  return parseFloat(inputs.g || '0') > 0
}

function unitInputToBase(m: any, u: string, n: number) {
  const b = baseUnit(m)
  if (!n) return 0
  if (u === b) return n
  if (u === 'kg') {
    const gFactor = unitToBaseFactor(m, 'g')
    return gFactor ? n * gFactor * 1000 : n * unitToBaseFactor(m, 'g') * 1000
  }
  return n * unitToBaseFactor(m, u)
}

function sumInputsToBaseWithGRounding(m: any, inputs: Record<string, string>) {
  if (!isGRoundCountBase(m)) return sumInputsToBase(m, inputs)
  let gPart = 0
  let otherPart = 0
  let hasG = false
  for (const [u, v] of Object.entries(inputs)) {
    const n = parseFloat(v || '0')
    if (!n) continue
    if (u === 'g' || u === 'kg') {
      hasG = true
      gPart += unitInputToBase(m, u, n)
    } else {
      otherPart += unitInputToBase(m, u, n)
    }
  }
  return (hasG ? Math.round(gPart) : 0) + otherPart
}

function drawerQtyZeroMessage(m: any, inputs: Record<string, string>, isAddEntry = false) {
  if (isGRoundCountBase(m) && hasGInput(inputs)) return '数量须大于 0'
  if (isAddEntry) return '本次录入须大于 0'
  return '请输入数量'
}

function fmtQty(n: number) {
  if (!n) return '0'
  return n.toFixed(3).replace(/\.?0+$/, '')
}

function mergeUnitInputs(existing: Record<string, string>, addition: Record<string, string>) {
  const merged: Record<string, string> = { ...existing }
  for (const [u, v] of Object.entries(addition)) {
    const a = parseFloat(v || '0') || 0
    if (!a) continue
    const e = parseFloat(merged[u] || '0') || 0
    merged[u] = String(e + a)
  }
  return merged
}

function initDrawerInputsForTab() {
  const m = drawerItem.value
  if (!m) return
  const units = ruleUnits(m)
  if (drawerIsEdit.value && drawerTab.value === 'overwrite') {
    const src = { ...existingUnitInputs.value }
    drawerUnitInputs.value = {}
    for (const u of units) drawerUnitInputs.value[u] = src[u] || ''
    drawerActiveUnit.value = units[0] || baseUnit(m) || ''
    return
  }
  drawerUnitInputs.value = {}
  for (const u of units) drawerUnitInputs.value[u] = ''
  drawerActiveUnit.value = units[0] || baseUnit(m) || ''
}

function openDrawer(m: any) {
  drawerIsEdit.value = false
  drawerTab.value = 'add'
  drawerWeightUnit.value = 'g'
  drawerItem.value = m
  existingUnitInputs.value = {}
  const stored = getStoredUnitInputs(m)
  drawerUnitInputs.value = Object.keys(stored).length ? { ...stored } : {}
  const bUnit = baseUnit(m)
  if (!Object.keys(drawerUnitInputs.value).length && bUnit) drawerUnitInputs.value[bUnit] = ''
  drawerActiveUnit.value = ruleUnits(m)[0] || bUnit || ''
}

const currAppendRecords = ref<any[]>([])

function openEditDrawer(m: any) {
  drawerIsEdit.value = true
  drawerTab.value = 'add'
  drawerItem.value = m
  existingUnitInputs.value = getStoredUnitInputs(m)
  initDrawerInputsForTab()
  drawerActiveUnit.value = ruleUnits(m)[0] || baseUnit(m) || ''
  try {
    currAppendRecords.value = m.appendRecords ? JSON.parse(m.appendRecords) : []
  } catch { currAppendRecords.value = [] }
}

function switchDrawerTab(tab: 'add' | 'overwrite') {
  if (drawerTab.value === tab) return
  drawerTab.value = tab
  initDrawerInputsForTab()
}

function resetDrawer() {
  drawerItem.value = null
  drawerIsEdit.value = false
  drawerTab.value = 'add'
  drawerUnitInputs.value = {}
  existingUnitInputs.value = {}
  drawerActiveUnit.value = ''
  currAppendRecords.value = []
}

function voiceUnitPattern() {
  const units = new Set<string>()
  for (const m of materials.value) {
    const rule = m?.inventoryRule
    if (rule?.units) for (const u of rule.units) { if (u.unitName) units.add(u.unitName) }
    if (rule?.baseUnit) units.add(rule.baseUnit)
  }
  if (units.size === 0) units.add('个')
  return [...units].sort((a, b) => b.length - a.length).join('|')
}

/** 中文数字 → 阿拉伯数字（语音引擎可能将 "1" 识别为 "一"） */
function cnToArabic(s: string): string {
  const digits: Record<string, number> = { '零':0,'一':1,'二':2,'两':2,'三':3,'四':4,'五':5,'六':6,'七':7,'八':8,'九':9 }
  const units: Record<string, number> = { '十':10,'百':100,'千':1000,'万':10000 }
  let result = 0, section = 0, num = 0
  for (const ch of s) {
    if (digits[ch] !== undefined) { num = digits[ch] }
    else if (units[ch] !== undefined) {
      if (num === 0) num = 1
      const u = units[ch]
      if (u >= 10000) { section = (section + num) * u; num = 0 }
      else { section += num * u; num = 0 }
    } else { return s } // 非中文数字，原样返回
  }
  return String(result + section + num)
}

function parseVoiceInput(text: string): { name: string; qty: number; unit: string } | null {
  let t = text.trim()
  // 中文数字 → 阿拉伯数字
  t = t.replace(/[零一二两三四五六七八九十百千万]+/g, m => cnToArabic(m))
  // 优先用动态单位列表匹配
  const dynUnits = voiceUnitPattern()
  let re = new RegExp(`^(.+?)\\s*(\\d+\\.?\\d*)\\s*(${dynUnits})$`)
  let m = t.match(re)
  if (!m) {
    m = t.match(/^(.+?)\s*(\d+\.?\d*)\s*(\S+)$/)
  }
  if (!m) return null
  return { name: m[1].trim(), qty: parseFloat(m[2]), unit: m[3] }
}

/** 尝试用语音解析的数量回填当前已打开的抽屉，单位匹配不上则忽略 */
function tryVoicePreFill() {
  if (parsedVoiceQty.value === null) return
  const u = parsedVoiceUnit.value
  if (drawerItem.value) {
    const units = ruleUnits(drawerItem.value)
    if (!units.includes(u)) { parsedVoiceQty.value = null; parsedVoiceUnit.value = ''; return }
    drawerUnitInputs.value = { [u]: String(parsedVoiceQty.value) }
    drawerActiveUnit.value = u
    parsedVoiceQty.value = null; parsedVoiceUnit.value = ''
  } else if (extDrawerItem.value) {
    const ex = extDrawerItem.value as any
    const extUnits = ex.units?.map((x: any) => x.unitName) || []
    if (!extUnits.includes(u)) { parsedVoiceQty.value = null; parsedVoiceUnit.value = ''; return }
    extDrawerUnitInputs.value = { [u]: String(parsedVoiceQty.value) }
    extDrawerActiveUnit.value = u
    parsedVoiceQty.value = null; parsedVoiceUnit.value = ''
  } else {
    parsedVoiceQty.value = null; parsedVoiceUnit.value = ''
  }
}

/** 箭头点击时快照语音值，避免 setTimeout 内被清空 */
function voicePreFillSnapshot() {
  const q = parsedVoiceQty.value, u = parsedVoiceUnit.value
  setTimeout(() => {
    if (q === null || !drawerItem.value) return
    const units = ruleUnits(drawerItem.value)
    if (!units.includes(u)) return
    drawerUnitInputs.value = { [u]: String(q) }
    drawerActiveUnit.value = u
    parsedVoiceQty.value = null; parsedVoiceUnit.value = ''
  }, 100)
}

function clearSearch() {
  searchKey.value = ''
  locateMode.value = false
  focusMaterialId.value = ''
  focusMaterialName.value = ''
  extResults.value = []
  parsedVoiceQty.value = null
  parsedVoiceUnit.value = ''
  voiceHintText.value = ''
}

/** 点击待盘物料：多单位打开抽屉，单单位有语音值则快速保存 */
function handlePendingClick(m: any) {
  voiceHintText.value = ''
  if (isMultiUnit(m)) {
    openDrawer(m)
    setTimeout(() => tryVoicePreFill(), 100)
  } else if (parsedVoiceQty.value !== null) {
    quickSave(m, String(parsedVoiceQty.value))
    parsedVoiceQty.value = null; parsedVoiceUnit.value = ''
  }
}

function handleCompletedClick(m: any) {
  voiceHintText.value = ''
  openEditDrawer(m)
  setTimeout(() => tryVoicePreFill(), 100)
}

function setDrawerActiveUnit(u: string) {
  drawerActiveUnit.value = u
}
function toggleWeightUnit() {
  drawerWeightUnit.value = drawerWeightUnit.value === 'g' ? 'kg' : 'g'
  drawerActiveUnit.value = drawerWeightUnit.value
}
function weightConversionHint(m: any, u: string) {
  const factor = u === 'kg' ? unitToBaseFactor(m, 'g') * 1000 : unitToBaseFactor(m, 'g')
  if (!factor) return ''
  const b = baseUnit(m)
  const rel = b === 'g' ? '=' : '≈'
  return `1${u}${rel}${fmtFactor(factor)}${b}`
}

function onDrawerKeyboardInput(val: string) {
  if (!drawerActiveUnit.value) return
  drawerUnitInputs.value = { ...drawerUnitInputs.value, [drawerActiveUnit.value]: val }
}

function setExtDrawerActiveUnit(u: string) {
  extDrawerActiveUnit.value = u
}

function onExtKeyboardInput(val: string) {
  if (!extDrawerActiveUnit.value) return
  extDrawerUnitInputs.value = { ...extDrawerUnitInputs.value, [extDrawerActiveUnit.value]: val }
}

function closeDrawer() {
  resetDrawer()
}

function discardDrawer() {
  resetDrawer()
}
function baseUnit(m: any) { return m.inventoryRule?.baseUnit || m.unit || '' }
function ruleUnits(m: any) { return m.inventoryRule?.units?.map((u:any)=>u.unitName) || (m.unit?[m.unit]:[]) }

const drawerExistingQty = computed(() => {
  const m = drawerItem.value
  if (!m) return '0'
  const n = Number(m.baseQty ?? m.inputQty ?? 0)
  return fmtQty(isNaN(n) ? 0 : n)
})

const drawerEntryTotal = computed(() => {
  const m = drawerItem.value
  if (!m) return '0'
  return fmtQty(sumInputsToBaseWithGRounding(m, drawerUnitInputs.value))
})

/** 从 appendRecords 求和的已有总量 */
const existingTotal = computed(() => {
  if (!drawerIsEdit.value) return 0
  return currAppendRecords.value.reduce((s: number, r: any) => s + (parseFloat(r.total) || 0), 0)
})

const drawerFinalQty = computed(() => {
  const m = drawerItem.value
  if (!m) return '0'
  if (drawerIsEdit.value && drawerTab.value === 'add') {
    const entry = sumInputsToBaseWithGRounding(m, drawerUnitInputs.value)
    return fmtQty(existingTotal.value + entry)
  }
  return drawerEntryTotal.value
})

const drawerTotal = computed(() => drawerEntryTotal.value)

const recordsScrollTop = ref(0)
watch(currAppendRecords, () => {
  recordsScrollTop.value += 9999
})

async function confirmDrawer() {
  if (saving.value) return
  const m = drawerItem.value; if (!m) return
  const b = baseUnit(m)
  let finalQty = 0
  let savedUnitInputs: Record<string, string> = {}

  if (drawerIsEdit.value) {
    if (drawerTab.value === 'add') {
      const entry = sumInputsToBaseWithGRounding(m, drawerUnitInputs.value)
      // 记录独立，但 material.unitInputs 存累加结果供回填
      savedUnitInputs = mergeUnitInputs(existingUnitInputs.value, drawerUnitInputs.value)
      finalQty = existingTotal.value + entry
    } else {
      savedUnitInputs = { ...drawerUnitInputs.value }
      finalQty = sumInputsToBaseWithGRounding(m, savedUnitInputs)
      if (finalQty < 0) { uni.showToast({ title: '数量不能为负数', icon: 'none' }); return }
    }
  } else {
    finalQty = sumInputsToBaseWithGRounding(m, drawerUnitInputs.value)
    if (finalQty < 0) { uni.showToast({ title: '数量不能为负数', icon: 'none' }); return }
    savedUnitInputs = { ...drawerUnitInputs.value }
  }

  // 数量异常校验
  const checkQty = drawerIsEdit.value ? finalQty : finalQty
  if (checkQty > 100000) {
    const res = await new Promise<boolean>(resolve => {
      uni.showModal({ title: '温馨提示', content: '录入数量较大，请确认是否正确', confirmText: '确认录入', cancelText: '返回修改', success: (r: any) => resolve(r.confirm) })
    })
    if (!res) return
  }

  // 累加记录：只存本次新录入（用 drawerUnitInputs，不用 merged）
  const parts: string[] = []
  for (const [u, v] of Object.entries(drawerUnitInputs.value)) {
    const n = parseFloat(v || '0'); if (n > 0) parts.push(`${n}${u}`)
  }
  const recordLine = parts.length ? parts.join(' + ') : ''
  const entryQty = sumInputsToBaseWithGRounding(m, drawerUnitInputs.value)
  const recordEntry = {
    input: recordLine,
    total: String(entryQty),
    unit: b,
    time: new Date().toISOString().replace('T', ' ').substring(0, 19),
  }
  let appendRecords = [...currAppendRecords.value]
  if (drawerIsEdit.value && drawerTab.value === 'add') {
    appendRecords.push(recordEntry)
  } else {
    appendRecords = [recordEntry]
  }

  saving.value = true
  try {
    await itemSave(taskId.value, zoneId.value, {
      materialId: m.materialId,
      qty: finalQty,
      inputMode: 'unit',
      originalQty: finalQty,
      originalUnit: b,
      unitInputs: JSON.stringify(savedUnitInputs),
      appendRecords: JSON.stringify(appendRecords),
    })
    m.inputQty = finalQty
    m.inputStatus = 'entered'
    m.appendRecords = JSON.stringify(appendRecords)
    uni.setStorageSync(`ui_${taskId.value}_${zoneId.value}_${m.materialId}`, { ...savedUnitInputs })
    searchKey.value = ''
  } catch { /* error toast from api */ }
  finally { saving.value = false; resetDrawer() }
}

async function quickSave(m: any, val: string) {
  const q=parseFloat(val); if(isNaN(q)||q<0)return
  if (q > 100000) {
    const res = await new Promise<boolean>(resolve => { uni.showModal({ title:'温馨提示', content:'录入数量较大，请确认是否正确', confirmText:'确认录入', cancelText:'返回修改', success:(r:any)=>resolve(r.confirm) }) })
    if (!res) return
  }
  try{await itemSave(taskId.value,zoneId.value,{materialId:m.materialId,qty:q,inputMode:'unit',originalQty:q,originalUnit:m.unit||''}); m.inputQty=q; m.inputQtyRaw=val; m.inputStatus='entered'}catch{}
}

async function handleSave() {
  if (saving.value) return
  if (pendingCount.value > 0) {
    finishConfirmNames.value = pending.value.map(m => materialDisplayName(m)).join('、')
    showFinishConfirm.value = true
    return
  }
  await doFinishSave()
}
async function doFinishSave() {
  saving.value = true
  showFinishConfirm.value = false
  try {
    const zeroItems = pending.value.map(m => ({
      materialId: m.materialId,
      qty: 0,
      inputMode: 'unit',
      originalQty: 0,
      originalUnit: m.unit || ''
    }))
    await saveZone(taskId.value, zoneId.value, zeroItems)
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } finally { saving.value = false }
}

/* 编辑模式 */
function enterEdit() {
  if (loading.value) { uni.showToast({ title: '数据加载中，请稍后', icon: 'none' }); return }
  locateMode.value = false
  focusMaterialId.value = ''
  focusMaterialName.value = ''
  editList.value = materials.value.map((m:any)=>({...m})); editingIdx.value = -1; editMode.value = true
}
async function doSaveEdit() {
  if (saving.value) return
  saving.value = true
  try {
    const ids = editList.value.map((m:any)=>m.taskZoneMaterialId);
    await sortMaterials(taskId.value, zoneId.value, ids);
  }
  catch { uni.showToast({ title: '保存失败', icon: 'none' }); throw null }
  finally { saving.value = false }
}
const showEditConfirm = ref(false)
async function onCancelEdit() { const orderChanged = editList.value.length === materials.value.length && editList.value.some((m: any, i: number) => m.taskZoneMaterialId !== materials.value[i]?.taskZoneMaterialId); if (orderChanged) { showEditConfirm.value = true } else { await exitEdit() } }
async function exitEdit() { editMode.value = false; editList.value = []; await loadMaterials() }
async function saveAndExit() { showEditConfirm.value = false; try { await doSaveEdit(); await exitEdit() } catch {} }
async function onSaveEdit() { try { await doSaveEdit(); await exitEdit() } catch {} }
async function discardAndExit() { showEditConfirm.value = false; await exitEdit() }
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

function goSummary() {
  if (saving.value) { uni.showToast({ title: '保存中，请稍后', icon: 'none' }); return }
  uni.navigateTo({ url: `/pages/task/summary/index?taskId=${taskId.value}` })
}
function goBack() { uni.navigateBack() }
function goScan() {
  uni.navigateTo({ url: `/pages/inventory/scan/index?taskId=${taskId.value}&zoneId=${zoneId.value}&zoneName=${encodeURIComponent(zoneName.value)}` })
}

const addSearchVisible = ref(false); const addSearchKey = ref(''); const addSearchResults = ref<any[]>([]); const addSearchLoading = ref(false); let addSearchTimer:any=null; const addSearchAddings = ref(new Set<string>())
function openAddSearch() { addSearchKey.value=''; addSearchResults.value=[]; addSearchVisible.value=true; doAddSearch() }
function onAddSearchInput() { if(addSearchTimer)clearTimeout(addSearchTimer); addSearchTimer=setTimeout(doAddSearch,300) }
async function doAddSearch() { addSearchLoading.value=true; try{addSearchResults.value=airportFilter(((await fetchCandidates(taskId.value,zoneId.value,addSearchKey.value))as any[])||[])} finally{addSearchLoading.value=false} }
async function handleAddSearch(item:any) { if(item.inCurrentZone||addSearchAddings.value.has(item.materialId))return; addSearchAddings.value.add(item.materialId); addSearchAddings.value=new Set(addSearchAddings.value); try{await addMaterial(taskId.value,zoneId.value,item.materialId); uni.showToast({title:'添加成功',icon:'success'}); item.inCurrentZone=true; loadMaterials()} finally{addSearchAddings.value.delete(item.materialId);addSearchAddings.value=new Set(addSearchAddings.value)} }

const voiceSearchVersion = ref(0)
const extResults = ref<any[]>([]); const extLoading = ref(false); let extTimer:any=null; const extAddings = ref(new Set<string>()); let extSkipWatch = false
const extDrawerItem = ref<any>(null)
const extDrawerUnitInputs = ref<Record<string,string>>({})
const extDrawerTotal = computed(() => {
  const m = extDrawerItem.value; if (!m) return '0'
  let t = 0
  for (const [u, v] of Object.entries(extDrawerUnitInputs.value)) {
    const n = parseFloat(v||'0'); if (!n) continue
    if (u === (m.baseUnit || m.unit)) t += n
    else { const c = (m.conversions||[]).find((x:any)=>x.fromUnit===u); t += c ? n*(Number(c.toQuantity)/Number(c.fromQuantity)) : 0 }
  }
  return t>0?t.toFixed(2):'0'
})
// 检查全部物料（不限当前 tab）是否有匹配
const allMatchCount = computed(() => {
  if (!searchKey.value.trim()) return -1
  const kw = searchKey.value.trim().toLowerCase()
  return materials.value.filter(m => (m.materialName||'').toLowerCase().includes(kw) || (m.spec||'').toLowerCase().includes(kw)).length
})
watch([searchKey, voiceSearchVersion, filteredPending, filteredCompleted], () => {
  if (locateMode.value && searchKey.value.trim() !== (focusMaterialName.value || '').trim()) {
    locateMode.value = false
    focusMaterialId.value = ''
    focusMaterialName.value = ''
  }
  if (focusMaterialId.value || locateMode.value) {
    extResults.value = []
    extLoading.value = false
    return
  }
  const kw = searchKey.value.trim()
  if (extSkipWatch) { extSkipWatch = false; return }
  if (!kw) { extResults.value = []; extLoading.value = false; parsedVoiceQty.value = null; parsedVoiceUnit.value = ''; showPending.value = true; showDone.value = true; return }
  showPending.value = filteredPending.value.length > 0
  showDone.value = filteredCompleted.value.length > 0
  // 精准命中唯一物料 → 自动打开抽屉
  const matchOne = (list: any[], isEdit: boolean) => {
    if (list.length !== 1 || !kw) return false
    const m = list[0]
    const qtyVal = parsedVoiceQty.value
    const unitVal = parsedVoiceUnit.value
    showPending.value = true; showDone.value = true
    // 先开抽屉再清 searchKey，避免 searchKey='' 触发的 watch 重跑在抽屉打开前干扰状态
    if (isMultiUnit(m)) {
      if (isEdit) openEditDrawer(m); else openDrawer(m)
      if (qtyVal !== null) {
        const avail = ruleUnits(m)
        if (avail.includes(unitVal)) {
          drawerUnitInputs.value[unitVal] = String(qtyVal)
          drawerActiveUnit.value = unitVal
        }
      }
    } else if (qtyVal !== null && !isEdit) {
      quickSave(m, String(qtyVal))
    } else if (qtyVal !== null && isEdit) {
      // 语音搜索命中已盘单单位物料 → 打开编辑抽屉累加模式并预填数量
      openEditDrawer(m)
      const u = m.unit || ''
      drawerUnitInputs.value = { [u]: String(qtyVal) }
      drawerActiveUnit.value = u
    }
    searchKey.value = ''
    parsedVoiceQty.value = null; parsedVoiceUnit.value = ''
    voiceHintText.value = ''
    return true
  }
  // 总数唯一才自动弹窗，多个匹配展示列表+黄色提示
  const totalMatch = filteredPending.value.length + filteredCompleted.value.length
  if (totalMatch === 1) {
    if (matchOne(filteredPending.value, false)) return
    if (matchOne(filteredCompleted.value, true)) return
  }
  // 模糊匹配且有语音解析值 → 显示提示
  if (kw && parsedVoiceQty.value !== null && totalMatch > 1) {
    voiceHintText.value = `${kw}${parsedVoiceQty.value}${parsedVoiceUnit.value}，请点击下方物料，数量自动回填`
  } else {
    voiceHintText.value = ''
  }
  if (extTimer) clearTimeout(extTimer)
  extLoading.value = true
  extTimer = setTimeout(async () => {
    try {
      const list = (await fetchCandidates(taskId.value, zoneId.value, kw)) as any[]
      extResults.value = airportFilter(Array.isArray(list) ? list : []).filter((x: any) => !x.inCurrentZone)
      // 精准命中单个物料 → 直接打开录入抽屉
      // 精准命中单个物料 → 加材质到区域后打开主抽屉
      if (extResults.value.length === 1 && zoneId.value > 0) {
        const item = extResults.value[0]
        extSkipWatch = true
        searchKey.value = ''
        extResults.value = []
        extLoading.value = false
        try {
          await addMaterial(taskId.value, zoneId.value, item.materialId)
          await loadMaterials()
          // 等 loadMaterials 更新全部 materials 后，查找刚加入的物料并打开抽屉
          const m = materials.value.find((x: any) => x.materialId === item.materialId)
          if (m) {
            if (isMultiUnit(m)) {
              // 强制设为待盘 tab 以便看到刚加的物料
              showPending.value = true
              showDone.value = true
              openDrawer(m)
            } else {
              showPending.value = true
              showDone.value = false
              focusMaterialId.value = m.materialId
            }
          }
        } catch {}
        return
      }
    } catch { extResults.value = [] }
    finally { extLoading.value = false }
  }, 500)
})
async function handleExtAdd(item:any) {
  if (extAddings.value.has(item.materialId)) return
  extAddings.value.add(item.materialId)
  try {
    const rule: any = await request({ url: `/tasks/${taskId.value}/zones/${zoneId.value}/materials/scan-rule`, data: { materialId: item.materialId }, showLoading: false, silent: true })
    const units = (rule?.units && rule.units.length > 0) ? rule.units : [{unitName: item.inventoryUnit || item.unit || ''}]
    extDrawerItem.value = {
      materialId: item.materialId,
      materialName: item.materialName,
      spec: item.spec,
      unit: item.inventoryUnit || item.unit,
      baseUnit: rule?.baseUnit || item.inventoryUnit || item.unit,
      units,
      conversions: rule?.conversions || [],
    }
    extDrawerUnitInputs.value = {}
    const first = units[0]?.unitName || extDrawerItem.value.baseUnit || ''
    if (first) extDrawerUnitInputs.value[first] = ''
    extDrawerActiveUnit.value = first
    setTimeout(() => tryVoicePreFill(), 100)
  } catch { /* ignore */ }
  extAddings.value.delete(item.materialId)
}
function closeExtDrawer() {
  extDrawerItem.value = null
  extDrawerActiveUnit.value = ''
}
async function confirmExtDrawer() {
  const m = extDrawerItem.value; if (!m || saving.value) return
  let t = 0; let h = false
  for (const [u, v] of Object.entries(extDrawerUnitInputs.value)) {
    const n = parseFloat(v||'0'); if (!n) continue; h = true
    if (u === m.baseUnit) t += n
    else { const c = (m.conversions||[]).find((x:any)=>x.fromUnit===u); t += c ? n*(Number(c.toQuantity)/Number(c.fromQuantity)) : 0 }
  }
  if (!h) { uni.showToast({title:'请输入数量',icon:'none'}); return }
  saving.value = true
  try {
    await addMaterial(taskId.value, zoneId.value, m.materialId)
    await itemSave(taskId.value, zoneId.value, { materialId: m.materialId, qty: t, inputMode:'unit', originalQty:t, originalUnit:m.baseUnit, unitInputs:JSON.stringify(extDrawerUnitInputs.value) })
    extResults.value = extResults.value.filter((x:any) => x.materialId !== m.materialId)
    closeExtDrawer()
    showDone.value = true
    loadMaterials()
    uni.showToast({title:'添加成功',icon:'success'})
  } catch { /* error toast from api */ }
  finally { saving.value = false }
}
</script>

<template>
  <view class="page">
    <Skeleton v-if="loading" :rows="5" />
    <template v-else>
      <!-- 搜索栏 -->
      <view class="search-bar" v-if="!editMode">
        <text class="sb-icon">🔍</text>
        <input class="sb-input" type="text" v-model="searchKey" placeholder="搜索当前任务物料" />
        <text v-if="searchKey" class="sb-clear" @click.stop="clearSearch">✕</text>
      </view>

      <!-- 提示条 -->
      <view class="info-banner" v-if="!editMode">
        <text class="ib-icon">ℹ️</text>
        <view class="ib-body">
          <text class="ib-title">盘点提示</text>
          <text class="ib-text">没有搜索到的物料不需要盘点。</text>
          <text class="ib-text">门店没有的物料不需要填写。</text>
        </view>
      </view>

      <!-- 筛选标签 -->
      <view class="filter-row" v-if="!editMode">
        <text class="filter-btn" :class="{active:showPending&&showDone}" @click="showPending=true;showDone=true">全部 {{ totalCount }}</text>
        <text class="filter-btn" :class="{active:showPending&&!showDone}" @click="showPending=true;showDone=false">待盘 {{ pendingCount }}</text>
        <text class="filter-btn" :class="{active:!showPending&&showDone}" @click="showPending=false;showDone=true">已盘 {{ doneCount }}</text>
      </view>
      <view v-if="voiceHintText" class="voice-hint">
        <text>{{ voiceHintText }}</text>
      </view>

      <view class="search-row" v-if="editMode">
        <text class="edit-tip">点击序号或箭头排序</text>
      </view>

      <!-- ======== 物料列表（统一展示） ======== -->
      <template v-if="!editMode">
        <view class="material-list">
          <template v-if="showPending">
            <view v-for="m in filteredPending" :key="m.materialId" class="m-row" @click="handlePendingClick(m)">
              <view class="mr-icon" :class="{green:isMultiUnit(m), gray:!isMultiUnit(m)}">
                <text>{{ isMultiUnit(m)?'📦':'📝' }}</text>
              </view>
              <view class="mr-info">
                <text class="mr-name">{{ materialDisplayName(m) }}</text>
                <text class="mr-desc">{{ m.spec || '--' }}</text>
              </view>
              <view v-if="!isMultiUnit(m)" class="mr-input-wrap" @click.stop>
                <input class="mr-input" type="digit" placeholder="0" :value="m.inputQtyRaw||''" @blur="(e:any)=>quickSave(m,e.detail.value)" />
                <text class="mr-unit">{{ m.inventoryUnit||m.inventory_unit||m.unit||'' }}</text>
              </view>
              <view v-else class="mr-arrow-wrap" @click.stop="openDrawer(m); voicePreFillSnapshot()">
                <text class="mr-arrow-label">{{ m.inputQty ? m.inputQty+' '+(m.unit||baseUnit(m)) : '未录入' }}</text>
                <text class="mr-arrow">›</text>
              </view>
            </view>
          </template>
          <template v-if="showDone">
            <view v-for="m in filteredCompleted" :key="'c-'+m.materialId" class="m-row done" @click="handleCompletedClick(m)">
              <view class="mr-icon check">✓</view>
              <view class="mr-info">
                <text class="mr-name done-name">{{ materialDisplayName(m) }}</text>
                <text class="mr-desc done-desc">{{ breakdownText(m) }}</text>
              </view>
              <view class="mr-done">
                <text class="mr-val">{{ m.inputQty||'--' }} {{ m.unit || baseUnit(m) }}</text>
              </view>
            </view>
          </template>
        </view>
        <EmptyState v-if="(!showPending||!filteredPending.length) && (!showDone||!filteredCompleted.length)" text="暂无物料" />
      </template>

      <!-- 编辑模式列表 -->
      <template v-if="editMode">
        <view v-if="displayList.length" class="material-list">
          <view v-for="(m, idx) in displayList" :key="(m.materialId||m.id)+'-v'+listKey" class="m-row" :class="{done:m.inputStatus==='entered'}">
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
              <text class="mr-name">{{ materialDisplayName(m) }}</text>
              <text class="mr-desc">{{ m.spec || '--' }}</text>
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
      <view v-if="!editMode && extLoading && !locateMode" class="ext-hint">搜索物料库中...</view>
      <template v-if="!editMode && !locateMode && !extLoading && searchKey">
        <template v-if="extResults.length > 0">
          <view class="ext-not-found">未在本区域的物料</view>
          <view class="ext-list">
            <view v-for="m in extResults" :key="m.materialId" class="ext-row" :class="{added:m.inCurrentZone}">
              <view class="ext-info">
                <text class="ext-name">{{ materialDisplayName(m) }}</text>
                <text class="ext-meta">{{ m.spec||'--' }} · {{ '盘点单位：'+(m.inventoryUnit||m.inventory_unit||m.unit||'--') }}</text>
              </view>
              <text class="ext-add" v-if="m.inCurrentZone">已加入</text>
              <view class="ext-add" v-else @click="handleExtAdd(m)">{{ extAddings.has(m.materialId)?'添加中...':'添加' }}</view>
            </view>
          </view>
        </template>
      </template>
    </template>

    <view class="bottom-bar" v-if="!editMode">
      <view class="bb-finish" @click="goSummary">结束盘点</view>
      <view class="bb-voice" :class="{ recording }" @touchstart="startVoice" @touchmove="onVoiceMove" @touchend="stopVoice">
        <view class="bb-mic">
          <view class="bb-mic-body"></view>
          <view class="bb-mic-bracket"></view>
          <view class="bb-mic-stand"></view>
        </view>
        <text>{{ voiceLoading ? '准备中...' : voiceRecognizing ? '识别中...' : recording ? '松开停止' : '语音搜索' }}</text>
      </view>
    </view>
    <view class="bottom-bar" v-else>
      <view class="bb-back" @click="onCancelEdit">取消</view>
      <view class="bb-confirm" @click="onSaveEdit">{{ saving?'保存中...':'保存' }}</view>
    </view>

    <view v-if="drawerItem" class="mask" @click="closeDrawer" @touchmove.stop.prevent>
      <view class="drawer" @click.stop>
        <view class="dh"></view>
        <view class="drawer-head">
          <view>
            <text class="dht">{{ materialDisplayName(drawerItem) }}</text>
            <text class="dhs" v-if="drawerItem.spec">{{ drawerItem.spec }}</text>
          </view>
          <text class="dhx" @click="discardDrawer">✕</text>
        </view>
        <view v-if="drawerIsEdit" class="drawer-tabs">
          <view class="dtab" :class="{ active: drawerTab === 'add' }" @click="switchDrawerTab('add')">累加（补录）</view>
          <view class="dtab" :class="{ active: drawerTab === 'overwrite' }" @click="switchDrawerTab('overwrite')">覆盖（重盘）</view>
        </view>
        <view class="drawer-body">
          <view class="df-grid df-grid-edit">
            <view
              v-for="u in ruleUnits(drawerItem)"
              :key="u"
              class="df-item df-card"
              :class="{ active: drawerActiveUnit === u, 'df-card-weight': isWeightUnit(drawerItem, u) }"
              @click="setDrawerActiveUnit(u)"
            >
              <view class="dfc-top" :class="{ 'dfc-top-w': isWeightUnit(drawerItem, u) }">
                <view class="dfc-left">
                  <view v-if="isWeightUnit(drawerItem, u)" class="dfc-w-row">
                    <text class="dfc-label-w">称重</text>
                    <text v-if="conversionHint(drawerItem, u)" class="dfc-hint dfc-hint-w">{{ conversionHint(drawerItem, u) }}</text>
                  </view>
                  <view class="dfc-unit-row" :class="{ 'dfc-unit-row-w': isWeightUnit(drawerItem, u) }">
                    <text class="dfc-unit" :class="{ 'dfc-unit-w': isWeightUnit(drawerItem, u) }">{{ u }}</text>
                    <text v-if="!isWeightUnit(drawerItem, u) && conversionHint(drawerItem, u)" class="dfc-hint">{{ conversionHint(drawerItem, u) }}</text>
                    <text v-if="!isWeightUnit(drawerItem, u)" class="dfc-val" :class="{ empty: !drawerUnitInputs[u] }">{{ drawerUnitInputs[u] || '0' }}</text>
                  </view>
                  <text v-if="isWeightUnit(drawerItem, u)" class="dfc-val dfc-val-w" :class="{ empty: !drawerUnitInputs[u] }">{{ drawerUnitInputs[u] || '0' }}</text>
                </view>
              </view>
            </view>
          </view>
          <view v-if="drawerIsEdit" class="drawer-summary">
            <view class="ds-row">
              <template v-if="drawerTab === 'add'">
                <text class="ds-label">{{ drawerEntryTotal > 0 ? '已盘 + 本次录入' : '已盘' }}</text>
                <text class="ds-val" v-if="drawerEntryTotal > 0">{{ existingTotal }} + {{ drawerEntryTotal }} = {{ drawerFinalQty }} {{ baseUnit(drawerItem) }}</text>
                <text class="ds-val" v-else>{{ existingTotal }} {{ baseUnit(drawerItem) }}</text>
              </template>
              <template v-else>
                <text class="ds-label">总数</text>
                <text class="ds-val">{{ drawerEntryTotal }} {{ baseUnit(drawerItem) }}</text>
              </template>
            </view>
            <view v-if="drawerTab === 'add' && currAppendRecords.length > 0" class="ds-records">
              <text class="dsr-label">累加记录：</text>
              <scroll-view class="dsr-scroll" scroll-y :scroll-top="recordsScrollTop" :scroll-with-animation="true">
                <view v-for="(rec, i) in currAppendRecords" :key="'r'+i" class="dsr-item">
                  <text>{{ rec.input }} = {{ rec.total }} {{ rec.unit }}</text>
                </view>
              </scroll-view>
            </view>
          </view>
          <view v-else class="drawer-summary">
            <view class="ds-row">
              <text class="ds-label">汇总</text>
              <text class="ds-val">{{ drawerTotal }} {{ baseUnit(drawerItem) }}</text>
            </view>
          </view>
        </view>
        <DrawerNumericKeyboard
          :model-value="drawerUnitInputs[drawerActiveUnit] || ''"
          :confirm-text="drawerIsEdit ? '确认' : '确认录入'"
          :loading="saving"
          @update:model-value="onDrawerKeyboardInput"
          @confirm="confirmDrawer"
        />
      </view>
    </view>

    <view v-if="extDrawerItem" class="mask" @click="closeExtDrawer" @touchmove.stop.prevent>
      <view class="drawer" @click.stop>
        <view class="dh"></view>
        <view class="drawer-head">
          <view>
            <text class="dht">{{ materialDisplayName(extDrawerItem) }}</text>
            <text class="dhs" v-if="extDrawerItem.spec">{{ extDrawerItem.spec }}</text>
          </view>
          <text class="dhx" @click="closeExtDrawer">✕</text>
        </view>
        <view class="drawer-body">
          <view class="df-grid df-grid-edit">
            <view
              v-for="u in (extDrawerItem.units||[])"
              :key="u.unitName"
              class="df-item df-card"
              :class="{ active: extDrawerActiveUnit === u.unitName }"
              @click="setExtDrawerActiveUnit(u.unitName)"
            >
              <view class="dfc-top">
                <text class="dfc-unit">{{ u.unitName }}</text>
                <text v-if="extConversionHint(extDrawerItem, u.unitName)" class="dfc-hint">{{ extConversionHint(extDrawerItem, u.unitName) }}</text>
              </view>
              <text class="dfc-val" :class="{ empty: !extDrawerUnitInputs[u.unitName] }">{{ extDrawerUnitInputs[u.unitName] || '0' }}</text>
            </view>
          </view>
          <view v-if="(extDrawerItem.units||[]).length > 1" class="drawer-summary">
            <view class="ds-row">
              <text class="ds-label">汇总</text>
              <text class="ds-val">{{ extDrawerTotal }} {{ extDrawerItem.baseUnit }}</text>
            </view>
          </view>
        </view>
        <DrawerNumericKeyboard
          :model-value="extDrawerUnitInputs[extDrawerActiveUnit] || ''"
          confirm-text="确认"
          :loading="saving"
          @update:model-value="onExtKeyboardInput"
          @confirm="confirmExtDrawer"
        />
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
              <view class="sri-info"><text class="sri-name">{{ materialDisplayName(m) }}</text><text class="sri-meta">{{ m.spec||'--' }}  ·  {{ '盘点单位：'+(m.inventoryUnit||m.inventory_unit||m.unit||'--') }}</text></view>
              <text class="sri-btn" v-if="m.inCurrentZone">已在当前分区</text>
              <text class="sri-btn add" v-else>{{ addSearchAddings.has(m.materialId)?'...':'添加' }}</text>
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

    <view v-if="showFinishConfirm" class="mask" @click="showFinishConfirm=false">
      <view class="popup" @click.stop>
        <text class="popup-close" @click="showFinishConfirm=false">✕</text>
        <text class="popup-title">提示</text>
        <text class="popup-msg">以下物料未盘点，确认后默认写为 0：</text>
        <text class="popup-names">{{ finishConfirmNames }}</text>
        <view class="popup-btns">
          <view class="popup-confirm" @click="doFinishSave">{{ saving?'保存中...':'确认' }}</view>
          <view class="popup-cancel" @click="showFinishConfirm=false">取消</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$d:#E05A47;$ps2:#247847;$so:#FFF8EE;$w:#E58A2D;
.page{min-height:100vh;background:$bg;padding:24rpx 32rpx 200rpx}

// 搜索栏
.search-bar{display:flex;align-items:center;gap:16rpx;height:88rpx;padding:0 28rpx;border:2rpx solid #D6EBDD;border-radius:20rpx;background:#F7FCF8;box-shadow:0 4rpx 14rpx rgba(31,36,33,.06);margin-bottom:24rpx}
.sb-icon{font-size:36rpx;flex-shrink:0}
.sb-input{flex:1;height:100%;font-size:26rpx;color:$t1;background:transparent}
.sb-clear{font-size:22rpx;color:$t2;padding:8rpx 16rpx;border-radius:999rpx;background:#EEF1EF;flex-shrink:0}

// 提示条
.info-banner{display:flex;gap:20rpx;padding:24rpx;background:$so;border:2rpx solid $b;border-radius:16rpx;margin-bottom:24rpx;box-shadow:0 4rpx 16rpx rgba(31,36,33,.06)}
.ib-icon{font-size:36rpx;flex-shrink:0;color:$w}
.ib-title{display:block;font-size:30rpx;font-weight:700;color:$t1}
.ib-text{display:block;font-size:26rpx;color:$t2;line-height:1.6}

// 筛选标签
.filter-row{display:flex;gap:20rpx;overflow-x:auto;margin-bottom:20rpx}
.voice-hint{margin-top:16rpx;margin-bottom:12rpx;padding:16rpx 24rpx;background:#FFF8EE;border-radius:12rpx;border:2rpx solid #E58A2D;font-size:26rpx;color:#9A4F1F}
.filter-btn{padding:14rpx 28rpx;border-radius:999rpx;border:2rpx solid $b;background:$s;font-size:26rpx;color:$t2;white-space:nowrap;flex-shrink:0}
.filter-btn.active{background:$p;color:#fff;border-color:$p;font-weight:600}

.search-row{display:flex;gap:12rpx;margin-bottom:16rpx;align-items:center}
.edit-tip{flex:1;font-size:26rpx;color:#E58A2D;font-weight:500}

.section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:16rpx;padding:0 4rpx}
.sh-left{display:flex;align-items:center;gap:4rpx}
.sh-title{font-size:30rpx;font-weight:600;color:$t1}
.sh-actions{display:flex;align-items:center;gap:12rpx;flex-shrink:0}
.sh-edit{font-size:24rpx;font-weight:500;color:$p}
.sh-add{padding:12rpx 24rpx;border-radius:12rpx;background:$p;color:#fff;font-size:26rpx;font-weight:600;white-space:nowrap}
.sh-arrow{font-size:28rpx;color:$t3;transition:transform .25s;margin-right:8rpx;flex-shrink:0}.sh-arrow.folded{transform:rotate(-90deg)}
.done-head{margin-top:32rpx}

.material-list{display:flex;flex-direction:column;border-radius:20rpx;overflow:hidden;border:2rpx solid $b;background:$s;box-shadow:0 4rpx 16rpx rgba(31,36,33,.04);margin-bottom:8rpx}
.material-list.done-list{background:transparent;border-color:transparent;box-shadow:none}
.m-row{display:flex;align-items:center;gap:16rpx;padding:24rpx;background:$s;border-bottom:2rpx solid #EEF1EF;min-height:96rpx}
.m-row:last-child{border-bottom:0}.m-row.done{background:$s;border-bottom:2rpx solid #EEF1EF;min-height:68rpx;padding:20rpx 24rpx}
.m-check{flex-shrink:0}.check-box{width:40rpx;height:40rpx;border-radius:8rpx;border:2rpx solid $b;display:flex;align-items:center;justify-content:center;font-size:26rpx;color:#fff}.check-box.on{background:$p;border-color:$p}
.m-arrows{display:flex;flex-direction:column;gap:2rpx;flex-shrink:0}.arr-btn{width:40rpx;height:34rpx;border-radius:6rpx;background:#FAFBF9;border:1rpx solid $b;display:flex;align-items:center;justify-content:center;font-size:18rpx;color:$t2}.arr-btn.dis{opacity:.3}
.m-order{width:120rpx;flex-shrink:0;display:flex;align-items:center;justify-content:center}.order-num{width:96rpx;height:52rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:700;color:$t1;border:2rpx solid $b;border-radius:10rpx;background:#FAFBF9}.order-input{width:96rpx;height:56rpx;text-align:center;font-size:28rpx;font-weight:700;border:2rpx solid $b;border-radius:10rpx;color:$t1;background:#FAFBF9}
.mr-icon{width:72rpx;height:72rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:32rpx;flex-shrink:0}.mr-icon.green{background:$ps;color:$p}.mr-icon.gray{background:#FAFBF9;color:$t3}.mr-icon.check{background:$ps;color:$p;width:56rpx;height:56rpx;font-size:24rpx;font-weight:700}
.mr-info{flex:1;min-width:0}.mr-name{display:block;font-size:28rpx;font-weight:400;color:$t1}.mr-name.done-name{font-size:28rpx;font-weight:400}
.mr-desc{display:block;margin-top:4rpx;font-size:24rpx;color:$t3}.mr-desc.done-desc{font-size:22rpx}
.mr-done{text-align:right;flex-shrink:0}.mr-val{display:block;font-size:26rpx;font-weight:600;color:$t1}.mr-edit{display:block;margin-top:4rpx;font-size:22rpx;color:$p;padding:8rpx 0}
.mr-input-wrap{display:flex;align-items:center;gap:8rpx;flex-shrink:0}
.mr-input{width:120rpx;height:72rpx;text-align:center;font-size:30rpx;font-weight:500;border:2rpx solid $b;border-radius:12rpx;background:#FAFBF9}
.mr-unit{font-size:24rpx;color:$t2;white-space:nowrap}
.mr-arrow-wrap{display:flex;align-items:center;gap:6rpx;flex-shrink:0}
.mr-arrow-label{font-size:22rpx;color:$d}
.mr-arrow{font-size:36rpx;color:$t3}

.ext-hint{text-align:center;padding:40rpx 0;font-size:26rpx;color:$t2}
.ext-not-found{margin-top:40rpx;margin-bottom:16rpx;text-align:center;font-size:28rpx;font-weight:400;color:$t2}
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
.ext-info{flex:1}.ext-name{font-size:28rpx;font-weight:400;color:$t1}.ext-meta{display:block;margin-top:4rpx;font-size:24rpx;color:$t2}
.ext-row.added{opacity:.6}.ext-row.added .ext-add{background:$b;color:$t2}
.ext-add{padding:12rpx 24rpx;border-radius:999rpx;background:$p;color:#fff;font-size:24rpx;font-weight:600;white-space:nowrap}
.finish-names{display:block;margin-top:12rpx;padding:16rpx;background:#FAFBF9;border-radius:12rpx;font-size:24rpx;color:#66706A;line-height:1.6}
.popup{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:600rpx;background:#fff;border-radius:24rpx;padding:48rpx 40rpx 32rpx}
.popup-close{position:absolute;top:24rpx;right:24rpx;font-size:36rpx;color:#98A19C}
.popup-title{display:block;text-align:center;font-size:34rpx;font-weight:700;color:#1F2421;margin-bottom:20rpx}
.popup-msg{display:block;font-size:28rpx;color:#1F2421;line-height:1.5}
.popup-names{display:block;margin-top:12rpx;padding:16rpx;background:#FAFBF9;border-radius:12rpx;font-size:24rpx;color:#66706A;line-height:1.6}
.popup-btns{display:flex;gap:20rpx;margin-top:32rpx}
.popup-confirm{flex:1;height:88rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}
.popup-cancel{flex:1;height:88rpx;border-radius:16rpx;background:#FAFBF9;color:#66706A;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}

.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:grid;grid-template-columns:200rpx 1fr;gap:20rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:linear-gradient(to top,$s 60%,transparent)}
.bb-back{flex:1;height:88rpx;border-radius:24rpx;border:2rpx solid $b;background:$s;color:$t1;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:600}
.bb-confirm{flex:1;height:88rpx;border-radius:24rpx;background:$ps2;color:#fff;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:700}
.bb-finish{height:88rpx;border-radius:24rpx;border:2rpx solid $p;background:#fff;color:$p;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:700}
.bb-voice{height:88rpx;border-radius:24rpx;background:$ps2;color:#fff;display:flex;align-items:center;justify-content:center;gap:8rpx;font-size:26rpx;font-weight:600}.bb-voice.recording{background:$d}
.bb-mic{width:28rpx;height:38rpx;display:flex;flex-direction:column;align-items:center;flex-shrink:0;margin-top:4rpx}
.bb-mic-body{width:18rpx;height:22rpx;border:2rpx solid #fff;border-radius:11rpx;box-sizing:border-box}
.bb-mic-bracket{width:20rpx;height:4rpx;border:0 solid #fff;border-bottom-width:2rpx;border-left-width:2rpx;border-right-width:2rpx;border-radius:0 0 11rpx 11rpx;margin-top:-2rpx}
.bb-mic-stand{width:2rpx;height:5rpx;background:#fff;border-radius:1rpx;margin-top:0}
.bb-del{flex:1;height:88rpx;border-radius:999rpx;background:$d;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}.bb-del.disabled{opacity:.5}

.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.drawer{width:100%;max-height:92vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column;overflow:hidden}
.dh{width:80rpx;height:6rpx;border-radius:999rpx;background:$b;margin:16rpx auto;flex-shrink:0}
.drawer-head{display:flex;align-items:flex-start;justify-content:space-between;padding:8rpx 32rpx 10rpx;border-bottom:2rpx solid #EEF1EF;flex-shrink:0}.dht{display:block;font-size:34rpx;font-weight:700;color:$t1}.dhs{display:block;margin-top:4rpx;font-size:24rpx;color:$t2}.dhx{font-size:48rpx;font-weight:700;color:$t2;flex-shrink:0;margin-left:16rpx;padding:8rpx}
.drawer-body{padding:12rpx 32rpx 8rpx;display:flex;flex-direction:column;gap:12rpx;flex-shrink:0}
.drawer-tabs{display:flex;margin:0 32rpx 8rpx;padding:6rpx;background:#F3F5F4;border-radius:16rpx;gap:6rpx;flex-shrink:0}
.dtab{flex:1;height:72rpx;border-radius:12rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:600;color:$t2}
.dtab.active{background:$s;color:$p;box-shadow:0 2rpx 8rpx rgba(31,36,33,.06)}
.df-grid{display:grid;grid-template-columns:1fr 1fr;gap:32rpx}
.df-grid-edit{gap:24rpx}
.df-item{display:flex;align-items:center}
.df-item.active .dfi-wrap{border-color:$p;background:$ps}
.df-card{position:relative;flex-direction:column;align-items:flex-start;min-height:130rpx;padding:20rpx 24rpx;border:2rpx solid $b;border-radius:16rpx;background:#FAFBF9;box-sizing:border-box}
.df-card.active{border-color:$p;background:$ps}
// 称重单位卡片：跨两列，橙色背景
.df-card-weight{grid-column:1/-1;min-height:auto;padding:12rpx 24rpx;background:#FFF8EE!important;border-color:$b!important;box-shadow:0 4rpx 14rpx rgba(31,36,33,.06)}
.df-card-weight.active{border-color:$p!important;background:$ps!important;box-shadow:0 0 0 6rpx rgba(47,143,87,.12),0 4rpx 14rpx rgba(31,36,33,.06)}
.dfc-label-w{display:block;font-size:30rpx;font-weight:700;color:#9A4F1F}
.dfc-unit-w{cursor:pointer}
.dfc-hint-w{padding:4rpx 12rpx;border-radius:999rpx;background:#FFF;color:#9A4F1F!important;font-size:22rpx}
.dfc-top{display:flex;align-items:baseline;width:100%;gap:8rpx;flex-wrap:nowrap}
.dfc-top-w{flex-direction:row;justify-content:space-between}
.dfc-w-row{display:flex;align-items:center;justify-content:space-between;width:100%}
.ml-auto{margin-left:auto}
.dfc-left{display:flex;flex-direction:column;gap:4rpx;flex:1;min-width:0}
.dfc-unit-row{display:flex;align-items:baseline}
.dfc-unit-row-w{justify-content:space-between}
.dfc-unit{font-size:30rpx;font-weight:700;color:$t1;flex-shrink:0;white-space:nowrap}
.dfc-hint{font-size:22rpx;color:$t3;margin-left:auto;flex-shrink:0;white-space:nowrap}
.dfc-val{position:absolute;right:20rpx;bottom:16rpx;min-width:120rpx;text-align:right;font-size:32rpx;font-weight:700;color:$t1}
.dfc-val.empty{font-weight:400;color:$t3}
.dfc-val-w{position:static;text-align:right;margin-top:12rpx}
.drawer-summary{padding:20rpx 24rpx;background:#F1F8F3;border-radius:16rpx}
.ds-row{display:flex;align-items:center;justify-content:space-between}
.ds-label{font-size:28rpx;color:$t2}
.ds-val{font-size:36rpx;font-weight:800;color:$p}
.ds-formula{display:block;margin-top:12rpx;font-size:24rpx;color:$t3}
.ds-records{margin-top:16rpx}
.dsr-scroll{max-height:130rpx;overflow-y:scroll}
.dsr-label{font-size:20rpx;color:$t3;margin-bottom:8rpx;display:block}
.dsr-item{padding:12rpx 16rpx;margin-top:8rpx;background:#fff;border-radius:12rpx;font-size:24rpx;color:$t2}
.dsr-pending{opacity:.5;border:2rpx dashed $t3}
.dfi-wrap{position:relative;flex:1;width:100%;border:2rpx solid $b;border-radius:12rpx;background:#FAFBF9;box-sizing:border-box}
.dfi-val{display:block;width:100%;height:68rpx;padding:0 80rpx 0 24rpx;line-height:68rpx;font-size:40rpx;font-weight:700;text-align:right;color:$t1;box-sizing:border-box}
.dfi-val.empty{font-weight:400;color:$t3}
.dfi-unit{position:absolute;right:24rpx;top:50%;transform:translateY(-50%);font-size:24rpx;font-weight:400;color:$t2;pointer-events:none}

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

/* 录音浮层 */
.voice-overlay{position:fixed;inset:0;z-index:200;display:flex;flex-direction:column;align-items:center;justify-content:center;background:rgba(31,36,33,.85)}.voice-overlay.cancelling{background:rgba(224,90,71,.85)}.voice-overlay.cancelling .vw-bar{background:#E05A47}
.vw-mic{width:80rpx;height:110rpx;display:flex;flex-direction:column;align-items:center;margin-bottom:24rpx}.vw-mic-body{width:48rpx;height:60rpx;border-radius:32rpx;border:8rpx solid rgba(255,255,255,.9);box-sizing:border-box}.vw-mic-body::after{content:\"\";position:absolute;top:4rpx;left:-4rpx;right:-4rpx;bottom:4rpx;border-radius:24rpx;background:rgba(255,255,255,.9)}.vw-mic-bracket{width:56rpx;height:22rpx;border:8rpx solid rgba(255,255,255,.9);border-top:none;border-radius:0 0 28rpx 28rpx;margin-top:4rpx}.vw-mic-stand{width:8rpx;height:16rpx;background:rgba(255,255,255,.9);border-radius:4rpx}
.vw-cancel{width:120rpx;height:120rpx;border-radius:50%;border:4rpx solid rgba(255,255,255,.8);display:flex;align-items:center;justify-content:center;margin-bottom:24rpx}.vw-cancel-icon{font-size:56rpx;color:rgba(255,255,255,.8);font-weight:300}
.vw-mic{width:70rpx;height:96rpx;display:flex;flex-direction:column;align-items:center;margin-bottom:20rpx}
.vw-mic-body{width:44rpx;height:52rpx;border:6rpx solid rgba(255,255,255,.9);border-radius:26rpx;box-sizing:border-box}
.vw-mic-bracket{width:52rpx;height:8rpx;border:0 solid rgba(255,255,255,.9);border-bottom-width:6rpx;border-left-width:6rpx;border-right-width:6rpx;border-radius:0 0 26rpx 26rpx;margin-top:-3rpx}
.vw-mic-stand{width:6rpx;height:16rpx;background:rgba(255,255,255,.9);border-radius:3rpx;margin-top:0}
.vw-cancel{width:120rpx;height:120rpx;border-radius:50%;border:4rpx solid rgba(255,255,255,.8);display:flex;align-items:center;justify-content:center;margin-bottom:20rpx}.vw-cancel-icon{font-size:56rpx;color:rgba(255,255,255,.8);font-weight:300}
.vw-loading-spinner{width:60rpx;height:60rpx;border:4rpx solid rgba(255,255,255,.2);border-top-color:rgba(255,255,255,.9);border-radius:50%;animation:spin .8s linear infinite;margin-bottom:24rpx}@keyframes spin{to{transform:rotate(360deg)}}
.vw-text{font-size:28rpx;color:rgba(255,255,255,.7);margin-bottom:28rpx}
.vw-bars{display:flex;align-items:center;gap:6rpx;height:80rpx}
.vw-bar{width:6rpx;border-radius:3rpx;background:$p;animation:waveBar .6s ease-in-out infinite alternate}
@keyframes waveBar{0%{height:12rpx}100%{height:80rpx}}
.vw-hint{position:absolute;bottom:80rpx;font-size:24rpx;color:rgba(255,255,255,.35)}
</style>