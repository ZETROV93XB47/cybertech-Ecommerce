package com.novatech.cybertech.config;

import com.novatech.cybertech.annotation.PaymentTypeHandler;
import com.novatech.cybertech.annotation.ShippingProviderHandler;
import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.novatech.cybertech.services.core.ShippingProviderService;
import com.novatech.cybertech.validator.implementation.ActiveUserValidator;
import com.novatech.cybertech.validator.implementation.BankCardValidityValidator;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The strategy maps built by {@link AppConfig} must resolve a bean's handler annotation even when
 * the bean is an AOP proxy. Resilience4j ({@code StripePaymentAttemptProcessor}) and the request
 * trace aspect both turn strategy beans into CGLIB subclasses, on which
 * {@code getClass().getAnnotation(..)} returns {@code null} — the strategy would silently vanish
 * from its map and every lookup would fail at runtime.
 */
class AppConfigStrategyMapTest {

    private final AppConfig appConfig = new AppConfig(mock(ActiveUserValidator.class), mock(BankCardValidityValidator.class));
    private final ApplicationContext context = mock(ApplicationContext.class);

    @PaymentTypeHandler({PaymentType.VISA, PaymentType.MASTERCARD})
    static class AnnotatedPaymentProcessor implements PaymentAttemptProcessor {
        @Override
        public PaymentAttemptResult processPayment(final UUID orderUuid, final Money amount, final String idempotencyKey) {
            return null;
        }

        @Override
        public PaymentAttemptResult refund(final UUID orderUuid, final Money amount, final String idempotencyKey, final String stripePaymentID) {
            return null;
        }
    }

    @ShippingProviderHandler(ShippingProvider.DHL)
    static class AnnotatedShippingProvider implements ShippingProviderService {
        @Override
        public BigDecimal calculateShippingCost(final ShippingType shippingType) {
            return BigDecimal.ONE;
        }

        @Override
        public String deliver(final String packageId, final ShippingType shippingType) {
            return packageId;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cglibProxyOf(final T target) {
        final ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());
        return (T) factory.getProxy();
    }

    @Test
    @DisplayName("a CGLIB-proxied payment processor is still registered under its payment types")
    void proxiedPaymentProcessorIsRegistered() {
        final PaymentAttemptProcessor proxied = cglibProxyOf(new AnnotatedPaymentProcessor());
        assertThat(proxied.getClass().getAnnotation(PaymentTypeHandler.class))
                .as("precondition: the raw lookup is blind on a proxy")
                .isNull();
        when(context.getBeansOfType(PaymentAttemptProcessor.class)).thenReturn(Map.of("stripe", proxied));

        final Map<Set<PaymentType>, PaymentAttemptProcessor> map = appConfig.paymentServiceMap(context);

        assertThat(map).containsEntry(Set.of(PaymentType.VISA, PaymentType.MASTERCARD), proxied);
    }

    @Test
    @DisplayName("a CGLIB-proxied shipping provider is still registered under its provider")
    void proxiedShippingProviderIsRegistered() {
        final ShippingProviderService proxied = cglibProxyOf(new AnnotatedShippingProvider());
        when(context.getBeansOfType(ShippingProviderService.class)).thenReturn(Map.of("dhl", proxied));

        final Map<ShippingProvider, ShippingProviderService> map = appConfig.getShippingProviderStrategies(context);

        assertThat(map).containsEntry(ShippingProvider.DHL, proxied);
    }

    @Test
    @DisplayName("a plain (non-proxied) bean keeps working")
    void plainBeanIsRegistered() {
        final AnnotatedShippingProvider plain = new AnnotatedShippingProvider();
        when(context.getBeansOfType(ShippingProviderService.class)).thenReturn(Map.of("dhl", plain));

        assertThat(appConfig.getShippingProviderStrategies(context)).containsEntry(ShippingProvider.DHL, plain);
    }
}
