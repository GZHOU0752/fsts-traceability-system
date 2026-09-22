import { ref } from 'vue'
import type { PageResult } from '@/types/api'

export function usePagedQuery<T, Q extends { current?: number; size?: number }>(loader: (query: Q) => Promise<PageResult<T>>, initialQuery: Q) {
  const query = ref({ ...initialQuery }) as { value: Q }
  const result = ref<PageResult<T>>({ records: [], total: 0, size: initialQuery.size ?? 10, current: initialQuery.current ?? 1, pages: 0 })
  const loading = ref(false)
  async function load() { loading.value = true; try { result.value = await loader(query.value) } finally { loading.value = false } }
  function reset() { query.value = { ...initialQuery }; return load() }
  return { query, result, loading, load, reset }
}
