package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.services.core.NotificationDispatchAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationRetryableDeliveryImp}.
 *
 * <p><b>Testing strategy:</b> {@link NotificationDispatchAttempt} is mocked here — its own
 * {@code @Retry} behaviour is exercised separately in {@code NotificationDispatchAttemptImpTest}.
 * This class only pins {@code deliver}'s two outcomes: dispatch succeeds → SENT persisted;
 * dispatch ultimately throws (retries exhausted, or an ignored exception) → PENDING_RETRY
 * persisted. This mirrors the split introduced to fix the self-invocation bug where
 * {@code attemptDispatch} used to live on this same class and its {@code @Retry} annotation was
 * silently never applied (a same-instance {@code this}-call bypasses the Spring AOP proxy).
 */
@ExtendWith(MockitoExtension.class)
class NotificationRetryableDeliveryImpTest {

    /** Same value as {@code cybertech.notification.dispatch.max-attempts} default. */
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private NotificationDispatchAttempt dispatchAttempt;
    @Mock
    private NotificationOutcomeRecorder outcomeRecorder;

    @InjectMocks
    private NotificationRetryableDeliveryImp delivery;

    private NotificationContext<?> context;

    @BeforeEach
    void setUp() {
        // The @Value-injected field is not populated by Mockito; set it explicitly so the catch
        // block can record retryCount = MAX_ATTEMPTS.
        ReflectionTestUtils.setField(delivery, "maxAttempts", MAX_ATTEMPTS);
        context = NotificationContext.builder().build();
    }

    @Test
    void happyPath_callsDispatchAttemptOnceAndPersistsSent() {
        doNothing().when(dispatchAttempt).attemptDispatch(any());
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        delivery.deliver(context);

        verify(dispatchAttempt).attemptDispatch(context);
        verify(outcomeRecorder).recordOutcome(eq(context), eq(NotificationStatus.SENT), eq(0), isNull());
        verify(outcomeRecorder, never()).recordOutcome(any(), eq(NotificationStatus.PENDING_RETRY), any(Integer.class), any());
    }

    @Test
    void attemptDispatchThrows_persistsPendingRetryAndDoesNotPropagate() {
        // Simulates @Retry on NotificationDispatchAttemptImp having exhausted its budget (or
        // short-circuited on an ignore-exception) and let the exception propagate here.
        final NotificationDeliveryException boom = new NotificationDeliveryException("budget exhausted");
        doThrow(boom).when(dispatchAttempt).attemptDispatch(any());
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        assertThatCode(() -> delivery.deliver(context)).doesNotThrowAnyException();

        verify(outcomeRecorder).recordOutcome(eq(context), eq(NotificationStatus.PENDING_RETRY), eq(MAX_ATTEMPTS), eq(boom));
        verify(outcomeRecorder, never()).recordOutcome(any(), eq(NotificationStatus.SENT), any(Integer.class), any());
    }

    @Test
    void redeliver_happyPath_updatesExistingRowAsSentWithUnchangedRetryCount() {
        final NotificationEntity existing = NotificationEntity.builder().retryCount(6).build();
        doNothing().when(dispatchAttempt).attemptDispatch(any());
        when(outcomeRecorder.updateOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(existing);

        delivery.redeliver(context, existing);

        verify(dispatchAttempt).attemptDispatch(context);
        // Success: the row is marked SENT, retryCount left as-is (no new failure to count).
        verify(outcomeRecorder).updateOutcome(eq(existing), eq(NotificationStatus.SENT), eq(6), isNull());
        verify(outcomeRecorder, never()).updateOutcome(any(), eq(NotificationStatus.PENDING_RETRY), any(Integer.class), any());
        // Never inserts a fresh row via recordOutcome — redrive only ever updates in place.
        verify(outcomeRecorder, never()).recordOutcome(any(), any(), any(Integer.class), any());
    }

    @Test
    void redeliver_attemptDispatchThrows_bumpsExistingRowRetryCountAndDoesNotPropagate() {
        final NotificationEntity existing = NotificationEntity.builder().retryCount(6).build();
        final NotificationDeliveryException boom = new NotificationDeliveryException("budget exhausted");
        doThrow(boom).when(dispatchAttempt).attemptDispatch(any());
        when(outcomeRecorder.updateOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(existing);

        assertThatCode(() -> delivery.redeliver(context, existing)).doesNotThrowAnyException();

        // retryCount bumped by this call's in-process budget (6 + 3 = 9) on the SAME row.
        verify(outcomeRecorder).updateOutcome(eq(existing), eq(NotificationStatus.PENDING_RETRY), eq(9), eq(boom));
        verify(outcomeRecorder, never()).updateOutcome(any(), eq(NotificationStatus.SENT), any(Integer.class), any());
        verify(outcomeRecorder, never()).recordOutcome(any(), any(), any(Integer.class), any());
    }
}
