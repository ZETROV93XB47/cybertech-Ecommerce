package com.novatech.cybertech.config;

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
 * Top-level OpenAPI / Swagger configuration.
 *
 * <p>Defines:
 * <ul>
 *   <li>Global API metadata (title, version, description, contact).</li>
 *   <li>The {@code keycloak} bearer/JWT security scheme used by every protected endpoint.</li>
 *   <li>A default global security requirement so springdoc emits a {@code security: [{keycloak: []}]}
 *       on every operation that does not declare its own. Public endpoints (Stripe webhook,
 *       product browsing, registration) override this with {@code security = {}} on their spec.</li>
 * </ul>
 *
 * <p>Once the application is running, the contract is served at:
 * <ul>
 *   <li>{@code /v3/api-docs} (JSON)</li>
 *   <li>{@code /v3/api-docs.yaml} (YAML)</li>
 *   <li>{@code /swagger-ui/index.html} (interactive UI)</li>
 * </ul>
 *
 * <p>The static, committed copy lives under {@code docs/openapi/openapi.yaml}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cybertechOpenAPI(@Value("${spring.application.name:cybertech}") final String appName,
                                    @Value("${server.port:8081}") final String serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("Cybertech E-Commerce API")
                        .version("1.0")
                        .description("""
                                Backend REST API for the Cybertech e-commerce platform.

                                Covers the full e-commerce lifecycle: product catalog, search,
                                cart, wishlist, orders, payments (Stripe), reviews and moderation,
                                bank cards (PCI-aware), discount campaigns, user management
                                (Keycloak-backed) and behavioural event ingestion.

                                Authentication is performed via Keycloak-issued JWT bearer tokens.
                                Public endpoints (catalog browsing, registration, Stripe webhook)
                                are explicitly whitelisted; every other endpoint requires a
                                valid Bearer token and may further restrict to USER or ADMIN roles.
                                """)
                        .contact(new Contact()
                                .name("Cybertech Team")
                                .email("dev@cybertech.local"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://cybertech.local/license")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local dev"),
                        new Server().url("https://api.cybertech.local").description("Production")
                ))
                .components(new Components()
                        .addSecuritySchemes("keycloak",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Keycloak-issued JWT access token. " +
                                                "Obtain one from the realm token endpoint and pass " +
                                                "it as the Authorization header: 'Bearer <token>'.")))
                .addSecurityItem(new SecurityRequirement().addList("keycloak"));
    }
}
