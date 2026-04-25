package com.novatech.cybertech.listener;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link NotificationListener}.
 *
 * <p>Phase 2: the hand-rolled retry loop and the recorder interaction have
 * both moved into {@link NotificationRetryableDelivery}. The listener's job
 * narrowed to "build the context, hand it to the retryable-delivery bean."
 * These tests therefore assert that exactly one delegate call happens with a
 * correctly populated context — the retry semantics belong to
 * {@code NotificationRetryableDeliveryImpTest}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationRetryableDelivery retryableDelivery;

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
    void onShouldDelegateExactlyOnceToRetryableDeliveryWithSippingContext() {
        OrderEventDto dto = sampleEventDto();
        OrderShippedEvent event = new OrderShippedEvent(dto);

        listener.on(event);

        ArgumentCaptor<NotificationContext> ctxCap = ArgumentCaptor.forClass(NotificationContext.class);
        verify(retryableDelivery, times(1)).deliver(ctxCap.capture());
        verifyNoMoreInteractions(retryableDelivery);

        NotificationContext ctx = ctxCap.getValue();
        assertThat(ctx.getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
        assertThat(ctx.getUser()).isSameAs(dto.getUserContactDto());
        assertThat(ctx.getCommunicationChanel()).isEqualTo(CommunicationChanel.EMAIL);
        assertThat(ctx.getPayload()).isNotNull();
    }
}
