package com.service.handlers;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.NktDynamicRepository;

import lombok.extern.slf4j.Slf4j;

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
	
	@Value("${image.basepath}")
	private String basePath;

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
//    public NktOperationHandler nearbyStores() {
//        return (data, userId, repo, mapper, def) -> {
//            double lat    = Double.parseDouble(str(data, "latitude"));
//            double lon    = Double.parseDouble(str(data, "longitude"));
//            double radius = data.get("radiusKm") != null ? Double.parseDouble(str(data, "radiusKm")) : 5.0;
//
//            List<Map<String, Object>> nearby = repo.findAll("stores", Map.of("status", "ACTIVE"))
//                    .stream()
//                    .filter(s -> {
//                        Object addrObj = s.get("address");
//                        if (!(addrObj instanceof Map)) return false;
//                        @SuppressWarnings("unchecked")
//                        Map<String, Object> addr = (Map<String, Object>) addrObj;
//                        Object sLat = addr.get("latitude"), sLon = addr.get("longitude");
//                        if (sLat == null || sLon == null) return false;
//                        return haversine(lat, lon,
//                                Double.parseDouble(sLat.toString()),
//                                Double.parseDouble(sLon.toString())) <= radius;
//                    })
//                    .sorted(Comparator.comparingDouble(s -> {
//                        @SuppressWarnings("unchecked")
//                        Map<String, Object> addr = (Map<String, Object>) s.get("address");
//                        return haversine(lat, lon,
//                                Double.parseDouble(addr.get("latitude").toString()),
//                                Double.parseDouble(addr.get("longitude").toString()));
//                    }))
//                    .collect(Collectors.toList());
//            return json(mapper, nearby);
//        };
//    }

    
    public NktOperationHandler nearbyStores() {
        return (data, userId, repo, mapper, def) -> {

            // ✅ Validate input
            if (str(data, "latitude") == null || str(data, "longitude") == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "Latitude and Longitude are required"
                ));
            }

            double lat;
            double lon;

            try {
                lat = Double.parseDouble(str(data, "latitude"));
                lon = Double.parseDouble(str(data, "longitude"));
            } catch (Exception e) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "Invalid latitude/longitude format"
                ));
            }

            double radius = data.get("radiusKm") != null
                    ? Double.parseDouble(str(data, "radiusKm"))
                    : 5.0;

            List<Map<String, Object>> nearby = repo.findAll(def.getCollection(), Map.of("status", "ACTIVE","typeOfStore",str(data, "typesOfStore")))
                    .stream()
                    .map(store -> {
                        Object addrObj = store.get("location");
						
                        if (!(addrObj instanceof Map))
							return null;

                        @SuppressWarnings("unchecked")
                        Map<String, Object> addr = (Map<String, Object>) addrObj;

                        Object sLat = addr.get("latitude");
                        Object sLon = addr.get("longitude");

                        if (sLat == null || sLon == null) return null;

                        double distance = haversine(
                                lat, lon,
                                Double.parseDouble(sLat.toString()),
                                Double.parseDouble(sLon.toString())
                        );

                        // ✅ filter by radius
						if (distance > radius)
							return null;

						Map<String, Object> result = new LinkedHashMap<>(store);

						// ✅ Convert logo (only first image for performance)
						List<Map<String, Object>> logos = (List<Map<String, Object>>) store.get("logo");
						if (logos != null && !logos.isEmpty()) {
						    Map<String, Object> img = logos.get(0);

						    Map<String, Object> newImg = new LinkedHashMap<>();
						    newImg.put("filename", img.get("filename"));
						    newImg.put("base64", toBase64(img.get("localPath").toString()));

						    result.put("logo", List.of(newImg));
						}

						// ✅ Convert banner (only first image)
						List<Map<String, Object>> banners = (List<Map<String, Object>>) store.get("bannerImage");
						if (banners != null && !banners.isEmpty()) {
						    Map<String, Object> img = banners.get(0);

						    Map<String, Object> newImg = new LinkedHashMap<>();
						    newImg.put("filename", img.get("filename"));
						    newImg.put("base64", toBase64(img.get("localPath").toString()));

						    result.put("bannerImage", List.of(newImg));
						}

						// ✅ distance
						result.put("distanceKm", distance);

						return result;
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingDouble(s -> (double) s.get("distanceKm")))
                    .limit(50) // ✅ avoid huge response
                    .collect(Collectors.toList());

            return json(mapper, Map.of(
                    "data", nearby,
                    "count", nearby.size(),
                    "statusCode", "N200",
                    "statusDesc", "Success"
            ));
        };
    }
    
	/****
	 * commented for stores categories and subcategories as it requires more complex
	 * data structure and handling, can be implemented in future if needed
	 ****/
//    public NktOperationHandler storeWithCategories() {
//        return (data, userId, repo, mapper, def) -> {
//
//            String storeId = str(data, "storeId");
//
//            if (storeId == null || storeId.isBlank()) {
//                return json(mapper, Map.of(
//                        "statusCode", "N400",
//                        "statusDesc", "storeId is required"
//                ));
//            }
//
//            // ✅ 1. Fetch Store
//            Map<String, Object> store = repo
//                    .findOne("stores", "storeId", storeId)
//                    .orElse(null);
//
//            if (store == null) {
//                return json(mapper, Map.of(
//                        "statusCode", "N404",
//                        "statusDesc", "Store not found"
//                ));
//            }
//
//            List<Map<String, Object>> storeCategories =
//                    (List<Map<String, Object>>) store.getOrDefault("categories", new ArrayList<>());
//
//            if (storeCategories.isEmpty()) {
//                store.put("categories", Collections.emptyList());
//                return json(mapper, Map.of(
//                        "data", store,
//                        "statusCode", "N200",
//                        "statusDesc", "Success"
//                ));
//            }
//
//            // ✅ 2. Fetch all categories & subcategories (bulk fetch)
//            List<Map<String, Object>> allCategories =
//                    repo.findAll("categories", Map.of("status", "ACTIVE"));
//
//            List<Map<String, Object>> allSubCategories =
//                    repo.findAll("subcategories", Map.of("status", "ACTIVE"));
//
//            Map<String, Map<String, Object>> categoryMap = allCategories.stream()
//                    .collect(Collectors.toMap(
//                            c -> c.get("categoryId").toString(),
//                            c -> c
//                    ));
//
//            Map<String, Map<String, Object>> subCategoryMap = allSubCategories.stream()
//                    .collect(Collectors.toMap(
//                            s -> s.get("subcategoryId").toString(),
//                            s -> s
//                    ));
//
//            // ✅ 3. Enrich categories
//            List<Map<String, Object>> enrichedCategories = new ArrayList<>();
//
//            for (Map<String, Object> sc : storeCategories) {
//
//                String catId = sc.get("categoryId").toString();
//                Map<String, Object> masterCat = categoryMap.get(catId);
//
//                if (masterCat == null) continue;
//
//                Map<String, Object> newCat = new LinkedHashMap<>();
//                newCat.put("categoryId", catId);
//                newCat.put("name", masterCat.get("name"));
//
//                // ✅ Add category icons
//                newCat.put("icon", enrichImages(masterCat.get("icon")));
//
//                // ✅ Subcategories
//                List<Map<String, Object>> subs =
//                        (List<Map<String, Object>>) sc.getOrDefault("subCategories", new ArrayList<>());
//
//                List<Map<String, Object>> enrichedSubs = new ArrayList<>();
//
//                for (Map<String, Object> sub : subs) {
//
//                    String subId = sub.get("subcategoryId").toString();
//                    Map<String, Object> masterSub = subCategoryMap.get(subId);
//
//                    if (masterSub == null) continue;
//
//                    Map<String, Object> newSub = new LinkedHashMap<>();
//                    newSub.put("subcategoryId", subId);
//                    newSub.put("name", masterSub.get("name"));
//
//                    // ✅ Add subcategory icons
//                    newSub.put("icon", enrichImages(masterSub.get("icon")));
//
//                    enrichedSubs.add(newSub);
//                }
//
//                newCat.put("subCategories", enrichedSubs);
//                enrichedCategories.add(newCat);
//            }
//
//            // ✅ Replace store categories
//            store.put("categories", enrichedCategories);
//
//            return json(mapper, Map.of(
//                    "data", store,
//                    "statusCode", "N200",
//                    "statusDesc", "Success"
//            ));
//        };
//    }
//    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> enrichImages(Object imagesObj) {

        if (!(imagesObj instanceof List<?>)) return Collections.emptyList();

        List<Map<String, Object>> images = (List<Map<String, Object>>) imagesObj;

        for (Map<String, Object> img : images) {

            String path = (String) img.get("localPath");

            // OPTION 1: Base64
            img.put("base64", toBase64(path));

            // OPTION 2 (recommended): URL
            // img.put("url", baseUrl + path);
        }

        return images;
    }
//    
    
    
    
    public NktOperationHandler getCategoriesByStore() {
        return (data, userId, repo, mapper, def) -> {

            String storeId = str(data, "storeId");

            if (storeId == null || storeId.isBlank()) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "storeId is required"
                ));
            }

            Map<String, Object> store = repo
                    .findOne("stores", "storeId", storeId)
                    .orElse(null);

            if (store == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Store not found"
                ));
            }

            List<Map<String, Object>> storeCategories =
                    (List<Map<String, Object>>) store.getOrDefault("categories", new ArrayList<>());

            if (storeCategories.isEmpty()) {
                return json(mapper, Map.of(
                        "data", Collections.emptyList(),
                        "count", 0,
                        "statusCode", "N200"
                ));
            }

            // Fetch master categories
            List<Map<String, Object>> allCategories =
                    repo.findAll("categories", Map.of("status", "ACTIVE"));

            Map<String, Map<String, Object>> categoryMap = allCategories.stream()
                    .collect(Collectors.toMap(c -> c.get("categoryId").toString(), c -> c));

            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> sc : storeCategories) {

                String catId = sc.get("categoryId").toString();
                Map<String, Object> master = categoryMap.get(catId);

                if (master == null) continue;

                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("categoryId", catId);
                obj.put("name", master.get("name"));

                // ✅ icon
                obj.put("icon", enrichImages(master.get("icon")));

                result.add(obj);
            }

            return json(mapper, Map.of(
                    "data", result,
                    "count", result.size(),
                    "statusCode", "N200",
                    "statusDesc", "Success"
            ));
        };
    }
    
    public NktOperationHandler getSubCategories() {
        return (data, userId, repo, mapper, def) -> {

            String storeId = str(data, "storeId");
            String categoryId = str(data, "categoryId");

            if (storeId == null || categoryId == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "storeId & categoryId required"
                ));
            }

            Map<String, Object> store = repo
                    .findOne("stores", "storeId", storeId)
                    .orElse(null);

            if (store == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Store not found"
                ));
            }

            List<Map<String, Object>> categories =
                    (List<Map<String, Object>>) store.get("categories");

            Map<String, Object> targetCategory = categories.stream()
                    .filter(c -> categoryId.equals(c.get("categoryId")))
                    .findFirst()
                    .orElse(null);

            if (targetCategory == null) {
                return json(mapper, Map.of(
                        "data", Collections.emptyList(),
                        "statusCode", "N200"
                ));
            }

            List<Map<String, Object>> subCats =
                    (List<Map<String, Object>>) targetCategory.getOrDefault("subCategories", new ArrayList<>());

            // Fetch master subcategories
            List<Map<String, Object>> allSubs =
                    repo.findAll("sub_categories", Map.of("status", "ACTIVE"));

            Map<String, Map<String, Object>> subMap = allSubs.stream()
                    .collect(Collectors.toMap(s -> s.get("subcategoryId").toString(), s -> s));

            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> sc : subCats) {

                String subId = sc.get("subcategoryId").toString();
                Map<String, Object> master = subMap.get(subId);

                if (master == null) continue;

                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("subcategoryId", subId);
                obj.put("name", master.get("name"));

                obj.put("icon", enrichImages(master.get("icon")));

                result.add(obj);
            }

            return json(mapper, Map.of(
                    "data", result,
                    "count", result.size(),
                    "statusCode", "N200",
                    "statusDesc", "Success"
            ));
        };
    }
    
    public NktOperationHandler getProductsBySubCategory() {
        return (data, userId, repo, mapper, def) -> {

            String storeId = str(data, "storeId");
            String subCategoryId = str(data, "subCategoryId");

            if (storeId == null || subCategoryId == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "storeId & subCategoryId required"
                ));
            }

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("storeId", storeId);
            filter.put("subCategoryId", subCategoryId);
            filter.put("status", "ACTIVE");

            List<Map<String, Object>> products =
                    repo.findAll("stocks", filter);

            // ✅ enrich product images
            products.forEach(p -> enrichProductImage(p.get("image")));

            return json(mapper, Map.of(
                    "data", products,
                    "count", products.size(),
                    "statusCode", "N200",
                    "statusDesc", "Success"
            ));
        };
    }
    
    @SuppressWarnings("unchecked")
    private void enrichProductImage(Object imgObj) {

        if (!(imgObj instanceof Map)) return;

        Map<String, Object> img = (Map<String, Object>) imgObj;

        String basePath = (String) img.get("localPath");
        List<String> filenames = (List<String>) img.get("filename");

        if (basePath == null || filenames == null) return;

        List<Map<String, Object>> images = new ArrayList<>();

        for (String file : filenames) {
            String fullPath = basePath + "/" + file;

            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("filename", file);
            obj.put("base64", toBase64(fullPath)); // or URL

            images.add(obj);
        }

        img.put("files", images);
    }
    
    /* ── STORES_GET_PRODUCTS ────────────────────────────────────────────── */
    public NktOperationHandler storeProducts() {
        return (data, userId, repo, mapper, def) -> {
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
        return (data, userId, repo, mapper, def) ->
                json(mapper, Map.of("storeId", str(data, "storeId"),
                        "serviceId", str(data, "serviceId"),
                        "fromDate",  str(data, "fromDate"),
                        "slots",     List.of()));
    }

    /* ── STOCK_LIST ─────────────────────────────────────────────────────── */
    public NktOperationHandler stockList() {
        return (data, userId, repo, mapper, def) -> {
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
        return (data, userId, repo, mapper, def) -> {
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
        return (data, userId, repo, mapper, def) -> {
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
        return (data, userId, repo, mapper, def) -> {
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
        return (data, userId, repo, mapper, def) -> {
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
        return (data, userId, repo, mapper, def) -> {
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
        return (data, userId, repo, mapper, def) -> {
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
        return (data, userId, repo, mapper, def) ->
                json(mapper, Map.of(
                        "latitude",  str(data, "latitude"),
                        "longitude", str(data, "longitude"),
                        "address",   "Address lookup requires Maps API integration",
                        "city", "Unknown", "state", "Unknown", "pincode", "000000"));
    }

    /* ── LOCATION_GLOBAL_SEARCH ─────────────────────────────────────────── */
    public NktOperationHandler globalSearch() {
        return (data, userId, repo, mapper, def) -> {
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
    
    /* ── Types Of Stores in the groceries ─────────────────────────────────────────── */
    public NktOperationHandler typesOfStore() {
        return (data, userId, repo, mapper, def) -> {

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("status", "ACTIVE");

            List<Map<String, Object>> stores = repo.findAll(def.getCollection(), filter);

            // ✅ Safe empty handling
            if (stores == null || stores.isEmpty()) {
                return json(mapper, Map.of(
                        "data", Collections.emptyList(),
                        "count", 0,
                        "statusCode", "N200",
                        "statusDesc", "No stores found"
                ));
            }

            // ✅ Optional limit (protect API)
            stores = stores.stream()
                    .limit(100)
                    .collect(Collectors.toList());

            // ✅ Optional: enrich images (if same structure as stores)
            if ("stores".equalsIgnoreCase(def.getCollection())) {
                stores.forEach(this::enrichStoreImages);
            }

            return json(mapper, Map.of(
                    "data", stores,
                    "count", stores.size(),
                    "statusCode", "N200",
                    "statusDesc", "Success"
            ));
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
    
    private String toBase64(String relativePath) {
        try {
            String fullPath = basePath + relativePath;

            java.nio.file.Path filePath = java.nio.file.Paths.get(fullPath);

            if (!java.nio.file.Files.exists(filePath)) {
                log.warn("File not found: {}", fullPath);
                return null;
            }

            byte[] bytes = java.nio.file.Files.readAllBytes(filePath);
            String mime = java.nio.file.Files.probeContentType(filePath);

            return "data:" + mime + ";base64," +
                    Base64.getEncoder().encodeToString(bytes);

        } catch (Exception e) {
            log.error("Base64 conversion failed", e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private void enrichStoreImages(Map<String, Object> store) {

        // 👉 LOGO LIST
        Object logosObj = store.get("logo");
        if (logosObj instanceof List<?>) {
            List<Map<String, Object>> logos = (List<Map<String, Object>>) logosObj;

            for (Map<String, Object> img : logos) {
                String path = (String) img.get("localPath");
                img.put("base64", toBase64(path));
            }
        }

        // 👉 BANNER LIST
        Object bannersObj = store.get("bannerImage");
        if (bannersObj instanceof List<?>) {
            List<Map<String, Object>> banners = (List<Map<String, Object>>) bannersObj;

            for (Map<String, Object> img : banners) {
                String path = (String) img.get("localPath");
                img.put("base64", toBase64(path));
            }
        }
    }
}
