package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.NotificationContext;

/**
 * The retry-eligible unit of work behind {@link NotificationRetryableDelivery}, split into its
 * own bean specifically so Resilience4j's {@code @Retry} AOP advice can actually intercept it.
 *
 * <p>The previous design put both the orchestrating {@code deliver()} method and the
 * {@code @Retry}-annotated {@code attemptDispatch()} method on the SAME bean
 * ({@code NotificationRetryableDeliveryImp}), and {@code deliver()} called {@code attemptDispatch()}
 * as a plain {@code this}-call. Spring AOP proxies only intercept calls that arrive through the
 * bean reference from OUTSIDE the bean — a same-instance call bypasses the proxy entirely, so
 * {@code @Retry} silently never engaged: every transient dispatch failure got exactly one attempt
 * instead of the configured 3-with-backoff. Splitting the retry-eligible call into its own bean
 * (this interface) means {@code NotificationRetryableDeliveryImp} now calls it through real
 * inter-bean dependency injection, which does go through the proxy.
 */
public interface NotificationDispatchAttempt {

    void attemptDispatch(NotificationContext<?> context);
}
