package com.service.handlers;

import java.time.LocalDateTime;
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
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.nkt.NktProcessDefinition;
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
//						    newImg.put("base64", toBase64(img.get("localPath").toString()));
						    newImg.put("url", img.get("localPath"));

						    result.put("logo", List.of(newImg));
						}

						// ✅ Convert banner (only first image)
						List<Map<String, Object>> banners = (List<Map<String, Object>>) store.get("bannerImage");
						if (banners != null && !banners.isEmpty()) {
						    Map<String, Object> img = banners.get(0);

						    Map<String, Object> newImg = new LinkedHashMap<>();
						    newImg.put("filename", img.get("filename"));
//						    newImg.put("base64", toBase64(img.get("localPath").toString()));
						    newImg.put("url", img.get("localPath"));

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
    
    @SuppressWarnings("unchecked")
    public NktOperationHandler getProductsBySubCategory() {

        return (data, userId, repo, mapper, def) -> {

            String storeId = str(data, "storeId");
            String categoryId = str(data, "categoryId");
            String subCategoryId = str(data, "subCategoryId");

            if (storeId == null || categoryId == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "storeId & categoryId required"
                ));
            }

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("storeId", storeId);
            filter.put("status", "ACTIVE");

            // If subCategoryId is provided, no need to read store document
            if (subCategoryId != null && !subCategoryId.isBlank()) {

                filter.put("subCategoryId", subCategoryId);

            } else {

                Map<String, Object> store = repo.findOne("stores", "storeId", storeId)
                        .orElse(null);

                if (store == null) {
                    return json(mapper, Map.of(
                            "statusCode", "N404",
                            "statusDesc", "Store not found"
                    ));
                }

                List<Map<String, Object>> categories =
                        (List<Map<String, Object>>) store.getOrDefault(
                                "categories",
                                Collections.emptyList());

                Map<String, Object> category = categories.stream()
                        .filter(c -> categoryId.equals(c.get("categoryId")))
                        .findFirst()
                        .orElse(null);

                if (category == null) {
                    return json(mapper, Map.of(
                            "statusCode", "N404",
                            "statusDesc", "Category not found"
                    ));
                }

                List<Map<String, Object>> subCategories =
                        (List<Map<String, Object>>) category.getOrDefault(
                                "subCategories",
                                Collections.emptyList());

                List<String> subCategoryIds = subCategories.stream()
                        .map(sc -> String.valueOf(sc.get("subcategoryId")))
                        .toList();

                if (subCategoryIds.isEmpty()) {
                    return json(mapper, Map.of(
                            "data", Collections.emptyList(),
                            "count", 0,
                            "statusCode", "N200",
                            "statusDesc", "No Products"
                    ));
                }

                filter.put("subCategoryId", Map.of("$in", subCategoryIds));
            }

            List<Map<String, Object>> products = repo.findAll("stocks", filter);

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
    
    public NktOperationHandler addCategory() {

        return (data, userId, repo, mapper, def) -> {

            String typeOfStore = str(data, "typeOfStore");

            Map<String, Object> name =
                    (Map<String, Object>) data.get("name");

            if (name == null || typeOfStore == null) {

                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "Category name and typeOfStore are required"
                ));
            }

            String categoryName = str(name, "en");

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("name.en", categoryName);
            filter.put("typeOfStore", typeOfStore);
            filter.put("status", "Active");

            if (!repo.findAll(def.getCollection(), filter).isEmpty()) {

                return json(mapper, Map.of(
                        "statusCode", "N409",
                        "statusDesc", "Category already exists"
                ));
            }

            String categoryId = "CAT_" + String.format("%05d",
                    repo.findAll(def.getCollection(), Collections.emptyMap()).size() + 1);

            Map<String, Object> document = new LinkedHashMap<>(data);

            document.put("categoryId", categoryId);
            document.put("status", "Active");
            document.put("createdBy", userId);
            document.put("updatedBy", userId);
            document.put("createdAt", LocalDateTime.now());
            document.put("updatedAt", LocalDateTime.now());

            repo.insert(def.getCollection(), document);

            return json(mapper, Map.of(
                    "statusCode", "N200",
                    "statusDesc", "Category created successfully",
                    "data", document
            ));
        };
    }
    
    public NktOperationHandler updateCategory() {

        return (data, userId, repo, mapper, def) -> {

            String id = str(data, "id");
            String typeOfStore = str(data, "typeOfStore");

            if (id == null || id.isBlank()) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "id is required"
                ));
            }

            Map<String, Object> existing = repo
                    .findOne(def.getCollection(), "_id", id)
                    .orElse(null);

            if (existing == null) {
                return json(mapper, Map.of(
                        "statusCode", "N404",
                        "statusDesc", "Category not found"
                ));
            }

            Map<String, Object> name =
                    (Map<String, Object>) data.get("name");

            if (name != null) {

                String categoryName = str(name, "en");

                Map<String, Object> filter = new LinkedHashMap<>();
                filter.put("name.en", categoryName);
                filter.put("typeOfStore", typeOfStore);
                filter.put("status", "Active");

                List<Map<String, Object>> duplicates =
                        repo.findAll(def.getCollection(), filter);

                boolean duplicateExists = duplicates.stream()
                        .anyMatch(d -> !id.equals(String.valueOf(d.get("id"))));

                if (duplicateExists) {
                    return json(mapper, Map.of(
                            "statusCode", "N400",
                            "statusDesc", "Category already exists"
                    ));
                }
            }

            // Merge request into existing document
            existing.putAll(data);

            // Preserve audit fields
            existing.put("createdAt", existing.get("createdAt"));
            existing.put("createdBy", existing.get("createdBy"));

            // Update audit fields
            existing.put("updatedBy", userId);
            existing.put("updatedAt", LocalDateTime.now());

            repo.updateById(def.getCollection(), id, existing);

            return json(mapper, Map.of(
                    "statusCode", "N200",
                    "statusDesc", "Category updated successfully",
                    "data", existing
            ));
        };
    }
    
   
    public NktOperationHandler getCategories() {

        return (data, userId, repo, mapper, def) -> {

            String typeOfStore = str(data, "typeOfStore");
            String searchText = str(data, "searchText");

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("status", "Active");

            if (typeOfStore != null && !typeOfStore.isBlank()) {
                filter.put("typeOfStore", typeOfStore);
            }

            if (searchText != null && !searchText.isBlank()) {

            	filter.put("$or", List.of(Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
						Map.of("name.ta", Map.of("$regex", searchText, "$options", "i"))));
            }

            List<Map<String, Object>> categories =
                    repo.findAll(def.getCollection(), filter);

//            // Search in English & Tamil
//            if (searchText != null && !searchText.isBlank()) {
//
//                String search = searchText.toLowerCase();
//
//                categories = categories.stream()
//                        .filter(cat -> {
//
//                            Map<String, Object> name =
//                                    (Map<String, Object>) cat.get("name");
//
//                            if (name == null) {
//                                return false;
//                            }
//
//                            String en = String.valueOf(
//                                    name.getOrDefault("en", "")
//                            ).toLowerCase();
//
//                            String ta = String.valueOf(
//                                    name.getOrDefault("ta", "")
//                            ).toLowerCase();
//
//                            return en.contains(search)
//                                    || ta.contains(search);
//
//                        })
//                        .toList();
//            }

            return json(mapper, Map.of(
                    "statusCode", "N200",
                    "statusDesc", "Success",
                    "count", categories.size(),
                    "data", categories
            ));
        };
    }

    private Map<String, Object> validateReferences(Map<String, Object> data, NktProcessDefinition def,
    		NktDynamicRepository repo, boolean b) {

     List<Map<String, String>> subCollections = def.getSubCollection();

     if (CollectionUtils.isEmpty(subCollections)) {
         return null;
     }

     for (Map<String, String> sub : subCollections) {

         String refCollection = sub.get("subCollectionname");
         String refField = sub.get("subCollectionField");
         String columnField = sub.get("subCollectionColumnField") != null ? sub.get("subCollectionColumnField") : "_id";

         if (!data.containsKey(refField)) {
               return Map.of("statusCode", "N400", "statusDesc", refField + " is required");
         }

         Object refValue = data.get(refField);

         if (refValue == null) {
             return Map.of("statusCode", "N400", "statusDesc", refField + " cannot be null");
         }

         Map<String, Object> parent = repo.findOne(refCollection, columnField, refValue).orElse(null);

         if (parent == null || !"Active".equals(parent.get("status"))) {
             return Map.of("statusCode", "N404", "statusDesc",
                     "Referenced " + refCollection + " not found or inactive");
         }
     }

     return null;
 }

 // subcategories
 public NktOperationHandler addSubCategory() {

     return (data, userId, repo, mapper, def) -> {

         String categoryId = str(data, "categoryId");
         Map<String, Object> name = (Map<String, Object>) data.get("name");

         if (categoryId == null || name == null) {
             return json(mapper, Map.of("statusCode", "N400", "statusDesc", "categoryId and name are required"));
         }

         Map<String, Object> refError = validateReferences(data, def, repo, true);
        
         if (refError != null) {
             return json(mapper, refError);
         }

         String subCategoryName = str(name, "en");

         Map<String, Object> filter = new LinkedHashMap<>();
         filter.put("name.en", subCategoryName);
         filter.put("categoryId", categoryId);
         filter.put("status", "Active");

         if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
             return json(mapper,
                     Map.of("statusCode", "N409", "statusDesc", "Sub category already exists under this category"));
         }

         String subCategoryCode = "SUB"
                 + String.format("%03d", repo.findAll(def.getCollection(), Collections.emptyMap()).size() + 1);

         Map<String, Object> document = new LinkedHashMap<>(data);

         document.put("subCategoryCode", subCategoryCode);
         document.put("status", "Active");
         document.put("createdBy", userId);
         document.put("updatedBy", userId);
         document.put("createdAt", LocalDateTime.now());
         document.put("updatedAt", LocalDateTime.now());

         repo.insert(def.getCollection(), document);

         return json(mapper,
                 Map.of("statusCode", "N200", "statusDesc", "Sub category created successfully", "data", document));
     };
 }


public NktOperationHandler updateSubCategory() {

     return (data, userId, repo, mapper, def) -> {

         String id = str(data, "id");

         if (id == null || id.isBlank()) {
             return json(mapper, Map.of("statusCode", "N400", "statusDesc", "id is required"));
         }

         Map<String, Object> existing = repo.findOne(def.getCollection(), "_id", id).orElse(null);

         if (existing == null) {
             return json(mapper, Map.of("statusCode", "N404", "statusDesc", "Sub category not found"));
         }

         Map<String, Object> refError = validateReferences(data, def, repo, false);
       
         if (refError != null) {
             return json(mapper, refError);
         }

         Map<String, Object> name = (Map<String, Object>) data.get("name");
       
         String subCategoryName = name != null ? str(name, "en") : null;

         String categoryId = data.containsKey("categoryId")
                 ? str(data, "categoryId")
                 : String.valueOf(existing.get("categoryId"));

         if (subCategoryName != null) {

             Map<String, Object> filter = new LinkedHashMap<>();
             filter.put("categoryId", categoryId);
             filter.put("name.en", subCategoryName);
             filter.put("status", "Active");
             filter.put("_id", Map.of("$ne", id));   // exclude self at the DB level

             if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
                 return json(mapper, Map.of(
                         "statusCode", "N400",
                         "statusDesc", "Sub category already exists under this category"
                 ));
             }
         }

         existing.putAll(data);

         existing.put("createdAt", existing.get("createdAt"));
         existing.put("createdBy", existing.get("createdBy"));

         existing.put("updatedBy", userId);
         existing.put("updatedAt", LocalDateTime.now());

         repo.updateById(def.getCollection(), id, existing);

         return json(mapper, Map.of(
                 "statusCode", "N200",
                 "statusDesc", "Sub category updated successfully",
                 "data", existing
         ));
     };
 }
    public NktOperationHandler searchSubCategories() {

        return (data, userId, repo, mapper, def) -> {

            String categoryId = str(data, "categoryId");
            String searchText = str(data, "searchText");

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("status", "Active");

            if (categoryId != null && !categoryId.isBlank()) {
                filter.put("categoryId", categoryId);
            }

            if (searchText != null && !searchText.isBlank()) {
            	filter.put("$or", List.of(Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
						Map.of("name.ta", Map.of("$regex", searchText, "$options", "i"))));
            }

            List<Map<String, Object>> subCategories =
                    repo.findAll(def.getCollection(), filter);

            return json(mapper, Map.of(
                    "statusCode", "N200",
                    "statusDesc", "Success",
                    "count", subCategories.size(),
                    "data", subCategories
            ));
        };
    }
    
    
 // products
    public NktOperationHandler addProduct() {

        return (data, userId, repo, mapper, def) -> {

            String categoryId = str(data, "categoryId");
            String subCategoryId = str(data, "subCategoryId");
           
            Map<String, Object> name = (Map<String, Object>) data.get("name");
            
            String productName = name != null ? str(name, "en") : null;

            if (categoryId == null || subCategoryId == null || productName == null) {
                return json(mapper, Map.of("statusCode", "N400",
                        "statusDesc", "categoryId, subCategoryId and productName are required"));
            }

            // Generic existence/active checks driven by config (sections, subsections)
            Map<String, Object> refError = validateReferences(data, def, repo, true);
            if (refError != null) {
                return json(mapper, refError);
            }

            // Relationship check the generic loop can't express: subCategory must belong to category
            Map<String, Object> subCategory = repo.findOne("subsections", "_id", subCategoryId).orElse(null);

            if (subCategory == null || !categoryId.equals(String.valueOf(subCategory.get("categoryId")))) {
                return json(mapper, Map.of("statusCode", "N400",
                        "statusDesc", "Sub category does not belong to the given category"));
            }

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("categoryId", categoryId);
            filter.put("subCategoryId", subCategoryId);
            filter.put("name.en", productName);
            filter.put("status", "Active");

            if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
                return json(mapper, Map.of("statusCode", "N400",
                        "statusDesc", "Product already exists under this sub category"));
            }

            String productCode = "PRD"
                    + String.format("%03d", repo.findAll(def.getCollection(), Collections.emptyMap()).size() + 1);

            Map<String, Object> document = new LinkedHashMap<>(data);

            document.put("productCode", productCode);
            document.put("name", name);
            document.put("status", "Active");
            document.put("createdBy", userId);
            document.put("updatedBy", userId);
            document.put("createdAt", LocalDateTime.now());
            document.put("updatedAt", LocalDateTime.now());

            repo.insert(def.getCollection(), document);

            return json(mapper, Map.of("statusCode", "N200",
                    "statusDesc", "Product created successfully", "data", document));
        };
    }

    public NktOperationHandler updateProduct() {

        return (data, userId, repo, mapper, def) -> {

            String id = str(data, "id");

            if (id == null || id.isBlank()) {
                return json(mapper, Map.of("statusCode", "N400", "statusDesc", "id is required"));
            }

            Map<String, Object> existing = repo.findOne(def.getCollection(), "_id", id).orElse(null);

            if (existing == null) {
                return json(mapper, Map.of("statusCode", "N404", "statusDesc", "Product not found"));
            }

            // Only validate refs actually being changed on this partial update
            Map<String, Object> refError = validateReferences(data, def, repo, false);
            if (refError != null) {
                return json(mapper, refError);
            }

            String categoryId = data.containsKey("categoryId")
                    ? str(data, "categoryId")
                    : String.valueOf(existing.get("categoryId"));
            String subCategoryId = data.containsKey("subCategoryId")
                    ? str(data, "subCategoryId")
                    : String.valueOf(existing.get("subCategoryId"));

            // If either category or subCategory is changing, re-validate the relationship
            if (data.containsKey("categoryId") || data.containsKey("subCategoryId")) {

                Map<String, Object> subCategory = repo.findOne("subsections", "_id", subCategoryId).orElse(null);

                if (subCategory == null || !categoryId.equals(String.valueOf(subCategory.get("categoryId")))) {
                    return json(mapper, Map.of("statusCode", "N404",
                            "statusDesc", "Sub category does not belong to the given category"));
                }
            }

            Map<String, Object> name = (Map<String, Object>) data.get("name");

            if (name != null) {

                Map<String, Object> filter = new LinkedHashMap<>();
                filter.put("categoryId", categoryId);
                filter.put("subCategoryId", subCategoryId);
                filter.put("name", name);
                filter.put("status", "Active");
                filter.put("_id", Map.of("$ne", id));   // exclude self at the DB level

                if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
                    return json(mapper, Map.of("statusCode", "N400",
                            "statusDesc", "Product already exists under this sub category"));
                }
            }

            existing.putAll(data);

            existing.put("createdAt", existing.get("createdAt"));
            existing.put("createdBy", existing.get("createdBy"));

            existing.put("updatedBy", userId);
            existing.put("updatedAt", LocalDateTime.now());

            repo.updateById(def.getCollection(), id, existing);

            return json(mapper, Map.of("statusCode", "N200",
                    "statusDesc", "Product updated successfully", "data", existing));
        };
    }

    public NktOperationHandler getProducts() {

        return (data, userId, repo, mapper, def) -> {

            String categoryId = str(data, "categoryId");
            String subCategoryId = str(data, "subCategoryId");
            String searchText = str(data, "searchText");

            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("status", "Active");

            if (categoryId != null && !categoryId.isBlank()) {
                filter.put("categoryId", categoryId);
            }

            if (subCategoryId != null && !subCategoryId.isBlank()) {
                filter.put("subCategoryId", subCategoryId);
            }

            if (searchText != null && !searchText.isBlank()) {
            	filter.put("$or", List.of(Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
						Map.of("name.ta", Map.of("$regex", searchText, "$options", "i"))));
            }

            List<Map<String, Object>> products = repo.findAll(def.getCollection(), filter);

            return json(mapper, Map.of("statusCode", "N200", "statusDesc", "Success",
                    "count", products.size(), "data", products));
        };
    }
    
	public NktOperationHandler addBrand() {

		return (data, userId, repo, mapper, def) -> {

			String productId = str(data, "productId");

			Map<String, Object> name = (Map<String, Object>) data.get("name");

			String brandName = name != null ? str(name, "en") : null;

			if (productId == null || brandName == null) {
				return json(mapper, Map.of("statusCode", "N400", "statusDesc", "productId and brandName are required"));
			}

			Map<String, Object> refError = validateReferences(data, def, repo, true);
			
			if (refError != null) {
				return json(mapper, refError);
			}


			Map<String, Object> filter = new LinkedHashMap<>();
			filter.put("productId", productId);
			filter.put("name.en", brandName);
			filter.put("status", "Active");

			if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
				return json(mapper,
						Map.of("statusCode", "N409", "statusDesc", "Brand already exists for this product"));
			}

			String brandCode = "BR"
					+ String.format("%03d", repo.findAll(def.getCollection(), Collections.emptyMap()).size() + 1);

			Map<String, Object> document = new LinkedHashMap<>(data);

			document.put("productId", productId);
			document.put("name", name);
			document.put("brandCode", brandCode);
			document.put("status", "Active");
			document.put("createdBy", userId);
			document.put("updatedBy", userId);
			document.put("createdAt", LocalDateTime.now());
			document.put("updatedAt", LocalDateTime.now());

			repo.insert(def.getCollection(), document);

			return json(mapper,
					Map.of("statusCode", "N200", "statusDesc", "Brand created successfully", "data", document));
		};
	}

	public NktOperationHandler updateBrand() {

		return (data, userId, repo, mapper, def) -> {

			String id = str(data, "id");

			if (id == null || id.isBlank()) {
				return json(mapper, Map.of("statusCode", "N400", "statusDesc", "id is required"));
			}

			Map<String, Object> existing = repo.findOne(def.getCollection(), "_id", id).orElse(null);

			if (existing == null) {
				return json(mapper, Map.of("statusCode", "N404", "statusDesc", "Brand not found"));
			}

			Map<String, Object> refError = validateReferences(data, def, repo, false);
			
			if (refError != null) {
				return json(mapper, refError);
			}

			Map<String, Object> name = (Map<String, Object>) data.get("name");

			String brandName = name != null ? str(name, "en") : null;
			String productId = data.containsKey("productId") ? str(data, "productId")
					: String.valueOf(existing.get("productId"));

			if (brandName != null) {

				Map<String, Object> filter = new LinkedHashMap<>();
				filter.put("productId", productId);
				filter.put("name.en", brandName);
				filter.put("status", "Active");
				filter.put("_id", Map.of("$ne", id)); // exclude self at the DB level

				if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
					return json(mapper,
							Map.of("statusCode", "N400", "statusDesc", "Brand already exists for this product"));
				}
			}

			existing.putAll(data);

			existing.put("createdAt", existing.get("createdAt"));
			existing.put("createdBy", existing.get("createdBy"));

			existing.put("updatedBy", userId);
			existing.put("updatedAt", LocalDateTime.now());

			repo.updateById(def.getCollection(), id, existing);

			return json(mapper,
					Map.of("statusCode", "N200", "statusDesc", "Brand updated successfully", "data", existing));
		};
	}

	public NktOperationHandler getBrands() {

		return (data, userId, repo, mapper, def) -> {

			String productId = str(data, "productId");
			String searchText = str(data, "searchText");

			Map<String, Object> filter = new LinkedHashMap<>();
			filter.put("status", "Active");

			if (productId != null && !productId.isBlank()) {
				filter.put("productId", productId);
			}

			if (searchText != null && !searchText.isBlank()) {

				filter.put("$or", List.of(Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
						Map.of("name.ta", Map.of("$regex", searchText, "$options", "i"))));
			}

			List<Map<String, Object>> brands = repo.findAll(def.getCollection(), filter);

			return json(mapper,
					Map.of("statusCode", "N200", "statusDesc", "Success", "count", brands.size(), "data", brands));
		};
	}
	
	public NktOperationHandler addQuantity() {

	    return (data, userId, repo, mapper, def) -> {

	        String productId = str(data, "productId");
	        String brandId = str(data, "brandId");
	        Object quantityVal = data.get("quantity");
	        String unit = str(data, "unit");
	        Object mrpVal = data.get("mrp");

	        if (productId == null || brandId == null || quantityVal == null
	                || unit == null || mrpVal == null) {
	            return json(mapper, Map.of("statusCode", "N400",
	                    "statusDesc", "productId, brandId, quantity, unit and mrp are required"));
	        }

	        // Config-driven existence/active checks (products <- productId, brand <- brandId)
	        Map<String, Object> refError = validateReferences(data, def, repo, true);
	        if (refError != null) {
	            return json(mapper, refError);
	        }

	        // Relationship check the generic loop can't express: brand must belong to product
	        Map<String, Object> product = repo.findOne("products", "_id", productId).orElse(null);
	        Map<String, Object> brand = repo.findOne("brand", "_id", brandId).orElse(null);

	        if (brand == null || !productId.equals(String.valueOf(brand.get("productId")))) {
	            return json(mapper, Map.of("statusCode", "N404",
	                    "statusDesc", "Brand does not belong to the given product"));
	        }

	        String productCode = String.valueOf(product.get("productCode"));
	        String brandCode = String.valueOf(brand.get("brandCode"));
	        String sku = productCode + "-" + brandCode + "-" + quantityVal + unit.toUpperCase();

	        Map<String, Object> filter = new LinkedHashMap<>();
	        filter.put("sku", sku);
	        filter.put("status", "Active");

	        if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
	            return json(mapper, Map.of("statusCode", "N409",
	                    "statusDesc", "This quantity already exists for the given product and brand"));
	        }

	        Map<String, Object> document = new LinkedHashMap<>(data);

	        document.put("sku", sku);
	        document.put("status", "Active");
	        document.put("createdBy", userId);
	        document.put("updatedBy", userId);
	        document.put("createdAt", LocalDateTime.now());
	        document.put("updatedAt", LocalDateTime.now());

	        repo.insert(def.getCollection(), document);

	        return json(mapper, Map.of("statusCode", "N200",
	                "statusDesc", "Quantity created successfully", "data", document));
	    };
	}

	public NktOperationHandler updateQuantity() {

	    return (data, userId, repo, mapper, def) -> {

	        String id = str(data, "id");

	        if (id == null || id.isBlank()) {
	            return json(mapper, Map.of("statusCode", "N400", "statusDesc", "id is required"));
	        }

	        Map<String, Object> existing = repo.findOne(def.getCollection(), "_id", id).orElse(null);

	        if (existing == null) {
	            return json(mapper, Map.of("statusCode", "N404", "statusDesc", "Quantity not found"));
	        }

	        // Only validate refs actually being reassigned on this partial update
	        Map<String, Object> refError = validateReferences(data, def, repo, false);
	        if (refError != null) {
	            return json(mapper, refError);
	        }

	        String productId = data.containsKey("productId")
	                ? str(data, "productId") : String.valueOf(existing.get("productId"));
	        String brandId = data.containsKey("brandId")
	                ? str(data, "brandId") : String.valueOf(existing.get("brandId"));

	        boolean identityChanging = data.containsKey("productId") || data.containsKey("brandId")
	                || data.containsKey("quantity") || data.containsKey("unit");

	        if (identityChanging) {

	            Map<String, Object> product = repo.findOne("products", "_id", productId).orElse(null);
	            Map<String, Object> brand = repo.findOne("brand", "_id", brandId).orElse(null);

	            if (brand == null || !productId.equals(String.valueOf(brand.get("productId")))) {
	                return json(mapper, Map.of("statusCode", "N404",
	                        "statusDesc", "Brand does not belong to the given product"));
	            }

	            Object quantityVal = data.getOrDefault("quantity", existing.get("quantity"));
	            String unit = data.containsKey("unit") ? str(data, "unit") : String.valueOf(existing.get("unit"));

	            String sku = product.get("productCode") + "-" + brand.get("brandCode")
	                    + "-" + quantityVal + unit.toUpperCase();

	            Map<String, Object> filter = new LinkedHashMap<>();
	            filter.put("sku", sku);
	            filter.put("status", "Active");
	            filter.put("_id", Map.of("$ne", id));

	            if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
	                return json(mapper, Map.of("statusCode", "N400",
	                        "statusDesc", "This quantity already exists for the given product and brand"));
	            }

	            data.put("sku", sku);
	        }

	        existing.putAll(data);

	        existing.put("createdAt", existing.get("createdAt"));
	        existing.put("createdBy", existing.get("createdBy"));

	        existing.put("updatedBy", userId);
	        existing.put("updatedAt", LocalDateTime.now());

	        repo.updateById(def.getCollection(), id, existing);

	        return json(mapper, Map.of("statusCode", "N200",
	                "statusDesc", "Quantity updated successfully", "data", existing));
	    };
	}

	public NktOperationHandler getQuantities() {

	    return (data, userId, repo, mapper, def) -> {

	        String productId = str(data, "productId");
	        String brandId = str(data, "brandId");
	        String searchText = str(data, "searchText");

	        Map<String, Object> filter = new LinkedHashMap<>();
	        filter.put("status", "Active");

	        if (productId != null && !productId.isBlank()) {
	            filter.put("productId", productId);
	        }

	        if (brandId != null && !brandId.isBlank()) {
	            filter.put("brandId", brandId);
	        }

	        if (searchText != null && !searchText.isBlank()) {
	        	filter.put("$or", List.of(Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
						Map.of("name.ta", Map.of("$regex", searchText, "$options", "i"))));
	        }

	        List<Map<String, Object>> quantities = repo.findAll(def.getCollection(), filter);

	        return json(mapper, Map.of("statusCode", "N200", "statusDesc", "Success",
	                "count", quantities.size(), "data", quantities));
	    };
	}
	
	
	public NktOperationHandler addStoreInventory() {

	    return (data, userId, repo, mapper, def) -> {

	        String storeId = str(data, "storeId");
	        String quantityId = str(data, "quantityId");
	        String stockName = str(data, "stockName");
	        Object sellingPriceObj = data.get("sellingPrice");
	        Object availableStockObj = data.get("availableStock");

	        if (storeId == null || quantityId == null
	                || sellingPriceObj == null || availableStockObj == null) {

	            return json(mapper, Map.of("statusCode", "N400",
	                    "statusDesc", "storeId, quantityId, sellingPrice and availableStock are required"));
	        }

	        Map<String, Object> refError = validateReferences(data, def, repo, true);
	        if (refError != null) {
	            return json(mapper, refError);
	        }

	        // Derive the full hierarchy from quantityId - this is the single source of truth
	        Map<String, Object> quantity = repo.findOne("quantity", "_id", quantityId).orElse(null);

	        if (quantity == null || !"Active".equals(quantity.get("status"))) {
	            return json(mapper, Map.of("statusCode", "N404", "statusDesc", "Quantity not found or inactive"));
	        }

	        String productId = String.valueOf(quantity.get("productId"));
	        String brandId = String.valueOf(quantity.get("brandId"));

	        Map<String, Object> product = repo.findOne("products", "_id", productId).orElse(null);

	        if (product == null || !"Active".equals(product.get("status"))) {
	            return json(mapper, Map.of("statusCode", "N404", "statusDesc", "Parent product not found or inactive"));
	        }

	        String categoryId = String.valueOf(product.get("categoryId"));
	        String subCategoryId = String.valueOf(product.get("subCategoryId"));

	        // If the client submitted hierarchy fields, they must match the derived chain -
	        // guards against a stale or tampered categoryId/subCategoryId/productId/brandId
	        if (!matchesIfPresent(data, "categoryId", categoryId)
	                || !matchesIfPresent(data, "subCategoryId", subCategoryId)
	                || !matchesIfPresent(data, "productId", productId)
	                || !matchesIfPresent(data, "brandId", brandId)) {

	            return json(mapper, Map.of("statusCode", "N400",
	                    "statusDesc", "Submitted category/subCategory/product/brand does not match the referenced quantity"));
	        }

	        Map<String, Object> filter = new LinkedHashMap<>();
	        filter.put("storeId", storeId);
	        filter.put("quantityId", quantityId);
	        filter.put("status", "Active");

	        if (!repo.findAll(def.getCollection(), filter).isEmpty()) {
	            return json(mapper, Map.of("statusCode", "N409",
	                    "statusDesc", "This quantity already exists in the store's inventory"));
	        }

	        Map<String, Object> document = new LinkedHashMap<>(data);

	        // Always set from the derived chain - never trust client-submitted hierarchy values directly
	        document.put("stockName", stockName);
	        document.put("categoryId", categoryId);
	        document.put("subCategoryId", subCategoryId);
	        document.put("productId", productId);
	        document.put("brandId", brandId);
	        document.put("quantityId", quantityId);
	        document.putIfAbsent("reservedStock", 0);
	        document.putIfAbsent("minimumStock", 0);
	        document.put("status", "Active");
	        document.put("createdBy", userId);
	        document.put("updatedBy", userId);
	        document.put("createdAt", LocalDateTime.now());
	        document.put("updatedAt", LocalDateTime.now());
	        document.put("lastUpdated", LocalDateTime.now());

	        repo.insert(def.getCollection(), document);

	        return json(mapper, Map.of("statusCode", "N200",
	                "statusDesc", "Store inventory record created successfully", "data", document));
	    };
	}

	// Returns true if the field is absent from the payload (nothing to check),
	// or present and equal to the derived value.
	private boolean matchesIfPresent(Map<String, Object> data, String field, String derivedValue) {
	    return !data.containsKey(field) || derivedValue.equals(str(data, field));
	}

	public NktOperationHandler updateStoreInventory() {

	    return (data, userId, repo, mapper, def) -> {

	        String id = str(data, "id");

	        if (id == null || id.isBlank()) {
	            return json(mapper, Map.of("statusCode", "N400", "statusDesc", "id is required"));
	        }

	        Map<String, Object> existing = repo.findOne(def.getCollection(), "_id", id).orElse(null);

	        if (existing == null) {
	            return json(mapper, Map.of("statusCode", "N404", "statusDesc", "Store inventory record not found"));
	        }

	        // storeId/quantityId/categoryId/subCategoryId/productId/brandId are structural
	        // identifiers, immutable through this endpoint - daily refresh is price/stock/
	        // batch/expiry only. Reassigning a store's inventory to a different variant
	        // should go through delete + add, not update.
	        Map<String, Object> updatable = new LinkedHashMap<>(data);
	        updatable.remove("storeId");
	        updatable.remove("quantityId");
	        updatable.remove("categoryId");
	        updatable.remove("subCategoryId");
	        updatable.remove("productId");
	        updatable.remove("brandId");

	        Object availableStock = updatable.getOrDefault("availableStock", existing.get("availableStock"));
	        Object reservedStock = updatable.getOrDefault("reservedStock", existing.get("reservedStock"));
	        updatable.getOrDefault("stockName", existing.get("stockName"));

	        if (availableStock instanceof Number a && reservedStock instanceof Number r
	                && a.doubleValue() < r.doubleValue()) {

	            return json(mapper, Map.of("statusCode", "N400",
	                    "statusDesc", "availableStock cannot be less than reservedStock"));
	        }

	        existing.putAll(updatable);

	        existing.put("createdAt", existing.get("createdAt"));
	        existing.put("createdBy", existing.get("createdBy"));

	        existing.put("updatedBy", userId);
	        existing.put("updatedAt", LocalDateTime.now());
	        existing.put("lastUpdated", LocalDateTime.now());

	        repo.updateById(def.getCollection(), id, existing);

	        return json(mapper, Map.of("statusCode", "N200",
	                "statusDesc", "Store inventory record updated successfully", "data", existing));
	    };
	}

//	public NktOperationHandler getStoreInventory() {
//
//	    return (data, userId, repo, mapper, def) -> {
//
//	        String storeId = str(data, "storeId");
//
//	        if (storeId == null || storeId.isBlank()) {
//	            return json(mapper, Map.of("statusCode", "N400", "statusDesc", "storeId is required"));
//	        }
//
//	        String categoryId = str(data, "categoryId");
//	        String subCategoryId = str(data, "subCategoryId");
//	        String productId = str(data, "productId");
//	        String searchText = str(data, "searchText");
//
//	        Map<String, Object> filter = new LinkedHashMap<>();
//	        filter.put("storeId", storeId);
//	        filter.put("status", "Active");
//
//	        if (categoryId != null && !categoryId.isBlank()) filter.put("categoryId", categoryId);
//	        if (subCategoryId != null && !subCategoryId.isBlank()) filter.put("subCategoryId", subCategoryId);
//	        if (productId != null && !productId.isBlank()) filter.put("productId", productId);
//
//	        if (searchText != null && !searchText.isBlank()) {
//	            filter.put("batchNumber", Map.of("$regex", searchText));
//	        }
//
//	        List<Map<String, Object>> inventory = repo.findAll(def.getCollection(), filter);
//
//	        return json(mapper, Map.of("statusCode", "N200", "statusDesc", "Success",
//	                "count", inventory.size(), "data", inventory));
//	    };
//	}
	
//	public NktOperationHandler getStoreInventory() {
//
//	    return (data, userId, repo, mapper, def) -> {
//
//	        String storeId = str(data, "storeId");
//	        String searchText = str(data, "searchText");
//
//	        if (storeId == null || storeId.isBlank()) {
//	            return json(mapper, Map.of("statusCode", "N400", "statusDesc", "storeId is required"));
//	        }
//
//	        Map<String, Object> store = repo.findOne("stores", "_id", storeId).orElse(null);
//
//	        if (store == null || !"Active".equals(store.get("status"))) {
//	            return json(mapper, Map.of("statusCode", "N404", "statusDesc", "Store not found or inactive"));
//	        }
//
//	        if (!userId.equals(String.valueOf(store.get("userId")))) {
//
//	            return json(mapper, Map.of("statusCode", "N403",
//	                    "statusDesc", "You are not authorized to view this store's inventory"));
//	        }
//
//	        Map<String, Object> filter = new LinkedHashMap<>();
//	        filter.put("storeId", storeId);
//	        filter.put("status", "Active");
//
//	        if (searchText != null && !searchText.isBlank()) {
//
//	            Map<String, Object> nameFilter = Map.of(
//	                "$or", List.of(
//	                    Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
//	                    Map.of("name.ta", Map.of("$regex", searchText, "$options", "i"))
//	                )
//	            );
//
//	            List<Object> categoryIds = idsMatching(repo, "sections", nameFilter);
//	            List<Object> subCategoryIds = idsMatching(repo, "subsections", nameFilter);
//	            List<Object> brandIds = idsMatching(repo, "brand", nameFilter);
//	            List<Object> productIds = idsMatching(repo, "products", nameFilter);
//
//	            Map<String, Object> quantityFilter = Map.of(
//	                "$or", List.of(
//	                    Map.of("mrp", Map.of("$regex", searchText, "$options", "i")),
//	                    Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
//	                    Map.of("name.ta", Map.of("$regex", searchText, "$options", "i"))
//	                )
//	            );
//	            List<Object> quantityIds = idsMatching(repo, "quantity", quantityFilter);
//
//	            List<Map<String, Object>> matchClauses = new ArrayList<>();
//	            if (!categoryIds.isEmpty()) matchClauses.add(Map.of("categoryId", Map.of("$in", categoryIds)));
//	            if (!subCategoryIds.isEmpty()) matchClauses.add(Map.of("subCategoryId", Map.of("$in", subCategoryIds)));
//	            if (!productIds.isEmpty()) matchClauses.add(Map.of("productId", Map.of("$in", productIds)));
//	            if (!brandIds.isEmpty()) matchClauses.add(Map.of("brandId", Map.of("$in", brandIds)));
//	            if (!quantityIds.isEmpty()) matchClauses.add(Map.of("quantityId", Map.of("$in", quantityIds)));
//	            matchClauses.add(Map.of("stockName", Map.of("$regex", searchText, "$options", "i")));
//
//	            if (matchClauses.isEmpty()) {
//	                return json(mapper, Map.of("statusCode", "N200", "statusDesc", "Success",
//	                        "count", 0, "data", List.of()));
//	            }
//
//	            filter.put("$or", matchClauses);
//	        }
//
//	        List<Map<String, Object>> inventory = repo.findAll(def.getCollection(), filter);
//
//	        return json(mapper, Map.of("statusCode", "N200", "statusDesc", "Success",
//	                "count", inventory.size(), "data", inventory));
//	    };
//	}
//
//
	private List<Object> idsMatching(NktDynamicRepository repo, String collection, Map<String, Object> nameFilter) {
	    return repo.findAll(collection, nameFilter).stream()
	            .map(doc -> doc.get("id"))
	            .collect(Collectors.toList());
	}
	
	public NktOperationHandler getStoreInventory() {

	    return (data, userId, repo, mapper, def) -> {

	        String storeId = str(data, "storeId");

	        if (storeId == null || storeId.isBlank()) {
	            return json(mapper, Map.of("statusCode", "N400", "statusDesc", "storeId is required"));
	        }

	        String categoryId = str(data, "categoryId");
	        String subCategoryId = str(data, "subCategoryId");
	        String productId = str(data, "productId");
	        String brandId = str(data, "brandId");
	        String quantityId = str(data, "quantityId");
	        String searchText = str(data, "searchText");

	        Map<String, Object> filter = new LinkedHashMap<>();
	        filter.put("storeId", storeId);
	        filter.put("status", "Active");

	        if (categoryId != null && !categoryId.isBlank()) filter.put("categoryId", categoryId);
	        if (subCategoryId != null && !subCategoryId.isBlank()) filter.put("subCategoryId", subCategoryId);
	        if (productId != null && !productId.isBlank()) filter.put("productId", productId);
	        if (brandId != null && !brandId.isBlank()) filter.put("brandId", brandId);
	        if (quantityId != null && !quantityId.isBlank()) filter.put("quantityId", quantityId);

	        if (searchText != null && !searchText.isBlank()) {

	            Map<String, Object> nameFilter = Map.of(
	                "$or", List.of(
	                    Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
	                    Map.of("name.ta", Map.of("$regex", searchText, "$options", "i"))
	                )
	            );

	            List<Object> categoryIds = idsMatching(repo, "sections", nameFilter);
	            List<Object> subCategoryIds = idsMatching(repo, "subsections", nameFilter);
	            List<Object> brandIds = idsMatching(repo, "brand", nameFilter);

	            Map<String, Object> productFilter = Map.of(
	                "$or", List.of(
	                    Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
	                    Map.of("name.en", Map.of("$regex", searchText, "$options", "i"))
	                )
	            );
	            List<Object> productIds = idsMatching(repo, "products", productFilter);

	            // Quantity has no name field - only sku/barcode carry free text
	            Map<String, Object> quantityFilter = Map.of(
	                "$or", List.of(
	                    Map.of("name.en", Map.of("$regex", searchText, "$options", "i")),
	                    Map.of("name.en", Map.of("$regex", searchText, "$options", "i"))
	                )
	            );
	            List<Object> quantityIds = idsMatching(repo, "quantity", quantityFilter);

	            List<Map<String, Object>> matchClauses = new ArrayList<>();
	            if (!categoryIds.isEmpty()) matchClauses.add(Map.of("categoryId", Map.of("$in", categoryIds)));
	            if (!subCategoryIds.isEmpty()) matchClauses.add(Map.of("subCategoryId", Map.of("$in", subCategoryIds)));
	            if (!productIds.isEmpty()) matchClauses.add(Map.of("productId", Map.of("$in", productIds)));
	            if (!brandIds.isEmpty()) matchClauses.add(Map.of("brandId", Map.of("$in", brandIds)));
	            if (!quantityIds.isEmpty()) matchClauses.add(Map.of("quantityId", Map.of("$in", quantityIds)));
	            matchClauses.add(Map.of("stockName", Map.of("$regex", searchText, "$options", "i"))); 

	            if (matchClauses.isEmpty()) {
	                // Nothing matched the search text anywhere - short-circuit to an empty result
	                return json(mapper, Map.of("statusCode", "N200", "statusDesc", "Success",
	                        "count", 0, "data", List.of()));
	            }

	            filter.put("$or", matchClauses);
	        }

	        List<Map<String, Object>> inventory = repo.findAll(def.getCollection(), filter);

	        return json(mapper, Map.of("statusCode", "N200", "statusDesc", "Success",
	                "count", inventory.size(), "data", inventory));
	    };
	}

}
