/**
 * K6 — Phase 2: Platform vs Virtual Thread throughput comparison.
 *
 * Ramp 0 → 5000 VUs. Platform test chạy trước, virtual test chạy sau.
 * Xem kết quả real-time trên Grafana.
 *
 * Chạy:
 *   docker compose run k6 run -o experimental-prometheus-rw /scripts/phase2-throughput.js
 */
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://boost:8080';

export const options = {
    scenarios: {
        platform_ramp: {
            executor: 'ramping-vus',
            exec: 'platformTest',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 100 },
                { duration: '30s', target: 1000 },
                { duration: '30s', target: 5000 },
                { duration: '1m',  target: 5000 },
                { duration: '15s', target: 0 },
            ],
            tags: { thread_model: 'platform' },
        },
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
            startTime: '3m',
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
    check(res, { 'platform 200': (r) => r.status === 200 });
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
    check(res, { 'virtual 200': (r) => r.status === 200 });
}
