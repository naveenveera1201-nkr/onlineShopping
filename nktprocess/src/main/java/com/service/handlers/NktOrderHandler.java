package com.service.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.NktDynamicRepository;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(ObjectMapper mapper, Map<String, Object> d, String k) {
        Object v = d.get(k);

        try {
            if (v instanceof String) {
                return mapper.readValue((String) v, List.class);
            } else if (v instanceof List<?>) {
                return ((List<?>) v).stream()
                        .filter(e -> e instanceof Map)
                        .map(e -> (Map<String, Object>) e)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid stocks format", e);
        }

        return List.of();
    }

    private String json(ObjectMapper m, Object o) {
        try { return m.writeValueAsString(o); }
        catch (Exception e) { return "{\"error\":\"serialisation failed\"}"; }
    }

    private String resolveStoreId(String ownerId, NktDynamicRepository repo) {
        return repo.findOne("stores", "userId", ownerId)
                .map(s -> s.get("storeId").toString())
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
        return (data, userId, repo, mapper, def) -> {
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
//    public NktOperationHandler placeOrder() {
//        return (data, userId, repo, mapper, def) -> {
//            List<Map<String, Object>> orderItems = list(data, "stocks").stream().map(i -> {
//                String itemId = (String) i.get("stockId");
//                Map<String, Object> si = repo.findById("stocks", itemId)
//                        .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
//                int qty = i.get("qty") != null ? Integer.parseInt(i.get("qty").toString()) : 1;
//                Map<String, Object> oi = new LinkedHashMap<>();
//                oi.put("itemId",   itemId);
//                oi.put("itemName", si.get("stockName"));
//                oi.put("qty",      qty);
//                oi.put("price",    si.get("price"));
//                return oi;
//            }).collect(Collectors.toList());
//
//            double total = orderItems.stream()
//                    .mapToDouble(i -> Double.parseDouble(i.get("price").toString())
//                            * Integer.parseInt(i.get("qty").toString())).sum();
//
//            String now = LocalDateTime.now().toString();
//            Map<String, Object> order = new LinkedHashMap<>();
//            order.put("userId",      userId);
//            order.put("storeId",         str(data, "storeId"));
//            order.put("categoryId",      str(data, "categoryId"));
//            order.put("addressId",       str(data, "addressId"));
//            order.put("paymentMethod",   str(data, "paymentMethod"));
//            order.put("appointmentSlot", str(data, "appointmentSlot"));
//            order.put("notes",           str(data, "notes"));
//            order.put("urgency",         str(data, "urgency"));
//            order.put("orderType",       str(data, "orderType"));
//            order.put("items",           orderItems);
//            order.put("status",          "placed");
//            order.put("currentStatus",   "placed");
//            order.put("totalAmount",     total);
//            order.put("createdAt",       now);
//            order.put("createdBy",       userId);
//            order.put("statusTimeline",  List.of(Map.of("status", "placed", "at", now)));
//            return json(mapper, repo.insert("orders", order));
//        };
//    }
    
    @SuppressWarnings("unchecked")
    public NktOperationHandler placeOrder() {
        return (data, userId, repo, mapper, def) -> {

            String storeId   = str(data, "storeId");
            String addressId = str(data, "addressId");

            // ✅ Validate required fields
            if (storeId == null || addressId == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "storeId and addressId are required"
                ));
            }

            // ✅ Validate store
            Map<String, Object> store = repo.findOne("stores", "storeId", storeId).orElse(null);
            if (store == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Store not found"
                ));
            }

            // ✅ Validate user address
            Map<String, Object> user = repo.findById("customerusers", userId).orElse(null);
            if (user == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "User not found"
                ));
            }

            List<Map<String, Object>> addresses =
                    (List<Map<String, Object>>) user.getOrDefault("addresses", new ArrayList<>());

            boolean validAddress = addresses.stream()
                    .anyMatch(a -> addressId.equals(a.get("id")));
            

            if (!validAddress) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "Invalid addressId"
                ));
            }
            
            Map<String, Object> address = addresses.stream().filter(a -> addressId.equals(a.get("id"))).findFirst()
					.orElse(null);

            // ✅ Validate items
			List<Map<String, Object>> inputItems = list(mapper, data, "stocks");
            if (inputItems == null || inputItems.isEmpty()) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "No items provided"
                ));
            }

            List<Map<String, Object>> orderItems = new ArrayList<>();
            double total = 0.0;

            for (Map<String, Object> i : inputItems) {

                String stockId = (String) i.get("stockId");
                int qty = i.get("qty") != null ? Integer.parseInt(i.get("qty").toString()) : 1;

                if (qty <= 0) {
                    return json(mapper, Map.of(
                            "statusCode", "N400",
                            "statusDesc", "Invalid quantity for stockId: " + stockId
                    ));
                }

                // ✅ Validate stock belongs to store
                Map<String, Object> stock = repo.findOneByCriteria("stocks",
                        Map.of("stockId", stockId, "storeId", storeId, "status", "ACTIVE"))
                        .orElse(null);

                if (stock == null) {
                    return json(mapper, Map.of(
                            "statusCode", "N404",
                            "statusDesc", "Stock not found in store: " + stockId
                    ));
                }

                // ✅ Availability check
                if (stock.get("available") != null && !Boolean.TRUE.equals(stock.get("available"))) {
                    return json(mapper, Map.of(
                            "statusCode", "N400",
                            "statusDesc", "Item not available: " + stockId
                    ));
                }

                double price = Double.parseDouble(stock.get("price").toString());
                double itemTotal = price * qty;

                Map<String, Object> oi = new LinkedHashMap<>();
                oi.put("stockId", stockId);
                oi.put("stockName", stock.get("stockName"));
                oi.put("name", stock.get("name"));
                oi.put("qty", qty);
                oi.put("price", price);
                oi.put("total", itemTotal);

                orderItems.add(oi);
                total += itemTotal;
            }

            // ✅ Create order
            String now = LocalDateTime.now().toString();
            
            String random = RandomStringUtils.randomNumeric(6);

            String orderId = "#ORD"
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + random;

            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderId", UUID.randomUUID().toString()); // ✅ important
            order.put("orderRef", orderId); // ✅ human-friendly);
            order.put("orderDate", now);
            order.put("userId", userId);
            order.put("storeId", storeId);
            order.put("storeName", store.get("storeName"));
            order.put("name", store.get("name"));
            order.put("address", address);
            order.put("paymentMethod", str(data, "paymentMethod"));
            order.put("appointmentSlot", str(data, "appointmentSlot"));
            order.put("notes", str(data, "notes"));
            order.put("urgency", str(data, "urgency"));
            order.put("orderType", str(data, "orderType"));
            order.put("items", orderItems);
            order.put("totalAmount", total);

            // ✅ status
            order.put("status", "placed");
            order.put("currentStatus", "placed");

            // ✅ audit
            order.put("createdAt", now);
            order.put("createdBy", userId);

            // ✅ timeline
            order.put("statusTimeline", List.of(
                    Map.of("status", "placed", "at", now)
            ));

            Map<String, Object> savedOrder = repo.insert("orders", order);

            return json(mapper, Map.of(
                    "data", savedOrder,
                    "statusCode", "N200",
                    "statusDesc", "Order placed successfully"
            ));
        };
    }

    /* ── ORDER_HISTORY ──────────────────────────────────────────────────── */
    public NktOperationHandler orderHistory() {
        return (data, userId, repo, mapper, def) -> {
            String status = str(data, "status");
            int page  = data.get("page")  != null ? Integer.parseInt(str(data, "page"))  : 0;
            int limit = data.get("limit") != null ? Integer.parseInt(str(data, "limit")) : 20;
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("userId", userId);
            if (status != null) filter.put("status", status);
            return json(mapper, repo.findAllSorted("orders", filter,
                    "createdAt", Sort.Direction.DESC, page * limit, limit));
        };
    }
    
    public NktOperationHandler customerOrdersList() {
        return (data, userId, repo, mapper, def) -> {

            try {
              
            	String status = str(data, "status");

                int page = 0;
                int limit = 10;

                try {
                    if (data.get("page") != null) {
                        page = Integer.parseInt(str(data, "page"));
                    }
                    if (data.get("limit") != null) {
                        limit = Integer.parseInt(str(data, "limit"));
                    }
                } catch (Exception e) {
                    return json(mapper, Map.of(
                            "statusCode", "N400",
                            "statusDesc", "Invalid pagination values"
                    ));
                }

				// ✅ Pagination safety
				if (page < 0)
					page = 0;
				
				if (limit <= 0 || limit > 100)
					limit = 10;

				// ✅ Build filter
				Map<String, Object> filter = new LinkedHashMap<>();
				filter.put("userId", userId);

				if (status != null && !status.isBlank()) {
					filter.put("status", status);
				}

                // ✅ Fetch data
                List<Map<String, Object>> orders = repo.findAllSorted(
                        "orders",
                        filter,
                        "createdAt",
                        Sort.Direction.DESC,
                        page * limit,
                        limit
                );

                // ✅ Response
                return json(mapper, Map.of(
                        "data", orders,
                        "page", page,
                        "limit", limit,
                        "count", orders.size(),
                        "statusCode", "N200",
                        "statusDesc", "Success"
                ));

            } catch (Exception e) {
                log.error("Error fetching order history", e);

                return json(mapper, Map.of(
                        "statusCode", "N500",
                        "statusDesc", "Failed to fetch order history"
                ));
            }
        };
    }

//    /* ── ORDER_GET_DETAIL ───────────────────────────────────────────────── */
//    public NktOperationHandler orderGetDetail() {
//        return (data, userId, repo, mapper, def) -> {
//            String orderId = str(data, "orderId");
//            Map<String, Object> order = repo.findById("orders", orderId)
//                    .orElseThrow(() -> new RuntimeException("Order not found"));
//            if (!userId.equals(order.get("userId"))) throw new RuntimeException("Unauthorized");
//            return json(mapper, order);
//        };
//    }
    
    public NktOperationHandler orderGetDetail() {
        return (data, userId, repo, mapper, def) -> {

            String orderId = str(data, "orderId");

            if (orderId == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "orderId is required"
                ));
            }

            // ✅ Step 1: Validate user existence
            Map<String, Object> user = repo.findById(data.get("userType")+"users", userId).orElse(null);

            if (user == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "User not found"
                ));
            }

            // ✅ Step 3: Fetch order
            Map<String, Object> order = repo.findById("orders", orderId).orElse(null);

            if (order == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Order not found"
                ));
            }

            String orderUserId  = order.get("userId") != null ? order.get("userId").toString() : null;
            String orderStoreId = order.get("storeId") != null ? order.get("storeId").toString() : null;

            boolean authorized = false;

            // ✅ Step 4: Access control
            switch (data.get("userType").toString().toLowerCase()) {

                case "customer":
                    // customer can access only their orders
                    authorized = userId.equals(orderUserId);
                    break;

                case "store":
                    // store user → need to resolve storeId
                    Map<String, Object> store = repo.findOne("stores", "storeId", userId).orElse(null);

                    if (store != null && store.get("storeId") != null) {
                        authorized = store.get("storeId").toString().equals(orderStoreId);
                    }
                    break;

                case "admin":
                    // admin can access everything
                    authorized = true;
                    break;

                default:
                    return json(mapper, Map.of(
                            "statusCode", "N403",
                            "statusDesc", "Invalid role"
                    ));
            }

            // ❌ Unauthorized access
            if (!authorized) {
                return json(mapper, Map.of(
                        "statusCode", "N403",
                        "statusDesc", "You are not allowed to access this order"
                ));
            }

            // ✅ Success response
            return json(mapper, Map.of(
                    "data", order,
                    "statusCode", "N200",
                    "statusDesc", "Success"
            ));
        };
    }

    /* ── ORDER_TRACK ────────────────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    public NktOperationHandler trackOrder() {
        return (data, userId, repo, mapper, def) -> {

            String orderId = str(data, "orderId");

            // ✅ Step 1: Validate input
            if (orderId == null || orderId.isBlank()) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "orderId is required"
                ));
            }

            // ✅ Step 2: Validate user
            
            String userType = data.get("userType") != null
            		? data.get("userType").toString().toLowerCase()
            				: "";
            
            Map<String, Object> user = repo.findById(userType+"users", userId).orElse(null);

            if (user == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "User not found"
                ));
            }

            // ✅ Step 3: Fetch order
            Map<String, Object> order = repo.findById("orders", orderId).orElse(null);

            if (order == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Order not found"
                ));
            }

            String orderUserId  = order.get("userId") != null ? order.get("userId").toString() : null;
            String orderStoreId = order.get("storeId") != null ? order.get("storeId").toString() : null;

            boolean authorized = false;

            // ✅ Step 4: Role-based access
            switch (userType) {

                case "customer":
                    authorized = userId.equals(orderUserId);
                    break;

                case "store":
                    Map<String, Object> store = repo.findOne("stores", "userId", userId).orElse(null);
                    if (store != null && store.get("storeId") != null) {
                        authorized = store.get("storeId").toString().equals(orderStoreId);
                    }
                    break;

                case "admin":
                    authorized = true;
                    break;
            }

            if (!authorized) {
                return json(mapper, Map.of(
                        "statusCode", "N403",
                        "statusDesc", "Unauthorized access"
                ));
            }

            // ✅ Step 5: Build response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orderId", orderId);
            result.put("status", order.get("status"));
            result.put("currentStatus", order.get("currentStatus"));

            // ✅ Delivery agent location
            Object agentObj = order.get("deliveryAgent");

            if (agentObj instanceof Map) {
                Map<String, Object> agent = (Map<String, Object>) agentObj;

                result.put("agentLatitude", agent.get("latitude"));
                result.put("agentLongitude", agent.get("longitude"));
                result.put("agentName", agent.get("name"));
                result.put("agentPhone", agent.get("phone"));
            }

            // ✅ Optional: timeline
            result.put("statusTimeline", order.getOrDefault("statusTimeline", List.of()));

            // ✅ Final response
            return json(mapper, Map.of(
                    "data", result,
                    "statusCode", "N200",
                    "statusDesc", "Success"
            ));
        };
    }

    /* ── ORDER_CANCEL ───────────────────────────────────────────────────── */
    public NktOperationHandler cancelOrder() {
        return (data, userId, repo, mapper, def) -> {
            String orderId = str(data, "orderId");
            Map<String, Object> order = repo.findById("orders", orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!userId.equals(order.get("userId"))) throw new RuntimeException("Unauthorized");
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
        return (data, userId, repo, mapper, def) -> {
            String orderId = str(data, "orderId");
            Map<String, Object> order = repo.findById("orders", orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (repo.exists("ratings", Map.of("orderId", orderId, "userId", userId)))
                throw new RuntimeException("Order already rated");
            Map<String, Object> rating = new LinkedHashMap<>();
            rating.put("orderId",    orderId);
            rating.put("storeId",    order.get("storeId"));
            rating.put("userId", userId);
            rating.put("rating",     Integer.parseInt(str(data, "rating")));
            rating.put("review",     str(data, "review"));
            rating.put("status",     "ACTIVE");
            rating.put("createdAt",  LocalDateTime.now().toString());
            return json(mapper, repo.insert("ratings", rating));
        };
    }

//    /* ── WISHLIST_ADD ───────────────────────────────────────────────────── */
//    public NktOperationHandler wishlistAdd() {
//        return (data, userId, repo, mapper, def) -> {
//            
//        	String itemId = str(data, "itemId");
//           
//            Map<String, Object> si = repo.findById("stocks", itemId)
//                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
//            
//            return repo.findOneByCriteria("wishlist", Map.of("customerId", userId, "itemId", itemId))
//                    .map(existing -> json(mapper, existing))
//                    .orElseGet(() -> {
//                        Map<String, Object> wi = new LinkedHashMap<>();
//                        wi.put("customerId", userId);
//                        wi.put("itemId",     itemId);
//                        wi.put("itemName",   si.get("name"));
//                        wi.put("price",      si.get("price"));
//                        wi.put("available",  si.get("available"));
//                        wi.put("status",     "ACTIVE");
//                        wi.put("createdAt",  LocalDateTime.now().toString());
//                        return json(mapper, repo.insert("wishlist", wi));
//                    });
//        };
//    }
//
//    /* ── WISHLIST_REMOVE ────────────────────────────────────────────────── */
//    public NktOperationHandler wishlistRemove() {
//        return (data, userId, repo, mapper, def) -> {
//            String itemId = str(data, "itemId");
//            Map<String, Object> wi = repo.findOneByCriteria("wishlist",
//                    Map.of("customerId", userId, "itemId", itemId))
//                    .orElseThrow(() -> new RuntimeException("Wishlist item not found"));
//            repo.updateById("wishlist", wi.get("id").toString(),
//                    Map.of("status", "DELETED"));
//            return json(mapper, Map.of("message", "Item removed from wishlist"));
//        };
//    }
    
    public NktOperationHandler wishlistAdd() {
        return (data, userId, repo, mapper, def) -> {

            String itemId = str(data, "itemId");

            // ✅ 1. Validate input
            if (itemId == null || itemId.isBlank()) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "itemId is required"
                ));
            }

            // ✅ 2. Validate stock item (ACTIVE only)
            Map<String, Object> stock = repo.findOne("stocks", "stockId", itemId)
//                    Map.of("_id", itemId, "status", "ACTIVE"))
                    .orElse(null);

            if (stock == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Item not found or inactive"
                ));
            }
            
            // ✅ Validate store
            Map<String, Object> store = repo.findOne("stores", "storeId", stock.get("storeId")).orElse(null);
            if (store == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Store not found"
                ));
            }
            
            Map<String, Object> existingOpt = repo.findOneByCriteria("wishlist",
            		 Map.of("userId", userId, "itemId", itemId, "status", "ACTIVE"))
                    .orElse(null);
            
            // ✅ 3. Check existing wishlist
//            Optional<Map<String, Object>> existingOpt =
//                    repo.findOneByCriteria("wishlist",
//                            Map.of("userId", userId, "stockId", itemId, "status", "ACTIVE"));

            if (!(existingOpt== null)) {
                return json(mapper, Map.of(
                        "data", existingOpt,
                        "statusCode", "N200",
                        "statusDesc", "Already in wishlist"
                ));
            }

            // ✅ 4. Insert new wishlist item
            Map<String, Object> wi = new LinkedHashMap<>();
            wi.put("userId", userId);
            wi.put("itemId", itemId);
            wi.put("itemName", stock.get("stockName")); // ✅ FIXED
            wi.put("StoreName", store.get("storeName")); // ✅ Added store name
            wi.put("name", stock.get("name"));
            wi.put("s_name", store.get("name"));
            wi.put("price", stock.get("price"));
            wi.put("available", stock.get("status").equals("ACTIVE"));
            wi.put("status", "ACTIVE");
            wi.put("createdAt", LocalDateTime.now().toString());

            Map<String, Object> saved = repo.insert("wishlist", wi);

            return json(mapper, Map.of(
                    "data", saved,
                    "statusCode", "N200",
                    "statusDesc", "Added to wishlist"
            ));
        };
    }
    
    public NktOperationHandler wishlistRemove() {
        return (data, userId, repo, mapper, def) -> {

            String itemId = str(data, "itemId");

            if (itemId == null || itemId.isBlank()) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "itemId is required"
                ));
            }

            Map<String, Object> wi = repo.findOneByCriteria("wishlist",
                    Map.of("userId", userId, "itemId", itemId, "status", "ACTIVE"))
                    .orElse(null);

            if (wi == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Wishlist item not found"
                ));
            }

            repo.updateById("wishlist", wi.get("id").toString(),
                    Map.of(
                            "status", "DELETED",
                            "updatedAt", LocalDateTime.now().toString()
                    ));

            return json(mapper, Map.of(
                    "data", Map.of("itemId", itemId),
                    "statusCode", "N200",
                    "statusDesc", "Removed from wishlist"
            ));
        };
    }
    
    @SuppressWarnings("unchecked")
    public NktOperationHandler wishlistToggle() {
        return (data, userId, repo, mapper, def) -> {

            String storeId = str(data, "storeId");
            String stockId = str(data, "stockId");
            String countStr = str(data, "stockCount");

            // ✅ Validate input
            if (storeId == null || stockId == null || countStr == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "storeId, stockId and stockCount are required"
                ));
            }

            int newCount;
            try {
                newCount = Integer.parseInt(countStr);
                if (newCount <= 0) throw new Exception();
            } catch (Exception e) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "Invalid stockCount"
                ));
            }

            // ✅ Validate store
            Map<String, Object> store = repo.findOne("stores", "storeId", storeId).orElse(null);
            if (store == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Store not found"
                ));
            }

            // ✅ Validate stock
            Map<String, Object> stock = repo.findOneByCriteria("stocks",
                    Map.of("stockId", stockId, "storeId", storeId, "status", "ACTIVE"))
                    .orElse(null);

            if (stock == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Stock not found in this store"
                ));
            }

            String categoryId = stock.get("categoryId") != null ? stock.get("categoryId").toString() : null;
            String subCategoryId = stock.get("subCategoryId") != null ? stock.get("subCategoryId").toString() : null;

            Optional<Map<String, Object>> existingOpt = repo.findOneByCriteria("wishlist",
                    Map.of("userId", userId, "stockId", stockId, "storeId", storeId));

            boolean saved;
            String action;

            if (existingOpt.isPresent()) {

                Map<String, Object> existing = existingOpt.get();
                int existingCount = Integer.parseInt(existing.get("stockCount").toString());

				if (existingCount == newCount) {
					// ✅ SAME COUNT → REMOVE
//                    repo.updateById("wishlist", existing.get("id").toString(),
//                            Map.of(
//                                    "status", "DELETED",
//                                    "updatedAt", LocalDateTime.now().toString()
//                            ));
					repo.deleteById("wishlist", existing.get("id").toString());
					saved = false;
					action = "REMOVED";

				} else {
                    // ✅ DIFFERENT COUNT → UPDATE
                    repo.updateById("wishlist", existing.get("id").toString(),
                            Map.of(
                                    "stockCount", newCount,
                                    "updatedAt", LocalDateTime.now().toString()
                            ));
                    saved = true;
                    action = "UPDATED";
                }

            } else {
                // ✅ NEW → INSERT
                Map<String, Object> wi = new LinkedHashMap<>();
                wi.put("userId", userId);
                wi.put("storeId", storeId);
                wi.put("stockId", stockId);
                wi.put("stockName", stock.get("stockName"));
                wi.put("price", stock.get("price"));
                wi.put("storeName", store.get("storeName"));
                wi.put("categoryId", categoryId);
                wi.put("subCategoryId", subCategoryId);
                wi.put("stockCount", newCount);
                wi.put("status", "ACTIVE");
                wi.put("createdAt", LocalDateTime.now().toString());
                wi.put("name", stock.get("name"));
                wi.put("s_name", store.get("name"));

                repo.insert("wishlist", wi);

                saved = true;
                action = "ADDED";
            }

            return json(mapper, Map.of(
                    "data", Map.of(
                            "saved", saved,
                            "action", action,
                            "storeId", storeId,
                            "stockId", stockId,
                            "stockCount", newCount
                    ),
                    "statusCode", "N200",
                    "statusDesc", "Success"
            ));
        };
    }

    /* ── STORE_ORDER_LIST ───────────────────────────────────────────────── */
    public NktOperationHandler storeOrderList() {
        return (data, userId, repo, mapper, def) -> {
            
            // ✅ Validate store
            Map<String, Object> store = repo.findOne("stores", "userId", userId).orElse(null);
            if (store == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Store not found"
                ));
            }
            
            String storeId = store.get("storeId").toString();

            // ✅ Step 2: Inputs
            String status = str(data, "status");

            int page = 0;
            int limit = 10;

            try {
                if (data.get("page") != null)
                    page = Integer.parseInt(str(data, "page"));

                if (data.get("limit") != null)
                    limit = Integer.parseInt(str(data, "limit"));
            } catch (Exception e) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "Invalid pagination values"
                ));
            }

            // ✅ Step 3: Build filter
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("storeId", storeId);

			if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
                filter.put("status", status.toLowerCase());
            }

            // ✅ Step 4: Fetch orders
            List<Map<String, Object>> orders = repo.findAllSortedTwo(
                    "orders",
                    filter,
                    "createdAt",
                    "updatedAt",
                    Sort.Direction.DESC,
                    page * limit,
                    limit
            );
            
            // ✅ Step 5: Response
            return json(mapper, Map.of(
                    "data", orders,
//                    "page", page,
//                    "limit", limit,
                    "count", orders.size()
//                    "statusCode", "N200",
//                    "statusDesc", "Success"
            ));
        };
    }

//    /* ── STORE_ORDER_ACCEPT ─────────────────────────────────────────────── */
//    public NktOperationHandler storeOrderAccept() {
//        return (data, userId, repo, mapper, def) -> {
//            String storeId = resolveStoreId(userId, repo);
//            String orderId = str(data, "orderId");
//            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
//            if (!"placed".equals(order.get("status")))
//                throw new RuntimeException("Cannot accept at status: " + order.get("status"));
//            repo.updateById("orders", orderId,
//                    Map.of("status", "accepted", "currentStatus", "accepted",
//                           "storeNote", String.valueOf(data.getOrDefault("storeNote", "")),
//                           "updatedAt", LocalDateTime.now().toString()));
//            return json(mapper, repo.findById("orders", orderId).orElseThrow());
//        };
//    }
//
//    /* ── STORE_ORDER_REJECT ─────────────────────────────────────────────── */
//    public NktOperationHandler storeOrderReject() {
//        return (data, userId, repo, mapper, def) -> {
//            String storeId = resolveStoreId(userId, repo);
//            String orderId = str(data, "orderId");
//            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
//            if (!"placed".equals(order.get("status")))
//                throw new RuntimeException("Cannot reject at status: " + order.get("status"));
//            repo.updateById("orders", orderId,
//                    Map.of("status", "cancelled", "currentStatus", "cancelled",
//                           "storeNote", String.valueOf(data.getOrDefault("reason", "")),
//                           "updatedAt", LocalDateTime.now().toString()));
//            return json(mapper, repo.findById("orders", orderId).orElseThrow());
//        };
//    }
//
//    /* ── STORE_ORDER_DISPATCH ───────────────────────────────────────────── */
//    public NktOperationHandler storeOrderDispatch() {
//        return (data, userId, repo, mapper, def) -> {
//            String storeId = resolveStoreId(userId, repo);
//            String orderId = str(data, "orderId");
//            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
//            if (!"accepted".equals(order.get("status")))
//                throw new RuntimeException("Order must be accepted before dispatch");
//            Map<String, Object> agent = Map.of(
//                    "name",         String.valueOf(data.getOrDefault("agentName",   "")),
//                    "phone",        String.valueOf(data.getOrDefault("agentPhone",  "")),
//                    "vehiclePlate", String.valueOf(data.getOrDefault("vehiclePlate","")));
//            repo.updateById("orders", orderId,
//                    Map.of("deliveryAgent", agent, "status", "dispatched",
//                           "currentStatus", "dispatched",
//                           "updatedAt", LocalDateTime.now().toString()));
//            return json(mapper, repo.findById("orders", orderId).orElseThrow());
//        };
//    }
//
//    /* ── STORE_ORDER_DELIVER ────────────────────────────────────────────── */
//    public NktOperationHandler storeOrderDeliver() {
//        return (data, userId, repo, mapper, def) -> {
//            String storeId = resolveStoreId(userId, repo);
//            String orderId = str(data, "orderId");
//            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
//            if (!"dispatched".equals(order.get("status")))
//                throw new RuntimeException("Order must be dispatched before delivery");
//            repo.updateById("orders", orderId,
//                    Map.of("status", "delivered", "currentStatus", "delivered",
//                           "deliveryProof", String.valueOf(data.getOrDefault("deliveryProof", "")),
//                           "updatedAt", LocalDateTime.now().toString()));
//            return json(mapper, repo.findById("orders", orderId).orElseThrow());
//        };
//    }
    
    private String updateOrderStatus(
            String orderId,
            String storeId,
            String expectedStatus,
            String newStatus,
            Map<String, Object> extraUpdates,
            NktDynamicRepository repo,
            ObjectMapper mapper
    ) {

        Map<String, Object> order = getStoreOrder(orderId, storeId, repo);

        String currentStatus = order.get("status") != null
                ? order.get("status").toString().toLowerCase()
                : "";
        
        if (!expectedStatus.equals(currentStatus)) {
            return json(mapper, Map.of(
                    "statusCode", "N400",
                    "statusDesc", "Invalid status transition. Current: " + currentStatus
            ));
        }

        String now = LocalDateTime.now().toString();

        // ✅ Timeline update
        List<Map<String, Object>> timeline =
                (List<Map<String, Object>>) order.getOrDefault("statusTimeline", new ArrayList<>());

        timeline.add(Map.of(
                "status", newStatus,
                "at", now
        ));
        
        

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("status", newStatus);
        updates.put("currentStatus", newStatus);
        updates.put("updatedAt", now);
        updates.put("statusTimeline", timeline);

        if (extraUpdates != null) {
            updates.putAll(extraUpdates);
        }

        repo.updateById("orders", orderId, updates);

        // ✅ return updated response directly (avoid extra DB hit if possible)
        order.putAll(updates);

        return json(mapper, Map.of(
                "data", order,
                "statusCode", "N200",
                "statusDesc", "Success"
        ));
    }
    
//    public NktOperationHandler storeOrderAccept() {
//        return (data, userId, repo, mapper, def) -> {
//
//            String orderId = str(data, "orderId");
//            String storeId = resolveStoreId(userId, repo);
//          
//            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
//          
//            List<Map<String, Object>> stocks =
//                    (List<Map<String, Object>>) data.get("fulfilledItems");
//
//            int requestTotalQuantity = stocks.stream()
//                    .mapToInt(stock -> Integer.parseInt(stock.get("qty").toString()))
//                    .sum();
//
//            System.out.println("Total Quantity : " + requestTotalQuantity);
//            
//            List<Map<String, Object>> items =
//                    (List<Map<String, Object>>) order.get("items");
//
//            int orderTotalQuantity = items.stream()
//                    .mapToInt(item -> Integer.parseInt(item.get("qty").toString()))
//                    .sum();
//
//            Map<String, Object> extra = Map.of(
//                    "storeNote", String.valueOf(data.getOrDefault("storeNote", ""))
//            );
//            
////			String status = str(data, "partial").equalsIgnoreCase("true") ? "partially accepted" : "accepted";
//			String status = requestTotalQuantity != orderTotalQuantity ? "partially accepted" : "accepted";
//			
//			if (requestTotalQuantity != orderTotalQuantity) {
//				// ✅ Validate items
//				List<Map<String, Object>> inputItems = list(mapper, data, "fulfilledItems");
//				
//				if (inputItems == null || inputItems.isEmpty()) {
//					return json(mapper, Map.of("statusCode", "N400", "statusDesc", "No items provided"));
//				}
//
//				List<Map<String, Object>> orderItems = new ArrayList<>();
//				
//				double total = 0.0;
//
//				for (Map<String, Object> i : inputItems) {
//
//					String stockId = (String) i.get("stockId");
//					int qty = i.get("qty") != null ? Integer.parseInt(i.get("qty").toString()) : 1;
//
//					if (qty <= 0) {
//						return json(mapper,
//								Map.of("statusCode", "N400", "statusDesc", "Invalid quantity for stockId: " + stockId));
//					}
//
//					// ✅ Validate stock belongs to store
//					Map<String, Object> stock = repo.findOneByCriteria("stocks",
//							Map.of("stockId", stockId, "storeId", storeId, "status", "ACTIVE")).orElse(null);
//
//					if (stock == null) {
//						return json(mapper,
//								Map.of("statusCode", "N404", "statusDesc", "Stock not found in store: " + stockId));
//					}
//
//					// ✅ Availability check
//					if (stock.get("available") != null && !Boolean.TRUE.equals(stock.get("available"))) {
//						return json(mapper,
//								Map.of("statusCode", "N400", "statusDesc", "Item not available: " + stockId));
//					}
//
//					double price = Double.parseDouble(stock.get("price").toString());
//					double itemTotal = price * qty;
//
//					Map<String, Object> oi = new LinkedHashMap<>();
//					oi.put("stockId", stockId);
//					oi.put("stockName", stock.get("stockName"));
//					oi.put("name", stock.get("name"));
//					oi.put("qty", qty);
//					oi.put("price", price);
//					oi.put("total", itemTotal);
//
//					orderItems.add(oi);
//					total += itemTotal;
//				}
//				extra.put("acceptedItems", orderItems);
//				extra.put("acceptedTotalAmount", total);
//			}
//
//
//            return updateOrderStatus(orderId, storeId, "placed", status, extra, repo, mapper);
//        };
//    }
    
    public NktOperationHandler storeOrderAccept() {
        return (data, userId, repo, mapper, def) -> {

            String orderId = str(data, "orderId");
            String storeId = resolveStoreId(userId, repo);

            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);

            // Request fulfilled items
            List<Map<String, Object>> fulfilledItems = list(mapper, data, "fulfilledItems");
            if (fulfilledItems == null || fulfilledItems.isEmpty()) {
                return json(mapper,
                        Map.of("statusCode", "N400",
                               "statusDesc", "No fulfilled items provided"));
            }

            // Original order items
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> orderItems =
                    (List<Map<String, Object>>) order.get("items");

            int requestTotalQuantity = getTotalQuantity(fulfilledItems);
            int orderTotalQuantity = getTotalQuantity(orderItems);

            String status = requestTotalQuantity == orderTotalQuantity
                    ? "accepted"
                    : "partially accepted";

            Map<String, Object> extra = new HashMap<>();
            extra.put("storeNote", String.valueOf(data.getOrDefault("storeNote", "")));

            // Build accepted items only for partial acceptance
            if ("partially accepted".equals(status)) {

                List<Map<String, Object>> acceptedItems = new ArrayList<>();
                double acceptedTotalAmount = 0.0;

                for (Map<String, Object> item : fulfilledItems) {

                    String stockId = String.valueOf(item.get("stockId"));
                    int qty = Integer.parseInt(item.getOrDefault("qty", 1).toString());

                    if (qty <= 0) {
                        return json(mapper,
                                Map.of("statusCode", "N400",
                                       "statusDesc", "Invalid quantity for stockId : " + stockId));
                    }

                    Map<String, Object> stock = repo.findOneByCriteria(
                            "stocks",
                            Map.of(
                                    "stockId", stockId,
                                    "storeId", storeId,
                                    "status", "ACTIVE"))
                            .orElse(null);

                    if (stock == null) {
                        return json(mapper,
                                Map.of("statusCode", "N404",
                                       "statusDesc", "Stock not found : " + stockId));
                    }

                    if (Boolean.FALSE.equals(stock.get("available"))) {
                        return json(mapper,
                                Map.of("statusCode", "N400",
                                       "statusDesc", "Item not available : " + stockId));
                    }

                    double price = Double.parseDouble(stock.get("price").toString());
                    double total = price * qty;

                    Map<String, Object> acceptedItem = new LinkedHashMap<>();
                    acceptedItem.put("stockId", stockId);
                    acceptedItem.put("stockName", stock.get("stockName"));
                    acceptedItem.put("name", stock.get("name"));
                    acceptedItem.put("qty", qty);
                    acceptedItem.put("price", price);
                    acceptedItem.put("total", total);

                    acceptedItems.add(acceptedItem);
                    acceptedTotalAmount += total;
                }

                extra.put("items", acceptedItems);
                extra.put("totalAmount", acceptedTotalAmount);
                extra.put("orderedItems", order.get("items"));
                extra.put("orderedTotalAmount", order.get("totalAmount"));
            }

            return updateOrderStatus(
                    orderId,
                    storeId,
                    "placed",
                    status,
                    extra,
                    repo,
                    mapper);
        };
    }
    
    private int getTotalQuantity(List<Map<String, Object>> items) {

        if (items == null || items.isEmpty()) {
            return 0;
        }

        return items.stream()
                .mapToInt(item -> Integer.parseInt(item.getOrDefault("qty", 0).toString()))
                .sum();
    }
    
    public NktOperationHandler storeOrderReject() {
        return (data, userId, repo, mapper, def) -> {

            String orderId = str(data, "orderId");
            String storeId = resolveStoreId(userId, repo);

            Map<String, Object> extra = Map.of(
                    "storeNote", String.valueOf(data.getOrDefault("reason", ""))
            );

            return updateOrderStatus(orderId, storeId, "placed", "cancelled", extra, repo, mapper);
        };
    }
    
    public NktOperationHandler storeOrderDispatch() {
        return (data, userId, repo, mapper, def) -> {

            String orderId = str(data, "orderId");
            String storeId = resolveStoreId(userId, repo);

            Map<String, Object> agent = Map.of(
                    "name", String.valueOf(data.getOrDefault("agentName", "")),
                    "phone", String.valueOf(data.getOrDefault("agentPhone", "")),
                    "vehiclePlate", String.valueOf(data.getOrDefault("vehiclePlate", ""))
            );

            Map<String, Object> extra = Map.of("deliveryAgent", agent);
            
            Map<String, Object> order = getStoreOrder(orderId, storeId, repo);
            
            String currentStatus = order.get("status") != null
                    ? order.get("status").toString().toLowerCase()
                    : "";
            
			return updateOrderStatus(orderId, storeId,
					currentStatus.equals("accepted") ? "accepted" : "partially accepted", "dispatched", extra, repo,
					mapper);
        };
    }
    
    public NktOperationHandler storeOrderDeliver() {
        return (data, userId, repo, mapper, def) -> {

            String orderId = str(data, "orderId");
            String storeId = resolveStoreId(userId, repo);

            Map<String, Object> extra = Map.of(
                    "deliveryProof", String.valueOf(data.getOrDefault("deliveryProof", ""))
            );

            return updateOrderStatus(orderId, storeId, "dispatched", "delivered", extra, repo, mapper);
        };
    }
}
