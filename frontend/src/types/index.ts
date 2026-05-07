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

export interface TestStatus {
  phase1Running: boolean;
  phase1Progress: number;
  phase2Running: boolean;
  phase2Progress: number;
}

export interface Phase1Result {
  summary: Record<string, {
    total: number;
    accurate: number;
    avgDuration: number;
  }>;
}

export interface Phase2Result {
  summary: Record<string, {
    avgThroughput: number;
    avgP95: number;
  }>;
}
