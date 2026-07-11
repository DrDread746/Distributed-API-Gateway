package com.example.Gateway.gateway;

import lombok.Getter;

import java.util.List;

/**
 * Defines a gateway route: which incoming prefix maps to which
 * upstream backend instances.
 *
 * Holds a pool of upstream URLs instead of a single targetUrl —
 * the LoadBalancer picks one per request, and each gets its own
 * circuit breaker so a single dead instance doesn't affect the others.
 */
@Getter
public class ProxyRoute {

    /**
     * Gateway-side prefix to match.
     * Example: "/api/users"
     */
    private final String prefix;

    /**
     * List of backend service instances.
     * Example:
     * [
     *   "http://localhost:8081",
     *   "http://localhost:8082",
     *   "http://localhost:8083"
     * ]
     */
    private final List<String> upstreamUrls;

    /**
     * Whether to remove the gateway prefix before forwarding.
     */
    private final boolean stripPrefix;

    public ProxyRoute(String prefix, List<String> upstreamUrls, boolean stripPrefix) {
        this.prefix = prefix;
        this.upstreamUrls = upstreamUrls;
        this.stripPrefix = stripPrefix;
    }

    /**
     * Resolves the full upstream URL for a given request path.
     *
     * The baseUrl is selected by the LoadBalancer before this method is called.
     *
     * Example (stripPrefix = true):
     *   prefix      = /api/users
     *   baseUrl     = http://localhost:8082
     *   requestPath = /api/users/42
     *   result      = http://localhost:8082/42
     *
     * Example (stripPrefix = false):
     *   prefix      = /api/search
     *   baseUrl     = http://localhost:8081
     *   requestPath = /api/search?q=hello
     *   result      = http://localhost:8081/api/search
     */
    public String resolveUpstreamUrl(String baseUrl, String requestPath) {
        if (stripPrefix) {
            String tail = requestPath.substring(prefix.length());
            return baseUrl + (tail.isEmpty() ? "/" : tail);
        }

        return baseUrl + requestPath;
    }
}

//continue from here and make the rest of the changes