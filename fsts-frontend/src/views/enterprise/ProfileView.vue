<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showError } from '@/lib/feedback'
import { ElMessage } from 'element-plus'
import { Lock, Refresh } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'
import { enterpriseApi } from '@/api/enterprise'
import { useAuthStore } from '@/stores/auth'
import type { EnterpriseInfo } from '@/types/domain'
import { licenseFieldsOf } from '@/lib/enterpriseForm'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const auth = useAuthStore()
const profile = ref<EnterpriseInfo>()
const loading = ref(false)
const actionLoading = ref(false)
const passwordVisible = ref(false)
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

/** 资质字段按企业类型返回（接口 4.5 / 8.1），与后端 EnterpriseConverter 的白名单一致 */
const licenseFields = computed(() => licenseFieldsOf(profile.value?.enterpriseType))

function licenseValue(key: string): string | number {
  const source = profile.value as unknown as Record<string, string | number | undefined> | undefined
  const value = source?.[key]
  return value === undefined || value === null || value === '' ? '-' : value
}

async function load() {
  loading.value = true
  try {
    profile.value = await enterpriseApi.profile()
  } catch (error) {
    showError(error instanceof Error ? error.message : '企业资料加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 修改登录密码（接口 4.6）。
 * 后端改密成功后当前令牌立即失效，所以这里必须清空会话并跳回登录页，
 * 而不是只弹一句"请重新登录"却把用户留在工作台。
 */
async function changePassword() {
  if (!password.oldPassword || password.newPassword.length < 6 || password.newPassword !== password.confirmPassword) {
    ElMessage.warning('请检查密码填写，长度至少 6 位且两次一致')
    return
  }
  actionLoading.value = true
  try {
    await authApi.enterprisePassword({ ...password })
    ElMessage.success('密码已修改，请使用新密码重新登录')
    passwordVisible.value = false
    Object.assign(password, { oldPassword: '', newPassword: '', confirmPassword: '' })
    auth.clearSession()
    router.replace('/enterprise/login')
  } catch (error) {
    showError(error instanceof Error ? error.message : '密码修改失败')
  } finally {
    actionLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="profile-view">
    <PageHeader title="企业资料">
      <template #default>
        <el-button :icon="Refresh" @click="load">刷新资料</el-button>
      </template>
    </PageHeader>

    <div v-loading="loading" class="profile-grid">
      <section class="data-panel profile-card">
        <div class="profile-card__hero">
          <span class="profile-card__avatar">{{ profile?.enterpriseName?.slice(0, 1) || '企' }}</span>
          <div>
            <h2>{{ profile?.enterpriseName || '加载中' }}</h2>
            <p>{{ profile?.enterpriseTypeName || '-' }} · {{ profile?.enterpriseCode || '-' }}</p>
          </div>
        </div>
        <div class="detail-list detail-list--grid">
          <div><span>统一信用代码</span><strong>{{ profile?.creditCode || '-' }}</strong></div>
          <div><span>登录账号</span><strong>{{ profile?.loginName || '-' }}</strong></div>
          <div><span>法定代表人</span><strong>{{ profile?.legalPerson || '-' }}</strong></div>
          <div><span>联系人</span><strong>{{ profile?.contactPerson || '-' }} {{ profile?.contactPhone || '' }}</strong></div>
          <div><span>所在地区</span><strong>{{ profile?.provinceName }} · {{ profile?.cityName }}</strong></div>
          <div><span>注册地址</span><strong>{{ profile?.address || '-' }}</strong></div>
          <div><span>注册时间</span><strong>{{ profile?.registerTime || '-' }}</strong></div>
          <div><span>最近登录</span><strong>{{ profile?.lastLoginTime || '本次为首次登录' }}</strong></div>
        </div>
      </section>

      <section v-if="licenseFields.length" class="data-panel profile-card">
        <div class="data-panel__head">
          <div><strong>资质证件</strong><span>按企业类型登记，如需变更请联系系统管理员</span></div>
        </div>
        <div class="detail-list detail-list--grid">
          <div v-for="field in licenseFields" :key="String(field.key)">
            <span>{{ field.label }}</span>
            <strong>{{ licenseValue(String(field.key)) }}</strong>
          </div>
        </div>
      </section>

      <section class="data-panel profile-security">
        <div class="data-panel__head"><div><strong>账户安全</strong></div></div>
        <div class="security-row">
          <span class="security-row__icon"><el-icon><Lock /></el-icon></span>
          <div>
            <strong>登录密码</strong>
            <p>修改后当前登录会话立即失效，需要用新密码重新登录</p>
          </div>
          <el-button plain @click="passwordVisible = true">修改密码</el-button>
        </div>
      </section>
    </div>

    <el-dialog v-model="passwordVisible" title="修改登录密码" width="430px">
      <el-form label-position="top">
        <el-form-item label="当前密码">
          <el-input v-model="password.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="password.newPassword" type="password" show-password placeholder="长度 6~20 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="password.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="changePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>
