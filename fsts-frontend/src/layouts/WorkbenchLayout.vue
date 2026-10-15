<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { enterpriseApi } from '@/api/enterprise'
import AppLogo from '@/components/AppLogo.vue'
import {
  ArrowLeft,
  Box,
  CircleCheck,
  Connection,
  DataAnalysis,
  Expand,
  Fold,
  Lock,
  Monitor,
  OfficeBuilding,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'

interface NavItem {
  label: string
  path: string
  icon: Component
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const mobileOpen = ref(false)
const isAdmin = computed(() => auth.role === 'admin')

/**
 * 菜单按"角色 + 企业环节类型"生成。
 *
 * <p>链条源头（类型 1）没有上游企业，菜单里不出现"上游数据"；
 * 零售商（类型 4）处于链条末端，不参与下游进场确认，菜单里不出现"进场确认"。
 * 之前四个菜单对所有类型都一样，点进去只会收到 400/403 报错。
 */
const nav = computed<NavItem[]>(() => {
  if (isAdmin.value) {
    return [
      { label: '运营总览', path: '/admin/dashboard', icon: DataAnalysis },
      { label: '节点企业', path: '/admin/enterprises', icon: OfficeBuilding },
      { label: '账号安全', path: '/admin/profile', icon: Lock },
    ]
  }

  const type = auth.enterpriseType
  const items: NavItem[] = [{ label: '产品批号', path: '/enterprise/batches', icon: Box }]
  if (type !== 4) {
    items.push({ label: '进场确认', path: '/enterprise/confirm-requests', icon: CircleCheck })
  }
  if (type !== 1) {
    items.push({ label: '上游数据', path: '/enterprise/upstream', icon: Connection })
  }
  items.push({ label: '企业资料', path: '/enterprise/profile', icon: User })
  return items
})

const title = computed(() => String(route.meta.title || '工作台'))

/**
 * 待处理进场确认角标。
 *
 * <p>上游企业只有在进入"进场确认"页后才知道有活要干，容易漏处理；
 * 这里在菜单上直接显示待确认数量，并在路由切换与每分钟各刷新一次。
 * 零售商（类型 4）处于链条末端，不参与下游确认，直接跳过请求。
 */
const pendingConfirm = ref(0)
let badgeTimer: ReturnType<typeof setInterval> | undefined

async function loadPendingBadge() {
  if (isAdmin.value || auth.enterpriseType === 4) {
    pendingConfirm.value = 0
    return
  }
  try {
    const result = await enterpriseApi.confirmRequests({ current: 1, size: 1, requestStatus: 1 })
    pendingConfirm.value = result.total
  } catch {
    pendingConfirm.value = 0
  }
}

onMounted(() => {
  void loadPendingBadge()
  badgeTimer = setInterval(() => void loadPendingBadge(), 60_000)
})

watch(() => route.fullPath, () => void loadPendingBadge())

onBeforeUnmount(() => {
  if (badgeTimer) clearInterval(badgeTimer)
})

async function logout() {
  const target = isAdmin.value ? '/admin/login' : '/enterprise/login'
  await auth.logout()
  router.replace(target)
}

function goAccount() {
  router.push(isAdmin.value ? '/admin/profile' : '/enterprise/profile')
}
</script>

<template>
  <div class="workbench" :class="{ 'is-collapsed': collapsed }">
    <aside class="sidebar" :class="{ 'is-open': mobileOpen }">
      <div class="sidebar__top">
        <AppLogo />
        <el-button text class="mobile-close" @click="mobileOpen = false"><ArrowLeft /></el-button>
      </div>
      <div class="workspace-chip">
        <span class="workspace-chip__dot"></span>
        <span>{{ isAdmin ? '系统管理端' : '流通节点端' }}</span>
      </div>
      <nav class="side-nav">
        <router-link
          v-for="item in nav"
          :key="item.path"
          :to="item.path"
          class="side-nav__item"
          active-class="is-active"
          @click="mobileOpen = false"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <el-badge
            v-if="item.path === '/enterprise/confirm-requests' && pendingConfirm > 0"
            :value="pendingConfirm"
            class="side-nav__badge"
          />
        </router-link>
      </nav>
      <div class="sidebar__foot">
        <router-link to="/trace" class="side-nav__item side-nav__item--muted">
          <el-icon><Monitor /></el-icon>
          <span>消费者查询</span>
        </router-link>
      </div>
    </aside>

    <div v-if="mobileOpen" class="sidebar-scrim" @click="mobileOpen = false"></div>

    <section class="main-shell">
      <header class="topbar">
        <div class="topbar__left">
          <el-button text class="collapse-btn" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-button>
          <el-button text class="mobile-menu" @click="mobileOpen = true"><Fold /></el-button>
          <div class="breadcrumbs"><span>FSTS</span><b>/</b><strong>{{ title }}</strong></div>
        </div>
        <div class="topbar__right">
          <div class="topbar__signal"><span></span>服务正常</div>
          <el-dropdown>
            <button class="identity">
              <span class="identity__avatar">{{ auth.displayName.slice(0, 1) }}</span>
              <span class="identity__name">{{ auth.displayName }}</span>
              <el-icon><Expand /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goAccount">账户设置</el-dropdown-item>
                <el-dropdown-item divided @click="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="page-content"><router-view /></main>
      <footer class="app-footer">FSTS 冷链溯源系统</footer>
    </section>
  </div>
</template>

<style scoped>
/* 角标推到行尾，与菜单文字对齐；侧边栏折叠时（只剩图标）自动隐藏 */
.side-nav__badge {
  margin-left: auto;
}

.is-collapsed .side-nav__badge {
  display: none;
}
</style>
