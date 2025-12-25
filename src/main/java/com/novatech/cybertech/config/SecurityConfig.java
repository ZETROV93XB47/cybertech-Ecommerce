package com.novatech.cybertech.config;


import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.security.KeycloakRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Définit la liste des URL à autoriser sans sécurité
    private static final String[] PUBLIC_URLS = {
            "/auth/**",
            "/h2-console/**",       // Accès à la console H2 (Très important en développement !)
            "/api/public/**",       // Exemple: Tous les endpoints sous /api/public/
            "/auth/register",       // Exemple: Endpoint d'enregistrement
            "/auth/login",          // Exemple: Endpoint de connexion (si géré sans sécurité initiale)
            "/products/list",       // Exemple: Liste publique des produits
            "/swagger-ui/**",       // Accès à Swagger UI (si utilisé)
            "/v3/api-docs/**",      // Accès à la définition OpenAPI (si utilisé)
            "/api/v1/services/**",
            "/public/**",
            "/users/register",
            "/api/v1/register",
            "/api/v1/register/**",
            "/api/v1/services/product/generate"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http, final UserRepository userRepository) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())));
        return http.build();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }
}