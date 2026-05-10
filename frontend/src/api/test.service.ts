import { apiFetch, jsonBody } from './client';
import type { Phase1IterationResult, Phase1GroupSummary } from '@/types';

/**
 * Phase 1 Accuracy Test — chạy hoàn toàn từ Frontend.
 *
 * Flow: Frontend gửi HTTP request → Backend API → @Async(executor) → Service → Redis/MySQL
 * Frontend tự verify expected vs actual points.
 *
 * Không có backend test logic nào cả — backend chỉ là hệ thống bị test (SUT).
 */

const EXECUTORS = ['SINGLE', 'PLATFORM', 'VIRTUAL'] as const;
const MODES = ['LOCK', 'NO_LOCK'] as const;
const CONCURRENT_REQUESTS = 5;

type Executor = typeof EXECUTORS[number];
type Mode = typeof MODES[number];

export interface Phase1Progress {
  running: boolean;
  current: number;
  total: number;
  currentLabel: string;
}

export interface Phase1TestState {
  results: Phase1IterationResult[];
  summary: Record<string, Phase1GroupSummary>;
}

/** Build API path: PLATFORM + NO_LOCK → /api/rewards/platform/no-lock */
function buildApiPath(executor: Executor, mode: Mode): string {
  const execLower = executor.toLowerCase();
  const modeLower = mode.toLowerCase().replace('_', '-');
  return `/api/rewards/${execLower}/${modeLower}`;
}

/** Gửi N request đồng thời cho cùng 1 customerId, return duration */
async function sendConcurrentRequests(
  apiPath: string,
  customerId: string,
  amount: number,
  count: number
): Promise<{ durationMs: number; allOk: boolean }> {
  const start = performance.now();
  const payload = { customerId, amount };

  const promises = Array.from({ length: count }, () =>
    fetch(apiPath, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
  );

  const results = await Promise.allSettled(promises);
  const allOk = results.every(
    r => r.status === 'fulfilled' && r.value.ok
  );
  const durationMs = Math.round(performance.now() - start);

  return { durationMs, allOk };
}

/** Query actual points cho 1 customerId */
async function getActualPoints(customerId: string): Promise<number> {
  const res = await apiFetch<{ primaryPoints: number }>(
    `/api/rewards/points/${customerId}`
  );
  if (res.ok) return res.data.primaryPoints ?? 0;
  return 0;
}

/** Tính summary từ raw results */
function computeSummary(
  results: Phase1IterationResult[]
): Record<string, Phase1GroupSummary> {
  const summary: Record<string, Phase1GroupSummary> = {};

  for (const exec of EXECUTORS) {
    for (const mode of MODES) {
      const key = `${exec}_${mode}`;
      const filtered = results.filter(
        r => r.executor === exec && r.mode === mode
      );
      const accurate = filtered.filter(r => r.isAccurate).length;
      const total = filtered.length;

      summary[key] = {
        total,
        accurate,
        percent: total > 0 ? (accurate / total) * 100 : 0,
        avgDuration: total > 0
          ? Math.round(filtered.reduce((s, r) => s + r.durationMs, 0) / total)
          : 0,
      };
    }
  }

  return summary;
}

/**
 * Chạy Phase 1 accuracy test.
 * Gọi API trực tiếp từ browser — đi qua full HTTP stack.
 *
 * @param iterations Số lần lặp cho mỗi combo executor+mode
 * @param onProgress Callback cập nhật tiến trình
 * @param shouldStop Hàm kiểm tra dừng sớm
 * @returns Kết quả test
 */
export async function runPhase1Test(
  iterations: number,
  onProgress: (p: Phase1Progress) => void,
  shouldStop: () => boolean
): Promise<Phase1TestState> {
  const results: Phase1IterationResult[] = [];
  const totalIterations = EXECUTORS.length * MODES.length * iterations;
  let current = 0;

  for (const executor of EXECUTORS) {
    for (const mode of MODES) {
      const apiPath = buildApiPath(executor, mode);

      for (let i = 1; i <= iterations; i++) {
        if (shouldStop()) {
          return { results, summary: computeSummary(results) };
        }

        current++;
        onProgress({
          running: true,
          current,
          total: totalIterations,
          currentLabel: `${executor} / ${mode === 'LOCK' ? 'Lock' : 'No Lock'} — Vòng ${i}`,
        });

        const customerId = `fe-${executor.toLowerCase()}-${mode.toLowerCase()}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
        const amount = Math.round((Math.random() * 90 + 10) * 100) / 100;
        const expectedPoints = Math.round(amount * 10) * CONCURRENT_REQUESTS;

        // Gửi 5 request đồng thời đến API endpoint
        const { durationMs } = await sendConcurrentRequests(
          apiPath, customerId, amount, CONCURRENT_REQUESTS
        );

        // Chờ async processing hoàn tất
        await new Promise(r => setTimeout(r, 300));

        // Query actual points
        const actualPoints = await getActualPoints(customerId);

        results.push({
          executor,
          mode,
          iteration: i,
          customerId,
          amount,
          expectedPoints,
          actualPoints,
          isAccurate: expectedPoints === actualPoints,
          durationMs,
        });
      }
    }
  }

  return { results, summary: computeSummary(results) };
}

/**
 * Clear reward data (để reset trước khi test mới).
 */
export async function clearRewardData() {
  return apiFetch<void>('/api/rewards/points/clear', { method: 'POST' });
}
