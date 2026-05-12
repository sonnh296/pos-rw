import { apiFetch } from './client';

export const testService = {
  async clear() {
    // Note: Browser cannot delete local files. 
    // Please run loadtest/script_run/clear_phaseX.sh manually.
    console.warn('Clear via API is no longer supported. Run clear scripts manually.');
    alert('Clear via API is no longer supported. Please run the clear scripts in loadtest/script_run/ manually.');
  },

  async getPhase1Csv(): Promise<{ ok: boolean; data?: string; error?: string }> {
    try {
      // Fetch directly from public directory
      const res = await fetch('/results_csv/phase1_results.csv');
      if (!res.ok) return { ok: false, error: 'No result found. Run test first.' };
      const text = await res.text();
      return { ok: true, data: text };
    } catch (e: any) {
      return { ok: false, error: e.message };
    }
  },

  async getPhase2Csv(): Promise<{ ok: boolean; data?: string; error?: string }> {
    try {
      // Fetch directly from public directory
      const res = await fetch('/results_csv/phase2_results.csv');
      if (!res.ok) return { ok: false, error: 'No result found. Run test first.' };
      const text = await res.text();
      return { ok: true, data: text };
    } catch (e: any) {
      return { ok: false, error: e.message };
    }
  }
};
