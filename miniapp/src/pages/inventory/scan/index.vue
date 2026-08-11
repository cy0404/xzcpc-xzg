<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { scanBarcode, supplementBarcode } from '@/api/inventory'
import { addMaterial } from '@/api/material'
import { materialDisplayName } from '@/utils/formatter'

const taskId = ref(0)
const zoneId = ref(0)
const zoneName = ref('')
const scanning = ref(false)
const loading = ref(false)
const result = ref<any>(null)
const showSupplement = ref(false)
const supplementName = ref('')
const supplementBarcode = ref('')

onLoad((options: any) => {
  taskId.value = Number(options.taskId || 0)
  zoneId.value = Number(options.zoneId || 0)
  zoneName.value = options.zoneName ? decodeURIComponent(options.zoneName) : ''
  uni.setNavigationBarTitle({ title: '扫码盘点' })
  startScan()
})

function startScan() {
  scanning.value = true; result.value = null
  uni.scanCode({
    scanType: ['barCode', 'qrCode'],
    success: (res: any) => handleScanResult(res.result || res.code || ''),
    fail: () => { scanning.value = false }
  })
}

async function handleScanResult(barcode: string) {
  scanning.value = false; loading.value = true
  try {
    const data = await scanBarcode(barcode, taskId.value || undefined, zoneId.value || undefined)
    data._barcode = barcode; result.value = data
  } catch (e: any) {
    result.value = { found: false, _barcode: barcode, suggestedAction: 'supplement' }
  } finally { loading.value = false }
}

async function handleAddToZone() {
  if (!result.value) return; loading.value = true
  try {
    await addMaterial(taskId.value, zoneId.value, result.value.materialId)
    uni.showToast({ title: '已加入当前分区', icon: 'success' }); uni.navigateBack()
  } catch { uni.showToast({ title: '添加失败', icon: 'none' }) }
  finally { loading.value = false }
}

function handleGoEntry() {
  const m = result.value; if (!m) return
  uni.navigateTo({ url: `/pages/task/zone-entry/index?taskId=${taskId.value}&zoneId=${zoneId.value}&zoneName=${encodeURIComponent(zoneName.value)}&focusMaterialId=${encodeURIComponent(m.materialId)}&focusMaterialName=${encodeURIComponent(m.materialName)}&fromSearch=1` })
}

function openSupplement() {
  if (!result.value) return
  supplementBarcode.value = result.value._barcode || ''; supplementName.value = ''; showSupplement.value = true
}

async function submitSupplement() {
  if (loading.value) return
  loading.value = true
  try {
    await supplementBarcode(supplementBarcode.value, supplementName.value)
    uni.showToast({ title: '提交成功', icon: 'success' }); showSupplement.value = false; uni.navigateBack()
  } catch { uni.showToast({ title: '提交失败', icon: 'none' }) }
  finally { loading.value = false }
}
</script>

<template>
  <view class="page">
    <!-- 扫码中 -->
    <view v-if="scanning" class="scanning">
      <view class="scan-frame">
        <view class="scan-corner tl"></view><view class="scan-corner tr"></view>
        <view class="scan-corner bl"></view><view class="scan-corner br"></view>
        <view class="scan-line"></view>
      </view>
      <text class="scan-hint">将条码/二维码对准框内</text>
      <view class="scan-back" @click="uni.navigateBack()">返回</view>
    </view>

    <!-- 加载中 -->
    <view v-else-if="loading" class="center">
      <view class="spinner"></view><text class="load-text">识别中...</text>
    </view>

    <!-- 识别成功 -->
    <view v-else-if="result && result.found" class="result">
      <view class="card ok">
        <text class="ci">✅</text>
        <text class="cn">{{ materialDisplayName(result) }}</text>
        <text class="cs" v-if="result.spec">{{ result.spec }}</text>
        <text class="cm" v-if="result.category">分类：{{ result.category }}</text>
        <text class="cm">单位：{{ result.unit || '--' }}</text>
        <view class="badges">
          <view v-if="result.alreadyEntered" class="badge warn">⚠ 当前分区已录入</view>
          <view v-else-if="result.notInCurrentZone" class="badge info">ℹ {{ result.inZoneName || '其他分区' }}</view>
          <view v-else class="badge ok">✓ 可录入</view>
        </view>
      </view>
      <view class="actions">
        <view v-if="result.alreadyEntered" class="btn" @click="handleGoEntry">查看已盘（{{ result.enteredQty ?? '--' }} {{ result.unit || '' }}）</view>
        <view v-else-if="result.notInCurrentZone" class="btn primary" @click="handleAddToZone">加入当前分区</view>
        <view v-else class="btn primary" @click="handleGoEntry">去录入数量</view>
        <view class="btn outline" @click="startScan">📷 继续扫码</view>
        <view class="back" @click="uni.navigateBack()">返回分区</view>
      </view>
    </view>

    <!-- 未识别 -->
    <view v-else-if="result && !result.found" class="result">
      <view class="card fail">
        <text class="ci">🔍</text>
        <text class="cn">未识别到物料</text>
        <text class="cb">条码：{{ result._barcode }}</text>
      </view>
      <view class="actions">
        <view class="btn outline" @click="startScan">📷 重新扫码</view>
        <view class="btn primary" @click="openSupplement">📝 补充条码信息</view>
        <view class="back" @click="uni.navigateBack()">返回分区</view>
      </view>
    </view>

    <!-- 空态 -->
    <view v-else class="center">
      <text class="empty-icon">📷</text>
      <text class="empty-text">点击按钮开始扫码</text>
      <view class="btn primary" style="margin-top:40rpx" @click="startScan">📷 开始扫码</view>
    </view>

    <!-- 补充条码弹窗 -->
    <view v-if="showSupplement" class="mask" @click="showSupplement=false">
      <view class="sheet" @click.stop>
        <view class="sh"></view>
        <view class="sheet-head"><text class="st">补充条码信息</text><text class="sx" @click="showSupplement=false">✕</text></view>
        <view class="sheet-body">
          <view class="fi"><text class="fil">条码</text><input class="fii" :value="supplementBarcode" disabled /></view>
          <view class="fi"><text class="fil">物料名称</text><input class="fii" v-model="supplementName" placeholder="如知道可填写" /></view>
        </view>
        <view class="sheet-btns">
          <view class="sb-cancel" @click="showSupplement=false">取消</view>
          <view class="sb-confirm" @click="submitSupplement">{{ loading?'提交中...':'提交' }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;$s:#fff;$bg:#F7F8F6;$d:#E05A47;
.page{min-height:100vh;background:$bg;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:48rpx 32rpx}
.center{display:flex;flex-direction:column;align-items:center;gap:20rpx;width:100%}
.empty-icon{font-size:96rpx}.empty-text{font-size:28rpx;color:$t2}
.scanning{display:flex;flex-direction:column;align-items:center;gap:40rpx}
.scan-frame{width:500rpx;height:500rpx;position:relative;border-radius:24rpx}
.scan-corner{position:absolute;width:48rpx;height:48rpx;border:6rpx solid $p}
.scan-corner.tl{top:0;left:0;border-right:none;border-bottom:none;border-radius:12rpx 0 0 0}
.scan-corner.tr{top:0;right:0;border-left:none;border-bottom:none;border-radius:0 12rpx 0 0}
.scan-corner.bl{bottom:0;left:0;border-right:none;border-top:none;border-radius:0 0 0 12rpx}
.scan-corner.br{bottom:0;right:0;border-left:none;border-top:none;border-radius:0 0 12rpx 0}
.scan-line{position:absolute;left:8%;top:0;width:84%;height:3rpx;background:$p;animation:scanLine 2s ease-in-out infinite}
@keyframes scanLine{0%{top:0}50%{top:calc(100% - 3rpx)}100%{top:0}}
.scan-hint{font-size:28rpx;color:$t2}
.scan-back{font-size:26rpx;color:$t3;padding:12rpx;margin-top:20rpx}
.spinner{width:60rpx;height:60rpx;border:4rpx solid $b;border-top-color:$p;border-radius:50%;animation:spin .8s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
.load-text{font-size:28rpx;color:$t2;margin-top:16rpx}
.result{width:100%;max-width:600rpx;display:flex;flex-direction:column;gap:32rpx}
.card{background:$s;border-radius:20rpx;padding:40rpx 32rpx;display:flex;flex-direction:column;align-items:center;gap:12rpx;border:2rpx solid $b}
.card.ok{border-color:$p}.card.fail{border-color:$d}
.ci{font-size:64rpx}.cn{font-size:34rpx;font-weight:700;color:$t1;text-align:center}.cs{font-size:26rpx;color:$t2}.cm{font-size:24rpx;color:$t3}.cb{font-size:24rpx;color:$t3;font-family:monospace}
.badges{display:flex;gap:12rpx;margin-top:8rpx}
.badge{padding:8rpx 20rpx;border-radius:999rpx;font-size:22rpx;font-weight:600}
.badge.warn{background:#FFF3E0;color:#E58A2D}.badge.info{background:#E3F0FF;color:#2D6FE5}.badge.ok{background:$ps;color:$p}
.actions{display:flex;flex-direction:column;gap:16rpx;width:100%}
.btn{width:100%;height:88rpx;border-radius:16rpx;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600;background:$ps;color:$p;border:2rpx solid $p}
.btn.primary{background:$p;color:#fff}.btn.outline{background:$s;border:2rpx solid $b;color:$t1}
.back{text-align:center;font-size:26rpx;color:$t3;padding:12rpx}
.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;max-height:80vh;border-radius:32rpx 32rpx 0 0;background:$s;display:flex;flex-direction:column}
.sh{width:96rpx;height:6rpx;border-radius:999rpx;background:$b;margin:20rpx auto}
.sheet-head{display:flex;justify-content:center;padding:8rpx 32rpx 20rpx;position:relative}
.st{font-size:36rpx;font-weight:700;color:$t1}.sx{position:absolute;right:32rpx;font-size:40rpx;color:$t2;padding:8rpx}
.sheet-body{padding:0 32rpx 32rpx}
.fi{margin-bottom:24rpx}.fil{display:block;font-size:26rpx;color:$t2;margin-bottom:8rpx}.fii{width:100%;height:80rpx;padding:0 20rpx;border:2rpx solid $b;border-radius:12rpx;font-size:28rpx;background:#FAFBF9;box-sizing:border-box}
.sheet-btns{display:grid;grid-template-columns:1fr 1fr;gap:24rpx;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);border-top:2rpx solid #EEF1EF}
.sb-cancel{height:96rpx;border-radius:16rpx;background:#FAFBF9;color:$t1;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:600;border:2rpx solid $b}
.sb-confirm{height:96rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:700}
</style>
