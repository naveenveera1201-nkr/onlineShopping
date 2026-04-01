package com.service.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.NktDynamicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all order and wishlist operations.
 *
 * Keys: ORDER_VALIDATE_CART, ORDER_PLACE, ORDER_HISTORY, ORDER_GET_DETAIL,
 *       ORDER_TRACK, ORDER_CANCEL, ORDER_RATE,
 *       WISHLIST_ADD, WISHLIST_REMOVE,
 *       STORE_ORDER_LIST, STORE_ORDER_ACCEPT, STORE_ORDER_REJECT,
 *       STORE_ORDER_DISPATCH, STORE_ORDER_DELIVER
 */
@Component
@Slf4j
public class NktOrderHandler {

    private String str(Map<String, Object> d, String k) {
        Object v = d.get(k); return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> d, String k) {
        Object v = d.get(k);
        return v instanceof List ? (List<Map<String, Object>>) v : List.of();
    }

    private String json(ObjectMapper m, Object o) {
        try { return m.writeValueAsString(o); }
        catch (Exception e) { return "{\"error\":\"serialisation failed\"}"; }
    }

    private String resolveStoreId(String ownerId, NktDynamicRepository repo) {
        return repo.findOne("stores", "ownerId", ownerId)
                .map(s -> s.get("id").toString())
                .orElseThrow(() -> new RuntimeException("Store not found for owner"));
    }

    private Map<String, Object> getStoreOrder(String orderId, String storeId, NktDynamicRepository repo) {
        Map<String, Object> order = repo.findById("orders", orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        if (!storeId.equals(order.get("storeId"))) throw new RuntimeException("Unauthorized");
        return order;
    }

    /* ── ORDER_VALIDATE_CART ────────────────────────────────────────────── */
    public NktOperationHandler validateCart() {
        return (data, userId, repo, mapper) -> {
            List<Map<String, Object>> updated = new ArrayList<>();
            for (Map<String, Object> item : list(data, "items")) {
                String itemId = (String) item.get("itemId");
                Map<String, Object> si = repo.findById("stockItems", itemId)
                        .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
                Map<String, Object> out = new LinkedHashMap<>(item);
                out.put("currentPrice", si.get("price"));
                out.put("available",    si.get("available"));
                updated.add(out);
            }
            return json(mapper, Map.of("storeId", str(data, "storeId"), "items", updated, "valid", true));
        };
    }

    /* ── ORDER_PLACE ────────────────────────────────────────────────────── */
    public NktOperationHandler placeOrder() {
        return (data, userId, repo, mapper) -> {
            List<Map<String, Object>> orderItems = list(data, "items").stream().map(i -> {
                String itemId = (String) i.get("itemId");
                Map<String, Object> si = repo.findById("stockItems", itemId)
                        .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
                int qty = i.get("qty") != null ? Integer.parseInt(i.get("qty").toString()) : 1;
                Map<String, Object> oi = new LinkedHashMap<>();
                oi.put("itemId",   itemId);
                oi.put("itemName", si.get("name"));
                oi.put("qty",      qty);
                oi.put("price",    si.get("price"));
                return oi;
            }).collect(Collectors.toList());

            double total = orderItems.stream()
                    .mapToDouble(i -> Double.parseDouble(i.get("price").toString())
                            * Integer.parseInt(i.get("qty").toString())).sum();

            String now = LocalDateTime.now().toString();
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("customerId",      userId);
            order.put("storeId",         str(data, "storeId"));
            order.put("categoryId",      str(data, "categoryId"));
            order.put("addressId",       str(data, "addressId"));
            order.put("paymentMethod",   str(data, "paymentMethod"));
            order.put("appointmentSlot", str(data, "appointmentSlot"));
            order.put("notes",           str(data, "notes"));
            order.put("urgency",         str(data, "urgency"));
            order.put("orderType",       str(data, "orderType"));
            order.put("items",           orderItems);
            order.put("status",          "placed");
            order.put("currentStatus",   "placed");
            order.put("totalAmount",     total);
            order.put("createdAt",       now);
            order.put("createdBy",       userId);
            order.put("statusTimeline",  List.of(Map.of("status", "placed", "at", now)));
            return json(mapper, repo.insert("orders", order));
        };
    }

    /* ── ORDER_HISTORY ──────────────────────────────────────────────────── */
    public NktOperationHandler orderHistory() {
        return (data, userId, repo, mapper) -> {
            String status = str(data, "status");
            int page  = data.get("page")  != null ? Integer.parseInt(str(data, "page"))  : 0;
            int limit = data.get("limit") != null ? Integer.parseInt(str(data, "limit")) : 20;
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("customerId", userId);
            if (status != null) filter.put("status", status);
            return json(mapper, repo.findAllSorted("orders", filter,
                    "createdAt", Sort.Direction.DESC, page * limit, limit));
        };
    }

    /* ── ORDER_GET_DETAIL ───────────────────────────────────────────────── */
    public NktOperationHandler orderGetDetail() {
        return (data, userId, repo, mapper) -> {
            String orderId = str(data, "orderId");
            Map<String, Object> order = repo.findById("orders", orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!userId.equals(order.get("customerId"))) throw new RuntimeException("Unauthorized");
            return json(mapper, order);
        };
    }

    /* ── ORDER_TRACK ────────────────────────────────────────────────────── */
    public NktOperationHandler trackOrder() {
        return (data, userId, repo, mapper) -> {
            String orderId = str(data, "orderId");
            Map<String, Object> order = repo.findById("orders", orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!userId.equals(order.get("customerId"))) throw new RuntimeException("Unauthorized");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orderId", orderId);
            result.put("status",  order.get("status"));
            Object agent = order.get("deliveryAgent");
            if (agent instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ag = (Map<String, Object>) agent;
                result.put("agentLatitude",  ag.get("latitude"));
                result.put("agentLongitude", ag.get("longitude"));
            }
            return json(mapper, result);
        };
    }

    /* ── ORDER_CANCEL ───────────────────────────────────────────────────── */
    public NktOperationHandler cancelOrder() {
        return (data, userId, repo, mapper) -> {
            String orderId = str(data, "orderId");
            Map<String, Object> order = repo.findById("orders", orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!userId.equals(order.get("customerId"))) throw new RuntimeException("Unauthorized");
            if (!"placed".equals(order.get("status")))
                throw new RuntimeException("Order cannot be cancelled at status: " + order.get("status"));
            repo.updateById("orders", orderId,
                    Map.of("status", "cancelled", "currentStatus", "cancelled",
                           "updatedAt", LocalDateTime.now().toString()));
            return json(mapper, repo.findById("orders", orderId).orElseThrow());
        };
    }

    /* ── ORDER_RATE ─────────────────────────────────────────────────────── */
    public NktOperationHandler rateOrder() {
        return (data, userId, repo, mapper) -> {
            String orderId = str(data, "orderId");
            Map<String, Object> order = repo.findById("orders", orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (repo.exists("ratings", Map.of("orderId", orderId, "customerId", userId)))
                throw new RuntimeException("Order already rated");
            Map<String, Object> rating = new LinkedHashMap<>();
            rating.put("orderId",    orderId);
            rating.put("storeId",    order.get("storeId"));
            rating.put("customerId", userId);
            rating.put("rating",     Integer.parseInt(str(data, "rating")));
            rating.put("review",     str(data, "review"));
            rating.put("status",     "ACTIVE");
            rating.put("createdAt",  LocalDateTime.now().toString());
            return json(mapper, repo.insert("ratings", rating));
        };
    }

    /* ── WISHLIST_ADD ───────────────────────────────────────────────────── */
    public NktOperationHandler wishlistAdd() {
        return (data, userId, repo, mapper) -> {
            String itemId = str(data, "itemId");
            Map<String, Object> si = repo.findById("stockItems", itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
            return repo.findOneByCriteria("wishlist", Map.of("customerId", userId, "itemId", itemId))
                    .map(existing -> json(mapper, existing))
                    .orElseGet(() -> {
                        Map<String, Object> wi = new LinkedHashMap<>();
                        wi.put("customerId", userId);
                        wi.put("itemId",     itemId);
                        wi.put("itemName",   si.get("name"));
                        wi.put("price",      si.get("price"));
                        wi.put("available",  si.get("available"));
                        wi.put("status",     "ACTIVE");
                        wi.put("createdAt",  LocalDateTime.now().toString());
                        return json(mapper, repo.insert("wishlist", wi));
                    });
        };
    }

    /* ── WISHLIST_REMOVE ────────────────────────────────────────────────── */
    public NktOperationHandler wishlistRemove() {
        return (data, userId, repo, mapper) -> {
            String itemId = str(data, "itemId");
            Map<String, Object> wi = repo.findOneByCriteria("wishlist",
                    Map.of("customerId", userId, "itemId", itemId))
                    .orElseThrow(() -> new RuntimeException("Wishlist item not found"));
            repo.updateById("wishlist", wi.get("id").toString(),
                    Map.of("status", "DELETED"));
            return json(mapper, Map.of("message", "Item removed from wishlist"));
        };
    }

    /* ── STORE_ORDER_LIST ───────────────────────────────────────────────── */
    public NktOperationHandler storeOrderList() {
        return (data, userId, repo, mapper) -> {
            String storeId = resolveStoreId(userId, repo);
            String status  = str(data, "status");
            int page  = data.get("page")  != null ? Integer.parseInt(str(data, "page"))  : 0;
            int limit = data.get("limit") != null ? Integer.parseInt(str(data, "limit")) : 20;
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("storeId", storeId);
            if (status != null) filter.put("status", status);
            return json(mapper, repo.findAllSorted("orders", filter,
                    "createdAt", Sort.Direction.DESC, page * limit, limit));
        };
    }

    /* ── STORE_ORDER_ACCEPT ─────────────────────────────────────────────── */
    public NktOperationHandler storeOrderAccept() {
        return (data, userId, repo, mapper) -> {
            String storeId = resolveStoreId(userId, repo);
            String orderId = str(data, "orderId");
            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
            if (!"placed".equals(order.get("status")))
                throw new RuntimeException("Cannot accept at status: " + order.get("status"));
            repo.updateById("orders", orderId,
                    Map.of("status", "accepted", "currentStatus", "accepted",
                           "storeNote", String.valueOf(data.getOrDefault("storeNote", "")),
                           "updatedAt", LocalDateTime.now().toString()));
            return json(mapper, repo.findById("orders", orderId).orElseThrow());
        };
    }

    /* ── STORE_ORDER_REJECT ─────────────────────────────────────────────── */
    public NktOperationHandler storeOrderReject() {
        return (data, userId, repo, mapper) -> {
            String storeId = resolveStoreId(userId, repo);
            String orderId = str(data, "orderId");
            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
            if (!"placed".equals(order.get("status")))
                throw new RuntimeException("Cannot reject at status: " + order.get("status"));
            repo.updateById("orders", orderId,
                    Map.of("status", "cancelled", "currentStatus", "cancelled",
                           "storeNote", String.valueOf(data.getOrDefault("reason", "")),
                           "updatedAt", LocalDateTime.now().toString()));
            return json(mapper, repo.findById("orders", orderId).orElseThrow());
        };
    }

    /* ── STORE_ORDER_DISPATCH ───────────────────────────────────────────── */
    public NktOperationHandler storeOrderDispatch() {
        return (data, userId, repo, mapper) -> {
            String storeId = resolveStoreId(userId, repo);
            String orderId = str(data, "orderId");
            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
            if (!"accepted".equals(order.get("status")))
                throw new RuntimeException("Order must be accepted before dispatch");
            Map<String, Object> agent = Map.of(
                    "name",         String.valueOf(data.getOrDefault("agentName",   "")),
                    "phone",        String.valueOf(data.getOrDefault("agentPhone",  "")),
                    "vehiclePlate", String.valueOf(data.getOrDefault("vehiclePlate","")));
            repo.updateById("orders", orderId,
                    Map.of("deliveryAgent", agent, "status", "dispatched",
                           "currentStatus", "dispatched",
                           "updatedAt", LocalDateTime.now().toString()));
            return json(mapper, repo.findById("orders", orderId).orElseThrow());
        };
    }

    /* ── STORE_ORDER_DELIVER ────────────────────────────────────────────── */
    public NktOperationHandler storeOrderDeliver() {
        return (data, userId, repo, mapper) -> {
            String storeId = resolveStoreId(userId, repo);
            String orderId = str(data, "orderId");
            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
            if (!"dispatched".equals(order.get("status")))
                throw new RuntimeException("Order must be dispatched before delivery");
            repo.updateById("orders", orderId,
                    Map.of("status", "delivered", "currentStatus", "delivered",
                           "deliveryProof", String.valueOf(data.getOrDefault("deliveryProof", "")),
                           "updatedAt", LocalDateTime.now().toString()));
            return json(mapper, repo.findById("orders", orderId).orElseThrow());
        };
    }
}
