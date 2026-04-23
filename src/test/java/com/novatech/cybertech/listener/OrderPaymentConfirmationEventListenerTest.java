package com.novatech.cybertech.listener;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.PaymentDtoFixtures;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentConfirmationEventListenerTest {

    @Mock
    private CartService cartService;
    @Mock
    private StockService stockService;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderPaymentConfirmationEventListener listener;

    private static StripeWebhookEventDto eventForOrder(UUID orderUuid) {
        StripeWebhookEventDto event = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        event.getData().getPaymentIntentPayload().getMetadata().put("order_uuid", orderUuid.toString());
        return event;
    }

    private static OrderEntity orderWithKeycloakId(UUID uuid, String keycloakId) {
        UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        return OrderEntityBuilder.aValidOrderBuilder().uuid(uuid).userEntity(user).build();
    }

    // ---------- @TransactionalEventListener phase reflection ----------

    @Test
    void handlePaymentSuccessIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method m = OrderPaymentConfirmationEventListener.class.getMethod("handlePaymentSuccess", PaymentSucceededEvent.class);
        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void handlePaymentFailedIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method m = OrderPaymentConfirmationEventListener.class.getMethod("handlePaymentFailed", PaymentFailedEvent.class);
        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void handleRefundIsAnnotatedTransactionalEventListenerAfterCommit() throws NoSuchMethodException {
        Method m = OrderPaymentConfirmationEventListener.class.getMethod("handleRefund", PaymentRefundedEvent.class);
        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);
        assertThat(ann).isNotNull();
        assertThat(ann.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    // ---------- happy paths ----------

    @Test
    void handlePaymentSuccessShouldCommitStockMarkPaidAndClearCart() {
        UUID uuid = UUID.randomUUID();
        OrderEntity order = orderWithKeycloakId(uuid, "kc-1");
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        listener.handlePaymentSuccess(new PaymentSucceededEvent(eventForOrder(uuid)));

        verify(stockService).commitStock(uuid);
        ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(cartService).clearCart("kc-1");
    }

    @Test
    void handlePaymentSuccessShouldThrowWhenOrderNotFound() {
        UUID uuid = UUID.randomUUID();
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener.handlePaymentSuccess(new PaymentSucceededEvent(eventForOrder(uuid))))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(uuid.toString());
        verifyNoInteractions(stockService, cartService);
    }

    @Test
    void handlePaymentFailedShouldReleaseStockAndMarkPaymentFailed() {
        UUID uuid = UUID.randomUUID();
        OrderEntity order = orderWithKeycloakId(uuid, "kc-2");
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        listener.handlePaymentFailed(new PaymentFailedEvent(eventForOrder(uuid)));

        verify(stockService).releaseStock(uuid);
        ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verifyNoInteractions(cartService);
    }

    @Test
    void handleRefundShouldReleaseStockAndMarkRefunded() {
        UUID uuid = UUID.randomUUID();
        OrderEntity order = orderWithKeycloakId(uuid, "kc-3");
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.of(order));

        listener.handleRefund(new PaymentRefundedEvent(eventForOrder(uuid)));

        verify(stockService).releaseStock(uuid);
        ArgumentCaptor<OrderEntity> cap = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verifyNoInteractions(cartService);
    }

    @Test
    void handlePaymentFailedShouldThrowWhenOrderNotFound() {
        UUID uuid = UUID.randomUUID();
        when(orderRepository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener.handlePaymentFailed(new PaymentFailedEvent(eventForOrder(uuid))))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(uuid.toString());
    }

    // ---------- BUG-124: no null-guard on metadata.order_uuid ----------

    @Test
    void bug124_handlePaymentSuccess_nullOrderUuidInMetadata_shouldThrowDomainError() {
        StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        stripe.getData().getPaymentIntentPayload().getMetadata().remove("order_uuid");
        // BUG-124 FIX: missing order_uuid is now translated to a domain-level PaymentNotFoundException
        // (was: raw NullPointerException out of UUID.fromString(null) bubbling to the listener container).
        assertThatThrownBy(() -> listener.handlePaymentSuccess(new PaymentSucceededEvent(stripe)))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("order_uuid");
        verifyNoInteractions(stockService, cartService, orderRepository);
    }

    @Test
    void bug124_handlePaymentFailed_nullOrderUuidInMetadata_shouldThrowDomainError() {
        StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        stripe.getData().getPaymentIntentPayload().getMetadata().remove("order_uuid");
        assertThatThrownBy(() -> listener.handlePaymentFailed(new PaymentFailedEvent(stripe)))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("order_uuid");
        verifyNoInteractions(stockService, orderRepository);
    }

    @Test
    void bug124_handleRefund_nullOrderUuidInMetadata_shouldThrowDomainError() {
        StripeWebhookEventDto stripe = PaymentDtoFixtures.aValidPaymentSucceededEvent();
        stripe.getData().getPaymentIntentPayload().getMetadata().remove("order_uuid");
        assertThatThrownBy(() -> listener.handleRefund(new PaymentRefundedEvent(stripe)))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("order_uuid");
        verifyNoInteractions(stockService, orderRepository);
    }
}
