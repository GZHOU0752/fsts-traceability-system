import { describe, expect, it } from 'vitest'
import { parseCsv, toCsv } from './csv'

describe('csv helpers', () => {
  it('解析 BOM、CRLF 与空行', () => {
    const rows = parseCsv('\uFEFF批号,品种\r\nYY001,带鱼\r\n\r\n')
    expect(rows).toEqual([
      ['批号', '品种'],
      ['YY001', '带鱼'],
    ])
  })

  it('支持引号包裹的逗号、换行与转义引号', () => {
    const rows = parseCsv('a,b\n"含,逗号","含""引号""的字"\n"跨\n行",x')
    expect(rows[1]).toEqual(['含,逗号', '含"引号"的字'])
    expect(rows[2]).toEqual(['跨\n行', 'x'])
  })

  it('导出的 CSV 带 BOM 并对特殊字符加引号', () => {
    const text = toCsv([['产品批号', '备注'], ['YY001', '含,逗号']])
    expect(text.startsWith('\uFEFF')).toBe(true)
    expect(text).toContain('"含,逗号"')
    expect(text.endsWith('\r\n')).toBe(true)
  })

  it('空单元格保持为空字符串而不是 undefined', () => {
    const rows = parseCsv('a,b,c\n1,,3')
    expect(rows[1]).toEqual(['1', '', '3'])
  })
})
