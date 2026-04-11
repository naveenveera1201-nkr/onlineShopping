package com.service.handlers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.nkt.NktProcessDefinition;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles user/customer profile, store-profile, and notification operations
 * that require custom logic beyond simple CRUD.
 *
 * Keys: CUSTOMER_ADD_ADDRESS, CUSTOMER_DELETE_ADDRESS, CUSTOMER_TOGGLE_FAV,
 *       STORE_PROFILE_GET, STORE_PROFILE_UPDATE, STORE_PROFILE_TOGGLE,
 *       STORE_PROFILE_DASHBOARD, NOTIFICATION_REGISTER_DEVICE
 */
@Component
@Slf4j
public class NktUserHandler {

    private String str(Map<String, Object> d, String k) {
        Object v = d.get(k); return v == null ? null : v.toString();
    }

    private String json(ObjectMapper m, Object o) {
        try { return m.writeValueAsString(o); }
        catch (Exception e) { return "{\"error\":\"serialisation failed\"}"; }
    }

    /* ── CUSTOMER_ADD_ADDRESS ───────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    public NktOperationHandler addAddress() {
    	return (data, userId, repo, mapper, def) -> {

    	    String tableName = data.get("userType") + def.getCollection();

    	    if (!ObjectId.isValid(userId)) {
    	        return json(mapper, Map.of(
    	                "statusCode", "N400",
    	                "statusDesc", "Invalid userId format"
    	        ));
    	    }

    	    String id = userId;

    	    Map<String, Object> user = repo.findById(tableName, id).orElse(null);

    	    if (user == null) {
    	        return json(mapper, Map.of(
    	                "statusCode", "N404",
    	                "statusDesc", "User not found"
    	        ));
    	    }

    	    List<Map<String, Object>> addresses =
    	            (List<Map<String, Object>>) user.getOrDefault("addresses", new ArrayList<>());

    	    String addressId = str(data, "id"); // incoming address id (optional)
    	    String newLabel = str(data, "label").trim();

    	    Map<String, Object> existingAddress = null;

    	    // ✅ Find existing address by ID
    	    if (addressId != null) {
    	        for (Map<String, Object> a : addresses) {
    	            if (addressId.equals(a.get("id"))) {
    	                existingAddress = a;
    	                break;
    	            }
    	        }
    	    }
    	    
    	    String existingAddressId = existingAddress != null 
    	            ? existingAddress.get("id").toString() 
    	            : null;

    	    if (existingAddress != null) {
    	        // 🔄 UPDATE FLOW

    	        existingAddress.put("label", newLabel);
    	        existingAddress.put("line1", str(data, "line1"));
    	        existingAddress.put("line2", str(data, "line2"));
    	        existingAddress.put("city", str(data, "city"));
    	        existingAddress.put("state", str(data, "state"));
    	        existingAddress.put("pincode", str(data, "pincode"));
    	        existingAddress.put("latitude", data.get("latitude"));
    	        existingAddress.put("longitude", data.get("longitude"));
    	        existingAddress.put("updatedAt", LocalDateTime.now().toString());

    	    } else {

				boolean labelExists = addresses.stream()
						.anyMatch(a -> newLabel.equalsIgnoreCase(String.valueOf(a.get("label")))
								&& (existingAddressId == null || !a.get("id").equals(existingAddressId)));

				if (labelExists) {
					return json(mapper, Map.of("statusCode", "N400", "statusDesc", "Address label already exists"));
				}


    	        Map<String, Object> addr = new LinkedHashMap<>();
    	        addr.put("id", UUID.randomUUID().toString());
    	        addr.put("label", newLabel);
    	        addr.put("line1", str(data, "line1"));
    	        addr.put("line2", str(data, "line2"));
    	        addr.put("city", str(data, "city"));
    	        addr.put("state", str(data, "state"));
    	        addr.put("pincode", str(data, "pincode"));
    	        addr.put("latitude", data.get("latitude"));
    	        addr.put("longitude", data.get("longitude"));
    	        addr.put("status", "ACTIVE");
    	        addr.put("createdAt", LocalDateTime.now().toString());

    	        addresses.add(addr);
    	    }

    	    // ✅ Save back
    	    repo.updateById(tableName, id,
    	            Map.of("addresses", addresses, "updatedAt", LocalDateTime.now().toString()));

    	    return json(mapper, Map.of(
    	            "data", Map.of(
    	                    "message", existingAddress != null ? "Address updated successfully" : "Address added successfully",
    	                    "statusCode", "N200",
    	                    "statusDesc", "Success"
    	            )
    	    ));
    	};
    }

    /* ── CUSTOMER_DELETE_ADDRESS ────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    public NktOperationHandler deleteAddress() {
        return (data, userId, repo, mapper, def) -> {

            String tableName = data.get("userType") + def.getCollection();
            String addressId = str(data, "addressId");

            // ✅ Validate userId
            if (!ObjectId.isValid(userId)) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "Invalid userId format"
                ));
            }

            // ✅ Validate addressId
            if (addressId == null || addressId.isBlank()) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "addressId is required"
                ));
            }

            Map<String, Object> user = repo.findById(tableName, userId).orElse(null);

            if (user == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "User not found"
                ));
            }

            List<Map<String, Object>> addresses =
                    (List<Map<String, Object>>) user.getOrDefault("addresses", new ArrayList<>());

            // ✅ REMOVE ADDRESS
            boolean removed = addresses.removeIf(a -> addressId.equals(a.get("id")));

            if (!removed) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Address not found"
                ));
            }

            // ✅ Update DB
            repo.updateById(tableName, userId,
                    Map.of("addresses", addresses, "updatedAt", LocalDateTime.now().toString()));

            return json(mapper, Map.of(
                    "data", Map.of(
                            "message", "Address deleted successfully",
                            "statusCode", "N200",
                            "statusDesc", "Success"
                    )
            ));
        };
    }

    /* ── CUSTOMER_TOGGLE_FAV ────────────────────────────────────────────── */
    @SuppressWarnings("unchecked")
    public NktOperationHandler toggleFavourite() {
        return (data, userId, repo, mapper, def) -> {
            String storeId = str(data, "storeId");
            repo.findById("stores", storeId)
                    .orElseThrow(() -> new RuntimeException("Store not found"));
            Map<String, Object> user = repo.findById("users", userId)
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
            List<String> favs = (List<String>) user.getOrDefault("favouriteStoreIds", new ArrayList<>());
            boolean saved;
            if (favs.contains(storeId)) { favs.remove(storeId); saved = false; }
            else                        { favs.add(storeId);    saved = true;  }
            repo.updateById("users", userId,
                    Map.of("favouriteStoreIds", favs, "updatedAt", LocalDateTime.now().toString()));
            return json(mapper, Map.of("saved", saved, "storeId", storeId));
        };
    }

    /* ── STORE_PROFILE_GET ──────────────────────────────────────────────── */
    public NktOperationHandler storeProfileGet() {
        return (data, userId, repo, mapper, def) -> {
            Map<String, Object> store = repo.findOne("stores", "ownerId", userId)
                    .orElseThrow(() -> new RuntimeException("Store not found for owner"));
            return json(mapper, store);
        };
    }

    /* ── STORE_PROFILE_UPDATE ───────────────────────────────────────────── */
    public NktOperationHandler storeProfileUpdate() {
        return (data, userId, repo, mapper, def) -> {
            Map<String, Object> store = repo.findOne("stores", "ownerId", userId)
                    .orElseThrow(() -> new RuntimeException("Store not found for owner"));
            Map<String, Object> updates = new LinkedHashMap<>();
            if (data.get("name")  != null) updates.put("name",  str(data, "name"));
            if (data.get("phone") != null) updates.put("phone", str(data, "phone"));
            if (data.get("hours") != null) updates.put("hours", str(data, "hours"));
            updates.put("updatedAt", LocalDateTime.now().toString());
            repo.updateById("stores", store.get("id").toString(), updates);
            return json(mapper, repo.findById("stores", store.get("id").toString()).orElse(store));
        };
    }

    /* ── STORE_PROFILE_TOGGLE ───────────────────────────────────────────── */
    public NktOperationHandler storeProfileToggle() {
        return (data, userId, repo, mapper, def) -> {
            Map<String, Object> store = repo.findOne("stores", "ownerId", userId)
                    .orElseThrow(() -> new RuntimeException("Store not found for owner"));
            boolean isOpen = Boolean.parseBoolean(str(data, "isOpen"));
            repo.updateById("stores", store.get("id").toString(),
                    Map.of("open", isOpen, "updatedAt", LocalDateTime.now().toString()));
            return json(mapper, Map.of(
                    "storeId",   store.get("id"),
                    "isOpen",    isOpen,
                    "updatedAt", LocalDateTime.now().toString()));
        };
    }

    /* ── STORE_PROFILE_DASHBOARD ────────────────────────────────────────── */
    public NktOperationHandler storeProfileDashboard() {
        return (data, userId, repo, mapper, def) -> {
            Map<String, Object> store = repo.findOne("stores", "ownerId", userId)
                    .orElseThrow(() -> new RuntimeException("Store not found for owner"));
            String storeId  = store.get("id").toString();
            String today    = LocalDateTime.now().toLocalDate().toString();

            List<Map<String, Object>> orders = repo.findAll("orders", Map.of("storeId", storeId));
            long newOrders  = orders.stream().filter(o -> "placed".equals(o.get("status"))).count();
            long todayCount = orders.stream().filter(o -> today.equals(
                    o.getOrDefault("createdAt", "").toString().substring(0, 10))).count();
            double todayRev = orders.stream()
                    .filter(o -> "delivered".equals(o.get("status")) && today.equals(
                            o.getOrDefault("createdAt", "").toString().substring(0, 10)))
                    .mapToDouble(o -> o.get("totalAmount") != null
                            ? Double.parseDouble(o.get("totalAmount").toString()) : 0).sum();

            Map<String, Object> dash = new LinkedHashMap<>();
            dash.put("storeId",       storeId);
            dash.put("newOrders",     newOrders);
            dash.put("todayOrders",   todayCount);
            dash.put("todayRevenue",  todayRev);
            dash.put("currentStatus", Boolean.TRUE.equals(store.get("open")) ? "OPEN" : "CLOSED");
            return json(mapper, dash);
        };
    }

    /* ── NOTIFICATION_REGISTER_DEVICE ──────────────────────────────────── */
    public NktOperationHandler registerDevice() {
        return (data, userId, repo, mapper, def) -> {
            String deviceToken = str(data, "deviceToken");
            Optional<Map<String, Object>> existing = repo.findOneByCriteria("deviceTokens",
                    Map.of("userId", userId, "deviceToken", deviceToken));
            if (existing.isPresent()) {
                repo.updateFirst("deviceTokens",
                        Map.of("userId", userId, "deviceToken", deviceToken),
                        Map.of("platform", str(data, "platform"), "status", "ACTIVE",
                               "updatedAt", LocalDateTime.now().toString()));
            } else {
                Map<String, Object> dt = new LinkedHashMap<>();
                dt.put("userId",      userId);
                dt.put("deviceToken", deviceToken);
                dt.put("platform",    str(data, "platform"));
                dt.put("userType",    str(data, "userType"));
                dt.put("status",      "ACTIVE");
                dt.put("createdAt",   LocalDateTime.now().toString());
                dt.put("updatedAt",   LocalDateTime.now().toString());
                repo.insert("deviceTokens", dt);
            }
            return json(mapper, Map.of("message", "Device registered successfully"));
        };
    }
    
}
