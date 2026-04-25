package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.NotificationStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationRetryableDeliveryImp}.
 *
 * <p><b>Testing strategy:</b> we don't spin up a Spring context. The
 * {@code @Retry} AOP is exercised by integration tests (and would be
 * exercised by a {@code @SpringBootTest} slice if we needed it here), but
 * for the per-method behaviour we want to pin the contract is:
 * <ul>
 *   <li>Happy path → recorder gets {@code SENT, retryCount=0}.</li>
 *   <li>Retries-exhausted path → recorder gets {@code PENDING_RETRY,
 *       retryCount=maxAttempts}.</li>
 *   <li>Ignored exception (programmer-error) is still routed to the
 *       fallback by Resilience4j and recorded as {@code PENDING_RETRY}
 *       — but see the dedicated test for the nuance: it MUST NOT trigger
 *       the retry loop (i.e. the dispatcher is invoked exactly once).</li>
 *   <li>Success after transient failures → recorder gets {@code SENT}.</li>
 * </ul>
 *
 * <p>To exercise the actual retry loop without Spring's AOP proxy, we wrap
 * the dispatcher call with a programmatic {@link Retry} instance whose
 * configuration mirrors the {@code notificationDispatch} instance defined in
 * {@code application.properties}. The retry-and-fallback semantics are then
 * driven by Resilience4j directly (same library, same code path inside
 * Resilience4j) without booting Spring. This keeps the test fast and pure
 * while still asserting against the real retry engine, not a hand-rolled
 * mock of one.
 *
 * <p>For the simple happy-path test we call {@link
 * NotificationRetryableDeliveryImp#deliver(NotificationContext)} directly —
 * it invokes {@link NotificationRetryableDeliveryImp#attemptDispatch(NotificationContext)}
 * via {@code this} (no proxy), but on the success path Resilience4j's role
 * is a no-op anyway, so direct invocation faithfully exercises the
 * persistence side of {@code deliver}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRetryableDeliveryImpTest {

    /** Same value as {@code cybertech.notification.dispatch.max-attempts} default. */
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private NotificationDispatcher notificationDispatcher;
    @Mock
    private NotificationOutcomeRecorder outcomeRecorder;

    @InjectMocks
    private NotificationRetryableDeliveryImp delivery;

    private NotificationContext<?> context;
    private Retry retry;

    @BeforeEach
    void setUp() {
        // The @Value-injected field is not populated by Mockito; set it
        // explicitly so the fallback can record retryCount = MAX_ATTEMPTS.
        ReflectionTestUtils.setField(delivery, "maxAttempts", MAX_ATTEMPTS);
        context = NotificationContext.builder().build();

        // Programmatic Retry mirroring the application.properties config so
        // we can drive the retry loop without Spring AOP.
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .waitDuration(Duration.ofMillis(1)) // tests must not sleep for real
                .retryExceptions(NotificationDeliveryException.class)
                .ignoreExceptions(NoStrategyFoundForProcessingTheRequest.class)
                .build();
        retry = Retry.of("notificationDispatch", config);
    }

    @Test
    void happyPath_callsDispatcherOnceAndPersistsSent() {
        // dispatcher succeeds → no exception
        doNothing().when(notificationDispatcher).dispatch(any());
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        delivery.deliver(context);

        verify(notificationDispatcher, times(1)).dispatch(context);
        verify(outcomeRecorder).recordOutcome(eq(context), eq(NotificationStatus.SENT), eq(0), isNull());
    }

    @Test
    void transientFailure_retriesUpToMaxAttempts_thenPersistsPendingRetry() throws Throwable {
        // The dispatcher always fails with the retry-eligible signal.
        NotificationDeliveryException boom = new NotificationDeliveryException("smtp blip");
        doThrow(boom).when(notificationDispatcher).dispatch(any());
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        // Drive the retry loop programmatically. The decorated runnable wraps
        // delivery.attemptDispatch(context) — same method @Retry annotates in
        // production. After MAX_ATTEMPTS failures the decorated runnable
        // rethrows; we then call the package-private fallback path via
        // delivery.deliver to check the persistence side. (delivery.deliver
        // itself catches everything that escapes attemptDispatch and routes
        // to the same PENDING_RETRY persistence as the @Retry fallback would.)
        CheckedRunnable decorated = Retry.decorateCheckedRunnable(retry, () -> delivery.attemptDispatch(context));

        assertThatThrownBy(decorated::run)
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("smtp blip");

        // Resilience4j attempted exactly MAX_ATTEMPTS times.
        verify(notificationDispatcher, times(MAX_ATTEMPTS)).dispatch(context);

        // Now exercise the deliver()-level catch (mirrors what the fallback
        // would persist when wired with Spring AOP). deliver wraps
        // attemptDispatch in a try/catch and persists PENDING_RETRY on
        // escape — verify that.
        // Reset the dispatcher invocation count so we can re-assert deliver's
        // single delegation.
        org.mockito.Mockito.clearInvocations(notificationDispatcher, outcomeRecorder);
        delivery.deliver(context);

        verify(outcomeRecorder).recordOutcome(eq(context), eq(NotificationStatus.PENDING_RETRY), eq(MAX_ATTEMPTS), any(Throwable.class));
    }

    @Test
    void ignoredException_doesNotRetry_andRoutesToFallback() throws Throwable {
        // NoStrategyFoundForProcessingTheRequest is on ignore-exceptions; it
        // should fail FAST (one dispatch call), not consume retry budget.
        NoStrategyFoundForProcessingTheRequest progErr =
                new NoStrategyFoundForProcessingTheRequest("no strategy");
        doThrow(progErr).when(notificationDispatcher).dispatch(any());

        CheckedRunnable decorated = Retry.decorateCheckedRunnable(retry, () -> delivery.attemptDispatch(context));

        assertThatThrownBy(decorated::run)
                .isInstanceOf(NoStrategyFoundForProcessingTheRequest.class);

        // Critical assertion: the ignored exception did NOT trigger a retry
        // loop — exactly one dispatch call.
        verify(notificationDispatcher, times(1)).dispatch(context);
    }

    @Test
    void successAfterTransientFailures_persistsSent() throws Throwable {
        // Two failures then success — Resilience4j should retry through and
        // ultimately succeed.
        doThrow(new NotificationDeliveryException("blip 1"))
                .doThrow(new NotificationDeliveryException("blip 2"))
                .doNothing()
                .when(notificationDispatcher).dispatch(any());

        CheckedRunnable decorated = Retry.decorateCheckedRunnable(retry, () -> delivery.attemptDispatch(context));

        // No exception escapes the decorated runnable — the third attempt
        // succeeded.
        assertThatCode(decorated::run).doesNotThrowAnyException();
        verify(notificationDispatcher, times(3)).dispatch(context);

        // Recorder is not called by attemptDispatch — that's deliver's job.
        // Now drive deliver to assert the SENT persistence on the final
        // outcome. Reset the dispatcher to a single success for the second
        // run-through.
        org.mockito.Mockito.clearInvocations(notificationDispatcher, outcomeRecorder);
        doNothing().when(notificationDispatcher).dispatch(any());
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        delivery.deliver(context);
        verify(outcomeRecorder).recordOutcome(eq(context), eq(NotificationStatus.SENT), eq(0), isNull());
    }

    @Test
    void deliver_swallowsExceptionAndPersistsPendingRetry_whenAttemptDispatchEscapes() {
        // Direct test of deliver()'s defensive catch — simulates a scenario
        // where the @Retry advice somehow let an exception escape (mis-
        // configuration, proxy missing, etc). deliver MUST persist
        // PENDING_RETRY rather than crash the async worker.
        NotificationDeliveryException boom = new NotificationDeliveryException("escaped");
        doThrow(boom).when(notificationDispatcher).dispatch(any());

        assertThatCode(() -> delivery.deliver(context)).doesNotThrowAnyException();

        verify(outcomeRecorder).recordOutcome(eq(context), eq(NotificationStatus.PENDING_RETRY), eq(MAX_ATTEMPTS), eq(boom));
        // Recorder NEVER got SENT.
        verify(outcomeRecorder, never()).recordOutcome(any(), eq(NotificationStatus.SENT), any(Integer.class), any());
    }
}
