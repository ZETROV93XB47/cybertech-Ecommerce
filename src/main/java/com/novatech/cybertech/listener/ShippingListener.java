package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.events.OrderPaidEvent;
import com.novatech.cybertech.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingListener {

    private final OrderRepository orderRepository;
    private final ShippingDispatcher shippingDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderPaidEvent e) {
        OrderEntity order = orderRepository.findByUuid(e.getOrderUUID()).orElseThrow();

        if (order.getStatus() != OrderStatus.PAID) return;

        ShippingContext ctx = ShippingContext.builder()
                .user(order.getUserEntity())
                .packageId(order.getUuid().toString())
                .payload(order)
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .build();

        shippingDispatcher.dispatch(ctx);

        order.setStatus(OrderStatus.SHIPPED); // ou READY_TO_SHIP puis SHIPPED
        orderRepository.save(order);
    }
}
