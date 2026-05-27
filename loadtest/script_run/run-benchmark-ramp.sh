#!/usr/bin/env bash
# Ramp in-process benchmark (PLATFORM vs VIRTUAL) và ghi CSV cho UI Phase 2.
# Usage: ./loadtest/script_run/run-benchmark-ramp.sh [host] [port] [levels] [durationSec] [pauseSec]
# Example: ./loadtest/script_run/run-benchmark-ramp.sh localhost 8080 "500,1000,1500,2000,3000,4000,5000" 10 2

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOST="${1:-localhost}"
PORT="${2:-8080}"
LEVELS="${3:-500,1000,1500,2000,3000,4000,5000}"
DURATION="${4:-10}"
PAUSE="${5:-2}"
WARMUP="${WARMUP:-true}"

OUT_CSV="${ROOT}/frontend/public/results_csv/phase2_results.csv"
mkdir -p "$(dirname "$OUT_CSV")"

URL="http://${HOST}:${PORT}/api/benchmark/ramp"
echo "POST ${URL}"
echo "  levels=${LEVELS} durationSeconds=${DURATION} warmup=${WARMUP} pauseBetweenLevelsSeconds=${PAUSE}"

JSON="$(curl -sf --max-time 3600 -X POST \
  "${URL}?levels=${LEVELS}&durationSeconds=${DURATION}&warmup=${WARMUP}&pauseBetweenLevelsSeconds=${PAUSE}&executor=BOTH")"

printf '%s' "$JSON" | python3 "${ROOT}/loadtest/script_run/ramp_json_to_csv.py" "$OUT_CSV"

echo "Done. Refresh Phase 2 UI or open ${OUT_CSV}"
