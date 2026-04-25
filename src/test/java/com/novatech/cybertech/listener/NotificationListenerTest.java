package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.services.implementation.NotificationOutcomeRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

import static com.novatech.cybertech.listener.NotificationListener.MAX_DISPATCH_RETRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationListener}.
 *
 * <p>Phase 1: persistence is delegated to
 * {@link NotificationOutcomeRecorder}, so these tests assert the recorder
 * interaction rather than the {@code NotificationRepository.save(...)} that
 * used to live inline. The hand-rolled retry loop is intentionally still in
 * place (to be removed in Phase 2).
 */
@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationOutcomeRecorder outcomeRecorder;
    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private NotificationListener listener;

    private OrderEventDto sampleEventDto() {
        return OrderEventDto.builder()
                .orderUuid(UUID.randomUUID())
                .totalAmount(new BigDecimal("100.00"))
                .orderStatus(OrderStatus.SHIPPED)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .userContactDto(UserContactDto.builder()
                        .name("Jane").email("jane@example.com").phoneNumber("+33600000000")
                        .defaultCommunicationChanel(CommunicationChanel.EMAIL).build())
                .paymentAttemptStatus(PaymentAttemptStatus.SUCCESS)
                .build();
    }

    @Test
    void onMethodIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method m = NotificationListener.class.getMethod("on", OrderShippedEvent.class);
        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).as("@TransactionalEventListener present").isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void onShouldDispatchAndRecordSentOutcomeOnHappyPath() {
        OrderEventDto dto = sampleEventDto();
        OrderShippedEvent event = new OrderShippedEvent(dto);
        // Recorder returns a non-null entity to mirror the real signature.
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        listener.on(event);

        ArgumentCaptor<NotificationContext> ctxCap = ArgumentCaptor.forClass(NotificationContext.class);
        verify(notificationDispatcher).dispatch(ctxCap.capture());
        NotificationContext ctx = ctxCap.getValue();
        assertThat(ctx.getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
        assertThat(ctx.getUser()).isSameAs(dto.getUserContactDto());
        assertThat(ctx.getCommunicationChanel()).isEqualTo(CommunicationChanel.EMAIL);
        assertThat(ctx.getPayload()).isNotNull();

        // SENT, retryCount=0, no failure
        verify(outcomeRecorder).recordOutcome(eq(ctx), eq(NotificationStatus.SENT), eq(0), isNull());
    }

    @Test
    void onShouldRecordFailedOutcomeWhenAllRetriesExhausted() {
        OrderEventDto dto = sampleEventDto();
        OrderShippedEvent event = new OrderShippedEvent(dto);
        doThrow(new RuntimeException("SMTP unreachable")).when(notificationDispatcher).dispatch(any());

        listener.on(event);

        verify(notificationDispatcher, times(MAX_DISPATCH_RETRIES)).dispatch(any());

        ArgumentCaptor<Throwable> failureCap = ArgumentCaptor.forClass(Throwable.class);
        verify(outcomeRecorder).recordOutcome(
                any(NotificationContext.class),
                eq(NotificationStatus.FAILED),
                eq(MAX_DISPATCH_RETRIES),
                failureCap.capture());
        assertThat(failureCap.getValue()).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP unreachable");
    }

    @Test
    void onShouldRetryAndRecordSentOnSecondAttempt() {
        OrderEventDto dto = sampleEventDto();
        OrderShippedEvent event = new OrderShippedEvent(dto);
        doThrow(new RuntimeException("transient"))
                .doNothing()
                .when(notificationDispatcher).dispatch(any());

        listener.on(event);

        verify(notificationDispatcher, times(2)).dispatch(any());

        // attempts=2, success → retryCount = attempts - 1 = 1, no failure passed.
        verify(outcomeRecorder).recordOutcome(
                any(NotificationContext.class),
                eq(NotificationStatus.SENT),
                eq(1),
                isNull());
    }

    @Test
    void onShouldUseRecorderRatherThanRepositoryDirectly() {
        // Pin: the listener is wired to the recorder, not to the repository
        // (Phase 1 centralisation).
        OrderEventDto dto = sampleEventDto();
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        listener.on(new OrderShippedEvent(dto));

        verify(outcomeRecorder).recordOutcome(
                any(NotificationContext.class),
                any(NotificationStatus.class),
                any(Integer.class),
                any());
        // Sanity: a non-null context was passed.
        verify(outcomeRecorder).recordOutcome(notNull(), any(), any(Integer.class), any());
    }
}
