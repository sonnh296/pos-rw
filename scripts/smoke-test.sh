#!/usr/bin/env bash
# Kiểm tra nhanh sau khi docker compose up
set -euo pipefail
HOST="${1:-localhost}"
PORT="${2:-8080}"
BASE="http://${HOST}:${PORT}"

echo "Health..."
curl -sf "${BASE}/actuator/health" | python3 -m json.tool

TXN="smoke-$(date +%s)"
echo ""
echo "Checkout (${TXN})..."
curl -sf -X POST "${BASE}/api/rewards/checkout" \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"smoke-customer\",\"transactionId\":\"${TXN}\",\"amount\":100}" \
  | python3 -m json.tool

echo ""
echo "Points..."
curl -sf "${BASE}/api/rewards/points/smoke-customer" | python3 -m json.tool

echo ""
echo "OK"
