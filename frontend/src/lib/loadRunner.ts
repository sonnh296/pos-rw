export type LoadOptions = {
  totalRequests: number
  concurrency: number
  timeoutMs?: number
}

export type LoadResultItem = {
  ok: boolean
  elapsedMs: number
  status?: number
  error?: string
}

export async function runLoad(
  task: (
    timeoutMs: number | undefined,
    requestIndex: number
  ) => Promise<{ ok: true; status?: number } | { ok: false; status?: number; error: string }>,
  opts: LoadOptions,
  onProgress?: (done: number, ok: number, fail: number) => void
): Promise<LoadResultItem[]> {
  const total = Math.max(0, opts.totalRequests | 0)
  const conc = Math.max(1, Math.min(total || 1, opts.concurrency | 0 || 1))

  let cursor = 0
  let done = 0
  let ok = 0
  let fail = 0
  const results: LoadResultItem[] = []

  async function worker() {
    while (true) {
      const i = cursor++
      if (i >= total) return
      const start = performance.now()
      const r = await task(opts.timeoutMs, i)
      const elapsedMs = Math.round(performance.now() - start)
      if (r.ok) ok++
      else fail++
      done++
      results.push({
        ok: r.ok,
        elapsedMs,
        status: r.status,
        error: r.ok ? undefined : r.error,
      })
      onProgress?.(done, ok, fail)
    }
  }

  await Promise.all(Array.from({ length: conc }, () => worker()))
  return results
}

