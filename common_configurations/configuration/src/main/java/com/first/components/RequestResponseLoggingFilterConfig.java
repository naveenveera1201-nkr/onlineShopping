package com.first.components;


import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RequestResponseLoggingFilterConfig {

    @Bean
    public FilterRegistrationBean<RequestCachingFilter> registerLoggingFilter(RequestCachingFilter filter) {

        FilterRegistrationBean<RequestCachingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);

        // VERY IMPORTANT: highest precedence
        registration.setOrder(Integer.MIN_VALUE);

        registration.addUrlPatterns("/*");

        return registration;
    }
}
