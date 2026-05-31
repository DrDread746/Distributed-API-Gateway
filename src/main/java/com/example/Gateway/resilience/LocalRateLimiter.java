package com.example.Gateway.resilience;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalRateLimiter {

    // simple in-memory fallback
    private final ConcurrentHashMap<String, long[]> localStore
            = new ConcurrentHashMap<>();

    private static final int FALLBACK_LIMIT = 10;
    private static final long WINDOW_MS = 60_000;

    public boolean isAllowed(String identity, String route) {
        String key = identity + ":" + route;
        long currentTime = System.currentTimeMillis();

        long[] data = localStore.compute(key, (k, existing) -> {
            if(existing == null ||
                    (currentTime - existing[1]) > WINDOW_MS) {
                return new long[]{1, currentTime};
            }
            existing[0]++;
            return existing;
        });

        return data[0] <= FALLBACK_LIMIT;
    }
}