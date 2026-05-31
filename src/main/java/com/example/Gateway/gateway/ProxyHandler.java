package com.example.Gateway.gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Component
public class ProxyHandler {
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean forward(String targetUrl, HttpServletRequest request,  HttpServletResponse response) throws IOException {
        try{
            String backendResponse = restTemplate.getForObject(targetUrl, String.class);

            response.setStatus(200);
            response.setContentType("application/json");
            assert backendResponse != null;
            response.getWriter().write(backendResponse);

            return false;
        }
        catch(Exception e){
            response.setStatus(502);
            response.getWriter().write("Backend service unavailable");
            return false;
        }
    }

}
