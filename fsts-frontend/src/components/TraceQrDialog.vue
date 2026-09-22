<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Download, Link, Printer } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import TraceQrCode from '@/components/TraceQrCode.vue'
import { printTraceLabel } from '@/lib/traceLabel'
import { useTraceLinkBase } from '@/composables/useTraceLinkBase'
import type { TraceCodeInfo } from '@/types/domain'

/**
 * 图形化溯源码弹窗：给企业端展示、复制、下载和打印二维码标签。
 *
 * 二维码里承载的是追溯页链接，链接的主机会被换成局域网地址（见 useTraceLinkBase），
 * 这样消费者用手机扫码才能真正打开追溯页。
 */
const props = defineProps<{ modelValue: boolean; info: TraceCodeInfo | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const visible = computed({ get: () => props.modelValue, set: (value: boolean) => emit('update:modelValue', value) })
const qrRef = ref<InstanceType<typeof TraceQrCode>>()
const { detect, link } = useTraceLinkBase()
const traceLink = computed(() => (props.info?.traceCode ? link(props.info.traceCode, props.info.qrContent) : ''))

watch(visible, (open) => {
  if (open) {
    void detect()
  }
})

async function copy(value?: string, tip = '已复制') {
  if (!value) {
    return
  }
  await navigator.clipboard?.writeText(value)
  ElMessage.success(tip)
}

function download() {
  qrRef.value?.download(props.info?.traceCode)
}

function print() {
  const dataUrl = qrRef.value?.toDataUrl()
  if (!dataUrl || !props.info) {
    ElMessage.warning('二维码还在生成中，请稍后再打印')
    return
  }
  const opened = printTraceLabel({
    qrDataUrl: dataUrl,
    traceCode: props.info.traceCode,
    batchNo: props.info.batchNo,
    productVariety: props.info.productVariety,
    saleStore: props.info.saleStore,
    traceUrl: traceLink.value,
  })
  if (!opened) {
    ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
  }
}
</script>
<template>
  <el-dialog v-model="visible" title="图形化溯源码" width="min(520px, 94vw)" class="trace-qr-dialog" destroy-on-close>
    <div v-if="info" class="trace-qr-dialog__body">
      <div class="trace-qr-dialog__meta">
        <strong>{{ info.batchNo }}</strong>
        <span>{{ info.productVariety || '品种未填写' }}<template v-if="info.saleStore"> · {{ info.saleStore }}</template></span>
        <StatusTag :label="info.statusName || '有效'" :tone="info.status === 1 ? 'success' : 'info'" />
      </div>
      <TraceQrCode ref="qrRef" :value="traceLink" :link="traceLink" :code="info.traceCode" :size="176" :caption="`用手机相机或微信扫码即可打开本批次追溯页 · 生成于 ${info.generateTime || '—'}`" />
    </div>
    <template #footer>
      <el-button size="small" :icon="CopyDocument" @click="copy(info?.traceCode, '溯源码已复制')">溯源码</el-button>
      <el-button size="small" :icon="Link" @click="copy(traceLink, '追溯链接已复制')">复制链接</el-button>
      <el-button size="small" :icon="Download" @click="download">下载图片</el-button>
      <el-button size="small" type="primary" :icon="Printer" @click="print">打印标签</el-button>
    </template>
  </el-dialog>
</template>
