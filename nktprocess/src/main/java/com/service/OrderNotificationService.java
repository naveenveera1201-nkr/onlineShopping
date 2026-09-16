package com.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.repository.NktDynamicRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Order-domain notification composer.
 *
 * Owns the WORDING and TARGETING rules for order-lifecycle push
 * notifications ("Order placed successfully", "New order ORD10001
 * received", ...). It knows about orders / stores / store-staff employees;
 * it knows NOTHING about the Firebase Admin SDK — all of that lives in
 * {@link FirebaseNotificationService} / {@link NotificationDispatchService}.
 * This keeps order business logic OUT of the Firebase layer, per the
 * integration brief ("do not hard-code order business logic inside the
 * Firebase service").
 *
 * Called from {@link com.service.handlers.NktOrderHandler} right after an
 * order is inserted or its status changes. Every call swallows and logs its
 * own exceptions so a notification failure can NEVER break the order API
 * response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final NktDynamicRepository repo;
    private final NotificationDispatchService dispatch;

    private static final String SCREEN = "order_details";
    private static final String TYPE_ORDER = "ORDER";

    /** Customer placed a new order — notify the customer, active store staff and the store owner. */
    public void notifyOrderPlaced(Map<String, Object> order) {
        try {
            String orderId = ref(order);
            String storeId = str(order, "storeId");
            String customerId = str(order, "userId");

            Map<String, Object> data = orderData(orderId, storeId);

            if (customerId != null) {
                dispatch.notifyUser(customerId, "customer", storeId,
                        "Order placed successfully",
                        "Your order " + orderId + " has been placed successfully.",
                        TYPE_ORDER, data, true);
            }

            if (storeId != null) {
                String body = "New order " + orderId + " received";
                dispatch.notifyUsers(storeStaffAndOwner(storeId), "New Order", body, TYPE_ORDER, data, true);
            }
        } catch (Exception e) {
            log.warn("notifyOrderPlaced failed (non-fatal) for order {}: {}", order.get("orderId"), e.getMessage());
        }
    }

    /** Order status changed (accepted / partially accepted / dispatched / delivered / cancelled). */
    public void notifyOrderStatusChanged(Map<String, Object> order, String newStatus) {
        try {
            String orderId = ref(order);
            String storeId = str(order, "storeId");
            String customerId = str(order, "userId");
            if (customerId == null) return;

            Map<String, Object> data = orderData(orderId, storeId);
            String[] titleBody = messageFor(newStatus, orderId);

            dispatch.notifyUser(customerId, "customer", storeId, titleBody[0], titleBody[1],
                    TYPE_ORDER, data, true);
        } catch (Exception e) {
            log.warn("notifyOrderStatusChanged failed (non-fatal) for order {}: {}",
                    order.get("orderId"), e.getMessage());
        }
    }

    /** Delivery agent assigned to an order — notify that agent directly (used once agent accounts exist). */
    public void notifyDeliveryAssigned(Map<String, Object> order, String agentUserId) {
        if (agentUserId == null) return;
        try {
            String orderId = ref(order);
            String storeId = str(order, "storeId");
            dispatch.notifyUser(agentUserId, "employee", storeId,
                    "Delivery assigned",
                    "Order " + orderId + " assigned to you",
                    TYPE_ORDER, orderData(orderId, storeId), true);
        } catch (Exception e) {
            log.warn("notifyDeliveryAssigned failed (non-fatal): {}", e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private List<NotificationDispatchService.Recipient> storeStaffAndOwner(String storeId) {
        List<NotificationDispatchService.Recipient> recipients = new ArrayList<>();

        List<Map<String, Object>> employees = repo.findAll("store_staff_employees", Map.of("storeId", storeId));
        for (Map<String, Object> emp : employees) {
            String status = str(emp, "status");
            // Only currently-Active staff — a revoked/released employee must not be notified.
            if (status != null && status.equalsIgnoreCase("Active")) {
                recipients.add(new NotificationDispatchService.Recipient(str(emp, "employeeId"), "employee", storeId));
            }
        }

        repo.findOne("stores", "storeId", storeId).ifPresent(store ->
                recipients.add(new NotificationDispatchService.Recipient(str(store, "userId"), "business", storeId)));

        return recipients;
    }

    private String[] messageFor(String status, String orderId) {
        String s = status == null ? "" : status.toLowerCase();
        return switch (s) {
            case "accepted" -> new String[]{"Order Accepted", "Your order " + orderId + " has been accepted by the store."};
            case "partially accepted" -> new String[]{"Order Partially Accepted", "Your order " + orderId + " was partially accepted by the store."};
            case "dispatched" -> new String[]{"Order Dispatched", "Your order " + orderId + " is on the way."};
            case "delivered" -> new String[]{"Order Delivered", "Your order " + orderId + " has been delivered. Enjoy!"};
            case "cancelled" -> new String[]{"Order Cancelled", "Your order " + orderId + " has been cancelled."};
            default -> new String[]{"Order Update", "Your order " + orderId + " status is now " + status + "."};
        };
    }

    private Map<String, Object> orderData(String orderId, String storeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId);
        data.put("storeId", storeId);
        data.put("screen", SCREEN);
        return data;
    }

    private String ref(Map<String, Object> order) {
        Object ref = order.get("orderRef") != null ? order.get("orderRef") : order.get("orderId");
        return ref == null ? "" : ref.toString();
    }

    private String str(Map<String, Object> d, String k) {
        Object v = d.get(k);
        return v == null ? null : v.toString();
    }
}
