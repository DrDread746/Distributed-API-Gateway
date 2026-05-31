package com.example.Gateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ApiKey {
    private final String key;
    private final String owner;
    private final String tier;
}
