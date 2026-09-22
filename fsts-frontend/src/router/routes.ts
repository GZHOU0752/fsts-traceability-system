import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/trace' },
  { path: '/trace', component: () => import('@/views/public/TraceView.vue'), meta: { public: true, title: '产品溯源' } },
  { path: '/trace/:traceCode', component: () => import('@/views/public/TraceView.vue'), meta: { public: true, title: '溯源详情' } },
  { path: '/admin/login', component: () => import('@/views/admin/LoginView.vue'), meta: { public: true, bare: true, title: '管理端登录' } },
  { path: '/admin', component: () => import('@/layouts/WorkbenchLayout.vue'), redirect: '/admin/dashboard', meta: { role: 'admin' }, children: [
    { path: 'dashboard', component: () => import('@/views/admin/DashboardView.vue'), meta: { title: '运营总览', icon: 'DataAnalysis' } },
    { path: 'enterprises', component: () => import('@/views/admin/EnterpriseListView.vue'), meta: { title: '节点企业', icon: 'OfficeBuilding' } },
  ] },
  { path: '/enterprise/login', component: () => import('@/views/enterprise/LoginView.vue'), meta: { public: true, bare: true, title: '企业端登录' } },
  { path: '/enterprise', component: () => import('@/layouts/WorkbenchLayout.vue'), redirect: '/enterprise/batches', meta: { role: 'enterprise' }, children: [
    { path: 'batches', component: () => import('@/views/enterprise/BatchListView.vue'), meta: { title: '产品批号', icon: 'Box' } },
    { path: 'confirm-requests', component: () => import('@/views/enterprise/ConfirmRequestView.vue'), meta: { title: '进场确认', icon: 'CircleCheck' } },
    { path: 'upstream', component: () => import('@/views/enterprise/UpstreamDataView.vue'), meta: { title: '上游数据', icon: 'Connection' } },
    { path: 'profile', component: () => import('@/views/enterprise/ProfileView.vue'), meta: { title: '企业资料', icon: 'User' } },
  ] },
  { path: '/:pathMatch(.*)*', redirect: '/trace' },
]
