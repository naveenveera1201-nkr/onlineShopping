package com.first.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

//import dto.BusinessLogicConfig;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Service;
//
//import java.util.HashMap;
//import java.util.Map;

import org.springframework.stereotype.Service;

import com.first.dao.DynamicMongoRepository;
import com.first.dto.BusinessLogicConfig;

@Service
public class DatabaseExecutor {
	
    @Autowired
	private DynamicMongoRepository repository;

    public Map<String, Object> execute(BusinessLogicConfig config,
			Map<String, Object> params) {
	
    	Map<String, Object> result = new HashMap<>();

		for (BusinessLogicConfig.OperationConfig operation : config.getOperations()) {
			switch (operation.getType().toUpperCase()) {
			case "INSERT":
				repository.insert(operation.getEntity(), params);
				result.put("result",repository.findOne(operation.getEntity(), params));
				break;
			case "SELECT":
				repository.findOne(operation.getEntity(), params);
				break;
			case "UPDATE":
//                    executeUpdate(operation, params, result);
				break;
			case "DELETE":
//                    executeDelete(operation, params, result);
				break;
			case "SELECTALL":
				repository.findAll(operation.getEntity(),result);//(operation.getEntity(), params);
			break;
			}
		}

		return result;
	}

}