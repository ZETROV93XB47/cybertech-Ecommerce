package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShipOrderTransactionalDelegate}, in particular the {@code shippedAt}
 * idempotency guard added alongside the {@code updateOrder} charge-difference fix: an order whose
 * status was regressed to {@code AWAITING_PAYMENT} (to fund a top-up) and then re-promoted to
 * {@code PAID} by the payment webhook must never be handed to the carrier a second time.
 */
@ExtendWith(MockitoExtension.class)
class ShipOrderTransactionalDelegateTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShippingDispatcher shippingDispatcher;

    @InjectMocks
    private ShipOrderTransactionalDelegate delegate;

    private OrderEntity paidOrder() {
        return OrderEntityBuilder.aValidOrderBuilder()
                .uuid(UUID.randomUUID())
                .status(OrderStatus.PAID)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .build();
    }

    private ShippingContext context(final OrderEntity order) {
        return ShippingContext.builder()
                .packageId(order.getUuid().toString())
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .build();
    }

    @Test
    void claimAndShipDispatchesClaimsAndMarksShippedWhenNeverShippedBefore() {
        final OrderEntity order = paidOrder();
        when(orderRepository.save(order)).thenReturn(order);

        final boolean dispatched = delegate.claimAndShip(order, context(order));

        assertThat(dispatched).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getShippedAt()).isNotNull();
        verify(shippingDispatcher).dispatch(any(ShippingContext.class));
        verify(orderRepository, times(2)).save(order);
    }

    @Test
    void claimAndShipSkipsDispatchWhenOrderWasAlreadyShippedOnce() {
        final OrderEntity order = paidOrder();
        final LocalDateTime firstShipment = LocalDateTime.now().minusMinutes(10);
        order.setShippedAt(firstShipment);

        final boolean dispatched = delegate.claimAndShip(order, context(order));

        assertThat(dispatched).isFalse();
        assertThat(order.getShippedAt()).isEqualTo(firstShipment);
        // Status is left untouched — a re-promoted PAID order stays exactly as the caller found it.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(shippingDispatcher);
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }
}
