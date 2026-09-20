package com.first.services;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.first.components.NktProcessProperties;

@Service
public class ProcessEngineClient {

	private final RestClient restClient;

	public ProcessEngineClient(RestClient.Builder builder, NktProcessProperties properties) {
		
		 System.out.println("NKT PROCESS URL = [" + properties.getUrl() + "]");

		this.restClient = builder.baseUrl(properties.getUrl()).build();
	}

	 public String process(
	            Map<String, Object> data,
	            String processCode) {

	        System.out.println("Process code = " + processCode);
	        System.out.println("Request data = " + data);

	        return restClient.post()
	                .uri(uriBuilder -> uriBuilder
	                        .path("/data")
	                        .queryParam("code", processCode)
	                        .build())
	                .contentType(MediaType.APPLICATION_JSON)
	                .body(data)
	                .retrieve()
	                .body(String.class);
	    }
}
