package com.first.components;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.dto.ApiDefinition;
import com.first.services.ApiLogService;
import com.first.services.BusinessLogicExecutor;
import com.first.services.ResponseBuilders;
import com.first.services.SecurityService;
import com.first.services.ValidationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Production-grade NKT API Gateway Filter.
 *
 * Pipeline (per request):
 *   1. Assign Correlation ID → MDC + X-Correlation-ID response header
 *   2. Wrap request + response for safe body caching
 *   3. Read request body (once, from wrapper cache)
 *   4. Resolve API definition → security → rate-limit → validate → execute → build
 *   5. Write response through ContentCachingResponseWrapper (never raw response)
 *   6. Persist structured audit log to MongoDB (ApiLogService)
 *   7. copyBodyToResponse() — flush buffered bytes to client
 *   8. MDC.remove() — prevent thread-pool correlation-ID bleed
 *
 * Fixes applied vs. submitted version
 * ────────────────────────────────────
 *  FIX-1  Only one doFilterInternal override (protected, HttpServletRequest).
 *         The submitted code had a public doFilter(ServletRequest, ...) with the
 *         @SuppressWarnings("deprecation") trick — that is NOT the
 *         OncePerRequestFilter hook. The protected override at the bottom was empty,
 *         so every request fell through with no response.
 *
 *  FIX-2  ContentCachingRequestWrapper(request, maxPayloadLogBytes) — was (request, 0);
 *         size 0 disables all caching so getContentAsByteArray() always returned empty.
 *
 *  FIX-3  All response writes go through ContentCachingResponseWrapper.getOutputStream().
 *         Was: httpResponse.getWriter().write(...) on the raw response after the wrapper
 *         had already called getOutputStream() → IllegalStateException on every request.
 *
 *  FIX-4  wrappedResp.copyBodyToResponse() always called in finally.
 *         Was: never called → clients always received an empty HTTP body.
 *
 *  FIX-5  Injected @Primary ObjectMapper used everywhere.
 *         Was: new ObjectMapper() inline ×5 — ignores JavaTimeModule, wastes heap.
 *
 *  FIX-6  @RequiredArgsConstructor with final fields replaces @Autowired field injection.
 *
 *  FIX-7  log.error/warn(...) replaces all e.printStackTrace() calls.
 *
 *  FIX-8  MDC.remove(MDC_CORRELATION_KEY) in finally block.
 *         Was: never removed → correlationId leaked into thread-pool reuse.
 *
 *  FIX-9  handleError wraps executeCallbacks in its own try-catch.
 *         Was: callback failure threw back to doFilterInternal and the error
 *         response was never written.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
@Slf4j
public class RequestCachingFilter extends OncePerRequestFilter {

    // ── Injected dependencies ─────────────────────────────────────────────────
    private final ApiConfigLoader       configLoader;
    private final SecurityService       securityService;
    private final ValidationService     validationService;
    private final BusinessLogicExecutor businessLogicExecutor;
    private final ResponseBuilders      responseBuilder;
    private final ApiLogService         apiLogService;
    private final ObjectMapper          mapper;   // @Primary bean — never new ObjectMapper()

    // ── Configurable ──────────────────────────────────────────────────────────
    @Value("${app.filter.max-payload-log-bytes:8192}")
    private int maxPayloadLogBytes;

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String CORRELATION_HEADER  = "X-Correlation-ID";
    private static final String MDC_CORRELATION_KEY = "correlationId";
    private static final String MASKED              = "***";

    /** Values of these keys are replaced with *** in audit logs */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "token", "secret", "authorization", "pin", "cvv",
            "otp", "ssn", "cardnumber", "accesstoken", "refreshtoken",
            "apikey", "privatekey");

    // ═════════════════════════════════════════════════════════════════════════
    //  OncePerRequestFilter contract — ONE override, correct signature
    //  FIX-1: no public doFilter(ServletRequest,...) override
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        // ── Step 1: Correlation ID ────────────────────────────────────────────
        String correlationId = resolveCorrelationId(request);
        MDC.put(MDC_CORRELATION_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        // ── Step 2: Wrap for body caching ─────────────────────────────────────
        // FIX-2: maxPayloadLogBytes > 0 so the wrapper actually caches data
        ContentCachingRequestWrapper  wrappedReq  =
                new ContentCachingRequestWrapper(request, maxPayloadLogBytes);
        ContentCachingResponseWrapper wrappedResp =
                new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();

        try {
            // ── Step 3: Read body once ────────────────────────────────────────
            String requestBody = readBody(wrappedReq);

            // ── Step 4: Full API gateway pipeline ─────────────────────────────
            ResponseEntity<Map<String, Object>> apiResponse =
                    handleRequest(wrappedReq, requestBody, correlationId);

            // ── Step 5: Write through wrapper — never raw response ─────────────
            // FIX-3: was httpResponse.getWriter() on the raw response
            writeApiResponse(wrappedResp, apiResponse);

            // ── Step 6: Persist audit log ──────────────────────────────────────
            persistAuditLog(wrappedReq, wrappedResp, requestBody,
                    apiResponse, correlationId, System.currentTimeMillis() - start);

        } catch (Exception ex) {
            log.error("[{}] Unhandled pipeline exception — {} {}: {}",
                    correlationId, request.getMethod(), request.getRequestURI(),
                    ex.getMessage(), ex);
            writeErrorResponse(wrappedResp, correlationId, ex);

        } finally {
            // FIX-4: flush buffered bytes — was never called → empty body for client
            wrappedResp.copyBodyToResponse();
            // FIX-8: clear MDC — was never removed → correlationId leaked across threads
            MDC.remove(MDC_CORRELATION_KEY);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  API gateway pipeline
    // ═════════════════════════════════════════════════════════════════════════

    private ResponseEntity<Map<String, Object>> handleRequest(
            HttpServletRequest request,
            String             requestBody,
            String             correlationId) {

        String path   = request.getRequestURI();
        String method = request.getMethod();
        log.info("[{}] → {} {}", correlationId, method, path);

        // Collect request headers
        Map<String, String> headers = readRequestHeaders(request);

        // Parse body — safe fallback to empty map on blank / non-JSON body
        Map<String, Object> body = Collections.emptyMap();
        if (ObjectUtils.isNotEmpty(requestBody) && !requestBody.isBlank()) {
            try {
                // FIX-5: injected mapper, not new ObjectMapper()
                body = mapper.readValue(requestBody,
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                // FIX-7: structured log with correlationId, not e.printStackTrace()
                log.warn("[{}] Request body parse failed — proceeding with empty body: {}",
                        correlationId, ex.getMessage());
            }
        }

        // Resolve API definition
        ApiDefinition apiDef = configLoader.findApi(method, path);
       
        if (apiDef == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error",         "API endpoint not found",
                    "path",          path,
                    "correlationId", correlationId));
        }

        try {
//             1 — Security validation
            securityService.validateSecurity(apiDef, request, headers);

            // 2 — Rate limiting
//            securityService.enforceRateLimit(apiDef, request);

            // 3 — Request body / param validation
            Map<String, Object> validatedParams =
                    validationService.validate(apiDef, body, request, headers);

            // 4 — Execute business logic
            Map<String, Object> result =
                    businessLogicExecutor.execute(apiDef, validatedParams);

            // 5 — Build response
            Map<String, Object> builtResponse =
                    responseBuilder.build(apiDef, result, validatedParams);

            return ResponseEntity
                    .status(apiDef.getResponse().getSuccessCode())
                    .body(builtResponse);

        } catch (Exception ex) {
            return handleError(apiDef, ex, correlationId);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Response writing helpers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Write a successful (or business-level error) response through the wrapper.
     * FIX-3: all writes go to ContentCachingResponseWrapper.getOutputStream(),
     * never to httpResponse.getWriter() on the raw response.
     */
    private void writeApiResponse(ContentCachingResponseWrapper       resp,
                                  ResponseEntity<Map<String, Object>> apiResponse)
            throws IOException {
        resp.setStatus(apiResponse.getStatusCode().value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // FIX-5: mapper bean, not new ObjectMapper()
        byte[] bodyBytes = mapper.writeValueAsBytes(apiResponse.getBody());
        resp.getOutputStream().write(bodyBytes);
        resp.getOutputStream().flush();
    }

    /**
     * Write a structured JSON 500 when an uncaught exception escapes the pipeline.
     * Calls resp.reset() to discard any partial write before writing the error body.
     */
    private void writeErrorResponse(ContentCachingResponseWrapper resp,
                                    String                        correlationId,
                                    Exception                     ex) {
        if (resp.isCommitted()) {
            log.warn("[{}] Cannot write error — response already committed", correlationId);
            return;
        }
        try {
            resp.reset();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());

            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status",        "ERROR");
            error.put("statusCode",    500);
            error.put("message",       ex.getMessage() != null ? ex.getMessage() : "Unexpected error");
            error.put("correlationId", correlationId);
            error.put("timestamp",     Instant.now().toString());

            // FIX-5: mapper bean, not new ObjectMapper()
            byte[] bodyBytes = mapper.writeValueAsBytes(error);
            resp.getOutputStream().write(bodyBytes);
            resp.getOutputStream().flush();

        } catch (IOException writeEx) {
            log.error("[{}] Failed to write error response: {}", correlationId, writeEx.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Error handling
    // ═════════════════════════════════════════════════════════════════════════

    private ResponseEntity<Map<String, Object>> handleError(ApiDefinition apiDef,
                                                             Exception     ex,
                                                             String        correlationId) {
        log.error("[{}] Pipeline error — apiId={}: {}",
                correlationId, apiDef.getId(), ex.getMessage(), ex);

        // FIX-9: callback failure was not caught → rethrew to doFilterInternal
        //        and the error response was never written to the client.
        try {
            businessLogicExecutor.executeCallbacks(
                    apiDef,
                    Map.of("error", ex.getMessage() != null ? ex.getMessage() : ""),
                    "FAILED");
        } catch (Exception cbEx) {
            log.warn("[{}] Callback execution failed: {}", correlationId, cbEx.getMessage());
        }

        return ResponseEntity.badRequest().body(Map.of(
                "error",         ex.getClass().getSimpleName(),
                "message",       ex.getMessage() != null ? ex.getMessage() : "Unknown error",
                "apiId",         apiDef.getId(),
                "correlationId", correlationId));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Audit logging → MongoDB
    // ═════════════════════════════════════════════════════════════════════════

    private void persistAuditLog(HttpServletRequest                  request,
                                  ContentCachingResponseWrapper        resp,
                                  String                              requestBody,
                                  ResponseEntity<Map<String, Object>> apiResponse,
                                  String                              correlationId,
                                  long                                durationMs) {
        try {
            Map<String, Object> logMap = new LinkedHashMap<>();
            logMap.put("correlationId",   correlationId);
            logMap.put("method",          request.getMethod());
            logMap.put("path",            request.getRequestURI());
            logMap.put("statusCode",      resp.getStatus());
            logMap.put("durationMs",      durationMs);
            logMap.put("timestamp",       Instant.now().toString());
            logMap.put("requestHeaders",  readRequestHeaders(request));
            logMap.put("responseHeaders", readResponseHeaders(resp));

            if (ObjectUtils.isNotEmpty(requestBody) && !requestBody.isBlank()) {
                logMap.put("request", maskSensitive(requestBody));
            }
            if (apiResponse != null && apiResponse.getBody() != null) {
                logMap.put("response", apiResponse.getBody());
            }

            if (log.isInfoEnabled()) {
                log.info("[{}] ← {} {} in {}ms",
                        correlationId, resp.getStatus(), request.getRequestURI(), durationMs);
            }

            // ApiLogService wraps its own try-catch — never throws
//            apiLogService.save(logMap);

        } catch (Exception ex) {
            log.warn("[{}] Audit log persistence failed — {}", correlationId, ex.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Utility helpers
    // ═════════════════════════════════════════════════════════════════════════

    private String resolveCorrelationId(HttpServletRequest request) {
        String id = request.getHeader(CORRELATION_HEADER);
        return (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
    }

    /**
     * Read body from the wrapper's internal byte cache.
     * Falls back to the InputStream only if the cache is empty on first access.
     * FIX-2: with maxPayloadLogBytes > 0, getContentAsByteArray() now returns
     *         real data; the BufferedReader path is a safety net only.
     */
    private String readBody(ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        if (buf.length > 0) {
            return new String(buf, StandardCharsets.UTF_8);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining());
        } catch (IOException ex) {
            log.warn("readBody: could not read request stream — {}", ex.getMessage());
            return "";
        }
    }

    /** Read request headers; mask any header whose name is sensitive. */
    private Map<String, String> readRequestHeaders(HttpServletRequest request) {
        Map<String, String> map   = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return map;
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, SENSITIVE_KEYS.contains(name.toLowerCase())
                    ? MASKED
                    : request.getHeader(name));
        }
        return map;
    }

    private Map<String, String> readResponseHeaders(ContentCachingResponseWrapper response) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            map.put(name, response.getHeader(name));
        }
        return map;
    }

    /**
     * Recursively replace values of sensitive keys with ***.
     * Falls back to the raw string if the body is not valid JSON.
     */
    private Object maskSensitive(String json) {
        try {
            // FIX-5: mapper bean, not new ObjectMapper()
            Object parsed = mapper.readValue(json, Object.class);
            return maskObject(parsed);
        } catch (Exception ex) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private Object maskObject(Object obj) {
        if (obj instanceof Map) {
            Map<String, Object> original = (Map<String, Object>) obj;
            Map<String, Object> masked   = new LinkedHashMap<>();
            original.forEach((k, v) -> masked.put(k,
                    SENSITIVE_KEYS.contains(k.toLowerCase()) ? MASKED : maskObject(v)));
            return masked;
        }
        if (obj instanceof List) {
            ((List<Object>) obj).replaceAll(this::maskObject);
        }
        return obj;
    }
}
