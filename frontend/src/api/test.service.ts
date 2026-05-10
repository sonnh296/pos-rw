import { apiFetch } from './client';
import type { Phase1IterationResult, Phase1GroupSummary } from '@/types';

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

function getBaseUrl() {
  const raw = import.meta.env.VITE_API_BASE_URL as string | undefined;
  if (!raw) return '';
  return raw.replace(/\/+$/, '');
}

function buildApiPath(executor: Executor, mode: Mode): string {
  const base = getBaseUrl();
  const path = `/api/rewards/${executor.toLowerCase()}/${mode.toLowerCase().replace('_', '-')}`;
  return `${base}${path}`;
}

/**
 * Gửi N request đồng thời. 
 * QUAN TRỌNG: Phải có transactionId duy nhất cho mỗi request để tránh Idempotency check ở Backend.
 */
async function sendConcurrentRequests(
  fullUrl: string,
  customerId: string,
  amount: number,
  count: number
): Promise<{ durationMs: number; allOk: boolean }> {
  const start = performance.now();
  
  const promises = Array.from({ length: count }, (_, i) => {
    const payload = { 
      customerId, 
      amount, 
      transactionId: `txn-${customerId}-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 5)}` 
    };
    
    return fetch(fullUrl, {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(payload),
    });
  });

  const results = await Promise.allSettled(promises);
  const allOk = results.every(r => r.status === 'fulfilled' && r.value.ok);
  const durationMs = Math.round(performance.now() - start);

  return { durationMs, allOk };
}

async function getActualPoints(customerId: string): Promise<number> {
  const res = await apiFetch<{ primaryPoints: number }>(
    `/api/rewards/points/${customerId}`
  );
  return res.ok ? (res.data.primaryPoints ?? 0) : 0;
}

function computeSummary(
  results: Phase1IterationResult[]
): Record<string, Phase1GroupSummary> {
  const summary: Record<string, Phase1GroupSummary> = {};

  for (const exec of EXECUTORS) {
    for (const mode of MODES) {
      const key = `${exec}_${mode}`;
      const filtered = results.filter(r => r.executor === exec && r.mode === mode);
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
      const fullUrl = buildApiPath(executor, mode);

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
        const amount = 10.0; // Dùng số cố định để dễ debug
        const expectedPoints = Math.round(amount * 10) * CONCURRENT_REQUESTS;

        const { durationMs } = await sendConcurrentRequests(
          fullUrl, customerId, amount, CONCURRENT_REQUESTS
        );

        // Chờ 1 giây để backend xử lý xong outbox/batch
        await new Promise(r => setTimeout(r, 1000));

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

export async function clearRewardData() {
  return apiFetch<void>('/api/rewards/points/clear', { method: 'POST' });
}
