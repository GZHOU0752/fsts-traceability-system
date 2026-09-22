import { describe, expect, it } from 'vitest'
import { buildTraceLabelHtml, escapeHtml } from './traceLabel'

describe('trace label printing', () => {
  it('escapes user supplied text', () => {
    expect(escapeHtml('<script>"x"</script>')).toBe('&lt;script&gt;&quot;x&quot;&lt;/script&gt;')
  })

  it('renders the qr image, trace code and batch facts', () => {
    const html = buildTraceLabelHtml({
      qrDataUrl: 'data:image/png;base64,AAA',
      traceCode: 'FSTS-20260915-SH-0001',
      batchNo: 'LS20260915001',
      productVariety: '阿根廷红虾',
      saleStore: '佳鲜生鲜超市 陆家嘴店',
      traceUrl: 'http://localhost:5173/trace/FSTS-20260915-SH-0001',
    })
    expect(html).toContain('data:image/png;base64,AAA')
    expect(html).toContain('FSTS-20260915-SH-0001')
    expect(html).toContain('阿根廷红虾')
    expect(html).toContain('http://localhost:5173/trace/FSTS-20260915-SH-0001')
  })

  it('omits empty facts instead of printing blank rows', () => {
    const html = buildTraceLabelHtml({ qrDataUrl: 'data:image/png;base64,AAA', traceCode: 'FSTS-20260915-SH-0001' })
    expect(html).not.toContain('销售门店')
  })
})
