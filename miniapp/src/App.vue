<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'

onLaunch(() => {
  const userStore = useUserStore()
  userStore.checkLogin()
})
</script>

<style lang="scss">
@import './styles/theme.scss';

page {
  background-color: #F7F8F6;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 28rpx;
  color: #1F2421;
  box-sizing: border-box;
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;
}

// wot-design-uni 品牌色覆盖
page {
  --wot-color-theme: #2F8F57;
  --wot-button-primary-bg: #2F8F57;
  --wot-button-primary-color: #fff;
  --wot-progress-color: #2F8F57;
  --wot-tabs-nav-color: #2F8F57;
  --wot-tabs-nav-bg: #F7F8F6;
}
</style>
