import { apiFetch, jsonBody } from './client';
import type { CustomerPointListResponse, RewardResponse, TransactionRequest } from '@/types';

export const rewardsService = {
  async checkout(transaction: TransactionRequest) {
    return apiFetch<RewardResponse>('/api/rewards/checkout', {
      method: 'POST',
      ...jsonBody(transaction),
    });
  },

  async getPoints(params: { limit: number; offset: number; keyword?: string }) {
    const q = new URLSearchParams({
      limit: String(params.limit),
      offset: String(params.offset),
    });
    if (params.keyword) q.set('keyword', params.keyword);

    return apiFetch<CustomerPointListResponse>(`/api/rewards/points?${q.toString()}`);
  },

  async clearAllPoints() {
    return apiFetch<void>('/api/rewards/points/clear', {
      method: 'POST',
    });
  },
};
