package com.example.Gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/backend")
public class BackendController {

    @GetMapping("/users")
    public Map<String, String> getUsers() {
        return Collections.singletonMap("service", "user-service response");
    }

    @GetMapping("/search")
    public Map<String, String> search(){
        return Collections.singletonMap("service", "search-service response");
    }
}
