#!/usr/bin/env bash
# HTTP load: platform vs virtual demo endpoints at rising concurrency (shows virtual thread advantage).
set -euo pipefail
cd "$(dirname "$0")/.."

HOST="${1:-localhost}"
PORT="${2:-8080}"
RAMP_SEC="${3:-30}"
CONCURRENCY_LEVELS="${4:-500,1000,2000}"
LOOPS="${5:-30}"

RESULTS_DIR="results_csv"
mkdir -p "$RESULTS_DIR" result_jtl
CSV="${RESULTS_DIR}/phase2_http_concurrency.csv"
REPORT="${RESULTS_DIR}/phase2_http_report.md"

echo "test,executor,concurrency,duration_ms,throughput_rps,p95_ms,p99_ms,errors" > "$CSV"
echo "# Phase 2 HTTP — Platform vs Virtual Thread" > "$REPORT"
echo "" >> "$REPORT"
echo "- Host: \`${HOST}:${PORT}\`" >> "$REPORT"
echo "- Ramp: ${RAMP_SEC}s per level" >> "$REPORT"
echo "" >> "$REPORT"

cat << 'PY' > parse_jtl_http.py
import csv, json, sys

def parse_jtl(path):
    elapsed, errors = [], 0
    min_ts, max_ts = float("inf"), 0
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            if row.get("success", "true") != "true":
                errors += 1
            e = int(row["elapsed"])
            elapsed.append(e)
            ts = int(row["timeStamp"])
            min_ts = min(min_ts, ts)
            max_ts = max(max_ts, ts + e)
    if not elapsed:
        return {"throughput": 0, "p95": 0, "p99": 0, "errors": errors, "duration_ms": 0}
    elapsed.sort()
    p95 = elapsed[int(0.95 * len(elapsed)) - 1]
    p99 = elapsed[int(0.99 * len(elapsed)) - 1]
    dur_ms = max(1, int(max_ts - min_ts))
    tput = len(elapsed) / (dur_ms / 1000.0)
    return {"throughput": round(tput, 2), "p95": p95, "p99": p99, "errors": errors, "duration_ms": dur_ms}

if __name__ == "__main__":
    print(json.dumps(parse_jtl(sys.argv[1])))
PY

wait_health() {
  for _ in $(seq 1 40); do
    curl -sf "http://${HOST}:${PORT}/actuator/health" >/dev/null && return 0
    sleep 3
  done
  echo "API not ready" >&2
  exit 1
}

run_jmeter() {
  local executor="$1"
  local threads="$2"
  local path="/api/rewards/demo/${executor}/lock"
  local jtl="result_jtl/http_${executor}_${threads}.jtl"
  rm -f "$jtl"
  echo "  HTTP ${executor} @ ${threads} concurrent..." >&2
  jmeter -n -t jmeter_jmx/http_concurrency.jmx \
    -l "$jtl" \
    -Jhost="$HOST" -Jport="$PORT" \
    -Jthreads="$threads" -JrampSeconds="$RAMP_SEC" -Jloops="$LOOPS" \
    -JtargetPath="$path" >/dev/null 2>&1
  if [ ! -s "$jtl" ]; then
    echo '{"throughput":0,"p95":0,"p99":0,"errors":1,"duration_ms":0}' >&2
    echo '{"throughput":0,"p95":0,"p99":0,"errors":1,"duration_ms":0}'
    return 1
  fi
  python3 parse_jtl_http.py "$jtl"
}

wait_health
curl -sf -X POST "http://${HOST}:${PORT}/api/rewards/points/clear" >/dev/null || true

IFS=',' read -ra LEVELS <<< "$CONCURRENCY_LEVELS"
for threads in "${LEVELS[@]}"; do
  echo "" >> "$REPORT"
  echo "## Concurrency: ${threads}" >> "$REPORT"
  echo "| Executor | Throughput | p95 | p99 | Errors | Duration |" >> "$REPORT"
  echo "|----------|------------|-----|-----|--------|----------|" >> "$REPORT"

  for executor in platform virtual; do
    parsed=$(run_jmeter "$executor" "$threads")
    tput=$(echo "$parsed" | python3 -c "import sys,json; print(json.load(sys.stdin)['throughput'])")
    p95=$(echo "$parsed" | python3 -c "import sys,json; print(json.load(sys.stdin)['p95'])")
    p99=$(echo "$parsed" | python3 -c "import sys,json; print(json.load(sys.stdin)['p99'])")
    err=$(echo "$parsed" | python3 -c "import sys,json; print(json.load(sys.stdin)['errors'])")
    dur=$(echo "$parsed" | python3 -c "import sys,json; print(json.load(sys.stdin)['duration_ms'])")
    ex_upper=$(echo "$executor" | tr '[:lower:]' '[:upper:]')
    echo "http,${ex_upper},${threads},${dur},${tput},${p95},${p99},${err}" >> "$CSV"
    echo "| ${ex_upper} | ${tput} | ${p95}ms | ${p99}ms | ${err} | ${dur}ms |" >> "$REPORT"
    sleep 5
  done
done

python3 << PY >> "$REPORT"
import csv
from pathlib import Path
rows = list(csv.DictReader(open("${CSV}")))
print("\n## Virtual vs Platform (throughput uplift)\n")
by_c = {}
for r in rows:
    by_c.setdefault(r["concurrency"], {})[r["executor"]] = float(r["throughput_rps"])
for c, m in sorted(by_c.items(), key=lambda x: int(x[0])):
    p, v = m.get("PLATFORM", 0), m.get("VIRTUAL", 0)
    if p > 0:
        uplift = (v - p) / p * 100
        print(f"- **{c} concurrent**: Virtual **{uplift:+.1f}%** throughput ({v:.0f} vs {p:.0f} rps)")
PY

rm -f parse_jtl_http.py
echo "Wrote $CSV and $REPORT"
cat "$REPORT"
