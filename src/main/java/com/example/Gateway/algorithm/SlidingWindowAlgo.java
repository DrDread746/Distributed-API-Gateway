package com.example.Gateway.algorithm;

import com.example.Gateway.model.RoutePolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

@Component("slidingWindow")
public class SlidingWindowAlgo implements RateLimiterAlgo {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String LUA_SCRIPT =
            // Step 1: remove entries outside the current window
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[2]) " +

                    // Step 2: count remaining entries in the window
                    "local count = redis.call('ZCARD', KEYS[1]) " +

                    // Step 3: reject if at or over limit
                    "if count >= tonumber(ARGV[3]) then " +
                    "  return 0 " +
                    "end " +

                    // Step 4: admit request — add with current timestamp as score
                    "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4]) " +

                    // Step 5: set TTL so idle keys don't accumulate in Redis forever
                    "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5])) " +

                    "return 1";

    private final DefaultRedisScript<Long> slidingWindowScript;

    public SlidingWindowAlgo() {
        slidingWindowScript = new DefaultRedisScript<>();
        slidingWindowScript.setScriptText(LUA_SCRIPT);
        slidingWindowScript.setResultType(Long.class);
    }

    @Override
    public boolean isAllowed(String clientId,
                             String route,
                             RoutePolicy policy,
                             HttpServletRequest request,
                             HttpServletResponse response) throws Exception {

        String key = "sliding:" + route + ":" + clientId;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - policy.getWindowSizeMs();

        // TTL slightly larger than the window so Redis cleans up
        // keys shortly after they'd naturally expire anyway.
        long ttlSeconds = (policy.getWindowSizeMs() / 1000) + 10;

        // Unique member: timestamp + UUID prevents score collisions
        // when two requests arrive within the same millisecond.
        String member = currentTime + ":" + UUID.randomUUID();

        Long result = redisTemplate.execute(
                slidingWindowScript,
                Collections.singletonList(key),
                String.valueOf(currentTime),  // ARGV[1]
                String.valueOf(windowStart),  // ARGV[2]
                String.valueOf(policy.getMaxRequests()), // ARGV[3]
                member,                       // ARGV[4]
                String.valueOf(ttlSeconds)    // ARGV[5]
        );

        if (result == null || result == 0L) {
            response.setStatus(429);
            return false;
        }
        return true;
    }
}