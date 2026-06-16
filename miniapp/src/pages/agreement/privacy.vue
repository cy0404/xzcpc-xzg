<script setup lang="ts">
// 隐私政策 — 纯展示页，无需逻辑
</script>

<template>
  <scroll-view class="page" scroll-y :show-scrollbar="false">
    <view class="content">
      <text class="title">象掌柜隐私政策</text>
      <text class="meta">更新日期：2026年6月16日&emsp;生效日期：2026年6月16日</text>

      <text class="body">象子茶铺（以下简称"我们"）深知个人信息对你的重要性，我们将按照法律法规的规定，保护你的个人信息安全。本隐私政策旨在向你说明我们如何收集、使用、存储和保护你的个人信息。请你仔细阅读本隐私政策，在确认充分理解并同意后再使用"象掌柜"小程序（以下简称"本小程序"）。</text>

      <text class="section">一、我们收集哪些信息</text>
      <text class="body">在为你提供门店经营工具服务的过程中，我们可能收集以下类型的个人信息：</text>

      <text class="sub">1. 微信头像和昵称</text>
      <text class="body">通过微信授权接口（getUserProfile）获取，用于在盘点任务提交记录中展示操作人身份，便于门店内部追溯数据录入来源。</text>

      <text class="sub">2. 手机号</text>
      <text class="body">由你主动填写。我们使用手机号匹配你在门店中的员工身份或老板身份。手机号是你使用本小程序服务的基础身份标识。</text>

      <text class="sub">3. 姓名</text>
      <text class="body">由你主动填写。用于身份核验、门店员工管理和盘点任务记录，确保门店人员信息的真实性和可追溯性。</text>

      <text class="sub">4. 生日</text>
      <text class="body">由你主动填写（仅在老板绑定门店流程中需要）。用于结合手机号自动匹配你在系统内预留的门店信息，实现快速绑定。</text>

      <text class="sub">5. 门店信息</text>
      <text class="body">由系统根据你的身份验证结果自动关联，包括你所属门店的名称和你的岗位角色。用于为你分配对应的功能权限和工作台。</text>

      <text class="sub">6. 盘点业务数据</text>
      <text class="body">你在使用盘点功能时录入的物料数量、备注等信息。用于生成盘点汇总报告和更新门店物料清单。</text>

      <text class="section">二、我们如何使用信息</text>
      <text class="body">我们收集个人信息的目的仅限于为你提供本小程序的核心服务功能，具体用途如下：</text>

      <view class="table">
        <view class="tr">
          <text class="th">信息类型</text>
          <text class="th">使用目的</text>
          <text class="th">使用方式</text>
        </view>
        <view class="tr">
          <text class="td">微信头像/昵称</text>
          <text class="td">盘点操作人展示</text>
          <text class="td">在盘点任务提交记录中显示操作人</text>
        </view>
        <view class="tr">
          <text class="td">手机号</text>
          <text class="td">身份匹配与登录</text>
          <text class="td">匹配门店员工/老板身份，作为账户标识</text>
        </view>
        <view class="tr">
          <text class="td">姓名</text>
          <text class="td">身份核验与员工管理</text>
          <text class="td">门店人员登记、审批与信息管理</text>
        </view>
        <view class="tr">
          <text class="td">生日</text>
          <text class="td">门店自动匹配</text>
          <text class="td">与手机号共同匹配系统预留门店信息</text>
        </view>
        <view class="tr">
          <text class="td">门店信息</text>
          <text class="td">权限分配</text>
          <text class="td">分配对应门店的功能权限和工作台</text>
        </view>
        <view class="tr">
          <text class="td">盘点业务数据</text>
          <text class="td">生成盘点报告</text>
          <text class="td">汇总统计、更新门店物料清单</text>
        </view>
      </view>

      <text class="body">我们不会将你的个人信息用于上述目的之外的任何用途，除非获得你的明确同意或法律法规另有规定。</text>

      <text class="section">三、我们如何存储和保护信息</text>
      <text class="body">1. 你的个人信息存储于我们的安全服务器上，采用加密传输（HTTPS）和数据库访问控制等安全措施。</text>
      <text class="body">2. 我们仅在为你提供服务的必要期间内保留你的个人信息。当你离职或账户被注销后，我们将依法对相关信息进行删除或匿名化处理。</text>
      <text class="body">3. 我们采取权限分级管理，只有经授权的人员才能访问个人信息，且仅限完成其工作职责所必需的范围。</text>
      <text class="body">4. 若不慎发生个人信息安全事件，我们将按照法律法规的要求及时告知你并采取补救措施。</text>

      <text class="section">四、信息的共享与披露</text>
      <text class="body">1. 我们不会将你的个人信息出售、出租或以其他形式提供给任何第三方。</text>
      <text class="body">2. 你的姓名、手机号、岗位信息在门店内部（同门店的店长、老板）可见，用于员工管理。盘点提交记录中的微信昵称在门店内可见。</text>
      <text class="body">3. 在以下情形中，我们可能依法共享你的个人信息：</text>
      <text class="body" style="padding-left:2em">（1）获得你的明确同意或授权；</text>
      <text class="body" style="padding-left:2em">（2）根据法律法规或行政、司法机关的强制性要求；</text>
      <text class="body" style="padding-left:2em">（3）为维护我们或他人的合法权益所必需。</text>

      <text class="section">五、你的权利</text>
      <text class="body">你对自己的个人信息享有以下权利：</text>
      <text class="body">1. 查阅权：你可以联系门店管理员或总部，申请查阅我们持有的你的个人信息。</text>
      <text class="body">2. 更正权：如果你发现个人信息有误，可联系门店管理员进行更正。</text>
      <text class="body">3. 删除权：当你离职或不再使用本小程序时，可要求我们删除你的个人信息。法律法规另有规定的除外。</text>
      <text class="body">4. 撤回同意：你可以在微信小程序设置中取消授权，但撤回后可能无法正常使用本小程序的部分功能。</text>

      <text class="section">六、未成年人保护</text>
      <text class="body">本小程序面向门店员工和企业管理人员，主要供已满16周岁的就业人员使用。我们不会主动收集未满14周岁未成年人的个人信息。如果你发现我们在不知情的情况下收集了未成年人的信息，请立即联系我们处理。</text>

      <text class="section">七、隐私政策的更新</text>
      <text class="body">我们可能根据业务发展或法律法规的变化适时更新本隐私政策。重大变更时，我们将在小程序内以弹窗或显著通知的方式告知你。请你定期查阅本隐私政策，以了解最新的信息保护措施。</text>

      <text class="section">八、联系方式</text>
      <text class="body">如你对本隐私政策有任何疑问、意见或建议，请通过以下方式联系我们：</text>
      <text class="body">联系方：象子茶铺总部</text>
      <text class="body">你也可以通过门店管理渠道向你的门店负责人反馈，由门店负责人转达至总部。</text>

      <view class="spacer" />
    </view>
  </scroll-view>
</template>

<style lang="scss" scoped>
.page {
  height: 100vh;
  background: #F7F8F6;
}

.content {
  padding: 32rpx 32rpx 80rpx;
}

.title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #1F2421;
  line-height: 56rpx;
  text-align: center;
  margin-bottom: 12rpx;
}

.meta {
  display: block;
  font-size: 24rpx;
  color: #98A19C;
  text-align: center;
  margin-bottom: 40rpx;
}

.section {
  display: block;
  margin-top: 32rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #1F2421;
  line-height: 44rpx;
}

.sub {
  display: block;
  margin-top: 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
  line-height: 44rpx;
}

.body {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  color: #444;
  line-height: 44rpx;
}

// 信息使用说明表
.table {
  margin-top: 16rpx;
  border-radius: 12rpx;
  overflow: hidden;
  border: 1px solid #E8ECE9;
}

.tr {
  display: flex;
  border-bottom: 1px solid #E8ECE9;

  &:last-child {
    border-bottom: 0;
  }
}

.th {
  flex: 1;
  padding: 16rpx 12rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #66706A;
  background: #FAFBF9;
  text-align: center;
  line-height: 36rpx;
}

.td {
  flex: 1;
  padding: 16rpx 12rpx;
  font-size: 24rpx;
  color: #444;
  background: #fff;
  text-align: center;
  line-height: 36rpx;
}

.spacer {
  height: 1rpx;
}
</style>
