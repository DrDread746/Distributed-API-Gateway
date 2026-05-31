import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 100 },   // ramp up to 10 users
        { duration: '1m',  target: 500 },   // ramp up to 50 users
        { duration: '30s', target: 10000 },  // ramp up to 100 users
        { duration: '30s', target: 250 },    // ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],  // 95% requests under 500ms
        http_req_failed: ['rate<0.1'],     // less than 10% failures
    }
};

export default function() {
    const res = http.get('http://localhost:8080/ping', {
        headers: { 'X-API-KEY': 'free-key-123' }
    });

    check(res, {
        'status is 200 or 429': (r) =>
            r.status === 200 || r.status === 429,
        'response time OK': (r) =>
            r.timings.duration < 500,
    });

    sleep(0.1); // 100ms between requests per user
}