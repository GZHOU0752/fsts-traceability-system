import { request } from '@/lib/http'
import type { AdminInfo, AdminLogin, EnterpriseInfo, EnterpriseLogin } from '@/types/domain'

export interface LoginPayload { loginName: string; password: string }
export interface PasswordPayload { oldPassword: string; newPassword: string; confirmPassword: string }

export const authApi = {
  adminLogin: (payload: LoginPayload) => request<AdminLogin>({ method: 'POST', url: '/admin/auth/login', data: payload }),
  adminInfo: () => request<AdminInfo>({ method: 'GET', url: '/admin/auth/info' }),
  adminLogout: () => request<void>({ method: 'POST', url: '/admin/auth/logout' }),
  enterpriseLogin: (payload: LoginPayload) => request<EnterpriseLogin>({ method: 'POST', url: '/enterprise/auth/login', data: payload }),
  enterpriseInfo: () => request<EnterpriseInfo>({ method: 'GET', url: '/enterprise/auth/info' }),
  enterpriseProfile: () => request<EnterpriseInfo>({ method: 'GET', url: '/enterprise/profile' }),
  enterprisePassword: (payload: PasswordPayload) => request<void>({ method: 'PUT', url: '/enterprise/auth/password', data: payload }),
  enterpriseLogout: () => request<void>({ method: 'POST', url: '/enterprise/auth/logout' }),
}
