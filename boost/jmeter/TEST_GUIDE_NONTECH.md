# Hướng Dẫn Demo Test (Non-tech) - 7 Pha

## 1) Mục tiêu demo

Bộ test đánh giá sản phẩm theo 4 tiêu chí: **Consistency, Data Integrity, Concurrency, Availability**.

**Demo cơ bản (kể câu chuyện kỹ thuật, file `customer-demo-thread-comparison.jmx`):**

1. **Pha 1 - No-lock**: cho thấy dữ liệu có thể sai khi nhiều request cùng `customerId` (race condition).
2. **Pha 2 - Lock (hotspot)**: cùng workload hotspot như Pha 1 nhưng có lock → chứng minh lock fix được race (dữ liệu đúng). Chạy 3 mode `single` / `platform` / `virtual` để thấy lock hoạt động trên cả 3.
3. **Pha 3 - Production-like**: so sánh hiệu năng `platform` vs `virtual` với nhiều profile khách hàng (multi-customer fair-compare).

**Quality gate nâng cao (file `advanced-scenarios.jmx`):**

4. **Pha 4 - Idempotency**: cùng `transactionId` gửi N lần → chỉ 1 lần được apply, còn lại DUPLICATE.
5. **Pha 5 - Soak/Endurance**: 30 phút ở tải trung bình → phát hiện memory leak, connection pool leak.
6. **Pha 6 - Stress step load**: 100 → 500 → 1000 → 2000 users để tìm knee-point.
7. **Pha 7 - Chaos / Availability**: pause Redis/MySQL giữa load → verify circuit breaker fallback + consistency.

Kết luận: virtual thread + lock + idempotency key + outbox pattern là lựa chọn phù hợp cho workload POS.

---

## 2) File cần dùng

2 file JMeter:

- `boost/jmeter/customer-demo-thread-comparison.jmx` → Phase 1, 2, 3 (demo cơ bản).
- `boost/jmeter/advanced-scenarios.jmx` → Phase 4, 5, 6, 7 (quality gate).

Script tự động:

- `boost/jmeter/scripts/run-ci.sh` → chạy non-GUI + HTML dashboard + regression gate.
- `boost/jmeter/scripts/compare-baseline.py` → so sánh P95 với baseline.
- `boost/jmeter/scripts/run-chaos-redis.sh` → all-in-one Phase 7.
- `boost/jmeter/chaos/redis-down.sh` → pause/unpause Redis.
- `boost/jmeter/chaos/mysql-down.sh` → pause/unpause MySQL.

Frontend đã có sẵn phần hiển thị kết quả:

- Biểu đồ so sánh theo từng phase
- Verdict race condition cho Phase 1 (Expected vs Actual trên 1 hotspot customerId → mong đợi `RACE_DETECTED`)
- Verdict race condition cho Phase 2 (Expected vs Actual trên 1 hotspot customerId → mong đợi `CONSISTENT` nhờ lock + txnId guard)
- Verdict idempotency cho Phase 4 (Expected vs Actual khi spam cùng 1 txnId)
- Global consistency verdict cho Phase 2/3/5/6/7 (Σ Redis vs Σ MySQL)
- Nút clear toàn bộ dữ liệu để reset giữa các lần test

API mới để verify:

- `GET /api/rewards/consistency/global` trả:
  - `verdict` (MySQL vs Redis): `CONSISTENT / OUTBOX_DRAINING / MYSQL_BEHIND / REDIS_BEHIND / REDIS_UNAVAILABLE`.
  - `promiseVerdict` (Σ `expected:*` vs Σ `customer:points`): `PROMISE_KEPT / LOST_UPDATE_DETECTED / OVER_APPLIED / REDIS_UNAVAILABLE`. `LOST_UPDATE_DETECTED` nghĩa là có nơi làm read-modify-write ngoài lock → dữ liệu bị mất.

---

## 3) Input (tham số đầu vào)

Nhập trong `Test Plan -> User Defined Variables`:

Nhóm biến cho Pha 1 và Pha 2 (demo nhanh):

- `host` (mặc định: `localhost`)
- `port` (mặc định: `8080`)
- `protocol` (mặc định: `http`)
- `threads` (số user ảo đồng thời, mặc định: `40`)
- `rampUpSec` (thời gian tăng tải, mặc định: `5`)
- `loops` (số lần lặp mỗi thread, mặc định: `5`)
- `amount` (giá trị cộng mỗi request, mặc định: `50.0`)
- `inputsDir` (thư mục chứa CSV input cho Pha 1 và Pha 2, mặc định: `/Users/mac2019/Desktop/pos/boost/jmeter/inputs`)
- Pha 1 dùng CSV hotspot riêng cho mỗi test case (không cần sửa UDV `hotspotCustomerId`):
  - `p1-a-hotspot.csv` → `customer-race-a`
  - `p1-b-hotspot.csv` → `customer-race-b`
  - `p1-c-hotspot.csv` → `customer-race-c`
- Pha 2 cũng hotspot nhưng dùng customerId riêng (tách hẳn khỏi Pha 1 để verdict không đè lên nhau):
  - `p2-a-hotspot.csv` → `customer-lock-a`
  - `p2-b-hotspot.csv` → `customer-lock-b`
  - `p2-c-hotspot.csv` → `customer-lock-c`
  Backend tự biết map từng test case với customerId tương ứng khi tính verdict.
- `hotspotCustomerId` (UDV legacy, không dùng cho Pha 1/Pha 2 nữa nhưng vẫn giữ để tương thích, mặc định: `customer-race-001`)
- `connectTimeoutMs` (mặc định: `5000`)
- `responseTimeoutMs` (mặc định: `15000`)
- `resultDir` (thư mục lưu kết quả, mặc định: `/Users/mac2019/Desktop/pos/boost/jmeter/results`)

Nhóm biến cho Pha 3 (prod-like):

- `prodThreads` (mặc định: `500`)
- `prodRampUpSec` (mặc định: `30`)
- `prodLoops` (mặc định: `20`)
- `prodMinAmount`, `prodMaxAmount` (khoảng amount random, mặc định: `20`–`200`)

Ghi chú:
- Pha 1: mỗi test case đọc 1 CSV riêng trong `inputsDir` → 3 test case dùng 3 `customerId` khác nhau (không đè lên nhau).
- Pha 2: **cũng hotspot** giống Pha 1, đọc từ `p2-*-hotspot.csv` → 3 test case dùng 3 `customerId` riêng (`customer-lock-a/b/c`). Điểm khác biệt duy nhất với Pha 1 là endpoint có `/lock`, để chứng minh **cùng workload nhưng có lock thì race bị fix**.
- Pha 3: **customerId luôn unique** (dùng `${__UUID()}`) để đảm bảo “mỗi user hoàn toàn khác nhau”, tránh race/lock contention. `amount` vẫn random theo khoảng `prodMinAmount`–`prodMaxAmount`.

---

## 4) Output (kết quả cần xem)

### 4.1 Trong JMeter

- `Summary Report (Main Output)`: xem `# Samples`, `Average`, `Error %`, `Throughput`.
- `Aggregate Report (P95/P99)`: xem `95% Line`, `99% Line`.

### 4.2 File `.jtl` tự lưu cho từng test case

Mỗi test case có 1 file `.jtl` riêng:

> ⚠ **QUAN TRỌNG**: JMeter mặc định **APPEND** (ghi tiếp vào cuối) file `.jtl` đã tồn tại, không phải ghi đè. Do đó nếu bạn bấm Start nhiều lần mà **không** bấm `🗑 Clear data` trước, Samples sẽ cộng dồn qua từng run → verdict sai (Expected phình to).
>
> Nút **🗑 Clear data** (từ `v2` trở lên) đã được mở rộng để xoá tất cả `.jtl` trong thư mục results cùng với MySQL + Redis. Phản hồi JSON có thêm field `deletedJtlFiles` cho bạn kiểm tra.
>
> → **Luôn bấm Clear data trước mỗi run** là đủ, không cần thao tác gì thêm.

- `p1-a-single-no-lock.jtl`
- `p1-b-platform-no-lock.jtl`
- `p1-c-virtual-no-lock.jtl`
- `p2-a-single-lock.jtl`
- `p2-b-platform-lock.jtl`
- `p2-c-virtual-lock.jtl`
- `p3-a-platform-lock-prod.jtl`
- `p3-b-virtual-lock-prod.jtl`

Đường dẫn thư mục: `/Users/mac2019/Desktop/pos/boost/jmeter/results`

### 4.3 Trên frontend

Mở trang “Customer Points” trong frontend, bấm `↻ Refresh summary`:

- Mỗi Phase có 1 biểu đồ riêng, so sánh `Avg`, `P95`, `Throughput`.
- Phase 1 có thêm block verdict với 3 trạng thái:
  - `✅ Consistent` — `Actual == Expected`, dữ liệu đúng.
  - `⚠ Race detected` — `Actual < Expected`, bị race, mất điểm.
  - `ℹ Dirty data (clear before re-run)` — `Actual > Expected`, data cũ chưa clear, cần bấm Clear rồi chạy lại.
- Mỗi verdict hiển thị `Expected`, `Actual`, `Missing` và customerId tương ứng.

---

## 5) Quy trình chạy demo (GUI, từng bước)

### Bước A - Mở file JMX

1. Mở JMeter GUI.
2. `File -> Close` file cũ (nếu đang mở bản cũ trong memory).
3. `File -> Open` → chọn `customer-demo-thread-comparison.jmx`.
4. Kiểm tra cây cây thread group hiển thị đủ P1-A...P3-B.

### Bước B - Kiểm tra backend đã chạy

- `http://localhost:8080/actuator/health` phải trả về `UP`.

### Bước C - Reset data trước mỗi phase

- Mở frontend, bấm `🗑 Clear data`.
- Hoặc gọi API: `POST http://localhost:8080/api/rewards/points/clear`.

Nút Clear sẽ làm 3 việc:
1. Xóa bảng `customer_balance` và `reward_ledger` trong MySQL.
2. `FLUSHDB` Redis (xóa sạch cả `customer:points`, outbox, idempotency và mọi key tồn dư của Redisson lock).
3. **Xoá toàn bộ file `.jtl`** trong thư mục results (vì JMeter append, không ghi đè).

Quan trọng: nếu không clear giữa các lần chạy, verdict sẽ sai do 2 nguồn tích luỹ:
- **Actual** (`customer:points` trên Redis) cộng dồn từ run cũ → `ℹ Dirty data`.
- **Expected** (key `expected:{customerId}` trên Redis) cũng cộng dồn đồng đều cùng Actual. Cả hai đều lệch đi so với số mẫu trong file `.jtl` hiện tại → verdict hiểu nhầm.

Nút Clear đã reset cả 3: MySQL, Redis (bao gồm `expected:*`), và `.jtl`, nên chỉ cần bấm 1 lần là sạch toàn bộ.

---

## 6) Pha 1 - Demo race condition

Thread Groups:

- `P1-A Single no-lock hotspot`
- `P1-B Platform no-lock hotspot`
- `P1-C Virtual no-lock hotspot`

### Cách chạy

Lặp lại quy trình sau cho mỗi thread group:

1. Clear data (bước C ở trên).
2. Chỉ enable đúng 1 thread group trong Phase 1, disable các group khác.
3. Bấm nút `Start`.
4. Sau khi chạy xong: mở frontend, bấm `↻ Refresh summary`.
5. Xem verdict ở block của test case tương ứng.

### Kỳ vọng

- `Single no-lock` (customer-race-a): thường `✅ Consistent` (tuần tự trong 1 executor).
- `Platform no-lock` (customer-race-b): thường `⚠ Race detected`, `Missing > 0`.
- `Virtual no-lock` (customer-race-c): thường `⚠ Race detected`, `Missing > 0`.

Vì mỗi test case dùng 1 customerId riêng nên verdict của từng test case không bị nhiễu bởi run của test case khác. Tuy nhiên vẫn nên clear data trước mỗi test case để expected=actual khi dữ liệu sạch.

### Công thức expected (động theo input)

Từ bản này backend không còn dùng `hotspotAmount` hardcoded để tính `Expected`. Thay vào đó, **mọi request server quyết định cộng điểm sẽ atomic `INCRBY expected:{customerId}`** (Redis) trước khi làm read-modify-write trên `customer:points`. Vì vậy:

- `Expected` = giá trị key `expected:{customerId}` trong Redis = Σ (amount × 10) của mọi request server thực sự nhận.
- `Actual` = giá trị `customer:points` (có thể bị race làm mất).
- Công thức ngầm: `Expected = samples_success × amount × 10`, nhưng bạn đổi `amount` trong JMeter thì `Expected` tự đổi theo (không cần sửa config backend).

Ví dụ: `threads=40`, `loops=5`, `amount=50` → `Expected = 40 × 5 × 50 × 10 = 100.000`.

Nếu race xảy ra, `Actual < Expected` → `Missing > 0`.

---

## 7) Pha 2 - Lock fix race (correctness)

Thread Groups:

- `P2-A Single lock` (hotspot `customer-lock-a`)
- `P2-B Platform lock` (hotspot `customer-lock-b`)
- `P2-C Virtual lock` (hotspot `customer-lock-c`)

Pha này **cố ý copy y hệt workload của Pha 1** (cùng hotspot 1 customerId, cùng threads, cùng loops, cùng amount) — khác **duy nhất** ở chỗ endpoint có `/lock`. Mục tiêu: chứng minh rằng cùng 1 workload có contention cao, **Pha 1 mất điểm (race), Pha 2 không mất điểm (lock)**.

Pha này kiểm chứng **cả 2 tầng bảo vệ** của production path (`LockingRedisRewardService`):

1. **Lock theo `customerId`** (Redisson `reward:lock:{customerId}`): ngăn race khi nhiều request cùng cộng điểm cho 1 khách hàng.
2. **Guard theo `transactionId`** (`setIfAbsent("idempotency:{txnId}")`): ngăn 1 giao dịch bị cộng 2 lần nếu client retry.

> ⚠ **Về so sánh performance Single/Platform/Virtual**
> Vì cả 3 mode đều serialize qua cùng 1 lock Redisson trên 1 customerId hotspot, throughput của 3 mode sẽ xấp xỉ nhau trong Pha 2 — đó là điều tất yếu của lock. Muốn so sánh thread model fair-compare (multi-customer, contention thấp), hãy xem **Pha 3**, đó mới là nơi Virtual Thread thể hiện ưu thế.

### Cách chạy

Lặp lại cho từng thread group:

1. Clear data.
2. Chỉ enable đúng 1 group.
3. Bấm `Start`.
4. Frontend: block Phase 2 sẽ có verdict `✅ Consistent / Expected / Actual / Missing` ngay cạnh biểu đồ Avg/P95/Throughput.

### Kỳ vọng

- **Correctness** (mục tiêu chính, chứng minh lock hoạt động):
  - Verdict: `✅ Consistent`.
  - `Expected = Actual`, `Missing = 0`.
  - Nếu thấy `⚠ Race detected` → lock theo customerId bị phá vỡ (lock không hoạt động, giống Pha 1), phải điều tra ngay.
  - Nếu thấy `ℹ Dirty data` → chưa Clear trước khi chạy; bấm Clear rồi chạy lại.
- So sánh với Pha 1 (cùng customerId nhóm): Pha 1 hotspot → Missing > 0; Pha 2 hotspot → Missing = 0. Đó là bằng chứng "lock fix race".

### Công thức expected (Pha 2)

Giống Pha 1: dùng per-hotspot.
- `Expected = expected:{customerId}` trong Redis (atomic INCRBY mỗi lần server quyết định cộng điểm).
- `Actual = customer:points → {customerId}` trong Redis.
- Lock đúng ⇒ `Expected == Actual`. Lost update ⇒ `Expected > Actual`.

Ví dụ: `threads=100`, `loops=1`, `amount=30` → `Expected = 100 × 1 × 30 × 10 = 30.000`. Pha 1 hotspot thường `Actual < 30.000` (race); Pha 2 hotspot phải `Actual = 30.000` chính xác.

> 💡 **Lock theo transactionId ở đâu?**
> Mỗi request lọt qua lock customerId sẽ còn đi qua `setIfAbsent(idempotency:{txnId})`. Trong Pha 2 mỗi request có `transactionId` duy nhất nên mọi request SUCCESS bình thường. Nếu có bug trùng txnId, verdict sẽ chuyển thành `DIRTY_DATA` (`Actual > Expected`). Pha 4 sẽ stress test riêng nhánh này.

---

## 8) Pha 3 - Production-like

Thread Groups:

- `P3-A Production-like Platform lock`
- `P3-B Production-like Virtual lock`

### Cách chạy

1. Clear data.
2. Enable `P3-A` (disable mọi group khác), bấm `Start`, đợi xong.
3. Clear data.
4. Enable `P3-B`, bấm `Start`, đợi xong.
5. Trên frontend xem biểu đồ Phase 3.

### Kỳ vọng

- Không có race (đều dùng lock).
- So sánh chỉ số trong biểu đồ Phase 3: throughput, P95, P99, error%.
- Thường `Virtual lock` cho throughput cao hơn hoặc tương đương với latency ổn định hơn `Platform lock` khi nhiều request bị blocking I/O.

> 💡 **Mẹo demo để thấy Virtual Thread thắng rõ**
> Nếu backend đang bị bottleneck bởi Redis/DB thì Platform và Virtual có thể khá sát nhau. Để làm hiệu ứng queueing của platform thread pool rõ ràng hơn (đúng Cách 2):
> - Set `app.executors.platform.size` xuống `50`–`100` (mặc định `100`).
> - Set `prodThreads` >= `500` (thường `1500`–`3000`) và `prodRampUpSec` ~ `10`–`30`.
> Khi đó `Platform lock` sẽ bắt đầu queue (P95/P99 tăng rõ), còn `Virtual lock` thường giữ latency ổn định hơn.

---

## 9) Bảng tổng hợp kết quả để trình bày

### Pha 1 - No-lock race

| Mode | Customer | Samples | Error % | Expected | Actual | Missing | Verdict |
|---|---|---:|---:|---:|---:|---:|---|
| Single no-lock | customer-race-a |  |  |  |  |  |  |
| Platform no-lock | customer-race-b |  |  |  |  |  |  |
| Virtual no-lock | customer-race-c |  |  |  |  |  |  |

### Pha 2 - Lock fix race (hotspot)

| Mode | Customer | Samples | Error % | Expected | Actual | Missing | Verdict |
|---|---|---:|---:|---:|---:|---:|---|
| Single lock | customer-lock-a |  |  |  |  |  |  |
| Platform lock | customer-lock-b |  |  |  |  |  |  |
| Virtual lock | customer-lock-c |  |  |  |  |  |  |

So sánh dòng tương ứng ở Pha 1 (cùng thread model) để thấy hiệu ứng của lock: `Missing` từ > 0 ở Pha 1 trở về 0 ở Pha 2.

### Pha 3 - Production-like

| Mode | Samples | Error % | Avg (ms) | P95 (ms) | P99 (ms) | Throughput (req/s) |
|---|---:|---:|---:|---:|---:|---:|
| Platform lock (prod) |  |  |  |  |  |  |
| Virtual lock (prod) |  |  |  |  |  |  |

---

## 10) Lưu ý để so sánh công bằng

- Mỗi lần chỉ enable **1 Thread Group**.
- Clear data giữa các test case Phase 1/2/4 để verdict không bị ảnh hưởng bởi run trước (Phase 1 và Phase 2 đều hotspot theo customerId, nếu không clear thì `Expected` / `Actual` sẽ cộng dồn qua các run).
- Giữ nguyên input giữa các mode trong cùng 1 phase để so sánh công bằng.
- Nếu dao động lớn: chạy mỗi mode 3 lần, bỏ lần đầu (warm-up), lấy trung bình 2 lần sau.
- Không cần đổi tên file output thủ công; chạy lại cùng test case sẽ ghi đè đúng file của nó.
- Sau khi chỉnh sửa JMX bên ngoài, phải `File -> Close` rồi `File -> Open` lại trong JMeter để nạp bản mới.

---

## 11) Pha 4 - Idempotency (data integrity)

File JMX: `advanced-scenarios.jmx`, thread group `P4-A Idempotency same-txnId`.

### Mục tiêu

Một hệ thống POS **phải đảm bảo 1 giao dịch chỉ được ghi nhận 1 lần** dù client retry bao nhiêu lần (mất mạng, timeout, user bấm nút 2 lần…). Test này gửi **800 request cùng `transactionId`** và kiểm tra số điểm cuối cùng chỉ cộng 1 lần.

### Input

- `idemTxnId` = `idem-fixed-txn-001` (cố định)
- `idemCustomerId` = `customer-idempotency-001`
- `idemAmount` = `50`
- `idemThreads` = `40`, `idemLoops` = `20` → 800 request

### Cách chạy

1. Clear data.
2. Enable **đúng** `P4-A Idempotency` trong `advanced-scenarios.jmx`.
3. Bấm `Start`.
4. Mở frontend, bấm `↻ Refresh summary` → xem block Phase 4.

### Kỳ vọng

- `Expected points` = `idemAmount × 10` (chỉ 1 request apply). Với `idemAmount=50` → `Expected = 500`.
- `Actual points` phải bằng `Expected` (backend dùng `expected:{customerId}` atomic INCRBY khi và chỉ khi SUCCESS → nếu `actual == expected != 0` tức đã áp đúng 1 lần).
- Khoảng 799/800 response có `"status":"DUPLICATE_TRANSACTION"`, 1 response có `"status":"SUCCESS"`.
- Verdict: `✅ IDEMPOTENT_OK` khi `ok == 1 && samples > 1 && actual == expected`.
- Nếu `IDEMPOTENCY_VIOLATED` (ok > 1, amount được apply nhiều lần) → có bug nghiêm trọng, dừng demo.

### Bonus: test TTL

Idempotency key TTL 60s. Nếu cần test post-TTL: chạy P4-A, đợi 70s, rồi chạy lại → lần 2 sẽ apply thêm 500 điểm nữa → tổng = 1000. Nên cân nhắc tăng TTL cho production.

---

## 12) Pha 5 - Soak / Endurance (availability)

File JMX: `advanced-scenarios.jmx`, thread group `P5 Soak 30min virtual lock`.

### Mục tiêu

Phát hiện các lỗi chỉ xuất hiện sau thời gian dài: memory leak, connection pool leak, thread leak, Redisson lock rò rỉ. Chạy 200 users liên tục 30 phút.

### Input

- `soakThreads` = `200`, `soakRampUpSec` = `60`, `soakDurationSec` = `1800` (30 phút).

### Cách chạy

1. Clear data.
2. Enable `P5 Soak`.
3. Bấm `Start`, đợi ~31 phút.
4. Mở HTML dashboard (xem mục 14) để check P95 theo thời gian.

### Kỳ vọng

- Error % < 0.1%.
- P95 ở 5 phút cuối **không tăng quá 20%** so với 5 phút đầu.
- `GET /api/rewards/consistency/global` sau test: verdict `CONSISTENT` hoặc `OUTBOX_DRAINING` (đợi vài giây sau khi test xong).
- Heap JVM ổn định (xem qua `http://localhost:8080/actuator/metrics/jvm.memory.used`).

---

## 13) Pha 6 - Stress step load (concurrency & knee-point)

File JMX: `advanced-scenarios.jmx`, thread groups `P6-A..D` (100, 500, 1000, 2000 users).

### Mục tiêu

Tìm **knee-point**: mức tải mà throughput bắt đầu giảm và latency bắt đầu tăng phi tuyến. Đây là con số quan trọng để capacity planning.

### Cách chạy

Chạy lần lượt **từng** group, clear data giữa các lần:

1. Clear data → enable `P6-A Stress 100` → Start → ghi lại throughput + P95.
2. Clear data → enable `P6-B Stress 500` → Start → ghi lại.
3. Clear data → enable `P6-C Stress 1000` → Start → ghi lại.
4. Clear data → enable `P6-D Stress 2000` → Start → ghi lại.

### Kỳ vọng & cách đọc

| Load | Kỳ vọng throughput | Kỳ vọng P95 | Signal |
|---|---|---|---|
| 100 | baseline | < 200ms | OK |
| 500 | ~5× của 100 | < 400ms | Scale tuyến tính |
| 1000 | < 2× của 500 | > 1s | Bắt đầu quá tải |
| 2000 | ~ của 1000 hoặc kém hơn | > 3s | Đã vượt knee-point |

Knee-point = mức load mà throughput không còn tăng tuyến tính. Đây là **capacity thật** của hệ thống.

---

## 14) Pha 7 - Chaos / Availability

File JMX: `advanced-scenarios.jmx`, thread group `P7 Chaos virtual lock`.

### Mục tiêu

Verify **circuit breaker Redis → MySQL fallback** hoạt động đúng khi Redis chết giữa load. Hệ thống phải giữ availability, **không lỗi 5xx hàng loạt**.

### Cách chạy (thủ công, 2 terminal)

Terminal 1 (JMeter):

```bash
# Enable P7 Chaos, bấm Start trong JMeter GUI, hoặc:
cd boost/jmeter
./scripts/run-ci.sh advanced-scenarios.jmx p7-chaos
```

Terminal 2 (chaos):

```bash
# Pause Redis 30s, bắt đầu sau 30s (đủ cho P7 warm-up)
cd boost/jmeter
./chaos/redis-down.sh 30 30
```

Hoặc dùng script all-in-one:

```bash
cd boost/jmeter
./scripts/run-chaos-redis.sh
```

### Kỳ vọng

- Trong lúc Redis down: response có status chứa suffix `_REDIS_LOCK_REJECTED_FALLBACK_MYSQL`.
- Error rate tổng thể < 5%.
- Sau khi Redis up lại, rewards-batch drain outbox → verify:

```bash
curl -s http://localhost:8080/api/rewards/consistency/global | jq
```

→ `verdict: "CONSISTENT"`, `diff: 0`.

### Biến thể

- `./chaos/mysql-down.sh 30 30` → MySQL chết → Redis vẫn phục vụ, outbox tích tụ → kiểm chứng khả năng chịu lỗi DB.

---

## 15) CI / Non-GUI run

Tất cả phase chạy được không cần GUI:

```bash
cd boost/jmeter
./scripts/run-ci.sh customer-demo-thread-comparison.jmx p2-c-virtual-lock
./scripts/run-ci.sh advanced-scenarios.jmx p4-a-idempotency
./scripts/run-ci.sh advanced-scenarios.jmx p6-b-stress-500
```

Script sẽ:

1. Chạy JMeter non-GUI với target `localhost:8080` (đổi qua env `HOST`, `PORT`).
2. Sinh HTML dashboard trong `boost/jmeter/reports/<case>-<timestamp>/index.html`.
3. So sánh với baseline ở `boost/jmeter/baseline/<case>.jtl`.
4. Fail nếu P95 > `P95_THRESHOLD_MS` (mặc định 500ms) hoặc error% > `ERROR_THRESHOLD_PCT` (mặc định 1%) hoặc P95 regression > 15% so với baseline.

Baseline tự được tạo lần đầu chạy (commit vào repo để lock-in).

---

## 16) Bảng tổng hợp các chất lượng phi chức năng

| Tiêu chí | Phase liên quan | Pass criteria |
|---|---|---|
| **Consistency** | P1 (race), P2/P3/P5/P6/P7 (global) | Verdict `CONSISTENT`, `diff=0` |
| **Data Integrity** | P4 (idempotency), P7 (outbox durability) | `IDEMPOTENT_OK`, no lost txn sau chaos |
| **Concurrency** | P2, P3, P6 (step load) | Throughput scale tuyến tính đến knee-point; P95 ổn định |
| **Availability** | P5 (soak), P7 (chaos) | Error % < 5%, CB fallback kích hoạt, phục hồi tự động |
