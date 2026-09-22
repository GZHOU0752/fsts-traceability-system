import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export function useAsyncAction() {
  const loading = ref(false)
  async function run<T>(action: () => Promise<T>, success?: string) { loading.value = true; try { const result = await action(); if (success) ElMessage.success(success); return result } catch (error) { ElMessage.error(error instanceof Error ? error.message : '操作失败'); throw error } finally { loading.value = false } }
  return { loading, run }
}
