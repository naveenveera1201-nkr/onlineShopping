package com.first.dao;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.first.dto.ApiRequestResponseLog;

@Repository
public interface ApiLogRepository extends MongoRepository<ApiRequestResponseLog, String> {
    // add custom queries if needed
}
