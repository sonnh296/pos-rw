#!/usr/bin/env bash
# All-in-one: chạy JMeter P7 chaos + inject Redis down + verify fallback.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JMETER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

JMETER_BIN="${JMETER_HOME:+$JMETER_HOME/bin/}jmeter"
HOST="${HOST:-localhost}"
PORT="${PORT:-8080}"
CHAOS_DURATION="${CHAOS_DURATION:-30}"
CHAOS_DELAY="${CHAOS_DELAY:-30}"

echo "[run] Enable P7 trong advanced-scenarios.jmx trước khi chạy (hoặc dùng -Jenable=p7)."
echo "[run] Bắt đầu Phase 7 + song song chaos..."

rm -f "$JMETER_DIR/results/p7-chaos.jtl"

# Chaos chạy song song
"$JMETER_DIR/chaos/redis-down.sh" "$CHAOS_DURATION" "$CHAOS_DELAY" &
CHAOS_PID=$!

"$JMETER_BIN" -n \
  -t "$JMETER_DIR/advanced-scenarios.jmx" \
  -Jhost="$HOST" -Jport="$PORT" \
  -JresultDir="$JMETER_DIR/results" \
  -l "$JMETER_DIR/results/p7-chaos.jtl" \
  -e -o "$JMETER_DIR/reports/p7-chaos-$(date +%Y%m%d-%H%M%S)"

wait "$CHAOS_PID" || true

echo "[run] Consistency check:"
curl -s "http://$HOST:$PORT/api/rewards/consistency/global" | python3 -m json.tool || true
