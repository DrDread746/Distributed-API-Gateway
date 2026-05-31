package com.example.Gateway.logger;

import com.example.Gateway.model.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GatewayLogger {

    private static final Logger log = LoggerFactory.getLogger(GatewayLogger.class);

    public void logRequest(RequestContext ctx){
        if(ctx.isAllowed()){
            log.info("[{}] route={} tier={} owner={} algo={} status={} latency={}ms ALLOWED",
                    ctx.getRequestId(),
                    ctx.getRoute(),
                    ctx.getTier(),
                    ctx.getOwner(),
                    ctx.getAlgorithm(),
                    ctx.getStatusCode(),
                    ctx.getLatencyMs()
            );
        } else {
            log.warn("[{}] route={} tier={} owner={} algo={} status={} latency={}ms BLOCKED",
                    ctx.getRequestId(),
                    ctx.getRoute(),
                    ctx.getTier(),
                    ctx.getOwner(),
                    ctx.getAlgorithm(),
                    ctx.getStatusCode(),
                    ctx.getLatencyMs()
            );
        }
    }

    public void logAuthFailure(String requestId, String reason){
        log.warn("[{}] AUTH FAILED reason={}",
                requestId, reason);
    }
}
