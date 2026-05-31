package com.example.Gateway.gateway;


import lombok.Getter;

@Getter
public class ProxyRoute {
    private final String targetUrl;

    public ProxyRoute(String targetUrl){
        this.targetUrl = targetUrl;
    }
}
