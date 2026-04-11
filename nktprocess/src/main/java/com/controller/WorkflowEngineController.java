package com.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resource.WorkflowEngineResource;
import com.service.NktCoreService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Single controller for the NKT Process Engine (Project 2).
 *
 * Entry point: POST /data?data={json}&code={processCode}
 *
 * All process codes starting with "nkt." are dispatched to NktCoreService.
 * The controller is intentionally thin — no business logic lives here.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngineController implements WorkflowEngineResource {

    private final NktCoreService nktCoreService;
    private final ObjectMapper   mapper;

    @Override
    public String process(@RequestParam("data") String data,
                          @RequestParam("code") String code) {

        log.info("WorkflowEngineController.process: code={}", code);

        try {
            Map<String, Object> dataMap = mapper.readValue(
                    data, new TypeReference<Map<String, Object>>() {});

            // All NKT no-code platform codes → NktCoreService
            if (code != null && code.startsWith("nkt.")) {
                return nktCoreService.process(code, dataMap);
            }

            log.warn("Unrecognised process code: {}", code);
            return mapper.writeValueAsString(
                    Map.of("status", "ERROR",
                           "message", "Unrecognised process code: " + code));

        } catch (JsonProcessingException e) {
            log.error("JSON parse error for code={}: {}", code, e.getMessage(), e);
            try {
                return mapper.writeValueAsString(
                        Map.of("status", "ERROR",
                               "message", "Invalid JSON input: " + e.getOriginalMessage()));
            } catch (JsonProcessingException ex) {
                return "{\"status\":\"ERROR\",\"message\":\"Invalid JSON input\"}";
            }
        }
    }
}
