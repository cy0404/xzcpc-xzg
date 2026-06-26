import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

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
        target: 'https://www.xzcpc-9pd.top',
        changeOrigin: true,
      },
    },
  },
})
