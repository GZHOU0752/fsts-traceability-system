import { describe, expect, it, vi } from 'vitest'

vi.mock('@/api/public', () => ({
  publicApi: { accessHosts: vi.fn(async () => ['192.168.127.162']) },
}))

import { useTraceLinkBase } from './useTraceLinkBase'

describe('useTraceLinkBase', () => {
  it('页面跑在 localhost 时把二维码链接换成局域网地址，手机才扫得开', async () => {
    const { detect, link } = useTraceLinkBase()
    await detect()
    expect(link('fsts-20260915-sh-0001'))
      .toBe(`${window.location.protocol}//192.168.127.162:${window.location.port}/trace/FSTS-20260915-SH-0001`)
  })

  it('后端返回的正式地址优先，前端不再改写', async () => {
    const { link } = useTraceLinkBase()
    expect(link('FSTS-20260915-SH-0001', 'https://trace.seafood.cn/trace/FSTS-20260915-SH-0001'))
      .toBe('https://trace.seafood.cn/trace/FSTS-20260915-SH-0001')
  })
})
