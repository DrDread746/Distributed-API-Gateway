package com.example.Gateway.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a proxy route as a serializable model.
 *
 * Used for:
 * - Admin API request/response body (JSON over HTTP)
 * - Redis storage (serialized as JSON string in a Redis hash)
 *
 * Keeping this separate from ProxyRoute (the runtime object) is intentional —
 * RouteDefinition is a data transfer/storage model, ProxyRoute is the
 * operational object with behavior (resolveUpstreamUrl, etc).
 */
public class RouteDefinition {

    private final String prefix;
    private final List<String> upstreamUrls;
    private final boolean stripPrefix;

    // Algorithm and limits per tier — optional, falls back to defaults if absent
    private final String algorithm;   // "slidingWindow", "fixedWindow", "tokenBucket"
    private final int freeLimit;
    private final int proLimit;
    private final int enterpriseLimit;
    private final long windowSizeMs;

    @JsonCreator
    public RouteDefinition(
            @JsonProperty("prefix")         String prefix,
            @JsonProperty("upstreamUrls")   List<String> upstreamUrls,
            @JsonProperty("stripPrefix")    boolean stripPrefix,
            @JsonProperty("algorithm")      String algorithm,
            @JsonProperty("freeLimit")      int freeLimit,
            @JsonProperty("proLimit")       int proLimit,
            @JsonProperty("enterpriseLimit") int enterpriseLimit,
            @JsonProperty("windowSizeMs")   long windowSizeMs) {
        this.prefix          = prefix;
        this.upstreamUrls    = upstreamUrls;
        this.stripPrefix     = stripPrefix;
        this.algorithm       = algorithm != null ? algorithm : "slidingWindow";
        this.freeLimit       = freeLimit > 0 ? freeLimit : 5;
        this.proLimit        = proLimit > 0 ? proLimit : 20;
        this.enterpriseLimit = enterpriseLimit > 0 ? enterpriseLimit : 100;
        this.windowSizeMs    = windowSizeMs > 0 ? windowSizeMs : 60_000;
    }

    public String getPrefix()              { return prefix; }
    public List<String> getUpstreamUrls() { return upstreamUrls; }
    public boolean isStripPrefix()         { return stripPrefix; }
    public String getAlgorithm()           { return algorithm; }
    public int getFreeLimit()              { return freeLimit; }
    public int getProLimit()               { return proLimit; }
    public int getEnterpriseLimit()        { return enterpriseLimit; }
    public long getWindowSizeMs()          { return windowSizeMs; }
}