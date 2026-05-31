package com.example.Gateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RoutePolicy {
    private final int maxRequests;
    private final long windowSizeMs;
    private final String algorithm;
    private final double refillRate;
}