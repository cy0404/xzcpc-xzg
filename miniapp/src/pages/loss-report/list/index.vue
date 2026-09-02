<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { getLossList, createLoss, getContainers, searchMaterials, uploadLossVideo, uploadLossImage, appendLossVoucher, closeLoss, approveLoss, rejectApproval, receiveLoss, notReceiveLoss, batchApproveLoss } from '@/api/loss-report'
import { BASE_URL, H5_BASE } from '@/utils/constants'

const userStore = useUserStore()
const loading = ref(false)
const records = ref<any[]>([])
const showSheet = ref(false)
const lossType = ref<'daily' | 'arrival'>('daily')
const lossObject = ref<'finished' | 'semi_finished'>('semi_finished')
const selectedMaterial = ref<any>(null)
const materialSearchKey = ref('')
const materialResults = ref<any[]>([])
const materialSearching = ref(false)
const searchExpanded = ref(false)
const lossQty = ref('')
const inputUnit = ref('')
const unitPrice = ref('')
const lossAmount = ref('')
const qimaiOrderNo = ref('')
const grossWeight = ref('')
const containers = ref<any[]>([])
const containerId = ref<number | null>(null)
const reason = ref('过期')
const remark = ref('')
const isUrgent = ref(false)

// 监听数量和单价变化，自动计算金额
watch([lossQty, unitPrice, grossWeight, containerId, lossObject], () => {
  const price = parseFloat(unitPrice.value)
  if (!(price > 0)) { lossAmount.value = ''; return }
  const qty = lossObject.value === 'semi_finished' ? netWeight.value : parseFloat(lossQty.value)
  lossAmount.value = qty > 0 ? (qty * price).toFixed(2) : '0.00'
}, { immediate: true })
const submitting = ref(false)
let searchTimer: any = null

const dailyReasons = ['过期', '破损', '制作损耗', '其他']
const arrivalReasons = ['运输破损-外包装', '运输破损-内包装', '少货', '错发', '临期或变质', '其他']
const lossReasons = computed(() => lossType.value === 'arrival' ? arrivalReasons : dailyReasons)

const displayUnit = computed(() => {
  if (!selectedMaterial.value) return ''
  return lossType.value === 'arrival'
    ? (inputUnit.value || selectedMaterial.value.stockUnit || selectedMaterial.value.baseUnit || '')
    : (selectedMaterial.value.baseUnit || selectedMaterial.value.stockUnit || '')
})
const arrivalUnitHint = computed(() => {
  if (!selectedMaterial.value || lossType.value !== 'arrival') return ''
  const infos = selectedMaterial.value.unitInfos || []
  const info = infos.find((i: any) => i.unit === inputUnit.value)
  if (!info || !info.hint) return ''
  if (inputUnit.value === selectedMaterial.value.baseUnit) return ''
  return info.hint
})
const isAvocado = computed(() => selectedMaterial.value?.materialName?.includes('牛油果泥'))
const filteredUnits = computed(() => {
  const units = selectedMaterial.value?.units || []
  if (!isAvocado.value || lossType.value !== 'arrival') return units
  return units.filter((u: string) => u !== 'g' && u !== 'kg')
})

const todayStr = `${new Date().getFullYear()}-${String(new Date().getMonth()+1).padStart(2,'0')}-${String(new Date().getDate()).padStart(2,'0')}`
const todayCount = computed(() => records.value.filter(r => r.createdAt && r.createdAt.startsWith(todayStr)).length)
const pendingCount = computed(() => records.value.filter(r => r.status === 'pending').length)
const resolvedCount = computed(() => records.value.filter(r => r.status === 'confirmed_resend').length)

onShow(() => { if (userStore.token && userStore.bound) fetchList() })

onPullDownRefresh(async () => {
  await fetchList(false)  // 下拉刷新：保留列表显示，不切骨架
  uni.stopPullDownRefresh()
})

async function fetchList(showSkeleton = true) {
  if (showSkeleton) loading.value = true
  try { const res: any = await getLossList({ pageSize: 500 }); records.value = res.records || [] }
  finally { loading.value = false }
}

function openArrival() {
  const h5url = H5_BASE + '/upload/h5/loss-arrival.html?v=3&token=' + encodeURIComponent(uni.getStorageSync('token') || '') + '&storeName=' + encodeURIComponent(userStore.storeName || '')
  uni.navigateTo({ url: '/pages/loss-report/camera-h5/index?url=' + encodeURIComponent(h5url) })
}

function resubmitArrival(r: any) {
  const h5url = H5_BASE + '/upload/h5/loss-arrival.html?v=3&token=' + encodeURIComponent(uni.getStorageSync('token') || '') + '&storeName=' + encodeURIComponent(userStore.storeName || '') + '&resubmit=' + r.id
  uni.navigateTo({ url: '/pages/loss-report/camera-h5/index?url=' + encodeURIComponent(h5url) })
}

async function doClose(r: any) {
  if (actionLoadingId.value) return
  actionLoadingId.value = r.id
  try { await closeLoss(r.id); uni.showToast({ title: '已关闭', icon: 'success' }); fetchList() }
  catch { fetchList() }
  finally { actionLoadingId.value = 0 }
}

function openSheet(type: 'daily' | 'arrival') {
  lossType.value = type; lossObject.value = type === 'arrival' ? 'finished' : 'semi_finished'
  selectedMaterial.value = null; materialSearchKey.value = ''; materialResults.value = []
  lossQty.value = ''; unitPrice.value = ''; grossWeight.value = type === 'daily' ? '0' : ''; inputUnit.value = ''; qimaiOrderNo.value = ''; containerId.value = null
  isUrgent.value = false; searchExpanded.value = false
  reason.value = type === 'arrival' ? '运输破损' : '过期'; remark.value = ''
  mediaList.value = []; showSheet.value = true
  loadDefaultMaterials()
  loadContainers().then(() => {
    if (type === 'daily') {
      const def = containers.value.find((c: any) => c.alias === '去皮' || c.name === '去皮' || c.tareWeight === 0)
      if (def) containerId.value = def.id
    }
  })
}

async function loadContainers() {
  try { containers.value = (await getContainers()) || [] } catch { containers.value = [] }
}

// 物料搜索
function onMaterialSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = materialSearchKey.value.trim()
  // 首次打开 / 清空关键词：加载白名单物料（loss_visible=1）
  if (!kw) { loadDefaultMaterials(); return }
  searchTimer = setTimeout(async () => {
    materialSearching.value = true
    try { const res: any = await searchMaterials(kw, lossObject.value); materialResults.value = res.list || [] }
    catch { materialResults.value = [] }
    finally { materialSearching.value = false }
  }, 300)
}

async function loadDefaultMaterials() {
  materialSearching.value = true
  try { const res: any = await searchMaterials('', lossObject.value); materialResults.value = res.list || [] }
  catch { materialResults.value = [] }
  finally { materialSearching.value = false }
}

function resolveUnitPrice(m: any, unit: string): string {
  if (!m || !m.unitPrice) return ''
  const baseUnit = m.baseUnit || ''
  if (!unit || unit === baseUnit) return String(m.unitPrice)
  // 从 unitInfos 中找换算因子，如 "1箱=24个" → factor=24
  const infos = m.unitInfos || []
  const info = infos.find((i: any) => i.unit === unit)
  if (info && info.hint) {
    const m2 = info.hint.match(/=(\d+\.?\d*)/)
    if (m2) return (Number(m.unitPrice) * parseFloat(m2[1])).toFixed(2)
  }
  return String(m.unitPrice)
}

function selectMaterial(m: any) {
  selectedMaterial.value = m
  materialSearchKey.value = m.materialName
  materialResults.value = []
  if (lossType.value === 'arrival') {
    inputUnit.value = m.stockUnit || m.baseUnit || ''
  }
  unitPrice.value = resolveUnitPrice(m, inputUnit.value)
}
function selectArrivalUnit(unit: string) {
  inputUnit.value = unit
  unitPrice.value = resolveUnitPrice(selectedMaterial.value, unit)
}

function clearMaterial() { selectedMaterial.value = null; materialSearchKey.value = '' }

// 容器
const activeContainer = computed(() => containers.value.find((c: any) => c.id === containerId.value))
function selectContainer(id: number) { containerId.value = containerId.value === id ? null : id }

const netWeight = computed(() => {
  if (lossObject.value !== 'semi_finished') return 0
  const gross = parseFloat(grossWeight.value) || 0
  const tare = activeContainer.value?.tareWeight || 0
  return Math.round((gross - tare) * 10) / 10
})

// 媒体上传（图片+视频混合）
interface MediaItem { url: string; isVideo: boolean; uploading: boolean; failed: boolean; size: number; progress: number; thumb?: string }
const mediaList = ref<MediaItem[]>([])

function startVideoUpload(item: MediaItem, idx: number) {
  item.uploading = true; item.failed = false; item.progress = 0
  uploadLossVideo(item.url, item.size, (progress: number) => {
    if (mediaList.value[idx]) mediaList.value[idx].progress = progress
  }, userStore.storeName).then((res: any) => {
    const url = typeof res === 'string' ? res : res.url
    const thumb = typeof res === 'string' ? '' : (res.thumb || '')
    mediaList.value.splice(idx, 1, { url, thumb, isVideo: true, uploading: false, failed: false, size: item.size, progress: 100 })
  }).catch(() => {
    mediaList.value.splice(idx, 1, { ...item, uploading: false, failed: true })
  })
}

function startImageUpload(item: MediaItem, idx: number) {
  item.uploading = true; item.failed = false
  uploadLossImage(item.url, userStore.storeName).then(url => {
    mediaList.value.splice(idx, 1, { url, isVideo: false, uploading: false, failed: false, size: 0, progress: 100 })
  }).catch(() => {
    mediaList.value.splice(idx, 1, { ...item, uploading: false, failed: true })
  })
}

function chooseVideo() {
  if (mediaList.value.length >= 4) { uni.showToast({ title: '最多4个文件', icon: 'none' }); return }
  uni.chooseMedia({
    count: 1, mediaType: ['video'], sourceType: ['album'],
    success: (res: any) => {
      const f = res.tempFiles?.[0]
      if (!f) return
      checkAndAddVideo(f.tempFilePath, f.size)
    },
    fail: (err: any) => { uni.showToast({ title: err?.errMsg || '选择取消', icon: 'none' }) },
  })
}

function checkAndAddVideo(filePath: string, size: number) {
  if (size > 200 * 1024 * 1024) {
    uni.showToast({ title: '视频超过200M，请重新选择', icon: 'none', duration: 3000 }); return
  }
  if (size > 0) {
    addVideoItem(filePath, size)
  } else {
    addVideoItem(filePath, 0)
  }
}

function addVideoItem(filePath: string, size: number) {
  const item: MediaItem = { url: filePath, isVideo: true, uploading: true, failed: false, size, progress: 0 }
  mediaList.value.push(item)
  startVideoUpload(item, mediaList.value.length - 1)
}

function openCamera() {
  if (mediaList.value.length >= 4) { uni.showToast({ title: '最多4个文件', icon: 'none' }); return }
  uni.showActionSheet({
    itemList: ['拍摄', '从相册选择'],
    success: (res) => {
      if (res.tapIndex === 0) {
        const baseUrl = (BASE_URL.startsWith('https://') ? BASE_URL : 'https://www.xzcpc-9pd.top/storeInventory/api/mp').replace(/\/api\/mp\/?$/, '')
        const token = encodeURIComponent(uni.getStorageSync('token') || '')
        const h5url = baseUrl + '/upload/h5/camera.html?t=' + Date.now() + '&storeName=' + encodeURIComponent(userStore.storeName || '') + '&token=' + token
        uni.navigateTo({ url: '/pages/loss-report/camera-h5/index?url=' + encodeURIComponent(h5url) })
      } else {
        chooseVideo()
      }
    },
  })
}

function onCameraRecorded(filePath: string, size: number, thumb?: string) {
  // H5 相机已上传完毕，直接使用返回的 URL
  const item: MediaItem = { url: filePath, isVideo: true, uploading: false, failed: false, size, progress: 100, thumb: thumb || '' }
  mediaList.value.push(item)
}

function chooseImage() {
  if (mediaList.value.length >= 4) { uni.showToast({ title: '最多4个文件', icon: 'none' }); return }
  uni.chooseImage({ count: 4 - mediaList.value.length, sizeType: ['compressed'], success: (res) => {
    for (const p of res.tempFilePaths) {
      if (mediaList.value.length >= 4) break
      const item: MediaItem = { url: p, isVideo: false, uploading: true, failed: false, size: 0, progress: 0 }
      mediaList.value.push(item)
      startImageUpload(item, mediaList.value.length - 1)
    }
  }})
}

function removeMedia(idx: number) { mediaList.value.splice(idx, 1) }
const uploadedUrls = computed(() => mediaList.value.filter(i => !i.uploading && !i.failed).map(i => i.url))
const videoCount = computed(() => mediaList.value.filter(i => i.isVideo && !i.uploading && !i.failed).length)

// 上传提示（根据报损原因）
const uploadHint = computed(() => {
  if (lossType.value !== 'arrival') return ''
  const needsVideo = ['运输破损-外包装', '运输破损-内包装', '临期或变质']
  if (needsVideo.includes(reason.value)) return '请至少拍摄1段视频作为凭证'
  return '请上传图片或视频作为凭证'
})

// 视频/图片预览
const previewMediaUrl = ref('')
const previewMediaType = ref<'image'|'video'>('image')
function previewMedia(url: string, isVideo?: boolean) {
  if (isVideo) {
    uni.previewMedia({ sources: [{ url, type: 'video' }] })
  } else if (isVideo === false) {
    previewMediaUrl.value = url; previewMediaType.value = 'image'
  }
}
function closePreviewMedia() { previewMediaUrl.value = '' }

// 提交
async function submitLoss() {
  if (submitting.value) return
  if (!selectedMaterial.value) { uni.showToast({ title: '请选择物料', icon: 'none' }); return }
  if (lossObject.value === 'semi_finished') {
    if (!grossWeight.value || parseFloat(grossWeight.value) <= 0) { uni.showToast({ title: '请填写含容器重量', icon: 'none' }); return }
    if (!containerId.value) { uni.showToast({ title: '请选择容器', icon: 'none' }); return }
    if (netWeight.value <= 0) { uni.showToast({ title: '净重必须大于0', icon: 'none' }); return }
  }
  if (lossType.value === 'arrival' && lossObject.value !== 'semi_finished' && (!lossQty.value || parseFloat(lossQty.value) <= 0)) { uni.showToast({ title: '请填写报损数量', icon: 'none' }); return }
  if (lossType.value === 'arrival' && !qimaiOrderNo.value.trim()) { uni.showToast({ title: '请填写企迈单号', icon: 'none' }); return }
  if (!reason.value) { uni.showToast({ title: '请选择报损原因', icon: 'none' }); return }
  if (lossType.value === 'arrival' && reason.value === '错发' && !remark.value.trim()) { uni.showToast({ title: '请填写备注说明错发情况', icon: 'none' }); return }
  if (mediaList.value.length === 0) { uni.showToast({ title: '请上传现场凭证', icon: 'none' }); return }
  if (mediaList.value.some(m => m.failed)) { uni.showToast({ title: '有文件上传失败，请重试或删除', icon: 'none' }); return }
  // 到货验收：外包装/内包装/临期或变质必须至少有1个视频
  if (lossType.value === 'arrival') {
    const needsVideo = ['运输破损-外包装', '运输破损-内包装', '临期或变质']
    if (needsVideo.includes(reason.value) && videoCount.value === 0) {
      uni.showToast({ title: '该原因需至少拍摄1段视频作为凭证', icon: 'none' }); return
    }
  }

  submitting.value = true
  try {
    // 提单时：已上传完的URL直接用，还在上传的提单后继续传+追加
    const m = selectedMaterial.value
    const result: any = await createLoss({
      lossType: lossType.value, lossObject: lossObject.value,
      materialId: m?.materialId || null,
      materialName: m?.materialName || materialSearchKey.value.trim(),
      spec: m?.spec || '',
      inputUnit: lossObject.value === 'semi_finished' ? (displayUnit.value || 'g') : inputUnit.value,
      inputQty: lossObject.value === 'semi_finished' ? netWeight.value : (lossQty.value ? parseFloat(lossQty.value) : null),
      unitPrice: unitPrice.value ? parseFloat(unitPrice.value) : null,
      totalAmount: lossAmount.value ? parseFloat(lossAmount.value) : null,
      grossWeight: grossWeight.value ? parseFloat(grossWeight.value) : null,
      containerId: containerId.value,
      qimaiOrderNo: qimaiOrderNo.value.trim(),
      reason: reason.value, remark: remark.value,
      voucherUrl: uploadedUrls.value.join(','),
      urgent: lossType.value === 'arrival' && isUrgent.value ? 1 : 0,
    })
    const reportId = result?.id || result?.data?.id
    uni.showToast({ title: '提交成功', icon: 'success' })
    showSheet.value = false; fetchList()

    // 提单后：剩余未上传完的视频继续后台传，传完自动追加到工单
    const pendingVideos = mediaList.value.filter(m => m.uploading)
    if (reportId && pendingVideos.length > 0) {
      uni.showToast({ title: `${pendingVideos.length}个视频后台上传中，请稍等片刻再退出`, icon: 'none', duration: 3000 })
      pendingVideos.forEach(v => {
        uploadLossVideo(v.url, v.size).then((res: any) => {
          var videoUrl = typeof res === 'string' ? res : (res.url || res)
          appendLossVoucher(reportId, videoUrl).catch(() => {})
        }).catch(() => {})
      })
    }
  } catch { uni.showToast({ title: '提交失败', icon: 'none' }) }
  finally { submitting.value = false }
}

function goDetail(r: any) { uni.navigateTo({ url: `/pages/loss-report/detail/index?id=${r.id}` }) }
function goDailyLoss() { uni.navigateTo({ url: '/pages/loss-report/form-daily/index' }) }
defineExpose({ onCameraRecorded })

function goStandard(materialId: string) { uni.navigateTo({ url: `/pages/loss-report/standard/index?materialId=${materialId}` }) }

const canManage = computed(() => userStore.role === 'store_manager' || userStore.role === 'owner' || userStore.role === '店长' || userStore.role === '老板')
const activeTab = ref('all')
const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pending_approval', label: '待审核' },
  { key: 'confirmed_resend', label: '待收货' },
  { key: 'pending', label: '待确认' },
  { key: 'registered', label: '已登记' },
  { key: 'rejected', label: '已拒绝' },
]
const tabRecords = computed(() => {
  if (activeTab.value === 'all') return records.value
  return records.value.filter(r => r.status === activeTab.value)
})
const actionLoadingId = ref(0)

// ============ 批量审批（店长/老板） ============
const batchMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const batchSubmitting = ref(false)
// 批量模式只允许勾选待审核记录（全部/待审核 tab 通用）
const selectableRecords = computed(() => tabRecords.value.filter(r => r.status === 'pending_approval'))
const allSelected = computed(() => selectableRecords.value.length > 0 && selectableRecords.value.every(r => selectedIds.value.has(r.id)))
// 批量入口：当前列表有待审核记录时显示
const showBatchEntry = computed(() => tabRecords.value.some(r => r.status === 'pending_approval'))

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  selectedIds.value.clear()
}
// 切 tab 时退出批量模式，避免误批当前不可见的记录
function switchTab(key: string) {
  if (activeTab.value === key) return
  activeTab.value = key
  if (batchMode.value) toggleBatchMode()
}
function toggleSelect(r: any) {
  if (r.status !== 'pending_approval') return
  if (selectedIds.value.has(r.id)) selectedIds.value.delete(r.id)
  else selectedIds.value.add(r.id)
}
function toggleSelectAll() {
  if (allSelected.value) selectedIds.value.clear()
  else selectableRecords.value.forEach(r => selectedIds.value.add(r.id))
}
function doBatch(action: 'approve' | 'reject') {
  if (batchSubmitting.value || selectedIds.value.size === 0) return
  const ids = [...selectedIds.value]
  const label = action === 'approve' ? '通过' : '拒绝'
  uni.showModal({
    title: `批量${label}`,
    content: `确定批量${label}选中的 ${ids.length} 条报损记录吗？`,
    confirmText: `批量${label}`,
    success: async (res) => {
      if (!res.confirm) return
      batchSubmitting.value = true
      try {
        const r: any = await batchApproveLoss(ids, action)
        // success 字段缺失时按 0 计（不猜测全成功），跳过数缺失按 0
        const ok = r?.success ?? 0
        const skip = r?.skipped ?? 0
        uni.showToast({ title: `已${label} ${ok} 条${skip ? `，跳过 ${skip} 条` : ''}`, icon: 'none', duration: 2500 })
        // 乐观更新：本地立即将选中记录置为终态，操作按钮马上消失（fetchList 结果回来再校准）
        const idSet = new Set(ids)
        records.value = records.value.map(lr => idSet.has(lr.id)
          ? { ...lr, status: action === 'approve' ? (lr.lossType === 'arrival' ? 'pending' : 'completed') : 'rejected' }
          : lr)
        batchMode.value = false; selectedIds.value.clear()
        fetchList()
      } catch { uni.showToast({ title: '批量操作失败', icon: 'none' }) }
      finally { batchSubmitting.value = false }
    },
  })
}

async function doApprove(r: any) {
  if (actionLoadingId.value) return
  actionLoadingId.value = r.id
  try { await approveLoss(r.id); uni.showToast({ title: '已通过', icon: 'success' }); r.status = r.lossType === 'arrival' ? 'pending' : 'completed'; fetchList() }
  catch { fetchList() }  // 并发冲突时静默刷新
  finally { actionLoadingId.value = 0 }
}
async function doReject(r: any) {
  if (actionLoadingId.value) return
  actionLoadingId.value = r.id
  try { await rejectApproval(r.id); uni.showToast({ title: '已拒绝', icon: 'success' }); r.status = 'rejected'; fetchList() }
  catch { fetchList() }
  finally { actionLoadingId.value = 0 }
}
async function doReceive(r: any) {
  if (actionLoadingId.value) return
  actionLoadingId.value = r.id
  try { await receiveLoss(r.id); uni.showToast({ title: '已确认收货', icon: 'success' }); fetchList() }
  catch { uni.showToast({ title: '操作失败', icon: 'none' }) }
  finally { actionLoadingId.value = 0 }
}
async function doNotReceive(r: any) {
  if (actionLoadingId.value) return
  actionLoadingId.value = r.id
  try { await notReceiveLoss(r.id); uni.showToast({ title: '已标记未收到', icon: 'success' }); fetchList() }
  catch { uni.showToast({ title: '操作失败', icon: 'none' }) }
  finally { actionLoadingId.value = 0 }
}

function statusLabel(s: string, r?: any) {
  if (s === 'confirmed_resend' && r?.isFruitVeg) return '已发券'
  const m: Record<string, string> = { pending_approval: '待审批', pending: '待确认', registered: '已登记', confirmed_resend: '已确认补发', rejected: '已拒绝', completed: '已录入', closed: '已关闭', received: '已收货', not_received: '未收到货' }
  return m[s] || s
}
function statusClass(s: string) {
  if (s === 'registered') return 's-blue'; if (s === 'confirmed_resend' || s === 'completed' || s === 'closed' || s === 'received') return 's-ok'; if (s === 'rejected') return 's-red'; if (s === 'not_received') return 's-gray'; if (s === 'pending_approval') return 's-warn'; return 's-warn'
}
</script>

<template>
  <view class="page" :class="{ dimmed: showSheet }">
    <!-- 统计面板 -->
    <view class="overview-card">
      <view class="ov-top">
        <view>
          <text class="ov-store">{{ userStore.storeName }}</text>
          <text class="ov-desc">日常报损仅记录；到货验收报损需厂家确认。</text>
        </view>
        <text class="ov-icon">📋</text>
      </view>
      <view class="ov-metrics">
        <view class="ov-m"><text class="oml">今日记录</text><text class="omv">{{ todayCount }}</text></view>
        <view class="ov-m"><text class="oml">待审核</text><text class="omv warn">{{ records.filter(r=>r.status==='pending_approval').length }}</text></view>
        <view class="ov-m"><text class="oml">待收货</text><text class="omv ok">{{ resolvedCount }}</text></view>
      </view>
      <!-- 批量入口：进入/退出批量模式 -->
      <view v-if="canManage && (batchMode || showBatchEntry)" class="ov-batch" :class="{ 'batch-on': batchMode }" @click="toggleBatchMode()">
        <text class="ovb-txt">{{ batchMode ? '已选 ' + selectedIds.size + ' 条' : '批量处理待审核报损' }}</text>
        <text class="ovb-btn">{{ batchMode ? '取消' : '去处理 ›' }}</text>
      </view>
    </view>

    <!-- Tab 栏 -->
    <view class="tab-bar">
      <scroll-view scroll-x enhanced show-scrollbar="false" class="tab-scroll">
        <view class="tab-row">
          <view v-for="t in tabs" :key="t.key" class="tab-item" :class="{ on: activeTab === t.key }" @click="switchTab(t.key)">
            {{ t.label }}
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 报损记录 -->
    <view class="section">
      <!-- 加载中：骨架（不渲染旧数据，避免返回列表页时闪现旧状态按钮） -->
      <view v-if="loading" class="list-loading">
        <view v-for="i in 4" :key="i" class="lk-card">
          <view class="lk-line" style="width:30%"></view>
          <view class="lk-line" style="width:70%"></view>
          <view class="lk-line" style="width:50%"></view>
        </view>
      </view>
      <view v-else-if="tabRecords.length" class="record-list">
        <view v-for="r in tabRecords" :key="r.id" class="rec-card" :class="{ 'batch-on': batchMode }" @click="batchMode ? toggleSelect(r) : goDetail(r)">
          <view v-if="batchMode" class="rec-check" :class="{ on: selectedIds.has(r.id), disabled: r.status !== 'pending_approval' }" @click.stop="toggleSelect(r)">✓</view>
          <view class="rc-main">
          <view class="rc-top">
            <text class="rc-type" :class="r.lossType === 'arrival' ? 'orange' : ''">{{ r.lossType === 'arrival' ? '到货验收' : '日常报损' }}</text>
            <text class="rc-status" :class="statusClass(r.status)">{{ statusLabel(r.status, r) }}</text>
          </view>
          <text class="rc-name">{{ r.itemNames || r.materialName }}</text>
          <text class="rc-meta">
            <template v-if="r.itemCount && r.itemCount > 0">{{ r.itemCount }} 种物料</template>
            <template v-else>{{ r.lossObject === 'semi_finished' ? (r.netWeight ? `净重 ${r.netWeight}g` : '--') : (r.inputQty ? `${r.inputQty} ${r.inputUnit || ''}` : '--') }}</template>
            <text v-if="r.totalAmount"> · ¥{{ r.totalAmount }}</text>
            · {{ r.handlerName || '' }}
            <text v-if="r.createdAt"> · {{ r.createdAt?.substring(0,16) }}</text>
          </text>
          <text v-if="r.rejectReason && !r.latestLogAction" class="rc-reject">拒绝原因：{{ r.rejectReason }}</text>
          <text v-if="r.lossType === 'arrival' && r.latestLogAction && r.latestLogAction !== '提交报损'" :class="r.latestLogAction.includes('拒绝') ? 'rc-reject' : 'rc-progress'">{{ r.latestLogAction }}<text v-if="r.latestLogRemark">：{{ r.latestLogRemark }}</text></text>
          <!-- 待审批：仅店长/老板可见 -->
          <view v-if="r.status === 'pending_approval' && canManage && !batchMode" class="rc-actions">
            <view class="rca-btn rca-no" @click.stop="doReject(r)">拒绝</view>
            <view class="rca-btn rca-ok" @click.stop="doApprove(r)">通过</view>
          </view>
          <!-- 已确认补发/已发券：所有人可见 -->
          <view v-else-if="r.status === 'confirmed_resend' && !batchMode" class="rc-actions">
            <view class="rca-btn rca-no" @click.stop="doNotReceive(r)">{{ r.isFruitVeg ? '未收到' : '未收到货' }}</view>
            <view class="rca-btn rca-ok" @click.stop="doReceive(r)">{{ r.isFruitVeg ? '已收到' : '已收货' }}</view>
          </view>
          <!-- 厂家拒绝：重新提交 + 关闭 -->
          <view v-else-if="r.status === 'rejected' && r.lossType === 'arrival' && !batchMode" class="rc-actions">
            <view class="rca-btn rca-no" @click.stop="doClose(r)">关闭</view>
            <view class="rca-btn rca-ok" @click.stop="resubmitArrival(r)">重新提交</view>
          </view>
          <!-- 其他状态：原有箭头 -->
          <view v-else-if="!batchMode" class="rc-arrow">查看报损 ›</view>
          </view>
        </view>
      </view>
      <view v-else class="empty">{{ activeTab === 'all' ? '暂无报损记录' : '暂无' + tabs.find(t=>t.key===activeTab)?.label + '的报损' }}</view>
    </view>
  </view>

  <!-- 底部固定快捷入口 -->
  <view v-if="!batchMode" class="bottom-bar">
    <view class="b-btn b-btn-arrival" @click="openArrival()">到货验收报损</view>
    <view class="b-btn b-btn-daily" @click="goDailyLoss()">日常报损</view>
  </view>

  <!-- 批量操作条 -->
  <view v-else class="batch-bar">
    <view class="bb-left" @click="toggleSelectAll">
      <view class="rec-check bb-check" :class="{ on: allSelected }">✓</view>
      <text class="bb-select-txt">{{ allSelected ? '取消全选' : '全选' }}</text>
    </view>
    <view class="bb-right">
      <view class="bb-btn bb-reject" @click="doBatch('reject')">批量拒绝</view>
      <view class="bb-btn bb-ok" @click="doBatch('approve')">批量通过 ({{ selectedIds.size }})</view>
    </view>
  </view>

  <!-- 新建报损弹窗 -->
  <view v-if="showSheet" class="sheet-mask" @click="showSheet = false">
    <view class="sheet" @click.stop>
      <view class="sh"></view>
      <text class="sheet-title">{{ lossType === 'daily' ? '日常报损' : '到货验收报损' }}</text>
      <text v-if="lossType === 'daily'" class="sheet-sub">过期、破损、制作损耗</text>
      <view v-if="lossType === 'arrival'" class="sheet-warn">
        <text class="sheet-warn-icon">⚠️</text>
        <text class="sheet-warn-text">仅限企迈平台发的货支持到货验收报损，收货后请立即报损</text>
      </view>

      <view class="sheet-body">
        <!-- 物品名称：物料搜索 -->
        <view class="fi"><text class="fil">物品名称</text>
          <view v-if="!selectedMaterial && !searchExpanded" class="search-tap" @click="searchExpanded = true">点击搜索物料名称 ›</view>
          <view v-else-if="!selectedMaterial">
            <input class="fii" v-model="materialSearchKey" placeholder="搜索物料名称" @input="onMaterialSearchInput" focus />
            <view v-if="materialResults.length" class="mat-results">
              <view v-for="m in materialResults" :key="m.materialId" class="mat-item" @click="selectMaterial(m)">
                <view class="mat-info"><text class="mat-name">{{ m.materialName }}</text><text class="mat-meta">{{ m.spec || '--' }} · {{ m.category || '--' }}</text></view>
              </view>
            </view>
            <view v-else-if="materialSearching" class="mat-searching">搜索中...</view>
            <view v-else-if="materialSearchKey.trim() && !materialSearching && materialResults.length === 0" class="mat-empty">暂无物料</view>
          </view>
          <view v-else class="mat-row">
            <view class="selected-mat">
              <text class="sm-name">{{ selectedMaterial.materialName }}</text><text class="sm-spec" v-if="selectedMaterial.spec"> · {{ selectedMaterial.spec }}</text>
              <text class="sm-clear" @click="clearMaterial">✕</text>
            </view>
            <text class="standard-btn" v-if="selectedMaterial.materialId && lossType !== 'daily'" @click="goStandard(selectedMaterial.materialId)">验收标准</text>
          </view>
        </view>

        <!-- 半成品去皮 -->
        <template v-if="lossObject === 'semi_finished'">
          <view class="fi"><text class="fil">含容器重量</text>
            <view class="gross-row">
              <view class="unit-input gross-input">
                <input class="fii" v-model="grossWeight" type="digit" placeholder="0" />
                <text class="unit-suffix" v-if="displayUnit">{{ displayUnit }}</text>
                <text class="unit-suffix" v-else>g</text>
              </view>
              <text class="net-inline" v-if="grossWeight && activeContainer">净重：{{ netWeight }}g</text>
            </view>
            <view v-if="grossWeight && activeContainer" class="net-formula">
              净重 {{ netWeight }}g = 含容器重量 {{ parseFloat(grossWeight) || 0 }}g - {{ activeContainer?.name || '去皮' }} {{ activeContainer?.tareWeight || 0 }}g
            </view>
          </view>
          <view class="fi"><text class="fil">容器（去皮）</text>
            <view v-if="activeContainer" class="container-detail">
              <view class="cd-icon">
                <image v-if="activeContainer.image" :src="activeContainer.image" mode="aspectFill" class="cd-img" @click.stop="previewMedia(activeContainer.image, false)" />
                <text v-else class="cd-placeholder">📦</text>
              </view>
              <view class="cd-info">
                <text class="cd-name">{{ activeContainer.name }}</text>
                <text class="cd-desc">{{ activeContainer.name }} · 约{{ activeContainer.tareWeight }}g</text>
              </view>
            </view>
            <view class="container-scroll-wrap">
              <scroll-view scroll-x enhanced show-scrollbar="false" class="container-scroll">
                <view class="container-pills">
                  <view v-for="c in containers" :key="c.id" class="cpill" :class="{ on: containerId === c.id }" @click="selectContainer(c.id)">{{ c.alias || c.name }}</view>
                </view>
              </scroll-view>
              <view class="scroll-fade"></view>
            </view>
          </view>
        </template>

        <!-- 到货验收：单位选择 + 数量 -->
        <template v-if="lossType === 'arrival'">
          <view class="fi"><text class="fil">企迈单号 <text class="req">*</text></text>
            <input class="fii" v-model="qimaiOrderNo" placeholder="请输入企迈单号" />
          </view>
          <view class="fi">
            <text class="fil">是否加急</text>
            <view style="display:flex;gap:12rpx">
              <view class="upill" :style="isUrgent ? 'border-color:#E05A47;color:#E05A47;background:#FEF0EF' : ''" @click="isUrgent = true">是</view>
              <view class="upill" :class="{ on: !isUrgent }" @click="isUrgent = false">否</view>
            </view>
          </view>
          <view class="fi" v-if="selectedMaterial && filteredUnits.length">
            <text class="fil">报损单位</text>
            <view class="unit-pills">
              <view v-for="u in filteredUnits" :key="u" class="upill" :class="{ on: inputUnit === u }" @click="selectArrivalUnit(u)">{{ u }}</view>
            </view>
            <view v-if="arrivalUnitHint" class="unit-hint">{{ arrivalUnitHint }}</view>
          </view>
          <view class="fi"><text class="fil">报损数量</text>
            <view class="unit-input"><input class="fii" v-model="lossQty" type="digit" placeholder="0" /><text class="unit-suffix" v-if="inputUnit">{{ inputUnit }}</text></view>
          </view>
        </template>

        <!-- 金额（所有类型） -->
        <view class="fi" v-if="selectedMaterial">
          <text class="fil" v-if="!unitPrice || unitPrice === '0' || unitPrice === '0.00'" style="color:#E58A2D">⚠ 该物料未配置单价，请在总部端物料管理补充</text>
          <text class="fil" v-else>金额（单价：¥{{ unitPrice }}）</text>
          <text class="fiv primary">¥{{ lossAmount || '0.00' }}</text>
        </view>

        <!-- 原因 -->
        <view class="fi"><text class="fil">报损原因</text>
          <view class="container-scroll-wrap">
            <scroll-view scroll-x enhanced show-scrollbar="false" class="container-scroll">
              <view class="reason-pills">
                <view v-for="r in lossReasons" :key="r" class="rp-pill" :class="{ on: reason === r }" @click="reason = r">{{ r }}</view>
              </view>
            </scroll-view>
            <view class="scroll-fade"></view>
          </view>
        </view>

        <!-- 备注 -->
        <view class="fi"><text class="fil">备注</text><textarea class="fta" v-model="remark" placeholder="备注信息" /></view>

        <!-- 上传凭证 -->
        <view class="fi">
          <view class="photo-head"><text class="fil">现场凭证 <text class="req">*</text></text><text class="photo-count" v-if="lossType === 'arrival'">最多4个 · 视频不超200M</text><text class="photo-count" v-else>最多4张</text></view>
          <text class="upload-hint" v-if="uploadHint && lossType === 'arrival'">{{ uploadHint }}</text>
          <view class="photo-grid">
            <view class="photo-upload" @click="chooseImage" v-if="mediaList.length < 4">
              <text class="pu-icon">📷</text><text class="pu-txt" v-if="lossType === 'arrival'">图片</text><text class="pu-txt" v-else>上传图片</text>
            </view>
            <view class="photo-upload" @click="openCamera" v-if="mediaList.length < 4 && lossType === 'arrival'">
              <text class="pu-icon">🎬</text><text class="pu-txt">视频</text>
            </view>
            <view v-for="(item, idx) in mediaList" :key="idx" class="photo-thumb" :class="{ 'video-thumb': item.isVideo, failed: item.failed }" @click="!item.failed && !item.uploading && previewMedia(item.url, item.isVideo)">
              <image v-if="!item.isVideo || item.thumb" :src="item.thumb || item.url" mode="aspectFill" class="pt-img" />
              <view v-if="item.isVideo && !item.thumb" class="vt-play">▶</view>
              <view v-if="item.isVideo && item.thumb" class="vt-play-mini">▶</view>
              <view v-if="item.uploading && item.isVideo" class="pt-loading">
                <text class="pt-progress-text">{{ item.progress || 0 }}%</text>
                <view class="pt-progress-bar"><view class="pt-progress-fill" :style="{ width: (item.progress || 0) + '%' }"></view></view>
              </view>
              <view v-if="item.uploading && !item.isVideo" class="pt-loading"><text>⋯</text></view>
              <view v-if="item.failed" class="vt-retry" @click.stop="retryMedia(item, idx)">
                <text class="vt-retry-icon">↻</text>
                <text class="vt-retry-txt">重试</text>
              </view>
              <text class="pt-del" @click.stop="removeMedia(idx)">✕</text>
            </view>
          </view>
        </view>
      </view>

      <view class="sheet-foot">
        <view class="sf-cancel" @click="showSheet = false">取消</view>
        <view class="sf-submit" @click="submitLoss">{{ submitting ? '提交中...' : '提交报损' }}</view>
      </view>
    </view>
  </view>

  <!-- 图片全屏预览 -->
  <view v-if="previewMediaUrl && previewMediaType === 'image'" class="img-viewer" @click="closePreviewMedia">
    <image :src="previewMediaUrl" mode="aspectFit" class="img-viewer-img" @click.stop />
    <view class="img-viewer-close" @click.stop="closePreviewMedia">✕</view>
  </view>
</template>

<style lang="scss" scoped>
$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$s:#fff;$bg:#F7F8F6;$w:#E58A2D;$d:#E05A47;.req{color:$d;margin-left:4rpx}
.page{min-height:100vh;background:$bg;padding-top:12rpx;padding-bottom:150rpx}.page.dimmed{overflow:hidden}
// 图片全屏预览
.img-viewer{position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.92);display:flex;align-items:center;justify-content:center}
.img-viewer-img{width:100%;height:80vh}
.img-viewer-close{position:fixed;top:calc(env(safe-area-inset-top) + 24rpx);right:32rpx;z-index:10000;width:88rpx;height:88rpx;border-radius:50%;background:rgba(0,0,0,.6);border:4rpx solid #fff;color:#fff;font-size:48rpx;line-height:1;display:flex;align-items:center;justify-content:center}
.img-viewer-close:active{background:rgba(255,255,255,.25)}

.overview-card{margin:20rpx;padding:24rpx;background:$s;border-radius:16rpx;border:1px solid $b}
.ov-top{display:flex;justify-content:space-between;gap:12rpx}.ov-store{font-size:26rpx;color:$p;font-weight:600}.ov-amount{display:block;font-size:36rpx;font-weight:800;color:$t1;margin-top:4rpx}.ov-desc{display:block;font-size:24rpx;color:$t2;margin-top:4rpx}.ov-icon{font-size:48rpx}
.ov-metrics{display:grid;grid-template-columns:1fr 1fr 1fr;gap:12rpx;margin-top:20rpx;padding:16rpx;background:#FAFBF9;border-radius:12rpx;text-align:center}
.ov-m{}.oml{font-size:22rpx;color:$t3}.omv{display:block;font-size:36rpx;font-weight:800;color:$t1;margin-top:4rpx}.omv.warn{color:$w}.omv.ok{color:$p}

// 底部固定快捷入口
.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:flex;gap:16rpx;padding:12rpx 24rpx calc(env(safe-area-inset-bottom) + 24rpx);background:rgba(255,255,255,.92);backdrop-filter:blur(20rpx);box-shadow:0 -10rpx 40rpx rgba(0,0,0,.04)}
// 底部按钮
.b-btn{flex:1;height:76rpx;border-radius:999rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:700;color:#fff}.b-btn-daily{background:$p}.b-btn-arrival{background:$w}

.tab-bar{display:flex;align-items:center;gap:12rpx;margin:0 20rpx 16rpx}.tab-scroll{flex:1;width:0}.tab-row{display:flex;gap:12rpx;white-space:nowrap}.tab-item{flex-shrink:0;padding:10rpx 24rpx;border-radius:999rpx;font-size:24rpx;color:$t2;background:#fff;border:1px solid $b}.tab-item.on{background:$p;color:#fff;border-color:$p}
.ov-batch{display:flex;align-items:center;justify-content:space-between;margin-top:16rpx;padding:14rpx 24rpx;border-radius:999rpx;background:#FDF3E7;border:1.5rpx solid #F0D9AE;color:#E58A2D;font-size:26rpx;font-weight:700}
.ov-batch.batch-on{background:#FEF0EF;color:#E05A47;border-color:#F3C1BC}
.ovb-btn{font-size:24rpx;font-weight:700;opacity:.95}
.section{margin:0 20rpx 24rpx}.section-title{font-size:32rpx;font-weight:700;color:$t1;margin-bottom:16rpx}
.record-list{display:flex;flex-direction:column;gap:16rpx}
// 加载骨架
.list-loading{display:flex;flex-direction:column;gap:16rpx}
.lk-card{padding:24rpx;background:$s;border-radius:16rpx;border:1px solid $b}
.lk-line{height:28rpx;border-radius:6rpx;background:linear-gradient(90deg,#F0F2F0 25%,#E5E9E5 50%,#F0F2F0 75%);background-size:200% 100%;animation:lk 1.2s infinite}
.lk-line+.lk-line{margin-top:16rpx}
@keyframes lk{from{background-position:200% 0}to{background-position:-200% 0}}
.rec-card{padding:24rpx;background:$s;border-radius:16rpx;border:1px solid $b}
.rec-check{flex-shrink:0;width:40rpx;height:40rpx;margin-right:20rpx;border-radius:50%;border:2rpx solid #C9CDD4;display:flex;align-items:center;justify-content:center;font-size:24rpx;color:transparent;background:#fff}.rec-check.on{border-color:$p;background:$p;color:#fff}.rec-check.disabled{opacity:.35}
.rec-card.batch-on{display:flex;align-items:flex-start}.rc-main{flex:1;min-width:0}
// 批量操作条
.batch-bar{position:fixed;left:0;right:0;bottom:0;z-index:10;display:flex;align-items:center;justify-content:space-between;gap:16rpx;padding:12rpx 24rpx calc(env(safe-area-inset-bottom) + 24rpx);background:rgba(255,255,255,.92);backdrop-filter:blur(20rpx);box-shadow:0 -10rpx 40rpx rgba(0,0,0,.04)}
.bb-left{display:flex;align-items:center}.bb-check{margin-right:8rpx}.bb-select-txt{font-size:26rpx;color:$t1}
.bb-right{display:flex;gap:16rpx}
.bb-btn{height:76rpx;padding:0 36rpx;border-radius:999rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:700;color:#fff}.bb-ok{background:$w}.bb-reject{background:#FEF0EF;color:#E05A47;border:1px solid #F3C1BC}
.rc-top{display:flex;justify-content:space-between;margin-bottom:12rpx}
.rc-type{font-size:22rpx;padding:4rpx 16rpx;border-radius:999rpx;background:$b;color:$t2}.rc-type.orange{background:#FFF8EE;color:$w}
.rc-status{font-size:22rpx;padding:4rpx 16rpx;border-radius:999rpx;background:$ps;color:$p}.rc-status.s-warn{background:#FFF8EE;color:$w}.rc-status.s-ok{background:$ps;color:$p}.rc-status.s-blue{background:#E8F0FE;color:#1A73E8}.rc-status.s-red{background:#FEF0EF;color:#E05A47}.rc-status.s-gray{background:$b;color:$t3}
.rc-name{display:block;font-size:28rpx;font-weight:600;color:$t1}.rc-code{display:block;font-size:22rpx;color:$t3;margin-top:2rpx;font-family:monospace}.rc-meta{display:block;font-size:24rpx;color:$t2;margin-top:4rpx}
.rc-reject{display:block;font-size:22rpx;color:#E05A47;margin-top:6rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.rc-progress{display:block;font-size:22rpx;color:#2F8F57;margin-top:6rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.rc-arrow{text-align:right;font-size:24rpx;color:$p;font-weight:600;margin-top:12rpx}
// 卡片底部操作按钮
.rc-actions{display:flex;gap:16rpx;margin-top:16rpx}
.rca-btn{flex:1;height:72rpx;border-radius:999rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:600;border:2rpx solid transparent}
.rca-ok{background:$ps;color:$p;border-color:$p}.rca-ok:active{background:$p;color:$s}
.rca-no{background:#FEF0EF;color:$d;border-color:$d}.rca-no:active{background:$d;color:$s}
.empty{text-align:center;padding:80rpx 0;font-size:26rpx;color:$t3}

.sheet-mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;max-height:88vh;border-radius:32rpx 32rpx 0 0;background:$s;overflow-y:auto;padding-bottom:env(safe-area-inset-bottom)}
.sh{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:20rpx auto}
.sheet-title{display:block;text-align:center;font-size:34rpx;font-weight:700;color:$t1;margin-bottom:4rpx}
.sheet-sub{display:block;text-align:center;font-size:24rpx;color:$t3;margin-bottom:20rpx}
.sheet-sub{display:block;text-align:center;font-size:24rpx;color:$t2;margin-bottom:20rpx}
.sheet-warn{display:flex;align-items:center;justify-content:center;gap:8rpx;margin-bottom:16rpx;padding:12rpx 24rpx;background:#FDF3E7;border-radius:12rpx}
.sheet-warn-icon{font-size:24rpx;flex-shrink:0}
.sheet-warn-text{font-size:24rpx;color:$w;line-height:1.4}
.search-tap{height:80rpx;padding:0 20rpx;border:1px solid $b;border-radius:12rpx;font-size:28rpx;color:$t3;display:flex;align-items:center;background:#FAFBF9}

.sheet-body{padding:0 32rpx;display:flex;flex-direction:column;gap:20rpx}
.fi{margin-bottom:24rpx}.fiv{display:block;font-size:30rpx;font-weight:700}.fiv.primary{color:$p}.fil{display:block;font-size:26rpx;color:$t2;margin-bottom:8rpx}.fii{width:100%;height:80rpx;padding:0 20rpx;border:1px solid $b;border-radius:12rpx;font-size:28rpx;background:#FAFBF9;box-sizing:border-box}
.unit-input{position:relative}.unit-suffix{position:absolute;right:20rpx;top:50%;transform:translateY(-50%);font-size:26rpx;color:$t2}
.gross-row{display:flex;align-items:center;justify-content:space-between}
.gross-input{flex:0 0 66.5%}
.net-inline{flex:1;text-align:right;margin-left:16rpx;font-size:28rpx;font-weight:600;color:$p;white-space:nowrap}
.container-grid{display:flex;flex-wrap:wrap;gap:8rpx}
.cg-item{padding:12rpx 24rpx;border-radius:999rpx;border:1px solid $b;font-size:24rpx;color:$t2;background:$s}.cg-item.on{border-color:$p;background:$ps;color:$p}
.container-preview{padding:16rpx;border-radius:12rpx;background:#FAFBF9;border:1px solid $b}.cpl{font-size:26rpx;color:$t2}
.net-formula{padding:16rpx;border-radius:12rpx;background:$ps;font-size:24rpx;color:$p;line-height:1.5;margin-top:3px}
.arrival-hint{padding:16rpx;border-radius:12rpx;background:#FFF8EE;font-size:24rpx;color:$w;line-height:1.5}
.reason-pills{display:flex;gap:12rpx;padding-right:40rpx}
.rp-pill{flex-shrink:0;padding:10rpx 28rpx;border-radius:999rpx;font-size:26rpx;background:$s;color:$t2;border:1.5px solid $b}.rp-pill.on{background:$ps;color:$p;border-color:$p}
.fta{width:100%;height:110rpx;padding:12rpx 16rpx;border:1px solid $b;border-radius:12rpx;font-size:26rpx;background:#FAFBF9;box-sizing:border-box}

.sheet-foot{display:grid;grid-template-columns:1fr 2fr;gap:16rpx;padding:24rpx 32rpx;margin-top:16rpx;border-top:1px solid $b}
.sf-cancel{height:88rpx;border-radius:12rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600;background:#FAFBF9;color:$t2;border:1px solid $b}
.sf-submit{height:88rpx;border-radius:12rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700;background:$p;color:#fff}

/* 物料搜索 */
.mat-results{max-height:350rpx;overflow:auto;margin-top:8rpx;border:1px solid $b;border-radius:12rpx;background:$s;position:relative}
.mat-results::after{content:'';position:absolute;right:4rpx;top:20%;height:60%;width:8rpx;border-radius:4rpx;background:rgba(0,0,0,0.18);opacity:0;animation:fadeBar 0.6s 0.3s ease-out forwards}
@keyframes fadeBar{to{opacity:1}}
.mat-item{display:flex;justify-content:space-between;align-items:center;padding:20rpx;border-bottom:1px solid #EEF1EF}.mat-item:last-child{border-bottom:0}
.mat-info{flex:1}.mat-name{font-size:28rpx;color:$t1;font-weight:500}.mat-meta{display:block;font-size:22rpx;color:$t3;margin-top:4rpx}
.mat-unit{font-size:24rpx;color:$p;font-weight:600;flex-shrink:0}
.mat-searching{text-align:center;padding:24rpx;font-size:24rpx;color:$t3}
.mat-empty{text-align:center;padding:32rpx;font-size:24rpx;color:$t3}
.mat-row{display:flex;align-items:center;gap:12rpx}
.selected-mat{flex:1;display:flex;align-items:center;height:80rpx;padding:0 20rpx;border:1px solid $p;border-radius:12rpx;background:$ps;overflow:hidden;min-width:0}
.sm-name{font-size:28rpx;font-weight:600;color:$t1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.sm-spec{font-size:24rpx;color:$t2;margin-left:8rpx;flex-shrink:0}
.sm-clear{font-size:36rpx;color:$t3;margin-left:auto;padding:8rpx;flex-shrink:0}
.standard-btn{flex-shrink:0;height:72rpx;padding:0 24rpx;display:flex;align-items:center;justify-content:center;font-size:24rpx;color:$p;border:1.5px solid $p;border-radius:999rpx}

/* 报损单位 */
.unit-pills{display:flex;flex-wrap:wrap;gap:10rpx}
.upill{padding:14rpx 28rpx;border-radius:999rpx;border:1.5px solid $b;font-size:26rpx;color:$t2;background:$s}
.upill.on{border-color:$p;color:$p;background:$ps}
.unit-hint{padding:12rpx 16rpx;margin-top:10rpx;border-radius:10rpx;background:$ps;font-size:24rpx;color:$p;line-height:1.4}

/* 容器卡片 */
.container-scroll-wrap{position:relative}
.container-scroll{white-space:nowrap}
.container-pills{display:flex;gap:10rpx;padding-right:40rpx}
.cpill{flex-shrink:0;padding:14rpx 28rpx;border-radius:999rpx;border:1.5px solid $b;font-size:26rpx;color:$t2;background:$s}
.cpill.on{border-color:$p;color:$p;background:$ps}
.scroll-fade{position:absolute;right:0;top:0;bottom:0;width:60rpx;background:linear-gradient(to right, transparent, $s);pointer-events:none;z-index:1}
.container-detail{display:flex;align-items:center;gap:16rpx;margin:16rpx 0;padding:16rpx;border-radius:14rpx;background:#FAFBF9;border:1px solid $b}
.cd-icon{width:88rpx;height:88rpx;border-radius:16rpx;background:$s;display:flex;align-items:center;justify-content:center;flex-shrink:0;border:1px solid $b}
.cd-img{width:88rpx;height:88rpx;border-radius:16rpx}.cd-placeholder{font-size:44rpx}
.cd-info{flex:1}.cd-name{display:block;font-size:28rpx;font-weight:600;color:$t1}.cd-desc{display:block;font-size:24rpx;color:$t3;margin-top:4rpx}

/* 视频上传 */
.photo-head{display:flex;justify-content:space-between;align-items:center}.photo-count{font-size:22rpx;color:$t3}
.upload-hint{display:block;font-size:22rpx;color:#E58A2D;margin-top:4rpx}
.photo-grid{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:8rpx}
.photo-upload{width:140rpx;height:140rpx;border:1.5px dashed $b;border-radius:14rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4rpx;background:#FAFBF9}
.pu-icon{font-size:40rpx;color:$t3;line-height:1}.pu-txt{font-size:20rpx;color:$t3}
.photo-thumb{width:140rpx;height:140rpx;border-radius:14rpx;overflow:hidden;position:relative}.pt-img{width:100%;height:100%}.pt-loading{position:absolute;inset:0;background:rgba(0,0,0,.6);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4rpx}.pt-progress-text{font-size:24rpx;font-weight:700;color:#fff}.pt-progress-bar{width:80%;height:6rpx;background:rgba(255,255,255,.3);border-radius:999rpx;overflow:hidden}.pt-progress-fill{height:100%;background:#2F8F57;border-radius:999rpx;transition:width .3s}
.pt-del{position:absolute;top:4rpx;right:4rpx;width:36rpx;height:36rpx;border-radius:50%;background:rgba(0,0,0,.5);color:#fff;display:flex;align-items:center;justify-content:center;font-size:22rpx;z-index:1}
/* 视频缩略图 */
.video-thumb{background:linear-gradient(135deg,#3A3F3C,#1F2421)}.video-thumb.failed{background:linear-gradient(135deg,#4A3F3C,#3F2421);opacity:.7}
.vt-play{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:44rpx;color:#fff;opacity:.9;pointer-events:none}
.vt-play-mini{position:absolute;right:4rpx;bottom:4rpx;width:40rpx;height:40rpx;border-radius:50%;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;font-size:22rpx;color:#fff;pointer-events:none}
.vt-retry{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4rpx;background:rgba(0,0,0,.5);z-index:2}
.vt-retry-icon{font-size:36rpx;color:#fff}.vt-retry-txt{font-size:20rpx;color:#fff}
</style>
