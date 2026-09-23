package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import io.github.resilience4j.core.functions.CheckedRunnable;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link NotificationDispatchAttemptImp}.
 *
 * <p>We don't spin up a Spring context, so the real {@code @Retry} AOP advice isn't in play here
 * either — to exercise the retry loop without booting Spring, we wrap the call with a
 * programmatic {@link Retry} instance configured to mirror the {@code notificationDispatch}
 * instance in {@code application.properties}. This drives the same Resilience4j engine
 * production uses, just without the annotation-based proxy.
 *
 * <p>The one thing this test class does NOT (and structurally cannot) verify is that the
 * {@code @Retry} annotation on {@link NotificationDispatchAttemptImp#attemptDispatch} is actually
 * wired up by Spring in production — that requires the object to be a real Spring-managed proxy,
 * which is exactly the self-invocation trap this class was extracted to avoid (see
 * {@code NotificationDispatchAttempt} javadoc). That wiring is covered by the Spring context
 * booting successfully with this bean present; no dedicated IT was added for it here.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchAttemptImpTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private NotificationDispatchAttemptImp dispatchAttempt;

    private NotificationContext<?> context;
    private Retry retry;

    @BeforeEach
    void setUp() {
        context = NotificationContext.builder().build();

        final RetryConfig config = RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .waitDuration(Duration.ofMillis(1)) // tests must not sleep for real
                .retryExceptions(NotificationDeliveryException.class)
                .ignoreExceptions(NoStrategyFoundForProcessingTheRequest.class)
                .build();
        retry = Retry.of("notificationDispatch", config);
    }

    @Test
    void transientFailure_retriesUpToMaxAttemptsThenRethrows() throws Throwable {
        final NotificationDeliveryException boom = new NotificationDeliveryException("smtp blip");
        doThrow(boom).when(notificationDispatcher).dispatch(any());

        final CheckedRunnable decorated = Retry.decorateCheckedRunnable(retry, () -> dispatchAttempt.attemptDispatch(context));

        assertThatThrownBy(decorated::run)
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("smtp blip");

        verify(notificationDispatcher, times(MAX_ATTEMPTS)).dispatch(context);
    }

    @Test
    void ignoredException_doesNotRetry() throws Throwable {
        // NoStrategyFoundForProcessingTheRequest is on ignore-exceptions; it should fail FAST
        // (one dispatch call), not consume retry budget.
        final NoStrategyFoundForProcessingTheRequest progErr = new NoStrategyFoundForProcessingTheRequest("no strategy");
        doThrow(progErr).when(notificationDispatcher).dispatch(any());

        final CheckedRunnable decorated = Retry.decorateCheckedRunnable(retry, () -> dispatchAttempt.attemptDispatch(context));

        assertThatThrownBy(decorated::run).isInstanceOf(NoStrategyFoundForProcessingTheRequest.class);
        verify(notificationDispatcher, times(1)).dispatch(context);
    }

    @Test
    void successAfterTransientFailures_doesNotThrow() throws Throwable {
        doThrow(new NotificationDeliveryException("blip 1"))
                .doThrow(new NotificationDeliveryException("blip 2"))
                .doNothing()
                .when(notificationDispatcher).dispatch(any());

        final CheckedRunnable decorated = Retry.decorateCheckedRunnable(retry, () -> dispatchAttempt.attemptDispatch(context));

        assertThatCode(decorated::run).doesNotThrowAnyException();
        verify(notificationDispatcher, times(3)).dispatch(context);
    }
}
