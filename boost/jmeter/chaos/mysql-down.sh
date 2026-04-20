#!/usr/bin/env bash
# Chaos: tạm dừng MySQL để kiểm chứng:
# - Redis vẫn phục vụ được hot-path (process reward, cộng điểm tạm).
# - Outbox tích tụ trong Redis.
# - Sau khi MySQL up lại, rewards-batch drain toàn bộ outbox -> Σ MySQL == Σ Redis.

set -euo pipefail

DURATION="${1:-30}"
DELAY="${2:-10}"
CONTAINER="${MYSQL_CONTAINER:-boost-mysql-1}"

echo "[chaos] MySQL container: $CONTAINER"
echo "[chaos] Chờ ${DELAY}s rồi pause MySQL trong ${DURATION}s..."
sleep "$DELAY"

echo "[chaos] >>> docker pause $CONTAINER"
docker pause "$CONTAINER" >/dev/null

sleep "$DURATION"

echo "[chaos] <<< docker unpause $CONTAINER"
docker unpause "$CONTAINER" >/dev/null

echo "[chaos] Chờ rewards-batch drain outbox (~15s) rồi check:"
sleep 15
echo "  curl -s http://localhost:8080/api/rewards/consistency/global | jq"
