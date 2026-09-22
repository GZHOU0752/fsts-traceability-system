<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { Download } from '@element-plus/icons-vue'
import QRCode from 'qrcode'

const props = withDefaults(defineProps<{
  value?: string
  size?: number
  code?: string
  /** 二维码里真正编码的地址：手机扫出来打开的就是它，展示出来便于核对 */
  link?: string
  caption?: string
  showActions?: boolean
  downloadName?: string
}>(), { value: '', code: '', link: '', caption: '', downloadName: '', size: 196, showActions: false })

const emit = defineEmits<{ (event: 'error', message: string): void }>()

const canvas = ref<HTMLCanvasElement>()
const error = ref('')

async function render() {
  const element = canvas.value
  if (!element) {
    return
  }
  if (!props.value) {
    error.value = '暂无可生成二维码的内容'
    return
  }
  // 按设备像素比放大画布，扫码更稳、打印更清晰，显示尺寸仍按 size 控制
  const ratio = Math.min(Math.max(window.devicePixelRatio || 1, 2), 3)
  try {
    await QRCode.toCanvas(element, props.value, {
      width: Math.round(props.size * ratio),
      margin: 1,
      errorCorrectionLevel: 'M',
      color: { dark: '#102b3fff', light: '#ffffffff' },
    })
    element.style.width = `${props.size}px`
    element.style.height = `${props.size}px`
    error.value = ''
  } catch {
    error.value = '二维码生成失败'
    emit('error', error.value)
  }
}

function toDataUrl(): string {
  return canvas.value?.toDataURL('image/png') ?? ''
}

function download(name?: string) {
  const dataUrl = toDataUrl()
  if (!dataUrl) {
    emit('error', '二维码尚未生成完成')
    return
  }
  const link = document.createElement('a')
  link.href = dataUrl
  link.download = `${name || props.downloadName || props.code || 'trace-qrcode'}.png`
  document.body.appendChild(link)
  link.click()
  link.remove()
}

defineExpose({ toDataUrl, download, render })

watch(() => [props.value, props.size], () => { void render() })
onMounted(async () => { await nextTick(); await render() })
</script>
<template><div class="trace-qr"><div class="trace-qr__frame"><canvas v-show="!error" ref="canvas" class="trace-qr__canvas" :style="{ width: `${size}px`, height: `${size}px` }"></canvas><div v-if="error" class="trace-qr__error">{{ error }}</div></div><p v-if="code" class="trace-qr__code">{{ code }}</p><p v-if="link" class="trace-qr__link">{{ link }}</p><p v-if="caption" class="trace-qr__caption">{{ caption }}</p><div v-if="showActions || $slots.actions" class="trace-qr__actions"><el-button v-if="showActions" :icon="Download" @click="download()">下载二维码</el-button><slot name="actions"></slot></div></div></template>
