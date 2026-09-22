import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.isAuthenticated) return auth.role === 'admin' ? '/admin/login' : auth.role === 'enterprise' ? '/enterprise/login' : '/trace'
  if (to.meta.role && to.meta.role !== auth.role) return auth.role === 'admin' ? '/admin/dashboard' : '/enterprise/batches'
  return true
})

export default router
