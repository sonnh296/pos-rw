# Load test — Platform vs Virtual threads

Dùng **Apache JMeter** bắn HTTP vào demo endpoints (không dùng `/api/benchmark`).

## Quick start

```bash
# 1. Stack Docker
docker compose up -d --build

# 2. JMeter trên host
cd loadtest/script_run
./run-phase2-http-concurrency.sh localhost 8080 30 "500,1000,2000" 30
```

## Full suite (Docker + JMeter + CSV cho UI)

```bash
./loadtest/script_run/run-performance-suite.sh
```

Biến môi trường:

- `SKIP_DOCKER=true` — stack đã chạy sẵn
- `USE_PERF=false` — không dùng `docker-compose.perf.yml`

## Kết quả

- `loadtest/results_csv/phase2_http_report.md`
- `loadtest/results_csv/phase2_http_concurrency.csv`
- `frontend/public/results_csv/phase2_results.csv` (sau `run-performance-suite.sh`)

## Phase 1 (lock vs no-lock)

```bash
cd loadtest/script_run
./run-phase1.sh
```

Kết quả: `frontend/public/results_csv/phase1_results.csv`

## Yêu cầu

- Docker Compose
- JMeter (`jmeter -v`)
- Python 3 (parse JTL trong script)
