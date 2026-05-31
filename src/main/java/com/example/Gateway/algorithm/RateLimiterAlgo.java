package com.example.Gateway.algorithm;

import com.example.Gateway.model.RoutePolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface RateLimiterAlgo {
    boolean isAllowed(String clientIp,
                      String route,
                      RoutePolicy policy,
                      HttpServletRequest request,
                      HttpServletResponse response) throws Exception;
}