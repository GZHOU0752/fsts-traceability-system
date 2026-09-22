<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, Refresh, Search, Upload } from '@element-plus/icons-vue'
import jsQR from 'jsqr'
import { extractTraceCode } from '@/lib/traceCode'

const props = withDefaults(defineProps<{ modelValue: boolean; title?: string }>(), { title: '扫码溯源' })
const emit = defineEmits<{ 'update:modelValue': [boolean]; decoded: [string] }>()

type ScanStatus = 'idle' | 'starting' | 'scanning' | 'insecure' | 'unsupported' | 'denied' | 'missing' | 'busy' | 'error'

const visible = computed({ get: () => props.modelValue, set: (value: boolean) => emit('update:modelValue', value) })
const tab = ref<'camera' | 'upload'>('camera')
const video = ref<HTMLVideoElement>()
const fileInput = ref<HTMLInputElement>()
const status = ref<ScanStatus>('idle')
const hint = ref('')
const manualCode = ref('')
const dragging = ref(false)

const STATUS_TEXT: Record<ScanStatus, string> = {
  idle: '准备开启摄像头…',
  starting: '正在启动摄像头…',
  scanning: '',
  insecure: '浏览器只允许在 https 或 localhost 下调用摄像头，请改用上传图片方式',
  unsupported: '当前浏览器不支持调用摄像头，请改用上传图片方式',
  denied: '摄像头权限被拒绝，请在地址栏放行后重试',
  missing: '没有检测到可用摄像头，请改用上传图片方式',
  busy: '摄像头被其他程序占用，关闭占用程序后重试',
  error: '摄像头启动失败，请重试或改用上传图片方式',
}

const scanning = computed(() => status.value === 'scanning')
const statusText = computed(() => STATUS_TEXT[status.value])
const cameraRetryable = computed(() => ['denied', 'missing', 'busy', 'error'].includes(status.value))

let stream: MediaStream | null = null
let frameId = 0
let cooldownUntil = 0
let canvas: HTMLCanvasElement | null = null
let context: CanvasRenderingContext2D | null = null

async function startCamera() {
  stopCamera()
  hint.value = ''
  if (typeof window !== 'undefined' && !window.isSecureContext) {
    status.value = 'insecure'
    return
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    status.value = 'unsupported'
    return
  }
  status.value = 'starting'
  await nextTick()
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false,
    })
    const element = video.value
    if (!element) {
      stopCamera()
      return
    }
    element.srcObject = stream
    await element.play().catch(() => undefined)
    status.value = 'scanning'
    frameId = requestAnimationFrame(tick)
  } catch (cause) {
    status.value = mapCameraError(cause)
  }
}

function stopCamera() {
  if (frameId) {
    cancelAnimationFrame(frameId)
    frameId = 0
  }
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
  if (video.value) {
    video.value.srcObject = null
  }
  status.value = 'idle'
  cooldownUntil = 0
}

function renderContext(): CanvasRenderingContext2D | null {
  if (!canvas) {
    canvas = document.createElement('canvas')
    context = canvas.getContext('2d', { willReadFrequently: true })
  }
  return context
}

/** 每帧识别太耗电，这里限制在约 8 次/秒；画面只做截取，不改变取景。 */
function tick(timestamp: number) {
  frameId = requestAnimationFrame(tick)
  if (timestamp < cooldownUntil) {
    return
  }
  const element = video.value
  const ctx = renderContext()
  if (!element || !ctx || !canvas || !stream || !element.videoWidth || element.readyState < 2) {
    return
  }
  const scale = Math.min(1, 480 / element.videoWidth)
  const width = Math.round(element.videoWidth * scale)
  const height = Math.round((element.videoHeight || element.videoWidth) * scale)
  canvas.width = width
  canvas.height = height
  ctx.drawImage(element, 0, 0, width, height)
  const pixels = ctx.getImageData(0, 0, width, height)
  const result = jsQR(pixels.data, width, height, { inversionAttempts: 'attemptBoth' })
  if (!result?.data) {
    return
  }
  const code = extractTraceCode(result.data)
  if (code) {
    finish(code)
    return
  }
  cooldownUntil = timestamp + 2500
  hint.value = '这个二维码不是 FSTS 溯源码，请对准产品包装上的溯源码标签'
}

function finish(code: string) {
  stopCamera()
  visible.value = false
  ElMessage.success(`已识别溯源码 ${code}`)
  emit('decoded', code)
}

function mapCameraError(cause: unknown): ScanStatus {
  const name = (cause as DOMException | undefined)?.name ?? ''
  if (['NotAllowedError', 'PermissionDeniedError', 'SecurityError'].includes(name)) {
    return 'denied'
  }
  if (['NotFoundError', 'DevicesNotFoundError', 'OverconstrainedError'].includes(name)) {
    return 'missing'
  }
  if (['NotReadableError', 'TrackStartError', 'AbortError'].includes(name)) {
    return 'busy'
  }
  return 'error'
}

function pickFile() {
  fileInput.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    void decodeFile(file)
  }
  input.value = ''
}

async function onDrop(event: DragEvent) {
  dragging.value = false
  const file = Array.from(event.dataTransfer?.files ?? []).find((item) => item.type.startsWith('image/'))
  if (!file) {
    ElMessage.warning('请拖入二维码图片文件')
    return
  }
  await decodeFile(file)
}

async function decodeFile(file: File) {
  const ctx = renderContext()
  if (!ctx || !canvas) {
    return
  }
  hint.value = ''
  const objectUrl = URL.createObjectURL(file)
  try {
    const image = await loadImage(objectUrl)
    const scale = Math.min(1, 1400 / Math.max(image.width, image.height))
    canvas.width = Math.max(1, Math.round(image.width * scale))
    canvas.height = Math.max(1, Math.round(image.height * scale))
    ctx.drawImage(image, 0, 0, canvas.width, canvas.height)
    const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height)
    const result = jsQR(pixels.data, canvas.width, canvas.height, { inversionAttempts: 'attemptBoth' })
    const code = result?.data ? extractTraceCode(result.data) : null
    if (code) {
      finish(code)
      return
    }
    ElMessage.warning(result?.data ? '这不是本系统的溯源码，请上传产品包装上的溯源码标签' : '没有识别到二维码，请换一张更清晰、完整的图片')
  } catch {
    ElMessage.error('图片读取失败，请重新选择')
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('图片加载失败'))
    image.src = url
  })
}

function submitManual() {
  const value = manualCode.value.trim()
  if (!value) {
    ElMessage.warning('请输入溯源码')
    return
  }
  const code = extractTraceCode(value)
  if (!code) {
    ElMessage.warning('溯源码格式形如 FSTS-20260921-SH-0001')
    return
  }
  finish(code)
}

watch(() => props.modelValue, async (open) => {
  if (open) {
    hint.value = ''
    manualCode.value = ''
    await nextTick()
    if (tab.value === 'camera') {
      await startCamera()
    }
    return
  }
  stopCamera()
})

watch(tab, async (value) => {
  hint.value = ''
  if (value === 'camera') {
    await startCamera()
    return
  }
  stopCamera()
})

onBeforeUnmount(stopCamera)
</script>
<template><el-dialog v-model="visible" :title="title" width="min(480px, 94vw)" class="scan-dialog" append-to-body @closed="stopCamera"><el-tabs v-model="tab" stretch><el-tab-pane label="摄像头扫码" name="camera"><div class="scan-stage"><video ref="video" class="scan-stage__video" playsinline muted autoplay></video><div class="scan-stage__frame" :class="{ 'is-idle': !scanning }"></div><div v-if="!scanning" class="scan-stage__mask"><el-icon><Camera /></el-icon><span>{{ statusText }}</span><el-button v-if="cameraRetryable" size="small" :icon="Refresh" @click="startCamera">重新开启摄像头</el-button></div></div><p class="scan-tip">{{ hint || '把溯源码放进取景框，识别成功后会自动查询这条冷链记录。' }}</p></el-tab-pane><el-tab-pane label="上传二维码图片" name="upload"><div class="scan-upload" :class="{ 'is-dragging': dragging }" @click="pickFile" @dragover.prevent="dragging = true" @dragleave="dragging = false" @drop.prevent="onDrop"><el-icon><Upload /></el-icon><strong>点击选择，或把二维码图片拖到这里</strong><span>支持截图、拍照和标签照片，图片越清晰越容易识别</span></div><input ref="fileInput" class="scan-file-input" type="file" accept="image/*" @change="onFileChange"><p class="scan-tip">{{ hint || '电脑没有摄像头时，可以从手机相册上传二维码图片。' }}</p></el-tab-pane></el-tabs><div class="scan-manual"><el-input v-model="manualCode" clearable placeholder="FSTS-20260921-SH-0001" @keyup.enter="submitManual"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-button type="primary" @click="submitManual">查询</el-button></div></el-dialog></template>
