<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createExpense, updateExpense, fetchExpenseDetail, fetchExpenseItems, uploadVoucher } from '@/api/expense'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const isEdit = ref(false)
const editId = ref('')
const items = ref<any[]>([])
const itemId = ref('')
const itemName = ref('请选择支出项目')
const typeId = ref('')
const typeName = ref('')
const amount = ref('')
const remark = ref('')
const occurredDate = ref(new Date().toISOString().slice(0,10))
const handlerName = ref(userStore.employeeName || '')
const showItems = ref(false)
const saving = ref(false)
const voucherPath = ref('')

onLoad(async (q: any) => {
  items.value = await fetchExpenseItems()
  if (q?.expenseId) {
    isEdit.value = true
    editId.value = q.expenseId
    const d = await fetchExpenseDetail(q.expenseId) as any
    if (d) {
      typeId.value = d.typeId || ''; typeName.value = d.typeName || ''
      itemId.value = d.itemId || ''; itemName.value = d.itemName || d.typeName || '请选择支出项目'
      amount.value = String(d.amount || '')
      remark.value = d.remark || ''
      occurredDate.value = d.occurredDate || ''
      handlerName.value = d.handlerName || ''
      const vu = d.voucherUrl || ''
      voucherPath.value = (vu.startsWith('http') && !vu.includes('127.0.0.1') && !vu.includes('__tmp__') && !vu.includes('://tmp/') && !vu.includes('localhost')) ? vu : ''
    }
  }
})

function selectItem(item: any) { itemId.value = item.itemId; itemName.value = item.name; typeId.value = item.typeId; typeName.value = ''; showItems.value = false }
function onDateChange(e: any) { occurredDate.value = e.detail.value }
function chooseVoucher() {
  uni.chooseImage({ count: 1, success: (res: any) => { voucherPath.value = res.tempFilePaths[0] } })
}

async function submit() {
  if (saving.value) return
  if (!itemId.value) { uni.showToast({ title: '请选择支出项目', icon: 'none' }); return }
  const amt = parseFloat(amount.value)
  if (!amt || amt <= 0) { uni.showToast({ title: '请输入金额', icon: 'none' }); return }
  saving.value = true
  try {
    // 微信临时路径（http://tmp/...）或本地路径需要上传；已是服务器 URL 则直接使用
    const isTempPath = voucherPath.value && (
      !voucherPath.value.startsWith('http') ||
      voucherPath.value.includes('://tmp/') ||
      voucherPath.value.includes('__tmp__') ||
      voucherPath.value.includes('127.0.0.1') ||
      voucherPath.value.includes('localhost')
    )
    let uploadedUrl = ''
    if (voucherPath.value && isTempPath) {
      try {
        const res = await uploadVoucher(voucherPath.value)
        uploadedUrl = res.url
      } catch { uni.showToast({ title: '凭证上传失败', icon: 'none' }); saving.value = false; return }
    } else {
      uploadedUrl = voucherPath.value
    }
    const data = { typeId: typeId.value, typeName: typeName.value, itemId: itemId.value, itemName: itemName.value, amount: amt, occurredDate: occurredDate.value, handlerName: handlerName.value || '--', voucherUrl: uploadedUrl || undefined, remark: remark.value }
    if (isEdit.value) { await updateExpense(editId.value, data) }
    else { await createExpense(data) }
    uni.showToast({ title: isEdit.value ? '修改成功' : '登记成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } finally { saving.value = false }
}
</script>

<template>
  <view class="page">
    <view class="amount-input">
      <text class="amount-symbol">¥</text>
      <input class="amount-num" type="digit" v-model="amount" placeholder="0.00" :focus="true" />
    </view>

    <view class="card">
      <view class="row" @click="showItems=true"><text class="rk">支出项目</text><text class="rv" :class="{sel:!itemId}">{{ itemName }} ›</text></view>
      <picker mode="date" :value="occurredDate" @change="onDateChange">
        <view class="row"><text class="rk">日期</text><text class="rv">{{ occurredDate }} ›</text></view>
      </picker>
      <view class="row"><text class="rk">说明</text><input class="rv" v-model="remark" placeholder="选填" /></view>
    </view>

    <view class="card card-sm">
      <text class="card-label">凭证照片</text>
      <view class="voucher-area" @click="chooseVoucher">
        <image v-if="voucherPath" :src="voucherPath" class="voucher-img" mode="widthFix" @error="voucherPath = ''" />
        <view v-if="voucherPath" class="voucher-overlay"><text>📷 重新上传</text></view>
        <text v-else class="voucher-placeholder">📷 上传凭证</text>
      </view>
    </view>

    <view class="bottom"><view class="btn" @click="submit">{{ saving ? '提交中...' : (isEdit ? '保存修改' : '提交登记') }}</view></view>
  </view>

  <view v-if="showItems" class="mask" @click="showItems=false">
    <view class="sheet" @click.stop>
      <view class="sh"></view>
      <text class="st">选择支出项目</text>
      <view class="type-grid">
        <view v-for="item in items" :key="item.itemId" class="type-btn" :class="{on:itemId===item.itemId}" @click="selectItem(item)">
          <text>{{ item.name }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$bg:#F7F8F6;$s:#fff;$p:#2F8F57;$ps:#E7F4EB;$t1:#1F2421;$t2:#66706A;$t3:#98A19C;$b:#E8ECE9;
.page{min-height:100vh;background:$bg;padding:48rpx 32rpx 280rpx;box-sizing:border-box}
.amount-input{display:flex;align-items:center;justify-content:center;padding:24rpx 0 48rpx;margin-bottom:24rpx}
.amount-symbol{font-size:56rpx;font-weight:300;color:$p;margin-right:8rpx}
.amount-num{font-size:80rpx;font-weight:800;color:$t1;text-align:center;width:400rpx;height:100rpx;line-height:100rpx;border-bottom:4rpx solid $b;overflow:visible}
.card{background:$s;border-radius:20rpx;padding:28rpx;border:2rpx solid $b;margin-bottom:24rpx}.card-sm{padding:24rpx}
.card-label{display:block;font-size:26rpx;font-weight:600;color:$t3;margin-bottom:16rpx}
.row{display:flex;justify-content:space-between;align-items:center;padding:20rpx 0;border-bottom:2rpx solid #EEF1EF}.row:last-child{border:0}
.rk{font-size:28rpx;color:$t1}.rv{font-size:28rpx;color:$t1;text-align:right}.rv.sel{color:$t3}
.voucher-area{min-height:200rpx;border:2rpx dashed $b;border-radius:16rpx;display:flex;align-items:center;justify-content:center;background:#FAFBF9;overflow:hidden;position:relative}
.voucher-img{width:100%;display:block}.voucher-placeholder{font-size:28rpx;color:$t3;padding:60rpx 0}
.voucher-overlay{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.4);color:#fff;font-size:26rpx;opacity:0;transition:opacity .2s}
.voucher-area:active .voucher-overlay{opacity:1}
.bottom{position:fixed;left:0;right:0;bottom:0;z-index:10;padding:20rpx 32rpx calc(env(safe-area-inset-bottom) + 20rpx);background:$s;border-top:2rpx solid #EEF1EF}
.btn{width:100%;height:96rpx;border-radius:16rpx;background:$p;color:#fff;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:700}
.mask{position:fixed;inset:0;z-index:100;display:flex;align-items:flex-end;background:rgba(31,36,33,.4)}
.sheet{width:100%;max-height:70vh;border-radius:32rpx 32rpx 0 0;background:$s;padding:24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx)}
.sh{width:80rpx;height:6rpx;border-radius:999rpx;background:$b;margin:8rpx auto 20rpx}.st{display:block;font-size:34rpx;font-weight:700;color:$t1;margin-bottom:20rpx}
.type-grid{display:flex;flex-wrap:wrap;gap:16rpx;max-height:60vh;overflow-y:auto;padding:8rpx 0}
.type-btn{width:calc((100% - 32rpx) / 3);padding:18rpx 4rpx;border-radius:16rpx;background:#FAFBF9;border:2rpx solid $b;text-align:center;font-size:26rpx;color:$t1;display:flex;align-items:center;justify-content:center;min-height:64rpx;box-sizing:border-box}
.type-btn.on{background:$ps;border-color:$p;color:$p;font-weight:600}
</style>
