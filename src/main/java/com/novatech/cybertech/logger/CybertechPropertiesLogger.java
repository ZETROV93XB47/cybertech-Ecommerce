package com.novatech.cybertech.logger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import java.util.Arrays;
import java.util.function.Predicate;

@Slf4j
public class CybertechPropertiesLogger implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    //To avoid logging passwords and credentials
    private static final Predicate<String> MATCH_ONLY_NON_SENSIBLE_PROPERTIES = envProperty -> !(
            envProperty.contains("password") ||
                    envProperty.contains("secret") ||
                    envProperty.contains("pwd") ||
                    envProperty.contains("PWD") ||
                    envProperty.contains("SECRET") ||
                    envProperty.contains("PASSWORD") ||
                    envProperty.contains("credential")
    );

    @Override
    public void onApplicationEvent(final ApplicationEnvironmentPreparedEvent event) {

        final Environment environment = event.getEnvironment();

        log.info("=========================== Environment And Configuration ===========================");
        log.info("Active profiles: {} \n", Arrays.toString(environment.getActiveProfiles()));

        final MutablePropertySources propertySourceStream = ((AbstractEnvironment) environment).getPropertySources();

        log.info("=========================== Properties List ===========================");

        propertySourceStream.stream()
                .filter(EnumerablePropertySource.class::isInstance)
                .map(propertySource -> ((EnumerablePropertySource<?>) propertySource).getPropertyNames())
                .flatMap(Arrays::stream)
                .distinct()
                .filter(MATCH_ONLY_NON_SENSIBLE_PROPERTIES)
                .forEach(property -> log.info("{}: {}", property, environment.getProperty(property)));

        log.info("======================================================================================");
    }
}