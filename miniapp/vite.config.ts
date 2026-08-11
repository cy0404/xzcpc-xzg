import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// 切换环境：true=生产 false=本地
const IS_PROD = true
const PROXY_TARGET = IS_PROD ? 'https://www.xzcpc-9pd.top' : 'http://192.168.0.4:30261'

export default defineConfig({
  plugins: [uni()],
  css: {
    preprocessorOptions: {
      scss: {
        silenceDeprecations: ['legacy-js-api', 'global-builtin'],
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: PROXY_TARGET,
        changeOrigin: true,
      },
    },
  },
})
