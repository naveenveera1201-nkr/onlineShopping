package com.resource;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

//@RequestMapping("/api")
public interface WorkflowEngineResource {

	@PostMapping("/data")
	String process(@RequestParam("data") String data, @RequestParam("code") String code);
}
