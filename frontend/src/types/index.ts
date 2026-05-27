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

export interface TransactionRequest {
  customerId: string;
  transactionId: string;
  amount: number;
}

export interface RewardResponse {
  customerId: string;
  totalPoints: number;
  status: string;
  threadName: string;
  processingTimeMs: number;
  degraded?: boolean;
  fallbackSource?: string;
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

export interface Phase2Point {
  rps: number;
  throughputRps: number;
  p95Ms: number;
  p99Ms: number;
}

export interface Phase2RampSeries {
  rpsLevels: number[];
  PLATFORM: Phase2Point[];
  VIRTUAL: Phase2Point[];
}

export interface Phase2Result {
  summary: Record<string, {
    avgThroughput: number;
    avgP95: number;
    avgP99: number;
  }>;
  byRps?: Phase2RampSeries;
}
