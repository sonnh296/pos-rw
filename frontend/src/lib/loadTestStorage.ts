import type { SummaryStats } from './stats'

const STORAGE_KEY = 'pos.loadTestDemo.history'
const MAX_RUNS = 80

export type SavedLoadRun = {
  id: string
  at: number
  target: string
  totalRequests: number
  concurrency: number
  timeoutMs: number
  stats: SummaryStats
  wallMs: number
  rps: number
  sampleErrors: string[]
}

export function loadSavedRuns(): SavedLoadRun[] {
  if (typeof localStorage === 'undefined') return []
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? (parsed as SavedLoadRun[]) : []
  } catch {
    return []
  }
}

export function persistSavedRuns(runs: SavedLoadRun[]) {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(STORAGE_KEY, JSON.stringify(runs.slice(0, MAX_RUNS)))
}

export function clearSavedRuns() {
  if (typeof localStorage === 'undefined') return
  localStorage.removeItem(STORAGE_KEY)
}
