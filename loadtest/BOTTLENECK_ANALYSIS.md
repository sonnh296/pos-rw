# Điều tra nút thắt: Virtual Thread vs Platform Thread

## 1. Cấu hình hiện tại (sau chỉnh sửa)

| Thành phần | Giá trị |
|------------|---------|
| Platform pool | **200** (`app.executors.platform.size`) |
| Virtual executor | `newVirtualThreadPerTaskExecutor()` (không giới hạn task) |
| Redisson pool (mặc định) | **200** (`connectionPoolSize`) |
| Tomcat | `spring.threads.virtual.enabled=true` |
| Mỗi request SUCCESS | ~5–6 round-trip Redis |

**Quan sát quan trọng:** Platform pool **200** ≈ Redisson pool **200** → khi tải cao, **cả hai executor cùng tranh ~200 connection Redis**. Virtual thread tạo thêm task nhưng không tạo thêm connection → lợi thế virtual **bị chặn ở tầng Redis**.

---

## 2. Luồng xử lý từng bước

```
Client (JMeter)
  → Tomcat (virtual thread)           [H4: lớp 1]
  → CompletableFuture.supplyAsync     [H4: lớp 2 — platform hoặc virtual executor]
  → LockingRewardService
  → LockingRedisRewardService
       1. tryLock (Redisson)          [lockMs]
       2. setIfAbsent idempotency     [idempotencyMs]
       3. atomic expected + map       [pointsUpdateMs]
       4. serialize + outbox deque    [outboxMs]
       5. unlock
  → Redis (single-threaded core)      [H3]
  → Redisson connection pool          [H1]
```

Gọi chẩn đoán:

```bash
curl -s http://localhost:8080/api/benchmark/diagnose | python3 -m json.tool
```

Trường `phaseTimingMillis` cho biết thời gian trung bình từng pha (ms) trên mẫu 30 request.

---

## 3. Vì sao virtual không “vượt trội” mọi lúc?

### Bước 1 — Tải thấp (< ~200 concurrent in-flight)

| Nút | Platform | Virtual |
|-----|----------|---------|
| Pool 200 | Đủ thread | Không cần thêm |
| Redis | Chưa bão hòa | Chưa bão hòa |

→ **Gần như ngang** (đúng với kết quả ramp 50–500 req/s).

### Bước 2 — Tải trung bình (500–1000 concurrent HTTP)

| Nút | Platform | Virtual |
|-----|----------|---------|
| Pool 200 | Bắt đầu queue task | Nhiều task hơn |
| Redis pool 200 | Chờ connection | Chờ connection |

→ Virtual **+28% ~ +55%** (đo được trong suite perf) vì platform queue **trước** khi chờ Redis.

### Bước 3 — Tải cao (2000+ concurrent)

| Nút | Platform | Virtual |
|-----|----------|---------|
| Platform pool | Queue dài | Ít queue hơn |
| **Redis pool + CPU** | **Trần** | **Trần** |

→ Virtual chỉ còn **+12%** — hai bên cùng nghẽn ở **Redis**, không phải thread model.

### Bước 4 — In-process benchmark (1500 req/s offered)

- Đo latency **trong** `processReward` (không gồm queue HTTP).
- Platform pool 200 vs virtual → virtual **+21%** throughput.
- Latency p95 thấp hơn vì ít chờ thread pool; Redis vẫn là phần lớn `totalMs`.

### Bước 5 — Benchmark UUID mỗi request (H5)

- Mỗi `customerId` mới → **không tranh** `reward:lock:{customerId}`.
- Lock vẫn tốn 2+ round-trip nhưng không serialize nghiệp vụ.
- Production (cùng khách checkout liên tục) có thể **tệ hơn** cho platform hơn benchmark.

---

## 4. Bảng giả thuyết & cách xác minh

| ID | Giả thuyết | Xác minh |
|----|------------|----------|
| H1 | Redisson pool 200 là trần chung | Tăng `connectionPoolSize` → 512, chạy lại `run-performance-suite.sh` |
| H2 | Platform pool 200 queue khi >200 in-flight | `diagnose` + thread dump; so sánh platform vs virtual tại 1000 concurrent |
| H3 | Redis CPU/command queue | `redis-cli INFO` → `instantaneous_ops_per_sec` |
| H4 | Double scheduling HTTP + executor | So sánh HTTP JMeter vs `/api/benchmark/run` |
| H5 | Không lock contention trong benchmark | JMeter với `customerId` cố định |

---

## 5. Đề xuất khắc phục (ưu tiên)

### Ngắn hạn (config)

1. **Cân bằng pool:** `connectionPoolSize` ≥ 512 khi benchmark/production high concurrency (profile `perf` đã có 512).
2. **Giữ platform = 200** nếu đó là sizing ops; virtual dùng cho `/checkout` production.
3. Chạy diagnose sau mỗi thay đổi: `GET /api/benchmark/diagnose`.

### Trung hạn (code)

4. **Lua script Redis** — gộp lock + idem + cộng điểm + outbox → **1–2 round-trip** thay vì 5–6.
5. **Bỏ hop executor thừa** trên checkout nếu request đã chạy trên virtual thread Tomcat:

   ```java
   // Cân nhắc: gọi trực tiếp lockingRewardService.processReward(request)
   // thay vì supplyAsync(..., virtualExecutor) khi Thread.currentThread().isVirtual()
   ```

6. Bật **Micrometer** `@Timed` trên từng pha lock/idempotency/points.

### Dài hạn (scale)

7. **Redis Cluster** + sharding theo `customerId`.
8. **Scale ngang** nhiều pod `boost` + LB (cùng Redis).
9. Tách đọc điểm (cache) và ghi ledger (outbox batch) để giảm đường critical path.

---

## 6. Kỳ vọng sau khi sửa

| Thay đổi | Virtual vs Platform |
|----------|---------------------|
| Chỉ tăng platform 32→200 | Chênh lệch **giảm** ở tải 500–1000 (platform đủ thread) |
| Tăng Redisson pool 512 | **Cả hai** tăng throughput; chênh lệch phụ thuộc H2 |
| Lua gộp Redis ops | Cả hai tăng mạnh; Redis CPU giảm |
| Bỏ double scheduling | Virtual + platform gần nhau hơn trên HTTP; checkout đơn giản hơn |

Virtual thread **vẫn đúng hướng** cho production I/O-bound; lợi ích **lớn nhất** khi platform pool nhỏ hơn concurrent **và** Redis/DB chưa là trần.
