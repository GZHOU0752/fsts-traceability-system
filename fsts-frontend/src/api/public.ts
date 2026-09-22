import { request } from '@/lib/http'
import type { PublicTrace } from '@/types/domain'

export const publicApi = {
  trace: (traceCode: string) => request<PublicTrace>({ method: 'GET', url: `/public/trace/${encodeURIComponent(traceCode)}` }),
  /** 手机扫码配套：探测服务端所在机器的内网 IP，用于生成手机可打开的二维码链接 */
  accessHosts: () => request<string[]>({ method: 'GET', url: '/public/access-hosts' }),
}
