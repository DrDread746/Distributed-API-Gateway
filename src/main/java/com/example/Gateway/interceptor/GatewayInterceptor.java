package com.example.Gateway.interceptor;

import com.example.Gateway.algorithm.RateLimiterAlgo;
import com.example.Gateway.auth.ApiKeyValidator;
import com.example.Gateway.gateway.ProxyHandler;
import com.example.Gateway.gateway.ProxyRoute;
import com.example.Gateway.gateway.RouteConfig;
import com.example.Gateway.logger.GatewayLogger;
import com.example.Gateway.model.ApiKey;
import com.example.Gateway.model.RateLimitInfo;
import com.example.Gateway.model.RequestContext;
import com.example.Gateway.model.RoutePolicy;
import com.example.Gateway.resilience.LocalRateLimiter;
import com.example.Gateway.resilience.RedisHealthMonitor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

@Component
public class GatewayInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GatewayInterceptor.class);

    @Autowired
    private RedisHealthMonitor redisHealthMonitor;

    @Autowired
    private LocalRateLimiter localRateLimiter;

    @Autowired
    private GatewayLogger gatewayLogger;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ProxyHandler proxyHandler;

    @Autowired
    private RouteConfig routeConfig;

    @Autowired
    private Map<String, RateLimiterAlgo> algorithms;

    @Autowired
    private ApiKeyValidator apiKeyValidator;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        RequestContext ctx = new RequestContext(requestId);
        response.setHeader("X-Request-ID", requestId);

        // Admin Bypass
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/admin/")) {
            return true;
        }

        // Step 1 - Auth
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey == null) {
            response.setStatus(401);
            response.getWriter().write("Invalid API-KEY");
            return false;
        }

        ApiKey validatedKey = apiKeyValidator.validate(apiKey);
        if (validatedKey == null) {
            meterRegistry.counter("gateway.auth.failures", "reason", "invalid_key").increment();
            response.setStatus(401);
            response.getWriter().write("Invalid API-KEY");
            return false;
        }

        // Step 2 - Build context
        String identity = validatedKey.getOwner();
        String tier = validatedKey.getTier();
        String route = request.getRequestURI();

        ctx.setRoute(route);
        ctx.setTier(tier);
        ctx.setOwner(identity);

        RoutePolicy policy = routeConfig.getPolicyForRoute(route, tier);
        ctx.setAlgorithm(policy.getAlgorithm());

        meterRegistry.counter("gateway.requests.total", "route", route, "tier", tier).increment();

        // Step 3 - Rate limit
        // RateLimitInfo carries limit/remaining/reset so ProxyHandler can write
        // X-RateLimit-* headers on every response, and Retry-After on 429s.
        boolean allowed;
        RateLimitInfo rateLimitInfo;

        if (redisHealthMonitor.isHealthy()) {
            RateLimiterAlgo algo = algorithms.get(policy.getAlgorithm());
            allowed = algo.isAllowed(identity, route, policy, request, response);
            ctx.setAllowed(allowed);

            // Most algorithms track remaining internally via Redis.
            // We use policy.getMaxRequests() as the cap; remaining is approximate
            // here — a future improvement would be to have isAllowed() return it directly.
            rateLimitInfo = RateLimitInfo.fromPolicy(policy, allowed ? policy.getMaxRequests() - 1 : 0);
        } else {
            log.warn("[{}] Redis unavailable - using local fallback limiter", requestId);
            allowed = localRateLimiter.isAllowed(identity, route);
            if (!allowed) response.setStatus(429);
            meterRegistry.counter("gateway.redis.fallback", "route", route).increment();

            // On the fallback limiter we don't have precise remaining counts,
            // so we signal unknown with remaining = -1. ProxyHandler skips
            // X-RateLimit-Remaining in that case rather than lying to the client.
            rateLimitInfo = RateLimitInfo.unknown(policy);
        }

        if (!allowed) {
            ctx.setStatusCode(429);
            meterRegistry.counter("gateway.requests.blocked", "route", route, "tier", tier).increment();

            // Write rate limit headers even on blocked responses —
            // Retry-After especially is critical so clients back off correctly.
            response.setHeader("X-RateLimit-Limit", String.valueOf(policy.getMaxRequests()));
            response.setHeader("Retry-After", String.valueOf(rateLimitInfo.getRetryAfterSeconds()));

            gatewayLogger.logRequest(ctx);
            return false;
        }

        // Step 4 - Proxy
        ProxyRoute proxyRoute = routeConfig.getProxyRoute(route);
        if (proxyRoute != null) {
            ctx.setStatusCode(200);
            gatewayLogger.logRequest(ctx);
            return proxyHandler.forward(proxyRoute, route, requestId, tier, rateLimitInfo, request, response);
        }

        ctx.setStatusCode(200);
        gatewayLogger.logRequest(ctx);
        return true;
    }
}