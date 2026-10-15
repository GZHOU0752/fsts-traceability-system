<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showError } from '@/lib/feedback'
import { ElMessage } from 'element-plus'
import { Search, Refresh, ArrowRight, Box } from '@element-plus/icons-vue'
import { enterpriseApi } from '@/api/enterprise'
import { useAuthStore } from '@/stores/auth'
import type { UpstreamBatch, UpstreamEnterprise } from '@/types/domain'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const auth = useAuthStore()
/** 链条源头（类型 1）没有上游企业，后端会直接返回 400，页面提前给出空态而不是报错 */
const isSourceStage = computed(() => auth.enterpriseType === 1)

const enterprises = ref<UpstreamEnterprise[]>([])
const batches = ref<UpstreamBatch[]>([])
const enterpriseLoading = ref(false)
const batchLoading = ref(false)
const selectedId = ref<string>()
const query = reactive({ enterpriseName: '', batchNo: '' })

async function loadEnterprises() {
  if (isSourceStage.value) return
  enterpriseLoading.value = true
  try {
    const result = await enterpriseApi.upstreamEnterprises({ current: 1, size: 50, enterpriseName: query.enterpriseName })
    enterprises.value = result.records
  } catch (error) {
    showError(error instanceof Error ? error.message : '上游企业加载失败')
  } finally {
    enterpriseLoading.value = false
  }
}

async function selectEnterprise(id: string) {
  selectedId.value = id
  batches.value = []
  batchLoading.value = true
  try {
    const result = await enterpriseApi.upstreamBatches({ current: 1, size: 50, upstreamEnterpriseId: id, batchNo: query.batchNo })
    batches.value = result.records
  } catch (error) {
    showError(error instanceof Error ? error.message : '上游批号加载失败')
  } finally {
    batchLoading.value = false
  }
}

onMounted(loadEnterprises)
</script>

<template>
  <div class="upstream-view">
    <PageHeader title="上游数据">
      <template #default>
        <el-button :icon="Refresh" @click="loadEnterprises">刷新</el-button>
      </template>
    </PageHeader>

    <section v-if="isSourceStage" class="data-panel">
      <EmptyState
        title="链条源头没有上游企业"
        description="捕捞与养殖企业处于溯源链起点，批号发布后由下游企业选择；这里不需要上游数据。"
      />
    </section>

    <div v-else class="upstream-layout">
      <section class="data-panel upstream-enterprises">
        <div class="data-panel__head">
          <div><strong>上游企业</strong><span>{{ enterprises.length }} 家可选</span></div>
        </div>
        <div class="upstream-search">
          <el-input v-model="query.enterpriseName" clearable placeholder="搜索企业" @keyup.enter="loadEnterprises">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <div v-loading="enterpriseLoading" class="upstream-list">
          <button
            v-for="enterprise in enterprises"
            :key="enterprise.id"
            class="upstream-item"
            :class="{ 'is-selected': selectedId === enterprise.id }"
            @click="selectEnterprise(enterprise.id)"
          >
            <span class="upstream-item__avatar"><el-icon><Box /></el-icon></span>
            <span>
              <strong>{{ enterprise.enterpriseName }}</strong>
              <small>{{ enterprise.enterpriseTypeName }} · {{ enterprise.provinceName }}{{ enterprise.cityName }}</small>
            </span>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <EmptyState v-if="!enterprises.length && !enterpriseLoading" title="暂无可用企业" />
        </div>
      </section>

      <section class="data-panel upstream-batches">
        <div class="data-panel__head">
          <div><strong>已发布批号</strong><span>仅展示可被选择的已确认批号</span></div>
        </div>
        <div v-if="selectedId" class="upstream-search">
          <el-input v-model="query.batchNo" clearable placeholder="搜索上游批号" @keyup.enter="selectEnterprise(selectedId!)">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <div v-loading="batchLoading" class="upstream-list">
          <!-- 原实现的"查看"按钮没有绑定任何事件，属于死控件；
               这里改为直接展示下游选货需要的全部字段，不再放无效按钮。 -->
          <article v-for="batch in batches" :key="batch.id" class="upstream-batch">
            <div class="upstream-batch__main">
              <strong>{{ batch.batchNo }}</strong>
              <small>{{ batch.productVariety || '品种未填写' }} · 发布 {{ batch.publishTime || '-' }}</small>
            </div>
            <div class="upstream-batch__meta">
              <el-tag size="small" effect="plain">{{ batch.sourceTypeName || '来源未标注' }}</el-tag>
              <el-tag size="small" effect="plain" :type="batch.coldChainOk === false ? 'danger' : 'success'">
                交接 {{ batch.handoverTemp != null ? `${batch.handoverTemp} ℃` : '-' }}
              </el-tag>
            </div>
          </article>
          <EmptyState v-if="selectedId && !batches.length && !batchLoading" title="暂无已发布批号" description="该企业当前没有可选的已确认批号" />
          <div v-if="!selectedId" class="upstream-placeholder">
            <span>01</span>
            <strong>选择上游企业</strong>
            <p>这里会展示该企业当前可被选择的已发布产品批号，供新建批号时引用。</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 批号行只是信息展示：下游企业没有"查看上游批号详情"的接口（10.2 只能查本企业批号），
   所以这里必须去掉全局样式带来的 pointer 光标与 hover 高亮，
   否则鼠标移上去像能点、点了却什么都没有。 */
.upstream-list .upstream-batch,
.upstream-list .upstream-batch:hover {
  background: transparent;
  cursor: default;
}

.upstream-batch {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border: 1px solid var(--mist-200);
  border-radius: 12px;
  background: var(--paper);
}

.upstream-batch__main {
  display: grid;
  gap: 3px;
}

.upstream-batch__meta {
  display: flex;
  gap: 8px;
}
</style>
