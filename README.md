# POS Boost Demo (Backend + Frontend)

Repo này là một **project demo** để mô phỏng luồng “thanh toán → cộng điểm” và **so sánh hiệu năng** theo các kịch bản:

- **Platform threads (fixed pool)** vs **Virtual threads**
- **Có/không gọi Redis**, **có/không gọi MySQL**
- **Redis bị ngắt kết nối** (degraded mode / fallback)

Thành phần chính:

- **Backend**: `boost/` — Spring Boot 3 (Java 21), MySQL, Redis, Redisson, Resilience4j CircuitBreaker
- **Batch**: `boost/rewards-batch/` — consumer/outbox demo (được docker-compose dựng kèm)
- **Frontend**: `frontend/` — Vue 3 + Vite (UI demo + load test giả lập)

## Business flow (luồng nghiệp vụ)

### 1) Thanh toán (checkout) → cộng điểm (rewards)

- POS/cashier gửi “transaction” gồm:
  - `customerId`
  - `transactionId` (idempotency key)
  - `amount`
- Hệ thống tính điểm: \(pointsDelta = round(amount \times 10)\)
- Cập nhật điểm theo mode (demo):
  - **Redis mode (mặc định)**:
    - (tuỳ endpoint) lock theo `customerId` bằng **Redisson** hoặc **không lock**
    - cộng điểm tạm trên Redis để trả kết quả nhanh
    - (đường lock) đẩy event vào Redis outbox để batch ghi vĩnh viễn xuống MySQL
    - nếu Redis/Redisson lỗi → **Circuit Breaker** mở mạch và fallback qua MySQL
  - **MySQL-only mode** (`--spring.profiles.active=mysql-only`):
    - không khởi tạo Redis/Redisson
    - ghi ledger và balance trực tiếp MySQL
    - (tuỳ endpoint) có **FOR UPDATE** (locking) hoặc **không FOR UPDATE** (lost update demo)

### 2) Query điểm & so sánh consistency

- `GET /api/rewards/points/{customerId}`: lấy “điểm chính” theo mode hiện tại
- `GET /api/rewards/balance/compare/{customerId}`: so sánh Redis vs MySQL (để thấy lệch khi no-lock / concurrent cao)

## Functional requirements (yêu cầu chức năng)

- **Checkout / rewards**
  - Nhận transaction, tính điểm, trả về tổng điểm hiện tại
  - Hỗ trợ nhiều “đường xử lý” để demo: lock / no-lock, platform / virtual
  - Idempotency theo `transactionId` (tránh cộng điểm 2 lần)
- **Users API (demo)**
  - `GET /api/users`: lấy danh sách user (in-memory)
  - `POST /api/users`: tạo user (in-memory)
- **Benchmark API**
  - Endpoint để tạo workload có kiểm soát: none/sleep/mysql/redis/redis+mysql
  - Cho phép so sánh platform vs virtual dưới cùng điều kiện
- **Frontend demo**
  - UI gọi rewards/users/health
  - UI “Load test (DDoS giả lập)”:
    - chọn target endpoint
    - cấu hình `totalRequests`, `concurrency`, `timeout`
    - hiển thị thống kê latency (min/p50/p95/p99/max/avg) + OK/Fail

## Non-functional requirements (yêu cầu phi chức năng)

- **Performance/throughput**
  - Chứng minh khác biệt khi concurrent cao:
    - platform thread pool bị queue/đợi khi vượt số thread
    - virtual threads scale tốt hơn cho workload dạng IO-wait
- **Resilience**
  - Khi Redis lỗi/timeout:
    - hệ thống có thể degrade/fallback (CircuitBreaker) để tiếp tục phục vụ
    - có thể quan sát “chậm hơn” khi fallback sang MySQL
- **Consistency**
  - Cho thấy rủi ro lost update khi không lock (no-lock path)
  - Có đường lock (Redisson hoặc MySQL row-lock) để đảm bảo nhất quán tốt hơn
- **Observability (demo-level)**
  - Actuator health: `GET /actuator/health`

## Technical notes

### Threading model

- Backend có 2 executor cho async endpoints:
  - `platformExecutor`: fixed thread pool (200)
  - `virtualExecutor`: virtual-thread-per-task
- Rewards API expose 4 endpoint để so sánh:
  - `POST /api/rewards/platform/lock`
  - `POST /api/rewards/platform/no-lock`
  - `POST /api/rewards/virtual/lock`
  - `POST /api/rewards/virtual/no-lock`

### Redis + Redisson + Circuit Breaker (fallback)

- Redis mode dùng Redis để:
  - lock theo customer (Redisson)
  - cập nhật điểm tạm / cache
  - outbox queue (batch drain)
- Khi Redis/Redisson lỗi (connection/timeout), service sẽ ném `RedisUnavailableException`.
- **Resilience4j CircuitBreaker** (2 instance: `redisNoLock`, `redisLocking`) sẽ:
  - ghi nhận lỗi `RedisUnavailableException`
  - khi lỗi vượt ngưỡng → chuyển sang **OPEN** trong một khoảng thời gian
  - trong trạng thái OPEN, request sẽ chạy **fallback** sang MySQL để hệ thống vẫn phục vụ (nhưng thường chậm hơn)

### Benchmark endpoints (đo workload có kiểm soát)

- `GET /api/bench/platform?...`
- `GET /api/bench/virtual?...`
- Params chính:
  - `work=none|sleep|mysql|redis|redis+mysql`
  - `sleepMs=...` (giả lập IO-wait)
  - `fallbackToMysqlOnRedisError=true|false`

## API quick list

- Rewards:
  - `POST /api/rewards/platform/no-lock`
  - `POST /api/rewards/platform/lock`
  - `POST /api/rewards/virtual/no-lock`
  - `POST /api/rewards/virtual/lock`
  - `GET /api/rewards/points/{customerId}`
  - `GET /api/rewards/balance/compare/{customerId}`
- Users:
  - `GET /api/users`
  - `POST /api/users`
- Benchmark:
  - `GET /api/bench/meta`
  - `GET /api/bench/platform`
  - `GET /api/bench/virtual`
- Health:
  - `GET /actuator/health`

## Run locally

### Backend (docker compose)

```bash
cd boost
docker compose up --build
```

Backend chạy ở `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend chạy ở `http://localhost:5173`.

## Demo scenarios (gợi ý test)

- **Virtual vs Platform**:
  - Load test chọn `Bench platform • redis+mysql` và `Bench virtual • redis+mysql` với concurrency cao.
- **No DB / only sleep**:
  - Load test chọn `Bench ... • none` hoặc `Bench ... • sleep`.
- **Redis down**:
  - Stop Redis container, rồi chọn `Bench ... • redis+mysql` + bật “Redis error → fallback MySQL”.
  - Quan sát tăng latency và/hoặc fail rate.

