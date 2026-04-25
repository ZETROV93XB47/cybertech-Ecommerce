package com.novatech.cybertech.config;

import com.novatech.cybertech.annotation.*;
import com.novatech.cybertech.entities.enums.*;
import com.novatech.cybertech.listener.RedisExpirationListener;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockRepository;
import com.novatech.cybertech.services.core.AbstractNotification;
import com.novatech.cybertech.services.core.NotificationProcessor;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.novatech.cybertech.services.core.ShippingProviderService;
import com.novatech.cybertech.strategy.discount.DiscountStrategy;
import com.novatech.cybertech.validator.core.OrderValidator;
import com.novatech.cybertech.validator.implementation.ActiveUserValidator;
import com.novatech.cybertech.validator.implementation.BankCardValidityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.Executor;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;

@Slf4j
@Configuration
@EnableCaching
@EnableJpaAuditing
@RequiredArgsConstructor
public class AppConfig {

    @Value("${keycloak.client.user.management.server.url}")
    private String serverUrl;
    @Value("${keycloak.client.user.management.realm}")
    private String realm;
    @Value("${keycloak.client.user.management.client.id}")
    private String clientId;
    @Value("${keycloak.client.user.management.client.secret}")
    private String clientSecret;

    private final ActiveUserValidator activeUserValidator;
    private final BankCardValidityValidator bankCardValidityValidator;

    @Bean
    public OrderValidator orderValidatorChain() {
        activeUserValidator
                .setNext(bankCardValidityValidator);

        return activeUserValidator;
    }

    @Bean
    public Map<Set<PaymentType>, PaymentAttemptProcessor> paymentServiceMap(final ApplicationContext context) {
        final Map<Set<PaymentType>, PaymentAttemptProcessor> serviceMap = new HashMap<Set<PaymentType>, PaymentAttemptProcessor>();

        final Map<String, PaymentAttemptProcessor> beans = context.getBeansOfType(PaymentAttemptProcessor.class);

        for (PaymentAttemptProcessor service : beans.values()) {
            final PaymentTypeHandler annotation = service.getClass().getAnnotation(PaymentTypeHandler.class);
            if (annotation != null) {
                serviceMap.put((new HashSet<>(Arrays.asList(annotation.value()))), service);
            }
        }

        log.info("payment service map : {}", serviceMap);

        return serviceMap;
    }


    @Bean
    public Map<DiscountCalculationType, DiscountStrategy> discountStrategyMap(ApplicationContext context) {
        Map<DiscountCalculationType, DiscountStrategy> map = new EnumMap<>(DiscountCalculationType.class);
        context.getBeansOfType(DiscountStrategy.class).forEach((name, bean) -> {
            DiscountTypeHandler annotation = bean.getClass().getAnnotation(DiscountTypeHandler.class);
            if (annotation != null) {
                for (DiscountCalculationType calcType : annotation.value()) {
                    map.put(calcType, bean);
                }
            }
        });
        return map;
    }

    @Bean
    public Map<NotificationType, AbstractNotification> getNotificationStrategies(final ApplicationContext context) {
        Map<NotificationType, AbstractNotification> map = new EnumMap<>(NotificationType.class);
        context.getBeansOfType(AbstractNotification.class).forEach((name, bean) -> {
            NotificationTypeHandler annotation = bean.getClass().getAnnotation(NotificationTypeHandler.class);
            if (annotation != null) {
                map.put(annotation.value(), bean);
            }
        });
        return map;
    }

    @Bean
    public Map<CommunicationChanel, NotificationProcessor> getNotificationProcessorStrategies(final ApplicationContext context) {
        Map<CommunicationChanel, NotificationProcessor> map = new EnumMap<>(CommunicationChanel.class);
        context.getBeansOfType(NotificationProcessor.class).forEach((name, bean) -> {
            CommunicationTypeHandler annotation = bean.getClass().getAnnotation(CommunicationTypeHandler.class);
            if (annotation != null) {
                map.put(annotation.value(), bean);
            }
        });
        return map;
    }


    @Bean
    public Map<ShippingProvider, ShippingProviderService> getShippingProviderStrategies(final ApplicationContext applicationContext) {
        Map<ShippingProvider, ShippingProviderService> map = new EnumMap<>(ShippingProvider.class);
        applicationContext.getBeansOfType(ShippingProviderService.class).forEach((name, bean) -> {
            ShippingProviderHandler annotation = bean.getClass().getAnnotation(ShippingProviderHandler.class);
            if (annotation != null) {
                map.put(annotation.value(), bean);
            }
        });
        return map;
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory, StockRepository reservationRepository, ProductRepository productRepository) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Listen to EXPIRED events
        container.addMessageListener(new RedisExpirationListener(container, reservationRepository, productRepository), new PatternTopic("__keyevent@*__:expired"));

        return container;
    }

    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }


    @Bean(name = APPLICATION_ASYNC_TASK_EXECUTOR)
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    static class MdcTaskDecorator implements TaskDecorator {

        @Override
        public @NonNull Runnable decorate(@NonNull Runnable runnable) {
            final Map<String, String> contextMap = MDC.getCopyOfContextMap();

            return () -> {

                final Map<String, String> previous = MDC.getCopyOfContextMap();

                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    else {
                        MDC.clear();
                    }
                    runnable.run();
                }
                finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    }
                    else {
                        MDC.clear();
                    }
                }
            };
        }
    }
}


