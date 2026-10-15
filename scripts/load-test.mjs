#!/usr/bin/env node
/**
 * 轻量压测脚本（Node 18+，无需额外依赖）。
 *
 * 为什么需要：项目 README 里"高并发设计"是重点（限流、热点写聚合、两级缓存、CAS 流转），
 * 但设计只有配上实测数字才有说服力；这一脚本用最少的依赖给出 QPS 与 P50/P95/P99。
 *
 * 用法：
 *   node scripts/load-test.mjs                                  # 默认压溯源查询
 *   node scripts/load-test.mjs --path /api/common/regions/provinces --concurrency 50 --duration 15
 *   node scripts/load-test.mjs --base http://localhost:8080
 *
 * 说明：
 *   - 只读接口，默认压最容易被刷的消费者溯源查询（12.1）；
 *   - 服务端默认对 /api/public/** 限流（200 QPS/实例，见 fsts.ratelimit.public-qps），
 *     超过阈值返回 429 属于预期行为，脚本会把 429 单独统计而不是算作失败。
 */

const args = parseArgs(process.argv.slice(2))
const base = args.base ?? 'http://localhost:8080'
const path = args.path ?? '/api/public/trace/FSTS-20260915-SH-0001'
const duration = Number(args.duration ?? 10)
const concurrency = Number(args.concurrency ?? 20)
const timeoutMs = Number(args.timeout ?? 5000)
const url = `${base.replace(/\/+$/, '')}${path}`

const latencies = []
const statuses = new Map()
let inflight = 0
const deadline = Date.now() + duration * 1000

console.log(`目标: ${url}`)
console.log(`并发: ${concurrency}  时长: ${duration}s  超时: ${timeoutMs}ms`)

// 预热：避免把连接建立、JIT、缓存冷启动算进正式统计
for (let i = 0; i < Math.min(concurrency, 10); i += 1) {
  try {
    await fetch(url, { signal: AbortSignal.timeout(timeoutMs) })
  } catch {
    // 预热失败不影响正式统计，正式阶段会记录到错误分布里
  }
}

async function worker() {
  while (Date.now() < deadline) {
    inflight += 1
    const started = performance.now()
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(timeoutMs) })
      await response.text()
      pushStatus(String(response.status))
    } catch (error) {
      pushStatus(error.name === 'TimeoutError' ? 'timeout' : 'network-error')
    } finally {
      latencies.push(performance.now() - started)
      inflight -= 1
    }
  }
}

function pushStatus(key) {
  statuses.set(key, (statuses.get(key) ?? 0) + 1)
}

const startedAt = Date.now()
await Promise.all(Array.from({ length: concurrency }, () => worker()))
const elapsed = (Date.now() - startedAt) / 1000

const total = latencies.length
const sorted = [...latencies].sort((a, b) => a - b)
const pick = (percent) => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * percent))] ?? 0
const qps = total / elapsed

console.log('')
console.log('=== 压测结果 ===')
console.log(`总请求: ${total}    实际耗时: ${elapsed.toFixed(2)}s    QPS: ${qps.toFixed(1)}`)
console.log(`延迟(ms): avg ${average(sorted).toFixed(1)}  P50 ${pick(0.5).toFixed(1)}  P95 ${pick(0.95).toFixed(1)}  P99 ${pick(0.99).toFixed(1)}  max ${(sorted[sorted.length - 1] ?? 0).toFixed(1)}`)
console.log('状态分布:')
for (const [key, count] of [...statuses.entries()].sort((a, b) => b[1] - a[1])) {
  console.log(`  ${key.padEnd(14)} ${count} (${((count / total) * 100).toFixed(1)}%)`)
}

const limited = statuses.get('429') ?? 0
if (limited > 0) {
  console.log('')
  console.log(`注意：有 ${limited} 次被限流（429），说明请求已超过 fsts.ratelimit.public-qps 配置；`)
  console.log('      若要测服务端吞吐上限，请临时调高该配置，或把并发降到阈值以下。')
}

function average(list) {
  return list.length ? list.reduce((sum, value) => sum + value, 0) / list.length : 0
}

function parseArgs(argv) {
  const result = {}
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i]
    if (!token.startsWith('--')) continue
    const key = token.slice(2)
    const next = argv[i + 1]
    if (next && !next.startsWith('--')) {
      result[key] = next
      i += 1
    } else {
      result[key] = true
    }
  }
  return result
}
