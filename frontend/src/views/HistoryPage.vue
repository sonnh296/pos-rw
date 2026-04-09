<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { apiFetch } from '../lib/api'

type LedgerRow = {
  id: number
  customer_id: string
  transaction_id: string
  amount: number
  points_delta: number
  created_at: string
}

type Stats = {
  totalTransactions: number
  uniqueCustomers: number
  totalPoints: number
  lastTransactionAt: string
  topCustomers: { customer_id: string; balance: number }[]
}

const rows = ref<LedgerRow[]>([])
const stats = ref<Stats | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const total = ref(0)
const limit = ref(50)
const offset = ref(0)

const filterCustomerId = ref('')

async function loadStats() {
  const res = await apiFetch<Stats>('/api/ledger/stats')
  if (res.ok) stats.value = res.data
}

async function load(resetOffset = false) {
  if (resetOffset) offset.value = 0
  loading.value = true
  error.value = null

  const params = new URLSearchParams({
    limit: String(limit.value),
    offset: String(offset.value),
  })
  if (filterCustomerId.value.trim()) {
    params.set('customerId', filterCustomerId.value.trim())
  }

  const res = await apiFetch<{ rows: LedgerRow[]; total: number }>(`/api/ledger?${params}`)
  loading.value = false
  if (!res.ok) { error.value = res.error.message; return }
  rows.value = res.data.rows
  total.value = res.data.total
}

function prevPage() {
  if (offset.value <= 0) return
  offset.value = Math.max(0, offset.value - limit.value)
  load()
}

function nextPage() {
  if (offset.value + limit.value >= total.value) return
  offset.value += limit.value
  load()
}

function fmtDate(s: string) {
  try { return new Date(s).toLocaleString('vi-VN') } catch { return s }
}

function truncate(s: string, n = 20) {
  return s.length > n ? s.slice(0, n) + '…' : s
}

onMounted(() => {
  loadStats()
  load()
})
</script>

<template>
  <div class="page">
    <!-- Stats row -->
    <div v-if="stats" class="stats-row">
      <div class="stat-card">
        <div class="stat-card__label">Tổng giao dịch</div>
        <div class="stat-card__value">{{ stats.totalTransactions.toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card__label">Unique customers</div>
        <div class="stat-card__value">{{ stats.uniqueCustomers.toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card__label">Tổng điểm thưởng</div>
        <div class="stat-card__value">{{ stats.totalPoints.toLocaleString() }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card__label">Giao dịch cuối</div>
        <div class="stat-card__value stat-card__value--sm">
          {{ stats.lastTransactionAt ? fmtDate(stats.lastTransactionAt) : '—' }}
        </div>
      </div>
    </div>

    <!-- Top customers + filter -->
    <div class="two-col" v-if="stats && stats.topCustomers.length > 0">
      <section class="card">
        <div class="card__title">Top customers (by balance)</div>
        <table class="table">
          <thead><tr><th>Customer ID</th><th>Balance</th></tr></thead>
          <tbody>
            <tr v-for="c in stats.topCustomers" :key="c.customer_id">
              <td
                class="mono customer-link"
                @click="filterCustomerId = c.customer_id; load(true)"
                :title="'Lọc theo ' + c.customer_id"
              >{{ c.customer_id }}</td>
              <td>{{ c.balance.toLocaleString() }} pts</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Filter panel -->
      <section class="card">
        <div class="card__title">Lọc giao dịch</div>
        <label class="field">
          <div class="field__label">Customer ID</div>
          <input
            v-model="filterCustomerId"
            class="input"
            placeholder="user-0001 (bỏ trống = tất cả)"
            @keyup.enter="load(true)"
          />
        </label>
        <div class="filter-actions">
          <button class="btn btn--primary" :disabled="loading" @click="load(true)">Tìm kiếm</button>
          <button
            class="btn"
            v-if="filterCustomerId"
            @click="filterCustomerId = ''; load(true)"
          >✕ Xóa filter</button>
        </div>
      </section>
    </div>

    <!-- Ledger table -->
    <section class="card">
      <div class="table-header">
        <div class="card__title" style="margin-bottom: 0">
          Reward Ledger
          <span class="badge">{{ total.toLocaleString() }} records</span>
          <span v-if="filterCustomerId" class="badge badge--filter">{{ filterCustomerId }}</span>
        </div>
        <button class="btn" :disabled="loading" @click="loadStats(); load()">↻ Refresh</button>
      </div>

      <div v-if="error" class="error-msg">{{ error }}</div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>#</th>
              <th>Customer</th>
              <th>Transaction ID</th>
              <th>Amount</th>
              <th>Points Δ</th>
              <th>Created At</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="empty">Đang tải…</td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td colspan="6" class="empty">Không có dữ liệu. Hãy chạy Load Test trước.</td>
            </tr>
            <tr v-for="r in rows" :key="r.id">
              <td class="id-num">{{ r.id }}</td>
              <td
                class="mono customer-link"
                @click="filterCustomerId = r.customer_id; load(true)"
                :title="'Lọc theo ' + r.customer_id"
              >{{ r.customer_id }}</td>
              <td class="mono txn-cell" :title="r.transaction_id">{{ truncate(r.transaction_id, 24) }}</td>
              <td>{{ r.amount }}</td>
              <td :class="r.points_delta >= 0 ? 'pos' : 'neg'">
                {{ r.points_delta >= 0 ? '+' : '' }}{{ r.points_delta }}
              </td>
              <td class="date-cell">{{ fmtDate(r.created_at) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="pagination">
        <button class="btn" :disabled="offset === 0 || loading" @click="prevPage">← Trước</button>
        <span class="page-info">
          {{ offset + 1 }} – {{ Math.min(offset + limit, total) }} / {{ total.toLocaleString() }}
        </span>
        <button class="btn" :disabled="offset + limit >= total || loading" @click="nextPage">Sau →</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }

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
  flex-wrap: wrap;
}

.stats-row {
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

.stat-card__label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.5;
  margin-bottom: 6px;
}

.stat-card__value {
  font-size: 22px;
  font-weight: 700;
}

.stat-card__value--sm { font-size: 14px; }

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.field { display: flex; flex-direction: column; margin-bottom: 10px; }
.field__label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.55;
  margin-bottom: 6px;
}

.input {
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px solid rgba(232, 236, 255, 0.13);
  background: rgba(10, 14, 28, 0.5);
  color: inherit;
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}

.filter-actions { display: flex; gap: 8px; margin-top: 4px; }

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
}

.btn:disabled { opacity: 0.4; cursor: not-allowed; }
.btn--primary { border-color: rgba(140, 170, 255, 0.5); background: rgba(140, 170, 255, 0.12); }

.badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(232, 236, 255, 0.08);
  border: 1px solid rgba(232, 236, 255, 0.12);
}

.badge--filter {
  background: rgba(96, 165, 250, 0.12);
  border-color: rgba(96, 165, 250, 0.3);
  color: #93c5fd;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
}

.table-wrap { overflow-x: auto; }

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.table th {
  text-align: left;
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.5;
  border-bottom: 1px solid rgba(232, 236, 255, 0.1);
  white-space: nowrap;
}

.table td {
  padding: 8px 10px;
  border-bottom: 1px solid rgba(232, 236, 255, 0.05);
  vertical-align: middle;
}

.table tr:last-child td { border-bottom: none; }
.table tr:hover td { background: rgba(232, 236, 255, 0.02); }

.mono { font-family: ui-monospace, Menlo, Monaco, Consolas, monospace; }
.id-num { opacity: 0.4; }
.date-cell { opacity: 0.65; white-space: nowrap; }
.txn-cell { opacity: 0.7; }
.pos { color: #34d399; font-weight: 600; }
.neg { color: #f87171; font-weight: 600; }

.customer-link {
  cursor: pointer;
  color: #93c5fd;
}
.customer-link:hover { text-decoration: underline; }

.empty { text-align: center; opacity: 0.4; padding: 28px; }
.error-msg { color: #f87171; font-size: 13px; margin-bottom: 12px; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(232, 236, 255, 0.08);
}

.page-info { font-size: 12px; opacity: 0.65; }

@media (max-width: 900px) {
  .stats-row { grid-template-columns: 1fr 1fr; }
  .two-col { grid-template-columns: 1fr; }
}

@media (max-width: 500px) {
  .stats-row { grid-template-columns: 1fr; }
}
</style>
