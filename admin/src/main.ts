import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import zhCN from 'ant-design-vue/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'ant-design-vue/dist/reset.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'

dayjs.locale('zh-cn')

const app = createApp(App)
app.use(Antd, { locale: zhCN })
app.use(router)
app.mount('#app')
