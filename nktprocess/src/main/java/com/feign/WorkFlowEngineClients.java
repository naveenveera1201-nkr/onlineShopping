package com.feign;

import org.springframework.cloud.openfeign.FeignClient;

import com.resource.WorkflowEngineResource;

@FeignClient(name = "WorkFlowEngineClients", url = "http://localhost:8070")
public interface WorkFlowEngineClients extends WorkflowEngineResource {
}
