/**
 * 极简 CSV 读写。
 *
 * <p>为什么不用第三方库：导入模板的形态完全由我们自己定义（固定列、逗号分隔），
 * 需要的只是"引号包裹、逗号分隔、换行与 BOM 处理"这几条规则，
 * 为此引入一个依赖并不划算。实现按 RFC 4180 的常见子集来写。
 */

/** 解析 CSV 文本为二维数组；兼容 BOM、CRLF/LF、双引号转义（""）与字段内换行 */
export function parseCsv(text: string): string[][] {
  const content = String(text ?? '').replace(/^\uFEFF/, '')
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let inQuotes = false

  for (let i = 0; i < content.length; i += 1) {
    const char = content[i]
    if (inQuotes) {
      if (char === '"') {
        if (content[i + 1] === '"') {
          field += '"'
          i += 1
        } else {
          inQuotes = false
        }
      } else {
        field += char
      }
      continue
    }
    if (char === '"') {
      inQuotes = true
    } else if (char === ',') {
      row.push(field)
      field = ''
    } else if (char === '\r') {
      // 交给 \n 处理，单独出现 \r 时也按换行处理
      if (content[i + 1] !== '\n') {
        row.push(field)
        rows.push(row)
        row = []
        field = ''
      }
    } else if (char === '\n') {
      row.push(field)
      rows.push(row)
      row = []
      field = ''
    } else {
      field += char
    }
  }
  if (field !== '' || row.length > 0) {
    row.push(field)
    rows.push(row)
  }
  // 丢弃完全空白的行，避免用户在 Excel 里多敲几个回车就报"第 N 行数据为空"
  return rows.filter((item) => item.some((cell) => String(cell).trim() !== ''))
}

/**
 * 生成 CSV 文本。
 *
 * <p>开头写入 UTF-8 BOM：Excel（尤其是中文 Windows 版）在没有 BOM 时会按 GBK 解码，
 * 中文列名会变成乱码，用户看到的就是"模板打不开"。
 */
export function toCsv(rows: Array<Array<string | number | undefined | null>>): string {
  const body = rows
    .map((row) => row.map((cell) => escapeCell(cell)).join(','))
    .join('\r\n')
  return `\uFEFF${body}\r\n`
}

function escapeCell(value: string | number | undefined | null): string {
  const text = value === undefined || value === null ? '' : String(value)
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`
  }
  return text
}

/** 触发浏览器下载（导入模板复用同一套逻辑） */
export function downloadTextFile(filename: string, content: string, mime = 'text/csv;charset=utf-8'): void {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
