import { describe, expect, it } from 'vitest'
import { unwrapResponse } from './http'

describe('unwrapResponse', () => {
  it('returns data for a successful backend envelope', () => {
    expect(unwrapResponse({ code: 200, message: 'ok', data: { id: 7 } })).toEqual({ id: 7 })
  })

  it('throws the backend message for a failed envelope', () => {
    expect(() => unwrapResponse({ code: 4001, message: '登录已失效', data: null })).toThrow('登录已失效')
  })
})
