#!/usr/bin/env bash
# Chạy JMeter non-GUI + sinh HTML dashboard + compare baseline.
# Dùng được cả trên máy dev và CI.
#
#   ./scripts/run-ci.sh <jmx> <test-case-key>
#
# Ví dụ:
#   ./scripts/run-ci.sh customer-demo-thread-comparison.jmx p2-c-virtual-lock
#   ./scripts/run-ci.sh advanced-scenarios.jmx p4-a-idempotency
#
# Env vars (optional):
#   JMETER_HOME        - path tới JMeter (nếu ko có `jmeter` trong PATH)
#   HOST, PORT         - mặc định localhost:8080
#   RESULT_DIR         - mặc định <repo>/boost/jmeter/results
#   BASELINE_DIR       - mặc định <repo>/boost/jmeter/baseline
#   P95_THRESHOLD_MS   - mặc định 500
#   ERROR_THRESHOLD_PCT- mặc định 1.0

set -euo pipefail

JMX="${1:?Cần truyền đường dẫn .jmx (ví dụ customer-demo-thread-comparison.jmx)}"
CASE="${2:?Cần truyền test-case key (ví dụ p4-a-idempotency)}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JMETER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RESULT_DIR="${RESULT_DIR:-$JMETER_DIR/results}"
BASELINE_DIR="${BASELINE_DIR:-$JMETER_DIR/baseline}"
REPORT_DIR="${REPORT_DIR:-$JMETER_DIR/reports/$CASE-$(date +%Y%m%d-%H%M%S)}"
HOST="${HOST:-localhost}"
PORT="${PORT:-8080}"
P95_THRESHOLD_MS="${P95_THRESHOLD_MS:-500}"
ERROR_THRESHOLD_PCT="${ERROR_THRESHOLD_PCT:-1.0}"

JMETER_BIN="${JMETER_HOME:+$JMETER_HOME/bin/}jmeter"

mkdir -p "$RESULT_DIR" "$BASELINE_DIR" "$REPORT_DIR"

JMX_PATH="$JMETER_DIR/$JMX"
JTL_OUT="$RESULT_DIR/$CASE.jtl"

echo "[ci] JMX       : $JMX_PATH"
echo "[ci] Case      : $CASE"
echo "[ci] Result    : $JTL_OUT"
echo "[ci] Report    : $REPORT_DIR"
echo "[ci] Target    : http://$HOST:$PORT"

# Xoá JTL cũ của đúng test case đó để ko bị append
rm -f "$JTL_OUT"

"$JMETER_BIN" -n \
  -t "$JMX_PATH" \
  -Jhost="$HOST" -Jport="$PORT" \
  -JresultDir="$RESULT_DIR" \
  -JinputsDir="$JMETER_DIR/inputs" \
  -l "$JTL_OUT" \
  -e -o "$REPORT_DIR"

echo "[ci] HTML dashboard: $REPORT_DIR/index.html"

# ---- Regression gate dựa trên JTL ----
python3 "$SCRIPT_DIR/compare-baseline.py" \
  --current "$JTL_OUT" \
  --baseline "$BASELINE_DIR/$CASE.jtl" \
  --p95-threshold "$P95_THRESHOLD_MS" \
  --error-threshold "$ERROR_THRESHOLD_PCT" \
  || { echo "[ci] ❌ Regression gate FAILED"; exit 1; }

echo "[ci] ✅ PASS"
