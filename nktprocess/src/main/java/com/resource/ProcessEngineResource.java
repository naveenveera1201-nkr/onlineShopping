package com.resource;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

//@RequestMapping("/api")
public interface ProcessEngineResource {

    @PostMapping("/data")
    String process(
            @RequestBody Map<String, Object> data,
            @RequestParam("code") String code
    );
}