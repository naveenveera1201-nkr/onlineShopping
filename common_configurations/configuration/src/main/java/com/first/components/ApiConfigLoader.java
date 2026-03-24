package com.first.components;


import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.first.dto.ApiDefinition;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ApiConfigLoader {

//    private static final Logger logger = LoggerFactory.getLogger(ApiConfigLoader.class);

//    @Autowired
//    private ApiConfigProperties configProperties;

    private Map<String, ApiDefinition> apiRegistry = new HashMap<>();
//
//    @EventListener(ApplicationReadyEvent.class)
//    public void loadConfiguration() {
//
//		logger.info("Loading API configurations...");
//		logger.info("ApiConfigProperties bean: {}", configProperties);
//		logger.info("Loaded APIs: {}", configProperties.getApis());
//		
//        if (configProperties.getApis() != null) {
//            for (ApiDefinition api : configProperties.getApis()) {
//                if (api.isEnabled()) {
//                    String key = api.getMethod() + ":" + api.getPath();
//                    apiRegistry.put(key, api);
//                    logger.info("Registered API: {} - {}", key, api.getName());
//                }
//            }
//        }
//
//        logger.info("Total APIs loaded: {}", apiRegistry.size());
//    }
//
    public ApiDefinition findApi(String method, String path) {
        // Direct match
        String key = method + ":" + path;
        if (apiRegistry.containsKey(key)) {
            return apiRegistry.get(key);
        } else {
        	
        }

        // Pattern match for path variables
        for (Map.Entry<String, ApiDefinition> entry : apiRegistry.entrySet()) {
            String registeredPath = entry.getValue().getPath();
            if (matchesPathPattern(registeredPath, path) &&
                    entry.getKey().startsWith(method + ":")) {
                return entry.getValue();
            }
        }

        return null;
    }

    private boolean matchesPathPattern(String pattern, String path) {
        String regex = pattern.replaceAll("\\{[^}]+\\}", "[^/]+");
        return path.matches(regex);
    }

    public Map<String, ApiDefinition> getAllApis() {
        return apiRegistry;
    }

    public void reloadConfiguration() {
        apiRegistry.clear();
//        loadConfiguration();
        loadAllConfigs();
    }
    

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

//    private final List<ApiDefinition> apis = new ArrayList<>();

	/**
	 * This is needs to be executed after dependency injection is done to perform any
	 * initialization
	 **/
//    @PostConstruct
//    public void init() {
//        loadAllConfigs();
//    }
//    
    @EventListener(ApplicationReadyEvent.class)
    public void loadAllConfigs() {
//        apis.clear();
        try {
            Resource[] resources = resolver.getResources("classpath*:api-config/*.yml");
            log.info("Found {} API config files in classpath:api-config/", resources.length);
            for (Resource r : resources) {
                log.info(" -> loading file: {}", r.getFilename());
                try (InputStream is = r.getInputStream()) {
                    JsonNode root = yamlMapper.readTree(is);
                    JsonNode apisNode = root.get("apis");
                    if (apisNode != null && apisNode.isArray()) {
                        List<ApiDefinition> list = yamlMapper.convertValue(apisNode, new TypeReference<List<ApiDefinition>>() {});
                        log.info("    -> {} apis in {}", list.size(), r.getFilename());
//                        apis.addAll(list);
//						String key = list.get(0).getMethod() + ":" + list.get(0).getPath();
//                      apiRegistry.put(key, list.get(0));
                      for (ApiDefinition api : list) {
                    	    String key = api.getMethod() + ":" + api.getPath();
                    	    apiRegistry.put(key, api);
                    	}
                    } else {
                        log.warn("    -> no top-level 'apis' array found in {}", r.getFilename());
                    }
                } catch (Exception ex) {
                    log.error("    -> failed to parse {} : {}", r.getFilename(), ex.getMessage(), ex);
                }
            }
            log.info("Total APIs loaded: {}", apiRegistry.size());
        } catch (Exception e) {
            log.error("Failed to scan api-config folder: {}", e.getMessage(), e);
        }
    }

//    /** immutable view */
//    public List<ApiDefinition> getAllApi() {
//        return Collections.unmodifiableList(apiRegistry);
//    }
}