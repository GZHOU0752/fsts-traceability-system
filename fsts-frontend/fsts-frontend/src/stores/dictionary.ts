import { ref } from 'vue'
import { defineStore } from 'pinia'
import { commonApi } from '@/api/common'
import type { DictItem, Region } from '@/types/api'

export const useDictionaryStore = defineStore('dictionary', () => {
  const provinces = ref<Region[]>([])
  const cityMap = ref<Record<string, Region[]>>({})
  const dictMap = ref<Record<string, DictItem[]>>({})

  async function loadProvinces() {
    if (!provinces.value.length) provinces.value = await commonApi.provinces()
    return provinces.value
  }
  async function loadCities(provinceCode: string) {
    if (!cityMap.value[provinceCode]) cityMap.value[provinceCode] = await commonApi.cities(provinceCode)
    return cityMap.value[provinceCode]
  }
  async function loadDict(typeCode: string) {
    if (!dictMap.value[typeCode]) dictMap.value[typeCode] = await commonApi.dicts(typeCode)
    return dictMap.value[typeCode]
  }
  const label = (typeCode: string, code: number | string | undefined) => dictMap.value[typeCode]?.find((item) => item.itemCode === String(code))?.itemValue || String(code ?? '-')
  return { provinces, cityMap, dictMap, loadProvinces, loadCities, loadDict, label }
})
