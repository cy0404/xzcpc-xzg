<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { getLossReportDetail, confirmLossReport, rejectLossReport } from '../../api/loss'

const route = useRoute()
const id = Number(route.params.id)
const report = ref<any>(null)
const loading = ref(true)
const rejecting = ref(false)
const rejectReason = ref('')
const actionLoading = ref(false)

const statusCfg = computed(() => {
  const s = report.value?.status
  if (s === 'confirmed_resend') return { label: '已确认补发', color: '#2F8F57' }
  if (s === 'rejected') return { label: '已拒绝', color: '#E05A47' }
  if (s === 'closed') return { label: '已关闭', color: '#98A19C' }
  return { label: '待厂家确认', color: '#E58A2D' }
})

const canOperate = computed(() => report.value?.status === 'pending')

const qtyDisplay = computed(() => {
  if (!report.value) return '--'
  if (report.value.netWeight) return `净重 ${report.value.netWeight}g`
  if (report.value.inputQty) return `${report.value.inputQty} ${report.value.inputUnit || ''}`
  return '--'
})

function isVideoUrl(url: string) { return /\.(mp4|mov|avi|mkv|webm)($|\?)/i.test(url) }
const lossMedia = computed(() => {
  if (!report.value?.voucherUrl) return []
  return report.value.voucherUrl.split(',').filter(Boolean).map(url => ({ url, isVideo: isVideoUrl(url) }))
})
const videoPreviewOpen = ref(false)
const videoPreviewUrl = ref('')
function openVideoPreview(url: string) { videoPreviewUrl.value = url; videoPreviewOpen.value = true }

async function fetchDetail() {
  loading.value = true
  try {
    const res: any = await getLossReportDetail(id)
    report.value = res.data?.data || res.data
  } finally {
    loading.value = false
  }
}

async function doConfirm() {
  actionLoading.value = true
  try {
    await confirmLossReport(id)
    message.success('已确认补发')
    fetchDetail()
  } catch {
    message.error('操作失败')
  } finally {
    actionLoading.value = false
  }
}

async function doReject() {
  if (!rejectReason.value.trim()) {
    message.warning('请填写拒绝原因')
    return
  }
  actionLoading.value = true
  try {
    await rejectLossReport(id, rejectReason.value)
    message.success('已拒绝')
    rejecting.value = false
    fetchDetail()
  } catch {
    message.error('操作失败')
  } finally {
    actionLoading.value = false
  }
}

fetchDetail()
</script>

<template>
  <div style="max-width:720px;margin:0 auto;padding:24px">
    <div v-if="loading" style="text-align:center;padding:80px 0;color:#98A19C">加载中...</div>

    <template v-else-if="report">
      <div style="display:flex;align-items:center;gap:16px;margin-bottom:24px">
        <div style="font-size:22px;font-weight:700">{{ statusCfg.label }}</div>
        <a-tag :color="statusCfg.color">{{ statusCfg.label }}</a-tag>
      </div>

      <a-descriptions bordered :column="2" size="small" style="margin-bottom:24px">
        <a-descriptions-item label="物料">{{ report.materialName }}</a-descriptions-item>
        <a-descriptions-item label="数量/重量">{{ qtyDisplay }}</a-descriptions-item>
        <a-descriptions-item label="报损门店">{{ report.storeName || '--' }}</a-descriptions-item>
        <a-descriptions-item label="报损原因">{{ report.reason || '--' }}</a-descriptions-item>
        <a-descriptions-item label="企迈单号">{{ report.qimaiOrderNo || '--' }}</a-descriptions-item>
        <a-descriptions-item label="登记人">{{ report.handlerName || '--' }}</a-descriptions-item>
        <a-descriptions-item label="登记时间">{{ report.createdAt?.substring(0, 16) || '--' }}</a-descriptions-item>
      </a-descriptions>

      <div v-if="report.remark" style="margin-bottom:16px;padding:12px;background:#fafafa;border-radius:8px">
        <div style="color:#66706A;font-size:12px;margin-bottom:4px">备注</div>
        <div>{{ report.remark }}</div>
      </div>

      <!-- 附件 -->
      <div v-if="lossMedia.length" style="margin-bottom:16px">
        <div style="color:#66706A;font-size:12px;margin-bottom:8px">附件</div>
        <div class="media-grid">
          <template v-for="(item, i) in lossMedia" :key="i">
            <a-image v-if="!item.isVideo" :src="item.url" style="width:100%;border-radius:6px;aspect-ratio:1;object-fit:cover" />
            <div v-else class="video-thumb" @click="openVideoPreview(item.url)">
              <video :src="item.url" preload="metadata" muted playsinline class="video-thumb-el" />
              <div class="video-play-overlay">▶</div>
            </div>
          </template>
        </div>
      </div>

      <!-- 视频预览弹窗 -->
      <a-modal v-model:open="videoPreviewOpen" :footer="null" width="720px" destroy-on-close @cancel="videoPreviewOpen = false">
        <video v-if="videoPreviewUrl" :src="videoPreviewUrl" controls autoplay style="width:100%;max-height:70vh;border-radius:8px;background:#000" />
      </a-modal>

      <div v-if="canOperate" style="display:flex;gap:12px;margin-top:16px">
        <a-button type="primary" size="large" @click="doConfirm" :loading="actionLoading">✅ 确认补发</a-button>
        <a-button v-if="!rejecting" danger size="large" @click="rejecting = true">❌ 拒绝</a-button>
      </div>

      <div v-if="rejecting && canOperate" style="margin-top:16px">
        <a-textarea v-model:value="rejectReason" placeholder="请填写拒绝原因" :rows="3" style="margin-bottom:12px" />
        <div style="display:flex;gap:8px">
          <a-button danger @click="doReject" :loading="actionLoading">确认拒绝</a-button>
          <a-button @click="rejecting = false">取消</a-button>
        </div>
      </div>

      <div v-if="!canOperate && report.status === 'rejected' && report.rejectReason" style="margin-top:12px;padding:12px;background:#FFF4F2;border-radius:8px">
        <div style="color:#E05A47;font-size:12px;margin-bottom:4px">拒绝原因</div>
        <div style="color:#1F2421">{{ report.rejectReason }}</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.media-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
.video-thumb { position: relative; border-radius: 6px; overflow: hidden; cursor: pointer; aspect-ratio: 1; background: #000 }
.video-thumb-el { width: 100%; height: 100%; object-fit: cover; display: block }
.video-play-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 32px; color: #fff; background: rgba(0,0,0,.25); pointer-events: none }
</style>
