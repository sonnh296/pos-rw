#!/usr/bin/env bash
# Chaos: tạm dừng Redis trong N giây để kiểm chứng circuit breaker
# (LockingRewardService -> fallback MysqlLockingRewardService).
#
# Dùng song song với JMeter:
#   Terminal 1: jmeter chạy Phase 7 Chaos (duration ~180s)
#   Terminal 2: ./chaos/redis-down.sh 30 60     # pause 30s, bắt đầu sau 60s
#
# Pass criteria:
# - error rate < 5% (fallback hoạt động).
# - response body có suffix "_REDIS_LOCK_REJECTED_FALLBACK_MYSQL".
# - sau khi Redis up, consistency: Σ Redis == Σ MySQL (gọi /api/rewards/consistency/global).

set -euo pipefail

DURATION="${1:-30}"      # số giây pause Redis
DELAY="${2:-10}"         # số giây chờ trước khi bắt đầu pause
CONTAINER="${REDIS_CONTAINER:-boost-redis-1}"

echo "[chaos] Redis container: $CONTAINER"
echo "[chaos] Chờ ${DELAY}s rồi pause Redis trong ${DURATION}s..."
sleep "$DELAY"

echo "[chaos] >>> docker pause $CONTAINER (t=0)"
docker pause "$CONTAINER" >/dev/null

T0=$(date +%s)
sleep "$DURATION"

echo "[chaos] <<< docker unpause $CONTAINER (t=$(($(date +%s) - T0))s)"
docker unpause "$CONTAINER" >/dev/null

echo "[chaos] DONE. Kiểm tra:"
echo "  curl -s http://localhost:8080/api/rewards/consistency/global | jq"
