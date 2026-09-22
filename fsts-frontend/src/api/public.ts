import { request } from '@/lib/http'
import type { PublicTrace } from '@/types/domain'

export const publicApi = { trace: (traceCode: string) => request<PublicTrace>({ method: 'GET', url: `/public/trace/${encodeURIComponent(traceCode)}` }) }
