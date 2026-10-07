import { afterEach, describe, expect, it, vi } from 'vitest'
import { writeClipboard } from './clipboard'

const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard')
const originalExecCommand = Object.getOwnPropertyDescriptor(document, 'execCommand')

function setClipboard(value: unknown) {
  Object.defineProperty(navigator, 'clipboard', { value, configurable: true })
}

function setExecCommand(value: unknown) {
  Object.defineProperty(document, 'execCommand', { value, configurable: true })
}

afterEach(() => {
  if (originalClipboard) {
    Object.defineProperty(navigator, 'clipboard', originalClipboard)
  } else {
    Reflect.deleteProperty(navigator, 'clipboard')
  }
  if (originalExecCommand) {
    Object.defineProperty(document, 'execCommand', originalExecCommand)
  } else {
    Reflect.deleteProperty(document, 'execCommand')
  }
})

describe('writeClipboard', () => {
  it('空内容不写入剪贴板', async () => {
    const writeText = vi.fn()
    setClipboard({ writeText })
    expect(await writeClipboard('')).toBe(false)
    expect(writeText).not.toHaveBeenCalled()
  })

  it('安全上下文下使用标准 Clipboard API', async () => {
    const writeText = vi.fn(async () => undefined)
    setClipboard({ writeText })
    expect(await writeClipboard('FSTS-20260915-SH-0001')).toBe(true)
    expect(writeText).toHaveBeenCalledWith('FSTS-20260915-SH-0001')
  })

  it('局域网 http 下 Clipboard API 不存在时回退到 execCommand，并如实返回结果', async () => {
    setClipboard(undefined)
    setExecCommand(() => true)
    expect(await writeClipboard('FSTS-20260915-SH-0001')).toBe(true)
    // 复制结束后不能把临时 textarea 留在页面上
    expect(document.querySelectorAll('textarea')).toHaveLength(0)
  })

  it('回退方案也失败时返回 false，避免前端误报"已复制"', async () => {
    setClipboard(undefined)
    setExecCommand(() => false)
    expect(await writeClipboard('FSTS-20260915-SH-0001')).toBe(false)
  })

  it('标准 API 抛错（权限被拒）时仍尝试回退', async () => {
    setClipboard({ writeText: vi.fn(async () => { throw new Error('denied') }) })
    setExecCommand(() => true)
    expect(await writeClipboard('FSTS-20260915-SH-0001')).toBe(true)
  })
})
