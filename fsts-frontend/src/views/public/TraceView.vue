<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, ArrowRight, CopyDocument, Refresh, Camera, Download, Link } from '@element-plus/icons-vue'
import { publicApi } from '@/api/public'
import { ApiError } from '@/lib/http'
import { writeClipboard } from '@/lib/clipboard'
import { extractTraceCode } from '@/lib/traceCode'
import { useTraceLinkBase } from '@/composables/useTraceLinkBase'
import type { PublicProduct, PublicTrace } from '@/types/domain'
import TraceHero from '@/components/trace/TraceHero.vue'
import TraceSummary from '@/components/trace/TraceSummary.vue'
import TraceChain from '@/components/TraceChain.vue'
import TraceCertificates from '@/components/trace/TraceCertificates.vue'

// 重依赖按需加载：温度曲线带 echarts、扫码弹窗带 jsQR、二维码带 qrcode 库。
// 同步引入会让首屏一次性下载全部依赖，改成异步后只在真正用到时才拉取对应分包。
const TraceTemperature = defineAsyncComponent(() => import('@/components/trace/TraceTemperature.vue'))
const TraceQrCode = defineAsyncComponent(() => import('@/components/TraceQrCode.vue'))
const QrScanDialog = defineAsyncComponent(() => import('@/components/QrScanDialog.vue'))

const route = useRoute()
const router = useRouter()
const { detect, link } = useTraceLinkBase()

const traceCode = ref(String(route.params.traceCode || ''))
const loading = ref(false)
const trace = ref<PublicTrace>()
const state = ref<'idle' | 'ready' | 'not-found' | 'error'>('idle')
const scanVisible = ref(false)
const qrVisible = ref(false)
const qrProduct = ref<PublicProduct>()
const qrDialogRef = ref<InstanceType<typeof TraceQrCode>>()

const productKeyword = ref('')
const products = ref<PublicProduct[]>([])
const searching = ref(false)
const searched = ref(false)

const hasTrace = computed(() => state.value === 'ready' && trace.value)

/**
 * 空态展示的四个环节，取自 12.1 接口真实返回的链条结构：
 * 后端 TraceChainService 的 STAGE_NAMES 即为这四段，明细字段也一一对应。
 * 这里不是示意文案，而是告诉用户"查得到什么"。
 */
const PREVIEW_STAGES = [
  { name: '捕捞与养殖', detail: '起运温度 · 药残报告' },
  { name: '冷冻加工', detail: '速冻中心温度 · 出厂检验' },
  { name: '批发与冷链仓储', detail: '入库 / 冷库 / 出库温度' },
  { name: '零售终端', detail: '陈列柜温度 · 销售门店' },
]

/**
 * 产品搜索结果缓存 + 请求去重。
 *
 * <p>输入框每次按键都会触发联想查询，原始实现既不取消上一次请求，也不复用结果：
 * 快速输入时会并发发出多个请求，且先发的慢响应可能覆盖后发的快响应。
 * 这里用 AbortController 取消在途请求，并用序列号丢弃过期响应，同时缓存最近结果。
 */
const searchCache = new Map<string, PublicProduct[]>()
let suggestTimer: ReturnType<typeof setTimeout> | undefined
let suggestController: AbortController | undefined
let suggestSeq = 0
let traceSeq = 0

async function fetchProducts(keyword: string, signal?: AbortSignal, force = false): Promise<PublicProduct[]> {
  const key = keyword.trim()
  if (!force) {
    const cached = searchCache.get(key)
    if (cached) return cached
  }
  const list = await publicApi.searchProducts(key, signal)
  searchCache.set(key, list)
  if (searchCache.size > 32) {
    const oldest = searchCache.keys().next().value
    if (oldest !== undefined) searchCache.delete(oldest)
  }
  return list
}

// 二维码承载追溯页链接：手机扫它就能直接打开这一页，链接里已把 localhost 换成局域网地址
// 链接带 scan=1 标记：只有扫码打开时才展示完整冷链，桌面端只展示二维码
const traceLink = computed(() => (trace.value?.traceCode ? withScanMarker(link(trace.value.traceCode)) : ''))
const qrLink = computed(() => (qrProduct.value?.traceCode ? withScanMarker(link(qrProduct.value.traceCode)) : ''))
const showColdChain = computed(() => route.query.scan === '1')

async function loadTrace(value: string) {
  const input = value.trim()
  if (!input) return
  const code = extractTraceCode(input) || input
  // 查询序号：扫码 / 手动输入 / 路由跳转可能并发触发，慢响应不得覆盖新结果
  const seq = ++traceSeq
  traceCode.value = code
  loading.value = true
  try {
    const result = await publicApi.trace(code)
    if (seq !== traceSeq) return
    trace.value = result
    state.value = 'ready'
    if (route.params.traceCode !== code) {
      router.replace(`/trace/${encodeURIComponent(code)}`)
    }
  } catch (error) {
    if (seq !== traceSeq) return
    trace.value = undefined
    state.value = error instanceof ApiError && error.code === 3001 ? 'not-found' : 'error'
  } finally {
    if (seq === traceSeq) loading.value = false
  }
}

async function searchProducts() {
  const keyword = productKeyword.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入产品名称')
    return
  }
  searching.value = true
  try {
    products.value = await fetchProducts(keyword, undefined, true)
    searched.value = true
    if (products.value.length === 0) {
      ElMessage.info('没有找到相关产品')
    }
  } catch {
    products.value = []
    searched.value = true
    ElMessage.error('产品搜索失败，请稍后重试')
  } finally {
    searching.value = false
  }
}

function openProduct(product: PublicProduct) {
  if (!product.traceCode) return
  void detect()
  qrProduct.value = product
  qrVisible.value = true
}

// 关联搜索：输入时实时返回匹配产品，作为下拉建议展示
function querySearch(queryString: string, callback: (suggestions: Array<PublicProduct & { value: string }>) => void) {
  const keyword = queryString.trim()
  if (suggestTimer) clearTimeout(suggestTimer)
  suggestController?.abort()
  suggestTimer = setTimeout(async () => {
    const seq = ++suggestSeq
    const controller = new AbortController()
    suggestController = controller
    try {
      const list = await fetchProducts(keyword, controller.signal)
      if (seq !== suggestSeq) return
      callback(list.map((p) => ({ ...p, value: p.productVariety || p.batchNo })))
    } catch {
      // 被后续请求取消时不回调，避免旧响应覆盖新结果
      if (seq === suggestSeq) callback([])
    }
  }, 250)
}

function handleSelect(item: PublicProduct & { value?: string }) {
  openProduct(item)
}

async function onDecoded(code: string) {
  await loadTrace(code)
}

async function copy(value?: string, tip = '溯源码已复制') {
  if (!value) return
  // 局域网 http 下 Clipboard API 不可用，必须回退并如实提示，否则是"假成功"
  if (await writeClipboard(value)) {
    ElMessage.success(tip)
  } else {
    ElMessage.warning('当前浏览器禁止自动复制，请手动选中后复制')
  }
}

function downloadDialogQr() {
  qrDialogRef.value?.download(qrProduct.value?.traceCode)
}

function withScanMarker(url: string): string {
  if (!url) return url
  return url + (url.includes('?') ? '&' : '?') + 'scan=1'
}

function retry() {
  if (traceCode.value) loadTrace(traceCode.value)
}

// 直接改地址栏里的溯源码（例如手机扫码在新标签打开）时同步刷新结果
watch(
  () => route.params.traceCode,
  (value) => {
    const next = String(value || '')
    if (next && next !== traceCode.value) {
      traceCode.value = next
      loadTrace(next)
    }
  },
)

onMounted(() => {
  void detect()
  if (traceCode.value) loadTrace(traceCode.value)
})

// 联想输入是 250ms 防抖 + 在途请求，组件卸载时必须一并取消，否则会留下悬挂定时器
onBeforeUnmount(() => {
  if (suggestTimer) clearTimeout(suggestTimer)
  suggestController?.abort()
})
</script>

<template>
  <div class="trace-view">
    <TraceHero
      v-if="!showColdChain"
      :trace-code="trace?.traceCode"
      :product-variety="trace?.productVariety"
      :retailer-name="trace?.retailerName"
      :sale-store="trace?.saleStore"
    />

    <section v-if="!showColdChain" class="trace-search">
      <div class="trace-search__copy">
        <h2>输入产品名称，查询产品旅程</h2>
      </div>
      <div class="trace-search__form">
        <el-autocomplete
          v-model="productKeyword"
          :fetch-suggestions="querySearch"
          :trigger-on-focus="true"
          size="large"
          placeholder="输入产品名称，如：带鱼"
          clearable
          @select="handleSelect"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
          <template #default="{ item }">
            <div class="trace-suggestion">
              <span class="trace-suggestion__name">{{ item.productVariety || item.batchNo }}</span>
              <span class="trace-suggestion__meta">{{ [item.retailerName, item.saleStore].filter(Boolean).join(' · ') }}</span>
            </div>
          </template>
        </el-autocomplete>
        <el-button type="primary" size="large" :loading="searching" :icon="ArrowRight" @click="searchProducts">
          搜索产品
        </el-button>
        <el-button size="large" :icon="Camera" @click="scanVisible = true">扫一扫</el-button>
      </div>
    </section>

    <section v-if="searched" class="product-results">
      <div class="product-results__head">
        <h3>找到 {{ products.length }} 个可溯源产品</h3>
      </div>
      <div v-if="products.length === 0" class="product-results__empty">
        没有找到相关产品，换个关键词试试
      </div>
      <ul v-else class="product-list">
        <li v-for="p in products" :key="p.traceCode" class="product-item" @click="openProduct(p)">
          <div class="product-item__main">
            <div class="product-item__name">{{ p.productVariety || p.batchNo }}</div>
            <div class="product-item__meta">
              <span v-if="p.batchNo">批号 {{ p.batchNo }}</span>
              <span v-if="p.retailerName">{{ p.retailerName }}</span>
              <span v-if="p.saleStore">{{ p.saleStore }}</span>
            </div>
          </div>
          <el-icon class="product-item__arrow"><ArrowRight /></el-icon>
        </li>
      </ul>
    </section>

    <section v-if="hasTrace && showColdChain" class="trace-result">
      <TraceSummary :trace="trace!" />
      <div class="trace-result__grid">
        <div class="trace-result__main">
          <section class="trace-section">
            <div class="trace-section__head">
              <div>
                <h3>全链路流转</h3>
              </div>
              <el-button text :icon="CopyDocument" @click="copy(trace!.traceCode)">复制溯源码</el-button>
            </div>
            <TraceChain :links="trace!.links" />
          </section>
          <TraceTemperature :points="trace!.temperatureCurve || []" />
        </div>
        <aside class="trace-result__side">
          <TraceCertificates :links="trace!.links" />
          <section class="trace-section trace-note">
            <h3>数据说明</h3>
            <p>链路信息由各节点企业在交接确认时记录，仅用于产品溯源查询。</p>
          </section>
        </aside>
      </div>
    </section>

    <section v-else-if="hasTrace && !showColdChain" class="trace-scan-only">
      <div class="trace-scan-only__facts">
        <h3>{{ trace!.productVariety || trace!.batchNo }}</h3>
        <dl class="trace-scan-only__list">
          <div><dt>产品批号</dt><dd>{{ trace!.batchNo || '-' }}</dd></div>
          <div><dt>零售商</dt><dd>{{ trace!.retailerName || '-' }}</dd></div>
          <div><dt>销售门店</dt><dd>{{ trace!.saleStore || '-' }}</dd></div>
          <div><dt>标识码生成时间</dt><dd>{{ trace!.generateTime || '-' }}</dd></div>
          <div><dt>流转环节</dt><dd>{{ trace!.links?.length ?? 0 }} 个</dd></div>
        </dl>
        <p class="trace-scan-only__hint">扫码需手机与电脑处于同一 Wi-Fi。</p>
      </div>
      <TraceQrCode
        :value="traceLink"
        :link="traceLink"
        :code="trace!.traceCode"
        :size="240"
      />
    </section>

    <section v-else-if="state === 'idle'" class="trace-placeholder">
      <h3>从一个产品名开始</h3>
      <p>输入产品名称，或用手机扫描包装二维码，查看批号、流转企业与温度记录。</p>
      <ol class="trace-route" aria-label="溯源结果包含的环节与记录">
        <li
          v-for="(stage, index) in PREVIEW_STAGES"
          :key="stage.name"
          class="trace-route__stage"
          :style="{ '--route-index': index }"
        >
          <strong>{{ stage.name }}</strong>
          <small>{{ stage.detail }}</small>
        </li>
      </ol>
    </section>

    <section v-else class="trace-placeholder trace-placeholder--error">
      <div class="trace-placeholder__number">!</div>
      <h3>{{ state === 'not-found' ? '没有找到这串溯源码' : '暂时无法查询' }}</h3>
      <p>{{ state === 'not-found' ? '请确认二维码或链接是否完整，或向销售方核实。' : '服务正在恢复，请稍后重试。' }}</p>
      <el-button :icon="Refresh" @click="retry">重新查询</el-button>
    </section>

    <QrScanDialog v-model="scanVisible" title="扫一扫溯源码" @decoded="onDecoded" />

    <el-dialog v-model="qrVisible" title="微信扫一扫 · 查看完整冷链" width="min(460px, 94vw)" class="trace-qr-dialog" destroy-on-close>
      <div v-if="qrProduct" class="trace-qr-dialog__body">
        <div class="trace-qr-dialog__meta">
          <strong>{{ qrProduct.productVariety || qrProduct.batchNo }}</strong>
          <span>批号 {{ qrProduct.batchNo }}</span>
          <span v-if="qrProduct.retailerName">{{ qrProduct.retailerName }}</span>
          <span v-if="qrProduct.saleStore">{{ qrProduct.saleStore }}</span>
        </div>
        <TraceQrCode
          ref="qrDialogRef"
          :value="qrLink"
          :link="qrLink"
          :code="qrProduct.traceCode"
          :size="220"
          caption="用微信扫一扫，即可查看该产品的完整冷链过程"
        />
      </div>
      <template #footer>
        <el-button :icon="Download" @click="downloadDialogQr">下载二维码</el-button>
        <el-button :icon="Link" @click="copy(qrLink, '追溯链接已复制')">复制链接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.product-results {
  margin: -18px 46px 26px;
  padding: 20px 22px;
  border: 1px solid var(--mist-200);
  border-radius: 16px;
  background: var(--paper);
  box-shadow: var(--shadow-sm);
}

.product-results__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}

.product-results__head h3 {
  margin: 0;
  color: var(--ink-700);
  font-size: 15px;
}

.product-results__empty {
  padding: 22px 0;
  color: var(--ink-500);
  font-size: 13px;
  text-align: center;
}

.product-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.product-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--mist-200);
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.product-item:hover {
  border-color: var(--teal-500);
  background: var(--teal-100);
}

.product-item__name {
  color: var(--ink-950);
  font-size: 16px;
  font-weight: 700;
}

.product-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 6px;
  color: var(--ink-500);
  font-size: 12px;
}

.product-item__arrow {
  flex: 0 0 auto;
  color: var(--teal-600);
}

.trace-scan-only {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 34px;
  margin: 28px 46px 40px;
  padding: 34px 40px;
  border: 1px solid var(--mist-200);
  border-radius: 16px;
  background: var(--paper);
  box-shadow: var(--shadow-sm);
}

.trace-scan-only__facts {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.trace-scan-only__facts h3 {
  margin: 0;
  color: var(--ink-950);
  font-size: 22px;
}

.trace-scan-only__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px 22px;
  margin: 0;
}

.trace-scan-only__list div {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--mist-100);
}

.trace-scan-only__list dt {
  color: var(--ink-500);
  font-size: 11px;
}

.trace-scan-only__list dd {
  margin: 0;
  overflow: hidden;
  color: var(--ink-950);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-scan-only__hint {
  margin: 0;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.7;
}

/* 空态：原来只有一枚"01"数字，撑不住首屏高度；
   换成真实的四段冷链环节，既解释了会看到什么，也把留白变成信息。 */
.trace-route {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  width: 100%;
  max-width: 900px;
  margin: 40px auto 0;
  padding: 0;
  list-style: none;
}

.trace-route__stage {
  position: relative;
  display: grid;
  gap: 4px;
  justify-items: center;
  padding: 27px 12px 0;
  text-align: center;
}

/* 路线：一条从首节点圆心贯穿到末节点圆心的整线。
   早先按环节分段画，每段各自渐变，会读成四截断线；整线只画一次也更像"一条链路"。 */
.trace-route::before {
  position: absolute;
  top: 8px;
  right: 12.5%;
  left: 12.5%;
  height: 1px;
  background: linear-gradient(90deg, var(--teal-500), rgba(32, 167, 154, 0.22));
  content: '';
  transform: scaleX(0);
  transform-origin: left center;
  animation: trace-route-line 0.9s var(--ease-out) forwards;
}

/* 节点：白底 + 品牌色描边，压在连接线之上 */
.trace-route__stage::after {
  position: absolute;
  top: 2px;
  left: 50%;
  width: 13px;
  height: 13px;
  border: 2px solid var(--teal-500);
  border-radius: 50%;
  background: var(--paper);
  box-shadow: 0 0 0 5px rgba(32, 167, 154, 0.12);
  content: '';
  opacity: 0;
  transform: translateX(-50%) scale(0.5);
  animation: trace-route-node 0.45s var(--ease-out) forwards;
  animation-delay: calc(var(--route-index) * 160ms + 140ms);
}

.trace-route__stage strong {
  color: var(--ink-950);
  font-size: 13px;
}

.trace-route__stage small {
  color: var(--ink-500);
  font-size: 11px;
}

@keyframes trace-route-line {
  to {
    transform: scaleX(1);
  }
}

@keyframes trace-route-node {
  to {
    opacity: 1;
    transform: translateX(-50%) scale(1);
  }
}

@media (max-width: 700px) {
  .trace-route {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 28px 12px;
    margin-top: 30px;
  }

  .trace-route__stage {
    padding: 24px 6px 0;
  }

  /* 两列时整条路线会指向错误的方向，直接去掉，保留节点与文案 */
  .trace-route::before {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trace-route::before {
    animation: none;
    transform: scaleX(1);
  }

  .trace-route__stage::after {
    animation: none;
    opacity: 1;
    transform: translateX(-50%);
  }
}

@media (max-width: 600px) {
  .product-results {
    margin-right: 0;
    margin-left: 0;
  }

  .trace-scan-only {
    margin-right: 0;
    margin-left: 0;
  }
}

/* 窄屏放不下"信息 + 二维码"两栏，改为上下堆叠并居中 */
@media (max-width: 700px) {
  .trace-scan-only {
    grid-template-columns: minmax(0, 1fr);
    justify-items: center;
    gap: 26px;
    padding: 26px 22px 30px;
    text-align: center;
  }

  .trace-scan-only__facts {
    justify-items: center;
  }

  .trace-scan-only__list {
    grid-template-columns: minmax(0, 1fr);
    width: 100%;
  }
}
</style>

<style>
.trace-suggestion {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.trace-suggestion__name {
  color: var(--ink-950);
  font-size: 14px;
  font-weight: 600;
}

.trace-suggestion__meta {
  color: var(--ink-500);
  font-size: 12px;
}
</style>
