import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 20,        // 20 virtual users
    duration: '30s'
};

const API_KEYS = [
    'free-key-123',
    'pro-key-456',
    'enterprise-key-789'
];

export default function() {
    // randomly pick a tier
    const key = API_KEYS[Math.floor(Math.random() * API_KEYS.length)];

    const res = http.get('http://localhost:8080/api/search', {
        headers: { 'X-API-KEY': key }
    });

    check(res, {
        'valid response': (r) =>
            r.status === 200 || r.status === 429,
    });
}