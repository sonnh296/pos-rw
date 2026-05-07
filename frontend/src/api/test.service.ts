import { apiFetch } from './client';
import type { TestStatus, Phase1Result, Phase2Result } from '@/types';

export const testService = {
  async getStatus() {
    return apiFetch<TestStatus>('/api/custom-tests/status');
  },

  async stop() {
    return apiFetch<void>('/api/custom-tests/stop', { method: 'POST' });
  },

  async clear() {
    return apiFetch<void>('/api/custom-tests/clear', { method: 'POST' });
  },

  async runPhase1(iterations = 5) {
    return apiFetch<void>(`/api/custom-tests/run-phase1?iterations=${iterations}`, { method: 'POST' });
  },

  async runPhase2(iterations = 5, totalRequests = 5000) {
    return apiFetch<void>(`/api/custom-tests/run-phase2?iterations=${iterations}&totalRequestsPerIter=${totalRequests}`, { method: 'POST' });
  },

  async getPhase1Results() {
    return apiFetch<Phase1Result>('/api/custom-tests/results/phase1');
  },

  async getPhase2Results() {
    return apiFetch<Phase2Result>('/api/custom-tests/results/phase2');
  }
};
