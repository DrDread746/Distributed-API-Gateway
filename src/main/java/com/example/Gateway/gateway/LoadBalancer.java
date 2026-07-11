package com.example.Gateway.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancer across a pool of upstream URLs.
 * Each ProxyRoute has its own AtomicInteger counter — requests are
 * distributed evenly across all instances in the pool.
 * AtomicInteger is used instead of synchronized because:
 * - getAndIncrement() is a single CPU instruction (CAS), not a lock
 * - Under high concurrency this matters — no thread waits for another
 */
@Component
public class LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancer.class);

    // Per-route counters — keyed by route prefix e.g. "/api/users"
    // ConcurrentHashMap would be cleaner here; using a simple approach
    // since routes are registered at startup and don't change at runtime yet.
    private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> counters =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Picks the next upstream URL for this route using round-robin.
     *
     * @param route      the ProxyRoute containing the upstream pool
     * @param excludeUrl an upstream URL to skip (circuit is OPEN) — null means no exclusion
     * @return the chosen upstream base URL, or null if all instances are excluded
     */
    public String pick(ProxyRoute route, String excludeUrl) {
        List<String> upstreams = route.getUpstreamUrls();

        if (upstreams.isEmpty()) return null;

        // Single instance fast path — no need to round-robin
        if (upstreams.size() == 1) {
            String only = upstreams.get(0);
            return only.equals(excludeUrl) ? null : only;
        }

        AtomicInteger counter = counters.computeIfAbsent(
                route.getPrefix(), k -> new AtomicInteger(0));

        // Try each upstream at most once — skip any that match excludeUrl
        int size = upstreams.size();
        for (int i = 0; i < size; i++) {
            int index = Math.abs(counter.getAndIncrement() % size);
            String candidate = upstreams.get(index);
            if (!candidate.equals(excludeUrl)) {
                log.debug("LoadBalancer: route={} → selected {}", route.getPrefix(), candidate);
                return candidate;
            }
        }

        // All instances excluded (entire pool is open-circuited)
        log.warn("LoadBalancer: all upstreams unavailable for route={}", route.getPrefix());
        return null;
    }
}