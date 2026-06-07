package com.feign;

import org.springframework.cloud.openfeign.FeignClient;

import com.resource.ProcessEngineResource;

@FeignClient(name = "ProcessEngineClients", url = "http://localhost:8070")
public interface ProcessEngineClients extends ProcessEngineResource {
}
