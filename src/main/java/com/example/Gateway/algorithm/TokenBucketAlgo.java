package com.example.Gateway.algorithm;

import com.example.Gateway.model.RoutePolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component("tokenBucket")
public class TokenBucketAlgo implements RateLimiterAlgo {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    private static final int MAX_TOKENS = 5;
    private static final double REFILL_RATE = 0.1; // 1 token per 10 seconds

    private static final String LUA_SCRIPT =
            "local tokens = redis.call('HGET', KEYS[1], 'tokens') " +
                    "local lastRefill = redis.call('HGET', KEYS[1], 'lastRefill') " +
                    "local currentTime = tonumber(ARGV[1]) " +
                    "local maxTokens = tonumber(ARGV[2]) " +
                    "local refillRate = tonumber(ARGV[3]) " +

                    // new user - initialize bucket
                    "if tokens == false then " +
                    "  redis.call('HSET', KEYS[1], 'tokens', maxTokens - 1) " +
                    "  redis.call('HSET', KEYS[1], 'lastRefill', currentTime) " +
                    "  return 1 " +
                    "end " +

                    // calculate refill
                    "local currentTokens = tonumber(tokens) " +
                    "local lastRefillTime = tonumber(lastRefill) " +
                    "local elapsed = (currentTime - lastRefillTime) / 1000 " +
                    "local newTokens = math.min(currentTokens + (elapsed * refillRate), maxTokens) " +

                    // check and consume
                    "if newTokens < 1 then " +
                    "  return 0 " +
                    "end " +

                    // save and allow
                    "redis.call('HSET', KEYS[1], 'tokens', newTokens - 1) " +
                    "redis.call('HSET', KEYS[1], 'lastRefill', currentTime) " +
                    "return 1";


    public TokenBucketAlgo() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptText(LUA_SCRIPT);
        rateLimitScript.setResultType(Long.class);
    }

    @Override
    public boolean isAllowed(String clientIp,
                             String route,
                             RoutePolicy policy,
                             HttpServletRequest request,
                             HttpServletResponse response) throws Exception {


        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList("token_bucket:" + route + ":" + clientIp),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(policy.getMaxRequests()),
                String.valueOf(policy.getRefillRate())
        );

        if(result == null || result == 0L) {
            response.setStatus(429);
            return false;
        }
        return true;
    }
}