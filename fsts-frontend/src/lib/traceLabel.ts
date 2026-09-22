/**
 * 溯源标签打印。
 *
 * 打印逻辑放在独立窗口里完成：既不受工作台布局与弹窗层级的干扰，
 * 又能让企业按需选择打印机、份数和纸张，适合贴在包装箱上。
 */
export interface TraceLabelPayload {
  /** 二维码 PNG（data URL），由 TraceQrCode 组件提供 */
  qrDataUrl: string
  traceCode: string
  batchNo?: string
  productVariety?: string
  saleStore?: string
  retailerName?: string
  traceUrl?: string
}

export function escapeHtml(value: unknown): string {
  const map: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
  return String(value ?? '').replace(/[&<>"']/g, (char) => map[char])
}

export function buildTraceLabelHtml(payload: TraceLabelPayload): string {
  const rows: Array<[string, string | undefined]> = [
    ['产品批号', payload.batchNo],
    ['产品品种', payload.productVariety],
    ['销售门店', payload.saleStore],
    ['零售企业', payload.retailerName],
  ]
  const facts = rows
    .filter((row): row is [string, string] => Boolean(row[1]))
    .map(([label, value]) => `<div class="fact"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`)
    .join('')

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>${escapeHtml(payload.traceCode)} 溯源标签</title>
<style>
  @page { margin: 12mm; }
  body { margin: 0; font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif; color: #102b3f; }
  .label { width: 92mm; padding: 6mm; border: 1px dashed #b9c9ce; border-radius: 4mm; }
  .head { display: flex; align-items: center; justify-content: space-between; gap: 4mm; }
  .head h1 { margin: 0; font-size: 15px; letter-spacing: .5px; }
  .head p { margin: 2px 0 0; color: #708895; font-size: 10px; }
  .body { display: flex; gap: 4mm; margin-top: 4mm; }
  .facts { flex: 1; display: grid; gap: 3mm; align-content: start; }
  .fact { display: grid; gap: 1mm; }
  .fact span { color: #708895; font-size: 9px; letter-spacing: .5px; }
  .fact strong { font-size: 11px; word-break: break-all; }
  img { width: 30mm; height: 30mm; }
  .code { margin-top: 4mm; padding-top: 3mm; border-top: 1px solid #dce9e5; }
  .code strong { display: block; font-family: ui-monospace, monospace; font-size: 12px; letter-spacing: 1px; }
  .code small { display: block; margin-top: 1.5mm; color: #708895; font-size: 9px; word-break: break-all; }
</style>
</head>
<body>
<div class="label">
  <div class="head">
    <div>
      <h1>冷冻海产品溯源标签</h1>
      <p>手机扫码查看完整冷链记录</p>
    </div>
  </div>
  <div class="body">
    <div class="facts">${facts || '<div class="fact"><strong>暂无批次信息</strong></div>'}</div>
    <img src="${escapeHtml(payload.qrDataUrl)}" alt="溯源码二维码">
  </div>
  <div class="code">
    <strong>${escapeHtml(payload.traceCode)}</strong>
    <small>${escapeHtml(payload.traceUrl || '')}</small>
  </div>
</div>
<script>
  window.addEventListener('load', function () {
    window.setTimeout(function () { window.focus(); window.print() }, 150)
  })
  window.addEventListener('afterprint', function () { window.close() })
</script>
</body>
</html>`
}

/** 打开打印窗口；被浏览器拦截时返回 false，由调用方提示用户。 */
export function printTraceLabel(payload: TraceLabelPayload): boolean {
  const printWindow = window.open('', '_blank', 'width=520,height=720')
  if (!printWindow) {
    return false
  }
  printWindow.document.open()
  printWindow.document.write(buildTraceLabelHtml(payload))
  printWindow.document.close()
  return true
}
