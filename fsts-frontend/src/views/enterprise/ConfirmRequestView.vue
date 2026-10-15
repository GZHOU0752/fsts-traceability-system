<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { showError } from '@/lib/feedback'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, CircleCheck, Close, View } from '@element-plus/icons-vue'
import { enterpriseApi, type ConfirmQuery } from '@/api/enterprise'
import type { ConfirmRequest, ConfirmRequestDetail } from '@/types/domain'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'

const REQUEST_STATUS_OPTIONS = [
  { label: '待确认', value: 1 },
  { label: '已确认', value: 2 },
  { label: '已拒绝', value: 3 },
  { label: '已撤回', value: 4 },
]

const rows = ref<ConfirmRequest[]>([])
const total = ref(0)
const loading = ref(false)
const actionLoading = ref(false)
const detail = ref<ConfirmRequestDetail>()
const drawerVisible = ref(false)
const pendingTotal = ref(0)

const query = reactive<ConfirmQuery>({
  current: 1,
  size: 10,
  requestStatus: 1,
  downstreamEnterpriseName: '',
  batchNo: '',
})

function statusTone(status: number): 'warning' | 'success' | 'danger' | 'info' {
  if (status === 1) return 'warning'
  if (status === 2) return 'success'
  if (status === 4) return 'info'
  return 'danger'
}

async function load() {
  loading.value = true
  try {
    const result = await enterpriseApi.confirmRequests(query)
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    showError(error instanceof Error ? error.message : '确认请求加载失败')
  } finally {
    loading.value = false
  }
}

/** 顶部角标始终显示"待确认"数量，避免用户切到其他状态后误判积压量 */
async function loadPendingCount() {
  try {
    const result = await enterpriseApi.confirmRequests({ current: 1, size: 1, requestStatus: 1 })
    pendingTotal.value = result.total
  } catch {
    pendingTotal.value = 0
  }
}

async function open(row: ConfirmRequest) {
  try {
    detail.value = await enterpriseApi.confirmRequestDetail(row.id)
    drawerVisible.value = true
  } catch (error) {
    showError(error instanceof Error ? error.message : '确认请求详情加载失败')
  }
}

async function confirm(row: ConfirmRequest) {
  const result = await ElMessageBox.prompt('可以补充一条交接说明', '确认进场', {
    inputPlaceholder: '例如：包装完好，温度正常',
    inputType: 'textarea',
    confirmButtonText: '确认进场',
    cancelButtonText: '取消',
  })
    .then((value) => value)
    .catch(() => null)
  if (!result) return

  actionLoading.value = true
  try {
    await enterpriseApi.confirmRequest(row.id, result.value)
    ElMessage.success('已确认进场')
    load()
    loadPendingCount()
  } catch (error) {
    showError(error instanceof Error ? error.message : '确认失败')
  } finally {
    actionLoading.value = false
  }
}

async function reject(row: ConfirmRequest) {
  const result = await ElMessageBox.prompt('拒绝后下游批号会回到"新建"状态，请说明原因', '拒绝进场', {
    inputPlaceholder: '拒绝原因不能为空',
    inputType: 'textarea',
    confirmButtonText: '确认拒绝',
    cancelButtonText: '取消',
    inputValidator: (value: string) => (value && value.trim() ? true : '拒绝原因不能为空'),
  })
    .then((value) => value)
    .catch(() => null)
  if (!result?.value?.trim()) return

  actionLoading.value = true
  try {
    await enterpriseApi.rejectRequest(row.id, result.value)
    ElMessage.success('已拒绝该请求')
    load()
    loadPendingCount()
  } catch (error) {
    showError(error instanceof Error ? error.message : '拒绝失败')
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  load()
  loadPendingCount()
})
</script>

<template>
  <div class="confirm-view">
    <PageHeader title="进场确认">
      <template #default>
        <el-tag type="warning" effect="plain">待处理 {{ pendingTotal }}</el-tag>
      </template>
    </PageHeader>

    <FilterBar>
      <el-select v-model="query.requestStatus" placeholder="请求状态" style="width: 140px" @change="query.current = 1; load()">
        <el-option v-for="item in REQUEST_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="query.downstreamEnterpriseName" clearable placeholder="关联企业" style="width: 190px" />
      <el-input v-model="query.batchNo" clearable placeholder="批号" style="width: 190px" />
      <template #actions>
        <el-button type="primary" :icon="Search" @click="query.current = 1; load()">查询</el-button>
        <el-button
          :icon="Refresh"
          @click="Object.assign(query, { downstreamEnterpriseName: '', batchNo: '', current: 1 }); load()"
        >
          重置
        </el-button>
      </template>
    </FilterBar>

    <section class="data-panel confirm-table-panel">
      <div class="data-panel__head">
        <div>
          <strong>交接请求</strong>
          <span>这里是"本企业作为接收方"的请求；自己发起的请求可在「产品批号」页撤回</span>
        </div>
      </div>
      <el-table v-loading="loading" :data="rows" min-width="900">
        <el-table-column label="请求单号" min-width="185">
          <template #default="{ row }">
            <div class="request-cell">
              <strong>{{ row.requestNo }}</strong>
              <small>{{ row.requestTime || '-' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="批号链路" min-width="245">
          <template #default="{ row }">
            <div>
              <strong>{{ row.upstreamBatchNo || row.batchNo }}</strong>
              <small class="cell-sub">{{ row.fromEnterpriseName }} → {{ row.toEnterpriseName }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="交接温度" width="120">
          <template #default="{ row }">
            <span :class="{ 'temp-ok': row.handoverTemp != null && row.handoverTemp <= -18, 'temp-alert': row.handoverTemp != null && row.handoverTemp > -18 }">
              {{ row.handoverTemp != null ? `${row.handoverTemp}°C` : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="requestStatusName" label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :label="row.requestStatusName" :tone="statusTone(row.requestStatus)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="260" class-name="table-actions">
          <template #default="{ row }">
            <el-button link :icon="View" @click="open(row)">详情</el-button>
            <template v-if="row.requestStatus === 1">
              <el-button link type="primary" :icon="CircleCheck" :loading="actionLoading" @click="confirm(row)">确认进场</el-button>
              <el-button link type="danger" :icon="Close" @click="reject(row)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState title="暂无确认请求" description="当前筛选范围内没有待处理交接" />
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

    <el-drawer v-model="drawerVisible" title="确认请求详情" size="520px">
      <template v-if="detail">
        <div class="request-hero">
          <span class="request-hero__icon"><el-icon><CircleCheck /></el-icon></span>
          <div>
            <h3>{{ detail.requestNo }}</h3>
            <p>{{ detail.requestStatusName }} · {{ detail.requestTime }}</p>
          </div>
        </div>
        <div class="detail-list">
          <div><span>发起企业</span><strong>{{ detail.fromEnterpriseName || '-' }}</strong></div>
          <div><span>发起企业类型</span><strong>{{ detail.fromEnterpriseTypeName || '-' }}</strong></div>
          <div><span>目标企业</span><strong>{{ detail.toEnterpriseName || '-' }}</strong></div>
          <div><span>下游批号</span><strong>{{ detail.downstreamBatch?.batchNo || detail.batchNo }}</strong></div>
          <div><span>上游批号</span><strong>{{ detail.upstreamBatch?.batchNo || detail.upstreamBatchNo || '-' }}</strong></div>
          <div><span>交接温度</span><strong>{{ detail.handoverTemp != null ? `${detail.handoverTemp} ℃` : '-' }}</strong></div>
          <div><span>处理时间</span><strong>{{ detail.handleTime || '-' }}</strong></div>
          <div><span>处理说明</span><strong>{{ detail.handleRemark || '-' }}</strong></div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>
