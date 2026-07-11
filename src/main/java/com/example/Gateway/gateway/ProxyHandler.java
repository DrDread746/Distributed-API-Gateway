package com.example.Gateway.gateway;

import com.example.Gateway.model.RateLimitInfo;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

@Component
public class ProxyHandler {

    private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);

    private final RestClient restClient = RestClient.create();

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private LoadBalancer loadBalancer;

    private static final Set<String> BLOCKED_REQUEST_HEADERS = Set.of(
            "x-api-key", "transfer-encoding", "host"
    );

    private static final Set<String> BLOCKED_RESPONSE_HEADERS = Set.of(
            "transfer-encoding", "connection"
    );

    /**
     * Forwards the request to an upstream chosen by the LoadBalancer,
     * wrapped in a per-upstream CircuitBreaker.
     *
     * Flow:
     * 1. LoadBalancer picks an upstream (round-robin, skipping any excluded one)
     * 2. Get or create a CircuitBreaker for that specific upstream URL
     * 3. If CB is OPEN → CallNotPermittedException is thrown immediately (no network call)
     * 4. If CB is CLOSED/HALF_OPEN → attempt the call
     * 5. On failure → CB records it, retry with next upstream in pool
     * 6. If all upstreams fail → 502
     *
     * Each upstream URL gets its own CB so one dead instance doesn't
     * affect the others. CB names are derived from the URL so they're
     * stable across requests and visible in Prometheus metrics.
     */
    public boolean forward(ProxyRoute route,
                           String requestPath,
                           String requestId,
                           String tier,
                           RateLimitInfo rateLimitInfo,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {

        List<String> upstreams = route.getUpstreamUrls();
        String excludeUrl = null;

        // Try each upstream at most once
        for (int attempt = 0; attempt < upstreams.size(); attempt++) {
            String baseUrl = loadBalancer.pick(route, excludeUrl);

            if (baseUrl == null) {
                // All upstreams exhausted or open-circuited
                break;
            }

            String targetUrl = route.resolveUpstreamUrl(baseUrl, requestPath);

            // CB is keyed by upstream base URL — stable, unique per instance
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(
                    cbName(baseUrl),
                    "default"   // uses the config block in application.properties
            );

            log.debug("[{}] attempt={} upstream={} cb-state={}",
                    requestId, attempt + 1, baseUrl, cb.getState());

            try {
                // CircuitBreaker.executeCheckedSupplier() does three things:
                // - Throws CallNotPermittedException immediately if CB is OPEN
                // - Records success/failure for the sliding window
                // - Transitions CB state when thresholds are crossed
                ResponseEntity<byte[]> backendResponse = cb.executeCheckedSupplier(
                        () -> callUpstream(targetUrl, tier, requestId, request)
                );

                writeResponse(response, backendResponse, rateLimitInfo);
                return false;

            } catch (CallNotPermittedException e) {
                // CB is OPEN — this upstream is known-bad, skip it immediately
                // without making a network call. Try the next one.
                log.warn("[{}] Circuit OPEN for upstream={}, trying next", requestId, baseUrl);
                excludeUrl = baseUrl;

            } catch (Throwable e) {
                // Actual failure — CB has recorded it. Try the next upstream.
                log.error("[{}] Upstream {} failed: {}", requestId, baseUrl, e.getMessage());
                excludeUrl = baseUrl;
            }
        }

        // All upstreams failed or open-circuited
        writeBadGateway(requestId, response);
        return false;
    }

    /**
     * Makes the actual HTTP call to the upstream.
     * Extracted so it can be passed as a lambda to CircuitBreaker.executeCheckedSupplier().
     */
    private ResponseEntity<byte[]> callUpstream(String targetUrl,
                                                String tier,
                                                String requestId,
                                                HttpServletRequest request) throws IOException {
        String queryString = request.getQueryString();
        String fullUrl = queryString != null ? targetUrl + "?" + queryString : targetUrl;
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        return restClient
                .method(method)
                .uri(fullUrl)
                .headers(headers -> {
                    Enumeration<String> headerNames = request.getHeaderNames();
                    if (headerNames != null) {
                        Collections.list(headerNames).forEach(name -> {
                            if (!BLOCKED_REQUEST_HEADERS.contains(name.toLowerCase())) {
                                headers.add(name, request.getHeader(name));
                            }
                        });
                    }
                    String clientIp = request.getRemoteAddr();
                    String existingForwardedFor = request.getHeader("X-Forwarded-For");
                    headers.set("X-Forwarded-For",
                            existingForwardedFor != null
                                    ? existingForwardedFor + ", " + clientIp
                                    : clientIp);
                    headers.set("X-Gateway-Request-ID", requestId);
                    headers.set("X-Tier", tier);
                })
                .body(readBody(request))
                .retrieve()
                .onStatus(status -> true, (req, res) -> {})
                .toEntity(byte[].class);
    }

    private void writeResponse(HttpServletResponse response,
                               ResponseEntity<byte[]> backendResponse,
                               RateLimitInfo rateLimitInfo) throws IOException {
        int statusCode = backendResponse.getStatusCode().value();
        response.setStatus(statusCode);

        backendResponse.getHeaders().forEach((name, values) -> {
            if (!BLOCKED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                values.forEach(value -> response.addHeader(name, value));
            }
        });

        writeRateLimitHeaders(response, rateLimitInfo, statusCode);

        byte[] body = backendResponse.getBody();
        if (body != null && body.length > 0) {
            try (OutputStream out = response.getOutputStream()) {
                out.write(body);
            }
        }
    }

    private void writeRateLimitHeaders(HttpServletResponse response,
                                       RateLimitInfo info,
                                       int statusCode) {
        if (info == null) return;
        response.setHeader("X-RateLimit-Limit", String.valueOf(info.getLimit()));
        if (info.getRemaining() >= 0) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(info.getRemaining()));
        }
        if (statusCode == 429) {
            response.setHeader("Retry-After", String.valueOf(info.getRetryAfterSeconds()));
        }
    }

    private void writeBadGateway(String requestId, HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.setStatus(HttpStatus.BAD_GATEWAY.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"All upstream instances unavailable\"," +
                            "\"requestId\":\"" + requestId + "\"}"
            );
        }
    }

    private byte[] readBody(HttpServletRequest request) throws IOException {
        String method = request.getMethod().toUpperCase();
        if ("GET".equals(method) || "DELETE".equals(method)
                || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return null;
        }
        try (InputStream in = request.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * Derives a stable, readable CB name from an upstream URL.
     * "http://localhost:8081/backend" → "localhost-8081-backend"
     * Avoids special characters that confuse Prometheus label parsers.
     */
    private String cbName(String url) {
        return url.replaceAll("https?://", "")
                .replaceAll("[/:.]+", "-")
                .replaceAll("-$", "");
    }
}