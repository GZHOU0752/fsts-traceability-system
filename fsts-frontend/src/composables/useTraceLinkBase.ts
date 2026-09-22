import { ref } from 'vue'
import { publicApi } from '@/api/public'
import { buildTraceLink, defaultTraceBase, isLoopbackHost, lanTraceBase } from '@/lib/traceCode'

/**
 * 二维码链接前缀。
 *
 * 手机扫码后是手机浏览器打开追溯页，所以二维码里不能是 localhost。
 * 页面跑在本机回环地址时，向后端询问本机内网 IP，把前缀换成
 * http://<内网IP>:<前端端口>/trace，手机与电脑同一 Wi-Fi 即可扫码打开。
 *
 * 部署到正式域名、或前端与后端分离部署时，用 VITE_TRACE_BASE_URL 直接指定，
 * 例如 https://trace.example.com/trace。
 */
const configuredBase = String(import.meta.env.VITE_TRACE_BASE_URL ?? '').trim()
const base = ref(configuredBase || defaultTraceBase())
let detected = Boolean(configuredBase)
let pending: Promise<void> | null = null

export function useTraceLinkBase() {
  /** 探测一次即可，失败不影响页面其他功能 */
  async function detect() {
    if (detected || typeof window === 'undefined') {
      return
    }
    if (!isLoopbackHost(window.location.hostname)) {
      detected = true
      return
    }
    pending ??= (async () => {
      try {
        const hosts = await publicApi.accessHosts()
        const lan = hosts.find((item) => item && !isLoopbackHost(item))
        if (lan) {
          base.value = lanTraceBase(lan, window.location.port)
        }
      } catch {
        // 探测失败就退回本机地址：桌面端预览二维码仍然正常，只是手机扫不开
      } finally {
        detected = true
        pending = null
      }
    })()
    await pending
  }

  function link(traceCode: string, qrContent?: string) {
    return buildTraceLink(traceCode, base.value, qrContent)
  }

  return { base, detect, link }
}
