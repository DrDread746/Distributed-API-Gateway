package com.example.Gateway.algorithm;

import com.example.Gateway.model.RoutePolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component("fixedWindow")
public class FixedWindowAlgo implements RateLimiterAlgo {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_SIZE_SECONDS = 60;

    @Override
    public boolean isAllowed(String clientIp,
                             String route,
                             RoutePolicy policy,
                             HttpServletRequest request,
                             HttpServletResponse response) throws Exception {

        String key = "fixed:" + route + ":" + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);

        if(count == 1) {
            redisTemplate.expire(key, policy.getWindowSizeMs(), TimeUnit.MILLISECONDS);
        }

        if(count > policy.getMaxRequests()) {
            response.setStatus(429);
            return false;
        }
        return true;
    }
}