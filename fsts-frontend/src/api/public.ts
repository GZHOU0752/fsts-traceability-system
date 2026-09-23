import { request } from '@/lib/http'
import type { PublicProduct, PublicTrace } from '@/types/domain'

export const publicApi = {
  trace: (traceCode: string) => request<PublicTrace>({ method: 'GET', url: `/public/trace/${encodeURIComponent(traceCode)}` }),
  /** 消费者端产品搜索：按产品名称检索可溯源产品 */
  searchProducts: (keyword: string) => request<PublicProduct[]>({ method: 'GET', url: '/public/products', params: { keyword } }),
  /** 手机扫码配套：探测服务端所在机器的内网 IP，用于生成手机可打开的二维码链接 */
  accessHosts: () => request<string[]>({ method: 'GET', url: '/public/access-hosts' }),
}
