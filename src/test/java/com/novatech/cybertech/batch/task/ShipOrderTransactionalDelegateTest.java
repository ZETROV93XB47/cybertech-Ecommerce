package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShipOrderTransactionalDelegate}.
 *
 * <p>The delegate re-loads the order in its own {@code REQUIRES_NEW} transaction and works on that
 * copy only. The caller's instance belongs to the Spring Batch step's outer session: mutating it
 * left it dirty at a stale {@code @Version}, and the step's commit then failed with
 * {@code ObjectOptimisticLockingFailureException}. Each test therefore uses two instances sharing
 * one UUID — the caller's {@code order} and the {@code stored} copy returned by the repository.</p>
 *
 * <p>The {@code shippedAt} guard covers the {@code updateOrder} charge-difference scenario: an
 * order regressed to {@code AWAITING_PAYMENT} (to fund a top-up) and then re-promoted to
 * {@code PAID} by the payment webhook must never be handed to the carrier a second time.</p>
 */
@ExtendWith(MockitoExtension.class)
class ShipOrderTransactionalDelegateTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShippingDispatcher shippingDispatcher;

    @InjectMocks
    private ShipOrderTransactionalDelegate delegate;

    private static final UUID ORDER_UUID = UUID.randomUUID();

    private OrderEntity paidOrder() {
        return OrderEntityBuilder.aValidOrderBuilder()
                .uuid(ORDER_UUID)
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
        final OrderEntity stored = paidOrder();
        when(orderRepository.findByUuid(ORDER_UUID)).thenReturn(Optional.of(stored));

        final boolean dispatched = delegate.claimAndShip(order, context(order));

        assertThat(dispatched).isTrue();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(stored.getShippedAt()).isNotNull();
        verify(shippingDispatcher).dispatch(any(ShippingContext.class));
        verify(orderRepository, times(2)).save(stored);
    }

    @Test
    void claimAndShipNeverModifiesTheCallersInstance() {
        final OrderEntity order = paidOrder();
        when(orderRepository.findByUuid(ORDER_UUID)).thenReturn(Optional.of(paidOrder()));

        delegate.claimAndShip(order, context(order));

        // The caller's instance is managed by the batch step's session: any change here would be
        // flushed again at the step's commit with a stale @Version.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getShippedAt()).isNull();
        verify(orderRepository, never()).save(order);
    }

    @Test
    void claimAndShipSkipsDispatchWhenOrderWasAlreadyShippedOnce() {
        final OrderEntity order = paidOrder();
        final OrderEntity stored = paidOrder();
        final LocalDateTime firstShipment = LocalDateTime.now().minusMinutes(10);
        stored.setShippedAt(firstShipment);
        when(orderRepository.findByUuid(ORDER_UUID)).thenReturn(Optional.of(stored));

        final boolean dispatched = delegate.claimAndShip(order, context(order));

        assertThat(dispatched).isFalse();
        assertThat(stored.getShippedAt()).isEqualTo(firstShipment);
        // Status is left untouched — a re-promoted PAID order stays exactly as it was found.
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(shippingDispatcher);
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void claimAndShipSkipsDispatchWhenAnotherPathAlreadyClaimedTheOrder() {
        // ShippingListener committed its claim between the tasklet's findByStatus(PAID) and this call.
        final OrderEntity order = paidOrder();
        final OrderEntity stored = paidOrder();
        stored.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByUuid(ORDER_UUID)).thenReturn(Optional.of(stored));

        final boolean dispatched = delegate.claimAndShip(order, context(order));

        assertThat(dispatched).isFalse();
        verifyNoInteractions(shippingDispatcher);
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void claimAndShipThrowsWhenTheOrderNoLongerExists() {
        final OrderEntity order = paidOrder();
        when(orderRepository.findByUuid(ORDER_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delegate.claimAndShip(order, context(order)))
                .isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(shippingDispatcher);
    }
}
