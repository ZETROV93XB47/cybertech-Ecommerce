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
import com.novatech.cybertech.repositories.NotificationRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationRepository notificationRepository;
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
    void onShouldDispatchAndPersistNotificationOnHappyPath() {
        OrderEventDto dto = sampleEventDto();
        OrderShippedEvent event = new OrderShippedEvent(dto);

        listener.on(event);

        ArgumentCaptor<NotificationContext> ctxCap = ArgumentCaptor.forClass(NotificationContext.class);
        verify(notificationDispatcher).dispatch(ctxCap.capture());
        NotificationContext ctx = ctxCap.getValue();
        assertThat(ctx.getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
        assertThat(ctx.getUser()).isSameAs(dto.getUserContactDto());
        assertThat(ctx.getCommunicationChanel()).isEqualTo(CommunicationChanel.EMAIL);
        assertThat(ctx.getPayload()).isNotNull();

        ArgumentCaptor<NotificationEntity> entityCap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(entityCap.capture());
        NotificationEntity ent = entityCap.getValue();
        assertThat(ent.getOrderUuid()).isEqualTo(dto.getOrderUuid());
        assertThat(ent.getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
        assertThat(ent.getCommunicationChannel()).isEqualTo(CommunicationChanel.EMAIL);
        assertThat(ent.getRecipient()).isEqualTo("jane@example.com");
        assertThat(ent.getRetryCount()).isZero();
        assertThat(ent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(ent.getSentAt()).isNotNull();
        assertThat(ent.getErrorMessage()).isNull();
    }

    @Test
    void onShouldPersistFailedStatusWhenAllRetriesExhausted() {
        OrderEventDto dto = sampleEventDto();
        OrderShippedEvent event = new OrderShippedEvent(dto);
        doThrow(new RuntimeException("SMTP unreachable")).when(notificationDispatcher).dispatch(any());

        listener.on(event);

        verify(notificationDispatcher, times(MAX_DISPATCH_RETRIES)).dispatch(any());

        ArgumentCaptor<NotificationEntity> entityCap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(entityCap.capture());
        NotificationEntity ent = entityCap.getValue();
        assertThat(ent.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(ent.getRetryCount()).isEqualTo(MAX_DISPATCH_RETRIES);
        assertThat(ent.getErrorMessage()).contains("SMTP unreachable");
        assertThat(ent.getSentAt()).isNull();
        assertThat(ent.getLastAttemptAt()).isNotNull();
        assertThat(ent.getOrderUuid()).isEqualTo(dto.getOrderUuid());
    }

    @Test
    void onShouldRetryAndSucceedOnSecondAttempt() {
        OrderEventDto dto = sampleEventDto();
        OrderShippedEvent event = new OrderShippedEvent(dto);
        doThrow(new RuntimeException("transient"))
                .doNothing()
                .when(notificationDispatcher).dispatch(any());

        listener.on(event);

        verify(notificationDispatcher, times(2)).dispatch(any());

        ArgumentCaptor<NotificationEntity> entityCap = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(entityCap.capture());
        NotificationEntity ent = entityCap.getValue();
        assertThat(ent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(ent.getRetryCount()).isEqualTo(1);
        assertThat(ent.getSentAt()).isNotNull();
        assertThat(ent.getErrorMessage()).isNull();
    }
}
