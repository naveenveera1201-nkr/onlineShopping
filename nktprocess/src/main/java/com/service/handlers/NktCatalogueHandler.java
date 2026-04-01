package com.service.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.NktDynamicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles catalogue/discovery/location operations requiring custom logic.
 *
 * Keys: DISCOVER_NEARBY_STORES, STORES_GET_PRODUCTS, STORES_GET_AVAILABILITY,
 *       STOCK_LIST, STOCK_ADD, STOCK_CATEGORIES, STOCK_UPDATE,
 *       STOCK_ADJUST_QTY, STOCK_TOGGLE_AVAILABILITY, STOCK_DELETE,
 *       LOCATION_REVERSE_GEOCODE, LOCATION_GLOBAL_SEARCH,
 *       STORE_ORDER_LIST, STORE_ORDER_ACCEPT, STORE_ORDER_REJECT,
 *       STORE_ORDER_DISPATCH, STORE_ORDER_DELIVER
 */
@Component
@Slf4j
public class NktCatalogueHandler {

    private String str(Map<String, Object> d, String k) {
        Object v = d.get(k); return v == null ? null : v.toString();
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

    /* ── DISCOVER_NEARBY_STORES ─────────────────────────────────────────── */
    public NktOperationHandler nearbyStores() {
        return (data, userId, repo, mapper) -> {
            double lat    = Double.parseDouble(str(data, "latitude"));
            double lon    = Double.parseDouble(str(data, "longitude"));
            double radius = data.get("radiusKm") != null ? Double.parseDouble(str(data, "radiusKm")) : 5.0;

            List<Map<String, Object>> nearby = repo.findAll("stores", Map.of("status", "ACTIVE"))
                    .stream()
                    .filter(s -> {
                        Object addrObj = s.get("address");
                        if (!(addrObj instanceof Map)) return false;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> addr = (Map<String, Object>) addrObj;
                        Object sLat = addr.get("latitude"), sLon = addr.get("longitude");
                        if (sLat == null || sLon == null) return false;
                        return haversine(lat, lon,
                                Double.parseDouble(sLat.toString()),
                                Double.parseDouble(sLon.toString())) <= radius;
                    })
                    .sorted(Comparator.comparingDouble(s -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> addr = (Map<String, Object>) s.get("address");
                        return haversine(lat, lon,
                                Double.parseDouble(addr.get("latitude").toString()),
                                Double.parseDouble(addr.get("longitude").toString()));
                    }))
                    .collect(Collectors.toList());
            return json(mapper, nearby);
        };
    }

    /* ── STORES_GET_PRODUCTS ────────────────────────────────────────────── */
    public NktOperationHandler storeProducts() {
        return (data, userId, repo, mapper) -> {
            String storeId  = str(data, "storeId");
            String category = str(data, "category");
            String availStr = str(data, "available");
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("storeId", storeId);
            filter.put("status", "ACTIVE");
            if (category != null) filter.put("category",  category);
            if (availStr != null) filter.put("available", Boolean.parseBoolean(availStr));
            return json(mapper, repo.findAll("stockItems", filter));
        };
    }

    /* ── STORES_GET_AVAILABILITY (stub) ─────────────────────────────────── */
    public NktOperationHandler storeAvailability() {
        return (data, userId, repo, mapper) ->
                json(mapper, Map.of("storeId", str(data, "storeId"),
                        "serviceId", str(data, "serviceId"),
                        "fromDate",  str(data, "fromDate"),
                        "slots",     List.of()));
    }

    /* ── STOCK_LIST ─────────────────────────────────────────────────────── */
    public NktOperationHandler stockList() {
        return (data, userId, repo, mapper) -> {
            String storeId  = resolveStoreId(userId, repo);
            String category = str(data, "category");
            String availStr = str(data, "available");
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("storeId", storeId);
            filter.put("status",  "ACTIVE");
            if (category != null) filter.put("category",  category);
            if (availStr != null) filter.put("available", Boolean.parseBoolean(availStr));
            return json(mapper, repo.findAll("stockItems", filter));
        };
    }

    /* ── STOCK_ADD ──────────────────────────────────────────────────────── */
    public NktOperationHandler stockAdd() {
        return (data, userId, repo, mapper) -> {
            String storeId = resolveStoreId(userId, repo);
            int    qty     = data.get("qty")   != null ? Integer.parseInt(str(data, "qty"))   : 0;
            double price   = data.get("price") != null ? Double.parseDouble(str(data, "price")) : 0.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("storeId",   storeId);
            item.put("name",      str(data, "name"));
            item.put("sub",       str(data, "sub"));
            item.put("price",     price);
            item.put("emoji",     str(data, "emoji"));
            item.put("category",  str(data, "category"));
            item.put("qty",       qty);
            item.put("available", qty > 0);
            item.put("custom",    true);
            item.put("status",    "ACTIVE");
            item.put("createdAt", java.time.LocalDateTime.now().toString());
            item.put("updatedAt", java.time.LocalDateTime.now().toString());
            item.put("createdBy", userId);
            return json(mapper, repo.insert("stockItems", item));
        };
    }

    /* ── STOCK_CATEGORIES ───────────────────────────────────────────────── */
    public NktOperationHandler stockCategories() {
        return (data, userId, repo, mapper) -> {
            String storeId = resolveStoreId(userId, repo);
            List<String> cats = repo.findAll("stockItems", Map.of("storeId", storeId, "status", "ACTIVE"))
                    .stream()
                    .map(i -> i.get("category") != null ? i.get("category").toString() : null)
                    .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
            return json(mapper, cats);
        };
    }

    /* ── STOCK_UPDATE ───────────────────────────────────────────────────── */
    public NktOperationHandler stockUpdate() {
        return (data, userId, repo, mapper) -> {
            String itemId = str(data, "itemId");
            repo.findById("stockItems", itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
            Map<String, Object> updates = new LinkedHashMap<>();
            if (data.get("name")      != null) updates.put("name",      str(data, "name"));
            if (data.get("price")     != null) updates.put("price",     Double.parseDouble(str(data, "price")));
            if (data.get("qty")       != null) updates.put("qty",       Integer.parseInt(str(data, "qty")));
            if (data.get("available") != null) updates.put("available", Boolean.parseBoolean(str(data, "available")));
            if (data.get("sub")       != null) updates.put("sub",       str(data, "sub"));
            if (data.get("emoji")     != null) updates.put("emoji",     str(data, "emoji"));
            updates.put("updatedAt", java.time.LocalDateTime.now().toString());
            repo.updateById("stockItems", itemId, updates);
            return json(mapper, repo.findById("stockItems", itemId).orElseThrow());
        };
    }

    /* ── STOCK_ADJUST_QTY ───────────────────────────────────────────────── */
    public NktOperationHandler stockAdjustQty() {
        return (data, userId, repo, mapper) -> {
            String itemId = str(data, "itemId");
            int    delta  = Integer.parseInt(str(data, "delta"));
            Map<String, Object> item = repo.findById("stockItems", itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
            int newQty = Integer.parseInt(item.get("qty").toString()) + delta;
            if (newQty < 0) throw new RuntimeException("Stock cannot go below 0");
            repo.updateById("stockItems", itemId,
                    Map.of("qty", newQty, "available", newQty > 0,
                           "updatedAt", java.time.LocalDateTime.now().toString()));
            return json(mapper, repo.findById("stockItems", itemId).orElseThrow());
        };
    }

    /* ── STOCK_TOGGLE_AVAILABILITY ──────────────────────────────────────── */
    public NktOperationHandler stockToggleAvailability() {
        return (data, userId, repo, mapper) -> {
            String  itemId = str(data, "itemId");
            boolean avail  = Boolean.parseBoolean(str(data, "available"));
            repo.findById("stockItems", itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
            repo.updateById("stockItems", itemId,
                    Map.of("available", avail, "updatedAt", java.time.LocalDateTime.now().toString()));
            return json(mapper, repo.findById("stockItems", itemId).orElseThrow());
        };
    }

    /* ── STOCK_DELETE ───────────────────────────────────────────────────── */
    public NktOperationHandler stockDelete() {
        return (data, userId, repo, mapper) -> {
            String itemId = str(data, "itemId");
            Map<String, Object> item = repo.findById("stockItems", itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
            if (!Boolean.TRUE.equals(item.get("custom")))
                throw new RuntimeException("Only custom items can be deleted");
            repo.updateById("stockItems", itemId,
                    Map.of("status", "DELETED", "updatedAt", java.time.LocalDateTime.now().toString()));
            return json(mapper, Map.of("message", "Item removed from catalogue"));
        };
    }

    /* ── LOCATION_REVERSE_GEOCODE ───────────────────────────────────────── */
    public NktOperationHandler reverseGeocode() {
        return (data, userId, repo, mapper) ->
                json(mapper, Map.of(
                        "latitude",  str(data, "latitude"),
                        "longitude", str(data, "longitude"),
                        "address",   "Address lookup requires Maps API integration",
                        "city", "Unknown", "state", "Unknown", "pincode", "000000"));
    }

    /* ── LOCATION_GLOBAL_SEARCH ─────────────────────────────────────────── */
    public NktOperationHandler globalSearch() {
        return (data, userId, repo, mapper) -> {
            String q = str(data, "q");
            String cat = str(data, "categoryId");
            String lc  = q != null ? q.toLowerCase() : "";

            List<Map<String, Object>> stores = repo.findAll("stores", Map.of("status", "ACTIVE"))
                    .stream()
                    .filter(s -> s.get("name") != null && s.get("name").toString().toLowerCase().contains(lc))
                    .filter(s -> cat == null || cat.equals(s.get("categoryId")))
                    .collect(Collectors.toList());

            List<Map<String, Object>> items = repo.findAll("stockItems", Map.of("status", "ACTIVE"))
                    .stream()
                    .filter(i -> i.get("name") != null && i.get("name").toString().toLowerCase().contains(lc))
                    .collect(Collectors.toList());

            return json(mapper, Map.of(
                    "query", q, "stores", stores, "items", items,
                    "totalResults", stores.size() + items.size()));
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                  * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
