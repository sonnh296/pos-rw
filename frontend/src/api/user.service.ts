import { apiFetch, jsonBody } from './client';
import type { User } from '@/types';

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
};
