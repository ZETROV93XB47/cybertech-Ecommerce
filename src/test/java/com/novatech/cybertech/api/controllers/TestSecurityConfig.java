package com.novatech.cybertech.api.controllers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/api/v1/services/review/get/**"
           // "/api/v1/services/review/create"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) {
        try {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(PUBLIC_URLS).permitAll()
                            .anyRequest().authenticated()
                    )
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

            return http.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build SecurityFilterChain for tests", e);
        }
    }
}
