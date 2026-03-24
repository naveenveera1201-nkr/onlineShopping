package com.configs;

import java.net.URL;
import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ProcessFlowEngine;
import com.models.ProcessFlowConfig;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ProcessFlowConfigLoader {
	
	@Bean
	public ProcessFlowEngine processFlowEngine() throws Exception {
		log.info("ProcessFlowConfigLoader processflowengine called...");
		URL processFlowOneURL = ProcessFlowConfigLoader.class.getClassLoader().getResource("process-flow.json");
		String processFlowOnePath = Path.of(processFlowOneURL.toURI()).toString();

		URL processFlowStatusOneURL = ProcessFlowConfigLoader.class.getClassLoader().getResource("process-flow-status.json");
		String processFlowStatusOnePath = Path.of(processFlowStatusOneURL.toURI()).toString();

		ProcessFlowConfig config = ProcessFlowConfig.builder()
				.processConfigPath(processFlowOnePath)
				.statusConfigPath(processFlowStatusOnePath)
				.connectionString("mongodb://localhost:27017/")
				.databaseName("invester-profile").build();
		
		log.info("ProcessFlowConfigLoader processflowengine ended...");

		return new ProcessFlowEngine(config);
	}
}
