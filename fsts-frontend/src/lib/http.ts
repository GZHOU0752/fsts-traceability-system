import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  readonly code: number
  readonly status?: number

  constructor(message: string, code = 500, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export function unwrapResponse<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.code !== 200) {
    throw new ApiError(envelope.message || '请求失败', envelope.code)
  }
  return envelope.data
}

const TOKEN_KEY = 'fsts_token'

export const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 12_000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiEnvelope<unknown>
    try {
      response.data = unwrapResponse(payload)
      return response
    } catch (error) {
      return Promise.reject(error)
    }
  },
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new CustomEvent('fsts:unauthorized'))
    }
    const message = error.response?.data?.message || error.message || '网络连接失败'
    return Promise.reject(new ApiError(message, error.response?.data?.code, error.response?.status))
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<T>(config)
  return response.data
}

export { TOKEN_KEY }
