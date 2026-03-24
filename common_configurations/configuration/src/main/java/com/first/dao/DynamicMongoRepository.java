package com.first.dao;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class DynamicMongoRepository {

	@Autowired
	private MongoTemplate mongoTemplate;

	public void insert(String collectionName, Map<String, Object> data) {
		try {
			mongoTemplate.insert(data, collectionName);
		} catch (Exception e) {
			log.error("insert method got exception {}", e.getMessage());
			e.printStackTrace();
		}
	}

	public Object findOne(String collectionName, Map<String, Object> query) {
		return mongoTemplate.findById(query.get("_id"), Map.class, collectionName);
	}
	
	public Object findByName(String collectionName, Map<String, Object> data) {
		 Query query = new Query(Criteria.where("userName").is(data.get("userName")));
	        return mongoTemplate.findOne(query, Object.class);
	}
	
	public Object findAll(String collectionName, Map<String, Object> data) {

		return mongoTemplate.findAll(Map.class, collectionName);
	}
}