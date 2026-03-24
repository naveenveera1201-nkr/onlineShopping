package com.first.launcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import com.first.components.ApiConfigLoader;
import com.first.components.ApiConfigProperties;

@SpringBootApplication
@ComponentScan({"com.first"})
@Import(ApiConfigLoader.class)
@EnableConfigurationProperties(ApiConfigProperties.class)
@EnableFeignClients(basePackages = {"com"})
public class FirstprojectApplication {

	public static void main(String[] args) {
		SpringApplication.run(FirstprojectApplication.class, args);
	}

//	@Bean
//	CommandLineRunner test(ApiConfigProperties props) {
//	    return args -> System.out.println(">>> Config loaded: " + props.getApis());
//	}
//	
//	@Bean
//	CommandLineRunner verify(ApiConfigProperties props) {
//	    return args -> {
//	        System.out.println("✅ Loaded APIs: " + (props.getApis() != null ? props.getApis().size() : "NULL"));
//	    };
//	}
	
//	@Bean
//	CommandLineRunner verify(ApiConfigProperties props) {
//		return args ->
//	        System.out.println("Loaded APIs: " +
//	            (props.getApis() != null ? props.getApis() : "NULL"));
//
//		props.getApis().forEach(a -> System.out.println(" new Loaded: " + a.getId()));
//	}
	
//	@Bean
//	public OpenAPI customOpenAPI(ApiConfigLoader config) {
//		
//		Map<String, ApiDefinition> configs = config.getAllApis();
//		
//		System.out.println("customOpenAPI");
//
//		OpenAPI openAPI = new OpenAPI().info(new Info().title("Dynamic API Docs").version("1.0"));
//
//		Paths paths = new Paths();
//
////		if (config.getApis() != null) {
////			
//		  for (Map.Entry<String, ApiDefinition> entry : configs.entrySet()) {
////			for (ApiDefinition api : config.getApis()) {
//			  
//			  ApiDefinition  api = entry.getValue();
//
//				if (!api.isEnabled())
//					continue;
//
//				Operation operation = new Operation().operationId(api.getId()).summary(api.getName())
//						.description(api.getDescription());
//
//				PathItem pathItem = new PathItem();
//
//				switch (api.getMethod().toUpperCase()) {
//				case "POST":
//					pathItem.post(operation);
//					break;
//				case "GET":
//					pathItem.get(operation);
//					break;
//				case "PUT":
//					pathItem.put(operation);
//					break;
//				case "DELETE":
//					pathItem.delete(operation);
//					break;
//				}
//
//				paths.addPathItem(api.getPath(), pathItem);
//				
//				if (api.getRequest() != null &&
//					    api.getRequest().getParameters() != null) {
//
//					    ObjectSchema bodySchema = new ObjectSchema();
//					    List<String> requiredFields = new ArrayList<>();
//
//					    for (ParameterDefinition param : api.getRequest().getParameters()) {
//
//					        if (!"BODY".equalsIgnoreCase(param.getLocation()))
//					            continue;
//
//					        Schema<?> fieldSchema = mapType(param.getType());
//
//					        // ===== VALIDATIONS =====
//					        if (param.getValidation() != null) {
//
//					            ValidationConfig v = param.getValidation();
//
//					            if (v.getMinLength() != null)
//					                fieldSchema.setMinLength(v.getMinLength());
//
//					            if (v.getMaxLength() != null)
//					                fieldSchema.setMaxLength(v.getMaxLength());
//
//					            if (v.getPattern() != null)
//					                fieldSchema.setPattern(v.getPattern());
//
////					            if (v.getAllowedValues() != null)
////					                fieldSchema.setEnum(v.getAllowedValues());
//					        }
//
//					        bodySchema.addProperty(param.getName(), fieldSchema);
//
//					        if (param.isRequired())
//					            requiredFields.add(param.getName());
//					    }
//
//					    bodySchema.setRequired(requiredFields);
//
//					    RequestBody requestBody = new RequestBody()
//					            .content(new Content().addMediaType(
//					                    "application/json",
//					                    new MediaType().schema(bodySchema)));
//
//					    operation.setRequestBody(requestBody);
//					}
//			}
////		}
//
//		openAPI.setPaths(paths);
//		return openAPI;
//	}
//	
//	private Schema<?> mapType(String type) {
//
//	    return switch (type.toLowerCase()) {
//	        case "string" -> new StringSchema();
//	        case "integer" -> new IntegerSchema();
//	        case "boolean" -> new BooleanSchema();
//	        case "number" -> new NumberSchema();
//	        default -> new StringSchema();
//	    };
//	}
}
