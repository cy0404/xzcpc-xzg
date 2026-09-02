<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { createLoss, searchMaterials, uploadLossImage, uploadLossVideo, appendLossVoucher } from '@/api/loss-report'
import { BASE_URL, H5_BASE } from '@/utils/constants'

const userStore = useUserStore()

// 物料
const selectedMaterial = ref<any>(null)
const materialSearchKey = ref('')
const materialResults = ref<any[]>([])
const materialSearching = ref(false)
const searchExpanded = ref(false)
let searchTimer: any = null

// 到货验收表单
const qimaiOrderNo = ref('')
const isUrgent = ref(false)
const inputUnit = ref('')
const lossQty = ref('')
const unitPrice = ref('')
const lossAmount = ref('')
const reason = ref('运输破损-外包装')
const remark = ref('')
const arrivalReasons = ['运输破损-外包装', '运输破损-内包装', '少货', '错发', '临期或变质', '其他']
const submitting = ref(false)

// 媒体
interface MediaItem { url: string; isVideo: boolean; uploading: boolean; failed: boolean; size: number; progress: number; thumb?: string }
const mediaList = ref<MediaItem[]>([])
const uploadedUrls = computed(() => mediaList.value.filter(i => !i.uploading && !i.failed).map(i => i.url))
const videoCount = computed(() => mediaList.value.filter(i => i.isVideo && !i.uploading && !i.failed).length)
const uploadHint = computed(() => {
  const needsVideo = ['运输破损-外包装', '运输破损-内包装', '临期或变质']
  if (needsVideo.includes(reason.value)) return '请至少拍摄1段视频作为凭证'
  return '请上传图片或视频作为凭证'
})

// 金额计算
const displayUnit = computed(() => inputUnit.value || selectedMaterial.value?.stockUnit || selectedMaterial.value?.baseUnit || '')
const arrivalUnitHint = computed(() => {
  if (!selectedMaterial.value) return ''
  const infos = selectedMaterial.value.unitInfos || []
  const info = infos.find((i: any) => i.unit === inputUnit.value)
  if (!info || !info.hint) return ''
  if (inputUnit.value === selectedMaterial.value.baseUnit) return ''
  return info.hint
})
const isAvocado = computed(() => selectedMaterial.value?.materialName?.includes('牛油果泥'))
const filteredUnits = computed(() => {
  const units = selectedMaterial.value?.units || []
  if (!isAvocado.value) return units
  return units.filter((u: string) => u !== 'g' && u !== 'kg')
})
watch([lossQty, unitPrice], () => {
  const price = parseFloat(unitPrice.value)
  if (!(price > 0)) { lossAmount.value = ''; return }
  const qty = parseFloat(lossQty.value)
  lossAmount.value = qty > 0 ? (qty * price).toFixed(2) : '0.00'
})

// 物料搜索
function onMaterialSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = materialSearchKey.value.trim()
  if (!kw) { loadDefaultMaterials(); return }
  searchTimer = setTimeout(async () => {
    materialSearching.value = true
    try { const res: any = await searchMaterials(kw, 'finished'); materialResults.value = res.list || [] }
    catch { materialResults.value = [] }
    finally { materialSearching.value = false }
  }, 300)
}
async function loadDefaultMaterials() {
  materialSearching.value = true
  try { const res: any = await searchMaterials('', 'finished'); materialResults.value = res.list || [] }
  catch { materialResults.value = [] }
  finally { materialSearching.value = false }
}
function resolveUnitPrice(m: any, unit: string): string {
  if (!m || !m.unitPrice) return ''
  const baseUnit = m.baseUnit || ''
  if (!unit || unit === baseUnit) return String(m.unitPrice)
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
  inputUnit.value = m.stockUnit || m.baseUnit || ''
  unitPrice.value = resolveUnitPrice(m, inputUnit.value)
}
function selectArrivalUnit(unit: string) {
  inputUnit.value = unit
  unitPrice.value = resolveUnitPrice(selectedMaterial.value, unit)
}
function clearMaterial() { selectedMaterial.value = null; materialSearchKey.value = '' }

// 上传
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
    addVideo(filePath, size)
  } else {
    // chooseMedia 没返回 size（极少数旧设备），直接加，不阻塞用户
    addVideo(filePath, 0)
  }
}

function addVideo(filePath: string, size: number) {
  const item: MediaItem = { url: filePath, isVideo: true, uploading: true, failed: false, size, progress: 0 }
  mediaList.value.push(item)
  startVideoUpload(item, mediaList.value.length - 1)
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
function openCamera() {
  const h5url = H5_BASE + '/upload/h5/loss-arrival.html?v=3&token=' + encodeURIComponent(uni.getStorageSync('token') || '') + '&storeName=' + encodeURIComponent(userStore.storeName || '')
  uni.navigateTo({ url: '/pages/loss-report/camera-h5/index?url=' + encodeURIComponent(h5url) })
}
function onCameraRecorded(filePath: string, size: number, thumb?: string) {
  // H5 相机已上传完毕，直接使用返回的 URL
  const item: MediaItem = { url: filePath, isVideo: true, uploading: false, failed: false, size, progress: 100, thumb: thumb || '' }
  mediaList.value.push(item)
}
function removeMedia(idx: number) { mediaList.value.splice(idx, 1) }
function previewMedia(url: string, isVideo: boolean) {
  if (isVideo) uni.previewMedia({ sources: [{ url, type: 'video' }] })
  else uni.previewImage({ urls: [url], current: url })
}
function playVideo(url: string) { uni.previewMedia({ sources: [{ url, type: 'video' }] }) }
function retryMedia(item: MediaItem, idx: number) {
  if (item.isVideo) startVideoUpload({ ...item }, idx)
  else startImageUpload({ ...item }, idx)
}
function goStandard(materialId: string) {
  uni.navigateTo({ url: `/pages/loss-report/standard/index?materialId=${materialId}` })
}

// 提交
async function submitLoss() {
  if (submitting.value) return
  if (!selectedMaterial.value) { uni.showToast({ title: '请选择物料', icon: 'none' }); return }
  if (!lossQty.value || parseFloat(lossQty.value) <= 0) { uni.showToast({ title: '请填写报损数量', icon: 'none' }); return }
  if (!qimaiOrderNo.value.trim()) { uni.showToast({ title: '请填写企迈单号', icon: 'none' }); return }
  if (!reason.value) { uni.showToast({ title: '请选择报损原因', icon: 'none' }); return }
  if (reason.value === '错发' && !remark.value.trim()) { uni.showToast({ title: '请填写备注说明错发情况', icon: 'none' }); return }
  if (mediaList.value.length === 0) { uni.showToast({ title: '请上传现场凭证', icon: 'none' }); return }
  if (mediaList.value.some(m => m.failed)) { uni.showToast({ title: '有文件上传失败，请重试或删除', icon: 'none' }); return }
  const needsVideo = ['运输破损-外包装', '运输破损-内包装', '临期或变质']
  if (needsVideo.includes(reason.value) && videoCount.value === 0) {
    uni.showToast({ title: '该原因需至少拍摄1段视频作为凭证', icon: 'none' }); return
  }
  submitting.value = true
  try {
    const m = selectedMaterial.value
    const result: any = await createLoss({
      lossType: 'arrival', lossObject: 'finished',
      materialId: m?.materialId || null,
      materialName: m?.materialName || materialSearchKey.value.trim(),
      spec: m?.spec || '',
      inputUnit: inputUnit.value,
      inputQty: lossQty.value ? parseFloat(lossQty.value) : null,
      unitPrice: unitPrice.value ? parseFloat(unitPrice.value) : null,
      totalAmount: lossAmount.value ? parseFloat(lossAmount.value) : null,
      qimaiOrderNo: qimaiOrderNo.value.trim(),
      reason: reason.value, remark: remark.value,
      voucherUrl: uploadedUrls.value.join(','),
      urgent: isUrgent.value ? 1 : 0,
    })
    const reportId = result?.id || result?.data?.id
    uni.showToast({ title: '提交成功', icon: 'success' })
    const pendingVideos = mediaList.value.filter(m => m.uploading)
    if (reportId && pendingVideos.length > 0) {
      uni.showToast({ title: `${pendingVideos.length}个视频后台上传中，稍等再退出`, icon: 'none', duration: 3000 })
      pendingVideos.forEach(v => {
        uploadLossVideo(v.url, v.size).then((res: any) => {
          var videoUrl = typeof res === 'string' ? res : (res.url || res)
          appendLossVoucher(reportId, videoUrl).catch(() => {})
        }).catch(() => {})
      })
    }
    setTimeout(() => uni.navigateBack(), 800)
  } catch { uni.showToast({ title: '提交失败', icon: 'none' }) }
  finally { submitting.value = false }
}

onLoad(() => { loadDefaultMaterials() })

defineExpose({ onCameraRecorded })
</script>

<template>
  <view class="page">
    <view class="body">
      <!-- 警告条 -->
      <view class="warn-bar">
        <text class="warn-icon">⚠️</text>
        <text class="warn-text">仅限企迈平台发的货支持到货验收报损，收货后请立即报损</text>
      </view>

      <!-- 物料搜索 -->
      <view class="card">
        <text class="card-title">物品名称</text>
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
          <text class="standard-btn" v-if="selectedMaterial.materialId" @click="goStandard(selectedMaterial.materialId)">验收标准</text>
        </view>
      </view>

      <!-- 企迈单号 -->
      <view class="card">
        <text class="card-title">企迈单号 <text class="req">*</text></text>
        <input class="fii" v-model="qimaiOrderNo" placeholder="请输入企迈单号" />
      </view>

      <!-- 是否加急 -->
      <view class="card">
        <text class="card-title">是否加急</text>
        <view style="display:flex;gap:12rpx">
          <view class="upill" :class="{ on: isUrgent }" :style="isUrgent ? 'border-color:#E05A47;color:#E05A47;background:#FEF0EF' : ''" @click="isUrgent = true">是</view>
          <view class="upill" :class="{ on: !isUrgent }" @click="isUrgent = false">否</view>
        </view>
      </view>

      <!-- 报损单位 + 数量 -->
      <view class="card" v-if="selectedMaterial && filteredUnits.length">
        <text class="card-title">报损单位</text>
        <view class="unit-pills">
          <view v-for="u in filteredUnits" :key="u" class="upill" :class="{ on: inputUnit === u }" @click="selectArrivalUnit(u)">{{ u }}</view>
        </view>
        <view v-if="arrivalUnitHint" class="unit-hint">{{ arrivalUnitHint }}</view>
      </view>
      <view class="card">
        <text class="card-title">报损数量</text>
        <view class="unit-input"><input class="fii" v-model="lossQty" type="digit" placeholder="0" /><text class="unit-suffix" v-if="inputUnit">{{ inputUnit }}</text></view>
      </view>

      <!-- 金额 -->
      <view class="card" v-if="selectedMaterial">
        <text class="card-title" v-if="!unitPrice || unitPrice === '0' || unitPrice === '0.00'" style="color:#E58A2D">⚠ 该物料未配置单价，请在总部端补充</text>
        <text class="card-title" v-else>金额（单价：¥{{ unitPrice }}）</text>
        <text class="fiv primary">¥{{ lossAmount || '0.00' }}</text>
      </view>

      <!-- 原因 -->
      <view class="card">
        <text class="card-title">报损原因</text>
        <scroll-view scroll-x enhanced show-scrollbar="false">
          <view class="reason-pills">
            <view v-for="r in arrivalReasons" :key="r" class="rp-pill" :class="{ on: reason === r }" @click="reason = r">{{ r }}</view>
          </view>
        </scroll-view>
      </view>

      <!-- 备注 -->
      <view class="card">
        <text class="card-title">备注{{ reason === '错发' ? '（必填）' : '' }}</text>
        <textarea class="fta" v-model="remark" placeholder="备注信息" />
      </view>

      <!-- 凭证上传 -->
      <view class="card">
        <text class="card-title">现场凭证 <text class="req">*</text></text>
        <text class="photo-count">最多4个 · 视频不超200M</text>
        <text class="upload-hint" v-if="uploadHint">{{ uploadHint }}</text>
        <view class="photo-grid">
          <view class="photo-upload" @click="chooseImage" v-if="mediaList.length < 4">
            <text class="pu-icon">📷</text><text class="pu-txt">图片</text>
          </view>
          <view class="photo-upload" @click="openCamera" v-if="mediaList.length < 4">
            <text class="pu-icon">🎬</text><text class="pu-txt">视频</text>
          </view>
          <view v-for="(item, idx) in mediaList" :key="idx" class="photo-thumb" :class="{ 'video-thumb': item.isVideo, failed: item.failed }" @click="!item.failed && !item.uploading && (item.isVideo ? playVideo(item.url) : previewMedia(item.url, false))">
            <image v-if="!item.isVideo || item.thumb" :src="item.thumb || item.url" mode="aspectFill" class="pt-img" />
            <view v-if="item.isVideo && !item.thumb" class="vt-play">▶</view>
            <view v-if="item.isVideo && item.thumb" class="vt-play-mini">▶</view>
            <view v-if="item.uploading && item.isVideo" class="pt-loading">
              <text class="pt-progress-text">{{ item.progress || 0 }}%</text>
              <view class="pt-progress-bar"><view class="pt-progress-fill" :style="{ width: (item.progress || 0) + '%' }"></view></view>
            </view>
            <view v-if="item.uploading && !item.isVideo" class="pt-loading"><text>⋯</text></view>
            <view v-if="item.failed" class="vt-retry" @click.stop="retryMedia(item, idx)">
              <text class="vt-retry-icon">↻</text><text class="vt-retry-txt">重试</text>
            </view>
            <text class="pt-del" @click.stop="removeMedia(idx)">✕</text>
          </view>
        </view>
      </view>

      <!-- 提交 -->
      <view class="submit-bar">
        <view class="sf-submit" @click="submitLoss">{{ submitting ? '提交中...' : '提交报损' }}</view>
      </view>
    </view>

    <!-- 图片预览 -->
    <view v-if="previewImgUrl" class="img-viewer" @click="closePreview">
      <image :src="previewImgUrl" mode="aspectFit" class="img-viewer-img" @click.stop />
      <view class="img-viewer-close" @click.stop="closePreview">✕</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$s:#fff;$bg:#F7F8F6;$w:#E58A2D;$d:#E05A47;
.req{color:$d;margin-left:4rpx}
.page{min-height:100vh;background:$bg}
.body{padding:24rpx;display:flex;flex-direction:column;gap:20rpx}

.card{background:$s;border-radius:16rpx;padding:24rpx;border:1px solid $b}
.card-title{display:block;font-size:26rpx;color:$t2;margin-bottom:12rpx}
.fii{width:100%;height:80rpx;padding:0 20rpx;border:1px solid $b;border-radius:12rpx;font-size:28rpx;background:#FAFBF9;box-sizing:border-box}
.fiv{display:block;font-size:30rpx;font-weight:700}.fiv.primary{color:$p}

.search-tap{height:80rpx;padding:0 20rpx;border:1px solid $b;border-radius:12rpx;font-size:28rpx;color:$t3;display:flex;align-items:center;background:#FAFBF9}

.warn-bar{display:flex;align-items:center;justify-content:center;gap:8rpx;padding:16rpx 24rpx;background:#FDF3E7;border-radius:16rpx}
.warn-icon{font-size:24rpx;flex-shrink:0}.warn-text{font-size:24rpx;color:$w;line-height:1.4}

/* 物料选择 */
.mat-results{max-height:350rpx;overflow:auto;margin-top:8rpx;border:1px solid $b;border-radius:12rpx;background:$s}
.mat-item{display:flex;justify-content:space-between;align-items:center;padding:20rpx;border-bottom:1px solid #EEF1EF}.mat-item:last-child{border-bottom:0}
.mat-info{flex:1}.mat-name{font-size:28rpx;color:$t1;font-weight:500}.mat-meta{display:block;font-size:22rpx;color:$t3;margin-top:4rpx}
.mat-searching{text-align:center;padding:24rpx;font-size:24rpx;color:$t3}
.mat-empty{text-align:center;padding:32rpx;font-size:24rpx;color:$t3}
.mat-row{display:flex;align-items:center;gap:12rpx}
.selected-mat{flex:1;display:flex;align-items:center;height:80rpx;padding:0 20rpx;border:1px solid $p;border-radius:12rpx;background:$ps;overflow:hidden;min-width:0}
.sm-name{font-size:28rpx;font-weight:600;color:$t1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.sm-spec{font-size:24rpx;color:$t2;margin-left:8rpx;flex-shrink:0}
.sm-clear{font-size:36rpx;color:$t3;margin-left:auto;padding:8rpx;flex-shrink:0}
.standard-btn{flex-shrink:0;height:72rpx;padding:0 24rpx;display:flex;align-items:center;justify-content:center;font-size:24rpx;color:$p;border:1.5px solid $p;border-radius:999rpx}

/* 单位 */
.unit-pills{display:flex;flex-wrap:wrap;gap:10rpx}
.upill{padding:14rpx 28rpx;border-radius:999rpx;border:1.5px solid $b;font-size:26rpx;color:$t2;background:$s}
.upill.on{border-color:$p;color:$p;background:$ps}
.unit-hint{padding:12rpx 16rpx;margin-top:10rpx;border-radius:10rpx;background:$ps;font-size:24rpx;color:$p;line-height:1.4}
.unit-input{position:relative}.unit-suffix{position:absolute;right:20rpx;top:50%;transform:translateY(-50%);font-size:26rpx;color:$t2}

/* 原因 */
.reason-pills{display:flex;gap:12rpx;padding-right:40rpx;white-space:nowrap}
.rp-pill{flex-shrink:0;padding:10rpx 28rpx;border-radius:999rpx;font-size:26rpx;background:$s;color:$t2;border:1.5px solid $b}
.rp-pill.on{background:$ps;color:$p;border-color:$p}

.fta{width:100%;height:110rpx;padding:12rpx 16rpx;border:1px solid $b;border-radius:12rpx;font-size:26rpx;background:#FAFBF9;box-sizing:border-box}

/* 凭证 */
.photo-count{display:block;font-size:22rpx;color:$t3;margin-top:4rpx}
.upload-hint{display:block;font-size:22rpx;color:#E58A2D;margin-top:4rpx}
.photo-grid{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:8rpx}
.photo-upload{width:140rpx;height:140rpx;border:1.5px dashed $b;border-radius:14rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4rpx;background:#FAFBF9}
.pu-icon{font-size:40rpx;line-height:1}.pu-txt{font-size:20rpx;color:$t3}
.photo-thumb{width:140rpx;height:140rpx;border-radius:14rpx;overflow:hidden;position:relative}.pt-img{width:100%;height:100%}
.pt-loading{position:absolute;inset:0;background:rgba(0,0,0,.6);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4rpx;color:#fff;font-size:36rpx}
.pt-progress-text{font-size:24rpx;font-weight:700}.pt-progress-bar{width:80%;height:6rpx;background:rgba(255,255,255,.3);border-radius:999rpx;overflow:hidden}.pt-progress-fill{height:100%;background:#2F8F57;border-radius:999rpx;transition:width .3s}
.pt-del{position:absolute;top:4rpx;right:4rpx;width:36rpx;height:36rpx;border-radius:50%;background:rgba(0,0,0,.5);color:#fff;display:flex;align-items:center;justify-content:center;font-size:22rpx;z-index:1}
.video-thumb{background:linear-gradient(135deg,#3A3F3C,#1F2421)}.video-thumb.failed{opacity:.7}
.vt-play{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:44rpx;color:#fff;opacity:.9;pointer-events:none}
.vt-play-mini{position:absolute;right:4rpx;bottom:4rpx;width:40rpx;height:40rpx;border-radius:50%;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;font-size:22rpx;color:#fff;pointer-events:none}
.vt-retry{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4rpx;background:rgba(0,0,0,.5);z-index:2}
.vt-retry-icon{font-size:36rpx;color:#fff}.vt-retry-txt{font-size:20rpx;color:#fff}

.submit-bar{padding:24rpx 0 48rpx}
.sf-submit{width:100%;height:96rpx;border-radius:16rpx;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700;background:$p;color:#fff}

.img-viewer{position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.92);display:flex;align-items:center;justify-content:center}
.img-viewer-img{width:100%;height:80vh}
.img-viewer-close{position:fixed;top:calc(env(safe-area-inset-top) + 24rpx);right:32rpx;z-index:10000;width:88rpx;height:88rpx;border-radius:50%;background:rgba(0,0,0,.6);border:4rpx solid #fff;color:#fff;font-size:48rpx;line-height:1;display:flex;align-items:center;justify-content:center}
</style>
