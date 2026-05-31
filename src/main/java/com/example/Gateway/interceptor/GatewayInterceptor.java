package com.example.Gateway.interceptor;

import com.example.Gateway.algorithm.RateLimiterAlgo;
import com.example.Gateway.auth.ApiKeyValidator;
import com.example.Gateway.gateway.ProxyHandler;
import com.example.Gateway.gateway.ProxyRoute;
import com.example.Gateway.gateway.RouteConfig;
import com.example.Gateway.logger.GatewayLogger;
import com.example.Gateway.model.ApiKey;
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

        String requestId = UUID.randomUUID().toString().substring(0,8);
        RequestContext ctx = new RequestContext(requestId);
        response.setHeader("X-Request-ID", requestId);

        String apiKey = request.getHeader("X-API-KEY");

        //Step 1 - auth
        if(apiKey == null){
            response.setStatus(401);
            response.getWriter().write("Invalid API-KEY");
            return false;
        }

        ApiKey validatedKey = apiKeyValidator.validate(apiKey);
        if(validatedKey == null){
            meterRegistry.counter("gateway.auth.failures",
                    "reason", "invalid_key"
            ).increment();
            response.setStatus(401);
            response.getWriter().write("Invalid API-KEY");
            return false;
        }

        //Step 2 - Build Context
        String identity = validatedKey.getOwner();
        String tier = validatedKey.getTier();
        String route = request.getRequestURI();

        ctx.setRoute(route);
        ctx.setTier(tier);
        ctx.setOwner(identity);

        RoutePolicy policy = routeConfig.getPolicyForRoute(route, tier);
        ctx.setAlgorithm(policy.getAlgorithm());

        //Step 3 - Rate Limit
        meterRegistry.counter("gateway.requests.total",
                "route", route,
                "tier", tier
        ).increment();


        boolean allowed = false;


        if(redisHealthMonitor.isHealthy()){
            // normal path - Redis Based Limiting
            RateLimiterAlgo algo = algorithms.get(policy.getAlgorithm());
            allowed = algo.isAllowed(identity, route, policy, request, response);
            ctx.setAllowed(allowed);
        } else {
            log.warn("[{}] Redis unavailable - using local fallback limiter",
                    requestId);
            allowed = localRateLimiter.isAllowed(identity, route);
            if(!allowed) response.setStatus(429);

            // track fallback usage in metrics
            meterRegistry.counter("gateway.redis.fallback",
                    "route", route).increment();
        }

        if(!allowed) {
            ctx.setStatusCode(429);
            meterRegistry.counter("gateway.requests.blocked",
                    "route", route,
                    "tier", tier
            ).increment();
            gatewayLogger.logRequest(ctx);
            return false;
        }

        //Step 4 - Proxy
        ProxyRoute proxyRoute = routeConfig.getProxyRoute(route);
        if(proxyRoute != null){
            ctx.setStatusCode(200);
            gatewayLogger.logRequest(ctx);
            return proxyHandler.forward(proxyRoute.getTargetUrl(), request, response);
        }

        ctx.setStatusCode(200);
        gatewayLogger.logRequest(ctx);
        return true;
    }
}