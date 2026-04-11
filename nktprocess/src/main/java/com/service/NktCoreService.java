package com.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.configs.NktProcessConfigLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.nkt.NktProcessDefinition;
import com.repository.NktDynamicRepository;
import com.security.JwtTokenProvider;
import com.service.handlers.NktAuthHandler;
import com.service.handlers.NktCatalogueHandler;
import com.service.handlers.NktOperationHandler;
import com.service.handlers.NktOrderHandler;
import com.service.handlers.NktPaymentHandler;
import com.service.handlers.NktUserHandler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Single unified service for the NKT no-code platform.
 *
 * Responsibilities:
 * <ol>
 *   <li><b>Config-Driven</b>: reads every process definition from
 *       {@code process-flow.json} via {@link NktProcessConfigLoader}.
 *       No hard-coded routing table.</li>
 *   <li><b>Dynamic MongoDB</b>: delegates all persistence to
 *       {@link NktDynamicRepository} using collection names from the
 *       process definition — no Java model classes required.</li>
 *   <li><b>Identity</b>: extracts the authenticated {@code userId} from
 *       the JWT token in {@code data["token"]} for every protected endpoint
 *       before the handler is invoked.</li>
 *   <li><b>Validation</b>: checks required fields declared in the process
 *       definition before any business logic runs.</li>
 *   <li><b>Standard CRUD</b>: handles FIND_ALL, FIND_BY_ID,
 *       FIND_BY_USER_ID, INSERT, UPDATE_BY_USER_ID directly.</li>
 *   <li><b>Custom operations</b>: delegates CUSTOM operations to the
 *       appropriate {@link NktOperationHandler} via a handler registry
 *       populated at startup.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NktCoreService {

    private final NktProcessConfigLoader configLoader;
    private final NktDynamicRepository   repo;
    private final JwtTokenProvider       jwtProvider;
    private final ObjectMapper           mapper;

    // ── Handler components (5 consolidated handlers instead of 12 services) ──
    private final NktAuthHandler      authHandler;
    private final NktUserHandler      userHandler;
    private final NktCatalogueHandler catalogueHandler;
    private final NktOrderHandler     orderHandler;
    private final NktPaymentHandler   paymentHandler;

    /** Runtime handler registry: HandlerKey → NktOperationHandler */
    private final Map<String, NktOperationHandler> handlers = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Startup: register all custom handlers
    // ─────────────────────────────────────────────────────────────────────────

    @PostConstruct
    void registerHandlers() {
        // Auth
        handlers.put("AUTH_SEND_OTP",         authHandler.sendOtp());
        handlers.put("AUTH_VERIFY_OTP",        authHandler.verifyOtp());
        handlers.put("AUTH_REFRESH_TOKEN",     authHandler.refreshToken());
        handlers.put("AUTH_ENROL_BIOMETRIC",   authHandler.enrolBiometric());
        handlers.put("AUTH_VERIFY_BIOMETRIC",  authHandler.verifyBiometric());
        handlers.put("AUTH_LOGOUT",            authHandler.logout());

        // User / Customer / StoreProfile / Notification
        handlers.put("CUSTOMER_ADD_ADDRESS",    userHandler.addAddress());
        handlers.put("CUSTOMER_DELETE_ADDRESS", userHandler.deleteAddress());
        handlers.put("CUSTOMER_TOGGLE_FAV",     userHandler.toggleFavourite());
        handlers.put("STORE_PROFILE_GET",       userHandler.storeProfileGet());
        handlers.put("STORE_PROFILE_UPDATE",    userHandler.storeProfileUpdate());
        handlers.put("STORE_PROFILE_TOGGLE",    userHandler.storeProfileToggle());
        handlers.put("STORE_PROFILE_DASHBOARD", userHandler.storeProfileDashboard());
        handlers.put("NOTIFICATION_REGISTER_DEVICE", userHandler.registerDevice());

        // Catalogue / Stock / Location / Discover
        handlers.put("DISCOVER_NEARBY_STORES",   catalogueHandler.nearbyStores());
        handlers.put("STORES_GET_PRODUCTS",      catalogueHandler.storeProducts());
        handlers.put("STORES_GET_AVAILABILITY",  catalogueHandler.storeAvailability());
        handlers.put("STOCK_LIST",               catalogueHandler.stockList());
        handlers.put("STOCK_ADD",                catalogueHandler.stockAdd());
        handlers.put("STOCK_CATEGORIES",         catalogueHandler.stockCategories());
        handlers.put("STOCK_UPDATE",             catalogueHandler.stockUpdate());
        handlers.put("STOCK_ADJUST_QTY",         catalogueHandler.stockAdjustQty());
        handlers.put("STOCK_TOGGLE_AVAILABILITY",catalogueHandler.stockToggleAvailability());
        handlers.put("STOCK_DELETE",             catalogueHandler.stockDelete());
        handlers.put("LOCATION_REVERSE_GEOCODE", catalogueHandler.reverseGeocode());
        handlers.put("LOCATION_GLOBAL_SEARCH",   catalogueHandler.globalSearch());

        // Orders / Wishlist / StoreOrders
        handlers.put("ORDER_VALIDATE_CART", orderHandler.validateCart());
        handlers.put("ORDER_PLACE",         orderHandler.placeOrder());
        handlers.put("ORDER_HISTORY",       orderHandler.orderHistory());
        handlers.put("ORDER_GET_DETAIL",    orderHandler.orderGetDetail());
        handlers.put("ORDER_TRACK",         orderHandler.trackOrder());
        handlers.put("ORDER_CANCEL",        orderHandler.cancelOrder());
        handlers.put("ORDER_RATE",          orderHandler.rateOrder());
        handlers.put("WISHLIST_ADD",        orderHandler.wishlistAdd());
        handlers.put("WISHLIST_REMOVE",     orderHandler.wishlistRemove());
        handlers.put("STORE_ORDER_LIST",    orderHandler.storeOrderList());
        handlers.put("STORE_ORDER_ACCEPT",  orderHandler.storeOrderAccept());
        handlers.put("STORE_ORDER_REJECT",  orderHandler.storeOrderReject());
        handlers.put("STORE_ORDER_DISPATCH",orderHandler.storeOrderDispatch());
        handlers.put("STORE_ORDER_DELIVER", orderHandler.storeOrderDeliver());

        // Payments
        handlers.put("PAYMENT_INITIATE", paymentHandler.initiatePayment());
        handlers.put("PAYMENT_WEBHOOK",  paymentHandler.paymentWebhook());

        log.info("NktCoreService: {} operation handlers registered", handlers.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Process a NKT request end-to-end.
     *
     * @param processCode the {@code nkt.*} code from {@code @RequestParam("code")}
     * @param data        parsed JSON from {@code @RequestParam("data")};
     *                    must include {@code "token"} for protected endpoints
     * @return JSON string response
     */
    public String process(String processCode, Map<String, Object> data) {
        log.info("NktCoreService.process: code={}", processCode);

        // 1 ── Load process definition ────────────────────────────────────────
        NktProcessDefinition def = configLoader.get(processCode);

        // 2 ── Validate required fields ───────────────────────────────────────
        validateRequiredFields(def, data);

        // 3 ── Identity: extract userId from JWT (protected endpoints) ────────
        String userId = null;
        if (def.isProtectedEndpoint()) {
            userId = extractUserId(data);
        }
        // Strip the token from data so it never reaches handlers / DB
        data.remove("token");

        // 4 ── Inject userId into data under the configured field name ─────────
        if (userId != null && def.getUserIdField() != null) {
            data.put(def.getUserIdField(), userId);
        }

        // 5 ── Dispatch to operation ───────────────────────────────────────────
        return switch (def.getOperation()) {
            case "FIND_ALL"          -> execFindAll(def, data);
            case "FIND_BY_ID"        -> execFindById(def, data);
            case "FIND_BY_USER_ID"   -> execFindByUserId(def, userId);
            case "INSERT"            -> execInsert(def, data);
            case "UPDATE_BY_ID"      -> execUpdateById(def, data);
            case "UPDATE_BY_USER_ID" -> execUpdateByUserId(def, data, userId);
            case "SOFT_DELETE"       -> execSoftDelete(def, data);
            case "CUSTOM"            -> execCustom(def, data, userId);
            default -> throw new RuntimeException("Unknown operation: " + def.getOperation());
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Standard CRUD operations (config-driven, no custom code needed)
    // ─────────────────────────────────────────────────────────────────────────

    private String execFindAll(NktProcessDefinition def, Map<String, Object> data) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        // Static criteria from config
        if (def.getStaticCriteria() != null) criteria.putAll(def.getStaticCriteria());
        // Dynamic criteria from request data
        if (def.getCriteriaFields() != null) {
            def.getCriteriaFields().forEach(field -> {
                if (data.get(field) != null) criteria.put(field, data.get(field));
            });
        }
        Sort.Direction dir = "ASC".equalsIgnoreCase(def.effectiveSortDir())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        int skip  = data.get("page")  != null ? Integer.parseInt(data.get("page").toString())  * 20 : 0;
        int limit = data.get("limit") != null ? Integer.parseInt(data.get("limit").toString()) : 200;

        List<Map<String, Object>> results = repo.findAllSorted(
                def.getCollection(), criteria, def.effectiveSortField(), dir, skip, limit);
        return toJson(results);
    }

    private String execFindById(NktProcessDefinition def, Map<String, Object> data) {
        String id = str(data, def.effectiveIdField());
        if (id == null) throw new RuntimeException("Missing id field: " + def.effectiveIdField());
        return toJson(repo.findById(def.getCollection(), id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id)));
    }

    private String execFindByUserId(NktProcessDefinition def, String userId) {
        return toJson(repo.findOne(def.getCollection(), "userId", userId)
                .orElseThrow(() -> new RuntimeException("Document not found for user: " + userId)));
    }

    private String execInsert(NktProcessDefinition def, Map<String, Object> data) {
        return toJson(repo.insert(def.getCollection(), data));
    }

    private String execUpdateById(NktProcessDefinition def, Map<String, Object> data) {
        String id = str(data, def.effectiveIdField());
        if (id == null) throw new RuntimeException("Missing id field: " + def.effectiveIdField());
        Map<String, Object> updates = new LinkedHashMap<>(data);
        updates.remove(def.effectiveIdField());
        updates.put("updatedAt", java.time.LocalDateTime.now().toString());
        repo.updateById(def.getCollection(), id, updates);
        return toJson(repo.findById(def.getCollection(), id).orElseThrow());
    }

    /**added on 30-Mar-2026**/
    private String execUpdateByUserId(NktProcessDefinition def,
			Map<String, Object> data, String userId) {
		Map<String, Object> updates = new LinkedHashMap<>();
		// Only update fields explicitly allowed in config
		
		if (def.getRequiredFields() != null) {
			def.getRequiredFields().forEach(f -> {
				if (data.get(f) != null)
					updates.put(f, data.get(f));
			});
		} else {
			updates.putAll(data);
		}
		updates.put("updatedAt", java.time.LocalDateTime.now().toString());
		repo.updateFirst(def.getCollection(), Map.of("userId", userId), updates);
		return toJson(repo.findOne(def.getCollection(), "userId", userId).orElseThrow());
	}

    private String execSoftDelete(NktProcessDefinition def, Map<String, Object> data) {
        String id = str(data, def.effectiveIdField());
        if (id == null) throw new RuntimeException("Missing id field: " + def.effectiveIdField());
        repo.updateById(def.getCollection(), id,
                Map.of("status", "DELETED", "updatedAt", java.time.LocalDateTime.now().toString()));
        return toJson(Map.of("message", "Deleted successfully", "id", id));
    }

    private String execCustom(NktProcessDefinition def, Map<String, Object> data, String userId) {
        String key = def.getHandlerKey();
        NktOperationHandler handler = handlers.get(key);
        if (handler == null)
            throw new RuntimeException("No handler registered for key: " + key);
        return handler.handle(data, userId, repo, mapper,def);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validateRequiredFields(NktProcessDefinition def, Map<String, Object> data) {
        if (def.getRequiredFields() == null) return;
        List<String> missing = def.getRequiredFields().stream()
                .filter(f -> data.get(f) == null || data.get(f).toString().isBlank())
                .toList();
        if (!missing.isEmpty())
            throw new RuntimeException("Missing required fields: " + missing);
    }

	private String extractUserId(Map<String, Object> data) {
		
		Object tokenObj = data.get("token");
		
		if (tokenObj == null || tokenObj.toString().isBlank())
			throw new RuntimeException("Authentication required: missing token");

		String token = tokenObj.toString().replaceFirst("(?i)^Bearer\\s+", "");
		
//		if (!jwtProvider.isTokenValid(token))
//			throw new RuntimeException("Invalid or expired token");
		
		if (!jwtProvider.isTokenValid(token))
			throw new RuntimeException("Invalid or expired token");
		
		return jwtProvider.extractUserId(token);
	}

    private String str(Map<String, Object> d, String key) {
        Object v = d.get(key); return v == null ? null : v.toString();
    }

    private String toJson(Object obj) {
        try { return mapper.writeValueAsString(obj); }
        catch (Exception e) { return "{\"error\":\"serialisation failed\"}"; }
    }
}
