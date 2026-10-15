<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showError } from '@/lib/feedback'
import { ElMessage } from 'element-plus'
import { Lock, Refresh, User } from '@element-plus/icons-vue'
import { adminApi } from '@/api/admin'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import type { AdminInfo } from '@/types/domain'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const auth = useAuthStore()
const info = ref<AdminInfo>()
const loading = ref(false)
const actionLoading = ref(false)
const passwordVisible = ref(false)
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function load() {
  loading.value = true
  try {
    info.value = await authApi.adminInfo()
  } catch (error) {
    showError(error instanceof Error ? error.message : '管理员信息加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 修改管理员密码（接口 4.8）。
 * 后端改密后会把当前令牌加入黑名单，因此前端必须清理会话并回到登录页，
 * 否则用户会继续停留在工作台，随后每个请求都返回 401。
 */
async function changePassword() {
  if (!password.oldPassword || password.newPassword.length < 6 || password.newPassword !== password.confirmPassword) {
    ElMessage.warning('请检查密码填写，长度至少 6 位且两次一致')
    return
  }
  actionLoading.value = true
  try {
    await adminApi.changePassword({ ...password })
    ElMessage.success('密码已修改，请重新登录')
    passwordVisible.value = false
    Object.assign(password, { oldPassword: '', newPassword: '', confirmPassword: '' })
    auth.clearSession()
    router.replace('/admin/login')
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
    <PageHeader title="账号安全">
      <template #default>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div v-loading="loading" class="profile-grid">
      <section class="data-panel profile-card">
        <div class="profile-card__hero">
          <span class="profile-card__avatar">{{ info?.adminName?.slice(0, 1) || '管' }}</span>
          <div>
            <h2>{{ info?.adminName || '系统管理员' }}</h2>
            <p>{{ info?.loginName || '-' }}</p>
          </div>
        </div>
        <div class="detail-list detail-list--grid">
          <div><span>登录账号</span><strong>{{ info?.loginName || '-' }}</strong></div>
          <div><span>联系电话</span><strong>{{ info?.phone || '-' }}</strong></div>
          <div><span>电子邮箱</span><strong>{{ info?.email || '-' }}</strong></div>
          <div><span>最近登录</span><strong>{{ info?.lastLoginTime || '暂无记录' }}</strong></div>
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
        <div class="security-row">
          <span class="security-row__icon"><el-icon><User /></el-icon></span>
          <div>
            <strong>当前身份</strong>
            <p>{{ info?.loginName || '-' }} · 系统管理端</p>
          </div>
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
