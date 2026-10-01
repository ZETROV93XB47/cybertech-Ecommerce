package com.novatech.cybertech.logger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Logs two things useful when eyeballing a fresh boot: every resolved config property (with
 * sensitive ones masked) as soon as the {@link Environment} is available, and the Swagger /
 * OpenAPI URLs once the app is actually ready to serve them.
 *
 * <p>Not a {@code @Component}: {@link ApplicationEnvironmentPreparedEvent} fires before the
 * {@code ApplicationContext} refreshes, i.e. before component scanning has run, so a bean-based
 * listener would simply never see it. Registered explicitly via
 * {@code SpringApplication#addListeners} in {@code CyberTechApplication#main} instead — the same
 * instance then also receives the later {@link ApplicationReadyEvent}.
 *
 * <p>Replaces the previous {@code CybertechPropertiesLogger}, which had the same intent but was
 * never actually wired up (no {@code @Component}, no listener registration anywhere) and whose
 * sensitive-property filter only matched {@code password}/{@code secret}/{@code pwd}/
 * {@code credential} in the property name — missing {@code stripe.api.key},
 * {@code spring.cloud.vault.token} and {@code app.security.card-encryption-key}, all of which
 * would have been printed in clear.
 */
@Slf4j
public class CybertechStartupLogger implements ApplicationListener<ApplicationEvent> {

    private static final String MASKED_VALUE = "******";

    /**
     * Matches a property name carrying a secret-shaped segment, case-insensitively, on a
     * {@code . _ -} delimited word boundary — so {@code stripe.api.key} and
     * {@code app.security.card-encryption-key} match, but {@code keycloak.client.*} does NOT
     * (the "key" inside "key<b>cloak</b>" has no boundary after it, so {@code \bkey\b} never
     * fires there). Broader than the historical filter (also catches {@code key}/{@code token}/
     * {@code private}), on the theory that over-masking a harmless property beats leaking a
     * real one.
     */
    private static final Pattern SENSITIVE_PROPERTY_NAME = Pattern.compile(
            "(?i).*\\b(password|secret|pwd|credential|token|key|private)\\b.*");

    private static final String[] DEFAULT_SWAGGER_UI_PATHS = {"springdoc.swagger-ui.path", "/swagger-ui.html"};
    private static final String[] DEFAULT_API_DOCS_PATHS = {"springdoc.api-docs.path", "/v3/api-docs"};

    @Override
    public void onApplicationEvent(final ApplicationEvent event) {
        if (event instanceof ApplicationEnvironmentPreparedEvent environmentPreparedEvent) {
            logProperties(environmentPreparedEvent.getEnvironment());
        } else if (event instanceof ApplicationReadyEvent readyEvent) {
            logSwaggerUrls(readyEvent.getApplicationContext().getEnvironment());
        }
    }

    private void logProperties(final Environment environment) {
        log.info("=========================== Environment And Configuration ===========================");
        log.info("Active profiles: {}", Arrays.toString(environment.getActiveProfiles()));

        final MutablePropertySources propertySources = ((AbstractEnvironment) environment).getPropertySources();

        propertySources.stream()
                .filter(EnumerablePropertySource.class::isInstance)
                .map(propertySource -> ((EnumerablePropertySource<?>) propertySource).getPropertyNames())
                .flatMap(Arrays::stream)
                .distinct()
                .sorted()
                .forEach(property -> log.info("{} = {}", property, safeValue(property, environment)));

        log.info("========================================================================================");
    }

    private String safeValue(final String property, final Environment environment) {
        if (SENSITIVE_PROPERTY_NAME.matcher(property).matches()) {
            return MASKED_VALUE;
        }
        return environment.getProperty(property);
    }

    private void logSwaggerUrls(final Environment environment) {
        if (!environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true)) {
            log.info("springdoc.api-docs.enabled=false — Swagger / OpenAPI endpoints are disabled.");
            return;
        }

        final String port = environment.getProperty("server.port", "8080");
        final String contextPath = environment.getProperty("server.servlet.context-path", "");
        final String baseUrl = "http://localhost:" + port + contextPath;

        final String swaggerUiPath = environment.getProperty(DEFAULT_SWAGGER_UI_PATHS[0], DEFAULT_SWAGGER_UI_PATHS[1]);
        final String apiDocsPath = environment.getProperty(DEFAULT_API_DOCS_PATHS[0], DEFAULT_API_DOCS_PATHS[1]);

        log.info("=========================== Swagger / OpenAPI ===========================");
        log.info("Swagger UI     : {}{}", baseUrl, swaggerUiPath);
        log.info("OpenAPI (JSON) : {}{}", baseUrl, apiDocsPath);
        log.info("OpenAPI (YAML) : {}{}.yaml", baseUrl, apiDocsPath);
        log.info("===========================================================================");
    }
}
