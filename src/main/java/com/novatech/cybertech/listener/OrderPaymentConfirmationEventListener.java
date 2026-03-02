package com.novatech.cybertech.listener;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.CartService;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentConfirmationEventListener {

    private final CartService cartService;
    private final StockService stockService;
    private final OrderRepository orderRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentSuccess(final PaymentSucceededEvent event) {

        final String orderUuid = event.getStripeEvent().getData().getPaymentIntentPayload().getMetadata().get("order_uuid");
        final OrderEntity order = orderRepository.findByUuid(UUID.fromString(orderUuid)).orElseThrow(() -> new PaymentNotFoundException("Order not found for uuid : " + orderUuid));

        stockService.commitStock(order.getUuid());

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        cartService.clearCart(order.getUserEntity().getKeycloakId());

        log.info("Order {} marked as PAID", order.getUuid());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        final String orderUuid = event.getStripeEvent().getData().getPaymentIntentPayload().getMetadata().get("order_uuid");
        final OrderEntity order = orderRepository.findByUuid(UUID.fromString(orderUuid)).orElseThrow(() -> new PaymentNotFoundException("Order not found for uuid : " + orderUuid));

        stockService.releaseStock(order.getUuid());

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);

        log.info("Order {} marked as PAYMENT_FAILED", order.getUuid());
    }
}