package com.example.Gateway.model;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestContext {
    private final String requestId;
    private final long startTime;
    private String route;
    private String tier;
    private String owner;
    private String algorithm;
    private boolean allowed;
    private int statusCode;

    public RequestContext(String requestId){
        this.requestId = requestId;
        this.startTime = System.currentTimeMillis();
    }

    public long getLatencyMs(){
        return System.currentTimeMillis() - startTime;
    }
}
