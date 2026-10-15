<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showError } from '@/lib/feedback'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh, View, EditPen, Delete, OfficeBuilding } from '@element-plus/icons-vue'
import { adminApi, type EnterpriseQuery } from '@/api/admin'
import type { EnterpriseDetail, EnterpriseForm, EnterpriseListItem } from '@/types/domain'
import { useDictionaryStore } from '@/stores/dictionary'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  createEmptyEnterpriseForm,
  licenseFieldsOf,
  resetLicenseFields,
  toEnterpriseForm,
  toEnterprisePayload,
  validateEnterpriseForm,
  type LicenseField,
} from '@/lib/enterpriseForm'

const dict = useDictionaryStore()
const loading = ref(false)
const actionLoading = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<string>()
const detail = ref<EnterpriseDetail>()
const formRef = ref<FormInstance>()
const rows = ref<EnterpriseListItem[]>([])
const total = ref(0)

const query = reactive<EnterpriseQuery>({
  current: 1,
  size: 10,
  enterpriseName: '',
  creditCode: '',
  provinceCode: '',
  cityCode: '',
  enterpriseType: undefined,
  status: undefined,
})

const form = reactive<EnterpriseForm>(createEmptyEnterpriseForm())
/**
 * 必须用 computed 而不是 `const provinces = dict.provinces`：
 * Pinia 的 setup store 里 provinces 是 ref，读取时拿到的是"当时那个数组对象"，
 * 而 loadProvinces() 是整体替换（provinces.value = 新数组），
 * 直接捕获的话，下拉框会一直绑定在启动时的空数组上（表现为省份列表无数据）。
 */
const provinces = computed(() => dict.provinces)
const cities = ref<{ regionCode: string; regionName: string }[]>([])
const typeOptions = [
  { value: 1, label: '捕捞 / 养殖' },
  { value: 2, label: '冷冻加工' },
  { value: 3, label: '批发流通' },
  { value: 4, label: '零售终端' },
]

/**
 * 唯一性校验结果（接口 6.6）。
 * 之前虽然封装了接口却没人调用，用户只能"填完提交→收到 2002 报错"，
 * 与需求规格说明书 4.1"失焦即校验"的事件描述不符。
 */
const uniqueErrors = reactive<Record<string, string>>({})
const UNIQUE_FIELDS = [
  { key: 'enterpriseName', label: '企业名称' },
  { key: 'loginName', label: '登录账号' },
  { key: 'creditCode', label: '统一社会信用代码' },
] as const

const licenseFields = computed<LicenseField[]>(() => licenseFieldsOf(form.enterpriseType))
const detailLicenseFields = computed<LicenseField[]>(() => licenseFieldsOf(detail.value?.enterpriseType))

const rules: FormRules = {
  enterpriseName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  loginName: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  creditCode: [{ required: true, message: '请输入统一信用代码', trigger: 'blur' }],
  enterpriseType: [{ required: true, message: '请选择企业类型', trigger: 'change' }],
  provinceCode: [{ required: true, message: '请选择省份', trigger: 'change' }],
  cityCode: [{ required: true, message: '请选择城市', trigger: 'change' }],
}

async function load() {
  loading.value = true
  try {
    const result = await adminApi.enterprises(query)
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    showError(error instanceof Error ? error.message : '企业列表加载失败')
  } finally {
    loading.value = false
  }
}

async function changeProvince(code: string) {
  form.cityCode = ''
  cities.value = code ? await dict.loadCities(code) : []
}

function clearUniqueErrors() {
  for (const item of UNIQUE_FIELDS) uniqueErrors[item.key] = ''
}

/** 资质字段是动态渲染的，用统一入口写值，避免在模板里对联合类型做断言赋值 */
function setLicenseValue(key: keyof EnterpriseForm, value: string | number | undefined) {
  ;(form as Record<string, unknown>)[key] = value
}

/** 数值型资质字段的当前值（冷库容量等） */
function numLicenseValue(key: keyof EnterpriseForm): number | undefined {
  const value = form[key]
  return typeof value === 'number' ? value : undefined
}

function resetForm() {
  Object.assign(form, createEmptyEnterpriseForm())
  editingId.value = undefined
  cities.value = []
  clearUniqueErrors()
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: EnterpriseListItem) {
  const data = await adminApi.enterpriseDetail(row.id)
  Object.assign(form, toEnterpriseForm(data))
  editingId.value = row.id
  cities.value = await dict.loadCities(form.provinceCode)
  clearUniqueErrors()
  dialogVisible.value = true
}

async function openDetail(row: EnterpriseListItem) {
  detail.value = await adminApi.enterpriseDetail(row.id)
  detailVisible.value = true
}

/** 切换企业类型：清空另一套资质字段，避免把无关资质提交上去 */
function onTypeChange() {
  resetLicenseFields(form)
  formRef.value?.clearValidate(['enterpriseType'])
}

async function checkUnique(key: (typeof UNIQUE_FIELDS)[number]['key']) {
  const label = UNIQUE_FIELDS.find((item) => item.key === key)?.label ?? '字段'
  const value = String(form[key] ?? '').trim()
  uniqueErrors[key] = ''
  if (!value) return
  try {
    const result = await adminApi.checkEnterprise(key, value, editingId.value)
    uniqueErrors[key] = result.available ? '' : `该${label}已被使用`
  } catch {
    // 校验接口失败不阻塞填写，最终仍由提交时的后端唯一索引兜底
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请先补全标红的必填项')
    return
  }

  const message = validateEnterpriseForm(form)
  if (message) {
    ElMessage.warning(message)
    return
  }
  if (Object.values(uniqueErrors).some((item) => item)) {
    ElMessage.warning('请先修正唯一性校验未通过的字段')
    return
  }

  actionLoading.value = true
  try {
    const payload = toEnterprisePayload(form, Boolean(editingId.value))
    if (editingId.value) {
      // 后端不允许修改企业类型，编辑时表单已禁用该字段
      await adminApi.updateEnterprise(editingId.value, payload)
      ElMessage.success('企业资料已更新')
    } else {
      const created = await adminApi.createEnterprise(payload)
      ElMessage.success('企业已创建')
      await ElMessageBox.alert(
        `企业编码：${created.enterpriseCode}<br/>初始密码：<strong>${created.initialPassword}</strong><br/>请转告企业首次登录后立即修改密码。`,
        '企业创建成功',
        { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' },
      )
    }
    dialogVisible.value = false
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '保存失败')
  } finally {
    actionLoading.value = false
  }
}

async function remove(row: EnterpriseListItem) {
  await ElMessageBox.confirm(`确定删除「${row.enterpriseName}」吗？删除后不可恢复。`, '删除企业', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  })
  try {
    await adminApi.deleteEnterprise(row.id)
    ElMessage.success('企业已删除')
    load()
  } catch (error) {
    showError(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(async () => {
  await dict.loadProvinces()
  load()
})
</script>

<template>
  <div class="enterprise-list-view">
    <PageHeader title="节点企业">
      <template #default>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增企业</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-input v-model="query.enterpriseName" clearable placeholder="企业名称" style="width: 190px" @keyup.enter="query.current = 1; load()" />
      <el-input v-model="query.creditCode" clearable placeholder="统一信用代码" style="width: 190px" @keyup.enter="query.current = 1; load()" />
      <el-select v-model="query.enterpriseType" clearable placeholder="企业类型" style="width: 150px">
        <el-option v-for="option in typeOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态" style="width: 130px">
        <el-option label="正常" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <template #actions>
        <el-button :icon="Search" type="primary" @click="query.current = 1; load()">查询</el-button>
        <el-button
          :icon="Refresh"
          @click="Object.assign(query, { enterpriseName: '', creditCode: '', enterpriseType: undefined, status: undefined, current: 1 }); load()"
        >
          重置
        </el-button>
      </template>
    </FilterBar>

    <section class="data-panel enterprise-table-panel">
      <div class="data-panel__head">
        <div><strong>企业名录</strong><span>共 {{ total }} 家企业</span></div>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id" min-width="980">
        <el-table-column label="企业" min-width="230">
          <template #default="{ row }">
            <div class="entity-cell">
              <span class="entity-cell__avatar"><el-icon><OfficeBuilding /></el-icon></span>
              <div><strong>{{ row.enterpriseName }}</strong><small>{{ row.enterpriseCode }}</small></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enterpriseTypeName" label="类型" width="125" />
        <el-table-column label="所在地区" min-width="170">
          <template #default="{ row }">{{ row.provinceName }} · {{ row.cityName }}</template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="105" />
        <el-table-column prop="contactPhone" label="联系电话" width="140" />
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <StatusTag :label="row.statusName" :tone="row.status === 1 ? 'success' : 'danger'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="210" class-name="table-actions">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button link :icon="EditPen" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState title="暂无节点企业" description="可点击右上角新增企业" />
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

    <!-- 新建 / 编辑：公共字段 + 按企业类型联动的资质字段（接口 6.3 / 6.4） -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑企业' : '新增企业'" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="企业名称" prop="enterpriseName" :error="uniqueErrors.enterpriseName">
            <el-input v-model="form.enterpriseName" placeholder="请输入企业全称" @blur="checkUnique('enterpriseName')" />
          </el-form-item>
          <el-form-item label="登录账号" prop="loginName" :error="uniqueErrors.loginName">
            <el-input
              v-model="form.loginName"
              placeholder="用于企业端登录"
              :disabled="Boolean(editingId)"
              @blur="checkUnique('loginName')"
            />
          </el-form-item>
          <el-form-item label="统一信用代码" prop="creditCode" :error="uniqueErrors.creditCode">
            <el-input v-model="form.creditCode" placeholder="请输入统一社会信用代码" @blur="checkUnique('creditCode')" />
          </el-form-item>
          <el-form-item label="企业类型" prop="enterpriseType">
            <!-- 已注册企业变更类型会破坏既有溯源链，后端明确拒绝，因此编辑时禁用 -->
            <el-select v-model="form.enterpriseType" style="width: 100%" :disabled="Boolean(editingId)" @change="onTypeChange">
              <el-option v-for="option in typeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="!editingId" label="初始密码">
            <el-input v-model="form.password" type="password" show-password placeholder="留空使用默认密码 123456" />
          </el-form-item>
          <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
          <el-form-item label="法定代表人"><el-input v-model="form.legalPerson" /></el-form-item>
          <el-form-item label="省份" prop="provinceCode">
            <el-select v-model="form.provinceCode" filterable style="width: 100%" @change="changeProvince">
              <el-option v-for="item in provinces" :key="item.regionCode" :label="item.regionName" :value="item.regionCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="城市" prop="cityCode">
            <el-select v-model="form.cityCode" filterable style="width: 100%">
              <el-option v-for="item in cities" :key="item.regionCode" :label="item.regionName" :value="item.regionCode" />
            </el-select>
          </el-form-item>
          <el-form-item class="form-grid__wide" label="详细地址"><el-input v-model="form.address" /></el-form-item>
          <el-form-item label="账号状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="正常" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
        </div>

        <el-divider content-position="left">
          资质证件（{{ typeOptions.find((item) => item.value === form.enterpriseType)?.label || '-' }}）
        </el-divider>
        <p class="form-note">
          资质字段按企业类型必填，与后端校验规则一致；不属于当前类型的证件后端会忽略。
        </p>
        <div class="form-grid">
          <el-form-item
            v-for="field in licenseFields"
            :key="String(field.key)"
            :label="field.label"
            :required="field.required"
            :class="{ 'form-grid__wide': field.wide }"
          >
            <el-input-number
              v-if="field.type === 'number'"
              :model-value="numLicenseValue(field.key)"
              :min="0"
              :precision="2"
              controls-position="right"
              style="width: 100%"
              :placeholder="field.placeholder"
              @update:model-value="(value) => setLicenseValue(field.key, value ?? undefined)"
            />
            <el-input
              v-else
              :model-value="String(form[field.key] ?? '')"
              :placeholder="field.placeholder"
              @update:model-value="(value: string) => setLicenseValue(field.key, value)"
            />
            <small v-if="field.hint" class="form-hint">{{ field.hint }}</small>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submit">保存企业</el-button>
      </template>
    </el-dialog>

    <!-- 详情：列表接口为了控制体积不返回资质，这里按类型完整展示（接口 6.2） -->
    <el-drawer v-model="detailVisible" title="企业详情" size="560px">
      <template v-if="detail">
        <div class="detail-hero">
          <span class="detail-hero__avatar"><el-icon><OfficeBuilding /></el-icon></span>
          <div>
            <h3>{{ detail.enterpriseName }}</h3>
            <p>{{ detail.enterpriseTypeName }} · {{ detail.enterpriseCode }}</p>
          </div>
          <StatusTag :label="detail.statusName || '-'" :tone="detail.status === 1 ? 'success' : 'danger'" />
        </div>
        <div class="detail-list">
          <div><span>登录账号</span><strong>{{ detail.loginName || '-' }}</strong></div>
          <div><span>统一信用代码</span><strong>{{ detail.creditCode }}</strong></div>
          <div><span>法定代表人</span><strong>{{ detail.legalPerson || '-' }}</strong></div>
          <div><span>联系人</span><strong>{{ detail.contactPerson || '-' }} {{ detail.contactPhone || '' }}</strong></div>
          <div><span>所在地区</span><strong>{{ detail.provinceName }} · {{ detail.cityName }}</strong></div>
          <div><span>注册地址</span><strong>{{ detail.address || '-' }}</strong></div>
          <div><span>注册时间</span><strong>{{ detail.registerTime || '-' }}</strong></div>
          <div><span>最近登录</span><strong>{{ detail.lastLoginTime || '暂无登录记录' }}</strong></div>
        </div>
        <template v-if="detailLicenseFields.length">
          <el-divider content-position="left">资质证件</el-divider>
          <div class="detail-list">
            <div v-for="field in detailLicenseFields" :key="String(field.key)">
              <span>{{ field.label }}</span>
              <strong>{{ detail[field.key as keyof EnterpriseDetail] || '-' }}</strong>
            </div>
          </div>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.form-note {
  margin: -4px 0 14px;
  color: var(--ink-500);
  font-size: 12px;
  line-height: 1.7;
}

.form-hint {
  display: block;
  margin-top: 4px;
  color: var(--ink-500);
  font-size: 11px;
  line-height: 1.6;
}
</style>
