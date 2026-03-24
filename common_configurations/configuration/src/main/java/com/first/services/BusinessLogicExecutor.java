package com.first.services;


import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.feign.WorkFlowEngineClients;
import com.first.dto.ApiDefinition;
import com.first.dto.BusinessLogicConfig;
import com.first.functionalInterface.ProcessFlowInterface;

import tools.jackson.databind.ObjectMapper;

@Service
public class BusinessLogicExecutor {

    @Autowired
    private DatabaseExecutor databaseExecutor;

    @Autowired
    private ExternalApiExecutor externalApiExecutor;

    @Autowired
    private CallbackExecutor callbackExecutor;
    
//    @Autowired
//    private BusinessLogicServices businessLogicServices;
	
//    @Autowired
//	private ProcessFlowEngineClients processFlowEngineClients;
    
    @Autowired
   	private WorkFlowEngineClients workFlowEngineClients;

    public Map<String, Object> execute(ApiDefinition apiDef,
                                       Map<String, Object> params) {
        
    	BusinessLogicConfig businessLogic = apiDef.getBusinessLogic();

        if (businessLogic == null) {
            return generateMockResponse(apiDef, params);
        }

		/** Business Logic **/
        if(businessLogic.getBusinessLogicRequired() != null && businessLogic.getBusinessLogicRequired().equalsIgnoreCase("Y")) {
        	
        	String [] requestAllowedField = apiDef.getRequest().getAllowedFields();
        	
        	params = validateAllowedFields(params,requestAllowedField);
        	
//        	executeCustomService(businessLogic, params);
        }

        switch (businessLogic.getType().toUpperCase()) {
            case "DATABASE":
            	return databaseExecutor.execute(businessLogic, params);
//                  generateMockResponse(apiDef, params);
            case "EXTERNAL_API":
//                return externalApiExecutor.execute(businessLogic, params);
            case "CUSTOM_SERVICE":
                return executeCustomService(businessLogic, params);
            case "MOCK":
            default:
                return generateMockResponse(apiDef, params);
        }
    }

	private Map<String, Object> executeCustomService(BusinessLogicConfig config, Map<String, Object> params) {
		// Implement custom service logic
//		BusinessInterface businessCall = businessLogicServices::addUser;
//		businessCall.execute(params);
//		return new HashMap<>();
		
		// create a engine object to call a business components
		ObjectMapper mapper = new ObjectMapper();
		// Convert Map to JSON String
		String json = mapper.writeValueAsString(params);
		
//		ProcessFlowInterface businessCall = processFlowEngineClients::process;
		
		ProcessFlowInterface businessCall = workFlowEngineClients::process;
		
		String result = businessCall.execute(json,config.getProcessCode());
		Map<String, Object> map = mapper.readValue(result, Map.class);
		return map;
	}

	private Map<String, Object> generateMockResponse(ApiDefinition apiDef, Map<String, Object> params) {
		
		Map<String, Object> response = new HashMap<>();

		if (apiDef.getResponse() != null && apiDef.getResponse().getFields() != null) {

			apiDef.getResponse().getFields().forEach(field -> {
				Object value = field.getDefaultValue() != null ? field.getDefaultValue()
						: generateMockValue(field.getType(), field.getName());
				response.put(field.getName(), value);
			});
		}

		return response;
	}

    private Object generateMockValue(String type, String fieldName) {
        switch (type) {
            case "Long":
                return System.currentTimeMillis();
            case "Integer":
                return 100;
            case "String":
                return fieldName.contains("Id") ?
                        UUID.randomUUID().toString() : "mock-" + fieldName;
            case "Boolean":
                return true;
            case "BigDecimal":
                return 1000.50;
            case "LocalDateTime":
                return LocalDateTime.now().toString();
            case "LocalDate":
                return LocalDateTime.now().toLocalDate().toString();
            default:
                return "mock-value";
        }
    }

    public void executeCallbacks(ApiDefinition apiDef,
                                 Map<String, Object> response,
                                 String event) {
        if (apiDef.getCallbacks() == null) return;

        apiDef.getCallbacks().stream()
                .filter(cb -> cb.getEvent().equalsIgnoreCase(event))
                .forEach(cb -> callbackExecutor.execute(cb, response));
    }
    
    public Map<String, Object> validateAllowedFields(
            Map<String, Object> requestBody,
            String[] allowedFields) {

        if (allowedFields == null || allowedFields.length == 0) {
            return requestBody; // No restriction
        }

        Set<String> allowedSet = new HashSet<>(Arrays.asList(allowedFields));

        List<String> invalidFields = requestBody.keySet().stream()
                .filter(field -> !allowedSet.contains(field))
                .collect(Collectors.toList());

        if (!invalidFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid fields present: " + invalidFields
            );
        }
		return requestBody;
    }
}