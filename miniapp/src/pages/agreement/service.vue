<script setup lang="ts">
// 用户服务协议 — 纯展示页，无需逻辑
</script>

<template>
  <scroll-view class="page" scroll-y :show-scrollbar="false">
    <view class="content">
      <text class="title">象掌柜用户服务协议</text>
      <text class="meta">更新日期：2026年6月16日&emsp;生效日期：2026年6月16日</text>

      <text class="section">一、协议说明</text>
      <text class="body">欢迎使用"象掌柜"小程序（以下简称"本小程序"）。本小程序由象子茶铺（以下简称"我们"）提供，旨在为门店员工及管理人员提供盘点数据录入、支出登记、员工管理等门店经营工具服务。</text>
      <text class="body">请你务必审慎阅读、充分理解本协议各条款内容。当你勾选"已阅读并同意"并开始使用本小程序，即表示你已阅读、理解并同意接受本协议的全部内容。如果你不同意本协议的任何条款，请停止使用本小程序。</text>

      <text class="section">二、服务内容</text>
      <text class="body">本小程序向门店员工及管理人员提供以下服务：</text>
      <text class="body">1. 盘点任务管理：接收月盘任务、按分区录入物料数量、查看盘点汇总与结果。</text>
      <text class="body">2. 支出登记：记录门店日常支出信息。</text>
      <text class="body">3. 员工管理：门店人员信息的登记、查看与编辑。</text>
      <text class="body">4. 门店总览：老板/店长查看门店整体经营概况。</text>
      <text class="body">实际可用功能以你的门店权限和岗位设置为准。</text>

      <text class="section">三、用户注册与账户</text>
      <text class="body">1. 你通过微信授权登录后，系统将根据你在门店的登记信息自动匹配身份。未登记的用户无法使用本小程序。</text>
      <text class="body">2. 你应提供真实、准确的个人信息，包括姓名、手机号等。因信息不实导致的任何后果由你自行承担。</text>
      <text class="body">3. 你的账户仅限本人使用，不得转让、出借或授权第三方使用。</text>
      <text class="body">4. 你应对你账户下的所有操作行为负责。</text>

      <text class="section">四、用户行为规范</text>
      <text class="body">你在使用本小程序过程中，应遵守以下规范：</text>
      <text class="body">1. 遵守中华人民共和国相关法律法规，不得利用本小程序从事违法违规活动。</text>
      <text class="body">2. 不得干扰、破坏本小程序的正常运行，不得利用技术手段恶意攻击、入侵本小程序系统。</text>
      <text class="body">3. 不得录入虚假盘点数据，不得恶意篡改门店信息。</text>
      <text class="body">4. 不得冒用他人身份或越权操作。</text>
      <text class="body">如你违反上述规范，我们有权暂停或终止向你提供服务，并保留追究法律责任的权利。</text>

      <text class="section">五、服务变更与终止</text>
      <text class="body">1. 我们可能根据业务需要不时更新或调整服务内容，重大变更将通过小程序内通知的方式告知你。</text>
      <text class="body">2. 如你离职或不再属于任何门店，你的账户权限将被相应调整或终止。</text>
      <text class="body">3. 我们有权在下列情况下终止向你提供服务：你违反本协议约定；应门店管理者要求；应法律法规要求。</text>

      <text class="section">六、免责声明</text>
      <text class="body">1. 本小程序按"现状"提供服务，我们不对服务的及时性、安全性、准确性做出超出法律要求的额外保证。</text>
      <text class="body">2. 因不可抗力、系统维护、网络故障等原因导致的服务中断，我们不承担责任，但将尽力及时恢复。</text>
      <text class="body">3. 因你自身原因（如网络不畅、设备故障、操作失误）导致的数据丢失或录入错误，由你自行负责。</text>

      <text class="section">七、知识产权</text>
      <text class="body">本小程序的所有权、运营权和知识产权归象子茶铺所有。未经书面许可，任何主体不得复制、修改、反编译或以其他方式侵犯本小程序的知识产权。</text>

      <text class="section">八、法律适用与争议解决</text>
      <text class="body">1. 本协议的订立、执行和解释均适用中华人民共和国法律。</text>
      <text class="body">2. 因本协议引起的或与本协议有关的任何争议，双方应友好协商解决；协商不成的，任何一方均可向有管辖权的人民法院提起诉讼。</text>

      <text class="section">九、联系方式</text>
      <text class="body">如你对本协议有任何疑问，可通过门店管理渠道或联系象子茶铺总部进行反馈。</text>

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

.body {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  color: #444;
  line-height: 44rpx;
  text-indent: 1em;
}

.spacer {
  height: 1rpx;
}
</style>
