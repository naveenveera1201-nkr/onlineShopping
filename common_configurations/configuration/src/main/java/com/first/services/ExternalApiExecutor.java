package com.first.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.first.dto.BusinessLogicConfig;

import java.util.HashMap;
import java.util.Map;

@Service
public class ExternalApiExecutor {

    @Autowired
    private RestTemplate restTemplate;

    public Map<String, Object> execute(BusinessLogicConfig config,
                                       Map<String, Object> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

        try {
            HttpMethod method = HttpMethod.valueOf(config.getMethod());
            ResponseEntity<Map> response = restTemplate.exchange(
                    config.getEndpoint(), method, entity, Map.class);

            return response.getBody() != null ? response.getBody() : new HashMap<>();

        } catch (Exception e) {
            // Handle retry if configured
            if (config.getRetryPolicy() != null &&
                    config.getRetryPolicy().isEnabled()) {
                return executeWithRetry(config, params);
            }
            throw new RuntimeException("External API call failed: " + e.getMessage());
        }
    }

    private Map<String, Object> executeWithRetry(BusinessLogicConfig config,
                                                 Map<String, Object> params) {
        int attempts = 0;
        int maxAttempts = config.getRetryPolicy().getMaxAttempts();
        int backoff = 1000;

        while (attempts < maxAttempts) {
            try {
                return execute(config, params);
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxAttempts) {
                    throw new RuntimeException("Max retry attempts reached");
                }
                try {
                    Thread.sleep(backoff);
                    backoff *= config.getRetryPolicy().getBackoffMultiplier();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return new HashMap<>();
    }
}