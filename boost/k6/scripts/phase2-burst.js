/**
 * K6 — Phase 2: Burst Comparison (Platform vs Virtual)
 *
 * Đợt 1: Ném 5000 VUs vào Platform Threads (Pool 200).
 * Đợt 2: Ném 5000 VUs vào Virtual Threads (Loom).
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://boost:8080';

export const options = {
    scenarios: {
        // Đợt 1: Platform Burst
        platform_burst: {
            executor: 'constant-vus',
            vus: 2000,
            duration: '45s',
            exec: 'platformTest',
            tags: { thread_model: 'platform' },
        },
        // Đợt 2: Virtual Burst (Chạy sau khi Platform kết thúc 15s)
        virtual_burst: {
            executor: 'constant-vus',
            vus: 2000,
            duration: '45s',
            startTime: '60s',
            exec: 'virtualTest',
            tags: { thread_model: 'virtual' },
        },
    },
    thresholds: {
        'http_req_duration{thread_model:virtual}': ['p(95)<2000'],
        'http_req_failed': ['rate<0.2'],
    },
};

export function platformTest() {
    const res = http.post(
        `${BASE_URL}/api/rewards/bench/platform/io`,
        JSON.stringify({ customerId: 'burst-p', amount: 10 }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(res, { 'p-200': (r) => r.status === 200 });
}

export function virtualTest() {
    const res = http.post(
        `${BASE_URL}/api/rewards/bench/virtual/io`,
        JSON.stringify({ customerId: 'burst-v', amount: 10 }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(res, { 'v-200': (r) => r.status === 200 });
}

export function handleSummary(data) {
    // Trích xuất các chỉ số quan trọng để hiển thị ở Frontend
    const stats = {
        platform: {
            rps: data.metrics.http_reqs.values.rate, // Sẽ được lọc chính xác hơn ở đợt chạy
            p95: data.metrics['http_req_duration{thread_model:platform}']?.values['p(95)'] || 0,
            success: data.metrics.http_req_failed.values.passes === 0,
        },
        virtual: {
            rps: data.metrics.http_reqs.values.rate,
            p95: data.metrics['http_req_duration{thread_model:virtual}']?.values['p(95)'] || 0,
            success: data.metrics.http_req_failed.values.passes === 0,
        },
        total_requests: data.metrics.http_reqs.values.count
    };

    return {
        '/scripts/last_summary.json': JSON.stringify(stats),
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
