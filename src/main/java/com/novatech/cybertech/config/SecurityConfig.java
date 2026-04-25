package com.novatech.cybertech.config;


import com.novatech.cybertech.api.error.CustomAccessDeniedHandler;
import com.novatech.cybertech.api.error.CustomAuthenticationEntryPoint;
import com.novatech.cybertech.converter.KeycloakRoleConverter;
import com.novatech.cybertech.security.RateLimitFilter;
import com.novatech.cybertech.security.StripeWebhookIpAllowlistFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(proxyTargetClass = true)
public class SecurityConfig {

    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final RateLimitFilter rateLimitFilter;
    private final StripeWebhookIpAllowlistFilter stripeWebhookIpAllowlistFilter;

    /**
     * BUG-PRE-3: actuator probes ({@code /actuator/health}, {@code /actuator/health/liveness},
     * {@code /actuator/health/readiness}, {@code /actuator/info}) MUST be reachable anonymously
     * so kubelet liveness/readiness probes (and Helm chart wait jobs) succeed without
     * provisioning a service-account token. Every OTHER {@code /actuator/**} endpoint is locked
     * behind ROLE_ADMIN by the explicit rule in {@link #securityFilterChain(HttpSecurity)} —
     * the per-path permitAll must come BEFORE the ROLE_ADMIN catch-all to take effect.
     */
    private static final String[] PUBLIC_URLS = {
            "/h2-console/**",       // Accès à la console H2 (Très important en développement !)
            "/api/public/**",       // Exemple: Tous les endpoints sous /api/public/
            "/auth/register",       // Exemple: Endpoint d'enregistrement
            "/auth/login",          // Exemple: Endpoint de connexion (si géré sans sécurité initiale)
            "/products/list",       // Exemple: Liste publique des produits
            "/swagger-ui/**",       // Accès à Swagger UI (si utilisé)
            "/v3/api-docs/**",      // Accès à la définition OpenAPI (si utilisé)
            // BUG-201: narrowed from "/api/v1/services/user/register/**" to the exact
            // human-signup path so /register/auto/** is NO LONGER anonymously reachable.
            // The /auto endpoints are ADMIN-only via @PreAuthorize on the controllers.
            "/api/v1/services/user/register",
            // BUG-IDOR-D3: removed "/api/v1/services/user/get/all" — user listing must be
            // ADMIN-only via @PreAuthorize on the management controller, never anonymous.
            "/test/upload-image/**",
            "/api/v1/services/product/**",
            "/api/v1/services/discounts/**",
            "/api/v1/webhooks/**",
            // BUG-PRE-3: kubelet probes + Spring Boot health groups (liveness/readiness).
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    /**
     * CSP report-only directives. Started in REPORT-ONLY so legitimate frontend traffic
     * (Next.js inline styles, data: image URLs, etc.) is not blocked while we observe
     * what the live stack actually emits. Promote to enforcing once the report endpoint
     * shows zero violations for a sustained window.
     */
    private static final String CSP_DIRECTIVES =
            "default-src 'self'; "
                    + "img-src 'self' data: https:; "
                    + "script-src 'self' 'unsafe-inline'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "frame-ancestors 'none'";

    @Value("${cybertech.security.hsts.enabled:false}")
    private boolean hstsEnabled;

    @Value("${cybertech.cors.allowed-origins:http://localhost:3000}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {

        http
                // CSRF: Cybertech is JWT-only (Keycloak resource server) with
                // SessionCreationPolicy.STATELESS, so CSRF tokens add no protection — disable.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        // Lock down every other actuator endpoint to admins. MUST come
                        // BEFORE anyRequest().authenticated() and AFTER the permitAll on
                        // /actuator/health, /actuator/health/**, /actuator/info above.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint) // 401
                        .accessDeniedHandler(accessDeniedHandler)           // 403
                )
                .headers(headers -> {
                    // Always-on hardening headers.
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.frameOptions(frame -> frame.disable())
                            .addHeaderWriter(new XFrameOptionsHeaderWriter(
                                    XFrameOptionsHeaderWriter.XFrameOptionsMode.DENY));
                    headers.referrerPolicy(rp -> rp.policy(
                            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));

                    // CSP in REPORT-ONLY mode (Spring Security DSL exposes .reportOnly()).
                    headers.contentSecurityPolicy(csp -> csp
                            .policyDirectives(CSP_DIRECTIVES)
                            .reportOnly());

                    // Permissions-Policy: deny powerful APIs by default.
                    headers.addHeaderWriter(new StaticHeadersWriter(
                            "Permissions-Policy",
                            "geolocation=(), microphone=(), camera=()"));

                    // HSTS — only when enabled (prod / behind TLS). Default disabled in dev/test
                    // because the local stack runs on plain HTTP and a cached HSTS pin would
                    // make subsequent http://localhost calls fail in browsers.
                    if (hstsEnabled) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(63072000L));
                    } else {
                        headers.httpStrictTransportSecurity(hsts -> hsts.disable());
                    }
                })
                .addFilterBefore(stripeWebhookIpAllowlistFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    /**
     * Reads {@code cybertech.cors.allowed-origins} (comma-separated) and exposes the standard
     * REST-API CORS profile. {@code Location} and {@code X-API-VERSION} are exposed because
     * the frontend reads them after a 201 Create / when negotiating the API version header.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();

        final List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Location", "X-API-VERSION"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
