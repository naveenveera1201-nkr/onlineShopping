package com;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.configs.ConfigLoader;
import com.configs.ConfigType;
import com.configs.registries.process.InMemoryProcessRegistry;
import com.configs.registries.process.ProcessRegistry;
import com.configs.registries.status.InMemoryStatusRegistry;
import com.configs.registries.status.StatusRegistry;
import com.exceptions.OptimisticLockException;
import com.exceptions.ProcessFlowException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.models.ApprovalStep;
import com.models.InvesterProfile;
import com.models.Process;
import com.models.ProcessFlowConfig;
import com.models.ProcessHistory;
import com.models.ProcessInstance;
import com.models.Status;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ProcessQueryBuilder;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.UpdateResult;

public class ProcessFlowEngine implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ProcessFlowEngine.class);

    private MongoClient mongoClient;
    private MongoDatabase database;
    private final ObjectMapper objectMapper;
    private final ProcessFlowConfig config;
    private final StatusRegistry statusRegistry;
    private final ProcessRegistry processRegistry;

    public ProcessFlowEngine() throws ProcessFlowException {
        this(ProcessFlowConfig.builder().build());
    }
    
    public ProcessRegistry getProcessRegistry() {
    	return this.processRegistry;
    }
    
    public ObjectMapper getObjectMapper() {
    	return this.objectMapper;
    }

    public ProcessFlowEngine(ProcessFlowConfig config) throws ProcessFlowException {
        this.config = config;
        this.objectMapper = JsonMapper.builder()
        		.addModule(new JavaTimeModule())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();


        this.statusRegistry = new InMemoryStatusRegistry();
        this.processRegistry = new InMemoryProcessRegistry();
        configMongoDB(config);
        loadConfig(config);
    }

    private void configMongoDB(ProcessFlowConfig config) throws ProcessFlowException {
        // Configure MongoDB client with connection pooling
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new com.mongodb.ConnectionString(config.getConnectionString()))
                .applyToConnectionPoolSettings(builder -> builder
                        .maxSize(config.getConnectionPoolSize())
                        .maxWaitTime(config.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(builder -> builder
                        .connectTimeout(config.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS)
                        .readTimeout(config.getSocketTimeoutMs(), TimeUnit.MILLISECONDS))
                .retryWrites(true)
                .retryReads(true)
                .build();

        MongoClient client = MongoClients.create(settings);

        CodecRegistry pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build())
                );
        MongoDatabase mongoDatabase = client
                .getDatabase(config.getDatabaseName())
                .withCodecRegistry(pojoCodecRegistry);
        validateMongoDBConnection(client, mongoDatabase);

        this.mongoClient = client;
        this.database = mongoDatabase;
        logger.info("[ProcessFlowService] Database configuration completed :: database-name : {}", config.getDatabaseName());
    }

    private void validateMongoDBConnection(MongoClient client, MongoDatabase mongoDatabase) throws ProcessFlowException {
        if (client == null || mongoDatabase == null) {
            throw new ProcessFlowException("MongoDB client or database not initialized");
        }

        try {
            // Official MongoDB health check
            mongoDatabase.runCommand(new BsonDocument("ping", new BsonInt32(1)));
        } catch (MongoTimeoutException e) {
            throw new ProcessFlowException("MongoDB connection timeout", e);
        } catch (MongoException e) {
            throw new ProcessFlowException("MongoDB connection validation failed", e);
        }
    }

    private void loadConfig(ProcessFlowConfig config) throws ProcessFlowException {
        long start = System.currentTimeMillis();
        try {
            ConfigLoader configLoader = ConfigLoader.defaultConfigLoader(objectMapper, processRegistry, statusRegistry);
            Map<ConfigType, String> externalConfigPaths = new HashMap<>();
            externalConfigPaths.put(ConfigType.STATUS, config.getStatusConfigPath());
            externalConfigPaths.put(ConfigType.PROCESS, config.getProcessConfigPath());
            configLoader.loadAll(externalConfigPaths);
            logger.info("[ProcessFlowService] Configuration registries loaded successfully in {} ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            logger.error("Failed to load configuration registries", e);
            throw new ProcessFlowException("Configuration loading failed", e);
        }
    }

//    public String createProcessInstance(String processCode, String createdBy, Map<String, Object> data) throws ProcessFlowException {
//        return createProcessInstance(processCode, createdBy, null);
//    }

    public String createProcessInstance(String processCode, String createdBy, Map<String, Object> data) throws ProcessFlowException {
    	
    	if (CommonUtils.isBlank(processCode)) {
            throw new ProcessFlowException("Process code must be non-empty");
        }
        if (CommonUtils.isBlank(createdBy)) {
            throw new ProcessFlowException("CreatedBy must be non-empty");
        }


        Process process = processRegistry.get(processCode);
        String statusDesc = statusRegistry.getDescription(process.getInitialStatus());

        ProcessInstance instance = new ProcessInstance();
        instance.setId(new ObjectId());
        instance.setProcessCode(processCode);
        instance.setCurrentStatus(process.getInitialStatus());
        instance.setCurrentStatusDesc(statusDesc);
        instance.setCurrentLevel(Boolean.TRUE.equals(process.getApprovalFlow()) ? 1 : 0);
        instance.setCreatedBy(createdBy);
        instance.setModifiedBy(createdBy);
        instance.setData(data);
        instance.setHistory(new ArrayList<>());

        // Add initial history entry
        ProcessHistory history = new ProcessHistory();
        history.setLevel(0);
        history.setAction(ProcessAction.CREATED.toString());
        history.setFromStatus(null);
        history.setToStatus(process.getInitialStatus());
        history.setPerformedBy(createdBy);
        history.setPerformedDate(Instant.now());
        instance.getHistory().add(history);

        // Retry mechanism for transient failures
        MongoCollection<ProcessInstance> collection = database.getCollection("process_instances", ProcessInstance.class);
       
		if (isDuplicate(collection, instance.getData())) {
			logger.info("Duplicates detected {}", instance.getData());
			try {
				return objectMapper.writeValueAsString(Map.of("statusCode", "400","statusDesc", "Duplicates detected"));
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		String s = executeWithRetry(() -> {
			collection.insertOne(instance);
			logger.info("Created process instance {}", instance.getId());
			return instance.getId().toHexString();
		}, "create process instance");

		String result = "";
		try {
			Map<String, Object> map = objectMapper.convertValue(getProcessInstance(s), Map.class);
			map.put("statusCode", 200);
			result = objectMapper.writeValueAsString(map);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		} catch (ProcessFlowException e) {
			e.printStackTrace();
		}

		return result;
    }
    
	public boolean isDuplicate(MongoCollection<ProcessInstance> collection, Map<String, Object> data) {

		 return collection.find(and(
		            eq("data.email", data.get("email")),
		            eq("data.username", data.get("username")),
		            eq("data.password", data.get("password"))
		    ))
		    .limit(1)
		    .first() != null;
	}

    public String performAction(String instanceId, String action, String performedBy, String comments) throws ProcessFlowException {
        
    	String result = "";
    	
    	if (CommonUtils.isBlank(instanceId)) {
            throw new ProcessFlowException("Instance ID code must be non-empty");
        }
        if (CommonUtils.isBlank(action)) {
            throw new ProcessFlowException("Action cannot be null");
        }
        if (CommonUtils.isBlank(performedBy)) {
            throw new ProcessFlowException("Performed by must be non-empty");
        }

        ProcessInstance processInstance = executeWithRetry(() -> {
        	
            MongoCollection<ProcessInstance> collection = database.getCollection("process_instances", ProcessInstance.class);

            // Use optimistic locking if enabled
            ProcessInstance instance;
            Integer originalVersion = null;

            instance = getProcessInstance(instanceId);
            if(isProcessComplete(instance)) {
                throw new ProcessFlowException("Invalid operation: process already completed");
            }

            if (config.isEnableOptimisticLocking()) {
                originalVersion = instance.getVersion();
            }

            Process process = processRegistry.get(instance.getProcessCode());
            if (process == null || !Boolean.TRUE.equals(process.getApprovalFlow())) {
                throw new ProcessFlowException("Process does not support approval flow");
            }

            // Find the applicable approval step
            ApprovalStep step = process.getApprovalSteps()
                    .stream()
                    .filter(s -> s.getAction().equalsIgnoreCase(action) && s.getPrevStatus().equals(instance.getCurrentStatus()))
                    .findFirst()
                    .orElseThrow(() -> new ProcessFlowException(String.format("Invalid action '%s' for status %d at level %d", action, instance.getCurrentStatus(), instance.getCurrentLevel())));

            // Calculate duration in previous status
            long durationMs = 0;
            if (!instance.getHistory().isEmpty()) {
                ProcessHistory lastHistory = instance.getHistory().get(instance.getHistory().size() - 1);
                durationMs = Duration.between(lastHistory.getPerformedDate(), Instant.now()).toMillis();
            }

            // Update instance
            Integer oldStatus = instance.getCurrentStatus();
            Integer oldLevel = instance.getCurrentLevel();
            instance.setCurrentStatus(step.getNextStatus());
            String statusDesc = statusRegistry.getDescription(step.getNextStatus());
            instance.setCurrentStatusDesc(statusDesc);

            // Update level
            instance.setCurrentLevel(step.getLevel());

            instance.setModifiedDate(LocalDateTime.now());
            instance.setModifiedBy(performedBy);
            instance.setVersion(instance.getVersion() + 1);

            // Add history entry
            ProcessHistory history = new ProcessHistory();
            history.setLevel(oldLevel);
            history.setAction(action);
            history.setFromStatus(oldStatus);
            history.setToStatus(step.getNextStatus());
            history.setPerformedBy(performedBy);
            history.setComments(comments);
            history.setDurationMs(durationMs);
            instance.getHistory().add(history);

            if(isProcessComplete(instance)) {
                moveToCompletedCollection(instance, originalVersion);
            } else if (config.isEnableOptimisticLocking()) {
                long updateCount = collection.replaceOne(and(eq("_id", new ObjectId(instanceId)), eq("version", originalVersion)), instance).getModifiedCount();
                if (updateCount == 0) {
                    throw new OptimisticLockException("Process instance was modified by another transaction. Please retry.");
                }
            } else {
                collection.replaceOne(eq("_id", new ObjectId(instanceId)), instance);
            }

            logger.info("Performed action '{}' on instance {} by {}", action, instanceId, performedBy);
            return instance;

        }, "perform action");
        
        try {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("statusCode", 200);
			result = objectMapper.writeValueAsString(map);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
        
        return result;
    }

    private void moveToCompletedCollection(ProcessInstance instance, Integer originalVersion)
            throws ProcessFlowException {

        MongoCollection<ProcessInstance> active =
                database.getCollection("process_instances", ProcessInstance.class);
        MongoCollection<ProcessInstance> completed =
                database.getCollection("process_instances_completed", ProcessInstance.class);

        long deleteCount = active.deleteOne(
                and(eq("_id", instance.getId()), eq("version", originalVersion))
        ).getDeletedCount();

        if (deleteCount == 0) {
            throw new OptimisticLockException("Process instance modified before completion");
        }

        completed.insertOne(instance);
    }

    public List<String> getAvailableActions(String instanceId) throws ProcessFlowException {
        ProcessInstance instance = getProcessInstance(instanceId);
        Process process = processRegistry.get(instance.getProcessCode());

        if (process == null || !Boolean.TRUE.equals(process.getApprovalFlow())) {
            return Collections.emptyList();
        }

        return process.getApprovalSteps().stream().filter(s -> s.getPrevStatus().equals(instance.getCurrentStatus())).map(ApprovalStep::getAction).collect(Collectors.toList());
    }

    public ProcessInstance getProcessInstance(String instanceId) throws ProcessFlowException {
       
    	if(CommonUtils.isBlank(instanceId)) {
            throw new ProcessFlowException("Instance ID cannot be null");
        }

        return executeWithRetry(() -> {
            MongoCollection<ProcessInstance> collection =
                    database.getCollection("process_instances", ProcessInstance.class);

            ProcessInstance instance = collection
                    .find(eq("_id", new ObjectId(instanceId)))
                    .first();
            
            instance.setId(new ObjectId(instanceId));

            if (instance == null) {
                throw new ProcessFlowException("Process instance not found: " + instanceId);
            }
            return instance;
        }, "get process instance");
    }

    public List<ProcessInstance> getProcessInstancesByEntity(Integer status, Integer limit) {
        MongoCollection<Document> collection = database.getCollection("process_instances");
        List<ProcessInstance> instances = new ArrayList<>();

        var filter = eq("currentStatus", status);

        FindIterable<Document> results = collection.find(filter).sort(new Document("createdDate", -1));

        if (limit != null && limit > 0) {
            results.limit(limit);
        } else {
            results.limit(10);
        }

        results.forEach(doc -> {
            try {
                instances.add(objectMapper.readValue(doc.toJson(), ProcessInstance.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        return instances;
    }

    public List<ProcessInstance> queryInstances(ProcessQueryBuilder query) {
        MongoCollection<Document> collection = database.getCollection("process_instances");
        List<ProcessInstance> instances = new ArrayList<>();

        FindIterable<Document> results = collection.find(query.build()).sort(query.getSort()).skip(query.getSkip()).limit(query.getLimit());

        results.forEach(doc -> {
            try {
                instances.add(objectMapper.readValue(doc.toJson(), ProcessInstance.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        return instances;
    }

    public long countInstances(ProcessQueryBuilder query) {
        MongoCollection<Document> collection = database.getCollection("process_instances");
        return collection.countDocuments(query.build());
    }

    public String getStatusDescription(Integer statusCode) throws ProcessFlowException {
        Status status = statusRegistry.get(statusCode);
        return status != null ? status.getDescription() : "Unknown";
    }

    public ProcessInstance getCompletedProcessInstance(String instanceId) throws ProcessFlowException {
        if (CommonUtils.isBlank(instanceId)) {
            throw new ProcessFlowException("Instance ID must be non-empty");
        }

        try {
            MongoCollection<ProcessInstance> completedCollection = database.getCollection("process_instances_completed", ProcessInstance.class);
            return completedCollection.find(eq("_id", new ObjectId(instanceId))).first();
        } catch (IllegalArgumentException e) {
            // ObjectId parsing error
            throw new ProcessFlowException("Invalid instance ID format", e);
        } catch (Exception e) {
            throw new ProcessFlowException("Failed to fetch completed process instance", e);
        }
    }


    private boolean isProcessComplete(ProcessInstance instance) throws ProcessFlowException {
        Process process = processRegistry.get(instance.getProcessCode());

        if (process == null || !Boolean.TRUE.equals(process.getApprovalFlow())) {
            return false;
        }

        return instance.getCurrentLevel() >= process.getNoOfLevels();
    }

    private <T> T executeWithRetry(RetryableOperation<T> operation, String operationName) throws ProcessFlowException {

        int attempts = 0;
        Exception lastException = null;

        while (attempts < config.getRetryAttempts()) {
            try {
                return operation.execute();
            } catch (OptimisticLockException e) {
                throw e; // Don't retry optimistic lock failures
            } catch (ProcessFlowException e) {
                throw e; // Don't retry business logic errors
            } catch (Exception e) {
                lastException = e;
                attempts++;

                if (attempts < config.getRetryAttempts()) {
                    logger.warn("Retry attempt {} for {} failed: {}", attempts, operationName, e.getMessage());
                    try {
                        Thread.sleep(config.getRetryDelayMs() * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ProcessFlowException("Operation interrupted", ie);
                    }
                }
            }
        }

        throw new ProcessFlowException(String.format("Failed to %s after %d attempts", operationName, attempts), lastException);
    }

    
	/*** New methods for mutual fund ***/    
    
	public String registration(Map<String, Object> data, String processCode) throws ProcessFlowException {

		if (CollectionUtils.isEmpty(data)) {
			throw new ProcessFlowException("Request data cannot be empty");
		}

		InvesterProfile instance = executeWithRetry(() -> {
			
			Process process = processRegistry.get(processCode);

			if (process == null) {
				throw new ProcessFlowException("Invalid process code: " + processCode);
			}

			MongoCollection<InvesterProfile> collection = database.getCollection(process.getCollectionName(),
					InvesterProfile.class);

			InvesterProfile profile;

			Map<String, Object> profileData;

			if (data.get("id") != null) {

				profile = getInvesterProfile(data.get("id").toString(), process.getCollectionName());
				
				profileData = profile.getData();
				
				if (Objects.nonNull(profileData.get(process.getObjectName()))) {
					profileData.remove(process.getObjectName());
				}

			} else {

				profile = new InvesterProfile();
				profileData = new HashMap<>();
			}

			// add new object
			profileData.put(process.getObjectName(), data);

			// set updated data
			profile.setData(profileData);

			// update profile

			// 🔹 Status update
			Integer initialStatus = process.getInitialStatus();
			profile.setCurrentStatus(initialStatus);
			profile.setCurrentStatusDesc(statusRegistry.getDescription(initialStatus));

			profile.setModifiedDate(LocalDateTime.now());
			profile.setModifiedBy("SYSTEM");
			

			save(profile, collection);

			return profile;

		}, "registration");

		return "{\"statusCode\":200}";
	}

	public InvesterProfile getInvesterProfile(String instanceId, String collectionName) throws ProcessFlowException {

		if (CommonUtils.isBlank(instanceId)) {
			throw new ProcessFlowException("Instance ID cannot be blank");
		}

		return executeWithRetry(() -> {

			MongoCollection<InvesterProfile> collection = database.getCollection(collectionName,
					InvesterProfile.class);

			InvesterProfile instance = collection.find(Filters.eq("_id", new ObjectId(instanceId))).first();

			if (instance == null) {
				throw new ProcessFlowException("Process instance not found: " + instanceId);
			}

			return instance;

		}, "get invester profile");
	}
	
	public String getProfileDetails(String instanceId, String processCode) throws ProcessFlowException {

		Process process = processRegistry.get(processCode);
		
		if (CommonUtils.isBlank(instanceId)) {
			throw new ProcessFlowException("Instance ID cannot be blank");
		}

		return executeWithRetry(() -> {

			MongoCollection<InvesterProfile> collection = database.getCollection(process.getCollectionName(),
					InvesterProfile.class);

			InvesterProfile instance = collection
			        .find(Filters.and(
			                Filters.eq("_id", new ObjectId(instanceId)),
			                Filters.exists("data."+process.getObjectName())
			        ))
			        .first();

			Map<String, Object> mergedData = new HashMap<>();
			
			if (instance == null) {
				throw new ProcessFlowException("Process instance not found: " + instanceId);
			}
			
			mergedData.put("data", instance.getData().get(process.getObjectName()));

			return objectMapper.writeValueAsString(mergedData);

		}, "get profile details");
	}
	

	private void save(InvesterProfile instance, MongoCollection<InvesterProfile> collection)
			throws ProcessFlowException {

		if (instance.getId() == null) {
			
			if (instance.getData() != null && instance.getData().get("id") != null) {
				
				Bson filter = Filters.and(Filters.eq("data.id", instance.getData().get("id")));
				
				InvesterProfile res = collection.find(filter).first();

				if (Objects.nonNull(res)) {
					UpdateResult replaced = collection.replaceOne(filter, instance);
					return;
				}
			}
			collection.insertOne(instance);
			return;
		}

		Bson filter = Filters.and(Filters.eq("_id", instance.getId()));

		UpdateResult result = collection.replaceOne(filter, instance);

		if (result.getModifiedCount() == 0) {
			throw new OptimisticLockException("Document modified by another transaction");
		}
	}
	
	private void addHistory(InvesterProfile profile) {

	    InvesterProfile history = new InvesterProfile();
	    history.setData(profile.getData());
	    history.setModifiedDate(LocalDateTime.now());

	    if (profile.getHistory() == null) {
	        profile.setHistory(new ArrayList<>());
	    }

	    profile.getHistory().add(history);
	}
	

	public String getInvesterProfile(String processCode) throws ProcessFlowException {

//		instances =
//		        collection.find()
//		                  .projection(Projections.include("data", "workflowStatus"))
//		                  .into(new ArrayList<>());
//		
//		instances =
//		        collection.find(Filters.eq("investorId", "id"))
//		                  .into(new ArrayList<>());
		
		Process process = processRegistry.get(processCode);
		
		return executeWithRetry(() -> {

			MongoCollection<InvesterProfile> collection = database.getCollection(process.getCollectionName(), InvesterProfile.class);

			List<Map<String, Object>> instances = collection.find().map(doc -> doc.getData()).into(new ArrayList<>());
			
			Map<String, Object> mergedData = new HashMap<>();
			
			mergedData.put("data", instances);

//	        List<InvesterProfile> instances = collection.find().into(new ArrayList<>());

			if (CollectionUtils.isEmpty(instances)) {
				throw new ProcessFlowException("Process instance not found");
			}

			return objectMapper.writeValueAsString(mergedData);

		}, "get investor profile");
	}
	
	
	public String getAllFunds(Map<String, Object> dataMap, String processCode) throws ProcessFlowException {

		Process process = processRegistry.get(processCode);

		return executeWithRetry(() -> {

			MongoCollection<Document> collection = database.getCollection(process.getCollectionName());

			int page = Objects.nonNull(dataMap.get("page")) ? Integer.valueOf(dataMap.get("page").toString()) : 0;
			int size = Objects.nonNull(dataMap.get("size")) ? Integer.valueOf(dataMap.get("size").toString()) : 0;

			List<Map<String, Object>> instances = collection.find().skip(page * size).limit(size).map(doc -> {

				Map<String, Object> map = new HashMap<>(doc);

				map.put("id", doc.getObjectId("_id").toString());
				map.remove("_id");

				return map;

			}).into(new ArrayList<>());

//			MongoCollection<InvesterProfile> collection = database.getCollection(process.getCollectionName(),
//					InvesterProfile.class);

//			List<Map<String, Object>> instances = collection.find()
//					.skip(page * size).limit(size).map(doc -> {
//
//				Map<String, Object> map = new HashMap<>();
//
//				map.put("id", doc.getId());
//				map.putAll(doc.getData());
//
//				return map;
//
//			}).into(new ArrayList<>());

			if (instances.isEmpty()) {
				throw new ProcessFlowException("Process instance not found");
			}
			
			Map<String, Object> response = new HashMap<>();
			response.put("page", page);
			response.put("size", size);
			response.put("count", collection.countDocuments());
			response.put("data", instances);

			return objectMapper.writeValueAsString(response);

		}, "get all funds");
	}
    

	public String getFundById(Map<String, Object> dataMap, String processCode) throws ProcessFlowException, JsonProcessingException {

		String fundId = dataMap.get("fundId").toString();
		Bson filter = Filters.eq("_id", new ObjectId(fundId));

		List<Map<String, Object>> list = fetchFunds(filter, null, 1, 1, processCode);

		return objectMapper.writeValueAsString(list);
	}
	
	public String getFundCategories(Map<String, Object> dataMap, String processCode)
			throws ProcessFlowException, JsonProcessingException {

		Map<String, Object> categories = new HashMap<>();

		if (CollectionUtils.isEmpty(dataMap)) {
			Process process = processRegistry.get(processCode);

			MongoCollection<Document> collection = database.getCollection(process.getCollectionName());

			List<String> fundCategories = collection.distinct("fundCategory", String.class).into(new ArrayList<>());

			categories.put("data", fundCategories);

		} else {

			return getByCategory(dataMap, processCode);
		}
		return objectMapper.writeValueAsString(categories);
	}
	
	public String getByCategory(Map<String, Object> dataMap, String processCode)
			throws ProcessFlowException, JsonProcessingException {

		Process process = processRegistry.get(processCode);
		return executeWithRetry(() -> {
			String category = Objects.nonNull(dataMap.get("category")) ? dataMap.get("category").toString() : "";
			int page = Objects.nonNull(dataMap.get("page")) ? Integer.valueOf(dataMap.get("page").toString()) : 0;
			int size = Objects.nonNull(dataMap.get("size")) ? Integer.valueOf(dataMap.get("size").toString()) : 0;

			Bson filter = Filters.eq("fundCategory", category);

			Bson sort = Sorts.descending("AUM_Cr");

			MongoCollection<Document> collection = database.getCollection(process.getObjectName());

			int skip = (page - 1) * size;

			List<Map<String, Object>> instances = collection.find(filter).sort(sort).skip(skip).limit(size).map(doc -> {

				Map<String, Object> map = new HashMap<>(doc);

				map.put("id", doc.getObjectId("_id").toString());
				map.remove("_id");

				return map;

			}).into(new ArrayList<>());

			if (instances.isEmpty()) {
				throw new ProcessFlowException("Process instance not found");
			}

			Map<String, Object> response = new HashMap<>();
			response.put("page", page);
			response.put("size", size);
			response.put("count", collection.countDocuments());
			response.put("data", instances);

			return objectMapper.writeValueAsString(response);
		}, "get category funds");

	}
//	public List<Map<String,Object>> getTopPerformers(int page,int size){
//
//	    Bson filter = Filters.exists("data.oneYearReturn");
//
//	    Bson sort = Sorts.descending("data.oneYearReturn");
//
//	    return fetchFunds(filter,sort,page,size);
//	}
	
	public List<Map<String,Object>> getTopPerformers(Map<String, Object> dataMap ,String processCode) throws ProcessFlowException{
		
		String period = Objects.nonNull(dataMap.get("period")) ? dataMap.get("period").toString() : "";
		int page = Objects.nonNull(dataMap.get("page")) ? Integer.valueOf(dataMap.get("page").toString()) : 0;
		int size = Objects.nonNull(dataMap.get("size")) ? Integer.valueOf(dataMap.get("size").toString()) : 0;


	    String sortField = switch (period.toLowerCase()) {
	        case "1y" -> "data.oneYearReturn";
	        case "3y" -> "data.threeYearReturn";
	        case "5y" -> "data.fiveYearReturn";
	        default -> throw new IllegalArgumentException("Invalid period. Allowed: 1Y, 3Y, 5Y");
	    };

	    Bson filter = Filters.exists(sortField);
	    Bson sort = Sorts.descending(sortField);

	    return fetchFunds(filter, sort, page, size,processCode);
	}
	
	
	
	public List<Map<String,Object>> searchFunds(String keyword,int page,int size,String processCode) throws ProcessFlowException{

//	    Bson filter = Filters.regex("data.fundName",keyword,"i");
//
//	    return fetchFunds(filter,null,page,size);
		
	    List<Bson> filters = new ArrayList<>();

		  if (keyword != null && !keyword.isEmpty()) {

		        Bson keywordFilter = Filters.or(
		                Filters.regex("data.fundName", keyword, "i"),
		                Filters.regex("data.fundManager", keyword, "i"),
		                Filters.regex("data.fundCategory", keyword, "i"),
		                Filters.regex("data.currentNAV", keyword, "i"),
		                Filters.regex("data.oneYearReturn", keyword, "i"),
		                Filters.regex("data.threeYearReturn", keyword, "i"),
		                Filters.regex("data.fiveYearReturn", keyword, "i"),
		                Filters.regex("data.minInvest", keyword, "i"),
		                Filters.regex("data.fundRating", keyword, "i"),
		                Filters.regex("data.fundManager", keyword, "i"),
		                Filters.regex("data.AUM_Cr", keyword, "i"),
		                Filters.regex("data.launchDate", keyword, "i")
		        );

		        filters.add(keywordFilter);
		    }
		
		  Bson filter = filters.isEmpty()
		            ? new Document()
		            : Filters.and(filters);
		  
		  return fetchFunds(filter,null,page,size,processCode);
	}
	
	public List<Map<String,Object>> compareFunds(List<String> ids,String processCode) throws ProcessFlowException{

	    List<ObjectId> objectIds = ids.stream()
	            .map(ObjectId::new)
	            .toList();

	    Bson filter = Filters.in("_id",objectIds);

	    return fetchFunds(filter,null,1,10,processCode);
	}
	
	public List<Map<String,Object>> getFavoriteFunds(List<String> fundIds,String processCode) throws ProcessFlowException{

	    List<ObjectId> ids = fundIds.stream()
	            .map(ObjectId::new)
	            .toList();

	    Bson filter = Filters.in("_id",ids);

	    return fetchFunds(filter,null,1,50,processCode);
	}
	
	public List<Map<String, Object>> fetchFunds(Bson filter, Bson sort, int page, int size, String processCode) throws ProcessFlowException  {
		
		Process process = processRegistry.get(processCode);

		MongoCollection<Document> collection = database.getCollection(process.getObjectName());

		int skip = (page - 1) * size;

		List<Map<String, Object>> results = new ArrayList<>();

		collection.find(filter).sort(sort).skip(skip).limit(size).forEach(doc -> {

			Map<String, Object> fund = new HashMap<>();

			fund.put("id", doc.getObjectId("_id").toHexString());
			fund.putAll((Map<String, Object>) doc.get("data"));

			results.add(fund);
		});

		return results;
	}
    
    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }

        logger.info("ProcessFlowService closed");
    }
}