package com.first.launcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * Bootstrap class for the No-Code API Platform (Project 1).
 *
 * Component scan covers:
 *   com.first          – all platform components, services, controllers
 *   com.first.client   – Feign clients (WorkFlowEngineClient)
 *   com.first.exception – GlobalExceptionHandler
 *
 * EnableFeignClients scans the same packages so Feign proxies are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.first",
        "com.first.client",
        "com.first.exception"
})
@EnableFeignClients(basePackages = {"com.first.client"})
public class FirstprojectApplication {

    public static void main(String[] args) {
        SpringApplication.run(FirstprojectApplication.class, args);
    }
}
