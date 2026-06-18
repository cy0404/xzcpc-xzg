import { defineStore } from 'pinia'
import { ref } from 'vue'
import { request } from '@/utils/request'

export const useTaskStore = defineStore('task', () => {
  const currentTasks = ref<any[]>([])
  const historyTasks = ref<any[]>([])
  const loading = ref(false)

  async function fetchTaskList() {
    loading.value = true
    try {
      const data: any = await request({ url: '/tasks', showLoading: false })
      if (data) {
        currentTasks.value = data.current || []
        historyTasks.value = data.history || []
      }
    } catch {
      // 401 等错误已在 request 层处理（toast + 跳转）
    } finally {
      loading.value = false
    }
  }

  function reset() {
    currentTasks.value = []
    historyTasks.value = []
    loading.value = false
  }

  return { currentTasks, historyTasks, loading, fetchTaskList, reset }
})
