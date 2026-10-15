<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { showError } from '@/lib/feedback'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, EditPen, Delete, Promotion, Download, Link, View, CircleClose, UploadFilled, Document } from '@element-plus/icons-vue'
import { enterpriseApi, type BatchQuery } from '@/api/enterprise'
import { ApiError } from '@/lib/http'
import { downloadTextFile, parseCsv, toCsv } from '@/lib/csv'
import { useAuthStore } from '@/stores/auth'
import { useDictionaryStore } from '@/stores/dictionary'
import type { DictItem } from '@/types/api'
import type { BatchDetail, BatchForm, BatchImportResult, BatchListItem, TraceCodeInfo, UpstreamBatch, UpstreamEnterprise } from '@/types/domain'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import TraceQrDialog from '@/components/TraceQrDialog.vue'
import {
  batchFieldsOf,
  importColumnsOf,
  parseImportMatrix,
  canPublishDirectly,
  createEmptyBatchForm,
  hasTraceCode,
  requiresUpstream,
  stageInfoOf,
  toBatchForm,
  validateBatchForm,
  type BatchField,
  type ImportRowPreview,
} from '@/lib/batchForm'

const auth = useAuthStore()
const dict = useDictionaryStore()

/**
 * 当前登录企业的环节类型。正常来自登录返回的会话；
 * 若本地会话是旧版本写入的（缺 enterpriseType），退化为拉一次企业资料补齐。
 */
const fallbackType = ref<number | null>(null)
const enterpriseType = computed(() => auth.enterpriseType ?? fallbackType.value ?? 1)
const isSourceStage = computed(() => canPublishDirectly(enterpriseType.value))
const isRetail = computed(() => hasTraceCode(enterpriseType.value))

const STAGE_LABELS: Record<number, string> = {
  1: '捕捞与养殖记录',
  2: '冷冻加工记录',
  3: '批发与冷链仓储记录',
  4: '零售记录',
}

/** 接口文档 3.2：列表按状态分组查询，4（已下架）由后端直接过滤，不可浏览 */
const STATUS_TABS = [
  { value: 1, label: '新建' },
  { value: 2, label: '待确认' },
  { value: 3, label: '已确认' },
]

const rows = ref<BatchListItem[]>([])
const total = ref(0)
const loading = ref(false)
const actionLoading = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<BatchDetail>()
const editingId = ref<string>()
const traceVisible = ref(false)
const traceInfo = ref<TraceCodeInfo | null>(null)

const query = reactive<BatchQuery>({ current: 1, size: 10, batchStatus: 1, batchNo: '', productVariety: '', upstreamBatchNo: '' })
const form = ref<BatchForm>(createEmptyBatchForm(1))
const batchNoError = ref('')

// 下架撤销：需求规定列表不返回已下架批号，因此撤销入口做成"下架成功后 60 秒内的提示条"
const undoOffShelf = ref<{ id: string; batchNo: string } | null>(null)
let undoTimer: ReturnType<typeof setTimeout> | undefined

// 批量导入（接口 10.12）
const importVisible = ref(false)
const importRows = ref<ImportRowPreview[]>([])
const importResult = ref<BatchImportResult>()
const importLoading = ref(false)
const importFileName = ref('')

// 上游选择（接口 9.1 / 9.2），仅类型 2/3/4 使用
const upstreamEnterprises = ref<UpstreamEnterprise[]>([])
const upstreamBatches = ref<UpstreamBatch[]>([])
const upstreamLoading = ref(false)
const upstreamBatchLoading = ref(false)
const selectedUpstreamBatch = computed(() => upstreamBatches.value.find((item) => item.id === form.value.upstreamBatchId))

/** 表头行已单独渲染的字段（批号与品种），环节字段区不再重复渲染一次 */
const HEADER_FIELD_KEYS = new Set(['batchNo', 'productVariety'])
const fields = computed<BatchField[]>(() =>
  batchFieldsOf(enterpriseType.value).filter((field) => !HEADER_FIELD_KEYS.has(String(field.key))),
)
/** 当前环节的明细字段值（详情抽屉用） */
const detailStageInfo = computed<Record<string, unknown>>(() =>
  detail.value ? stageInfoOf(detail.value, enterpriseType.value) : {},
)
/** 详情抽屉只展示"有值"的字段，避免整屏 '-' */
const filledDetailFields = computed(() => fields.value.filter((field) => hasValue(detailStageInfo.value[String(field.key)])))

function hasValue(value: unknown): boolean {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function setField(key: keyof BatchForm, value: unknown) {
  ;(form.value as Record<string, unknown>)[key] = value
}

function numValue(key: keyof BatchForm): number | undefined {
  const value = form.value[key]
  return typeof value === 'number' ? value : undefined
}

function dictOptions(field: BatchField): Array<{ label: string; value: string | number }> {
  if (!field.dictType) return []
  const items: DictItem[] = dict.dictMap[field.dictType] ?? []
  return items.map((item) => ({
    label: item.itemValue,
    value: field.dictValue === 'value' ? item.itemValue : Number(item.itemCode),
  }))
}

async function load() {
  loading.value = true
  try {
    const result = await enterpriseApi.batches(query)
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    showError(error instanceof Error ? error.message : '批号列表加载失败')
  } finally {
    loading.value = false
  }
}

function switchStatus(status: number) {
  query.batchStatus = status
  query.current = 1
  load()
}

async function loadUpstreamEnterprises() {
  if (upstreamEnterprises.value.length) return
  upstreamLoading.value = true
  try {
    const result = await enterpriseApi.upstreamEnterprises({ current: 1, size: 100 })
    upstreamEnterprises.value = result.records
  } catch (error) {
    showError(error instanceof Error ? error.message : '上游企业加载失败')
  } finally {
    upstreamLoading.value = false
  }
}

async function loadUpstreamBatches(upstreamEnterpriseId: string) {
  upstreamBatchLoading.value = true
  try {
    const result = await enterpriseApi.upstreamBatches({ current: 1, size: 100, upstreamEnterpriseId })
    upstreamBatches.value = result.records
  } catch (error) {
    upstreamBatches.value = []
    showError(error instanceof Error ? error.message : '上游批号加载失败')
  } finally {
    upstreamBatchLoading.value = false
  }
}

async function onUpstreamEnterpriseChange(id: string) {
  form.value.upstreamBatchId = ''
  upstreamBatches.value = []
  if (id) await loadUpstreamBatches(id)
}

async function openEditor(row?: BatchListItem) {
  const type = enterpriseType.value
  if (row) {
    const data = await enterpriseApi.batchDetail(row.id)
    form.value = toBatchForm(data, type)
    editingId.value = row.id
  } else {
    form.value = createEmptyBatchForm(type)
    editingId.value = undefined
  }
  batchNoError.value = ''

  const dictTypes = fields.value.filter((field) => field.kind === 'dict' && field.dictType).map((field) => field.dictType as string)
  await Promise.all(dictTypes.map((type) => dict.loadDict(type)))

  if (requiresUpstream(type)) {
    await loadUpstreamEnterprises()
    if (form.value.upstreamEnterpriseId) {
      await loadUpstreamBatches(String(form.value.upstreamEnterpriseId))
    } else {
      upstreamBatches.value = []
    }
  }
  dialogVisible.value = true
}

/** 批号唯一性校验（接口 10.8）：失焦即校验，与需求规格说明书的事件描述一致 */
async function checkBatchNo() {
  const value = form.value.batchNo?.trim()
  batchNoError.value = ''
  if (!value) return
  try {
    const result = await enterpriseApi.checkBatchNo(value, editingId.value)
    batchNoError.value = result.available ? '' : '该批号在本企业已存在，请更换'
  } catch {
    // 校验失败不阻塞填写，提交时仍有后端唯一索引兜底
  }
}

async function persist(forceSubmit: boolean) {
  form.value.forceSubmit = forceSubmit
  if (editingId.value) {
    await enterpriseApi.updateBatch(editingId.value, form.value)
    ElMessage.success('批号已更新')
  } else {
    await enterpriseApi.createBatch(form.value)
    ElMessage.success('批号已创建')
  }
  dialogVisible.value = false
  load()
}

async function submit() {
  const message = validateBatchForm(enterpriseType.value, form.value)
  if (message) {
    ElMessage.warning(message)
    return
  }
  if (batchNoError.value) {
    ElMessage.warning(batchNoError.value)
    return
  }

  actionLoading.value = true
  try {
    await persist(false)
  } catch (error) {
    // 接口文档附录 F：温度超限返回 2004，前端二次确认后可带 forceSubmit 强制提交
    if (error instanceof ApiError && error.code === 2004) {
      const confirmed = await ElMessageBox.confirm(error.message, '冷链温度异常', {
        type: 'warning',
        confirmButtonText: '仍然提交',
        cancelButtonText: '返回修改',
      })
        .then(() => true)
        .catch(() => false)
      if (confirmed) {
        try {
          await persist(true)
        } catch (retryError) {
          showError(retryError instanceof Error ? retryError.message : '提交失败')
        }
      } else {
        ElMessage.info('已取消提交，请修正温度后重试')
      }
      return
    }
    showError(error instanceof Error ? error.message : '保存失败')
  } finally {
    actionLoading.value = false
  }
}

async function remove(row: BatchListItem) {
  const confirmed = await ElMessageBox.confirm(`确定删除批号「${row.batchNo}」吗？删除后不可恢复。`, '删除批号', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  try {
    await enterpriseApi.deleteBatch(row.id)
    ElMessage.success('批号已删除')
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '删除失败')
  }
}

/** 10.6 发布：仅链条源头（捕捞与养殖）企业可用 */
async function publish(row: BatchListItem) {
  const confirmed = await ElMessageBox.confirm(
    `发布后批号「${row.batchNo}」可被下游企业选择，确定发布吗？`,
    '发布产品批号',
    { type: 'info', confirmButtonText: '确认发布', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  try {
    await enterpriseApi.publishBatch(row.id)
    ElMessage.success('批号已发布')
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '发布失败')
  }
}

/** 10.9 向上游发送确认请求（类型 2/3/4） */
async function sendConfirm(row: BatchListItem) {
  const confirmed = await ElMessageBox.confirm(
    `将向上游企业发送批号「${row.batchNo}」的进场确认请求，发送后批号进入"待确认"状态。`,
    '发送进场确认请求',
    { type: 'info', confirmButtonText: '确认发送', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  try {
    const result = await enterpriseApi.sendConfirmRequest(row.id)
    ElMessage.success(`已发送确认请求（单号 ${result.requestNo}）`)
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '发送失败')
  }
}

/** 11.5 撤回自己发起的、上游尚未处理的确认请求 */
async function withdraw(row: BatchListItem) {
  try {
    const data = await enterpriseApi.batchDetail(row.id)
    const requestId = data.confirmRequest?.requestId
    if (!requestId) {
      ElMessage.info('该批号没有可撤回的确认请求，请刷新后重试')
      return
    }
    const confirmed = await ElMessageBox.confirm(
      `撤回后批号「${row.batchNo}」回到"新建"状态，可修改后重新发起。确定撤回吗？`,
      '撤回进场确认请求',
      { type: 'warning', confirmButtonText: '确认撤回', cancelButtonText: '取消' },
    )
      .then(() => true)
      .catch(() => false)
    if (!confirmed) return

    const result = await enterpriseApi.withdrawRequest(requestId)
    ElMessage.success(`已撤回确认请求，批号状态：${result.batchStatusName}`)
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '撤回失败')
  }
}

/** 10.7 下架：已确认(3) → 已下架(4) */
async function offShelf(row: BatchListItem) {
  const result = await ElMessageBox.prompt('请输入下架原因（可选）', '下架批号', {
    inputPlaceholder: '例如：库存售罄、冷链维护',
    confirmButtonText: '确认下架',
    cancelButtonText: '取消',
  })
    .then((value) => value)
    .catch(() => null)
  if (!result) return
  try {
    await enterpriseApi.offShelfBatch(row.id, result.value)
    scheduleUndo(row)
    ElMessage.success('批号已下架，不再对下游与消费者可见')
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '下架失败')
  }
}

/** 记录"刚下架的批号"，60 秒内允许一键撤销（下架会连带停用溯源码，必须可逆） */
function scheduleUndo(row: BatchListItem) {
  undoOffShelf.value = { id: row.id, batchNo: row.batchNo }
  if (undoTimer) clearTimeout(undoTimer)
  undoTimer = setTimeout(() => {
    undoOffShelf.value = null
  }, 60_000)
}

async function undoRestore() {
  const target = undoOffShelf.value
  if (!target) return
  try {
    await enterpriseApi.restoreBatch(target.id)
    undoOffShelf.value = null
    if (undoTimer) clearTimeout(undoTimer)
    ElMessage.success(`批号 ${target.batchNo} 已恢复上架，溯源码重新生效`)
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '恢复上架失败')
  }
}

// ---------------- 批量导入 ----------------

/** 模板里的示例值：让用户一眼看出每列该填什么 */
const IMPORT_EXAMPLE: Record<string, string | number> = {
  batchNo: 'YY20260901007',
  productVariety: '带鱼',
  sourceType: 2,
  catchBreedDate: '2026-09-01',
  certificateType: 2,
  certificateNo: 'CERT-2026-0901-001',
  departureTemp: -18.5,
  drugReportNo: 'YWJC-2026-0901',
  fishingLogNo: 'YL-2026-0901',
  publish: '是',
  upstreamBatchNo: 'YY20260901001',
  upstreamEnterpriseName: '青岛远洋渔业有限公司',
  processForm: '鱼片',
  inspectionNo: 'CYJ-2026-0902-001',
  quickFreezeTemp: -36,
  factoryTemp: -19.2,
  productionBatchNo: 'SC-2026-0902-01',
  wholesaleDate: '2026-09-05',
  inboundTemp: -18.6,
  coldStorageTemp: -18.8,
  outboundTemp: -18.5,
  transportToolNo: '沪B-LC0231',
  coldStorageNo: 'SH-LL-02',
  shelfDate: '2026-09-10',
  displayTemp: -19,
  saleStore: '陆家嘴店',
  displayEquipNo: 'LG-01',
  sendConfirmRequest: '是',
  remark: '批量导入示例行，提交前请删除',
}

function openImport() {
  importRows.value = []
  importResult.value = undefined
  importFileName.value = ''
  importVisible.value = true
}

function downloadTemplate() {
  const columns = importColumnsOf(enterpriseType.value)
  const header = columns.map((column) => column.label)
  const sample = columns.map((column) => IMPORT_EXAMPLE[column.key] ?? '')
  downloadTextFile(`产品批号导入模板-${STAGE_LABELS[enterpriseType.value] || '批号'}.csv`, toCsv([header, sample]))
  ElMessage.success('模板已下载，填写后上传即可（示例行请删除）')
}

async function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const text = await file.text()
    const parsed = parseImportMatrix(enterpriseType.value, parseCsv(text))
    if (!parsed.length) {
      ElMessage.warning('没有解析到数据行，请确认文件包含表头与至少一行数据')
      return
    }
    if (parsed.length > 500) {
      ElMessage.warning('单次最多导入 500 行，请拆分后再试')
      return
    }
    importRows.value = parsed
    importResult.value = undefined
    importFileName.value = file.name
  } catch {
    showError('文件读取失败，请确认为 UTF-8 编码的 CSV 文件')
  }
}

const importValidCount = computed(() => importRows.value.filter((row) => !row.error).length)

async function submitImport() {
  const valid = importRows.value.filter((row) => !row.error)
  if (!valid.length) {
    ElMessage.warning('没有可导入的有效行，请先修正预览中的问题')
    return
  }
  importLoading.value = true
  try {
    importResult.value = await enterpriseApi.importBatches(valid.map((row) => row.form))
    const result = importResult.value
    ElMessage.success(`导入完成：成功 ${result.successCount} 条，失败 ${result.failureCount} 条`)
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '批量导入失败')
  } finally {
    importLoading.value = false
  }
}


async function showTrace(row: BatchListItem) {
  try {
    const data = await enterpriseApi.traceCode(row.id)
    if (!data?.traceCode) {
      ElMessage.info('该批号暂未生成溯源码')
      return
    }
    traceInfo.value = data
    traceVisible.value = true
  } catch (error) {
    showError(error instanceof Error ? error.message : '溯源码查询失败')
  }
}

async function openDetail(row: BatchListItem) {
  try {
    detail.value = await enterpriseApi.batchDetail(row.id)
    detailVisible.value = true
  } catch (error) {
    showError(error instanceof Error ? error.message : '批号详情加载失败')
  }
}

onMounted(async () => {
  if (!auth.enterpriseType) {
    try {
      const profile = await auth.loadProfile()
      if (profile && 'enterpriseType' in profile && profile.enterpriseType) {
        fallbackType.value = Number(profile.enterpriseType)
      }
    } catch {
      // 拉取失败时按链条源头处理，用户仍可刷新页面重试
    }
  }
  load()
})

onBeforeUnmount(() => {
  if (undoTimer) clearTimeout(undoTimer)
})
</script>

<template>
  <div class="batch-list-view">
    <PageHeader title="产品批号">
      <template #default>
        <el-button :icon="UploadFilled" @click="openImport">批量导入</el-button>
        <el-button type="primary" :icon="Plus" @click="openEditor()">新建批号</el-button>
      </template>
    </PageHeader>

    <!-- 下架撤销提示条：批号下架后会同时停用溯源码，必须给一次反悔机会 -->
    <div v-if="undoOffShelf" class="undo-bar">
      <span>批号 <strong>{{ undoOffShelf.batchNo }}</strong> 已下架，消费者暂时查不到该溯源码。</span>
      <el-button size="small" type="primary" @click="undoRestore">撤销下架</el-button>
    </div>

    <FilterBar>
      <el-radio-group :model-value="query.batchStatus" @change="(value) => switchStatus(Number(value))">
        <el-radio-button v-for="tab in STATUS_TABS" :key="tab.value" :value="tab.value">{{ tab.label }}</el-radio-button>
      </el-radio-group>
      <el-input v-model="query.batchNo" clearable placeholder="批号" style="width: 170px" @keyup.enter="query.current = 1; load()" />
      <el-input v-model="query.productVariety" clearable placeholder="产品品种" style="width: 160px" @keyup.enter="query.current = 1; load()" />
      <el-input v-model="query.upstreamBatchNo" clearable placeholder="上游批号" style="width: 160px" @keyup.enter="query.current = 1; load()" />
      <template #actions>
        <el-button type="primary" :icon="Search" @click="query.current = 1; load()">查询</el-button>
        <el-button
          :icon="Refresh"
          @click="Object.assign(query, { batchNo: '', productVariety: '', upstreamBatchNo: '', current: 1 }); load()"
        >
          重置
        </el-button>
      </template>
    </FilterBar>

    <section class="data-panel batch-table-panel">
      <div class="data-panel__head">
        <div><strong>批号列表</strong></div>
        <span class="table-caption">共 {{ total }} 条</span>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id" min-width="1000">
        <el-table-column label="批号" min-width="190">
          <template #default="{ row }">
            <div class="batch-no-cell">
              <strong>{{ row.batchNo }}</strong>
              <small>{{ row.productVariety || '品种未填写' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="上游批号" min-width="180">
          <template #default="{ row }">
            <span>{{ row.upstreamBatchNo || '-' }}</span>
            <small v-if="row.upstreamEnterpriseName" class="cell-sub">{{ row.upstreamEnterpriseName }}</small>
          </template>
        </el-table-column>
        <el-table-column label="交接温度" width="120">
          <template #default="{ row }">
            <span :class="{ 'temp-ok': row.coldChainOk, 'temp-alert': row.coldChainOk === false }">
              {{ row.handoverTemp != null ? `${row.handoverTemp}°C` : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag
              :label="row.batchStatusName"
              :tone="row.batchStatus === 3 ? 'success' : row.batchStatus === 2 ? 'warning' : 'info'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" fixed="right" width="320" class-name="table-actions">
          <template #default="{ row }">
            <el-button v-if="row.batchStatus === 1" link :icon="EditPen" @click="openEditor(row)">编辑</el-button>
            <el-button
              v-if="row.batchStatus === 1 && isSourceStage"
              link
              type="primary"
              :icon="Promotion"
              @click="publish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.batchStatus === 1 && !isSourceStage"
              link
              type="primary"
              :icon="Promotion"
              @click="sendConfirm(row)"
            >
              发送确认
            </el-button>
            <el-button v-if="row.batchStatus === 2" link type="warning" :icon="CircleClose" @click="withdraw(row)">撤回</el-button>
            <el-button v-if="row.batchStatus === 3" link type="warning" :icon="Download" @click="offShelf(row)">下架</el-button>
            <el-button v-if="row.batchStatus === 3 && isRetail" link :icon="Link" @click="showTrace(row)">溯源码</el-button>
            <el-button v-if="row.batchStatus === 1" link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
            <el-button link :icon="View" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState title="暂无批号" description="可点击右上角新建批号" />
        </template>
      </el-table>
      <PaginationBar
        :total="total"
        :page="query.current || 1"
        :size="query.size || 10"
        @change="(page) => { query.current = page; load() }"
        @update:size="(size) => { query.size = size; query.current = 1; load() }"
      />
    </section>

    <!-- 新建 / 编辑：字段随登录企业的环节类型变化（附录 A.2） -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑批号' : '新建批号'" width="760px" destroy-on-close>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="产品批号" required :error="batchNoError">
            <el-input
              v-model="form.batchNo"
              :disabled="Boolean(editingId)"
              placeholder="请输入本企业唯一批号"
              @blur="checkBatchNo"
            />
          </el-form-item>
          <el-form-item v-if="isSourceStage" label="产品品种" required>
            <el-input v-model="form.productVariety" placeholder="例如：阿根廷红虾" />
          </el-form-item>
          <el-form-item v-else label="产品品种（上游带出）">
            <el-input :model-value="selectedUpstreamBatch?.productVariety || ''" disabled placeholder="选择上游批号后自动带出" />
          </el-form-item>
        </div>

        <template v-if="!isSourceStage">
          <el-divider content-position="left">上游来源</el-divider>
          <div class="form-grid">
            <el-form-item label="上游企业" required>
              <el-select
                v-model="form.upstreamEnterpriseId"
                filterable
                :loading="upstreamLoading"
                placeholder="请选择上游企业"
                style="width: 100%"
                @change="onUpstreamEnterpriseChange"
              >
                <el-option v-for="item in upstreamEnterprises" :key="item.id" :label="item.enterpriseName" :value="item.id">
                  <span>{{ item.enterpriseName }}</span>
                  <span class="option-meta">{{ item.enterpriseTypeName }} · {{ item.provinceName }}{{ item.cityName }}</span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="上游产品批号" required>
              <el-select
                v-model="form.upstreamBatchId"
                filterable
                :loading="upstreamBatchLoading"
                :disabled="!form.upstreamEnterpriseId"
                placeholder="请选择上游已发布批号"
                style="width: 100%"
              >
                <el-option v-for="item in upstreamBatches" :key="item.id" :label="item.batchNo" :value="item.id" />
              </el-select>
            </el-form-item>
          </div>
          <p v-if="selectedUpstreamBatch" class="form-note">
            来源类型：{{ selectedUpstreamBatch.sourceTypeName || '-' }} · 上游交接温度：{{ selectedUpstreamBatch.handoverTemp ?? '-' }} ℃ ·
            上游冷链：{{ selectedUpstreamBatch.coldChainOk === false ? '异常' : '合格' }}
          </p>
        </template>

        <el-divider content-position="left">{{ STAGE_LABELS[enterpriseType] || '环节记录' }}</el-divider>
        <div class="form-grid">
          <el-form-item
            v-for="field in fields"
            :key="String(field.key)"
            :label="field.unit ? `${field.label}（${field.unit}）` : field.label"
            :required="field.required"
            :class="{ 'form-grid__wide': field.wide }"
          >
            <el-date-picker
              v-if="field.kind === 'date'"
              :model-value="String(form[field.key] ?? '')"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              @update:model-value="(value) => setField(field.key, value)"
            />
            <el-input-number
              v-else-if="field.kind === 'number'"
              :model-value="numValue(field.key)"
              :min="-60"
              :max="30"
              :precision="1"
              controls-position="right"
              style="width: 100%"
              :placeholder="field.placeholder"
              @update:model-value="(value) => setField(field.key, value ?? undefined)"
            />
            <el-select
              v-else-if="field.kind === 'select'"
              :model-value="form[field.key]"
              style="width: 100%"
              @update:model-value="(value) => setField(field.key, value)"
            >
              <el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-select
              v-else-if="field.kind === 'dict'"
              :model-value="form[field.key]"
              placeholder="请选择"
              style="width: 100%"
              @update:model-value="(value) => setField(field.key, value)"
            >
              <el-option v-for="option in dictOptions(field)" :key="String(option.value)" :label="option.label" :value="option.value" />
            </el-select>
            <el-input
              v-else
              :model-value="String(form[field.key] ?? '')"
              :placeholder="field.placeholder"
              @update:model-value="(value) => setField(field.key, value)"
            />
          </el-form-item>
        </div>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>

        <div class="form-switches">
          <el-checkbox v-if="isSourceStage" v-model="form.publish">保存后直接发布（可被下游选择）</el-checkbox>
          <el-checkbox v-else v-model="form.sendConfirmRequest">保存后同时向上游发送进场确认请求</el-checkbox>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submit">保存批号</el-button>
      </template>
    </el-dialog>

    <!-- 批号详情（接口 10.2） -->
    <el-drawer v-model="detailVisible" title="批号详情" size="560px">
      <template v-if="detail">
        <div class="detail-hero">
          <span class="detail-hero__avatar"><el-icon><View /></el-icon></span>
          <div>
            <h3>{{ detail.batchNo }}</h3>
            <p>{{ detail.productVariety || '品种未填写' }} · {{ detail.enterpriseTypeName || '' }}</p>
          </div>
          <StatusTag :label="detail.batchStatusName" :tone="detail.batchStatus === 3 ? 'success' : detail.batchStatus === 2 ? 'warning' : 'info'" />
        </div>
        <div class="detail-list">
          <div><span>来源类型</span><strong>{{ detail.sourceTypeName || '-' }}</strong></div>
          <div><span>上游企业</span><strong>{{ detail.upstreamEnterpriseName || '链条源头' }}</strong></div>
          <div><span>上游批号</span><strong>{{ detail.upstreamBatchNo || '-' }}</strong></div>
          <div><span>上游产地</span><strong>{{ detail.upstreamProvinceName ? `${detail.upstreamProvinceName}${detail.upstreamCityName || ''}` : '-' }}</strong></div>
          <div><span>交接温度</span><strong>{{ detail.handoverTemp != null ? `${detail.handoverTemp} ℃` : '-' }}</strong></div>
          <div><span>冷链状态</span><strong>{{ detail.coldChainOk === false ? '异常' : '合格' }}</strong></div>
          <div><span>创建时间</span><strong>{{ detail.createTime || '-' }}</strong></div>
          <div><span>发布时间</span><strong>{{ detail.publishTime || '-' }}</strong></div>
          <div v-if="detail.offShelfTime"><span>下架时间</span><strong>{{ detail.offShelfTime }}</strong></div>
          <div><span>备注</span><strong>{{ detail.remark || '-' }}</strong></div>
        </div>

        <template v-if="filledDetailFields.length">
          <el-divider content-position="left">{{ STAGE_LABELS[enterpriseType] || '环节记录' }}</el-divider>
          <div class="detail-list">
            <div v-for="field in filledDetailFields" :key="String(field.key)">
              <span>{{ field.label }}</span>
              <strong>{{ detailStageInfo[String(field.key)] }}</strong>
            </div>
          </div>
        </template>

        <template v-if="detail.confirmRequest">
          <el-divider content-position="left">进场确认</el-divider>
          <div class="detail-list">
            <div><span>请求单号</span><strong>{{ detail.confirmRequest.requestNo }}</strong></div>
            <div><span>请求状态</span><strong>{{ detail.confirmRequest.requestStatusName }}</strong></div>
            <div><span>请求时间</span><strong>{{ detail.confirmRequest.requestTime || '-' }}</strong></div>
            <div><span>处理时间</span><strong>{{ detail.confirmRequest.handleTime || '-' }}</strong></div>
            <div><span>处理说明</span><strong>{{ detail.confirmRequest.handleRemark || '-' }}</strong></div>
          </div>
        </template>

        <template v-if="detail.traceCode">
          <el-divider content-position="left">溯源标识码</el-divider>
          <div class="detail-list">
            <div><span>标识码</span><strong>{{ detail.traceCode.traceCode }}</strong></div>
            <div><span>状态</span><strong>{{ detail.traceCode.statusName }}</strong></div>
            <div><span>生成时间</span><strong>{{ detail.traceCode.generateTime || '-' }}</strong></div>
            <div><span>查询次数</span><strong>{{ detail.traceCode.queryCount ?? 0 }}</strong></div>
          </div>
        </template>

        <!-- 状态流转时间线（接口 10.2 statusLogs）：谁在什么时候改了什么状态 -->
        <template v-if="detail.statusLogs?.length">
          <el-divider content-position="left">状态流转</el-divider>
          <el-timeline class="status-timeline">
            <el-timeline-item
              v-for="(log, index) in detail.statusLogs"
              :key="index"
              :timestamp="log.createTime"
              placement="top"
              color="#20a79a"
            >
              <div class="status-timeline__head">
                <strong>{{ log.action }}</strong>
                <span>{{ log.fromStatusName ? `${log.fromStatusName} → ${log.toStatusName}` : log.toStatusName }}</span>
              </div>
              <small>
                操作人：{{ log.operatorName || '系统' }}
                <template v-if="log.remark"> · {{ log.remark }}</template>
              </small>
            </el-timeline-item>
          </el-timeline>
        </template>
      </template>
    </el-drawer>

    <TraceQrDialog v-model="traceVisible" :info="traceInfo" />

    <!-- 批量导入：下载模板 → 上传 CSV → 预览校验 → 逐行提交 -->
    <el-dialog v-model="importVisible" title="批量导入产品批号" width="860px" destroy-on-close>
      <div class="import-steps">
        <div class="import-step">
          <span class="import-step__index">01</span>
          <div>
            <strong>下载模板</strong>
            <p>模板列与当前企业环节匹配；已经用过自己系统的企业，把导出的批号按列对进来即可。</p>
          </div>
          <el-button :icon="Document" @click="downloadTemplate">下载 CSV 模板</el-button>
        </div>
        <div class="import-step">
          <span class="import-step__index">02</span>
          <div>
            <strong>上传文件</strong>
            <p>支持 UTF-8 的 CSV；单次最多 500 行。下游环节用「上游产品批号」指定来源，无需填上游主键。</p>
          </div>
          <label class="import-upload">
            <input type="file" accept=".csv,text/csv" @change="onImportFile" />
            <el-button :icon="UploadFilled">选择 CSV 文件</el-button>
          </label>
        </div>
        <div class="import-step">
          <span class="import-step__index">03</span>
          <div>
            <strong>预览并提交</strong>
            <p>下面会先做一次本地校验，标红行不会被提交，正确行逐条入库、互不影响。</p>
          </div>
          <span v-if="importFileName" class="import-file">{{ importFileName }}</span>
        </div>
      </div>

      <template v-if="importRows.length">
        <div class="import-summary">
          共 {{ importRows.length }} 行，可导入 <strong>{{ importValidCount }}</strong> 行，待修正
          <strong class="import-summary__error">{{ importRows.length - importValidCount }}</strong> 行
        </div>
        <el-table :data="importRows" max-height="260" size="small">
          <el-table-column prop="rowNo" label="行号" width="70" />
          <el-table-column prop="batchNo" label="产品批号" min-width="150" />
          <el-table-column label="校验结果" min-width="240">
            <template #default="{ row }">
              <span v-if="row.error" class="import-error">{{ row.error }}</span>
              <StatusTag v-else label="可导入" tone="success" />
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template v-if="importResult">
        <el-divider content-position="left">导入结果</el-divider>
        <el-table :data="importResult.items" max-height="220" size="small">
          <el-table-column prop="rowNo" label="行号" width="70" />
          <el-table-column prop="batchNo" label="产品批号" min-width="150" />
          <el-table-column label="结果" min-width="240">
            <template #default="{ row }">
              <StatusTag v-if="row.success" label="已导入" tone="success" />
              <span v-else class="import-error">{{ row.message }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="importLoading"
          :disabled="!importValidCount"
          @click="submitImport"
        >
          导入 {{ importValidCount }} 行
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.undo-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 12px 18px;
  border: 1px solid var(--teal-500);
  border-radius: 12px;
  background: var(--teal-100);
  color: var(--ink-700);
  font-size: 13px;
}

.status-timeline__head {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.status-timeline__head span {
  color: var(--ink-500);
  font-size: 12px;
}

.status-timeline small {
  color: var(--ink-500);
  font-size: 11px;
}

.import-steps {
  display: grid;
  gap: 12px;
  margin-bottom: 18px;
}

.import-step {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid var(--mist-200);
  border-radius: 12px;
  background: var(--paper);
}

.import-step__index {
  color: var(--teal-600);
  font-size: 18px;
  font-weight: 800;
}

.import-step > div {
  flex: 1;
}

.import-step strong {
  font-size: 14px;
}

.import-step p {
  margin: 4px 0 0;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.7;
}

.import-upload input {
  display: none;
}

.import-file {
  color: var(--ink-500);
  font-size: 12px;
}

.import-summary {
  margin-bottom: 10px;
  color: var(--ink-700);
  font-size: 13px;
}

.import-summary__error,
.import-error {
  color: var(--coral-500);
}

.form-note {
  margin: -6px 0 16px;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.7;
}

.option-meta {
  float: right;
  margin-left: 16px;
  color: var(--ink-500);
  font-size: 12px;
}
</style>
