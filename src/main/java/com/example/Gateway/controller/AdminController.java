package com.example.Gateway.controller;

import com.example.Gateway.gateway.RouteConfig;
import com.example.Gateway.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin API for managing gateway routes at runtime.
 * All endpoints require an X-Admin-Key header matching the configured secret.
 * This is intentionally simple — in production you'd use Spring Security
 * with a proper auth mechanism. For a portfolio project, the pattern is correct,
 * the implementation is deliberately lightweight.
 * Endpoints:
 *   GET    /admin/routes              — list all active routes
 *   POST   /admin/routes              — register or update a route
 *   DELETE /admin/routes/{prefix}     — remove a route
 *   POST   /admin/routes/reload       — force immediate Redis sync
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    // In production: move to application.properties and inject with @Value.
    // Hardcoded here to keep the setup simple.
    private static final String ADMIN_KEY = "admin-secret-key";

    @Autowired
    private RouteConfig routeConfig;

    @GetMapping("/routes")
    public ResponseEntity<?> listRoutes(@RequestHeader("X-Admin-Key") String adminKey) {
        if (!isAuthorized(adminKey)) return unauthorized();

        Map<Object, Object> routes = routeConfig.listRoutes();
        return ResponseEntity.ok(routes);
    }

    /**
     * Register a new route or update an existing one.
     *
     * Example request body:
     * {
     *   "prefix": "/api/payments",
     *   "upstreamUrls": ["http://payment-service:8083", "http://payment-service:8084"],
     *   "stripPrefix": true,
     *   "algorithm": "tokenBucket",
     *   "freeLimit": 3,
     *   "proLimit": 10,
     *   "enterpriseLimit": 50,
     *   "windowSizeMs": 60000
     * }
     *
     * Takes effect immediately — no restart needed.
     */
    @PostMapping("/routes")
    public ResponseEntity<?> addRoute(@RequestHeader("X-Admin-Key") String adminKey,
                                      @RequestBody RouteDefinition definition) {
        if (!isAuthorized(adminKey)) return unauthorized();

        if (definition.getPrefix() == null || definition.getPrefix().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "prefix is required"));
        }
        if (definition.getUpstreamUrls() == null || definition.getUpstreamUrls().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "at least one upstreamUrl is required"));
        }

        try {
            routeConfig.saveRoute(definition);
            log.info("Admin: route registered prefix={}", definition.getPrefix());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Route registered",
                    "prefix", definition.getPrefix(),
                    "upstreams", definition.getUpstreamUrls()
            ));
        } catch (Exception e) {
            log.error("Admin: failed to save route: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove a route by prefix.
     * The prefix in the URL path should be URL-encoded if it contains slashes.
     * e.g. DELETE /admin/routes/%2Fapi%2Fusers
     */
    @DeleteMapping("/routes/{prefix}")
    public ResponseEntity<?> deleteRoute(@RequestHeader("X-Admin-Key") String adminKey,
                                         @PathVariable String prefix) {
        if (!isAuthorized(adminKey)) return unauthorized();

        // Restore leading slash stripped by path variable binding
        String normalizedPrefix = "/" + prefix;

        boolean deleted = routeConfig.deleteRoute(normalizedPrefix);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Route deleted", "prefix", normalizedPrefix));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Route not found", "prefix", normalizedPrefix));
    }

    /**
     * Force an immediate reload from Redis without waiting for the 60s scheduler.
     * Useful after bulk changes or when you want to verify Redis state is in sync.
     */
    @PostMapping("/routes/reload")
    public ResponseEntity<?> forceReload(@RequestHeader("X-Admin-Key") String adminKey) {
        if (!isAuthorized(adminKey)) return unauthorized();

        routeConfig.syncFromRedis();
        return ResponseEntity.ok(Map.of("message", "Routes reloaded from Redis"));
    }

    private boolean isAuthorized(String key) {
        return ADMIN_KEY.equals(key);
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid admin key"));
    }
}