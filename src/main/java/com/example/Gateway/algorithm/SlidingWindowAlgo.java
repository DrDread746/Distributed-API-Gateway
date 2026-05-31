package com.example.Gateway.algorithm;

import com.example.Gateway.model.RoutePolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("slidingWindow")
public class SlidingWindowAlgo implements RateLimiterAlgo {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_SIZE_MS = 60_000;

    @Override
    public boolean isAllowed(String clientIp,
                             String route,
                             RoutePolicy policy,
                             HttpServletRequest request,
                             HttpServletResponse response) throws Exception {

        String key = "sliding:" + route + ":" + clientIp;
        long currTime = System.currentTimeMillis();
        long windowStart = currTime - policy.getWindowSizeMs();

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        Long count = redisTemplate.opsForZSet().zCard(key);

        if(count != null && count >= policy.getMaxRequests()) {
            response.setStatus(429);
            return false;
        }

        String member = currTime + ":" + UUID.randomUUID();
        redisTemplate.opsForZSet().add(key, member, currTime);
        return true;
    }
}