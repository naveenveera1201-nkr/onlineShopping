package com.first.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for Project 2 (NKT Process Engine).
 * URL is externalised to application.yml: services.nkt-process.url
 */
@FeignClient(name = "workFlowEngineClient", url = "${services.nkt-process.url}")
public interface WorkFlowEngineClient {

    /**
     * Single entry point of the NKT Process Engine.
     *
     * @param data JSON-serialised request payload
     * @param code process code, e.g. "nkt.user.register"
     * @return JSON-serialised response string
     */
    @PostMapping("/data")
    String process(@RequestParam("data") String data,
                   @RequestParam("code") String code);
}
