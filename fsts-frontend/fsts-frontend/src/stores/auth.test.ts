import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('persists an enterprise session and clears it on logout', () => {
    const store = useAuthStore()
    store.setSession('enterprise', { token: 'token-1', enterpriseId: '8', enterpriseName: '海港冷链' })
    expect(store.isAuthenticated).toBe(true)
    expect(store.role).toBe('enterprise')
    expect(localStorage.getItem('fsts_token')).toBe('token-1')
    store.clearSession()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem('fsts_token')).toBeNull()
  })
})
