package com.example.Gateway.model;

import lombok.Getter;

/**
 * Carries rate limit state from the interceptor into ProxyHandler
 * so response headers can be written accurately.
 * These become the X-RateLimit-* headers the client sees on every response,
 * and Retry-After on 429s — standard practice across AWS, Stripe, GitHub APIs.
 */
public class RateLimitInfo {

    @Getter
    private final int limit;       // max requests allowed in the window
    @Getter
    private final int remaining;    // requests left in current window
    private final long resetAfterMs; // ms until the window resets

    public RateLimitInfo(int limit, int remaining, long resetAfterMs) {
        this.limit = limit;
        this.remaining = remaining;
        this.resetAfterMs = resetAfterMs;
    }


    /** Seconds until window reset, rounded up. Used for Retry-After header. */
    public int getRetryAfterSeconds() {
        return (int) Math.ceil(resetAfterMs / 1000.0);
    }

    /** Convenience: build from a RoutePolicy when we only know the cap, not remaining. */
    public static RateLimitInfo fromPolicy(RoutePolicy policy, int remaining) {
        return new RateLimitInfo(policy.getMaxRequests(), remaining, policy.getWindowSizeMs());
    }

    /** Fallback when we're on local limiter and don't have precise state. */
    public static RateLimitInfo unknown(RoutePolicy policy) {
        return new RateLimitInfo(policy.getMaxRequests(), -1, policy.getWindowSizeMs());
    }
}