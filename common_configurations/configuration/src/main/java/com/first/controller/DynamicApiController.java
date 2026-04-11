package com.first.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.first.components.ApiConfigLoader;
import com.first.dto.ApiDefinition;
import com.first.services.BusinessLogicExecutor;
import com.first.services.ResponseBuilders;
import com.first.services.SecurityService;
import com.first.services.ValidationService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Single catch-all controller for the no-code API platform.
 * Every inbound request is matched to a YAML-defined ApiDefinition,
 * then passed through the standard pipeline:
 *   security → rate-limit → validation → business-logic → response → callbacks
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class DynamicApiController {

    private final ApiConfigLoader      configLoader;
    private final SecurityService      securityService;
    private final ValidationService    validationService;
    private final BusinessLogicExecutor businessLogicExecutor;
    private final ResponseBuilders     responseBuilder;

    // ── Catch-all handler ─────────────────────────────────────────────────────

    @RequestMapping("/**")
    public ResponseEntity<?> handleRequest(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader Map<String, String> headers) {

        String path   = request.getRequestURI();
        String method = request.getMethod();

        log.info("Inbound {} {}", method, path);

        // 1. Resolve API definition
        ApiDefinition apiDef = configLoader.findApi(method, path);
        if (apiDef == null) {
            log.warn("No API definition found for {} {}", method, path);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "API endpoint not found",
                                 "path", path,
                                 "method", method));
        }

        try {
            // 2. JWT role check (SecurityContextHolder already populated by JwtAuthenticationFilter)
            securityService.validateRoles(apiDef);

            // 3. Rate limiting
            securityService.enforceRateLimit(apiDef, request);

            // 4. Request validation (required fields, types, patterns, path variables)
            Map<String, Object> params = validationService.validate(apiDef, body, request, headers);

            // 5. Business logic (DATABASE / EXTERNAL_API / CUSTOM_SERVICE / MOCK)
            Map<String, Object> result = businessLogicExecutor.execute(apiDef, params);

            // 6. Shape response according to YAML response definition
            Map<String, Object> response = responseBuilder.build(apiDef, result, params);

            // 7. Fire success callbacks (async webhooks etc.)
            businessLogicExecutor.executeCallbacks(apiDef, response, "SUCCESS");

            int successCode = apiDef.getResponse() != null
                    ? apiDef.getResponse().getSuccessCode() : 200;
            return ResponseEntity.status(successCode).body(response);

        } catch (Exception e) {
            log.error("Error processing {} {}: {}", method, path, e.getMessage(), e);
            businessLogicExecutor.executeCallbacks(apiDef,
                    Map.of("error", e.getMessage()), "FAILED");
            // GlobalExceptionHandler picks this up and returns structured JSON
            throw e;
        }
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @RequestMapping("/api/admin/reload-config")
    public ResponseEntity<Map<String, Object>> reloadConfig() {
        configLoader.reloadConfiguration();
        return ResponseEntity.ok(Map.of(
                "status",    "success",
                "message",   "Configuration reloaded",
                "totalApis", configLoader.getAllApis().size()
        ));
    }

    @RequestMapping("/api/admin/list-apis")
    public ResponseEntity<Map<String, Object>> listApis() {
        return ResponseEntity.ok(Map.of(
                "apis",  configLoader.getAllApis().values(),
                "total", configLoader.getAllApis().size()
        ));
    }
}
