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

  window.addEventListener('fsts:unauthorized', clearSession)

  return { role, session, profile, isAuthenticated, displayName, setSession, clearSession, login, loadProfile, logout }
})
