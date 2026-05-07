import { apiFetch, jsonBody } from './client';
import type { User, CustomerPointListResponse } from '@/types';

export const userService = {
  async getAll() {
    return apiFetch<User[]>('/api/users');
  },

  async create(user: Omit<User, 'id' | 'createdAt'>) {
    return apiFetch<User>('/api/users', {
      method: 'POST',
      ...jsonBody(user),
    });
  },

  async update(id: string, user: Partial<Omit<User, 'id' | 'createdAt'>>) {
    return apiFetch<User>(`/api/users/${id}`, {
      method: 'PUT',
      ...jsonBody(user),
    });
  },

  async delete(id: string) {
    return apiFetch<void>(`/api/users/${id}`, {
      method: 'DELETE',
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
  }
};
