package com.example.Gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class PingController {
    @GetMapping("/ping")
    public Map<String,String> ping() {
        return Collections.singletonMap("message","this is a message");
    }

    @GetMapping("api/search")
    public Map<String, String> search() {
        return Collections.singletonMap("results","search results");
    }

    @GetMapping("/api/login")
    public Map<String,String> login() {
        return  Collections.singletonMap("status","login endpoint");
    }

}
