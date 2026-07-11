package com.example.Gateway.gateway;

import com.example.Gateway.model.RouteDefinition;
import com.example.Gateway.model.RoutePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RouteConfig {

    private static final Logger log = LoggerFactory.getLogger(RouteConfig.class);

    // Redis hash key — all route definitions live under this key.
    // Each field in the hash is a route prefix, value is the RouteDefinition as JSON.
    // e.g. HGET gateway:routes /api/users → "{prefix:..., upstreamUrls:[...]}"
    static final String REDIS_ROUTES_KEY = "gateway:routes";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ConcurrentHashMap so reads during a reload don't need locking.
    // The interceptor reads these on every request — they must be thread-safe.
    private final ConcurrentHashMap<String, Map<String, RoutePolicy>> policies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProxyRoute> proxyRoutes = new ConcurrentHashMap<>();

    public RouteConfig() {
        loadHardcodedDefaults();
    }

    /**
     * On startup: load hardcoded defaults first so the gateway
     * is never in an empty state, then sync from Redis which
     * may override or add to the defaults.
     */
    @PostConstruct
    public void init() {
        syncFromRedis();
    }

    /**
     * Every 60 seconds: re-sync routes from Redis.
     * Now this actually does something — any route added or removed
     * via the admin API is picked up without a restart.
     */
    @Scheduled(fixedDelay = 60_000)
    public void reloadRoutes() {
        log.info("Reloading routes from Redis...");
        syncFromRedis();
    }

    /**
     * Reads all route definitions from the Redis hash and rebuilds
     * the in-memory proxyRoutes and policies maps.
     *
     * Uses putAll rather than clear+rebuild so the gateway stays
     * fully operational during the reload — no window where routes are empty.
     */
    public void syncFromRedis() {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash()
                    .entries(REDIS_ROUTES_KEY);

            if (entries.isEmpty()) {
                log.info("No routes found in Redis — using hardcoded defaults");
                return;
            }

            Map<String, ProxyRoute> updatedRoutes = new HashMap<>();
            Map<String, Map<String, RoutePolicy>> updatedPolicies = new HashMap<>();

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                String prefix = (String) entry.getKey();
                String json   = (String) entry.getValue();

                try {
                    RouteDefinition def = objectMapper.readValue(json, RouteDefinition.class);
                    updatedRoutes.put(prefix, toProxyRoute(def));
                    updatedPolicies.put(prefix, toPolicies(def));
                } catch (Exception e) {
                    log.error("Failed to deserialize route for prefix={}: {}", prefix, e.getMessage());
                }
            }

            proxyRoutes.putAll(updatedRoutes);
            policies.putAll(updatedPolicies);

            log.info("Loaded {} routes from Redis", updatedRoutes.size());

        } catch (Exception e) {
            log.error("Redis route sync failed — keeping existing routes: {}", e.getMessage());
            // Don't clear existing routes on failure — keep serving with what we have
        }
    }

    /**
     * Persists a RouteDefinition to Redis and immediately applies it
     * to the in-memory maps. Called by AdminController.
     */
    public void saveRoute(RouteDefinition def) throws Exception {
        String json = objectMapper.writeValueAsString(def);
        redisTemplate.opsForHash().put(REDIS_ROUTES_KEY, def.getPrefix(), json);

        // Apply immediately — don't wait for the 60s scheduler
        proxyRoutes.put(def.getPrefix(), toProxyRoute(def));
        policies.put(def.getPrefix(), toPolicies(def));

        log.info("Route saved and applied: prefix={} upstreams={}",
                def.getPrefix(), def.getUpstreamUrls());
    }

    /**
     * Removes a route from Redis and from the in-memory maps.
     * Returns false if the route didn't exist.
     */
    public boolean deleteRoute(String prefix) {
        Long removed = redisTemplate.opsForHash().delete(REDIS_ROUTES_KEY, prefix);
        if (removed > 0) {
            proxyRoutes.remove(prefix);
            policies.remove(prefix);
            log.info("Route deleted: prefix={}", prefix);
            return true;
        }
        return false;
    }

    /**
     * Returns all currently active route definitions.
     * Reads from Redis directly so the response always reflects persisted state.
     */
    public Map<Object, Object> listRoutes() {
        return redisTemplate.opsForHash().entries(REDIS_ROUTES_KEY);
    }

    // --- Runtime lookups (called on every request) ---

    public RoutePolicy getPolicyForRoute(String path, String tier) {
        // Try exact match first, then prefix match
        Map<String, RoutePolicy> tierPolicies = policies.get(path);

        if (tierPolicies == null) {
            // Check if any registered prefix matches this path
            for (Map.Entry<String, Map<String, RoutePolicy>> entry : policies.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    tierPolicies = entry.getValue();
                    break;
                }
            }
        }

        if (tierPolicies == null) return getDefaultPolicy(tier);

        RoutePolicy policy = tierPolicies.get(tier);
        return policy != null ? policy : getDefaultPolicy(tier);
    }

    public ProxyRoute getProxyRoute(String path) {
        ProxyRoute bestMatch = null;
        int bestMatchLength = -1;

        for (Map.Entry<String, ProxyRoute> entry : proxyRoutes.entrySet()) {
            String prefix = entry.getKey();
            if (path.startsWith(prefix) && prefix.length() > bestMatchLength) {
                bestMatch = entry.getValue();
                bestMatchLength = prefix.length();
            }
        }
        return bestMatch;
    }

    // --- Private helpers ---

    private void loadHardcodedDefaults() {
        // Rate limiting policies
        Map<String, RoutePolicy> pingPolicies = new HashMap<>();
        pingPolicies.put("free",       new RoutePolicy(5,   60_000, "slidingWindow", 0.1));
        pingPolicies.put("pro",        new RoutePolicy(20,  60_000, "slidingWindow", 0.5));
        pingPolicies.put("enterprise", new RoutePolicy(100, 60_000, "slidingWindow", 1.0));
        policies.put("/ping", pingPolicies);

        Map<String, RoutePolicy> searchPolicies = new HashMap<>();
        searchPolicies.put("free",       new RoutePolicy(5,  60_000, "fixedWindow", 0.1));
        searchPolicies.put("pro",        new RoutePolicy(15, 60_000, "fixedWindow", 0.3));
        searchPolicies.put("enterprise", new RoutePolicy(50, 60_000, "fixedWindow", 1.0));
        policies.put("/api/search", searchPolicies);

        Map<String, RoutePolicy> loginPolicies = new HashMap<>();
        loginPolicies.put("free",       new RoutePolicy(3,  60_000, "tokenBucket", 0.05));
        loginPolicies.put("pro",        new RoutePolicy(10, 60_000, "tokenBucket", 0.1));
        loginPolicies.put("enterprise", new RoutePolicy(50, 60_000, "tokenBucket", 0.5));
        policies.put("/api/login", loginPolicies);

        // Default proxy routes
        proxyRoutes.put("/api/users", new ProxyRoute(
                "/api/users",
                List.of("http://localhost:8080/backend/users"),
                true
        ));
        proxyRoutes.put("/api/search", new ProxyRoute(
                "/api/search",
                List.of("http://localhost:8080/backend/search"),
                false
        ));
    }

    private ProxyRoute toProxyRoute(RouteDefinition def) {
        return new ProxyRoute(def.getPrefix(), def.getUpstreamUrls(), def.isStripPrefix());
    }

    private Map<String, RoutePolicy> toPolicies(RouteDefinition def) {
        Map<String, RoutePolicy> tierPolicies = new HashMap<>();
        tierPolicies.put("free",
                new RoutePolicy(def.getFreeLimit(), def.getWindowSizeMs(), def.getAlgorithm(), 0.1));
        tierPolicies.put("pro",
                new RoutePolicy(def.getProLimit(), def.getWindowSizeMs(), def.getAlgorithm(), 0.5));
        tierPolicies.put("enterprise",
                new RoutePolicy(def.getEnterpriseLimit(), def.getWindowSizeMs(), def.getAlgorithm(), 1.0));
        return tierPolicies;
    }

    private RoutePolicy getDefaultPolicy(String tier) {
        return switch (tier) {
            case "pro"        -> new RoutePolicy(20,  60_000, "slidingWindow", 0.5);
            case "enterprise" -> new RoutePolicy(100, 60_000, "slidingWindow", 1.0);
            default           -> new RoutePolicy(5,   60_000, "slidingWindow", 0.1);
        };
    }
}