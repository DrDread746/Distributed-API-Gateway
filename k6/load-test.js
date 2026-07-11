import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 10  },
        { duration: '1m',  target: 50  },
        { duration: '30s', target: 100 },
        { duration: '30s', target: 0   },
    ],
    thresholds: {
        // Only measure latency on requests that actually got through (200s)
        // 429s involve no backend work so mixing them skews the number
        'http_req_duration{status:200}': ['p(95)<500'],

        // 429s are correct behavior — exclude them from the failure rate.
        // We only want to catch actual errors (5xx, connection refused, etc).
        // k6 counts non-2xx as failed by default; we override with a custom metric below.
        'checks{check:not a server error}': ['rate>0.99'],
    }
};

// 20 unique users across all three tiers.
// This spreads VUs across more rate limit buckets so the test
// actually exercises throughput instead of just hammering 3 buckets.
//
// Free:       10 users  × 5  req/min limit = 50  req/min allowed
// Pro:         5 users  × 20 req/min limit = 100 req/min allowed
// Enterprise:  5 users  × 100 req/min limit = 500 req/min allowed
const USERS = [
    // 10 free tier users
    { key: 'free-key-123',  id: 'free-1',  tier: 'free'       },
    { key: 'free-key-124',  id: 'free-2',  tier: 'free'       },
    { key: 'free-key-125',  id: 'free-3',  tier: 'free'       },
    { key: 'free-key-126',  id: 'free-4',  tier: 'free'       },
    { key: 'free-key-127',  id: 'free-5',  tier: 'free'       },
    { key: 'free-key-128',  id: 'free-6',  tier: 'free'       },
    { key: 'free-key-129',  id: 'free-7',  tier: 'free'       },
    { key: 'free-key-130',  id: 'free-8',  tier: 'free'       },
    { key: 'free-key-131',  id: 'free-9',  tier: 'free'       },
    { key: 'free-key-132',  id: 'free-10', tier: 'free'       },
    // 5 pro tier users
    { key: 'pro-key-456',   id: 'pro-1',   tier: 'pro'        },
    { key: 'pro-key-457',   id: 'pro-2',   tier: 'pro'        },
    { key: 'pro-key-458',   id: 'pro-3',   tier: 'pro'        },
    { key: 'pro-key-459',   id: 'pro-4',   tier: 'pro'        },
    { key: 'pro-key-460',   id: 'pro-5',   tier: 'pro'        },
    // 5 enterprise tier users
    { key: 'enterprise-key-789', id: 'ent-1', tier: 'enterprise' },
    { key: 'enterprise-key-790', id: 'ent-2', tier: 'enterprise' },
    { key: 'enterprise-key-791', id: 'ent-3', tier: 'enterprise' },
    { key: 'enterprise-key-792', id: 'ent-4', tier: 'enterprise' },
    { key: 'enterprise-key-793', id: 'ent-5', tier: 'enterprise' },
];

export default function () {
    const user = USERS[__VU % USERS.length];

    const res = http.get('http://api-gateway:8080/ping', {
        headers: { 'X-API-KEY': user.key },
        tags: { tier: user.tier, user: user.id },
    });

    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'not a server error':   (r) => r.status < 500,
        'response time OK':     (r) => r.timings.duration < 500,
    });

    if (res.status === 429) {
        console.log(`Rate limited — user=${user.id} tier=${user.tier} retryAfter=${res.headers['Retry-After']}s`);
    }

    sleep(0.1);
}