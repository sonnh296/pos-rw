<script setup lang="ts">
import { computed, ref } from 'vue'
import { apiFetch, jsonBody } from '../lib/api'
import { runLoad } from '../lib/loadRunner'
import { summarize } from '../lib/stats'

type Target =
  | 'bench_platform_none'
  | 'bench_virtual_none'
  | 'bench_platform_mysql'
  | 'bench_virtual_mysql'
  | 'bench_platform_redis'
  | 'bench_virtual_redis'
  | 'bench_platform_redis_mysql'
  | 'bench_virtual_redis_mysql'
  | 'rewards_platform_lock'
  | 'rewards_platform_no_lock'
  | 'rewards_virtual_lock'
  | 'rewards_virtual_no_lock'
  | 'users_list'
  | 'health'

const target = ref<Target>('bench_platform_none')
const totalRequests = ref(500)
const concurrency = ref(200)
const timeoutMs = ref(5000)

const benchSleepMs = ref(50)
const benchKey = ref('demo')
const fallbackToMysqlOnRedisError = ref(true)

const customerId = ref('customer-001')
const amount = ref(100)
const txnPrefix = ref('txn-load')

const running = ref(false)
const progress = ref({ done: 0, ok: 0, fail: 0 })
const results = ref<{ stats: any; sampleErrors: string[] } | null>(null)

const endpointLabel = computed(() => {
  const map: Record<Target, string> = {
    bench_platform_none: 'GET /api/bench/platform?work=none',
    bench_virtual_none: 'GET /api/bench/virtual?work=none',
    bench_platform_mysql: 'GET /api/bench/platform?work=mysql',
    bench_virtual_mysql: 'GET /api/bench/virtual?work=mysql',
    bench_platform_redis: 'GET /api/bench/platform?work=redis',
    bench_virtual_redis: 'GET /api/bench/virtual?work=redis',
    bench_platform_redis_mysql: 'GET /api/bench/platform?work=redis+mysql',
    bench_virtual_redis_mysql: 'GET /api/bench/virtual?work=redis+mysql',
    rewards_platform_lock: 'POST /api/rewards/platform/lock',
    rewards_platform_no_lock: 'POST /api/rewards/platform/no-lock',
    rewards_virtual_lock: 'POST /api/rewards/virtual/lock',
    rewards_virtual_no_lock: 'POST /api/rewards/virtual/no-lock',
    users_list: 'GET /api/users',
    health: 'GET /actuator/health',
  }
  return map[target.value]
})

function mkTxnId(i: number) {
  return `${txnPrefix.value}-${Date.now()}-${i}-${Math.random().toString(16).slice(2, 6)}`
}

async function task(timeout?: number, i?: number) {
  const t = target.value

  if (t.startsWith('bench_')) {
    const exec = t.includes('_platform_') ? 'platform' : 'virtual'
    const work = t.endsWith('_redis_mysql') ? 'redis+mysql' : t.split('_').slice(2).join('_')
    const apiWork = work === 'redis_mysql' ? 'redis+mysql' : work
    const path = `/api/bench/${exec}?sleepMs=${benchSleepMs.value}&work=${encodeURIComponent(
      apiWork.replace('_', '+')
    )}&key=${encodeURIComponent(benchKey.value)}&fallbackToMysqlOnRedisError=${fallbackToMysqlOnRedisError.value}`

    const res = await apiFetch<any>(path, { timeoutMs: timeout })
    return res.ok ? { ok: true as const } : { ok: false as const, error: res.error.message, status: res.error.status }
  }

  if (t === 'users_list') {
    const res = await apiFetch<any>('/api/users', { timeoutMs: timeout })
    return res.ok ? { ok: true as const } : { ok: false as const, error: res.error.message, status: res.error.status }
  }

  if (t === 'health') {
    const res = await apiFetch<any>('/actuator/health', { timeoutMs: timeout })
    return res.ok ? { ok: true as const } : { ok: false as const, error: res.error.message, status: res.error.status }
  }

  const rewardPath =
    t === 'rewards_platform_lock'
      ? '/api/rewards/platform/lock'
      : t === 'rewards_platform_no_lock'
        ? '/api/rewards/platform/no-lock'
        : t === 'rewards_virtual_lock'
          ? '/api/rewards/virtual/lock'
          : '/api/rewards/virtual/no-lock'

  const body = {
    customerId: customerId.value,
    transactionId: mkTxnId(i ?? 0),
    amount: Number(amount.value),
  }
  const res = await apiFetch<any>(rewardPath, { method: 'POST', ...jsonBody(body), timeoutMs: timeout })
  return res.ok ? { ok: true as const } : { ok: false as const, error: res.error.message, status: res.error.status }
}

async function run() {
  if (running.value) return
  running.value = true
  results.value = null
  progress.value = { done: 0, ok: 0, fail: 0 }

  const ms: number[] = []
  const errs: string[] = []

  const items = await runLoad(
    async (timeout) => {
      const idx = progress.value.done
      const start = performance.now()
      const r = await task(timeout, idx)
      const elapsed = Math.round(performance.now() - start)
      ms.push(elapsed)
      if (!r.ok && errs.length < 12) errs.push(`${r.status ?? 0} ${r.error}`)
      return r.ok ? { ok: true as const } : { ok: false as const, error: r.error, status: r.status }
    },
    { totalRequests: totalRequests.value, concurrency: concurrency.value, timeoutMs: timeoutMs.value },
    (done, ok, fail) => {
      progress.value = { done, ok, fail }
    }
  )

  const okCount = items.filter((x) => x.ok).length
  const failCount = items.length - okCount
  results.value = { stats: summarize(ms, okCount, failCount), sampleErrors: errs }
  running.value = false
}
</script>

<template>
  <section class="card">
    <div class="grid">
      <label class="field">
        <div class="field__label">Target</div>
        <select v-model="target" class="input">
          <optgroup label="Bench (none/sleep/mysql/redis)">
            <option value="bench_platform_none">Bench platform • none</option>
            <option value="bench_virtual_none">Bench virtual • none</option>
            <option value="bench_platform_mysql">Bench platform • mysql</option>
            <option value="bench_virtual_mysql">Bench virtual • mysql</option>
            <option value="bench_platform_redis">Bench platform • redis</option>
            <option value="bench_virtual_redis">Bench virtual • redis</option>
            <option value="bench_platform_redis_mysql">Bench platform • redis+mysql</option>
            <option value="bench_virtual_redis_mysql">Bench virtual • redis+mysql</option>
          </optgroup>
          <optgroup label="Rewards (thanh toán)">
            <option value="rewards_platform_lock">Rewards platform / lock</option>
            <option value="rewards_platform_no_lock">Rewards platform / no-lock</option>
            <option value="rewards_virtual_lock">Rewards virtual / lock</option>
            <option value="rewards_virtual_no_lock">Rewards virtual / no-lock</option>
          </optgroup>
          <optgroup label="Other">
            <option value="users_list">Users list</option>
            <option value="health">Actuator health</option>
          </optgroup>
        </select>
        <div class="hint">{{ endpointLabel }}</div>
      </label>

      <label class="field">
        <div class="field__label">Total requests</div>
        <input v-model.number="totalRequests" class="input" type="number" min="1" step="1" />
      </label>

      <label class="field">
        <div class="field__label">Concurrency</div>
        <input v-model.number="concurrency" class="input" type="number" min="1" step="1" />
      </label>

      <label class="field">
        <div class="field__label">Timeout (ms)</div>
        <input v-model.number="timeoutMs" class="input" type="number" min="100" step="100" />
      </label>
    </div>

    <div class="grid" style="margin-top: 12px">
      <label class="field">
        <div class="field__label">Bench sleepMs</div>
        <input v-model.number="benchSleepMs" class="input" type="number" min="0" step="10" />
      </label>
      <label class="field">
        <div class="field__label">Bench key</div>
        <input v-model="benchKey" class="input" />
      </label>
      <label class="field checkbox">
        <input v-model="fallbackToMysqlOnRedisError" type="checkbox" />
        <span>Redis error → fallback MySQL (để thấy latency tăng khi Redis down)</span>
      </label>
    </div>

    <div class="grid" style="margin-top: 12px">
      <label class="field">
        <div class="field__label">Customer ID (Rewards)</div>
        <input v-model="customerId" class="input" />
      </label>
      <label class="field">
        <div class="field__label">Amount (Rewards)</div>
        <input v-model.number="amount" class="input" type="number" min="0" step="1" />
      </label>
      <label class="field">
        <div class="field__label">Txn prefix (Rewards)</div>
        <input v-model="txnPrefix" class="input" />
      </label>
    </div>

    <div class="actions">
      <button class="btn primary" :disabled="running" @click="run">Run load</button>
      <div class="pill">
        Done {{ progress.done }} • OK {{ progress.ok }} • Fail {{ progress.fail }}
      </div>
    </div>

    <div v-if="results" class="result">
      <div class="result__title">Summary</div>
      <pre class="pre">{{ JSON.stringify(results.stats, null, 2) }}</pre>
      <div v-if="results.sampleErrors.length" class="result__title" style="margin-top: 12px">Sample errors</div>
      <pre v-if="results.sampleErrors.length" class="pre">{{ results.sampleErrors.join('\n') }}</pre>
    </div>
  </section>
</template>

<style scoped>
.card {
  background: rgba(232, 236, 255, 0.04);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 16px;
  padding: 16px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.field__label {
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 6px;
}
.input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(232, 236, 255, 0.14);
  background: rgba(11, 16, 32, 0.45);
  color: inherit;
}
.hint {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.75;
}
.checkbox {
  grid-column: span 2;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  opacity: 0.9;
}
.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 14px;
}
.btn {
  border-radius: 12px;
  border: 1px solid rgba(232, 236, 255, 0.14);
  background: rgba(232, 236, 255, 0.04);
  color: inherit;
  padding: 10px 12px;
  cursor: pointer;
  font-weight: 800;
  font-size: 13px;
}
.btn.primary {
  border-color: rgba(140, 170, 255, 0.55);
  background: rgba(140, 170, 255, 0.14);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.pill {
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(232, 236, 255, 0.12);
  background: rgba(0, 0, 0, 0.18);
}
.result {
  margin-top: 14px;
}
.result__title {
  font-weight: 800;
  font-size: 13px;
  opacity: 0.9;
  margin-bottom: 8px;
}
.pre {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(232, 236, 255, 0.12);
  background: rgba(0, 0, 0, 0.25);
  font-size: 12px;
  line-height: 1.4;
}
@media (max-width: 1024px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .checkbox {
    grid-column: span 1;
  }
}
</style>

