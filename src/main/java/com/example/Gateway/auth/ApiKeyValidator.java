package com.example.Gateway.auth;

import com.example.Gateway.model.ApiKey;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ApiKeyValidator {

    private final Map<String, ApiKey> validKeys = new HashMap<>();

    public ApiKeyValidator() {
        validKeys.put("free-key-123",
                new ApiKey("free-key-123", "user1", "free"));
        validKeys.put("pro-key-456",
                new ApiKey("pro-key-456", "user2", "pro"));
        validKeys.put("enterprise-key-789",
                new ApiKey("enterprise-key-789", "user3", "enterprise"));
    }

    public ApiKey validate(String key){
        return validKeys.get(key);
    }
}
