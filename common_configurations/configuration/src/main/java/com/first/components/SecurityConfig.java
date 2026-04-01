package com.first.components;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    /** Public endpoints – no JWT required */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/send-otp",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/refresh",
            "/api/v1/auth/biometric/verify",
            "/api/v1/categories",
            "/api/v1/stores",
            "/api/v1/stores/**",
            "/api/v1/payments/webhook",
            "/ws/**",
            "/swagger-ui/**", 
            "/swagger-ui.html", 
            "/v3/api-docs/**", 
            "/v3/api-docs.yaml"

    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
       
    	http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers("/api/v1/store/**", "/api/v1/store/orders/**").hasRole("STORE")
                .requestMatchers("/api/v1/customer/**", "/api/v1/orders/**",
                                 "/api/v1/cart/**", "/api/v1/payments/**",
                                 "/api/v1/discover/**", "/api/v1/search",
                                 "/api/v1/location/**").hasAnyRole("CUSTOMER", "STORE")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
