package com.example.Gateway.gateway;

import com.example.Gateway.model.RoutePolicy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RouteConfig {

    private final Map<String, Map<String,RoutePolicy>> policies = new HashMap<>();
    private final Map<String, ProxyRoute> proxyRoutes = new HashMap<>();

    public RouteConfig() {
        initializePolicies();
    }

    @Scheduled(fixedDelay = 60_000)
    public void reloadPolicies(){
        System.out.println("Reloading Policies...");
        initializePolicies();
    }

    public void initializePolicies() {
        Map<String, RoutePolicy> pingPolicies = new HashMap<>();
        pingPolicies.put("free",
                new RoutePolicy(5, 60_000, "slidingWindow", 0.1));
        pingPolicies.put("pro",
                new RoutePolicy(20, 60_000, "slidingWindow", 0.5));
        pingPolicies.put("enterprise",
                new RoutePolicy(100, 60_000, "slidingWindow", 1.0));
        policies.put("/ping", pingPolicies);

        Map<String, RoutePolicy> searchPolicies = new  HashMap<>();
        searchPolicies.put("free",
                new RoutePolicy(5, 60_000, "fixedWindow", 0.1));
        searchPolicies.put("pro",
                new RoutePolicy(15, 60_000, "fixedWindow", 0.3));
        searchPolicies.put("enterprise",
                new RoutePolicy(50, 60_000, "fixedWindow", 1.0));
        policies.put("/api/search", searchPolicies);

        Map<String, RoutePolicy> loginPolicies = new HashMap<>();
        loginPolicies.put("free",
                new RoutePolicy(3, 60_000, "tokenBucket", 0.05));
        loginPolicies.put("pro",
                new RoutePolicy(10, 60_000, "tokenBucket", 0.1));
        loginPolicies.put("enterprise",
                new RoutePolicy(50, 60_000, "tokenBucket", 0.5));
        policies.put("/api/login", loginPolicies);

        proxyRoutes.put("/api/users",
                new ProxyRoute("http://localhost:8080/backend/users"));
        proxyRoutes.put("/api/search",
                new ProxyRoute("http://localhost:8080/backend/search"));
    }

    public RoutePolicy getPolicyForRoute(String path, String tier){
        Map<String, RoutePolicy> tierPolicies = policies.get(path);

        if(tierPolicies == null){
            return getDefaultPolicy(tier);
        }

        RoutePolicy policy = tierPolicies.get(tier);

        if(policy == null){
            return getDefaultPolicy(tier);
        }

        return policy;
    }

    private RoutePolicy getDefaultPolicy(String tier){
        return switch (tier) {
            case "pro" -> new RoutePolicy(20, 60_000, "slidingWindow", 0.5);
            case "enterprise" -> new RoutePolicy(100, 60_000, "slidingWindow", 1.0);
            default -> new RoutePolicy(5, 60_000, "slidingWindow", 0.1);
        };
    }

    public ProxyRoute getProxyRoute(String path){
        return proxyRoutes.get(path);
    }
}