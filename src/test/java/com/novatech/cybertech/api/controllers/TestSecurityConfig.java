package com.novatech.cybertech.api.controllers;

import com.novatech.cybertech.api.error.CustomAuthenticationEntryPoint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(proxyTargetClass = true)
public class TestSecurityConfig {

    /**
     * Mirrors {@code com.novatech.cybertech.config.SecurityConfig#PUBLIC_URLS} so slice tests
     * can exercise anonymous access on the same surface as production. Keep in sync with prod.
     */
    private static final String[] PUBLIC_URLS = {
            "/h2-console/**",
            "/api/public/**",
            "/auth/register",
            "/auth/login",
            "/products/list",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // BUG-201: narrowed to the exact human-signup path; mirrors the production
            // SecurityConfig#PUBLIC_URLS change so slice tests exercise the same surface.
            "/api/v1/services/user/register",
            "/api/v1/services/user/get/all",
            "/test/upload-image/**",
            "/api/v1/services/product/**",
            "/api/v1/services/review/get/**",
            "/api/v1/webhooks/**",
            "/actuator/health/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) {
        try {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(PUBLIC_URLS).permitAll()
                            .anyRequest().authenticated()
                    )
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    // Wires the production entry point so anonymous calls return 401 (not 403).
                    // CustomAuthenticationEntryPoint has no constructor deps, safe to instantiate here.
                    .exceptionHandling(eh -> eh.authenticationEntryPoint(new CustomAuthenticationEntryPoint()));

            return http.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build SecurityFilterChain for tests", e);
        }
    }
}
