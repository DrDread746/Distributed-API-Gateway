package com.example.Gateway.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthMonitor.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private volatile boolean redisHealthy = true;

    @Scheduled(fixedDelay = 5000) // checking every 5 seconds
    public void checkHealth() {
        try {
            redisTemplate.opsForValue().set("health:check", "ok");
            if(!redisHealthy){
                log.info("Redis recovered — switching back to distributed limiting");
                redisHealthy = true;
            }
        } catch (Exception e) {
            if(redisHealthy){
                log.error("Redis is DOWN — falling back to local rate limiting");
                redisHealthy = false;
            }
        }
    }

    public boolean isHealthy() {
        return redisHealthy;
    }

}
