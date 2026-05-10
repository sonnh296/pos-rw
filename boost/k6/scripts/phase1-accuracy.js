/**
 * K6 Script — Phase 1: Accuracy Test
 * 
 * Kiểm tra độ chính xác của reward calculation dưới concurrency.
 * Gửi 5 request đồng thời cho cùng 1 customerId, sau đó verify expected vs actual points.
 * 
 * Chạy:  docker compose run k6 run /scripts/phase1-accuracy.js
 *   hoặc: k6 run --env BASE_URL=http://localhost:8080 k6/scripts/phase1-accuracy.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Custom metrics
const accuracyPass = new Counter('accuracy_pass');
const accuracyFail = new Counter('accuracy_fail');
const processingTime = new Trend('processing_time_ms');

const BASE_URL = __ENV.BASE_URL || 'http://boost:8080';

export const options = {
    scenarios: {
        // Mỗi scenario chạy tuần tự, mỗi VU tạo 1 customerId unique
        platform_lock: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 1,
            exec: 'testAccuracy',
            env: { EXECUTOR: 'platform', MODE: 'lock' },
            startTime: '0s',
        },
        platform_nolock: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 1,
            exec: 'testAccuracy',
            env: { EXECUTOR: 'platform', MODE: 'no-lock' },
            startTime: '15s',
        },
        virtual_lock: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 1,
            exec: 'testAccuracy',
            env: { EXECUTOR: 'virtual', MODE: 'lock' },
            startTime: '30s',
        },
        virtual_nolock: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 1,
            exec: 'testAccuracy',
            env: { EXECUTOR: 'virtual', MODE: 'no-lock' },
            startTime: '45s',
        },
    },
    thresholds: {
        'accuracy_pass': ['count>0'],
        'http_req_duration': ['p(95)<5000'],
    },
};

export function testAccuracy() {
    const executor = __ENV.EXECUTOR;
    const mode = __ENV.MODE;
    const customerId = `k6-${executor}-${mode}-${__VU}-${Date.now()}`;
    const amount = Math.round((Math.random() * 90 + 10) * 100) / 100;
    const concurrentRequests = 5;
    const expectedPoints = Math.round(amount * 10) * concurrentRequests;

    const url = `${BASE_URL}/api/rewards/${executor}/${mode}`;
    const payload = JSON.stringify({ customerId, amount });
    const params = { headers: { 'Content-Type': 'application/json' } };

    // Gửi 5 request tuần tự (mỗi VU đại diện 1 concurrent stream)
    for (let i = 0; i < concurrentRequests; i++) {
        const res = http.post(url, payload, params);
        check(res, { 'status 200': (r) => r.status === 200 });
        processingTime.add(res.timings.duration);
    }

    sleep(2); // Chờ processing hoàn tất

    // Verify accuracy
    const pointsRes = http.get(`${BASE_URL}/api/rewards/points/${customerId}`);
    if (pointsRes.status === 200) {
        const body = JSON.parse(pointsRes.body);
        const actual = body.primaryPoints || 0;
        if (actual === expectedPoints) {
            accuracyPass.add(1);
        } else {
            accuracyFail.add(1);
            console.warn(`MISMATCH [${executor}/${mode}] VU=${__VU}: expected=${expectedPoints}, actual=${actual}`);
        }
    } else {
        accuracyFail.add(1);
        console.error(`Failed to get points for ${customerId}: status=${pointsRes.status}`);
    }
}
