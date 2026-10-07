/**
 * 剪贴板写入工具。
 *
 * <p>为什么不直接用 navigator.clipboard：该 API 只在"安全上下文"
 * （https 或 localhost）下可用。本项目为了支持手机扫码，前端经常以
 * http://<内网IP>:5173 的形式在局域网里访问，此时 navigator.clipboard
 * 为 undefined —— 直接 `await navigator.clipboard?.writeText(...)` 会什么都不做，
 * 代码却继续弹"已复制"，用户拿到的是空剪贴板。
 *
 * <p>这里统一收口：优先用标准 API，不可用或被拒绝时回退到
 * textarea + execCommand，并如实返回是否复制成功，由调用方决定提示文案。
 */
export async function writeClipboard(text: string): Promise<boolean> {
  const value = String(text ?? '')
  if (!value) {
    return false
  }
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value)
      return true
    } catch {
      // 权限被拒绝时继续走回退方案，而不是直接失败
    }
  }
  return legacyCopy(value)
}

/**
 * 非安全上下文下的兜底实现。
 *
 * textarea 必须是"可见于文档流但被移出视口"的状态，否则在 iOS Safari 上
 * select() 选区为空；同时用 fixed 定位避免页面滚动跳动。
 */
function legacyCopy(value: string): boolean {
  if (typeof document === 'undefined' || !document.body) {
    return false
  }
  const area = document.createElement('textarea')
  area.value = value
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.top = '-1000px'
  area.style.opacity = '0'
  document.body.appendChild(area)
  try {
    area.select()
    area.setSelectionRange(0, value.length)
    return document.execCommand?.('copy') === true
  } catch {
    return false
  } finally {
    document.body.removeChild(area)
  }
}
