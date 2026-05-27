#!/usr/bin/env bash
# Docker stack + JMeter (platform vs virtual). Không dùng /api/benchmark.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/loadtest/script_run"

HOST="${1:-localhost}"
PORT="${2:-8080}"
SKIP_DOCKER="${SKIP_DOCKER:-false}"
USE_PERF="${USE_PERF:-true}"

echo "========== Load test: Platform vs Virtual (JMeter) =========="

if [ "$SKIP_DOCKER" != "true" ]; then
  echo "[1/4] Starting Docker..."
  if [ "$USE_PERF" = "true" ] && [ -f "$ROOT/docker-compose.perf.yml" ]; then
    docker compose -f "$ROOT/docker-compose.yml" -f "$ROOT/docker-compose.perf.yml" up -d --build
  else
    docker compose -f "$ROOT/docker-compose.yml" up -d --build
  fi
  echo "Waiting for API..."
  for _ in $(seq 1 60); do
    if curl -sf "http://${HOST}:${PORT}/actuator/health" >/dev/null 2>&1; then
      echo "API ready."
      break
    fi
    sleep 5
  done
else
  echo "[1/4] SKIP_DOCKER=true — using existing stack"
fi

echo "[2/4] Clear test data..."
curl -sf -X POST "http://${HOST}:${PORT}/api/rewards/points/clear" >/dev/null || true

echo "[3/4] JMeter HTTP concurrency (platform vs virtual)..."
bash ./run-phase2-http-concurrency.sh "$HOST" "$PORT" 30 "500,1000,2000" 30

echo "[4/4] Copy results for frontend charts..."
mkdir -p "$ROOT/frontend/public/results_csv"
python3 << PY
import csv
from pathlib import Path

root = Path("${ROOT}")
src = root / "loadtest" / "results_csv" / "phase2_http_concurrency.csv"
dst = root / "frontend" / "public" / "results_csv" / "phase2_results.csv"
if not src.exists():
    raise SystemExit(f"Missing {src}")

rows = list(csv.DictReader(src.open()))
dst.parent.mkdir(parents=True, exist_ok=True)
with dst.open("w", newline="") as out:
    w = csv.writer(out)
    w.writerow(["executor", "rps", "duration_ms", "throughput_rps", "p95_ms", "p99_ms"])
    for r in rows:
        w.writerow([
            r["executor"],
            r["concurrency"],
            r["duration_ms"],
            r["throughput_rps"],
            r["p95_ms"],
            r["p99_ms"],
        ])
print(f"Frontend CSV: {dst}")
PY

echo ""
echo "Done."
echo "  Report:  loadtest/results_csv/phase2_http_report.md"
echo "  Charts:  http://localhost:5173/custom-tests (sau khi mở frontend)"
echo "  Raw JTL: loadtest/result_jtl/"
