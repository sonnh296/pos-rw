export type SummaryStats = {
  total: number
  ok: number
  fail: number
  minMs: number
  p50Ms: number
  p95Ms: number
  p99Ms: number
  maxMs: number
  avgMs: number
}

export function summarize(ms: number[], okCount: number, failCount: number): SummaryStats {
  const total = okCount + failCount
  const sorted = [...ms].sort((a, b) => a - b)
  const pick = (p: number) => {
    if (sorted.length === 0) return 0
    const idx = Math.min(sorted.length - 1, Math.max(0, Math.floor(p * (sorted.length - 1))))
    return sorted[idx] ?? 0
  }
  const sum = sorted.reduce((a, b) => a + b, 0)
  const avg = sorted.length ? sum / sorted.length : 0

  return {
    total,
    ok: okCount,
    fail: failCount,
    minMs: sorted[0] ?? 0,
    p50Ms: pick(0.5),
    p95Ms: pick(0.95),
    p99Ms: pick(0.99),
    maxMs: sorted[sorted.length - 1] ?? 0,
    avgMs: avg,
  }
}

