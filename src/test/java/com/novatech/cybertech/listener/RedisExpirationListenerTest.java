package com.novatech.cybertech.listener;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.RESERVATION_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The listener only routes: all stock work lives behind {@link StockService}'s transactional proxy
 * (see the class javadoc for why). The DB behaviour of an expiry is covered by
 * {@code StockServiceImpTest} (expireReservation) and end-to-end by {@code ReservationExpiryIT}.
 */
@ExtendWith(MockitoExtension.class)
class RedisExpirationListenerTest {

    @Mock
    private RedisMessageListenerContainer container;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StockService stockService;

    private RedisExpirationListener listener;

    @BeforeEach
    void setUp() {
        listener = new RedisExpirationListener(container, orderRepository, stockService);
    }

    private static OrderEntity orderWithStatus(UUID orderUuid, OrderStatus status) {
        return OrderEntityBuilder.aValidOrderBuilder().uuid(orderUuid).status(status).build();
    }

    private static Message messageOf(String key) {
        return new DefaultMessage(new byte[0], key.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void onMessage_unpaidOrder_delegatesToExpireReservation() {
        UUID orderUuid = UUID.randomUUID();
        when(orderRepository.findByUuid(orderUuid))
                .thenReturn(Optional.of(orderWithStatus(orderUuid, OrderStatus.AWAITING_PAYMENT)));

        listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + orderUuid), null);

        verify(stockService).expireReservation(orderUuid);
        verify(stockService, never()).commitStock(any());
    }

    @Test
    void onMessage_unknownOrder_stillExpiresTheReservation() {
        UUID orderUuid = UUID.randomUUID();
        when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.empty());

        listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + orderUuid), null);

        verify(stockService).expireReservation(orderUuid);
    }

    @Test
    void onMessage_orderAlreadyPaid_shouldCommitStockInsteadOfReleasing() {
        // Oversell fix: if the Stripe webhook already flipped the order to PAID+ by the time the
        // Redis TTL fires, releasing the reservation would let the same units be resold. We must
        // self-heal by committing the stock (decrementing real inventory) instead of releasing it.
        UUID orderUuid = UUID.randomUUID();
        when(orderRepository.findByUuid(orderUuid))
                .thenReturn(Optional.of(orderWithStatus(orderUuid, OrderStatus.AWAITING_SHIPPING)));

        listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + orderUuid), null);

        verify(stockService).commitStock(orderUuid);
        verify(stockService, never()).expireReservation(any());
    }

    @Test
    void onMessage_wrongPrefixKey_shouldReturnSilently() {
        listener.onMessage(messageOf("some:other:key:" + UUID.randomUUID()), null);

        verifyNoInteractions(orderRepository, stockService);
    }

    @Test
    void bug121_malformedUuidAfterPrefix_shouldNotPropagateIllegalArgumentException() {
        // Malformed UUID tails are caught, logged at WARN, and short-circuit the handler instead of
        // propagating an IllegalArgumentException out to the listener container.
        listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + "not-a-uuid"), null);

        verifyNoInteractions(orderRepository, stockService);
    }

    @Test
    void onMessage_isNotAnnotatedTransactional() throws NoSuchMethodException {
        // The container calls the raw instance, never the Spring proxy: a @Transactional here would
        // be silently ignored and give a false sense of safety. Transactions belong to StockService.
        assertThat(RedisExpirationListener.class.getMethod("onMessage", Message.class, byte[].class)
                .getAnnotation(Transactional.class)).isNull();
    }
}
