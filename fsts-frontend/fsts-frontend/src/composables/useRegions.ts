import { computed, onMounted, ref, watch } from 'vue'
import { useDictionaryStore } from '@/stores/dictionary'
import type { Region } from '@/types/api'

export function useRegions(provinceCode: () => string) {
  const dictionary = useDictionaryStore()
  const cities = ref<Region[]>([])
  onMounted(() => dictionary.loadProvinces())
  watch(provinceCode, async (value) => { cities.value = value ? await dictionary.loadCities(value) : [] })
  return { provinces: computed(() => dictionary.provinces), cities }
}
