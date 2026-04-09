<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { apiFetch, jsonBody } from '../lib/api'
import { runLoad } from '../lib/loadRunner'
import { summarize, type SummaryStats } from '../lib/stats'
import {
  loadSavedRuns,
  persistSavedRuns,
  clearSavedRuns,
  type SavedLoadRun,
} from '../lib/loadTestStorage'
import { Chart, registerables } from 'chart.js'

Chart.register(...registerables)

const TIMEOUT_MS = 15_000

type Target =
  | 'rewards_single_lock'
  | 'rewards_single_no_lock'
  | 'rewards_platform_lock'
  | 'rewards_platform_no_lock'
  | 'rewards_virtual_lock'
  | 'rewards_virtual_no_lock'
  | 'users_list'
  | 'health'

// 6 reward modes: single / platform / virtual  ×  lock / no-lock
const REWARD_TARGETS: Target[] = [
  'rewards_single_lock',
  'rewards_single_no_lock',
  'rewards_platform_lock',
  'rewards_platform_no_lock',
  'rewards_virtual_lock',
  'rewards_virtual_no_lock',
]

const TARGET_LABELS: Record<string, string> = {
  rewards_single_lock: 'Single / Lock',
  rewards_single_no_lock: 'Single / No-lock',
  rewards_platform_lock: 'Platform / Lock',
  rewards_platform_no_lock: 'Platform / No-lock',
  rewards_virtual_lock: 'Virtual / Lock',
  rewards_virtual_no_lock: 'Virtual / No-lock',
  users_list: 'Users list',
  health: 'Health',
}

// 6 colors: single(red/orange) · platform(blue/sky) · virtual(green/teal)
const CHART_COLORS = [
  'rgba(244, 63,  94,  0.85)',  // single lock
  'rgba(251, 146, 60,  0.85)',  // single no-lock
  'rgba(96,  165, 250, 0.85)',  // platform lock
  'rgba(147, 197, 253, 0.85)',  // platform no-lock
  'rgba(52,  211, 153, 0.85)',  // virtual lock
  'rgba(110, 231, 183, 0.85)',  // virtual no-lock
]

const ENDPOINT_MAP: Record<Target, string> = {
  rewards_single_lock: 'POST /api/rewards/single/lock',
  rewards_single_no_lock: 'POST /api/rewards/single/no-lock',
  rewards_platform_lock: 'POST /api/rewards/platform/lock',
  rewards_platform_no_lock: 'POST /api/rewards/platform/no-lock',
  rewards_virtual_lock: 'POST /api/rewards/virtual/lock',
  rewards_virtual_no_lock: 'POST /api/rewards/virtual/no-lock',
  users_list: 'GET /api/users',
  health: 'GET /actuator/health',
}

// ── State ─────────────────────────────────────────────────
const target = ref<Target>('rewards_platform_lock')
const totalRequests = ref(500)
const concurrency = ref(50)

const userCount = ref(50)
const amount = ref(100)

const running = ref(false)
const progress = ref({ done: 0, ok: 0, fail: 0 })
const results = ref<{ stats: SummaryStats; sampleErrors: string[]; target: Target; rps: number } | null>(null)
const savedRuns = ref<SavedLoadRun[]>(loadSavedRuns())

const runningCompare = ref(false)
const compareProgress = ref('')
const compareResults = ref<Array<{ target: Target; stats: SummaryStats; rps: number }>>([])

// ── Chart canvas refs ──────────────────────────────────────
const latencyChartCanvas = ref<HTMLCanvasElement | null>(null)
const compareChartCanvas = ref<HTMLCanvasElement | null>(null)
const historyChartCanvas = ref<HTMLCanvasElement | null>(null)

let latencyChartInst: Chart | null = null
let compareChartInst: Chart | null = null
let historyChartInst: Chart | null = null

// ── Computed ───────────────────────────────────────────────
const endpointLabel = computed(() => ENDPOINT_MAP[target.value])
const isRewards = computed(() => target.value.startsWith('rewards_'))

// ── Helpers ────────────────────────────────────────────────
function getUserId(i: number): string {
  const n = Math.max(1, userCount.value)
  return `user-${String((i % n) + 1).padStart(4, '0')}`
}

function mkTxnId(i: number): string {
  return `txn-${Date.now()}-${i}-${Math.random().toString(16).slice(2, 8)}`
}

function fmtTime(ts: number): string {
  const d = new Date(ts)
  return [d.getHours(), d.getMinutes(), d.getSeconds()]
    .map((v) => String(v).padStart(2, '0'))
    .join(':')
}

function statCards(s: SummaryStats): { label: string; value: string; ms: boolean }[] {
  return [
    { label: 'Total', value: String(s.total), ms: false },
    { label: 'OK', value: String(s.ok), ms: false },
    { label: 'Fail', value: String(s.fail), ms: false },
    { label: 'P50', value: String(s.p50Ms), ms: true },
    { label: 'P95', value: String(s.p95Ms), ms: true },
    { label: 'P99', value: String(s.p99Ms), ms: true },
    { label: 'Max', value: String(s.maxMs), ms: true },
    { label: 'Avg', value: String(Math.round(s.avgMs)), ms: true },
  ]
}

// ── Core request ───────────────────────────────────────────
async function makeRequest(
  t: Target,
  timeout: number,
  i: number,
): Promise<{ ok: true; status?: number } | { ok: false; status?: number; error: string }> {
  if (t === 'users_list') {
    const res = await apiFetch<unknown>('/api/users', { timeoutMs: timeout })
    return res.ok ? { ok: true } : { ok: false, error: res.error.message, status: res.error.status }
  }
  if (t === 'health') {
    const res = await apiFetch<unknown>('/actuator/health', { timeoutMs: timeout })
    return res.ok ? { ok: true } : { ok: false, error: res.error.message, status: res.error.status }
  }
  const rewardPaths: Record<string, string> = {
    rewards_single_lock: '/api/rewards/single/lock',
    rewards_single_no_lock: '/api/rewards/single/no-lock',
    rewards_platform_lock: '/api/rewards/platform/lock',
    rewards_platform_no_lock: '/api/rewards/platform/no-lock',
    rewards_virtual_lock: '/api/rewards/virtual/lock',
    rewards_virtual_no_lock: '/api/rewards/virtual/no-lock',
  }
  const body = {
    customerId: getUserId(i),
    transactionId: mkTxnId(i),
    amount: Number(amount.value),
  }
  const res = await apiFetch<unknown>(rewardPaths[t], {
    method: 'POST',
    ...jsonBody(body),
    timeoutMs: timeout,
  })
  return res.ok ? { ok: true } : { ok: false, error: res.error.message, status: res.error.status }
}

// ── Load execution ─────────────────────────────────────────
async function execLoad(
  t: Target,
  onProg?: (done: number, ok: number, fail: number) => void,
): Promise<{ stats: SummaryStats; rps: number; wallMs: number; errs: string[] }> {
  const errs: string[] = []
  const wallStart = performance.now()
  const items = await runLoad(
    async (timeout, i) => {
      const r = await makeRequest(t, timeout ?? TIMEOUT_MS, i ?? 0)
      if (!r.ok && errs.length < 12) errs.push(`${r.status ?? 0} ${r.error}`)
      return r
    },
    { totalRequests: totalRequests.value, concurrency: concurrency.value, timeoutMs: TIMEOUT_MS },
    onProg,
  )
  const wallMs = Math.round(performance.now() - wallStart)
  const ms = items.map((x) => x.elapsedMs)
  const okCount = items.filter((x) => x.ok).length
  const stats = summarize(ms, okCount, items.length - okCount)
  const rps = wallMs > 0 ? Math.round((okCount / wallMs) * 1000) : 0
  return { stats, rps, wallMs, errs }
}

function saveRun(
  t: Target,
  stats: SummaryStats,
  rps: number,
  wallMs: number,
  errs: string[],
): void {
  const run: SavedLoadRun = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    at: Date.now(),
    target: t,
    totalRequests: totalRequests.value,
    concurrency: concurrency.value,
    timeoutMs: TIMEOUT_MS,
    stats,
    wallMs,
    rps,
    sampleErrors: errs,
  }
  savedRuns.value = [run, ...savedRuns.value].slice(0, 80)
  persistSavedRuns(savedRuns.value)
}

// ── Actions ────────────────────────────────────────────────
async function run() {
  if (running.value || runningCompare.value) return
  running.value = true
  results.value = null
  progress.value = { done: 0, ok: 0, fail: 0 }

  const { stats, rps, wallMs, errs } = await execLoad(target.value, (done, ok, fail) => {
    progress.value = { done, ok, fail }
  })

  results.value = { stats, sampleErrors: errs, target: target.value, rps }
  saveRun(target.value, stats, rps, wallMs, errs)
  running.value = false

  await nextTick()
  renderLatencyChart(stats, target.value)
  renderHistoryChart()
}

async function runCompare(targets: Target[]) {
  if (running.value || runningCompare.value) return
  runningCompare.value = true
  compareResults.value = []

  for (const t of targets) {
    compareProgress.value = `Running ${TARGET_LABELS[t] ?? t}…`
    const { stats, rps, wallMs, errs } = await execLoad(t)
    compareResults.value = [...compareResults.value, { target: t, stats, rps }]
    saveRun(t, stats, rps, wallMs, errs)
  }

  compareProgress.value = 'Xong!'
  runningCompare.value = false

  await nextTick()
  renderCompareChart()
  renderHistoryChart()
}

function clearHistory() {
  clearSavedRuns()
  savedRuns.value = []
  historyChartInst?.destroy()
  historyChartInst = null
}

// ── Chart rendering ────────────────────────────────────────
function renderLatencyChart(stats: SummaryStats, t: Target): void {
  if (!latencyChartCanvas.value) return
  latencyChartInst?.destroy()
  const label = TARGET_LABELS[t] ?? t
  latencyChartInst = new Chart(latencyChartCanvas.value, {
    type: 'bar',
    data: {
      labels: ['Min', 'P50', 'P95', 'P99', 'Max', 'Avg'],
      datasets: [
        {
          label,
          data: [
            stats.minMs,
            stats.p50Ms,
            stats.p95Ms,
            stats.p99Ms,
            stats.maxMs,
            Math.round(stats.avgMs),
          ],
          backgroundColor: [
            'rgba(96, 165, 250, 0.75)',
            'rgba(52, 211, 153, 0.75)',
            'rgba(251, 191, 36, 0.75)',
            'rgba(249, 115, 22, 0.75)',
            'rgba(244, 63, 94, 0.75)',
            'rgba(167, 139, 250, 0.75)',
          ],
          borderRadius: 6,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (ctx) => `${ctx.parsed.y} ms` } },
      },
      scales: {
        y: {
          beginAtZero: true,
          grid: { color: 'rgba(232,236,255,0.06)' },
          ticks: { color: 'rgba(232,236,255,0.6)', callback: (v) => `${v}ms` },
        },
        x: { ticks: { color: 'rgba(232,236,255,0.6)' }, grid: { display: false } },
      },
    },
  })
}

function renderCompareChart(): void {
  if (!compareChartCanvas.value || compareResults.value.length === 0) return
  compareChartInst?.destroy()
  compareChartInst = new Chart(compareChartCanvas.value, {
    type: 'bar',
    data: {
      labels: ['P50', 'P95', 'P99', 'Max', 'Avg'],
      datasets: compareResults.value.map((r, idx) => ({
        label: TARGET_LABELS[r.target] ?? r.target,
        data: [
          r.stats.p50Ms,
          r.stats.p95Ms,
          r.stats.p99Ms,
          r.stats.maxMs,
          Math.round(r.stats.avgMs),
        ],
        backgroundColor: CHART_COLORS[idx % CHART_COLORS.length],
        borderRadius: 5,
      })),
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: 'top', labels: { color: 'rgba(232,236,255,0.75)', padding: 16 } },
        tooltip: {
          callbacks: { label: (ctx) => `${ctx.dataset.label}: ${ctx.parsed.y} ms` },
        },
      },
      scales: {
        y: {
          beginAtZero: true,
          grid: { color: 'rgba(232,236,255,0.06)' },
          ticks: { color: 'rgba(232,236,255,0.6)', callback: (v) => `${v}ms` },
        },
        x: { ticks: { color: 'rgba(232,236,255,0.6)' }, grid: { display: false } },
      },
    },
  })
}

function renderHistoryChart(): void {
  if (!historyChartCanvas.value || savedRuns.value.length < 2) return
  historyChartInst?.destroy()
  const recent = [...savedRuns.value].slice(0, 20).reverse()
  historyChartInst = new Chart(historyChartCanvas.value, {
    type: 'line',
    data: {
      labels: recent.map((r) => fmtTime(r.at)),
      datasets: [
        {
          label: 'P95 (ms)',
          data: recent.map((r) => r.stats.p95Ms),
          borderColor: '#f472b6',
          backgroundColor: 'rgba(244, 114, 182, 0.08)',
          tension: 0.35,
          fill: true,
          pointRadius: 4,
          pointHoverRadius: 6,
        },
        {
          label: 'P50 (ms)',
          data: recent.map((r) => r.stats.p50Ms),
          borderColor: '#34d399',
          backgroundColor: 'rgba(52, 211, 153, 0.08)',
          tension: 0.35,
          fill: true,
          pointRadius: 4,
          pointHoverRadius: 6,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: 'top', labels: { color: 'rgba(232,236,255,0.75)', padding: 16 } },
        tooltip: {
          callbacks: {
            label: (ctx) => {
              const run = recent[ctx.dataIndex]
              const tgt = run ? (TARGET_LABELS[run.target] ?? run.target) : ''
              return `${ctx.dataset.label}: ${ctx.parsed.y} ms  (${tgt})`
            },
          },
        },
      },
      scales: {
        y: {
          beginAtZero: true,
          grid: { color: 'rgba(232,236,255,0.06)' },
          ticks: { color: 'rgba(232,236,255,0.6)', callback: (v) => `${v}ms` },
        },
        x: { ticks: { color: 'rgba(232,236,255,0.5)', maxRotation: 45 }, grid: { display: false } },
      },
    },
  })
}

// ── Lifecycle ──────────────────────────────────────────────
onMounted(async () => {
  if (savedRuns.value.length >= 2) {
    await nextTick()
    renderHistoryChart()
  }
})

onUnmounted(() => {
  latencyChartInst?.destroy()
  compareChartInst?.destroy()
  historyChartInst?.destroy()
})
</script>

<template>
  <div class="lt-root">
    <!-- ── Config ───────────────────────────── -->
    <section class="card">
      <div class="card__title">Cấu hình</div>

      <!-- Row 1: core params -->
      <div class="grid grid-4">
        <label class="field">
          <div class="field__label">Target</div>
          <select v-model="target" class="input">
            <optgroup label="Rewards · lock">
              <option value="rewards_single_lock">Single   / lock</option>
              <option value="rewards_platform_lock">Platform / lock</option>
              <option value="rewards_virtual_lock">Virtual  / lock</option>
            </optgroup>
            <optgroup label="Rewards · no-lock">
              <option value="rewards_single_no_lock">Single   / no-lock</option>
              <option value="rewards_platform_no_lock">Platform / no-lock</option>
              <option value="rewards_virtual_no_lock">Virtual  / no-lock</option>
            </optgroup>
            <optgroup label="Other">
              <option value="users_list">Users list</option>
              <option value="health">Actuator health</option>
            </optgroup>
          </select>
          <div class="field__hint">{{ endpointLabel }}</div>
        </label>

        <label class="field">
          <div class="field__label">Total Requests</div>
          <input v-model.number="totalRequests" class="input" type="number" min="1" step="1" />
          <div class="field__hint">Tổng số request gửi đến server trong một lần test</div>
        </label>

        <label class="field">
          <div class="field__label">Concurrency</div>
          <input v-model.number="concurrency" class="input" type="number" min="1" step="1" />
          <div class="field__hint">Số request chạy đồng thời (tương đương virtual users)</div>
        </label>

        <label class="field">
          <div class="field__label">Timeout</div>
          <div class="input input--readonly">15,000 ms</div>
          <div class="field__hint">Thời gian chờ tối đa mỗi request — cố định, không thể thay đổi</div>
        </label>
      </div>

      <!-- Row 2: Rewards options -->
      <div v-if="isRewards" class="grid grid-2" style="margin-top: 12px">
        <label class="field">
          <div class="field__label">User Count</div>
          <input v-model.number="userCount" class="input" type="number" min="1" step="1" />
          <div class="field__hint">
            Số lượng user ID khác nhau dùng trong test. Requests được phân bổ vòng tròn qua N user.
            <strong>Nhiều user = ít lock contention</strong> → kết quả trung thực hơn khi so sánh lock vs no-lock.
            Đặt về 1 để chủ ý tạo bottleneck.
          </div>
        </label>

        <label class="field">
          <div class="field__label">Amount</div>
          <input v-model.number="amount" class="input" type="number" min="0" step="1" />
          <div class="field__hint">Số điểm thưởng tích lũy cho mỗi giao dịch</div>
        </label>
      </div>

      <!-- Actions -->
      <div class="actions">
        <button class="btn btn--primary" :disabled="running || runningCompare" @click="run">
          <span v-if="running">Running… {{ progress.done }} / {{ totalRequests }}</span>
          <span v-else>▶ Run</span>
        </button>

        <!-- Compare all 6 reward modes (single/platform/virtual × lock/no-lock) -->
        <button
          v-if="isRewards"
          class="btn btn--compare"
          :disabled="running || runningCompare"
          @click="runCompare(REWARD_TARGETS)"
        >
          <span v-if="runningCompare">{{ compareProgress }}</span>
          <span v-else>⚡ Compare 6 reward modes</span>
        </button>

        <button
          v-if="savedRuns.length > 0"
          class="btn btn--danger"
          :disabled="running || runningCompare"
          @click="clearHistory"
        >
          🗑 Clear history
        </button>

        <div v-if="running" class="pill">
          Done {{ progress.done }} &nbsp;·&nbsp; OK
          <span class="ok"> {{ progress.ok }}</span> &nbsp;·&nbsp; Fail
          <span class="fail"> {{ progress.fail }}</span>
        </div>
      </div>
    </section>

    <!-- ── Current Run Result ─────────────────── -->
    <section v-if="results" class="card">
      <div class="card__title">
        Result —
        <span class="badge">{{ TARGET_LABELS[results.target] ?? results.target }}</span>
        <span class="badge badge--rps">{{ results.rps }} RPS</span>
      </div>

      <div class="result-layout">
        <div class="stat-grid">
          <div
            v-for="s in statCards(results.stats)"
            :key="s.label"
            class="stat-card"
            :class="{ 'stat-card--fail': s.label === 'Fail' && Number(s.value) > 0 }"
          >
            <div class="stat-card__label">{{ s.label }}</div>
            <div class="stat-card__value">
              {{ s.value }}<span v-if="s.ms" class="stat-card__unit">ms</span>
            </div>
          </div>
        </div>

        <div class="chart-wrap">
          <canvas ref="latencyChartCanvas"></canvas>
        </div>
      </div>

      <div v-if="results.sampleErrors.length" style="margin-top: 14px">
        <div class="section-label">Sample errors</div>
        <pre class="pre">{{ results.sampleErrors.join('\n') }}</pre>
      </div>
    </section>

    <!-- ── Compare Modes ─────────────────────── -->
    <section v-if="compareResults.length > 0" class="card">
      <div class="card__title">So sánh — {{ compareResults.map(r => TARGET_LABELS[r.target] ?? r.target).join(' · ') }}</div>

      <div class="chart-wrap chart-wrap--tall">
        <canvas ref="compareChartCanvas"></canvas>
      </div>

      <table class="data-table" style="margin-top: 16px">
        <thead>
          <tr>
            <th>Mode</th>
            <th>OK</th>
            <th>Fail</th>
            <th>P50</th>
            <th>P95</th>
            <th>P99</th>
            <th>Max</th>
            <th>Avg</th>
            <th>RPS</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in compareResults" :key="r.target">
            <td class="target-cell">{{ TARGET_LABELS[r.target] ?? r.target }}</td>
            <td class="ok">{{ r.stats.ok }}</td>
            <td :class="r.stats.fail > 0 ? 'fail' : ''">{{ r.stats.fail }}</td>
            <td>{{ r.stats.p50Ms }} ms</td>
            <td>{{ r.stats.p95Ms }} ms</td>
            <td>{{ r.stats.p99Ms }} ms</td>
            <td>{{ r.stats.maxMs }} ms</td>
            <td>{{ Math.round(r.stats.avgMs) }} ms</td>
            <td class="rps-cell">{{ r.rps }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- ── History ────────────────────────────── -->
    <section v-if="savedRuns.length >= 2" class="card">
      <div class="card__title">History <span class="badge">{{ savedRuns.length }} runs</span></div>

      <div class="chart-wrap">
        <canvas ref="historyChartCanvas"></canvas>
      </div>

      <table class="data-table" style="margin-top: 16px">
        <thead>
          <tr>
            <th>Time</th>
            <th>Target</th>
            <th>Req</th>
            <th>Conc</th>
            <th>P50</th>
            <th>P95</th>
            <th>P99</th>
            <th>RPS</th>
            <th>OK / Fail</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in savedRuns.slice(0, 15)" :key="r.id">
            <td class="time-cell">{{ fmtTime(r.at) }}</td>
            <td class="target-cell">{{ TARGET_LABELS[r.target] ?? r.target }}</td>
            <td>{{ r.totalRequests }}</td>
            <td>{{ r.concurrency }}</td>
            <td>{{ r.stats.p50Ms }} ms</td>
            <td>{{ r.stats.p95Ms }} ms</td>
            <td>{{ r.stats.p99Ms }} ms</td>
            <td class="rps-cell">{{ r.rps }}</td>
            <td>
              <span class="ok">{{ r.stats.ok }}</span> /
              <span :class="r.stats.fail > 0 ? 'fail' : ''">{{ r.stats.fail }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
/* ── Layout ────────────────────────────── */
.lt-root {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  background: rgba(232, 236, 255, 0.03);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 16px;
  padding: 18px 20px;
}

.card__title {
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ── Grids ─────────────────────────────── */
.grid {
  display: grid;
  gap: 12px;
}
.grid-4 { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.grid-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.grid-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); }

/* ── Field ─────────────────────────────── */
.field {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.field__label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.65;
  margin-bottom: 6px;
}

.field__hint {
  font-size: 11px;
  line-height: 1.45;
  opacity: 0.5;
  margin-top: 6px;
}

.field__hint strong {
  opacity: 1;
  color: #fbbf24;
}

/* ── Input ─────────────────────────────── */
.input {
  width: 100%;
  box-sizing: border-box;
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px solid rgba(232, 236, 255, 0.13);
  background: rgba(10, 14, 28, 0.5);
  color: inherit;
  font-size: 13px;
}

.input--readonly {
  display: flex;
  align-items: center;
  font-weight: 600;
  font-size: 13px;
  color: rgba(232, 236, 255, 0.5);
  cursor: default;
  user-select: none;
  background: rgba(232, 236, 255, 0.03);
  border-style: dashed;
}

.checkbox-field {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 10px;
  padding-top: 6px;
}

.checkbox-input {
  margin-top: 2px;
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  cursor: pointer;
}

/* ── Actions ───────────────────────────── */
.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  margin-top: 16px;
}

.btn {
  border-radius: 10px;
  border: 1px solid rgba(232, 236, 255, 0.14);
  background: rgba(232, 236, 255, 0.04);
  color: inherit;
  padding: 9px 16px;
  cursor: pointer;
  font-weight: 700;
  font-size: 13px;
  transition: background 0.15s, border-color 0.15s;
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn--primary {
  border-color: rgba(140, 170, 255, 0.55);
  background: rgba(140, 170, 255, 0.14);
}
.btn--primary:not(:disabled):hover {
  background: rgba(140, 170, 255, 0.22);
}

.btn--compare {
  border-color: rgba(52, 211, 153, 0.5);
  background: rgba(52, 211, 153, 0.1);
}
.btn--compare:not(:disabled):hover {
  background: rgba(52, 211, 153, 0.18);
}

.btn--danger {
  border-color: rgba(244, 114, 182, 0.4);
  background: rgba(244, 114, 182, 0.07);
  font-size: 12px;
}
.btn--danger:not(:disabled):hover {
  background: rgba(244, 114, 182, 0.14);
}

.pill {
  font-size: 12px;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(232, 236, 255, 0.12);
  background: rgba(0, 0, 0, 0.2);
}

/* ── Result layout ─────────────────────── */
.result-layout {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 16px;
  align-items: start;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(80px, 1fr));
  gap: 8px;
}

.stat-card {
  background: rgba(232, 236, 255, 0.04);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 10px;
  padding: 10px 12px;
  min-width: 80px;
}

.stat-card--fail {
  border-color: rgba(244, 114, 182, 0.4);
  background: rgba(244, 63, 94, 0.07);
}

.stat-card__label {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.5;
  margin-bottom: 4px;
}

.stat-card__value {
  font-size: 18px;
  font-weight: 700;
}

.stat-card__unit {
  font-size: 11px;
  font-weight: 400;
  opacity: 0.5;
  margin-left: 2px;
}

/* ── Chart ─────────────────────────────── */
.chart-wrap {
  height: 220px;
  position: relative;
}

.chart-wrap--tall {
  height: 300px;
}

/* ── Tables ────────────────────────────── */
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.data-table th {
  text-align: left;
  padding: 7px 10px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.5;
  border-bottom: 1px solid rgba(232, 236, 255, 0.1);
}

.data-table td {
  padding: 7px 10px;
  border-bottom: 1px solid rgba(232, 236, 255, 0.05);
}

.data-table tr:last-child td {
  border-bottom: none;
}

.target-cell {
  font-weight: 600;
  font-size: 12px;
}

.time-cell {
  opacity: 0.6;
  font-variant-numeric: tabular-nums;
}

.rps-cell {
  font-weight: 600;
  color: #60a5fa;
}

/* ── Misc ──────────────────────────────── */
.badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(232, 236, 255, 0.08);
  border: 1px solid rgba(232, 236, 255, 0.12);
}

.badge--rps {
  background: rgba(96, 165, 250, 0.12);
  border-color: rgba(96, 165, 250, 0.3);
  color: #93c5fd;
}

.section-label {
  font-weight: 700;
  font-size: 12px;
  opacity: 0.7;
  margin-bottom: 6px;
}

.pre {
  margin: 0;
  max-height: 180px;
  overflow: auto;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid rgba(232, 236, 255, 0.1);
  background: rgba(0, 0, 0, 0.22);
  font-size: 11px;
  line-height: 1.45;
}

.ok   { color: #34d399; font-weight: 600; }
.fail { color: #f87171; font-weight: 600; }

/* ── Responsive ────────────────────────── */
@media (max-width: 1100px) {
  .grid-4 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .grid-3 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .result-layout { grid-template-columns: 1fr; }
  .stat-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .grid-4,
  .grid-3,
  .grid-2 { grid-template-columns: 1fr; }
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
