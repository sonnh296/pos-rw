/**
 * K6 Script — Phase 2: Throughput Comparison (Platform vs Virtual Thread)
 * 
 * So sánh hiệu năng I/O-bound giữa Platform Thread pool (20 threads) và Virtual Thread.
 * Ramp từ 0 → 5000 VUs để quan sát điểm bão hòa (saturation point).
 *
 * Kết quả real-time: xem trên Grafana dashboard (Prometheus data source).
 *
 * Chạy:
 *   docker compose run k6 run -o experimental-prometheus-rw /scripts/phase2-throughput.js
 *
 * Hoặc local:
 *   k6 run --env BASE_URL=http://localhost:8080 \
 *     -o experimental-prometheus-rw \
 *     --env K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
 *     k6/scripts/phase2-throughput.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://boost:8080';

export const options = {
    scenarios: {
        // ── Platform Thread Test ──
        // Ramp lên 5000 VUs → platform pool (20 threads) sẽ bão hòa → latency tăng vọt
        platform_ramp: {
            executor: 'ramping-vus',
            exec: 'platformTest',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 100 },     // Warm-up
                { duration: '30s', target: 1000 },     // Ramp
                { duration: '30s', target: 5000 },     // Peak load
                { duration: '1m',  target: 5000 },     // Sustain
                { duration: '15s', target: 0 },         // Cool down
            ],
            tags: { thread_model: 'platform' },
        },
        // ── Virtual Thread Test ──
        // Chạy sau platform test, cùng load pattern để so sánh công bằng
        virtual_ramp: {
            executor: 'ramping-vus',
            exec: 'virtualTest',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 100 },
                { duration: '30s', target: 1000 },
                { duration: '30s', target: 5000 },
                { duration: '1m',  target: 5000 },
                { duration: '15s', target: 0 },
            ],
            startTime: '3m',  // Bắt đầu sau khi platform test hoàn tất
            tags: { thread_model: 'virtual' },
        },
    },
    thresholds: {
        'http_req_duration{thread_model:platform}': ['p(95)<10000'],
        'http_req_duration{thread_model:virtual}': ['p(95)<5000'],
        'http_req_failed{thread_model:platform}': ['rate<0.5'],
        'http_req_failed{thread_model:virtual}': ['rate<0.1'],
    },
};

export function platformTest() {
    const res = http.post(
        `${BASE_URL}/api/rewards/bench/platform/io`,
        JSON.stringify({ customerId: 'k6-bench', amount: 10 }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { thread_model: 'platform' },
        }
    );
    check(res, {
        'platform: status 200': (r) => r.status === 200,
    });
}

export function virtualTest() {
    const res = http.post(
        `${BASE_URL}/api/rewards/bench/virtual/io`,
        JSON.stringify({ customerId: 'k6-bench', amount: 10 }),
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { thread_model: 'virtual' },
        }
    );
    check(res, {
        'virtual: status 200': (r) => r.status === 200,
    });
}
