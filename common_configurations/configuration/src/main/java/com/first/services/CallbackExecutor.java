package com.first.services;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.first.dto.CallbackConfig;

@Service
public class CallbackExecutor {

    @Autowired
    private RestTemplate restTemplate;

    public void execute(CallbackConfig callback, Map<String, Object> data) {
        switch (callback.getType().toUpperCase()) {
            case "WEBHOOK":
                executeWebhook(callback, data);
                break;
            case "EMAIL":
                sendEmail(callback, data);
                break;
            case "SMS":
                sendSMS(callback, data);
                break;
        }
    }

    private void executeWebhook(CallbackConfig callback, Map<String, Object> data) {
        try {
            restTemplate.postForEntity(callback.getUrl(), data, String.class);
        } catch (Exception e) {
            // Log but don't fail the main request
            System.err.println("Webhook failed: " + e.getMessage());
        }
    }

    private void sendEmail(CallbackConfig callback, Map<String, Object> data) {
        // Implement email sending logic
        System.out.println("Sending email to: " + callback.getRecipient());
    }

    private void sendSMS(CallbackConfig callback, Map<String, Object> data) {
        // Implement SMS sending logic
        System.out.println("Sending SMS to: " + callback.getRecipient());
    }
}