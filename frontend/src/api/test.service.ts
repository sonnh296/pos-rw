import { apiFetch } from './client';

export const testService = {
  async clear() {
    return apiFetch<void>('/api/test-results/csv', { method: 'DELETE' });
  },

  async getPhase1Csv(): Promise<{ ok: boolean; data?: string; error?: string }> {
    try {
      const res = await fetch('/api/test-results/csv/phase1');
      if (!res.ok) return { ok: false, error: 'Failed to fetch CSV' };
      const text = await res.text();
      return { ok: true, data: text };
    } catch (e: any) {
      return { ok: false, error: e.message };
    }
  },

  async getPhase2Csv(): Promise<{ ok: boolean; data?: string; error?: string }> {
    try {
      const res = await fetch('/api/test-results/csv/phase2');
      if (!res.ok) return { ok: false, error: 'Failed to fetch CSV' };
      const text = await res.text();
      return { ok: true, data: text };
    } catch (e: any) {
      return { ok: false, error: e.message };
    }
  }
};
