<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getLossDetail, approveLoss, rejectApproval, getLossLogs, receiveLoss, notReceiveLoss, closeLoss, deleteDailyLoss } from '@/api/loss-report'
import { useUserStore } from '@/store/user'
import { H5_BASE } from '@/utils/constants'

const userStore = useUserStore()
const id = ref(0)
const report = ref<any>(null)
const loading = ref(true)
const canApprove = computed(() => userStore.role === 'store_manager' || userStore.role === 'owner')
const isDaily = computed(() => report.value?.lossType === 'daily')

onLoad((options: any) => { id.value = Number(options.id); fetchDetail() })
onShow(() => { if (id.value > 0) fetchDetail() })

const actionLoading = ref(false)
const logs = ref<any[]>([])

async function fetchDetail() {
  loading.value = true
  try { report.value = await getLossDetail(id.value); await fetchLogs() }
  finally { loading.value = false }
}
async function fetchLogs() {
  try { logs.value = (await getLossLogs(id.value)) || [] } catch { logs.value = [] }
}

function actionLabel(a: string) {
  const m: Record<string, string> = { submit: '提交报损', approve: '审批通过', reject_approval: '审批拒绝', confirm: '厂家确认补发', reject: '厂家拒绝', register: '厂家确认登记', issue_voucher: '厂家确认发券', receive: '已收货', not_receive: '未收到货', update: '修改报损', delete: '删除报损' }
  return m[a] || a
}

function startEdit() {
  uni.navigateTo({ url: `/pages/loss-report/form-daily/index?editId=${id.value}` })
}

async function doDelete() {
  const res = await new Promise<boolean>(resolve => {
    uni.showModal({ title: '确认删除', content: '删除后不可恢复，确定删除该报损记录？', success: r => resolve(r.confirm) })
  })
  if (!res) return
  if (actionLoading.value) return
  actionLoading.value = true
  try { await deleteDailyLoss(id.value); uni.showToast({ title: '已删除', icon: 'success' }); setTimeout(() => uni.navigateBack(), 800) }
  catch { uni.showToast({ title: '删除失败', icon: 'none' }) }
  finally { actionLoading.value = false }
}

// ---- Existing actions ----
const nextStep = computed(() => {
  const s = report.value?.status
  const isArrival = report.value?.lossType === 'arrival'
  if (s === 'pending_approval') return '等待店长审批'
  if (s === 'pending') return '等待厂家确认'
  if (s === 'registered') return report.value?.isFruitVeg ? '已登记，等待厂家发券' : '已发券，等待厂家发货'
  if (s === 'confirmed_resend') return '等待门店收货'
  if (isArrival && (s === 'completed' || s === 'closed' || s === 'rejected' || s === 'received' || s === 'not_received')) return ''
  if (!isArrival && (s === 'completed' || s === 'rejected')) return ''
  return ''
})
async function doApprove() { if (actionLoading.value) return; actionLoading.value = true; try { await approveLoss(id.value); fetchDetail() } catch { fetchDetail() } finally { actionLoading.value = false } }
async function doRejectApproval() { if (actionLoading.value) return; actionLoading.value = true; try { await rejectApproval(id.value); fetchDetail() } catch { fetchDetail() } finally { actionLoading.value = false } }
const receiving = ref(false); const receiveRemark = ref('')
async function doReceive() { if (actionLoading.value) return; actionLoading.value = true; try { await receiveLoss(id.value, receiveRemark.value); receiveRemark.value = ''; receiving.value = false; fetchDetail() } catch { fetchDetail() } finally { actionLoading.value = false } }
async function doNotReceive() { if (actionLoading.value) return; if (!receiveRemark.value.trim()) { uni.showToast({ title: '请填写未收到货原因', icon: 'none' }); return }; actionLoading.value = true; try { await notReceiveLoss(id.value, receiveRemark.value); receiveRemark.value = ''; receiving.value = false; fetchDetail() } catch { fetchDetail() } finally { actionLoading.value = false } }
async function doCloseLoss() { if (actionLoading.value) return; actionLoading.value = true; try { await closeLoss(id.value); uni.showToast({ title: '已关闭', icon: 'success' }); fetchDetail() } catch { fetchDetail() } finally { actionLoading.value = false } }
function doResubmit() { const h5url = H5_BASE + '/upload/h5/loss-arrival.html?v=2&token=' + encodeURIComponent(uni.getStorageSync('token') || '') + '&storeName=' + encodeURIComponent(userStore.storeName || '') + '&resubmit=' + id.value; uni.navigateTo({ url: '/pages/loss-report/camera-h5/index?url=' + encodeURIComponent(h5url) }) }

const statusCfg = computed(() => {
  const s = report.value?.status
  if (s === 'pending_approval') return { label: '待店长审批', cls: 's-orange', color: '#E58A2D' }
  if (s === 'registered') return { label: '已登记', cls: 's-green', color: '#1A73E8' }
  if (s === 'confirmed_resend') return { label: report.value?.isFruitVeg ? '已发券' : '已确认补发', cls: 's-green', color: '#2F8F57' }
  if (s === 'rejected') return { label: report.value?.lossType === 'arrival' ? '厂家拒绝' : '已拒绝', cls: 's-red', color: '#E05A47' }
  if (s === 'completed') return { label: '已录入', cls: 's-green', color: '#2F8F57' }
  if (s === 'received') return { label: '已收货', cls: 's-green', color: '#2F8F57' }
  if (s === 'not_received') return { label: '未收到货', cls: 's-red', color: '#E05A47' }
  if (s === 'closed') return { label: '已关闭', cls: 's-gray', color: '#98A19C' }
  if (report.value?.lossType === 'arrival') return { label: '待厂家确认', cls: 's-orange', color: '#E58A2D' }
  return { label: '已记录', cls: 's-green', color: '#2F8F57' }
})

const mediaList = computed(() => {
  if (!report.value?.voucherUrl) return []
  return report.value.voucherUrl.split(',').filter(Boolean).map((url: string) => ({ url, isVideo: /\.(mp4|mov|avi|mkv|webm)($|\?)/i.test(url) }))
})
function isVideo(url: string) { return /\.(mp4|mov|avi|mkv|webm)($|\?)/i.test(url) }
function playMedia(item: { url: string; isVideo: boolean }) { if (item.isVideo) { uni.previewMedia({ sources: [{ url: item.url, type: 'video' }] }) } else { previewImgUrl.value = item.url; showImgPreview.value = true } }
const previewImgUrl = ref(''); const showImgPreview = ref(false)
function previewImage(url: string) { uni.previewImage({ urls: [url], current: url }) }
async function removeVoucherImage(url: string) {
  try {
    await removeLossVoucher(id.value, url)
    // 本地移除
    report.value.voucherUrl = report.value.voucherUrl.split(',').filter((u: string) => u !== url).join(',')
  } catch { uni.showToast({ title: '删除失败', icon: 'none' }) }
}
const rejectAttCount = computed(() => {
  if (!report.value?.rejectReason) return 0
  const rejectLog = logs.value.find((l: any) => l.action === 'reject')
  if (!rejectLog?.attachmentUrl) return 0
  return rejectLog.attachmentUrl.split(',').filter(Boolean).length
})
function fmtTime(t: string) { if (!t) return '--'; return t.substring(0, 16) }
function playAttMedia(url: string) {
  if (isVideo(url)) {
    uni.previewMedia({ sources: [{ url, type: 'video' }] })
  } else {
    uni.previewImage({ urls: [url] })
  }
}
</script>

<template>
  <view class="page">
    <view v-if="!loading && report" class="content">
      <!-- 基础信息卡 -->
      <view class="card info-card">
        <view class="info-grid">
          <view class="ig-item"><text class="igl">报损类型</text><text class="igv">{{ report.lossType === 'arrival' ? '到货验收报损' : '日常报损' }}</text></view>
          <view class="ig-item"><text class="igl">报损原因</text><text class="igv">{{ report.reason || '--' }}</text></view>
          <view class="ig-item"><text class="igl">登记人</text><text class="igv">{{ report.handlerName || report.submittedBy || '--' }}</text></view>
          <view class="ig-item"><text class="igl">创建时间</text><text class="igv">{{ fmtTime(report.createdAt) }}</text></view>
          <view class="ig-item" v-if="report.updatedAt && report.updatedAt !== report.createdAt"><text class="igl">更新时间</text><text class="igv">{{ fmtTime(report.updatedAt) }}</text></view>
        </view>
      </view>

      <!-- 物料信息卡 -->
      <view class="card main-card">
        <view class="mc-top">
          <view class="mc-left">
            <text class="mc-tag" :class="statusCfg.cls">{{ statusCfg.label }}</text>
            <text class="mc-title">{{ report.itemNames || report.materialName }}</text>
            <text class="mc-desc">提交成功后进入本详情页，门店可查看后续状态。</text>
          </view>
          <view class="mc-icon" :style="{ background: statusCfg.color + '18', color: statusCfg.color }">📋</view>
        </view>

        <!-- 多物料明细 / 编辑模式 -->
        <template v-if="report.items && report.items.length > 0">
          <!-- 查看模式 -->
          <view v-if="!editing" class="item-list-card">
            <text class="ilc-title">报损明细</text>
            <view v-for="item in report.items" :key="item.id" class="ilc-row">
              <view class="ilc-info">
                <text class="ilc-name">{{ item.materialName }}</text>
                <text class="ilc-spec" v-if="item.spec">{{ item.spec }}</text>
                <text class="ilc-type">{{ item.lossObject === 'semi_finished' ? '半成品' : '成品' }}</text>
              </view>
              <view class="ilc-qty">
                <text>净重 {{ item.netWeight }}g</text>
              </view>
              <view class="ilc-amt" v-if="item.totalAmount">¥{{ item.totalAmount }}</view>
            </view>
            <view class="ilc-total" v-if="report.totalAmount">合计 ¥{{ report.totalAmount }}</view>
          </view>

        </template>

        <!-- 单物料信息（到货验收/旧数据） -->
        <view v-else class="mc-grid">
          <view class="mg-item"><text class="mgl">报损类型</text><text class="mgv">{{ report.lossType === 'arrival' ? '到货验收报损' : '日常报损' }}</text></view>
          <view class="mg-item"><text class="mgl">报损对象</text><text class="mgv">{{ report.lossObject === 'semi_finished' ? '半成品' : '成品' }}</text></view>
          <view class="mg-item"><text class="mgl">物品</text><text class="mgv">{{ report.materialName }}</text></view>
          <view class="mg-item"><text class="mgl">数量/重量</text><text class="mgv">{{ report.netWeight ? `净重 ${report.netWeight}g` : report.inputQty ? `${report.inputQty} ${report.inputUnit || ''}` : '--' }}</text></view>
          <view class="mg-item" v-if="report.unitPrice"><text class="mgl">单价</text><text class="mgv">¥{{ report.unitPrice }}</text></view>
          <view class="mg-item" v-if="report.totalAmount"><text class="mgl">金额</text><text class="mgv" style="color:#2F8F57;font-weight:700">¥{{ report.totalAmount }}</text></view>
          <view class="mg-item"><text class="mgl">原因</text><text class="mgv">{{ report.reason || '--' }}</text></view>
          <view class="mg-item" v-if="report.lossType === 'arrival'"><text class="mgl">企迈单号</text><text class="mgv">{{ report.qimaiOrderNo || '--' }}</text></view>
          <view class="mg-item"><text class="mgl">登记人</text><text class="mgv">{{ report.handlerName || report.submittedBy || '--' }}</text></view>
          <view class="mg-item"><text class="mgl">创建时间</text><text class="mgv">{{ fmtTime(report.createdAt) }}</text></view>
          <view class="mg-item" v-if="report.updatedAt && report.updatedAt !== report.createdAt"><text class="mgl">更新时间</text><text class="mgv">{{ fmtTime(report.updatedAt) }}</text></view>
        </view>
      </view>

      <!-- 处理流程 -->
      <view class="card" v-if="logs.length">
        <text class="card-title">处理流程</text>
        <view class="timeline">
          <view v-for="(l, idx) in logs" :key="idx" class="tl-item">
            <view class="tl-dot" :class="{ 'tl-dot-end': idx === logs.length-1 }"></view>
            <view class="tl-body">
              <view class="tl-head"><text class="tl-operator">{{ l.operator || '--' }}</text><text class="tl-time">{{ l.createdAt }}</text></view>
              <text class="tl-action">{{ actionLabel(l.action) }}</text>
              <text class="tl-remark" v-if="l.remark">{{ l.remark }}</text>
              <view class="tl-attachments" v-if="l.attachmentUrl">
                <view v-for="(u, ai) in l.attachmentUrl.split(',').filter(Boolean)" :key="ai" class="tl-att-item" @click="playAttMedia(u)">
                  <view v-if="isVideo(u)" class="tl-att-video-thumb"><text class="tl-att-play">▶</text></view>
                  <image v-else :src="u" mode="aspectFill" class="tl-att-media" />
                </view>
              </view>
            </view>
          </view>
          <view v-if="nextStep" class="tl-item tl-pending">
            <view class="tl-dot tl-dot-pending"></view><view class="tl-body"><text class="tl-action-pending">{{ nextStep }}</text></view>
          </view>
        </view>
      </view>

      <!-- 凭证与备注 -->
      <view class="card" v-if="mediaList.length || report.remark || report.rejectReason">
        <text class="card-title">凭证与备注</text>
        <text class="rm-text" v-if="report.remark">{{ report.remark }}</text>
        <view v-if="report.rejectReason" class="reject-block">
          <text class="reject-label">拒绝原因</text><text class="reject-text">{{ report.rejectReason }}</text>
          <text class="reject-att" v-if="rejectAttCount > 0">附件：{{ rejectAttCount }} 个（图片/视频）</text>
        </view>
        <view class="photo-grid" v-if="mediaList.length">
          <view v-for="(item, idx) in mediaList" :key="idx" class="media-item-detail" @click="playMedia(item)">
            <template v-if="item.isVideo"><video :src="item.url" class="media-video-thumb" /></template>
            <image v-else :src="item.url" mode="aspectFill" class="photo-item" />
          </view>
        </view>
      </view>

      <view v-if="showImgPreview" class="img-overlay" @click="showImgPreview = false">
        <image :src="previewImgUrl" mode="aspectFit" class="img-full" @click.stop />
        <view class="img-close" @click="showImgPreview = false">✕</view>
      </view>

      <!-- 编辑/删除按钮（日常报损，固定底部） -->
      <view v-if="isDaily && !editing" class="edit-bar">
        <view class="eb-btn eb-edit" @click="startEdit">编辑</view>
        <view class="eb-btn eb-delete" @click="doDelete">删除</view>
      </view>

      <view v-if="report.status === 'pending_approval' && canApprove" class="approval-bar">
        <view class="ab-reject" @click="doRejectApproval">拒绝</view>
        <view class="ab-approve" @click="doApprove">{{ actionLoading ? '处理中...' : '通过' }}</view>
      </view>
      <view v-if="report.status === 'confirmed_resend'" class="approval-bar">
        <template v-if="!receiving">
          <view class="ab-reject" @click="receiving = true">{{ report.isFruitVeg ? '未收到' : '未收到货' }}</view>
          <view class="ab-approve" @click="doReceive">{{ actionLoading ? '处理中...' : (report.isFruitVeg ? '已收到' : '已收货') }}</view>
        </template>
        <template v-else>
          <input class="receive-input" v-model="receiveRemark" placeholder="请填写原因/备注" />
          <view class="ab-reject" @click="doNotReceive">确认</view>
          <view class="ab-cancel" @click="receiving = false; receiveRemark = ''">取消</view>
        </template>
      </view>
      <view v-if="report.status === 'rejected' && report.lossType === 'arrival'" class="approval-bar">
        <view class="ab-reject" @click="doCloseLoss">关闭</view>
        <view class="ab-approve" @click="doResubmit">重新提交</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$s:#fff;$bg:#F7F8F6;$w:#E58A2D;$d:#E05A47;
.page{min-height:100vh;background:$bg}.content{padding:24rpx;padding-bottom:160rpx}
.card{background:$s;border-radius:16rpx;padding:24rpx;margin-bottom:16rpx;border:1px solid $b}
.card-title{display:block;font-size:30rpx;font-weight:700;color:$t1;margin-bottom:16rpx}
.main-card{}
.mc-top{display:flex;justify-content:space-between;gap:16rpx;margin-bottom:24rpx}
.mc-left{flex:1}
.mc-tag{display:inline-block;font-size:22rpx;padding:6rpx 20rpx;border-radius:999rpx;margin-bottom:12rpx}
.mc-tag.s-green{background:$ps;color:$p}.mc-tag.s-orange{background:#FFF8EE;color:$w}.mc-tag.s-red{background:#FFF4F2;color:$d}.mc-tag.s-gray{background:$b;color:$t3}
.mc-title{display:block;font-size:36rpx;font-weight:700;color:$t1}.mc-desc{display:block;font-size:26rpx;color:$t2;margin-top:4rpx}
.mc-icon{width:96rpx;height:96rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:44rpx;flex-shrink:0}
.mc-grid{display:grid;grid-template-columns:1fr 1fr;gap:16rpx 24rpx;padding:20rpx;background:#FAFBF9;border-radius:14rpx}
.mg-item{}.mgl{font-size:24rpx;color:$t3}.mgv{display:block;margin-top:6rpx;font-size:28rpx;font-weight:600;color:$t1}

// Info card
.info-card{padding:20rpx 24rpx}.info-grid{display:grid;grid-template-columns:1fr 1fr;gap:12rpx 24rpx}.ig-item{}.igl{font-size:24rpx;color:$t3}.igv{display:block;margin-top:4rpx;font-size:26rpx;font-weight:600;color:$t1}

// Item list (view mode)
.item-list-card{padding:20rpx;background:#FAFBF9;border-radius:14rpx}.ilc-title{display:block;font-size:24rpx;color:$t3;margin-bottom:12rpx}
.ilc-row{display:flex;align-items:center;gap:12rpx;padding:12rpx 0;border-bottom:1px solid #EDEFEC}.ilc-row:last-child{border-bottom:0}
.ilc-info{flex:1;min-width:0;display:flex;flex-direction:column;gap:2rpx}.ilc-name{font-size:26rpx;font-weight:600;color:$t1}.ilc-spec{font-size:22rpx;color:$t3}.ilc-type{font-size:20rpx;color:$p;padding:2rpx 10rpx;border-radius:4rpx;background:$ps;align-self:flex-start;margin-top:2rpx}
.ilc-qty{font-size:26rpx;color:$t1;white-space:nowrap}.ilc-amt{font-size:24rpx;color:$p;font-weight:600;white-space:nowrap}
.ilc-total{text-align:right;padding-top:12rpx;font-size:28rpx;font-weight:700;color:$p}
.ilc-meta-row{display:flex;gap:24rpx;padding-top:12rpx;border-top:1px solid #EDEFEC;margin-top:12rpx}.ilc-time{font-size:22rpx;color:$t3}

// Edit bar (fixed bottom)
.edit-bar{position:fixed;bottom:0;left:0;right:0;z-index:10;display:flex;gap:16rpx;padding:16rpx 24rpx;padding-bottom:calc(env(safe-area-inset-bottom) + 16rpx);background:$s;box-shadow:0 -4rpx 20rpx rgba(0,0,0,.06)}
.eb-btn{flex:1;height:80rpx;border-radius:12rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600}
.eb-edit{background:$p;color:#fff}.eb-delete{background:#FEF0EF;color:$d;border:1.5px solid $d}

// Edit mode
.edit-mode{display:flex;flex-direction:column;gap:20rpx}
.el-reason,.el-remark{}.el-label{display:block;font-size:24rpx;color:$t2;margin-bottom:8rpx}.el-ta{width:100%;height:80rpx;padding:8rpx 12rpx;border:1px solid $b;border-radius:8rpx;font-size:24rpx;background:#FAFBF9;box-sizing:border-box}
.reason-pills{display:flex;gap:10rpx;white-space:nowrap}.rp-pill{flex-shrink:0;padding:8rpx 24rpx;border-radius:999rpx;font-size:24rpx;background:$s;color:$t2;border:1.5px solid $b}.rp-pill.on{background:$ps;color:$p;border-color:$p}

.el-item{padding:16rpx 0;border-bottom:1px solid #EDEFEC;display:flex;flex-direction:column;gap:12rpx}
.el-item-head{display:flex;align-items:center;gap:8rpx}.el-name{font-size:26rpx;font-weight:600;color:$t1}.el-spec{font-size:22rpx;color:$t3}
.el-container-scroll{white-space:nowrap}.el-container-pills{display:flex;gap:10rpx}.el-cpill{flex-shrink:0;padding:12rpx 24rpx;border-radius:999rpx;border:1.5px solid $b;font-size:24rpx;color:$t2;background:$s}.el-cpill.on{border-color:$p;color:$p;background:$ps}
.el-gross-row{display:flex;align-items:center;gap:16rpx}.el-gross-input{flex:1;position:relative}.el-fii{width:100%;height:72rpx;padding:0 16rpx;border:1px solid $b;border-radius:10rpx;font-size:26rpx;background:#FAFBF9;box-sizing:border-box}.el-suffix{position:absolute;right:16rpx;top:50%;transform:translateY(-50%);font-size:24rpx;color:$t2}.el-net{font-size:24rpx;font-weight:600;color:$p;white-space:nowrap}

.el-btns{display:flex;gap:16rpx}.el-btn-cancel{flex:1;height:76rpx;border-radius:12rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:600;background:#FAFBF9;color:$t2;border:1px solid $b}.el-btn-save{flex:1;height:76rpx;border-radius:12rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:700;background:$p;color:#fff}

// Photo, timeline etc
.rm-text{font-size:26rpx;color:$t2;margin-bottom:32rpx;line-height:1.5}
.reject-block{background:#FEF0EF;border-radius:12rpx;padding:16rpx 20rpx;margin-bottom:24rpx}.reject-label{display:block;font-size:22rpx;color:#E05A47;margin-bottom:6rpx}.reject-text{display:block;font-size:26rpx;color:#E05A47}.reject-att{display:block;font-size:22rpx;color:#E05A47;margin-top:8rpx;opacity:.8}
.photo-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12rpx}.photo-item{width:100%;height:200rpx;border-radius:12rpx;border:1px solid $b;background:#FAFBF9}.media-item-detail{position:relative}.media-video-thumb{width:100%;height:200rpx;border-radius:12rpx;border:1px solid $b;background:linear-gradient(135deg,#3A3F3C,#1F2421);display:flex;align-items:center;justify-content:center}.mvt-play{font-size:48rpx;color:#fff;opacity:.9}.media-del{position:absolute;top:6rpx;right:6rpx;width:40rpx;height:40rpx;border-radius:50%;background:rgba(0,0,0,.5);color:#fff;display:flex;align-items:center;justify-content:center;font-size:24rpx;z-index:2}
.img-overlay{position:fixed;inset:0;z-index:300;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.92)}.img-full{width:100%;height:100%}.img-close{position:fixed;top:80rpx;right:32rpx;z-index:10;width:64rpx;height:64rpx;border-radius:999rpx;background:rgba(0,0,0,.5);color:#fff;font-size:32rpx;display:flex;align-items:center;justify-content:center}

.approval-bar{position:fixed;bottom:0;left:0;right:0;display:flex;gap:16rpx;padding:20rpx 32rpx;padding-bottom:calc(20rpx + env(safe-area-inset-bottom));background:#fff;border-top:1px solid $b;z-index:10}
.ab-approve,.ab-reject{flex:1;height:88rpx;border-radius:12rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}.ab-approve{background:$p;color:#fff}.ab-reject{background:#FFF4F2;color:$d}.ab-cancel{flex-shrink:0;width:120rpx;height:88rpx;border-radius:12rpx;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:600;background:#FAFBF9;color:$t2;border:1px solid $b}
.receive-input{flex:1;height:88rpx;padding:0 16rpx;border:1px solid $b;border-radius:12rpx;font-size:26rpx;background:#FAFBF9;box-sizing:border-box}

.timeline{padding-left:16rpx}.tl-item{display:flex;gap:16rpx;padding-bottom:8rpx}.tl-dot{width:16rpx;height:16rpx;border-radius:50%;background:$p;margin-top:6rpx;flex-shrink:0}.tl-dot-end{background:$t3}.tl-body{flex:1;padding-bottom:16rpx;border-left:2rpx solid $b;padding-left:16rpx}.tl-item:last-child .tl-body{border-left-color:transparent}.tl-head{display:flex;justify-content:space-between}.tl-operator{font-size:26rpx;font-weight:600;color:$t1}.tl-time{font-size:22rpx;color:$t3}.tl-action{font-size:24rpx;color:$p;margin-top:4rpx}.tl-remark{display:block;margin-top:8rpx;padding:10rpx 14rpx;border-radius:8rpx;background:#FAFBF9;border:1px solid $b;font-size:22rpx;color:$t2;line-height:1.5}.tl-dot-pending{background:$b;border:2rpx dashed $t3}.tl-pending .tl-body{border-left-style:dashed}.tl-action-pending{font-size:24rpx;color:$t3;margin-top:4rpx}
.tl-attachments{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:8rpx}
.tl-att-item{width:120rpx;height:120rpx;border-radius:8rpx;overflow:hidden;border:1px solid $b}
.tl-att-media{width:100%;height:100%;object-fit:cover}
.tl-att-video-thumb{width:100%;height:100%;border-radius:8rpx;background:linear-gradient(135deg,#3A3F3C,#1F2421);display:flex;align-items:center;justify-content:center}
.tl-att-play{font-size:36rpx;color:#fff;opacity:.9}
</style>
