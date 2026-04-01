package com.applicaiton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import lombok.extern.slf4j.Slf4j;

/**
 * NammaKadaiTheru Process Engine — entry point.
 *
 * Component scan covers all packages under {@code com}:
 *   com.applicaiton   – Spring Boot entry point
 *   com.configs       – SecurityConfig, WebSocketConfig, OpenApiConfig, ProcessFlowConfigLoader
 *   com.controller    – REST controllers (workflow engine + NKT API)
 *   com.dto           – Request / response DTOs
 *   com.exceptions    – Custom exceptions + GlobalExceptionHandler
 *   com.feign         – OpenFeign clients
 *   com.models        – Domain models (workflow + NKT e-commerce)
 *   com.mongodb       – MongoDB query utilities
 *   com.repository    – Spring Data MongoDB repositories
 *   com.resource      – REST endpoint interfaces
 *   com.security      – JWT token provider + authentication filter
 *   com.service       – Business logic services
 *   com.websocket     – WebSocket handlers (order tracking + store alerts)
 *   com.constant      – Enum constants
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com"})
@EnableMongoRepositories(basePackages = {"com.repository"})
@EnableFeignClients(basePackages = {"com.feign"})
@Slf4j
public class NKTProcessApplication {

    public static void main(String[] args) {
        SpringApplication.run(NKTProcessApplication.class, args);
    }
}
