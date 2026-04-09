<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { apiFetch } from '../lib/api'

type OutboxStats = {
  redisStatus: string
  outboxQueueSize: number
  totalProcessed: number
  lastProcessedAt: string
  processedLast1m: number
  processedLast5m: number
  processedLast1h: number
  recentActivity: { minute_slot: string; count: number; total_points: number }[]
}

const stats = ref<OutboxStats | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const lastRefresh = ref<Date | null>(null)
const autoRefresh = ref(false)

let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  error.value = null
  const res = await apiFetch<OutboxStats>('/api/outbox/stats')
  loading.value = false
  if (!res.ok) { error.value = res.error.message; return }
  stats.value = res.data
  lastRefresh.value = new Date()
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) {
    timer = setInterval(load, 5000)
  } else {
    if (timer) { clearInterval(timer); timer = null }
  }
}

function fmtDate(s: string) {
  if (!s) return '—'
  try { return new Date(s).toLocaleString('vi-VN') } catch { return s }
}

function queueColor(n: number) {
  if (n <= 0) return 'val--ok'
  if (n < 100) return 'val--warn'
  return 'val--error'
}

onMounted(load)
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="page">
    <!-- Toolbar -->
    <div class="toolbar">
      <button class="btn btn--primary" :disabled="loading" @click="load">
        {{ loading ? 'Đang tải…' : '↻ Refresh' }}
      </button>
      <button
        class="btn"
        :class="{ 'btn--active': autoRefresh }"
        @click="toggleAutoRefresh"
      >
        {{ autoRefresh ? '⏸ Dừng auto-refresh (5s)' : '▶ Auto-refresh (5s)' }}
      </button>
      <span v-if="lastRefresh" class="refresh-time">
        Cập nhật lần cuối: {{ lastRefresh.toLocaleTimeString('vi-VN') }}
      </span>
      <div v-if="error" class="error-msg">{{ error }}</div>
    </div>

    <template v-if="stats">
      <!-- Status cards -->
      <div class="stats-grid">
        <div class="stat-card" :class="stats.redisStatus === 'ok' ? 'stat-card--ok' : 'stat-card--error'">
          <div class="stat-card__label">Redis Status</div>
          <div class="stat-card__value">
            <span :class="stats.redisStatus === 'ok' ? 'val--ok' : 'val--error'">
              {{ stats.redisStatus === 'ok' ? '● Online' : '● ' + stats.redisStatus }}
            </span>
          </div>
        </div>

        <div class="stat-card" :class="stats.outboxQueueSize > 0 ? 'stat-card--warn' : ''">
          <div class="stat-card__label">Outbox Queue (Redis)</div>
          <div class="stat-card__value" :class="queueColor(stats.outboxQueueSize)">
            {{ stats.outboxQueueSize < 0 ? 'N/A' : stats.outboxQueueSize.toLocaleString() }}
          </div>
          <div class="stat-card__sub">items chờ được drain</div>
        </div>

        <div class="stat-card">
          <div class="stat-card__label">Đã xử lý (tổng)</div>
          <div class="stat-card__value">{{ stats.totalProcessed.toLocaleString() }}</div>
          <div class="stat-card__sub">records trong reward_ledger</div>
        </div>

        <div class="stat-card">
          <div class="stat-card__label">Lần xử lý cuối</div>
          <div class="stat-card__value stat-card__value--sm">{{ fmtDate(stats.lastProcessedAt) }}</div>
        </div>
      </div>

      <!-- Throughput cards -->
      <div class="throughput-row">
        <div class="tp-card">
          <div class="tp-card__period">1 phút qua</div>
          <div class="tp-card__value">{{ stats.processedLast1m.toLocaleString() }}</div>
          <div class="tp-card__label">transactions</div>
        </div>
        <div class="tp-card">
          <div class="tp-card__period">5 phút qua</div>
          <div class="tp-card__value">{{ stats.processedLast5m.toLocaleString() }}</div>
          <div class="tp-card__label">transactions</div>
        </div>
        <div class="tp-card">
          <div class="tp-card__period">1 giờ qua</div>
          <div class="tp-card__value">{{ stats.processedLast1h.toLocaleString() }}</div>
          <div class="tp-card__label">transactions</div>
        </div>
      </div>

      <!-- Architecture note -->
      <section class="card card--note">
        <div class="card__title">Batch architecture</div>
        <div class="arch-diagram">
          <div class="arch-step">
            <div class="arch-step__icon">📱</div>
            <div class="arch-step__label">POS API (8080)</div>
            <div class="arch-step__sub">ghi vào Redis outbox</div>
          </div>
          <div class="arch-arrow">→</div>
          <div class="arch-step">
            <div class="arch-step__icon">📦</div>
            <div class="arch-step__label">Redis List</div>
            <div class="arch-step__sub">rewards:outbox</div>
            <div class="arch-step__badge" :class="stats.outboxQueueSize > 0 ? 'badge--warn' : 'badge--ok'">
              {{ stats.outboxQueueSize < 0 ? 'N/A' : stats.outboxQueueSize }} items
            </div>
          </div>
          <div class="arch-arrow">→</div>
          <div class="arch-step">
            <div class="arch-step__icon">⚙️</div>
            <div class="arch-step__label">Batch App (8081)</div>
            <div class="arch-step__sub">drain mỗi 60s</div>
          </div>
          <div class="arch-arrow">→</div>
          <div class="arch-step">
            <div class="arch-step__icon">🗄️</div>
            <div class="arch-step__label">MySQL</div>
            <div class="arch-step__sub">reward_ledger</div>
            <div class="arch-step__badge badge--ok">{{ stats.totalProcessed.toLocaleString() }} records</div>
          </div>
        </div>
      </section>

      <!-- Recent activity log -->
      <section class="card" v-if="stats.recentActivity.length > 0">
        <div class="card__title">Hoạt động gần đây (30 phút)</div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Thời điểm</th>
                <th>Số giao dịch</th>
                <th>Tổng điểm</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in stats.recentActivity" :key="a.minute_slot">
                <td class="mono">{{ a.minute_slot }}</td>
                <td>{{ a.count }}</td>
                <td class="pos">+{{ a.total_points }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section class="card empty-activity" v-else>
        <div class="empty">
          Chưa có hoạt động trong 30 phút qua.<br />
          Hãy chạy <strong>Load Test</strong> với Rewards target để tạo dữ liệu.
        </div>
      </section>
    </template>

    <div v-else-if="!loading" class="no-data">Không thể tải dữ liệu. Kiểm tra backend.</div>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }

.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }

.btn {
  padding: 8px 14px;
  border-radius: 9px;
  border: 1px solid rgba(232, 236, 255, 0.14);
  background: rgba(232, 236, 255, 0.04);
  color: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.12s;
}

.btn:disabled { opacity: 0.45; cursor: not-allowed; }
.btn--primary { border-color: rgba(140, 170, 255, 0.5); background: rgba(140, 170, 255, 0.12); }
.btn--active { border-color: rgba(52, 211, 153, 0.5); background: rgba(52, 211, 153, 0.1); }

.refresh-time { font-size: 12px; opacity: 0.5; }
.error-msg { color: #f87171; font-size: 13px; }

.card {
  background: rgba(232, 236, 255, 0.03);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 16px;
  padding: 18px 20px;
}

.card--note { border-color: rgba(96, 165, 250, 0.2); background: rgba(96, 165, 250, 0.03); }

.card__title {
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 16px;
}

/* Stats grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-card {
  background: rgba(232, 236, 255, 0.04);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 12px;
  padding: 14px 16px;
}

.stat-card--ok { border-color: rgba(52, 211, 153, 0.3); }
.stat-card--warn { border-color: rgba(251, 191, 36, 0.35); }
.stat-card--error { border-color: rgba(244, 114, 182, 0.35); }

.stat-card__label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.5;
  margin-bottom: 6px;
}

.stat-card__value { font-size: 22px; font-weight: 700; }
.stat-card__value--sm { font-size: 14px; }
.stat-card__sub { font-size: 11px; opacity: 0.45; margin-top: 4px; }

.val--ok { color: #34d399; }
.val--warn { color: #fbbf24; }
.val--error { color: #f87171; }

/* Throughput */
.throughput-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.tp-card {
  background: rgba(232, 236, 255, 0.03);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 12px;
  padding: 14px 16px;
  text-align: center;
}

.tp-card__period { font-size: 12px; opacity: 0.55; margin-bottom: 6px; }
.tp-card__value { font-size: 28px; font-weight: 700; color: #60a5fa; }
.tp-card__label { font-size: 11px; opacity: 0.5; margin-top: 4px; }

/* Architecture diagram */
.arch-diagram {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.arch-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: rgba(232, 236, 255, 0.04);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 12px;
  padding: 12px 16px;
  min-width: 120px;
  text-align: center;
}

.arch-step__icon { font-size: 20px; }
.arch-step__label { font-size: 12px; font-weight: 700; }
.arch-step__sub { font-size: 11px; opacity: 0.5; }

.arch-step__badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  margin-top: 4px;
}

.badge--ok { background: rgba(52, 211, 153, 0.15); color: #34d399; border: 1px solid rgba(52, 211, 153, 0.3); }
.badge--warn { background: rgba(251, 191, 36, 0.15); color: #fbbf24; border: 1px solid rgba(251, 191, 36, 0.3); }

.arch-arrow { font-size: 20px; opacity: 0.4; flex-shrink: 0; }

/* Table */
.table-wrap { overflow-x: auto; }

.table { width: 100%; border-collapse: collapse; font-size: 12px; }
.table th {
  text-align: left;
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.5;
  border-bottom: 1px solid rgba(232, 236, 255, 0.1);
}

.table td {
  padding: 8px 10px;
  border-bottom: 1px solid rgba(232, 236, 255, 0.05);
}

.table tr:last-child td { border-bottom: none; }
.mono { font-family: ui-monospace, Menlo, Monaco, Consolas, monospace; }
.pos { color: #34d399; font-weight: 600; }

.empty { text-align: center; opacity: 0.5; padding: 28px; line-height: 1.6; }
.empty-activity { }
.no-data { opacity: 0.5; text-align: center; padding: 40px; }

@media (max-width: 900px) {
  .stats-grid { grid-template-columns: 1fr 1fr; }
  .arch-diagram { flex-direction: column; }
  .arch-arrow { transform: rotate(90deg); }
}

@media (max-width: 500px) {
  .stats-grid, .throughput-row { grid-template-columns: 1fr; }
}
</style>
