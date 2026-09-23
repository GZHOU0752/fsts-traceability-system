<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, ArrowRight, CopyDocument, Refresh, Camera, Download, Link } from '@element-plus/icons-vue'
import { publicApi } from '@/api/public'
import { ApiError } from '@/lib/http'
import { extractTraceCode } from '@/lib/traceCode'
import { useTraceLinkBase } from '@/composables/useTraceLinkBase'
import type { PublicProduct, PublicTrace } from '@/types/domain'
import TraceHero from '@/components/trace/TraceHero.vue'
import TraceSummary from '@/components/trace/TraceSummary.vue'
import TraceChain from '@/components/TraceChain.vue'
import TraceTemperature from '@/components/trace/TraceTemperature.vue'
import TraceCertificates from '@/components/trace/TraceCertificates.vue'
import TraceQrCode from '@/components/TraceQrCode.vue'
import QrScanDialog from '@/components/QrScanDialog.vue'

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

// 二维码承载追溯页链接：手机扫它就能直接打开这一页，链接里已把 localhost 换成局域网地址
// 链接带 scan=1 标记：只有扫码打开时才展示完整冷链，桌面端只展示二维码
const traceLink = computed(() => (trace.value?.traceCode ? withScanMarker(link(trace.value.traceCode)) : ''))
const qrLink = computed(() => (qrProduct.value?.traceCode ? withScanMarker(link(qrProduct.value.traceCode)) : ''))
const showColdChain = computed(() => route.query.scan === '1')

async function loadTrace(value: string) {
  const input = value.trim()
  if (!input) return
  const code = extractTraceCode(input) || input
  traceCode.value = code
  loading.value = true
  try {
    trace.value = await publicApi.trace(code)
    state.value = 'ready'
    if (route.params.traceCode !== code) {
      router.replace(`/trace/${encodeURIComponent(code)}`)
    }
  } catch (error) {
    trace.value = undefined
    state.value = error instanceof ApiError && error.code === 3001 ? 'not-found' : 'error'
  } finally {
    loading.value = false
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
    products.value = await publicApi.searchProducts(keyword)
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

let suggestTimer: ReturnType<typeof setTimeout> | undefined

// 关联搜索：输入时实时返回匹配产品，作为下拉建议展示
function querySearch(queryString: string, callback: (suggestions: Array<PublicProduct & { value: string }>) => void) {
  const keyword = queryString.trim()
  if (suggestTimer) clearTimeout(suggestTimer)
  suggestTimer = setTimeout(async () => {
    try {
      const list = await publicApi.searchProducts(keyword)
      callback(list.map((p) => ({ ...p, value: p.productVariety || p.batchNo })))
    } catch {
      callback([])
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
  if (value) {
    await navigator.clipboard?.writeText(value)
    ElMessage.success(tip)
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
        <span class="eyebrow">LOOK UP A PRODUCT</span>
        <h2>输入产品名称，查询产品旅程</h2>
        <p>输入产品名称（如“带鱼”），在结果里选择要查询的产品。</p>
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
        <span class="eyebrow">SEARCH RESULTS</span>
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
                <span class="eyebrow">TRACE CHAIN</span>
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
            <span class="eyebrow">TRUST NOTE</span>
            <h3>数据可信说明</h3>
            <p>链路信息来自参与流通的节点企业，由系统在交接确认时记录。展示内容仅用于产品溯源查询。</p>
            <div class="trace-note__seal">FSTS VERIFIED <small>可信冷链记录</small></div>
          </section>
        </aside>
      </div>
    </section>

    <section v-else-if="hasTrace && !showColdChain" class="trace-scan-only">
      <div class="trace-scan-only__head">
        <span class="eyebrow">SCAN TO TRACE</span>
        <h3>{{ trace!.productVariety || trace!.batchNo }}</h3>
      </div>
      <TraceQrCode
        :value="traceLink"
        :link="traceLink"
        :code="trace!.traceCode"
        :size="240"
        caption="用微信扫一扫，查看该产品的完整冷链过程"
      />
    </section>

    <section v-else-if="state === 'idle'" class="trace-placeholder">
      <div class="trace-placeholder__number">01</div>
      <h3>从一个产品名开始</h3>
      <p>输入产品名称（如“带鱼”），或用手机扫描产品包装上的二维码，即可查看批号、流转企业、温度记录和检验证明。</p>
    </section>

    <section v-else class="trace-placeholder trace-placeholder--error">
      <div class="trace-placeholder__number">!</div>
      <h3>{{ state === 'not-found' ? '没有找到这串溯源码' : '暂时无法查询' }}</h3>
      <p>{{ state === 'not-found' ? '请检查输入是否完整，或向销售方确认溯源码。' : '服务正在恢复，请稍后重试。' }}</p>
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
        <p class="trace-qr-dialog__tip">手机与电脑连接同一 Wi-Fi 时，用微信扫一扫即可查看该产品的完整冷链过程。</p>
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
  place-items: center;
  gap: 18px;
  margin: 28px 46px 40px;
  padding: 42px 24px;
  border: 1px solid var(--mist-200);
  border-radius: 16px;
  background: var(--paper);
  box-shadow: var(--shadow-sm);
  text-align: center;
}

.trace-scan-only__head {
  display: grid;
  gap: 6px;
}

.trace-scan-only__head h3 {
  margin: 0;
  color: var(--ink-950);
  font-size: 20px;
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
