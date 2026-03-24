package com.first.controller;

//@RestController
//@RequestMapping(value = "/api")
public class DynamicApiController  {
//   
//	public void execute() {
//    }
//
//    @Autowired
//    private ApiConfigLoader configLoader;
//
//    @Autowired
//    private SecurityService securityService;
//
//    @Autowired
//    private ValidationService validationService;
//
//    @Autowired
//    private BusinessLogicExecutor businessLogicExecutor;
//
//    @Autowired
//    private ResponseBuilders responseBuilder;
//    
//    @GetMapping("/hello")
//    public String sayHello() {
//    	return "Hello, Spring Boot REST!";
//    }
//    
//
////	@RequestMapping(value = "/first/")
////    @GetMapping("/first")
////    public ResponseEntity<Map<String, Object>> handleRequest(
////            HttpServletRequest request,
////            @RequestBody(required = false) Map<String, Object> body,
////            @RequestHeader Map<String, String> headers) {
////
////        String path = request.getRequestURI();
////        String method = request.getMethod();
////
////        // Find API definition
////        ApiDefinition apiDef = configLoader.findApi(method, path);
////        if (apiDef == null) {
////            return ResponseEntity.status(404)
////                    .body(Map.of("error", "API endpoint not found"));
////        }
////
////        try {
////            // 1. Security validation
////            securityService.validateSecurity(apiDef, request, headers);
////
////            // 2. Rate limiting
////            securityService.enforceRateLimit(apiDef, request);
////
////            // 3. Request validation
////            Map<String, Object> validatedParams = validationService.validate(
////                    apiDef, body, request, headers);
////
////            // 4. Execute business logic
////            Map<String, Object> result = businessLogicExecutor.execute(
////                    apiDef, validatedParams);
////
////            // 5. Build response
////            Map<String, Object> response = responseBuilder.build(
////                    apiDef, result, validatedParams);
////
////            // 6. Execute callbacks
////            businessLogicExecutor.executeCallbacks(apiDef, response, "SUCCESS");
////
////            return ResponseEntity.status(apiDef.getResponse().getSuccessCode())
////                    .body(response);
////
////        } catch (Exception e) {
////            return handleError(apiDef, e);
////        }
////    }
//
////    @PostMapping("/api/**")
////    public ResponseEntity<?> handleDynamicPost(HttpServletRequest request, @RequestBody Map<String, Object> body) {
////        String path = request.getRequestURI();
////        String method = request.getMethod();
////
////        ApiDefinition api = configLoader.findApi(method, path);
////
//////        if (api == null) {
//////            return ResponseEntity.status(HttpStatus.NOT_FOUND)
//////                    .body(Map.of("error", "API not found"));
//////        }
////
////		if (api.getBusinessLogic() != null && "DATABASE".equalsIgnoreCase(api.getBusinessLogic().getType())) {
////            
////            // Choose Mongo or SQL based on dataSource
////            if ("mongo".equalsIgnoreCase(api.getBusinessLogic().getDataSource())) {
////            	businessLogicExecutor.execute(api, body);
////                return ResponseEntity.status(HttpStatus.CREATED)
////                        .body(Map.of("message", "Data inserted into MongoDB successfully"));
////            }
////        }
////
////        return ResponseEntity.ok(Map.of("message", "API executed successfully"));
////    }
////    @GetMapping("api/admin/reload-config")
////    public ResponseEntity<Map<String, String>> reloadConfig() {
////        configLoader.reloadConfiguration();
////        return ResponseEntity.ok(Map.of(
////                "status", "success",
////                "message", "Configuration reloaded",
////                "totalApis", String.valueOf(configLoader.getAllApis().size())
////        ));
////    }
////
////    @GetMapping("/api/admin/list-apis")
////    public ResponseEntity<Map<String, Object>> listApis() {
////        return ResponseEntity.ok(Map.of(
////                "apis", configLoader.getAllApis().values(),
////                "total", configLoader.getAllApis().size()
////        ));
////    }
////
////    private ResponseEntity<Map<String, Object>> handleError(
////            ApiDefinition apiDef, Exception e) {
////        businessLogicExecutor.executeCallbacks(apiDef,
////                Map.of("error", e.getMessage()), "FAILED");
////
////        return ResponseEntity.badRequest().body(Map.of(
////                "error", e.getClass().getSimpleName(),
////                "message", e.getMessage(),
////                "apiId", apiDef.getId()
////        ));
////    }
}
