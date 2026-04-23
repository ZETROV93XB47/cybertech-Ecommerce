package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingListenerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ShippingDispatcher shippingDispatcher;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private NotificationDispatcher notificationDispatcher; // not wired into ShippingListener; verifies BUG-122

    @InjectMocks
    private ShippingListener listener;

    private OrderEntity paidOrder() {
        UserEntity user = UserEntityBuilder.aValidUserBuilder().firstName("Jane").build();
        OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user)
                .status(OrderStatus.PAID)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .build();
        List<PaymentEntity> attempts = new ArrayList<>();
        attempts.add(PaymentEntityBuilder.aValidPaymentBuilder()
                .status(PaymentAttemptStatus.SUCCESS)
                .build());
        order.setPaymentAttempts(attempts);
        return order;
    }

    @Test
    void onIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method m = ShippingListener.class.getMethod("on", OrderPaidEvent.class);
        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void onShouldDispatchShippingMarkOrderShippedAndPublishOrderShippedEvent() {
        OrderEntity order = paidOrder();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

        listener.on(new OrderPaidEvent(order.getUuid()));

        ArgumentCaptor<ShippingContext> ctxCap = ArgumentCaptor.forClass(ShippingContext.class);
        verify(shippingDispatcher).dispatch(ctxCap.capture());
        ShippingContext ctx = ctxCap.getValue();
        assertThat(ctx.getPackageId()).isEqualTo(order.getUuid().toString());
        assertThat(ctx.getShippingProvider()).isEqualTo(ShippingProvider.DHL);

        ArgumentCaptor<OrderEntity> savedCap = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(savedCap.capture());
        assertThat(savedCap.getValue().getStatus()).isEqualTo(OrderStatus.SHIPPED);

        ArgumentCaptor<OrderShippedEvent> evCap = ArgumentCaptor.forClass(OrderShippedEvent.class);
        verify(eventPublisher).publishEvent(evCap.capture());
        assertThat(evCap.getValue().getOrderEventDto().getOrderUuid()).isEqualTo(order.getUuid());
    }

    @Test
    void onShouldShortCircuitWhenOrderStatusIsNotPaid() {
        OrderEntity order = paidOrder();
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

        listener.on(new OrderPaidEvent(order.getUuid()));

        verifyNoInteractions(shippingDispatcher, eventPublisher);
        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onShouldThrowWhenOrderNotFound() {
        UUID uuid = UUID.randomUUID();
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener.on(new OrderPaidEvent(uuid)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(uuid.toString());
    }

    @Test
    void bug122_shippingListenerDoesNotDispatchNotification_notificationGoesViaOrderShippedEvent() {
        // BUG-122 FIX: the orphan NotificationContext local was removed. ShippingListener now
        // hands off the notification side-effect to NotificationListener via OrderShippedEvent.
        // It must never call NotificationDispatcher itself.
        OrderEntity order = paidOrder();
        when(orderRepository.findByUuid(order.getUuid())).thenReturn(Optional.of(order));

        listener.on(new OrderPaidEvent(order.getUuid()));

        verifyNoInteractions(notificationDispatcher);
        // Sanity: the OrderShippedEvent IS published so NotificationListener can pick it up.
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(OrderShippedEvent.class));
    }
}
