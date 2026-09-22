/**
 * 溯源标识码工具。
 *
 * 编码规则与后端 TraceCodeService 一致：FSTS-yyyyMMdd-{省份简称}-{4 位流水}，
 * 例如 FSTS-20260915-SH-0001。
 *
 * 二维码里承载的是追溯页链接（后端 trace_code.qr_content），但消费者扫到的内容
 * 也可能只是裸码、带查询参数的链接或第三方打印机改写过的文本，因此统一经
 * extractTraceCode 归一化后再查询，避免"明明贴着码却查不到"。
 */
export const TRACE_CODE_PATTERN = /^FSTS-\d{8}-[A-Z]{2}-\d{4}$/

/** 后端默认的演示域名，说明该码未按实际部署环境配置。 */
const PLACEHOLDER_HOST = /fsts\.example\.com/i

export function normalizeTraceCode(input: string): string {
  return String(input ?? '').trim().toUpperCase()
}

export function isTraceCode(value: string): boolean {
  return TRACE_CODE_PATTERN.test(normalizeTraceCode(value))
}

/**
 * 从扫码结果中提取溯源码，识别不了时返回 null。
 */
export function extractTraceCode(raw: string): string | null {
  const text = String(raw ?? '').trim()
  if (!text) {
    return null
  }
  if (isTraceCode(text)) {
    return normalizeTraceCode(text)
  }
  const segments = text.split(/[/?#&=+\s]+/)
  for (let index = segments.length - 1; index >= 0; index -= 1) {
    const candidate = safeDecode(segments[index]).replace(/[^A-Za-z0-9-]/g, '')
    if (isTraceCode(candidate)) {
      return normalizeTraceCode(candidate)
    }
  }
  const matched = normalizeTraceCode(text).match(/FSTS-\d{8}-[A-Z]{2}-\d{4}/)
  return matched ? matched[0] : null
}

/** 当前站点下的追溯页前缀（局域网改写失败时的兜底）。 */
export function defaultTraceBase(): string {
  return typeof window === 'undefined' ? '' : `${window.location.origin}/trace`
}

/**
 * 是否为本机回环地址。
 *
 * 只有页面跑在 localhost 时才需要把二维码改写成局域网地址：
 * 手机扫到 localhost 会指向手机自己，必然打不开。
 */
export function isLoopbackHost(hostname: string): boolean {
  const host = String(hostname ?? '').toLowerCase()
  return host === 'localhost' || host.endsWith('.localhost') || host === '127.0.0.1'
    || host === '::1' || host === '[::1]' || host === '0.0.0.0'
}

/**
 * 由内网 IP + 当前页面端口拼出追溯页前缀，例如 http://192.168.1.5:5173/trace
 */
export function lanTraceBase(hostname: string, port: string): string {
  const host = String(hostname ?? '').trim()
  const scheme = typeof window === 'undefined' ? 'http' : (window.location.protocol.replace(':', '') || 'http')
  return `${scheme}://${host}${port ? `:${port}` : ''}/trace`
}

/**
 * 计算二维码应承载的追溯页地址——手机扫的就是它。
 *
 * 优先使用后端返回的 qrContent；当它仍指向示例域名时，改用传入的 base
 * （通常是自动探测到的局域网地址），保证"手机扫码即打开本系统"。
 */
export function buildTraceLink(traceCode: string, base: string, qrContent?: string): string {
  const code = normalizeTraceCode(traceCode)
  const remote = String(qrContent ?? '').trim()
  if (remote && !PLACEHOLDER_HOST.test(remote)) {
    return remote
  }
  const prefix = (base || defaultTraceBase()).replace(/\/+$/, '')
  return `${prefix}/${code}`
}

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}
