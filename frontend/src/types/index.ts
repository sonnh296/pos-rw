export interface User {
  id: string;
  name: string;
  email: string;
  createdAt: string;
}

export interface CustomerPointRow {
  customerId: string;
  mysqlBalance: number;
  redisPoints: number | null;
  primaryPoints: number;
  source: 'mysql' | 'redis';
  inSync: boolean;
  updatedAt?: string | null;
}

export interface CustomerPointListResponse {
  rows: CustomerPointRow[];
  total: number;
  limit: number;
  offset: number;
  keyword: string;
}

/** Kết quả 1 iteration của Phase 1 accuracy test */
export interface Phase1IterationResult {
  executor: string;
  mode: string;
  iteration: number;
  customerId: string;
  amount: number;
  expectedPoints: number;
  actualPoints: number;
  isAccurate: boolean;
  durationMs: number;
}

/** Summary cho 1 combo executor+mode */
export interface Phase1GroupSummary {
  total: number;
  accurate: number;
  percent: number;
  avgDuration: number;
}
