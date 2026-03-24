package com.first.components;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.dto.ApiDefinition;
import com.first.services.ApiLogService;
import com.first.services.BusinessLogicExecutor;
import com.first.services.ResponseBuilders;
import com.first.services.SecurityService;
import com.first.services.ValidationService;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(1)
@Slf4j
public class RequestCachingFilter  implements Filter{

	@Autowired
	private ApiConfigLoader configLoader;

	@Autowired
	private SecurityService securityService;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private BusinessLogicExecutor businessLogicExecutor;

	@Autowired
	private ResponseBuilders responseBuilder;
	
	@Autowired
	private ApiLogService apiLogService;

	@SuppressWarnings("deprecation")
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest, 0);
		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

		/** Store request Body **/
		long start = System.currentTimeMillis();
//		String requestBody = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
//
//		if (requestBody.isBlank()) {
//			requestBody = new BufferedReader(new InputStreamReader(wrappedRequest.getInputStream())).lines()
//					.collect(Collectors.joining());
//		}
		
		String requestBody = readBody(wrappedRequest);

		log.info("doFilter method called...");
		ResponseEntity<Map<String, Object>> resposne = handleRequest(wrappedRequest,requestBody);

		log.info("REQUEST BODY = {}", requestBody);

		// Save log
		Map<String, Object> logMap = new HashMap<>();
		logMap.put("method", httpRequest.getMethod());
		logMap.put("path", httpRequest.getRequestURI());
		if (ObjectUtils.isNotEmpty(requestBody)) {
			logMap.put("request", new ObjectMapper().readValue(requestBody, Map.class));
		}
		logMap.put("timeMs", (System.currentTimeMillis() - start));
		logMap.put("requestHeaders", readRequestHeaders(wrappedRequest));

		if (ObjectUtils.isNotEmpty(resposne) && resposne.getStatusCode().value() != 200) {
			log.info("error response for doFilter {}", resposne.getBody().get("error"));
			log.warn("Error response in doFilter: {}", resposne.getBody().get("error"));

			// Set HTTP status
			httpResponse.setStatus(resposne.getStatusCode().value());

			// Set content type
			httpResponse.setContentType("application/json");

			// Write response body
			String jsonBody = new ObjectMapper().writeValueAsString(resposne.getBody());
			httpResponse.getWriter().write(jsonBody);
			httpResponse.getWriter().flush();

			wrappedResponse.copyBodyToResponse();
			logMap.put("status", wrappedResponse.getStatus());
			logMap.put("responseHeaders", readResponseHeaders(wrappedResponse));
			logMap.put("response",  new ObjectMapper().readValue(jsonBody, Map.class));
			apiLogService.save("api_logs", logMap);
			// Stop the chain (don't call controller)
			return;
		}

//		chain.doFilter(request, response);

	}
	
//	 public ResponseEntity<Map<String, Object>> handleRequest(
//	            HttpServletRequest request,
//	            @RequestBody(required = false) Map<String, Object> body,
//	            @RequestHeader Map<String, String> headers) {
		 
	public ResponseEntity<Map<String, Object>> handleRequest( HttpServletRequest request, String requestBody) throws IOException {
		
		log.info("handleRequest method called...");
	        String path = request.getRequestURI();
	        String method = request.getMethod();
	       
	        Map<String, String> headers = new HashMap<>();
	        
			Enumeration<String> headerNames = request.getHeaderNames();
			
			while (headerNames.hasMoreElements()) {
				String headerName = headerNames.nextElement();
				headers.put(headerName, request.getHeader(headerName));
			}

			log.info("handleRequest path :: {}", path);
			log.info("handleRequest method :: {}", method);
			
			 Map<String, Object> body = new HashMap<String, Object>();
			
			 try {
					if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())
							|| "GET".equalsIgnoreCase(request.getMethod())) {
//					try (BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()))) {
//
//						ObjectMapper objectMapper = new ObjectMapper();
//						
//							body.putAll(objectMapper.readValue(
//									reader.lines().collect(Collectors.joining(System.lineSeparator())), Map.class));
//						
//					}
					 if (!requestBody.isBlank()) {
			                body = new ObjectMapper().readValue(requestBody, Map.class);
			            }
				}
			 } catch (JsonMappingException e) {
					e.printStackTrace();
				} catch (JsonProcessingException e) {
					e.printStackTrace();
				}
			 
	        
	        // Find API definition
	        ApiDefinition apiDef = configLoader.findApi(method, path);
	        if (apiDef == null) {
	            return ResponseEntity.status(404)
	                    .body(Map.of("error", "API endpoint not found"));
	        }

	        try {
	        	
	            // 1. Security validation
//	            securityService.validateSecurity(apiDef, request, headers);

	            // 2. Rate limiting
//	            securityService.enforceRateLimit(apiDef, request);

	            // 3. Request validation
	            Map<String, Object> validatedParams = validationService.validate(
	                    apiDef, body, request, headers);
	
	            // 4. Execute business logic
	            Map<String, Object> result = businessLogicExecutor.execute(
	                    apiDef, validatedParams);

	            // 5. Build response
	            Map<String, Object> response = responseBuilder.build(
	                    apiDef, result, validatedParams);

//	            // 6. Execute callbacks
//	            businessLogicExecutor.executeCallbacks(apiDef, response, "SUCCESS");

	            return ResponseEntity.status(apiDef.getResponse().getSuccessCode())
	                    .body(response);

	        } catch (Exception e) {
	            return handleError(apiDef, e);
	        }
	    }
	 
	   private ResponseEntity<Map<String, Object>> handleError(
	            ApiDefinition apiDef, Exception e) {
	        businessLogicExecutor.executeCallbacks(apiDef,
	                Map.of("error", e.getMessage()), "FAILED");

	        return ResponseEntity.badRequest().body(Map.of(
	                "error", e.getClass().getSimpleName(),
	                "message", e.getMessage(),
	                "apiId", apiDef.getId()
	        ));
	    }
	   
	   
	   private Map<String, Object> logRequestAndResponse(ContentCachingRequestWrapper request) {

			String method = request.getMethod();
			String path = request.getRequestURI();

			// find API definition (method + path)
			ApiDefinition api = configLoader.findApi(method, path); // adapt to your loader method
			String apiId = api != null ? api.getId() : null;

//			ApiRequestResponseLog logEntry = new ApiRequestResponseLog();
			Map<String, Object> requestResponseLog = new HashMap<>();

//		        logEntry.setApiId(apiId);
//		        logEntry.setPath(path);
//		        logEntry.setMethod(method);
	//
//		        // headers
//		        logEntry.setRequestHeaders(readRequestHeaders(request));
//		        logEntry.setResponseHeaders(readResponseHeaders(response));
	//
//		        // request body
//	String reqBody = getRequestBody(request);
//		        logEntry.setRequestBody(reqBody);
	//
//		        // response body and status
//		        String resBody = getResponseBody(response);
//		        logEntry.setResponseBody(resBody);
//		        logEntry.setResponseStatus(response.getStatus());

			requestResponseLog.put("id", apiId);
			requestResponseLog.put("path", path);
			requestResponseLog.put("method", method);
//			requestResponseLog.put("requestHeaders", readRequestHeaders(request));
//		        requestResponseLog.put("responseHeaders", readResponseHeaders(response));
//		        requestResponseLog.put("requestBody", reqBody);
//		        requestResponseLog.put("responseBody", resBody);

//		        apiLogService.save("api_logs",requestResponseLog);

//		        log.info("Logged API call: apiId={}, path={}, method={}, status={}, durationMs={}",
//		                apiId, path, method, response.getStatus(), duration);
			log.info("Logged API call: apiId={}, path={}, method={}, status={}, durationMs={}", apiId, path, method);
			return requestResponseLog;
		}
	   
		private Map<String, String> readRequestHeaders(ContentCachingRequestWrapper request) {
			Map<String, String> map = new HashMap<>();
			Enumeration<String> headerNames = request.getHeaderNames();
			if (headerNames == null)
				return map;
			while (headerNames.hasMoreElements()) {
				String name = headerNames.nextElement();
				map.put(name, request.getHeader(name));
			}
			return map;
		}
		

		private Map<String, String> readResponseHeaders(ContentCachingResponseWrapper response) {
			Map<String, String> map = new HashMap<>();
			for (String name : response.getHeaderNames()) {
				map.put(name, response.getHeader(name));
			}
			return map;
		}
		

		private String getResponseBody(ContentCachingResponseWrapper response) {
			byte[] buf = response.getContentAsByteArray();
			if (buf == null || buf.length == 0)
				return null;
			return new String(buf, 0, buf.length, StandardCharsets.UTF_8);
		}
		
		 private String readBody(ContentCachingRequestWrapper request) throws IOException {
		        // after wrapping, body is cached
		        byte[] buf = request.getContentAsByteArray();

		        if (buf.length == 0) {
		            // read stream only ONCE if NOT YET read
		            String raw = new BufferedReader(
		                    new InputStreamReader(request.getInputStream()))
		                    .lines().collect(Collectors.joining());

		            return raw;
		        }

		        return new String(buf, StandardCharsets.UTF_8);
		    }

}
