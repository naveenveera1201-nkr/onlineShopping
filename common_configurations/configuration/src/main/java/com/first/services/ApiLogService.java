package com.first.services;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.first.dao.DynamicMongoRepository;

@Service
public class ApiLogService {

	@Autowired
    private DynamicMongoRepository repo;

//    public ApiLogService(ApiLogRepository repo) {
//        this.repo = repo;
//    }

//    public ApiRequestResponseLog save(ApiRequestResponseLog log) {
//        return repo.insert(log);
//    }
    
	public void save(String entityName, Map<String, Object> data) {
		repo.insert(entityName, data);
	}
}
