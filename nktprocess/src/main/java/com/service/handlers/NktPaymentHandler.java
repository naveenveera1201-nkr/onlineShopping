package com.service.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.NktDynamicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles payment operations requiring custom gateway / webhook logic.
 *
 * Keys: PAYMENT_INITIATE, PAYMENT_WEBHOOK
 */
@Component
@Slf4j
public class NktPaymentHandler {

    private String str(Map<String, Object> d, String k) {
        Object v = d.get(k); return v == null ? null : v.toString();
    }

    private String json(ObjectMapper m, Object o) {
        try { return m.writeValueAsString(o); }
        catch (Exception e) { return "{\"error\":\"serialisation failed\"}"; }
    }

    /* ── PAYMENT_INITIATE ───────────────────────────────────────────────── */
    public NktOperationHandler initiatePayment() {
        return (data, userId, repo, mapper, def) -> {
            String method   = str(data, "method");
            String orderId  = str(data, "orderId");
            String upiId    = str(data, "upiId");
            String bankCode = str(data, "bankCode");
            long   amount   = data.get("amount") != null ? Long.parseLong(str(data, "amount")) : 0L;

            String redirectUrl = "upi".equals(method)
                    ? "upi://pay?pa=" + upiId + "&am=" + (amount / 100.0)
                    : "https://netbanking.example.com/redirect?bank=" + bankCode;

            Map<String, Object> payment = new LinkedHashMap<>();
            payment.put("orderId",       orderId);
            payment.put("customerId",    userId);
            payment.put("amount",        amount);
            payment.put("method",        method);
            payment.put("upiId",         upiId);
            payment.put("bankCode",      bankCode);
            payment.put("status",        "pending");
            payment.put("currentStatus", "pending");
            payment.put("redirectUrl",   redirectUrl);
            payment.put("createdAt",     LocalDateTime.now().toString());
            payment.put("updatedAt",     LocalDateTime.now().toString());
            return json(mapper, repo.insert("payments", payment));
        };
    }

    /* ── PAYMENT_WEBHOOK ────────────────────────────────────────────────── */
    public NktOperationHandler paymentWebhook() {
        return (data, userId, repo, mapper, def) -> {
            // TODO: verify signature, update payment & order status
            String gatewayId = str(data, "paymentId");
            String status    = str(data, "status");
            return json(mapper, Map.of(
                    "received",         "true",
                    "gatewayPaymentId", gatewayId,
                    "status",           status));
        };
    }
}
