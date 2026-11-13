package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.services.core.NotificationDispatchAttempt;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * See {@link NotificationDispatchAttempt} for why this lives in its own bean.
 *
 * <p>No {@code fallbackMethod} here: once {@code @Retry} exhausts its attempts, the exception
 * propagates to {@code NotificationRetryableDeliveryImp#deliver}, whose existing {@code catch
 * (Throwable t)} persists the {@code PENDING_RETRY} audit row. Keeping that persistence in one
 * place (rather than duplicating it in a fallback method here) means there's only one code path
 * to reason about for "what happens when dispatch ultimately fails."
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatchAttemptImp implements NotificationDispatchAttempt {

    /**
     * Resilience4j retry instance name. Must match the key under
     * {@code resilience4j.retry.instances.<name>.*} in {@code application.properties}. Pulled
     * out as a constant so a typo here surfaces at compile time rather than as a silent
     * "no retry policy applied" at runtime.
     */
    public static final String RETRY_INSTANCE_NAME = "notificationDispatch";

    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Retry(name = RETRY_INSTANCE_NAME)
    public void attemptDispatch(final NotificationContext<?> context) {
        notificationDispatcher.dispatch(context);
    }
}
