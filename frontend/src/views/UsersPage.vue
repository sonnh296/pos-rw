<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { apiFetch } from "../lib/api";

type CustomerPointRow = {
  customerId: string;
  mysqlBalance: number;
  redisPoints: number | null;
  primaryPoints: number;
  source: "mysql" | "redis";
  inSync: boolean;
  updatedAt?: string | null;
};
type CustomerPointListResponse = {
  rows: CustomerPointRow[];
  total: number;
  limit: number;
  offset: number;
  keyword: string;
};
type RunSummary = {
  hasData: boolean;
  message?: string;
  testCaseKey?: string;
  testCaseLabel?: string;
  expectedResult?: string;
  validationGuide?: string;
  fileName?: string;
  filePath?: string;
  runTag?: string;
  modifiedAt?: string | null;
  samples?: number;
  ok?: number;
  fail?: number;
  errorPct?: number;
  avgMs?: number;
  p95Ms?: number;
  p99Ms?: number;
  maxMs?: number;
  throughputRps?: number;
  startedAt?: string | null;
  endedAt?: string | null;
  hotspotCustomerId?: string;
  expectedPoints?: number;
  actualPoints?: number;
  pointsDiff?: number;
  raceDetected?: boolean;
  dirtyData?: boolean;
  verdict?:
    | "RACE_DETECTED"
    | "CONSISTENT"
    | "DIRTY_DATA"
    | "LOST_UPDATE_DETECTED"
    | "IDEMPOTENT_OK"
    | "IDEMPOTENCY_VIOLATED"
    | "UNDER_APPLIED"
    | "INCONCLUSIVE";
  expectedDuplicates?: number;
  successfulApplies?: number;
  idempotencyCustomerId?: string;
};
type PhaseSummary = {
  phaseKey: string;
  phaseTitle: string;
  rows: RunSummary[];
};
type PhaseSummaryResponse = {
  phases: PhaseSummary[];
};

const pointsRows = ref<CustomerPointRow[]>([]);
const pointsLoading = ref(false);
const pointsError = ref<string | null>(null);
const clearError = ref<string | null>(null);
const clearMessage = ref<string | null>(null);
const pointsSearch = ref("");
const pointsLimit = ref(20);
const pointsOffset = ref(0);
const pointsTotal = ref(0);
const phases = ref<PhaseSummary[]>([]);
const runLoading = ref(false);
const runError = ref<string | null>(null);

const visiblePhases = computed(() => {
  // Remove Phase 4-7 test cases from frontend UI (no data yet / not in scope).
  const hidden = new Set(["p4", "p5", "p6", "p7"]);
  return phases.value.filter((p) => !hidden.has(p.phaseKey));
});

async function loadPoints() {
  pointsLoading.value = true;
  pointsError.value = null;
  const keyword = pointsSearch.value.trim();
  const q = new URLSearchParams({
    limit: String(pointsLimit.value),
    offset: String(pointsOffset.value),
  });
  if (keyword) q.set("keyword", keyword);

  const res = await apiFetch<CustomerPointListResponse>(
    `/api/rewards/points?${q.toString()}`,
  );
  pointsLoading.value = false;
  if (!res.ok) {
    pointsError.value = res.error.message;
    return;
  }
  pointsRows.value = res.data.rows ?? [];
  pointsTotal.value = Number(res.data.total ?? 0);
}

async function loadRunSummaries() {
  runLoading.value = true;
  runError.value = null;
  const res = await apiFetch<PhaseSummaryResponse>("/api/test-runs/phases");
  runLoading.value = false;
  if (!res.ok) {
    runError.value = res.error.message;
    return;
  }
  phases.value = res.data.phases ?? [];
}

async function clearAllData() {
  if (!confirm("Xóa toàn bộ dữ liệu điểm và lịch sử giao dịch để reset test?"))
    return;
  pointsLoading.value = true;
  clearError.value = null;
  clearMessage.value = null;

  const res = await apiFetch<Record<string, unknown>>(
    "/api/rewards/points/clear",
    {
      method: "POST",
    },
  );
  pointsLoading.value = false;

  if (!res.ok) {
    clearError.value = res.error.message;
    return;
  }

  const data = res.data as Record<string, unknown>;
  const flushed = data.redisFlushed === true;
  // clearMessage.value = flushed
  //   ? "Đã xóa MySQL + FLUSHDB Redis. Hệ thống đã sạch."
  //   : "Đã xóa MySQL + Redis keys (FLUSHDB thất bại, có thể còn key tồn dư).";
  pointsOffset.value = 0;
  await loadPoints();
  await loadRunSummaries();
}

async function searchPoints() {
  pointsOffset.value = 0;
  await loadPoints();
}

async function prevPoints() {
  if (pointsOffset.value <= 0) return;
  pointsOffset.value = Math.max(0, pointsOffset.value - pointsLimit.value);
  await loadPoints();
}

async function nextPoints() {
  const next = pointsOffset.value + pointsLimit.value;
  if (next >= pointsTotal.value) return;
  pointsOffset.value = next;
  await loadPoints();
}

function fmtDate(s: string) {
  try {
    return new Date(s).toLocaleString("vi-VN");
  } catch {
    return s;
  }
}

function fmtPoints(v: number | null | undefined) {
  if (v == null) return "—";
  return Number(v).toLocaleString("vi-VN");
}

const pointsPageText = computed(() => {
  if (pointsTotal.value === 0 || pointsRows.value.length === 0) return "0/0";
  const from = pointsOffset.value + 1;
  const to = pointsOffset.value + pointsRows.value.length;
  return `${from}-${to}/${pointsTotal.value}`;
});

function barWidth(value: number | null | undefined, maxValue: number) {
  const v = Number(value ?? 0);
  return `${Math.max(0, Math.min(100, (v / maxValue) * 100))}%`;
}

function verdictClass(row: RunSummary) {
  const v = row.verdict;
  if (v === "DIRTY_DATA") return "race-box--warn";
  if (
    v === "RACE_DETECTED" ||
    v === "LOST_UPDATE_DETECTED" ||
    v === "IDEMPOTENCY_VIOLATED" ||
    v === "UNDER_APPLIED"
  )
    return "race-box--bad";
  if (v === "INCONCLUSIVE") return "race-box--warn";
  return "race-box--ok";
}

function verdictText(row: RunSummary) {
  switch (row.verdict) {
    case "DIRTY_DATA":
      return "ℹ Dirty data (clear before re-run)";
    case "RACE_DETECTED":
      return "⚠ Race detected";
    case "LOST_UPDATE_DETECTED":
      return "⚠ Lost update detected (lock bị phá vỡ)";
    case "IDEMPOTENT_OK":
      return "✅ Idempotent (chỉ apply 1 lần)";
    case "IDEMPOTENCY_VIOLATED":
      return "⚠ Idempotency violated (cùng txnId bị cộng nhiều lần)";
    case "UNDER_APPLIED":
      return "⚠ Under applied (SUCCESS nhưng actual < expected)";
    case "INCONCLUSIVE":
      return "ℹ Inconclusive (Redis không khả dụng hoặc thiếu dữ liệu)";
    default:
      return "✅ Consistent";
  }
}

function phaseMaxLatency(rows: RunSummary[]) {
  const values = rows
    .filter((r) => r.hasData)
    .flatMap((r) => [Number(r.avgMs ?? 0), Number(r.p95Ms ?? 0)]);
  const max = Math.max(...values, 0);
  return max <= 0 ? 1 : max;
}

function phaseMaxThroughput(rows: RunSummary[]) {
  const values = rows
    .filter((r) => r.hasData)
    .map((r) => Number(r.throughputRps ?? 0));
  const max = Math.max(...values, 0);
  return max <= 0 ? 1 : max;
}

onMounted(async () => {
  await loadPoints();
  await loadRunSummaries();
});
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="table-header">
        <div class="card__title" style="margin-bottom: 0">
          So sánh theo phase (.jtl)
        </div>
        <div class="header-actions">
          <button class="btn" :disabled="runLoading" @click="loadRunSummaries">
            ↻ Refresh summary
          </button>
        </div>
      </div>

      <div v-if="runError" class="error-msg">{{ runError }}</div>
      <div v-if="visiblePhases.length === 0" class="empty">
        Chưa có dữ liệu test case để vẽ biểu đồ.
      </div>
      <div
        v-for="phase in visiblePhases"
        :key="phase.phaseKey"
        class="compare-panel"
      >
        <div class="compare-title">{{ phase.phaseTitle }}</div>
        <div
          v-for="row in phase.rows"
          :key="row.testCaseKey ?? row.fileName"
          class="phase-row"
        >
          <div class="phase-main">
            <div class="compare-name mono">{{ row.testCaseLabel }}</div>
            <div
              v-if="['p1', 'p2', 'p4'].includes(phase.phaseKey) && row.hasData"
              class="race-box"
              :class="verdictClass(row)"
            >
              <div class="race-verdict">{{ verdictText(row) }}</div>
              <div class="race-line">
                Expected: <strong>{{ fmtPoints(row.expectedPoints) }}</strong>
              </div>
              <div class="race-line">
                Actual: <strong>{{ fmtPoints(row.actualPoints) }}</strong>
              </div>
              <div class="race-line">
                Missing: <strong>{{ fmtPoints(row.pointsDiff) }}</strong>
              </div>
              <div
                v-if="phase.phaseKey === 'p4' && row.expectedDuplicates != null"
                class="race-line"
              >
                Duplicate responses:
                <strong>{{ row.expectedDuplicates }}</strong>
                / Success: <strong>{{ row.successfulApplies }}</strong>
              </div>
              <!-- <div
                v-if="phase.phaseKey === 'p2' && row.verdict === 'CONSISTENT'"
                class="race-hint"
              >
                Lock theo customerId + guard theo transactionId đều hoạt động:
                không mất điểm, không cộng trùng.
              </div> -->
            </div>
          </div>
          <div class="phase-metrics">
            <div v-if="row.hasData" class="compare-metric">
              <span class="compare-label">Avg</span>
              <div class="compare-bar-wrap">
                <div
                  class="compare-bar compare-bar--avg"
                  :style="{
                    width: barWidth(row.avgMs, phaseMaxLatency(phase.rows)),
                  }"
                ></div>
              </div>
              <span class="compare-value">{{ row.avgMs }} ms</span>
            </div>
            <div v-if="row.hasData" class="compare-metric">
              <span class="compare-label">P95</span>
              <div class="compare-bar-wrap">
                <div
                  class="compare-bar compare-bar--p95"
                  :style="{
                    width: barWidth(row.p95Ms, phaseMaxLatency(phase.rows)),
                  }"
                ></div>
              </div>
              <span class="compare-value">{{ row.p95Ms }} ms</span>
            </div>
            <div v-if="row.hasData" class="compare-metric">
              <span class="compare-label">Thr</span>
              <div class="compare-bar-wrap">
                <div
                  class="compare-bar compare-bar--thr"
                  :style="{
                    width: barWidth(
                      row.throughputRps,
                      phaseMaxThroughput(phase.rows),
                    ),
                  }"
                ></div>
              </div>
              <span class="compare-value">{{ row.throughputRps }} rps</span>
            </div>
            <div v-if="row.hasData" class="phase-sub">
              Error: {{ row.errorPct }}% · Samples: {{ row.samples }}
            </div>
            <div v-else class="phase-missing">
              Chưa có dữ liệu cho test case này
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="table-header">
        <div class="card__title" style="margin-bottom: 0">
          Điểm khách hàng
          <span class="badge">{{ pointsTotal }}</span>
        </div>
        <div class="header-actions">
          <input
            v-model="pointsSearch"
            class="input input--search"
            placeholder="Tìm theo customerId…"
            @keyup.enter="searchPoints"
          />
          <button class="btn" :disabled="pointsLoading" @click="searchPoints">
            Tìm
          </button>
          <button class="btn" :disabled="pointsLoading" @click="loadPoints">
            ↻ Refresh
          </button>
          <button
            class="btn btn--danger"
            :disabled="pointsLoading"
            @click="clearAllData"
          >
            🗑 Clear data
          </button>
        </div>
      </div>

      <div v-if="pointsError" class="error-msg">{{ pointsError }}</div>
      <div v-if="clearError" class="error-msg">{{ clearError }}</div>
      <div v-if="clearMessage" class="ok-msg">{{ clearMessage }}</div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>Customer ID</th>
              <th>Primary</th>
              <th>MySQL</th>
              <th>Redis</th>
              <th>In Sync</th>
              <th>Source</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="pointsRows.length === 0">
              <td colspan="7" class="empty">Không có dữ liệu điểm</td>
            </tr>
            <tr v-for="r in pointsRows" :key="r.customerId">
              <td class="mono">{{ r.customerId }}</td>
              <td>
                <strong>{{ fmtPoints(r.primaryPoints) }}</strong>
              </td>
              <td>{{ fmtPoints(r.mysqlBalance) }}</td>
              <td>{{ fmtPoints(r.redisPoints) }}</td>
              <td :class="r.inSync ? 'ok' : 'warn'">
                {{ r.inSync ? "Yes" : "No" }}
              </td>
              <td class="mono">{{ r.source }}</td>
              <td class="date-cell">
                {{ r.updatedAt ? fmtDate(r.updatedAt) : "—" }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <div class="mono">Trang: {{ pointsPageText }}</div>
        <div class="pager-actions">
          <button
            class="btn"
            :disabled="pointsLoading || pointsOffset <= 0"
            @click="prevPoints"
          >
            ← Prev
          </button>
          <button
            class="btn"
            :disabled="
              pointsLoading || pointsOffset + pointsLimit >= pointsTotal
            "
            @click="nextPoints"
          >
            Next →
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
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

.input--search {
  width: 220px;
}

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

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.btn--danger {
  border-color: rgba(244, 114, 182, 0.4);
  background: rgba(244, 114, 182, 0.07);
}
.btn--danger:not(:disabled):hover {
  background: rgba(244, 114, 182, 0.14);
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(232, 236, 255, 0.08);
  border: 1px solid rgba(232, 236, 255, 0.12);
}

.table-wrap {
  overflow-x: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.table th {
  text-align: left;
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.5;
  border-bottom: 1px solid rgba(232, 236, 255, 0.1);
  white-space: nowrap;
}

.table td {
  padding: 9px 12px;
  border-bottom: 1px solid rgba(232, 236, 255, 0.05);
  vertical-align: middle;
}

.table tr:last-child td {
  border-bottom: none;
}
.table tr:hover td {
  background: rgba(232, 236, 255, 0.02);
}

.mono {
  font-family: ui-monospace, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
.date-cell {
  font-size: 12px;
  opacity: 0.7;
  white-space: nowrap;
}
.pager {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.pager-actions {
  display: flex;
  gap: 8px;
}
.warn {
  color: #fbbf24;
  font-weight: 600;
}
.ok {
  color: #34d399;
  font-weight: 600;
}
.run-card {
  background: rgba(232, 236, 255, 0.04);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 10px;
  padding: 10px 12px;
}
.run-label {
  font-size: 11px;
  opacity: 0.6;
  margin-bottom: 4px;
}
.run-value {
  font-size: 14px;
  font-weight: 700;
}
.compare-panel {
  margin-top: 14px;
  border-top: 1px solid rgba(232, 236, 255, 0.08);
  padding-top: 12px;
}
.compare-title {
  font-size: 12px;
  font-weight: 700;
  opacity: 0.75;
  margin-bottom: 8px;
}
.phase-row {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 10px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid rgba(232, 236, 255, 0.06);
}
.phase-row:last-child {
  border-bottom: none;
}
.phase-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.phase-sub {
  font-size: 12px;
  opacity: 0.72;
}
.phase-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(232, 236, 255, 0.14);
  background: rgba(232, 236, 255, 0.06);
  font-size: 11px;
  font-weight: 600;
}
.phase-metrics {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.phase-missing {
  font-size: 12px;
  color: #fbbf24;
}
.race-box {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid rgba(232, 236, 255, 0.1);
  background: rgba(232, 236, 255, 0.03);
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.race-box--ok {
  border-color: rgba(52, 211, 153, 0.4);
  background: rgba(52, 211, 153, 0.08);
}
.race-box--bad {
  border-color: rgba(244, 114, 182, 0.45);
  background: rgba(244, 114, 182, 0.1);
}
.race-box--warn {
  border-color: rgba(251, 191, 36, 0.5);
  background: rgba(251, 191, 36, 0.1);
}
.race-hint {
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.85;
  font-style: italic;
}
.race-verdict {
  font-weight: 700;
  margin-bottom: 2px;
}
.race-line {
  opacity: 0.85;
}
.compare-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.compare-metric {
  display: grid;
  grid-template-columns: 26px 1fr 84px;
  align-items: center;
  gap: 8px;
}
.compare-label {
  font-size: 11px;
  opacity: 0.65;
}
.compare-bar-wrap {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: rgba(232, 236, 255, 0.08);
  overflow: hidden;
}
.compare-bar {
  height: 100%;
  border-radius: 999px;
}
.compare-bar--avg {
  background: linear-gradient(90deg, #60a5fa, #3b82f6);
}
.compare-bar--p95 {
  background: linear-gradient(90deg, #f59e0b, #d97706);
}
.compare-bar--thr {
  background: linear-gradient(90deg, #34d399, #10b981);
}
.compare-value {
  text-align: right;
  font-size: 12px;
  font-weight: 700;
}

.empty {
  text-align: center;
  opacity: 0.4;
  padding: 24px;
}

.error-msg {
  margin-top: 10px;
  color: #f87171;
  font-size: 13px;
}
.ok-msg {
  margin-top: 10px;
  color: #34d399;
  font-size: 13px;
}

@media (max-width: 768px) {
  .input--search {
    width: 100%;
  }
  .phase-row {
    grid-template-columns: 1fr;
  }
  .compare-metric {
    grid-template-columns: 26px 1fr 70px;
  }
}
</style>
