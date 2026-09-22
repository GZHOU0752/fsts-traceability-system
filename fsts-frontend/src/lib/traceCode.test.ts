import { describe, expect, it } from 'vitest'
import { buildTraceLink, extractTraceCode, isLoopbackHost, isTraceCode, lanTraceBase, normalizeTraceCode } from './traceCode'

describe('trace code helpers', () => {
  it('normalizes case and surrounding spaces', () => {
    expect(normalizeTraceCode('  fsts-20260915-sh-0001 ')).toBe('FSTS-20260915-SH-0001')
    expect(isTraceCode('fsts-20260915-sh-0001')).toBe(true)
    expect(isTraceCode('FSTS-2026-SH-1')).toBe(false)
  })

  it('extracts the code from a scanned trace link', () => {
    expect(extractTraceCode('https://fsts.example.com/trace/FSTS-20260915-SH-0001')).toBe('FSTS-20260915-SH-0001')
  })

  it('extracts the code from a link with query params produced by a third-party printer', () => {
    expect(extractTraceCode('http://192.168.1.9:5173/trace/fsts-20260915-sh-0001?from=label')).toBe('FSTS-20260915-SH-0001')
  })

  it('returns null for unrelated content', () => {
    expect(extractTraceCode('https://example.com/hello')).toBeNull()
    expect(extractTraceCode('   ')).toBeNull()
  })

  it('detects the loopback hosts that a phone can never open', () => {
    expect(isLoopbackHost('localhost')).toBe(true)
    expect(isLoopbackHost('127.0.0.1')).toBe(true)
    expect(isLoopbackHost('192.168.1.5')).toBe(false)
  })

  it('builds a lan link so the phone opens the same trace page', () => {
    expect(lanTraceBase('192.168.1.5', '5173')).toBe(`${window.location.protocol}//192.168.1.5:5173/trace`)
    expect(buildTraceLink('fsts-20260915-sh-0001', 'http://192.168.1.5:5173/trace'))
      .toBe('http://192.168.1.5:5173/trace/FSTS-20260915-SH-0001')
  })

  it('rebuilds a reachable link when the backend still points at the demo domain', () => {
    expect(buildTraceLink('FSTS-20260915-SH-0001', 'http://192.168.1.5:5173/trace', 'https://fsts.example.com/trace/FSTS-20260915-SH-0001'))
      .toBe('http://192.168.1.5:5173/trace/FSTS-20260915-SH-0001')
  })

  it('keeps a deployed qr content untouched', () => {
    expect(buildTraceLink('FSTS-20260915-SH-0001', 'http://192.168.1.5:5173/trace', 'https://trace.seafood.cn/FSTS-20260915-SH-0001'))
      .toBe('https://trace.seafood.cn/FSTS-20260915-SH-0001')
  })
})
