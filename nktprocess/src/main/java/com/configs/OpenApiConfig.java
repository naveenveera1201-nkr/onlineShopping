package com.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI 3 configuration for NammaKadaiTheru API.
 *
 * Swagger UI : http://localhost:8070/swagger-ui/index.html
 * OpenAPI JSON: http://localhost:8070/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "BearerAuth";

    @Value("${server.port:8070}")
    private String serverPort;

    @Bean
    public OpenAPI nktOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, jwtSecurityScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("NammaKadaiTheru (NKT) API")
                .description("""
                        **NammaKadaiTheru** is a no-code platform for local grocery & store management.

                        ### Authentication
                        All secured endpoints require a **JWT Bearer token** in the `Authorization` header:
                        ```
                        Authorization: Bearer <access_token>
                        ```
                        Obtain a token via **POST /api/v1/auth/verify-otp** or **POST /api/v1/auth/biometric/verify**.

                        ### Roles
                        | Role     | Description                        |
                        |----------|------------------------------------|
                        | PUBLIC   | No token required                  |
                        | CUSTOMER | Customer JWT required              |
                        | STORE    | Store-owner JWT required           |

                        ### WebSocket Endpoints
                        - `ws://localhost:8070/ws/orders/{orderId}/track?token=<jwt>` — Order tracking (API 52)
                        - `ws://localhost:8070/ws/store/orders?token=<jwt>` — Store order alerts (API 53)
                        """)
                .version("v1.0.0")
                .contact(new Contact()
                        .name("NKT Dev Team")
                        .email("dev@nammakadaitheru.in")
                        .url("https://nammakadaitheru.in"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://nammakadaitheru.in/terms"));
    }

    private List<Server> serverList() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local development server"),
                new Server()
                        .url("https://api.nammakadaitheru.in")
                        .description("Production server")
        );
    }

    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .name(BEARER_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(
                        "Enter the JWT token obtained from /api/v1/auth/verify-otp. " +
                        "Example: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`"
                );
    }
}
