package com.novatech.cybertech.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Activates Spring Retry's {@code @Retryable} proxy support (AOP-based, same mechanism as
 * {@code @Transactional}). Backs {@code OrderManagementServiceImp#cancelOrder}, which retries on
 * an {@code OptimisticLockingFailureException} race against the async payment-confirmation
 * listener rather than hand-rolling a retry loop.
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
