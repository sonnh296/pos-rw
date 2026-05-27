# POS Boost Demo

Demo luồng **thanh toán → cộng điểm** với Redis lock, idempotency, outbox → MySQL, và so sánh **platform threads vs virtual threads**.

| Module | Mô tả |
|--------|--------|
| `boost/` | API Spring Boot 3 (Java 21) |
| `rewards-batch/` | Drain outbox Redis → MySQL |
| `rewards-common/` | DTO outbox dùng chung |
| `frontend/` | Vue 3 + Vite |
| `loadtest/` | JMeter + script so sánh platform / virtual |

---

## Yêu cầu

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Compose v2)
- (Tùy chọn) [Apache JMeter](https://jmeter.apache.org/) trên máy host — chỉ cần khi chạy load test
- (Tùy chọn) Java 21 + Maven — chỉ khi chạy unit test / dev local không Docker

---

## Chạy bằng Docker (khuyến nghị)

Từ **thư mục gốc repo**:

```bash
docker compose up --build
```

| Dịch vụ | URL |
|---------|-----|
| **Frontend** | http://localhost:5173 |
| **API (boost)** | http://localhost:8080 |
| **Batch** | http://localhost:8081 |
| **Health** | http://localhost:8080/actuator/health |

Dừng stack:

```bash
docker compose down
```

Xóa volume Redis/MySQL (reset dữ liệu — **khuyến nghị** nếu Redis load chậm do dữ liệu load test cũ):

```bash
docker compose down -v
```

---

## Kiểm tra nhanh (smoke test)

Sau khi stack đã `healthy`:

```bash
chmod +x scripts/smoke-test.sh
./scripts/smoke-test.sh
```

Hoặc thủ công:

```bash
curl http://localhost:8080/actuator/health

curl -X POST http://localhost:8080/api/rewards/checkout \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"c1","transactionId":"txn-001","amount":100}'

curl http://localhost:8080/api/rewards/points/c1
```

---

## Unit test (Maven)

Không cần Docker cho test (dùng Testcontainers):

```bash
mvn test
```

Chỉ module boost:

```bash
mvn -pl boost -am test
```

---

## Load test — Platform vs Virtual (JMeter)

Load test bắn HTTP từ **máy host** vào API trong Docker.

### Bước 1 — Bật stack

```bash
docker compose up -d --build
```

Profile `perf` (platform pool 32 thread — dễ thấy chênh lệch):

```bash
docker compose -f docker-compose.yml -f docker-compose.perf.yml up -d --build
```

### Bước 2 — Cài JMeter (một lần)

```bash
jmeter -v
```

macOS: `brew install jmeter`

### Bước 3 — Chạy test

```bash
chmod +x loadtest/script_run/*.sh scripts/*.sh

# Chỉ JMeter (stack đã chạy)
cd loadtest/script_run
./run-phase2-http-concurrency.sh localhost 8080 30 "500,1000,2000" 30

# Hoặc full suite: Docker + JMeter + copy CSV cho UI
./run-performance-suite.sh
```

Script gọi:

- `POST /api/rewards/demo/platform/lock`
- `POST /api/rewards/demo/virtual/lock`

### Bước 4 — Xem kết quả

| Nơi | Nội dung |
|-----|----------|
| `loadtest/results_csv/phase2_http_report.md` | Báo cáo markdown |
| `loadtest/results_csv/phase2_http_concurrency.csv` | CSV chi tiết |
| `loadtest/result_jtl/*.jtl` | Raw JMeter |
| http://localhost:5173/custom-tests | Biểu đồ Phase 2 (cần `frontend/public/results_csv/phase2_results.csv` — suite tự tạo) |

Chi tiết thêm: [`loadtest/PERFORMANCE.md`](loadtest/PERFORMANCE.md)

---

## API chính

**Production checkout**

```http
POST /api/rewards/checkout
Content-Type: application/json

{"customerId":"c1","transactionId":"txn-unique","amount":100}
```

**Demo so sánh thread** (`app.demo.enabled=true`)

```http
POST /api/rewards/demo/platform/lock
POST /api/rewards/demo/virtual/lock
```

**Vận hành**

- `GET /api/rewards/points/{customerId}`
- `POST /api/rewards/points/clear` — xóa dữ liệu test
- Postman: `boost/postman/boost-rewards.postman_collection.json`

---

## Cấu hình

File chính: `boost/src/main/resources/application.yml`

```yaml
spring.threads.virtual.enabled: true
app.demo.enabled: true
app.executors.platform.size: 200   # giảm còn 32 khi profile perf
```

Profile `mysql-only`: bỏ Redis, ghi thẳng MySQL.

---

## CI

GitHub Actions: `.github/workflows/ci.yml` — `mvn test` + `npm run build`.
