package com.service.handlers;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.nkt.NktProcessDefinition;
import com.repository.NktDynamicRepository;

/**
 * Contract for a custom NKT operation handler.
 *
 * Implementations are registered in {@link com.service.NktCoreService} under
 * the {@code HandlerKey} that matches the entry in {@code process-flow.json}.
 * The core service validates required fields and extracts the {@code userId}
 * from the JWT token before invoking any handler, so handlers can trust that
 * those concerns are already resolved.
 *
 * @param data   mutable request data (token already stripped)
 * @param userId authenticated user id, or {@code null} for public endpoints
 * @param repo   model-less MongoDB repository
 * @param mapper shared Jackson ObjectMapper
 * @return       JSON string response body
 */
@FunctionalInterface
public interface NktOperationHandler {
    String handle(Map<String, Object> data, String userId,
                  NktDynamicRepository repo, ObjectMapper mapper, NktProcessDefinition def);
}
