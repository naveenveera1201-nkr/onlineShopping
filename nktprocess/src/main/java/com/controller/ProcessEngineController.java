package com.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resource.ProcessEngineResource;
import com.service.NktCoreService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Single controller for the NKT Process Engine (Project 2).
 *
 * Entry point: POST /data?data={json}&code={processCode}
 * Multipart entry point (file uploads, e.g. EXCEL_STOCK_MASTER_IMPORT):
 *   POST /data/upload  (multipart: file, data={json}, code={processCode})
 *
 * All process codes starting with "nkt." are dispatched to NktCoreService.
 * The controller is intentionally thin — no business logic lives here.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProcessEngineController implements ProcessEngineResource {

    private final NktCoreService nktCoreService;
    private final ObjectMapper   mapper;

    @Override
    public String process(@RequestParam("data") String data,
                          @RequestParam("code") String code) {

        log.info("ProcessEngineController.process: code={}", code);

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

    /**
     * Reads the uploaded file's bytes once here (the only place in the
     * request pipeline that touches Spring's {@link MultipartFile}) and folds
     * them into the same {@code data} map as {@code fileBytes} (byte[]) and
     * {@code fileName} (String) before delegating to the exact same
     * {@code NktCoreService.process()} used by every other endpoint — so JWT
     * auth, {@code allowedRoles}, and {@code RequiredFields} validation are
     * all reused unchanged; only {@code NktInventoryImportHandler} ever reads
     * {@code fileBytes} / {@code fileName} back out.
     */
    @Override
    public String processUpload(MultipartFile file, String data, String code) {

        log.info("ProcessEngineController.processUpload: code={}, fileName={}, size={}",
                code, file == null ? null : file.getOriginalFilename(),
                file == null ? 0 : file.getSize());

        try {
            Map<String, Object> dataMap = mapper.readValue(
                    data, new TypeReference<Map<String, Object>>() {});

            if (file != null && !file.isEmpty()) {
                dataMap.put("fileBytes", file.getBytes());
                dataMap.put("fileName", file.getOriginalFilename());
            }

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
        } catch (java.io.IOException e) {
            log.error("Could not read uploaded file for code={}: {}", code, e.getMessage(), e);
            return "{\"status\":\"ERROR\",\"message\":\"Could not read uploaded file\"}";
        }
    }
}
