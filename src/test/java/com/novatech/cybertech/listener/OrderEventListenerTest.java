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
import com.novatech.cybertech.events.OrderCreatedEvent;
import com.novatech.cybertech.events.OrderUpdatedEvent;
import com.novatech.cybertech.services.implementation.NotificationOutcomeRecorder;
import org.assertj.core.api.Assertions;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderEventListener}.
 *
 * <p>Phase 1 expands coverage: the order-created / order-updated paths now
 * persist a {@link NotificationEntity} via the
 * {@link NotificationOutcomeRecorder} on success AND failure (previously they
 * fired fire-and-forget with no audit trail).
 */
@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;
    @Mock
    private NotificationOutcomeRecorder outcomeRecorder;

    @InjectMocks
    private OrderEventListener listener;

    private OrderEventDto sample() {
        return OrderEventDto.builder()
                .orderUuid(UUID.randomUUID())
                .totalAmount(new BigDecimal("100.00"))
                .orderStatus(OrderStatus.CREATED)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .userContactDto(UserContactDto.builder()
                        .name("Jane").email("jane@example.com").phoneNumber("+33600000000")
                        .defaultCommunicationChanel(CommunicationChanel.EMAIL).build())
                .paymentAttemptStatus(PaymentAttemptStatus.CREATED)
                .build();
    }

    @Test
    void onOrderCreatedIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method m = OrderEventListener.class.getMethod("onOrderCreated", OrderCreatedEvent.class);
        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void onOrderUpdatedIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method m = OrderEventListener.class.getMethod("onOrderUpdated", OrderUpdatedEvent.class);
        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void onOrderCreatedShouldDispatchAndRecordSent() {
        OrderEventDto dto = sample();
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        listener.onOrderCreated(new OrderCreatedEvent(this, dto));

        ArgumentCaptor<NotificationContext> cap = ArgumentCaptor.forClass(NotificationContext.class);
        verify(notificationDispatcher).dispatch(cap.capture());
        NotificationContext ctx = cap.getValue();
        assertThat(ctx.getNotificationType()).isEqualTo(NotificationType.ORDER_CONFIRMATION);
        assertThat(ctx.getUser()).isSameAs(dto.getUserContactDto());
        assertThat(ctx.getData()).containsEntry("orderEventDto", dto);

        // Phase 1: success path persists SENT with retryCount=0.
        verify(outcomeRecorder).recordOutcome(eq(ctx), eq(NotificationStatus.SENT), eq(0), isNull());
    }

    @Test
    void onOrderUpdatedShouldDispatchAndRecordSent() {
        OrderEventDto dto = sample();
        when(outcomeRecorder.recordOutcome(any(), any(), any(Integer.class), any()))
                .thenReturn(NotificationEntity.builder().build());

        listener.onOrderUpdated(new OrderUpdatedEvent(this, dto));

        ArgumentCaptor<NotificationContext> cap = ArgumentCaptor.forClass(NotificationContext.class);
        verify(notificationDispatcher).dispatch(cap.capture());
        NotificationContext ctx = cap.getValue();
        assertThat(ctx.getNotificationType()).isEqualTo(NotificationType.ORDER_UPDATE);
        assertThat(ctx.getUser()).isSameAs(dto.getUserContactDto());
        assertThat(ctx.getData()).containsEntry("orderEventDto", dto);

        verify(outcomeRecorder).recordOutcome(eq(ctx), eq(NotificationStatus.SENT), eq(0), isNull());
    }

    @Test
    void onOrderCreatedShouldRecordFailedAndSwallowExceptionWhenDispatchThrows() {
        OrderEventDto dto = sample();
        RuntimeException boom = new RuntimeException("smtp down");
        doThrow(boom).when(notificationDispatcher).dispatch(any());

        // Async-listener contract: no propagation, even on failure.
        Assertions.assertThatCode(() -> listener.onOrderCreated(new OrderCreatedEvent(this, dto)))
                .doesNotThrowAnyException();

        verify(outcomeRecorder).recordOutcome(
                any(NotificationContext.class),
                eq(NotificationStatus.FAILED),
                eq(1),
                eq(boom));
    }

    @Test
    void onOrderUpdatedShouldRecordFailedAndSwallowExceptionWhenDispatchThrows() {
        OrderEventDto dto = sample();
        RuntimeException boom = new RuntimeException("smtp down");
        doThrow(boom).when(notificationDispatcher).dispatch(any());

        Assertions.assertThatCode(() -> listener.onOrderUpdated(new OrderUpdatedEvent(this, dto)))
                .doesNotThrowAnyException();

        verify(outcomeRecorder).recordOutcome(
                any(NotificationContext.class),
                eq(NotificationStatus.FAILED),
                eq(1),
                eq(boom));
    }
}
