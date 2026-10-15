import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type LoginPayload } from '@/api/auth'
import { TOKEN_KEY } from '@/lib/http'
import type { AdminInfo, AdminLogin, EnterpriseInfo, EnterpriseLogin, UserRole } from '@/types/domain'

const SESSION_KEY = 'fsts_session'

type Session = Partial<AdminLogin & EnterpriseLogin> & { role: UserRole; token: string }

export const useAuthStore = defineStore('auth', () => {
  const persisted = localStorage.getItem(SESSION_KEY)
  const initial = persisted ? (JSON.parse(persisted) as Session) : null
  const role = ref<UserRole | null>(initial?.role ?? null)
  const session = ref<Session | null>(initial)
  const profile = ref<AdminInfo | EnterpriseInfo | null>(null)

  const isAuthenticated = computed(() => Boolean(session.value?.token && role.value))
  const displayName = computed(() => session.value?.adminName || session.value?.enterpriseName || session.value?.loginName || '访客')
  /** 当前登录企业的环节类型（1 捕捞与养殖 / 2 冷冻加工 / 3 批发 / 4 零售），管理员为 null */
  const enterpriseType = computed(() => (role.value === 'enterprise' ? session.value?.enterpriseType ?? null : null))

  function setSession(nextRole: UserRole, data: Partial<AdminLogin & EnterpriseLogin> & { token: string }) {
    const next = { ...data, role: nextRole } as Session
    role.value = nextRole
    session.value = next
    localStorage.setItem(TOKEN_KEY, next.token)
    localStorage.setItem(SESSION_KEY, JSON.stringify(next))
  }

  function clearSession() {
    role.value = null
    session.value = null
    profile.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(SESSION_KEY)
  }

  async function login(nextRole: UserRole, payload: LoginPayload) {
    const data = nextRole === 'admin' ? await authApi.adminLogin(payload) : await authApi.enterpriseLogin(payload)
    setSession(nextRole, data)
    return data
  }

  async function loadProfile() {
    if (!role.value) return null
    profile.value = role.value === 'admin' ? await authApi.adminInfo() : await authApi.enterpriseProfile()
    return profile.value
  }

  async function logout() {
    try {
      if (role.value === 'admin') await authApi.adminLogout()
      if (role.value === 'enterprise') await authApi.enterpriseLogout()
    } finally {
      clearSession()
    }
  }

  /**
   * 令牌失效（401）统一处理：清空会话并回到对应端的登录页。
   * 之前只清空会话不跳转，用户会停在受保护页面上看到空白/报错，
   * 与接口文档 3.6"401 → 清空本地 Token，跳转登录页"的约定不一致。
   */
  function handleUnauthorized() {
    const wasAdmin = role.value === 'admin'
    const hadSession = Boolean(role.value)
    clearSession()
    if (typeof window === 'undefined' || !hadSession) {
      return
    }
    const target = wasAdmin ? '/admin/login' : '/enterprise/login'
    if (window.location.pathname !== target) {
      window.location.replace(`${target}?expired=1`)
    }
  }

  window.addEventListener('fsts:unauthorized', handleUnauthorized)

  return { role, session, profile, isAuthenticated, displayName, enterpriseType, setSession, clearSession, login, loadProfile, logout }
})
