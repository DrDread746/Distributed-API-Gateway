package com.example.Gateway.auth;

import com.example.Gateway.model.ApiKey;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ApiKeyValidator {

    private final Map<String, ApiKey> validKeys = new HashMap<>();

    public ApiKeyValidator() {

        // 10 free tier users
        for (int i = 0; i < 10; i++) {
            String key = i == 0 ? "free-key-123" : "free-key-" + (123 + i);
            validKeys.put(key, new ApiKey(key, "free-user-" + (i + 1), "free"));
        }

        // 5 pro tier users
        for (int i = 0; i < 5; i++) {
            String key = i == 0 ? "pro-key-456" : "pro-key-" + (456 + i);
            validKeys.put(key, new ApiKey(key, "pro-user-" + (i + 1), "pro"));
        }

        // 5 enterprise tier users
        for (int i = 0; i < 5; i++) {
            String key = i == 0 ? "enterprise-key-789" : "enterprise-key-" + (789 + i);
            validKeys.put(key, new ApiKey(key, "enterprise-user-" + (i + 1), "enterprise"));
        }
    }

    public ApiKey validate(String key) {
        return validKeys.get(key);
    }
}